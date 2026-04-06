# OAuth 2.0 Proxy Implementation Plan

> **For agentic workers:** Use subagent-driven-development (recommended) or executing-plans skill to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users authenticate to MCP via OAuth instead of PATs — click "Authenticate" in Claude Code, consent in browser, done.

**Architecture:** The plugin acts as an OAuth Authorization Server proxy. MCP clients treat our plugin as the AS. Our plugin proxies the authorization flow to Jira's built-in OAuth 2.0 endpoints at `/rest/oauth2/latest/`. We bridge the client's localhost callback with Jira's Application Link callback. No external sidecar needed.

**Tech Stack:** Java 17, JAX-RS (javax.ws.rs), Jira DC 10.7.4 OAuth 2.0, MCP Protocol 2025-06-18

**Build command:** `atlas-mvn package -DskipTests`

**Test command:** `atlas-mvn test`

**Deploy:** Upload JAR via UPM REST API (version bump required for CSS/JS cache busting)

**Important conventions (from CLAUDE.md):**
- Always use `javax.*` imports, never `jakarta.*`
- Plugin key is `com.atlassian.mcp.atlassian-mcp-plugin`
- Use Jackson `ObjectMapper` for JSON parsing (already on classpath via `com.fasterxml.jackson`)

---

## End-User Experience (Target)

```json
{
  "mcpServers": {
    "jira": {
      "type": "http",
      "url": "https://bpm.astrateam.net/rest/mcp/1.0/"
    }
  }
}
```

1. User adds config → Claude Code shows "Needs Auth"
2. Click "Authenticate" → browser opens Jira consent page
3. Click "Allow" → "Authentication Successful" → close browser
4. Claude Code has the token, all 46 tools work

## OAuth Flow (detailed)

```
Claude Code                     Our Plugin                          Jira OAuth
    |                               |                                   |
    |-- POST /rest/mcp/1.0/ ------>|                                   |
    |<-- 401 + WWW-Authenticate ---|                                   |
    |   resource_metadata=".../.well-known/oauth-protected-resource"   |
    |                               |                                   |
    |-- GET /.well-known/           |                                   |
    |   oauth-protected-resource -->|                                   |
    |<-- {resource, auth_servers} --|                                   |
    |                               |                                   |
    |-- GET /.well-known/           |                                   |
    |   oauth-authorization-server->|                                   |
    |<-- metadata JSON ------------|                                   |
    |                               |                                   |
    |-- POST /oauth/register ------>|                                   |
    |<-- client_id -----------------|                                   |
    |                               |                                   |
    |== opens browser =============|                                   |
    |-- GET /oauth/authorize ------>|                                   |
    |   ?client_id=X               |-- redirect ---------------------->|
    |   &redirect_uri=localhost     |   ?client_id=JIRA_ID              |
    |   &code_challenge=Y          |   &redirect_uri=.../oauth/callback|
    |   &state=Z                   |   &scope=WRITE                    |
    |                               |                                   |
    |                               |          [User consents]          |
    |                               |                                   |
    |                               |<-- redirect with code ------------|
    |                               |   /oauth/callback?code=JIRA_CODE  |
    |                               |                                   |
    |                               |-- POST /rest/oauth2/latest/token->|
    |                               |   grant_type=authorization_code   |
    |                               |   code=JIRA_CODE                  |
    |                               |   client_secret=JIRA_SECRET       |
    |                               |<-- access_token ------------------|
    |                               |                                   |
    |<-- redirect to localhost -----|                                   |
    |   ?code=PROXY_CODE&state=Z   |                                   |
    |                               |                                   |
    |-- POST /oauth/token --------->|                                   |
    |   grant_type=authorization_code                                   |
    |   code=PROXY_CODE             |                                   |
    |   code_verifier=VERIFIER      |                                   |
    |<-- access_token --------------|                                   |
    |                               |                                   |
    |== uses Bearer token ==========|                                   |
    |-- POST /rest/mcp/1.0/ ------>|  (Jira validates token)           |
    |<-- MCP response --------------|                                   |
```

## File Structure

| File | Responsibility |
|------|---------------|
| `rest/OAuthResource.java` | **NEW** — OAuth proxy endpoints: metadata, register, authorize, callback, token |
| `rest/OAuthWellKnownResource.java` | **NEW** — `/.well-known/oauth-protected-resource` and `/.well-known/oauth-authorization-server` |
| `config/McpPluginConfig.java` | **MODIFY** — Add OAuth client_id and client_secret settings |
| `config/OAuthStateStore.java` | **NEW** — In-memory store for pending auth states and proxy codes |
| `rest/McpResource.java` | **MODIFY** — Return 401 with RFC 9728 `WWW-Authenticate` header |
| `admin/ConfigResource.java` | **MODIFY** — Expose OAuth config in GET/PUT |
| `atlassian-plugin.xml` | **MODIFY** — Register well-known servlet for `/.well-known/*` paths |
| `templates/admin.vm` | **MODIFY** — Add OAuth tab to admin UI |
| `js/admin.js` | **MODIFY** — OAuth config fields in admin JS |

## Pre-implementation: Verify Jira OAuth Bearer → getRemoteUser()

Before implementing, we must verify that Jira DC maps OAuth 2.0 Bearer tokens to `UserManager.getRemoteUser(request)` for plugin REST endpoints. This is how PATs already work. Test:

```bash
# Get an OAuth token via the existing sidecar flow, then:
curl -s -X POST "https://bpm.astrateam.net/rest/mcp/1.0/" \
  -H "Authorization: Bearer <OAUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
```

If this returns a valid MCP response (not 401), then `getRemoteUser()` works with OAuth tokens and no additional code is needed in McpResource for user resolution.

If it returns 401, we need to add explicit token validation using Jira's `com.atlassian.oauth2.provider` API.

---

### Task 1: OAuth State Store

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/config/OAuthStateStore.java`

This stores three types of short-lived data:
1. **Pending authorizations** — maps our internal state → client's redirect_uri + state + code_challenge + client_id
2. **Proxy codes** — maps our proxy authorization code → Jira access token + bound client_id + redirect_uri
3. **Registered clients** — maps proxy client_id → client metadata (redirect_uris)

All expire after 10 minutes (except registered clients which persist in memory for the plugin lifecycle).

- [ ] **Step 1: Create OAuthStateStore**

```java
package com.atlassian.mcp.plugin.config;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Named
public class OAuthStateStore {

    private static final long EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    public static class PendingAuth {
        public final String clientRedirectUri;
        public final String clientState;
        public final String codeChallenge;
        public final String codeChallengeMethod;
        public final String clientId;
        public final long createdAt;

        public PendingAuth(String clientRedirectUri, String clientState,
                           String codeChallenge, String codeChallengeMethod, String clientId) {
            this.clientRedirectUri = clientRedirectUri;
            this.clientState = clientState;
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.clientId = clientId;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    public static class ProxyCode {
        public final String accessToken;
        public final String clientId;
        public final String redirectUri;
        public final String codeChallenge;
        public final String codeChallengeMethod;
        public final long createdAt;

        public ProxyCode(String accessToken, String clientId, String redirectUri,
                         String codeChallenge, String codeChallengeMethod) {
            this.accessToken = accessToken;
            this.clientId = clientId;
            this.redirectUri = redirectUri;
            this.codeChallenge = codeChallenge;
            this.codeChallengeMethod = codeChallengeMethod;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    public static class RegisteredClient {
        public final String clientId;
        public final String clientName;
        public final List<String> redirectUris;

        public RegisteredClient(String clientId, String clientName, List<String> redirectUris) {
            this.clientId = clientId;
            this.clientName = clientName;
            this.redirectUris = redirectUris;
        }
    }

    private final Map<String, PendingAuth> pendingAuths = new ConcurrentHashMap<>();
    private final Map<String, ProxyCode> proxyCodes = new ConcurrentHashMap<>();
    private final Map<String, RegisteredClient> registeredClients = new ConcurrentHashMap<>();

    public String createPendingAuth(String clientRedirectUri, String clientState,
                                     String codeChallenge, String codeChallengeMethod, String clientId) {
        cleanup();
        String internalState = UUID.randomUUID().toString();
        pendingAuths.put(internalState, new PendingAuth(clientRedirectUri, clientState,
                codeChallenge, codeChallengeMethod, clientId));
        return internalState;
    }

    public PendingAuth consumePendingAuth(String internalState) {
        PendingAuth auth = pendingAuths.remove(internalState);
        return (auth != null && !auth.isExpired()) ? auth : null;
    }

    public String createProxyCode(String accessToken, String clientId, String redirectUri,
                                   String codeChallenge, String codeChallengeMethod) {
        cleanup();
        String code = UUID.randomUUID().toString();
        proxyCodes.put(code, new ProxyCode(accessToken, clientId, redirectUri,
                codeChallenge, codeChallengeMethod));
        return code;
    }

    public ProxyCode consumeProxyCode(String code) {
        ProxyCode pc = proxyCodes.remove(code);
        return (pc != null && !pc.isExpired()) ? pc : null;
    }

    public RegisteredClient registerClient(String clientName, List<String> redirectUris) {
        String clientId = UUID.randomUUID().toString();
        RegisteredClient client = new RegisteredClient(clientId, clientName, redirectUris);
        registeredClients.put(clientId, client);
        return client;
    }

    public RegisteredClient getClient(String clientId) {
        return registeredClients.get(clientId);
    }

    /** Verify PKCE code_verifier against stored code_challenge */
    public static boolean verifyPkce(String codeVerifier, String codeChallenge, String method) {
        if (codeChallenge == null || codeChallenge.isEmpty()) {
            return true; // no PKCE was used
        }
        if (codeVerifier == null || codeVerifier.isEmpty()) {
            return false; // challenge was set but no verifier provided
        }
        if ("plain".equals(method)) {
            return codeChallenge.equals(codeVerifier);
        }
        // S256: BASE64URL(SHA256(code_verifier)) == code_challenge
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return codeChallenge.equals(computed);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private void cleanup() {
        pendingAuths.entrySet().removeIf(e -> e.getValue().isExpired());
        proxyCodes.entrySet().removeIf(e -> e.getValue().isExpired());
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/config/OAuthStateStore.java
git commit -m "feat: add OAuth state store with PKCE verification"
```

---

### Task 2: OAuth Plugin Config

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java`

Add OAuth client_id and client_secret to PluginSettings. The callback URL is always derived as `${baseUrl}/rest/mcp/1.0/oauth/callback` — not configurable.

- [ ] **Step 1: Add OAuth config methods to McpPluginConfig**

Add these methods after the existing `setJiraBaseUrlOverride`:

```java
    // OAuth 2.0 configuration
    public String getOAuthClientId() {
        String val = (String) settings().get(PREFIX + "oauthClientId");
        return val == null ? "" : val;
    }

    public void setOAuthClientId(String clientId) {
        settings().put(PREFIX + "oauthClientId", clientId);
    }

    public String getOAuthClientSecret() {
        String val = (String) settings().get(PREFIX + "oauthClientSecret");
        return val == null ? "" : val;
    }

    public void setOAuthClientSecret(String secret) {
        settings().put(PREFIX + "oauthClientSecret", secret);
    }

    public boolean isOAuthEnabled() {
        String id = getOAuthClientId();
        String secret = getOAuthClientSecret();
        return id != null && !id.isEmpty() && secret != null && !secret.isEmpty();
    }
```

- [ ] **Step 2: Build to verify**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/config/McpPluginConfig.java
git commit -m "feat: add OAuth client_id/secret to plugin config"
```

---

### Task 3: OAuth Metadata Endpoints

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/OAuthResource.java` (will be created in Task 4)

Jira servlet modules serve under `/plugins/servlet/`, NOT arbitrary paths. JAX-RS `@Path` doesn't support dots. So we serve metadata from clean JAX-RS paths under `/oauth/`:

- `/rest/mcp/1.0/oauth/protected-resource` — RFC 9728 Protected Resource Metadata
- `/rest/mcp/1.0/oauth/metadata` — RFC 8414 Authorization Server Metadata

The `WWW-Authenticate` header in Task 6 and the `authorization_servers` field both use the same consistent identifier: `{baseUrl}/rest/mcp/1.0/oauth` as the authorization server.

**Note:** This task's code is included in Task 4 (OAuthResource.java) which creates the full file. The metadata endpoints are `@GET /oauth/protected-resource` and `@GET /oauth/metadata` in OAuthResource.

- [ ] **Step 1: Design metadata responses**

Protected Resource Metadata (`GET /rest/mcp/1.0/oauth/protected-resource`):
```json
{
    "resource": "https://bpm.astrateam.net/rest/mcp/1.0/",
    "authorization_servers": ["https://bpm.astrateam.net/rest/mcp/1.0/oauth"]
}
```

Authorization Server Metadata (`GET /rest/mcp/1.0/oauth/metadata`):
```json
{
    "issuer": "https://bpm.astrateam.net/rest/mcp/1.0/oauth",
    "authorization_endpoint": "https://bpm.astrateam.net/rest/mcp/1.0/oauth/authorize",
    "token_endpoint": "https://bpm.astrateam.net/rest/mcp/1.0/oauth/token",
    "registration_endpoint": "https://bpm.astrateam.net/rest/mcp/1.0/oauth/register",
    "response_types_supported": ["code"],
    "grant_types_supported": ["authorization_code"],
    "token_endpoint_auth_methods_supported": ["none"],
    "code_challenge_methods_supported": ["S256"],
    "scopes_supported": ["WRITE", "READ"]
}
```

The key consistency rule: `authorization_servers[0]` == `issuer` == `{baseUrl}/rest/mcp/1.0/oauth`.

- [ ] **Step 2: No separate commit** — metadata endpoints are part of OAuthResource created in Task 4.

---

### Task 4: OAuth Authorize + Callback Endpoints

**Files:**
- Create: `src/main/java/com/atlassian/mcp/plugin/rest/OAuthResource.java`

This class handles `/oauth/authorize` and `/oauth/callback`. It goes in the existing `com.atlassian.mcp.plugin.rest` package alongside `McpResource` — no new `<rest>` module needed, they share the same `/rest/mcp/1.0/` base path.

- [ ] **Step 1: Create OAuthResource with authorize and callback**

```java
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
```

- [ ] **Step 2: Build to verify**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/rest/OAuthResource.java
git commit -m "feat: add OAuth authorize and callback endpoints"
```

---

### Task 5: OAuth Token + Register Endpoints

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/OAuthResource.java`

Add `/oauth/token` and `/oauth/register` to the existing OAuthResource.

- [ ] **Step 1: Add register endpoint**

Add to OAuthResource:

```java
    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(Map<String, Object> body) {
        if (!config.isOAuthEnabled()) {
            return Response.status(404).entity("{\"error\":\"OAuth not configured\"}").build();
        }

        String clientName = (String) body.getOrDefault("client_name", "MCP Client");
        @SuppressWarnings("unchecked")
        List<String> redirectUris = (List<String>) body.getOrDefault("redirect_uris", List.of());

        OAuthStateStore.RegisteredClient client = stateStore.registerClient(clientName, redirectUris);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("client_id", client.clientId);
        resp.put("client_name", client.clientName);
        resp.put("redirect_uris", client.redirectUris);
        resp.put("grant_types", List.of("authorization_code"));
        resp.put("response_types", List.of("code"));
        resp.put("token_endpoint_auth_method", "none");
        return Response.status(201).entity(resp).build();
    }
```

- [ ] **Step 2: Add token endpoint with PKCE verification**

Add to OAuthResource:

```java
    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(
            @FormParam("grant_type") String grantType,
            @FormParam("code") String code,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("client_id") String clientId,
            @FormParam("code_verifier") String codeVerifier) {

        if (!"authorization_code".equals(grantType)) {
            return Response.status(400)
                    .entity("{\"error\":\"unsupported_grant_type\"}").build();
        }

        OAuthStateStore.ProxyCode proxyCode = stateStore.consumeProxyCode(code);
        if (proxyCode == null) {
            return Response.status(400)
                    .entity("{\"error\":\"invalid_grant\",\"error_description\":\"Invalid or expired code\"}").build();
        }

        // Validate client_id matches
        if (!proxyCode.clientId.equals(clientId)) {
            return Response.status(400)
                    .entity("{\"error\":\"invalid_grant\",\"error_description\":\"client_id mismatch\"}").build();
        }

        // Validate redirect_uri matches
        if (redirectUri != null && !redirectUri.equals(proxyCode.redirectUri)) {
            return Response.status(400)
                    .entity("{\"error\":\"invalid_grant\",\"error_description\":\"redirect_uri mismatch\"}").build();
        }

        // Verify PKCE
        if (!OAuthStateStore.verifyPkce(codeVerifier, proxyCode.codeChallenge, proxyCode.codeChallengeMethod)) {
            return Response.status(400)
                    .entity("{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}").build();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("access_token", proxyCode.accessToken);
        resp.put("token_type", "bearer");
        resp.put("expires_in", 3600);
        return Response.ok(resp).build();
    }
```

- [ ] **Step 3: Build to verify**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/rest/OAuthResource.java
git commit -m "feat: add OAuth token endpoint with PKCE and register endpoint"
```

---

### Task 6: Update McpResource 401 Response

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java`

Return RFC 9728 compliant `WWW-Authenticate` header when OAuth is enabled and no auth is provided.

- [ ] **Step 1: Add ApplicationProperties injection**

Update the constructor to add `ApplicationProperties`:

```java
    private final ApplicationProperties applicationProperties;

    @Inject
    public McpResource(
            JsonRpcHandler handler,
            McpPluginConfig config,
            @ComponentImport UserManager userManager,
            @ComponentImport GroupManager groupManager,
            @ComponentImport ApplicationProperties applicationProperties) {
        this.handler = handler;
        this.config = config;
        this.userManager = userManager;
        this.groupManager = groupManager;
        this.applicationProperties = applicationProperties;
    }

    private String getJiraBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) return override;
        return applicationProperties.getBaseUrl().toString();
    }
```

- [ ] **Step 2: Update the null user response**

Replace the existing `user == null` block:

```java
        if (user == null) {
            if (config.isOAuthEnabled()) {
                String resourceMetadata = getJiraBaseUrl()
                        + "/rest/mcp/1.0/oauth/protected-resource";
                return Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate",
                                "Bearer resource_metadata=\"" + resourceMetadata + "\"")
                        .entity("{\"error\":\"Authentication required\"}")
                        .build();
            }
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Authentication required. Provide a Bearer token (PAT or OAuth).\"}")
                    .build();
        }
```

- [ ] **Step 3: Build to verify**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/rest/McpResource.java
git commit -m "feat: return RFC 9728 WWW-Authenticate with resource_metadata on 401"
```

---

### Task 7: Admin UI — OAuth Config Tab

**Files:**
- Modify: `src/main/java/com/atlassian/mcp/plugin/admin/ConfigResource.java`
- Modify: `src/main/resources/templates/admin.vm`
- Modify: `src/main/resources/js/admin.js`

- [ ] **Step 1: Add OAuth fields to ConfigResource GET/PUT**

In the GET handler, add after `result.put("jiraBaseUrl", ...)`:

```java
        result.put("oauthClientId", config.getOAuthClientId());
        String secret = config.getOAuthClientSecret();
        result.put("oauthClientSecretSet", secret != null && !secret.isEmpty());
        result.put("oauthEnabled", config.isOAuthEnabled());
```

In the PUT handler, add:

```java
        if (body.containsKey("oauthClientId")) {
            config.setOAuthClientId(body.get("oauthClientId").toString());
        }
        if (body.containsKey("oauthClientSecret")) {
            String secret = body.get("oauthClientSecret").toString();
            if (!secret.isEmpty()) {
                config.setOAuthClientSecret(secret);
            }
        }
```

- [ ] **Step 2: Add OAuth tab to admin.vm**

Add a new tab item in the `<ul class="tabs-menu">`:

```html
<li class="menu-item"><a href="#mcp-tab-oauth"><strong>OAuth</strong></a></li>
```

Add the tab pane after the Tools pane:

```html
            <!-- OAuth tab -->
            <div class="tabs-pane" id="mcp-tab-oauth">
                <p class="description" style="margin-bottom:16px;">
                    Configure OAuth 2.0 so users can authenticate via browser consent instead of PATs.
                    Create an incoming Application Link in Jira admin first, then enter the credentials here.
                </p>
                <div class="field-group">
                    <label for="oauthClientId">Client ID</label>
                    <input type="text" id="oauthClientId" class="text long-field">
                    <div class="description">From Application Links &gt; your incoming link &gt; Client ID</div>
                </div>
                <div class="field-group">
                    <label for="oauthClientSecret">Client Secret</label>
                    <input type="password" id="oauthClientSecret" class="text long-field"
                           placeholder="Enter new secret (leave empty to keep current)">
                    <div class="description" id="oauth-secret-status"></div>
                </div>
                <div class="field-group" id="oauth-status-group" style="display:none;">
                    <label>Status</label>
                    <div id="oauth-status"></div>
                </div>
                <div class="field-group">
                    <label>Callback URL (set in Application Link)</label>
                    <pre id="oauth-callback-url" style="background:#1b2638; padding:12px; border-radius:3px; font-size:12px;"></pre>
                    <div class="description">Set this as the Redirect URL when creating the incoming Application Link.</div>
                </div>
                <div class="field-group">
                    <label>MCP Config for Users</label>
                    <pre id="oauth-mcp-config" style="background:#1b2638; padding:12px; border-radius:3px; font-size:12px;"></pre>
                    <div class="description">Users paste this into their Claude Code / Cursor MCP settings.</div>
                </div>
            </div>
```

- [ ] **Step 3: Update admin.js**

In the config load callback, after loading existing fields:

```javascript
            // OAuth
            $("#oauthClientId").val(config.oauthClientId || "");
            if (config.oauthClientSecretSet) {
                $("#oauth-secret-status").text("Secret is configured.");
            } else {
                $("#oauth-secret-status").text("No secret configured yet.");
            }
            if (config.oauthEnabled) {
                $("#oauth-status-group").show();
                $("#oauth-status").html('<span class="aui-lozenge aui-lozenge-success">Active</span>');
            }
            var baseUrl = window.location.origin + AJS.contextPath();
            var mcpUrl = baseUrl + "/rest/mcp/1.0/";
            $("#oauth-callback-url").text(mcpUrl + "oauth/callback");
            $("#oauth-mcp-config").text(JSON.stringify({
                "mcpServers": {
                    "jira": { "type": "http", "url": mcpUrl }
                }
            }, null, 2));
```

In the save handler, add to the JSON body:

```javascript
                    oauthClientId: $("#oauthClientId").val(),
                    oauthClientSecret: $("#oauthClientSecret").val()
```

- [ ] **Step 4: Build to verify**

Run: `atlas-mvn package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/atlassian/mcp/plugin/admin/ConfigResource.java
git add src/main/resources/templates/admin.vm
git add src/main/resources/js/admin.js
git commit -m "feat: add OAuth tab to admin UI with client_id/secret config"
```

---

### Task 8: Version Bump, Deploy & End-to-End Test

**Files:**
- Modify: `pom.xml` — bump to `1.0.2-SNAPSHOT`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Bump version in pom.xml**

Change version to `1.0.2-SNAPSHOT`.

- [ ] **Step 2: Update CHANGELOG.md**

Add entry:

```markdown
## [1.0.2] - 2026-04-06

### Added

- **OAuth 2.0 proxy** — users authenticate via browser consent instead of PATs
- RFC 9728 protected resource metadata + RFC 8414 authorization server metadata
- OAuth endpoints: register (DCR), authorize, callback, token (with PKCE verification)
- OAuth tab in admin UI with client_id/secret config and user-facing MCP config snippet
- 401 response with `resource_metadata` link when no auth token provided
- PKCE (S256) support — proxy codes bound to client_id, redirect_uri, and code_challenge
```

- [ ] **Step 3: Build and deploy**

```bash
atlas-mvn package -DskipTests
```

Upload `target/atlassian-mcp-plugin-1.0.2-SNAPSHOT.jar` via UPM.

- [ ] **Step 4: Configure Application Link**

In Jira Admin → Application Links → Create Link → External Application → Incoming:
1. Name: `MCP Server`
2. Redirect URL: `https://bpm.astrateam.net/rest/mcp/1.0/oauth/callback`
3. Permission: Write
4. Save → copy Client ID and Client Secret

- [ ] **Step 5: Configure plugin OAuth tab**

In MCP Admin → OAuth tab:
1. Paste Client ID
2. Paste Client Secret
3. Save
4. Verify "Active" status badge appears

- [ ] **Step 6: Test end-to-end**

Configure Claude Code / Cursor:

```json
{
  "mcpServers": {
    "jira": {
      "type": "http",
      "url": "https://bpm.astrateam.net/rest/mcp/1.0/"
    }
  }
}
```

Expected flow:
1. "Needs Auth" appears → click "Authenticate"
2. Browser opens to Jira consent page ("MCP Server would like to access your Jira account")
3. Click "Allow"
4. "Authentication Successful" → close browser
5. MCP tools work

- [ ] **Step 7: Commit**

```bash
git add pom.xml CHANGELOG.md
git commit -m "chore: bump to 1.0.2-SNAPSHOT with OAuth support"
```

---

## Notes

- **PAT auth still works** — OAuth is additive, not a replacement. Both Bearer token types (PAT and OAuth) are validated by Jira's container auth automatically.
- **Jira DC token expiry** — tokens expire (typically 1 hour). Re-auth needed when expired.
- **Security model:**
  - `client_secret` never leaves the server (only used server→Jira for token exchange)
  - Proxy authorization codes are single-use, bound to client_id + redirect_uri, expire in 10 min
  - PKCE (S256) verified on proxy code redemption
  - Registered client redirect_uris validated on authorize
- **Callback URL** — must be set to `${jiraBaseUrl}/rest/mcp/1.0/oauth/callback` in the Application Link. This is NOT the same as the sidecar's callback URL.
- **Well-known paths** — served via Jira servlet (not JAX-RS) because JAX-RS `@Path` doesn't support dots in path segments.
