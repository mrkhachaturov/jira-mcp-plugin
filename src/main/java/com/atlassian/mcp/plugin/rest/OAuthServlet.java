package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.config.OAuthStateStore;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0 proxy servlet. Served at /plugins/servlet/mcp-oauth/*.
 * Uses @UnrestrictedAccess (Jira 10 secure endpoint defaults) to allow anonymous access.
 * Combined with before-login filter to prevent login redirect.
 */
@UnrestrictedAccess
public class OAuthServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(OAuthServlet.class);

    private static final int MAX_REGISTER_BODY = 65_536; // 64 KB
    private static final int MAX_TOKEN_BODY = 8_192; // 8 KB

    // Rate limits per minute per IP
    private static final int RATE_REGISTER = 5;
    private static final int RATE_TOKEN = 20;
    private static final int RATE_AUTHORIZE = 10;
    private static final int RATE_METADATA = 60;

    private final McpPluginConfig config;
    private final OAuthStateStore stateStore;
    private final RateLimiter rateLimiter;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Inject
    public OAuthServlet(
            McpPluginConfig config,
            OAuthStateStore stateStore,
            RateLimiter rateLimiter,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.stateStore = stateStore;
        this.rateLimiter = rateLimiter;
        this.applicationProperties = applicationProperties;
    }

    private String getBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) return override;
        return applicationProperties.getBaseUrl().toString();
    }

    private String getOAuthBase() {
        return getBaseUrl() + "/plugins/servlet/mcp-oauth";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "";
        String ip = getClientIp(req);

        if (path.equals("/protected-resource") || path.equals("/protected-resource/")) {
            if (!rateLimiter.isAllowed(ip, "oauth-metadata", RATE_METADATA)) {
                sendRateLimited(resp);
                return;
            }
            addSecurityHeaders(resp);
            resp.setContentType("application/json");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("resource", getBaseUrl() + "/rest/mcp/1.0/");
            meta.put("authorization_servers", List.of(getOAuthBase()));
            mapper.writeValue(resp.getWriter(), meta);

        } else if (path.equals("/metadata") || path.equals("/metadata/")) {
            if (!rateLimiter.isAllowed(ip, "oauth-metadata", RATE_METADATA)) {
                sendRateLimited(resp);
                return;
            }
            addSecurityHeaders(resp);
            resp.setContentType("application/json");
            if (!config.isOAuthEnabled()) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"OAuth not configured\"}");
                return;
            }
            String base = getOAuthBase();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("issuer", base);
            meta.put("authorization_endpoint", base + "/authorize");
            meta.put("token_endpoint", base + "/token");
            meta.put("registration_endpoint", base + "/register");
            meta.put("response_types_supported", List.of("code"));
            meta.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
            meta.put("token_endpoint_auth_methods_supported", List.of("none"));
            meta.put("code_challenge_methods_supported", List.of("S256"));
            meta.put("scopes_supported", List.of("WRITE", "READ"));
            mapper.writeValue(resp.getWriter(), meta);

        } else if (path.startsWith("/authorize")) {
            if (!rateLimiter.isAllowed(ip, "oauth-authorize", RATE_AUTHORIZE)) {
                sendRateLimited(resp);
                return;
            }
            handleAuthorize(req, resp);

        } else if (path.startsWith("/callback")) {
            handleCallback(req, resp);

        } else {
            resp.setStatus(404);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) path = "";
        String ip = getClientIp(req);
        resp.setContentType("application/json");

        if (path.equals("/register") || path.equals("/register/")) {
            if (!rateLimiter.isAllowed(ip, "oauth-register", RATE_REGISTER)) {
                log.warn("[MCP-SEC] Rate limit on /register from {}", ip);
                sendRateLimited(resp);
                return;
            }
            handleRegister(req, resp);
        } else if (path.equals("/token") || path.equals("/token/")) {
            if (!rateLimiter.isAllowed(ip, "oauth-token", RATE_TOKEN)) {
                log.warn("[MCP-SEC] Rate limit on /token from {}", ip);
                sendRateLimited(resp);
                return;
            }
            handleToken(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!config.isOAuthEnabled()) {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"OAuth not configured\"}");
            return;
        }

        // Body size limit
        byte[] bodyBytes = readLimited(req.getInputStream(), MAX_REGISTER_BODY);
        if (bodyBytes == null) {
            resp.setStatus(413);
            resp.getWriter().write("{\"error\":\"Request body too large\"}");
            return;
        }

        Map<String, Object> body = mapper.readValue(bodyBytes, Map.class);
        String clientName = (String) body.getOrDefault("client_name", "MCP Client");
        List<String> redirectUris = (List<String>) body.getOrDefault("redirect_uris", List.of());

        OAuthStateStore.RegisteredClient client = stateStore.registerClient(clientName, redirectUris);
        if (client == null) {
            resp.setStatus(503);
            resp.getWriter().write("{\"error\":\"Registration capacity reached\"}");
            return;
        }

        log.info("[MCP-SEC] Client registered: id={} name='{}' from {}",
                client.clientId.substring(0, 8), sanitizeLog(clientName), getClientIp(req));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("client_id", client.clientId);
        result.put("client_name", client.clientName);
        result.put("redirect_uris", client.redirectUris);
        result.put("grant_types", List.of("authorization_code"));
        result.put("response_types", List.of("code"));
        result.put("token_endpoint_auth_method", "none");
        resp.setStatus(201);
        mapper.writeValue(resp.getWriter(), result);
    }

    private void handleAuthorize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!config.isOAuthEnabled()) {
            resp.setStatus(400);
            resp.getWriter().write("OAuth not configured");
            return;
        }

        String clientId = req.getParameter("client_id");
        String redirectUri = req.getParameter("redirect_uri");
        String state = req.getParameter("state");
        String codeChallenge = req.getParameter("code_challenge");
        String codeChallengeMethod = req.getParameter("code_challenge_method");
        String scope = req.getParameter("scope");

        // PKCE is mandatory — reject if missing
        if (codeChallenge == null || codeChallenge.isEmpty()) {
            log.warn("[MCP-SEC] Authorize without code_challenge from {}", getClientIp(req));
            resp.setStatus(400);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"invalid_request\",\"error_description\":\"code_challenge is required\"}");
            return;
        }
        if (!"S256".equals(codeChallengeMethod)) {
            resp.setStatus(400);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"invalid_request\",\"error_description\":\"Only S256 code_challenge_method is supported\"}");
            return;
        }

        OAuthStateStore.RegisteredClient client = stateStore.getClient(clientId);
        if (client == null) {
            resp.setStatus(400);
            resp.getWriter().write("Unknown client_id");
            return;
        }

        // Validate redirect_uri against registered URIs (prevents open redirect / token theft)
        if (redirectUri == null || redirectUri.isEmpty()
                || client.redirectUris.isEmpty()
                || !client.redirectUris.contains(redirectUri)) {
            log.warn("[MCP-SEC] redirect_uri mismatch for client {} from {}", clientId, getClientIp(req));
            resp.setStatus(400);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\":\"invalid_request\",\"error_description\":\"redirect_uri does not match registered URIs\"}");
            return;
        }

        String internalState = stateStore.createPendingAuth(
                redirectUri, state, codeChallenge, codeChallengeMethod, clientId);
        if (internalState == null) {
            resp.setStatus(503);
            resp.getWriter().write("Server at capacity");
            return;
        }

        String jiraAuthorize = getBaseUrl() + "/rest/oauth2/latest/authorize";
        String callbackUri = getOAuthBase() + "/callback";

        String url = jiraAuthorize
                + "?client_id=" + enc(config.getOAuthClientId())
                + "&redirect_uri=" + enc(callbackUri)
                + "&response_type=code"
                + "&scope=" + enc(scope != null ? scope : "WRITE")
                + "&state=" + enc(internalState);

        resp.sendRedirect(url);
    }

    private void handleCallback(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String error = req.getParameter("error");
        if (error != null) {
            addSecurityHeaders(resp);
            resp.setContentType("text/html");
            resp.setHeader("Content-Security-Policy", "default-src 'none'");
            resp.setStatus(400);
            resp.getWriter().write("<h1>OAuth Error</h1><p>" + htmlEncode(error) + "</p>");
            return;
        }

        String jiraCode = req.getParameter("code");
        String internalState = req.getParameter("state");

        OAuthStateStore.PendingAuth pending = stateStore.consumePendingAuth(internalState);
        if (pending == null) {
            addSecurityHeaders(resp);
            resp.setContentType("text/html");
            resp.setHeader("Content-Security-Policy", "default-src 'none'");
            resp.setStatus(400);
            resp.getWriter().write("<h1>Error</h1><p>Invalid or expired state</p>");
            return;
        }

        JiraTokenResponse jiraTokens;
        try {
            jiraTokens = exchangeCodeForToken(jiraCode);
        } catch (Exception e) {
            log.warn("[MCP-SEC] Token exchange failed: {}", e.getMessage());
            addSecurityHeaders(resp);
            resp.setContentType("text/html");
            resp.setHeader("Content-Security-Policy", "default-src 'none'");
            resp.setStatus(500);
            resp.getWriter().write("<h1>Error</h1><p>Token exchange failed</p>");
            return;
        }

        String proxyCode = stateStore.createProxyCode(jiraTokens.accessToken,
                jiraTokens.refreshToken, jiraTokens.expiresIn,
                pending.clientId, pending.clientRedirectUri,
                pending.codeChallenge, pending.codeChallengeMethod);
        if (proxyCode == null) {
            resp.setStatus(503);
            resp.setContentType("text/html");
            resp.getWriter().write("<h1>Error</h1><p>Server at capacity</p>");
            return;
        }

        String clientCallback = pending.clientRedirectUri
                + (pending.clientRedirectUri.contains("?") ? "&" : "?")
                + "code=" + enc(proxyCode)
                + "&state=" + enc(pending.clientState);

        resp.sendRedirect(clientCallback);
    }

    private void handleToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Body size check via content length
        if (req.getContentLength() > MAX_TOKEN_BODY) {
            resp.setStatus(413);
            resp.getWriter().write("{\"error\":\"Request body too large\"}");
            return;
        }

        String grantType = req.getParameter("grant_type");
        String clientId = req.getParameter("client_id");

        if ("authorization_code".equals(grantType)) {
            handleAuthorizationCodeGrant(req, resp, clientId);
        } else if ("refresh_token".equals(grantType)) {
            handleRefreshTokenGrant(req, resp, clientId);
        } else {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"unsupported_grant_type\"}");
        }
    }

    private void handleAuthorizationCodeGrant(HttpServletRequest req, HttpServletResponse resp,
                                               String clientId) throws IOException {
        String code = req.getParameter("code");
        String redirectUri = req.getParameter("redirect_uri");
        String codeVerifier = req.getParameter("code_verifier");

        OAuthStateStore.ProxyCode proxyCode = stateStore.consumeProxyCode(code);
        if (proxyCode == null) {
            log.warn("[MCP-SEC] Invalid/expired proxy code from {}", getClientIp(req));
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid or expired code\"}");
            return;
        }

        if (!proxyCode.clientId.equals(clientId)) {
            log.warn("[MCP-SEC] client_id mismatch on token exchange from {}", getClientIp(req));
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"client_id mismatch\"}");
            return;
        }

        // redirect_uri is mandatory per RFC 6749 Section 4.1.3
        if (redirectUri == null || !redirectUri.equals(proxyCode.redirectUri)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"redirect_uri mismatch or missing\"}");
            return;
        }

        if (!OAuthStateStore.verifyPkce(codeVerifier, proxyCode.codeChallenge, proxyCode.codeChallengeMethod)) {
            log.warn("[MCP-SEC] PKCE verification failed from {}", getClientIp(req));
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", proxyCode.accessToken);
        result.put("token_type", "bearer");
        result.put("expires_in", proxyCode.expiresIn);

        // Issue a proxy refresh token if Jira gave us one
        if (proxyCode.refreshToken != null) {
            String proxyRefreshToken = stateStore.createRefreshToken(proxyCode.refreshToken, clientId);
            if (proxyRefreshToken != null) {
                result.put("refresh_token", proxyRefreshToken);
            }
        }

        addSecurityHeaders(resp);
        mapper.writeValue(resp.getWriter(), result);
    }

    private void handleRefreshTokenGrant(HttpServletRequest req, HttpServletResponse resp,
                                          String clientId) throws IOException {
        String proxyRefreshToken = req.getParameter("refresh_token");

        if (proxyRefreshToken == null || proxyRefreshToken.isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_request\",\"error_description\":\"refresh_token is required\"}");
            return;
        }

        // Consume (one-time use — rotation per OAuth 2.1 for public clients)
        OAuthStateStore.RefreshTokenMapping mapping = stateStore.consumeRefreshToken(proxyRefreshToken);
        if (mapping == null) {
            log.warn("[MCP-SEC] Invalid/expired refresh token from {}", getClientIp(req));
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid or expired refresh token\"}");
            return;
        }

        if (!mapping.clientId.equals(clientId)) {
            log.warn("[MCP-SEC] client_id mismatch on refresh from {}", getClientIp(req));
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"client_id mismatch\"}");
            return;
        }

        // Exchange Jira refresh token for new tokens
        JiraTokenResponse jiraTokens;
        try {
            jiraTokens = refreshJiraToken(mapping.jiraRefreshToken);
        } catch (Exception e) {
            // Restore consumed token so client can retry
            stateStore.createRefreshToken(mapping.jiraRefreshToken, mapping.clientId);
            log.warn("[MCP-SEC] Jira refresh token exchange failed: {}", e.getMessage());
            resp.setStatus(502);
            resp.getWriter().write("{\"error\":\"temporarily_unavailable\",\"error_description\":\"Upstream token refresh failed\"}");
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", jiraTokens.accessToken);
        result.put("token_type", "bearer");
        result.put("expires_in", jiraTokens.expiresIn);

        // Rotate: issue new proxy refresh token mapped to Jira's (possibly rotated) refresh token
        String jiraRefresh = jiraTokens.refreshToken != null ? jiraTokens.refreshToken : mapping.jiraRefreshToken;
        String newProxyRefresh = stateStore.createRefreshToken(jiraRefresh, clientId);
        if (newProxyRefresh != null) {
            result.put("refresh_token", newProxyRefresh);
        }

        addSecurityHeaders(resp);
        mapper.writeValue(resp.getWriter(), result);
    }

    private static class JiraTokenResponse {
        final String accessToken;
        final String refreshToken; // may be null
        final int expiresIn;

        JiraTokenResponse(String accessToken, String refreshToken, int expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    private JiraTokenResponse exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String tokenUrl = getBaseUrl() + "/rest/oauth2/latest/token";
        String callbackUri = getOAuthBase() + "/callback";

        String body = "grant_type=authorization_code"
                + "&client_id=" + enc(config.getOAuthClientId())
                + "&client_secret=" + enc(config.getOAuthClientSecret())
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(callbackUri);

        return callJiraTokenEndpoint(body);
    }

    private JiraTokenResponse refreshJiraToken(String jiraRefreshToken) throws IOException, InterruptedException {
        String body = "grant_type=refresh_token"
                + "&client_id=" + enc(config.getOAuthClientId())
                + "&client_secret=" + enc(config.getOAuthClientSecret())
                + "&refresh_token=" + enc(jiraRefreshToken);

        return callJiraTokenEndpoint(body);
    }

    private JiraTokenResponse callJiraTokenEndpoint(String body) throws IOException, InterruptedException {
        String tokenUrl = getBaseUrl() + "/rest/oauth2/latest/token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode tokenNode = json.get("access_token");
        if (tokenNode == null) {
            throw new IOException("No access_token in response");
        }

        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
        int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;

        return new JiraTokenResponse(tokenNode.asText(), refreshToken, expiresIn);
    }

    // ── Security helpers ─────────────────────────────────────────────

    private static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String htmlEncode(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private static String sanitizeLog(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\\r\\n\\t]", "_");
    }

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void addSecurityHeaders(HttpServletResponse resp) {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static void sendRateLimited(HttpServletResponse resp) throws IOException {
        resp.setStatus(429);
        resp.setContentType("application/json");
        resp.setHeader("Retry-After", "60");
        resp.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
    }

    /** Read up to maxBytes from stream. Returns null if exceeded. */
    private static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        byte[] buf = new byte[Math.min(maxBytes + 1, 65537)];
        int total = 0;
        int read;
        while ((read = in.read(buf, total, buf.length - total)) > 0) {
            total += read;
            if (total > maxBytes) return null;
        }
        return Arrays.copyOf(buf, total);
    }
}
