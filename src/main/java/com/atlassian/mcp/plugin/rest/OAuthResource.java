package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.config.OAuthStateStore;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Path("/oauth")
public class OAuthResource {

    private final McpPluginConfig config;
    private final OAuthStateStore stateStore;
    private final ApplicationProperties applicationProperties;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public OAuthResource(
            McpPluginConfig config,
            OAuthStateStore stateStore,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.stateStore = stateStore;
        this.applicationProperties = applicationProperties;
    }

    private String getJiraBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) return override;
        return applicationProperties.getBaseUrl().toString();
    }

    private String getMcpBaseUrl() {
        return getJiraBaseUrl() + "/rest/mcp/1.0";
    }

    private String getOAuthIssuer() {
        return getMcpBaseUrl() + "/oauth";
    }

    // ==================== Metadata Endpoints ====================

    @GET
    @Path("/protected-resource")
    @Produces(MediaType.APPLICATION_JSON)
    public Response protectedResourceMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resource", getMcpBaseUrl() + "/");
        meta.put("authorization_servers", List.of(getOAuthIssuer()));
        return Response.ok(meta).build();
    }

    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authorizationServerMetadata() {
        if (!config.isOAuthEnabled()) {
            return Response.status(404).entity("{\"error\":\"OAuth not configured\"}").build();
        }
        String issuer = getOAuthIssuer();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("issuer", issuer);
        meta.put("authorization_endpoint", issuer + "/authorize");
        meta.put("token_endpoint", issuer + "/token");
        meta.put("registration_endpoint", issuer + "/register");
        meta.put("response_types_supported", List.of("code"));
        meta.put("grant_types_supported", List.of("authorization_code"));
        meta.put("token_endpoint_auth_methods_supported", List.of("none"));
        meta.put("code_challenge_methods_supported", List.of("S256"));
        meta.put("scopes_supported", List.of("WRITE", "READ"));
        return Response.ok(meta).build();
    }

    // ==================== Authorization Flow ====================

    @GET
    @Path("/authorize")
    public Response authorize(
            @QueryParam("client_id") String clientId,
            @QueryParam("redirect_uri") String redirectUri,
            @QueryParam("state") String state,
            @QueryParam("code_challenge") String codeChallenge,
            @QueryParam("code_challenge_method") String codeChallengeMethod,
            @QueryParam("scope") String scope,
            @QueryParam("response_type") String responseType) {

        if (!config.isOAuthEnabled()) {
            return Response.status(400).entity("OAuth not configured").build();
        }

        // Validate registered client and redirect_uri
        OAuthStateStore.RegisteredClient client = stateStore.getClient(clientId);
        if (client == null) {
            return Response.status(400).entity("Unknown client_id").build();
        }
        if (redirectUri != null && !client.redirectUris.isEmpty()) {
            boolean allowed = client.redirectUris.stream().anyMatch(
                    uri -> redirectUri.startsWith(uri.replaceAll("\\*$", "")));
            if (!allowed) {
                return Response.status(400).entity("Invalid redirect_uri").build();
            }
        }

        // Store client's auth request
        String internalState = stateStore.createPendingAuth(
                redirectUri, state, codeChallenge, codeChallengeMethod, clientId);

        // Build Jira OAuth authorize URL
        String jiraAuthorize = getJiraBaseUrl() + "/rest/oauth2/latest/authorize";
        String callbackUri = getMcpBaseUrl() + "/oauth/callback";

        String url = jiraAuthorize
                + "?client_id=" + enc(config.getOAuthClientId())
                + "&redirect_uri=" + enc(callbackUri)
                + "&response_type=code"
                + "&scope=" + enc(scope != null ? scope : "WRITE")
                + "&state=" + enc(internalState);

        return Response.temporaryRedirect(URI.create(url)).build();
    }

    @GET
    @Path("/callback")
    public Response callback(
            @QueryParam("code") String jiraCode,
            @QueryParam("state") String internalState,
            @QueryParam("error") String error) {

        if (error != null) {
            return Response.status(400).entity("OAuth error: " + error).build();
        }

        OAuthStateStore.PendingAuth pending = stateStore.consumePendingAuth(internalState);
        if (pending == null) {
            return Response.status(400).entity("Invalid or expired state").build();
        }

        // Exchange Jira auth code for access token
        String accessToken;
        try {
            accessToken = exchangeCodeForToken(jiraCode);
        } catch (Exception e) {
            return Response.status(500).entity("Token exchange failed: " + e.getMessage()).build();
        }

        // Create proxy code bound to client_id, redirect_uri, and PKCE challenge
        String proxyCode = stateStore.createProxyCode(accessToken,
                pending.clientId, pending.clientRedirectUri,
                pending.codeChallenge, pending.codeChallengeMethod);

        // Redirect to client's localhost callback
        String clientCallback = pending.clientRedirectUri
                + (pending.clientRedirectUri.contains("?") ? "&" : "?")
                + "code=" + enc(proxyCode)
                + "&state=" + enc(pending.clientState);

        return Response.temporaryRedirect(URI.create(clientCallback)).build();
    }

    private String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String tokenUrl = getJiraBaseUrl() + "/rest/oauth2/latest/token";
        String callbackUri = getMcpBaseUrl() + "/oauth/callback";

        String body = "grant_type=authorization_code"
                + "&client_id=" + enc(config.getOAuthClientId())
                + "&client_secret=" + enc(config.getOAuthClientSecret())
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(callbackUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Token exchange failed: HTTP " + response.statusCode() + " " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode tokenNode = json.get("access_token");
        if (tokenNode == null) {
            throw new IOException("No access_token in response: " + response.body());
        }
        return tokenNode.asText();
    }

    static String enc(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
