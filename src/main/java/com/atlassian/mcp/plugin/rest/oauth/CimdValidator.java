package com.atlassian.mcp.plugin.rest.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
 * Resolves and validates OAuth Client ID Metadata Documents (CIMD) per <a
 * href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-client-id-metadata-document-00">
 * draft-ietf-oauth-client-id-metadata-document-00</a> and the MCP 2025-11-25 authorization spec
 * (SEP-991).
 *
 * <p>A CIMD-style {@code client_id} is an HTTPS URL that resolves to a JSON document describing the
 * client (its display name, registered redirect URIs, etc.). Because the URL is attacker-controlled
 * and fetched from inside the Jira JVM by an <b>unauthenticated</b> {@code /authorize} request,
 * this validator enforces a layered SSRF/DoS defense:
 *
 * <ul>
 *   <li><b>HTTPS only, no redirects</b> — the client is built with {@code Redirect.NEVER} so a
 *       benign URL cannot 302-bounce onto an internal target.
 *   <li><b>Address guard</b> — the host is resolved and the fetch is rejected if <em>any</em>
 *       A/AAAA record is loopback, link-local, site-local (RFC 1918), CGNAT (100.64.0.0/10),
 *       unique-local (fc00::/7), or the cloud-metadata IP (169.254.169.254), <em>before</em> the
 *       request is sent. This blocks static internal-IP DNS records. It does <em>not</em> pin the
 *       address the {@link HttpClient} later connects to (the JDK re-resolves DNS at connect time),
 *       so an active DNS-rebinding attacker who flips the record between this check and the connect
 *       is an accepted residual risk — the {@code /authorize} entry point is rate-limited, and full
 *       closure would require connection-level IP pinning.
 *   <li><b>Bounded body</b> — the 8 KB cap is enforced while streaming, not after buffering.
 *   <li><b>Timeouts</b> — 5 s connect / 10 s request.
 * </ul>
 *
 * <p>Thread-safe. The result cache is bounded to 1000 entries with positive and (short) negative
 * caching so a repeatedly-failing URL cannot be used to hammer the resolver.
 */
public final class CimdValidator {

  private static final Logger log = LoggerFactory.getLogger(CimdValidator.class);

  /** Hard cap on the metadata document body (8 KB). */
  public static final int MAX_BODY_BYTES = 8 * 1024;

  /** Cache TTL for a successful resolution. */
  private static final Duration POSITIVE_TTL = Duration.ofHours(1);

  /** Cache TTL for a failed resolution — short, so a transient failure self-heals quickly. */
  private static final Duration NEGATIVE_TTL = Duration.ofMinutes(5);

  /** Maximum number of cached entries (positive + negative combined). */
  private static final int CACHE_MAX = 1000;

  private final HttpClient httpClient;
  private final ObjectMapper mapper;
  private final boolean enforceSsrfGuards;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public CimdValidator() {
    this(
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build(),
        new ObjectMapper(),
        true);
  }

  /**
   * Constructor for tests — allows injecting a stub {@link HttpClient} and disabling the address
   * guard when a test needs to resolve a stub host that would otherwise be blocked.
   */
  public CimdValidator(HttpClient httpClient, ObjectMapper mapper, boolean enforceSsrfGuards) {
    this.httpClient = httpClient;
    this.mapper = mapper;
    this.enforceSsrfGuards = enforceSsrfGuards;
  }

  /**
   * Returns {@code true} if the given {@code client_id} is a CIMD-style URL (an HTTPS URL with a
   * path component). This is the discriminator the spec uses to distinguish CIMD from DCR client
   * IDs.
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
   * <p>Performs an in-process cache lookup first (positive and negative); on miss, performs the
   * guarded HTTPS GET and validates the document. Failures are negatively cached so a bad URL
   * cannot be replayed to hammer the resolver.
   */
  public CimdMetadata resolve(String clientIdUrl) throws CimdException {
    if (!isCimdClientId(clientIdUrl)) {
      throw new CimdException("client_id is not a valid CIMD URL");
    }

    CacheEntry cached = cache.get(clientIdUrl);
    if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
      if (cached.failure != null) {
        throw new CimdException("cached failure: " + cached.failure);
      }
      return cached.metadata;
    }

    try {
      CimdMetadata metadata = fetchAndValidate(clientIdUrl);
      putPositive(clientIdUrl, metadata);
      return metadata;
    } catch (CimdException e) {
      putNegative(clientIdUrl, e.getMessage());
      throw e;
    }
  }

  private CimdMetadata fetchAndValidate(String clientIdUrl) throws CimdException {
    URI uri;
    try {
      uri = new URI(clientIdUrl);
    } catch (URISyntaxException e) {
      throw new CimdException("Invalid CIMD URL: " + e.getMessage());
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new CimdException("CIMD URL must be https");
    }
    guardSsrf(uri.getHost());

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(uri)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

    HttpResponse<InputStream> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new CimdException("Failed to fetch CIMD: " + e.getMessage());
    }

    if (response.statusCode() != 200) {
      throw new CimdException("CIMD fetch returned HTTP " + response.statusCode());
    }

    byte[] body = readBounded(response.body(), MAX_BODY_BYTES);
    if (body == null) {
      throw new CimdException("CIMD document exceeds " + MAX_BODY_BYTES + " byte cap");
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
    if (docClientId != null
        && docClientId.isTextual()
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
            "CIMD redirect_uri must use https:// or http://localhost|127.0.0.1");
      }
      redirectUris.add(uriStr);
    }

    return new CimdMetadata(
        clientIdUrl,
        textOrNull(root, "client_name"),
        Collections.unmodifiableList(redirectUris),
        textOrNull(root, "scope"),
        textOrNull(root, "token_endpoint_auth_method"));
  }

  /**
   * SSRF host guard: resolve all A/AAAA records and reject if ANY is loopback, link-local,
   * site-local (RFC 1918), any-local, CGNAT (100.64.0.0/10), unique-local (fc00::/7), or the
   * cloud-metadata IP (169.254.169.254). Rejects static internal-IP DNS records before the fetch.
   * Does not bind the address the {@link HttpClient} later connects to, so an active DNS-rebinding
   * attacker is an accepted residual risk (see class javadoc).
   */
  private void guardSsrf(String host) throws CimdException {
    if (!enforceSsrfGuards) return;
    if (host == null || host.isEmpty()) {
      throw new CimdException("CIMD URL has no host");
    }
    InetAddress[] addrs;
    try {
      addrs = InetAddress.getAllByName(host);
    } catch (UnknownHostException e) {
      throw new CimdException("CIMD host does not resolve: " + host);
    }
    for (InetAddress a : addrs) {
      if (a.isLoopbackAddress()
          || a.isLinkLocalAddress()
          || a.isSiteLocalAddress()
          || a.isAnyLocalAddress()
          || isUniqueLocalOrMetadata(a)) {
        throw new CimdException("CIMD host resolves to a blocked address: " + a.getHostAddress());
      }
    }
  }

  private static boolean isUniqueLocalOrMetadata(InetAddress a) {
    byte[] b = a.getAddress();
    if (b.length == 16 && (b[0] & 0xFE) == 0xFC) {
      return true; // fc00::/7 unique-local
    }
    return b.length == 4
        && (
        // 169.254.169.254 cloud-metadata IP
        ((b[0] & 0xFF) == 169
                && (b[1] & 0xFF) == 254
                && (b[2] & 0xFF) == 169
                && (b[3] & 0xFF) == 254)
            // 100.64.0.0/10 CGNAT (RFC 6598)
            || ((b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40));
  }

  /** Reads up to {@code max} bytes from the response stream; null if the body exceeds the cap. */
  private static byte[] readBounded(InputStream in, int max) {
    if (in == null) return new byte[0];
    try (InputStream stream = in) {
      ByteArrayOutputStream buf = new ByteArrayOutputStream();
      byte[] chunk = new byte[2048];
      long total = 0;
      int n;
      while ((n = stream.read(chunk)) != -1) {
        total += n;
        if (total > max) return null;
        buf.write(chunk, 0, n);
      }
      return buf.toByteArray();
    } catch (IOException e) {
      return new byte[0];
    }
  }

  /**
   * Allowed redirect URIs: https for any host, OR http ONLY for the exact loopback hosts (localhost
   * / 127.0.0.1 / [::1]). Host is exact-matched via {@link URI} parsing so {@code
   * http://localhost.evil.example} and embedded-credential URIs are rejected.
   */
  // Package-private (not private) so CimdValidatorTest can assert host-exact matching directly.
  static boolean isAllowedRedirectUri(String uri) {
    if (uri == null || uri.isEmpty()) return false;
    URI u;
    try {
      u = new URI(uri);
    } catch (URISyntaxException e) {
      return false;
    }
    if (u.getUserInfo() != null) return false; // reject embedded credentials
    String scheme = u.getScheme();
    String host = u.getHost();
    if (scheme == null || host == null) return false;
    if ("https".equalsIgnoreCase(scheme)) return true;
    if ("http".equalsIgnoreCase(scheme)) {
      return host.equalsIgnoreCase("localhost")
          || host.equals("127.0.0.1")
          || host.equals("[::1]")
          || host.equals("::1");
    }
    return false;
  }

  private static String textOrNull(JsonNode root, String field) {
    JsonNode n = root.get(field);
    return (n != null && n.isTextual()) ? n.asText() : null;
  }

  private void putPositive(String url, CimdMetadata metadata) {
    evictIfFull();
    cache.put(url, new CacheEntry(metadata, null, Instant.now().plus(POSITIVE_TTL)));
  }

  private void putNegative(String url, String failure) {
    evictIfFull();
    cache.put(
        url,
        new CacheEntry(
            null, failure == null ? "error" : failure, Instant.now().plus(NEGATIVE_TTL)));
  }

  private void evictIfFull() {
    if (cache.size() < CACHE_MAX) return;
    Instant now = Instant.now();
    // Best-effort eviction — drop everything already expired first
    cache.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    // If still full, evict the entry closest to expiry
    if (cache.size() >= CACHE_MAX) {
      cache.entrySet().stream()
          .min(Map.Entry.comparingByValue((a, b) -> a.expiresAt.compareTo(b.expiresAt)))
          .ifPresent(e -> cache.remove(e.getKey()));
    }
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

    public CimdMetadata(
        String clientId,
        String clientName,
        List<String> redirectUris,
        String scope,
        String tokenEndpointAuthMethod) {
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
    final String failure;
    final Instant expiresAt;

    CacheEntry(CimdMetadata metadata, String failure, Instant expiresAt) {
      this.metadata = metadata;
      this.failure = failure;
      this.expiresAt = expiresAt;
    }
  }
}
