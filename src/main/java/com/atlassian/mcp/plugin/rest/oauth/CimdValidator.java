package com.atlassian.mcp.plugin.rest.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and validates OAuth Client ID Metadata Documents (CIMD) per
 * <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-client-id-metadata-document-00">
 * draft-ietf-oauth-client-id-metadata-document-00</a> and the MCP 2025-11-25
 * authorization spec (SEP-991).
 *
 * <p>A CIMD-style {@code client_id} is an HTTPS URL that resolves to a JSON document describing
 * the client (its display name, registered redirect URIs, etc.). This validator fetches that
 * document, enforces a strict size cap (8 KB) and timeouts to prevent SSRF/DoS, parses it, and
 * caches the parsed metadata in memory for one hour.
 *
 * <p>Thread-safe. Cache is bounded to 1000 entries; oldest entries are evicted when full.
 */
public final class CimdValidator {

    private static final Logger log = LoggerFactory.getLogger(CimdValidator.class);

    /** Hard cap on the metadata document body (8 KB). */
    public static final int MAX_BODY_BYTES = 8 * 1024;

    /** Cache TTL — 1 hour. The spec says clients SHOULD respect HTTP cache headers; we use a
     *  conservative fixed TTL here as a forward-compatible baseline. */
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /** Maximum number of cached metadata entries. Mirrors DCR cache shape. */
    private static final int CACHE_MAX = 1000;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CimdValidator() {
        this(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                new ObjectMapper());
    }

    /** Constructor for tests — allows injecting a stub HttpClient. */
    public CimdValidator(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Returns {@code true} if the given {@code client_id} is a CIMD-style URL (an HTTPS URL
     * with a path component). This is the discriminator the spec uses to distinguish CIMD from
     * DCR client IDs.
     */
    public static boolean isCimdClientId(String clientId) {
        if (clientId == null || !clientId.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(clientId);
            String path = uri.getPath();
            // Spec MUST: URL must use "https" scheme and contain a path component
            return uri.getHost() != null && path != null && !path.isEmpty() && !path.equals("/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Fetches and validates the CIMD at {@code clientIdUrl}. Returns the parsed metadata.
     *
     * <p>Performs an in-process cache lookup first; on miss, performs a bounded HTTPS GET
     * (8 KB cap, 5 s connect / 10 s request timeout, no redirects). Validates that the document
     * is well-formed JSON, contains a {@code redirect_uris} array of HTTPS or {@code http://localhost}
     * URIs, and that the document's own {@code client_id} (if present) matches the URL.
     */
    public CimdMetadata resolve(String clientIdUrl) throws CimdException {
        if (!isCimdClientId(clientIdUrl)) {
            throw new CimdException("client_id is not a valid CIMD URL");
        }

        CacheEntry cached = cache.get(clientIdUrl);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.metadata;
        }

        CimdMetadata metadata = fetchAndValidate(clientIdUrl);
        putInCache(clientIdUrl, metadata);
        return metadata;
    }

    private CimdMetadata fetchAndValidate(String clientIdUrl) throws CimdException {
        URI uri;
        try {
            uri = new URI(clientIdUrl);
        } catch (URISyntaxException e) {
            throw new CimdException("Invalid CIMD URL: " + e.getMessage());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CimdException("Failed to fetch CIMD: " + e.getMessage());
        }

        if (response.statusCode() != 200) {
            throw new CimdException("CIMD fetch returned HTTP " + response.statusCode());
        }

        byte[] body = response.body();
        if (body == null) {
            throw new CimdException("CIMD response had no body");
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new CimdException(
                    "CIMD document exceeds " + MAX_BODY_BYTES + " byte cap (" + body.length + ")");
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            throw new CimdException("CIMD is not valid JSON: " + e.getMessage());
        }

        if (root == null || !root.isObject()) {
            throw new CimdException("CIMD must be a JSON object");
        }

        // Spec MUST: validate that fetched document's client_id matches the URL exactly
        JsonNode docClientId = root.get("client_id");
        if (docClientId != null && docClientId.isTextual()
                && !clientIdUrl.equals(docClientId.asText())) {
            throw new CimdException("CIMD client_id does not match the fetched URL");
        }

        // Spec MUST: redirect_uris is required
        JsonNode redirectUrisNode = root.get("redirect_uris");
        if (redirectUrisNode == null || !redirectUrisNode.isArray() || redirectUrisNode.size() == 0) {
            throw new CimdException("CIMD redirect_uris is required and must be a non-empty array");
        }

        List<String> redirectUris = new ArrayList<>();
        Iterator<JsonNode> it = redirectUrisNode.elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (!n.isTextual()) {
                throw new CimdException("CIMD redirect_uris entries must be strings");
            }
            String uriStr = n.asText();
            if (!isAllowedRedirectUri(uriStr)) {
                throw new CimdException(
                        "CIMD redirect_uri must use https:// or http://localhost or http://127.0.0.1");
            }
            redirectUris.add(uriStr);
        }

        String clientName = textOrNull(root, "client_name");
        String scope = textOrNull(root, "scope");
        String tokenAuthMethod = textOrNull(root, "token_endpoint_auth_method");

        return new CimdMetadata(
                clientIdUrl,
                clientName,
                Collections.unmodifiableList(redirectUris),
                scope,
                tokenAuthMethod);
    }

    private static boolean isAllowedRedirectUri(String uri) {
        if (uri == null) return false;
        if (uri.startsWith("https://")) return true;
        // Loopback per RFC 8252 — common for native MCP clients
        if (uri.startsWith("http://localhost") || uri.startsWith("http://127.0.0.1")) return true;
        return false;
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode n = root.get(field);
        return (n != null && n.isTextual()) ? n.asText() : null;
    }

    private void putInCache(String url, CimdMetadata metadata) {
        // Simple cap: when cache is full, drop the oldest entries (insertion-time approximation).
        if (cache.size() >= CACHE_MAX) {
            // Best-effort eviction — drop everything that's already expired first
            Instant now = Instant.now();
            cache.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
            // If still full, evict the earliest-inserted entry
            if (cache.size() >= CACHE_MAX) {
                cache.entrySet().stream()
                        .min(Map.Entry.comparingByValue(
                                (a, b) -> a.insertedAt.compareTo(b.insertedAt)))
                        .ifPresent(e -> cache.remove(e.getKey()));
            }
        }
        Instant now = Instant.now();
        cache.put(url, new CacheEntry(metadata, now, now.plus(CACHE_TTL)));
    }

    /** Visible for testing — clears the cache. */
    public void clearCache() {
        cache.clear();
    }

    /** Visible for testing — current cache size. */
    public int cacheSize() {
        return cache.size();
    }

    /** Parsed and validated CIMD metadata. Immutable. */
    public static final class CimdMetadata {
        public final String clientId;
        public final String clientName;
        public final List<String> redirectUris;
        public final String scope;
        public final String tokenEndpointAuthMethod;

        public CimdMetadata(String clientId, String clientName, List<String> redirectUris,
                            String scope, String tokenEndpointAuthMethod) {
            this.clientId = clientId;
            this.clientName = clientName;
            this.redirectUris = redirectUris;
            this.scope = scope;
            this.tokenEndpointAuthMethod = tokenEndpointAuthMethod;
        }

        /** Convenience for logs / debug. Does not include sensitive data. */
        public Map<String, Object> toLogMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("client_id", clientId);
            m.put("client_name", clientName);
            m.put("redirect_uris", redirectUris);
            return m;
        }
    }

    /** Checked exception thrown when a CIMD cannot be resolved or fails validation. */
    public static final class CimdException extends Exception {
        public CimdException(String message) {
            super(message);
        }
    }

    private static final class CacheEntry {
        final CimdMetadata metadata;
        final Instant insertedAt;
        final Instant expiresAt;

        CacheEntry(CimdMetadata metadata, Instant insertedAt, Instant expiresAt) {
            this.metadata = metadata;
            this.insertedAt = insertedAt;
            this.expiresAt = expiresAt;
        }
    }
}
