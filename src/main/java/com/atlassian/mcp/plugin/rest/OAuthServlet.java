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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * OAuth 2.0 proxy servlet. Served at /plugins/servlet/mcp-oauth/*.
 * Uses @UnrestrictedAccess (Jira 10 secure endpoint defaults) to allow anonymous access.
 * Combined with before-login filter to prevent login redirect.
 */
@UnrestrictedAccess
public class OAuthServlet extends HttpServlet {

    private final McpPluginConfig config;
    private final OAuthStateStore stateStore;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Inject
    public OAuthServlet(
            McpPluginConfig config,
            OAuthStateStore stateStore,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.stateStore = stateStore;
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

        if (path.equals("/protected-resource") || path.equals("/protected-resource/")) {
            resp.setContentType("application/json");
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("resource", getBaseUrl() + "/rest/mcp/1.0/");
            meta.put("authorization_servers", List.of(getOAuthBase()));
            mapper.writeValue(resp.getWriter(), meta);

        } else if (path.equals("/metadata") || path.equals("/metadata/")) {
            resp.setContentType("application/json");
            // config is injected via constructor
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
            meta.put("grant_types_supported", List.of("authorization_code"));
            meta.put("token_endpoint_auth_methods_supported", List.of("none"));
            meta.put("code_challenge_methods_supported", List.of("S256"));
            meta.put("scopes_supported", List.of("WRITE", "READ"));
            mapper.writeValue(resp.getWriter(), meta);

        } else if (path.startsWith("/authorize")) {
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
        resp.setContentType("application/json");

        if (path.equals("/register") || path.equals("/register/")) {
            handleRegister(req, resp);
        } else if (path.equals("/token") || path.equals("/token/")) {
            handleToken(req, resp);
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // config is injected via constructor
        if (!config.isOAuthEnabled()) {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"OAuth not configured\"}");
            return;
        }
        Map<String, Object> body = mapper.readValue(req.getInputStream(), Map.class);
        String clientName = (String) body.getOrDefault("client_name", "MCP Client");
        List<String> redirectUris = (List<String>) body.getOrDefault("redirect_uris", List.of());

        OAuthStateStore.RegisteredClient client = stateStore.registerClient(clientName, redirectUris);

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
        // config is injected via constructor
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

        OAuthStateStore store = stateStore;
        OAuthStateStore.RegisteredClient client = store.getClient(clientId);
        if (client == null) {
            resp.setStatus(400);
            resp.getWriter().write("Unknown client_id");
            return;
        }

        String internalState = store.createPendingAuth(
                redirectUri, state, codeChallenge, codeChallengeMethod, clientId);

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
            resp.setContentType("text/html");
            resp.setStatus(400);
            resp.getWriter().write("<h1>OAuth Error</h1><p>" + error + "</p>");
            return;
        }

        String jiraCode = req.getParameter("code");
        String internalState = req.getParameter("state");

        OAuthStateStore store = stateStore;
        OAuthStateStore.PendingAuth pending = store.consumePendingAuth(internalState);
        if (pending == null) {
            resp.setContentType("text/html");
            resp.setStatus(400);
            resp.getWriter().write("<h1>Error</h1><p>Invalid or expired state</p>");
            return;
        }

        String accessToken;
        try {
            accessToken = exchangeCodeForToken(jiraCode);
        } catch (Exception e) {
            resp.setContentType("text/html");
            resp.setStatus(500);
            resp.getWriter().write("<h1>Error</h1><p>Token exchange failed: " + e.getMessage() + "</p>");
            return;
        }

        String proxyCode = store.createProxyCode(accessToken,
                pending.clientId, pending.clientRedirectUri,
                pending.codeChallenge, pending.codeChallengeMethod);

        String clientCallback = pending.clientRedirectUri
                + (pending.clientRedirectUri.contains("?") ? "&" : "?")
                + "code=" + enc(proxyCode)
                + "&state=" + enc(pending.clientState);

        resp.sendRedirect(clientCallback);
    }

    private void handleToken(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String grantType = req.getParameter("grant_type");
        String code = req.getParameter("code");
        String redirectUri = req.getParameter("redirect_uri");
        String clientId = req.getParameter("client_id");
        String codeVerifier = req.getParameter("code_verifier");

        if (!"authorization_code".equals(grantType)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"unsupported_grant_type\"}");
            return;
        }

        OAuthStateStore.ProxyCode proxyCode = stateStore.consumeProxyCode(code);
        if (proxyCode == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid or expired code\"}");
            return;
        }

        if (!proxyCode.clientId.equals(clientId)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"client_id mismatch\"}");
            return;
        }

        if (redirectUri != null && !redirectUri.equals(proxyCode.redirectUri)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"redirect_uri mismatch\"}");
            return;
        }

        if (!OAuthStateStore.verifyPkce(codeVerifier, proxyCode.codeChallenge, proxyCode.codeChallengeMethod)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", proxyCode.accessToken);
        result.put("token_type", "bearer");
        result.put("expires_in", 3600);
        mapper.writeValue(resp.getWriter(), result);
    }

    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        // config is injected via constructor
        String tokenUrl = getBaseUrl() + "/rest/oauth2/latest/token";
        String callbackUri = getOAuthBase() + "/callback";

        String body = "grant_type=authorization_code"
                + "&client_id=" + enc(config.getOAuthClientId())
                + "&client_secret=" + enc(config.getOAuthClientSecret())
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(callbackUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode tokenNode = json.get("access_token");
        if (tokenNode == null) {
            throw new IOException("No access_token in response");
        }
        return tokenNode.asText();
    }

    private static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
