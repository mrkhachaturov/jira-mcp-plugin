package com.atlassian.mcp.plugin.e2e;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * End-to-end tests for the Jira MCP plugin running on a live Jira instance.
 *
 * <p>All MCP protocol concerns (JSON-RPC framing, session ids, SSE wrapping, Accept negotiation,
 * MCP-Protocol-Version) are owned by the official MCP Java SDK ({@link McpSyncClient}). These tests
 * do NOT re-verify any of that — that would only re-test the SDK. Instead we assert on real plugin
 * behaviour that the SDK cannot own:
 *
 * <ul>
 *   <li>Server identity and tool registry contents
 *   <li>Live Jira data returned by read tools (real users, projects, issues)
 *   <li>{@code ResponseTrimmer} strips verbose fields
 *   <li>MCP Apps wiring: {@code _meta.ui.resourceUri} on tools that link to the issue-card widget;
 *       non-null {@code structuredContent}
 *   <li>Schema validation rejects calls with missing required parameters
 *   <li>Anonymous access works for OAuth metadata + Dynamic Client Registration endpoints (verifies
 *       {@code OAuthAnonymousFilter} + {@code @UnrestrictedAccess})
 * </ul>
 *
 * <p>Required env vars (skipped cleanly when absent):
 *
 * <pre>
 *   JIRA_URL          — e.g. https://bpm.astrateam.net
 *   JIRA_PAT_RKADMIN  — PAT for an admin user with MCP access
 * </pre>
 *
 * <p>Optional:
 *
 * <pre>
 *   JIRA_PROJECT_KEY  — project key for search tool test (default: TES)
 * </pre>
 *
 * <p>Run: {@code just e2e} <br>
 * Or: {@code source .credentials/jira.env && atlas-mvn test -Dtest=McpEndpointE2ETest}
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class McpEndpointE2ETest {

  // --- environment --------------------------------------------------------

  private static final String JIRA_URL = System.getenv("JIRA_URL");
  private static final String JIRA_PAT = System.getenv("JIRA_PAT_RKADMIN");
  private static final String PROJECT_KEY = System.getenv().getOrDefault("JIRA_PROJECT_KEY", "TES");

  /** MCP servlet path (see atlassian-plugin.xml). */
  private static final String MCP_ENDPOINT = "/plugins/servlet/mcp";

  /** Per-call timeout — must remain well below surefire fork timeout (180s). */
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Shared client — initialize() is the expensive bit so we do it once. */
  private static McpSyncClient client;

  /** Live issue key discovered against $JIRA_URL — used by get_issue + search tests. */
  private static String knownIssueKey;

  // --- lifecycle ----------------------------------------------------------

  @BeforeClass
  public static void setUp() throws Exception {
    Assume.assumeTrue(
        "JIRA_URL not set — skipping live e2e", JIRA_URL != null && !JIRA_URL.isEmpty());
    Assume.assumeTrue(
        "JIRA_PAT_RKADMIN not set — skipping live e2e", JIRA_PAT != null && !JIRA_PAT.isEmpty());

    client = newClient();
    InitializeResult init = client.initialize();
    // Sanity guard — every downstream test depends on a live, initialized session.
    assertNotNull("initialize() returned null result", init);
    assertNotNull("server info missing", init.serverInfo());

    knownIssueKey = discoverIssueKey();
  }

  @AfterClass
  public static void tearDown() {
    if (client != null) {
      try {
        client.close();
      } catch (Exception ignored) {
        /* best-effort */
      }
    }
  }

  // ========================================================================
  // 1 — Connection + server identity
  // ========================================================================

  @Test
  public void test01_connectionAndCapabilities() {
    InitializeResult init = client.getCurrentInitializationResult();
    assertNotNull("no initialization result cached on client", init);

    McpSchema.Implementation serverInfo = init.serverInfo();
    assertEquals(
        "server identifies as something other than jira-mcp-plugin",
        "jira-mcp-plugin",
        serverInfo.name());
    assertNotNull("server reported empty version", serverInfo.version());
    assertFalse("server reported empty version string", serverInfo.version().isEmpty());

    McpSchema.ServerCapabilities caps = init.capabilities();
    assertNotNull("server capabilities missing", caps);
    assertNotNull("server does not advertise tools capability — plugin is broken", caps.tools());
  }

  // ========================================================================
  // 2 — tools/list returns the expected tool surface
  // ========================================================================

  @Test
  public void test02_toolsListReturnsExpectedSubset() {
    ListToolsResult result = client.listTools();
    List<Tool> tools = result.tools();
    assertNotNull("tools/list returned null tools array", tools);

    // The deployed Jira lacks JSM/Proforma so capability-gated tools are hidden.
    // 30 is a sane floor; the full upstream count is 49.
    assertTrue("tools/list returned suspiciously few tools: " + tools.size(), tools.size() >= 30);

    // Core read-only tools that MUST always be advertised.
    Set<String> mustHave =
        Set.of(
            "get_user_profile",
            "get_all_projects",
            "get_issue",
            "search",
            "get_link_types",
            "search_fields");

    Map<String, Tool> byName = new HashMap<>();
    for (Tool t : tools) {
      byName.put(t.name(), t);
    }

    for (String name : mustHave) {
      Tool t = byName.get(name);
      assertNotNull("tool '" + name + "' not advertised by server", t);
      assertNotNull("tool '" + name + "' has null description", t.description());
      assertFalse("tool '" + name + "' has empty description", t.description().isEmpty());
      assertNotNull("tool '" + name + "' has null inputSchema", t.inputSchema());
      assertFalse("tool '" + name + "' has empty inputSchema", t.inputSchema().isEmpty());
    }
  }

  // ========================================================================
  // 3 — get_user_profile returns real Jira user data
  // ========================================================================

  @Test
  public void test03_getUserProfileReturnsRealJiraUser() {
    CallToolResult result = call("get_user_profile", Map.of("user_identifier", "rkadmin"));

    assertNotErrored("get_user_profile", result);
    String text = firstText(result);

    // Real values present on the live Jira at bpm.astrateam.net.
    assertContains("display name", text, "Ruben Khachaturov");
    assertContains("user key", text, "JIRAUSER10000");
    assertContains("time zone", text, "Europe/Moscow");
  }

  // ========================================================================
  // 4 — get_all_projects returns the live project list
  // ========================================================================

  @Test
  public void test04_getAllProjectsReturnsKnownProject() {
    CallToolResult result = call("get_all_projects", Map.of());
    assertNotErrored("get_all_projects", result);

    String text = firstText(result);
    // Discovered live via /rest/api/2/project against $JIRA_URL.
    // If the project list changes, update this assertion to match real state.
    assertContains("'TES' project key", text, "\"key\":\"TES\"");
    assertContains("'TES' project name", text, "Test_service");
  }

  // ========================================================================
  // 5 — ResponseTrimmer strips avatarUrls / iconUrl / expand
  // ========================================================================

  @Test
  public void test05_responseTrimmerStripsVerboseFields() {
    CallToolResult result = call("get_user_profile", Map.of("user_identifier", "rkadmin"));
    assertNotErrored("get_user_profile", result);

    String text = firstText(result);
    // ResponseTrimmer is our code — it must strip these recursively.
    // The raw Jira /rest/api/2/myself response DOES include avatarUrls.
    assertFalse(
        "ResponseTrimmer left 'avatarUrls' in: " + truncate(text, 200),
        text.contains("avatarUrls"));
    assertFalse(
        "ResponseTrimmer left 'iconUrl' in: " + truncate(text, 200), text.contains("iconUrl"));
    assertFalse(
        "ResponseTrimmer left 'expand' in: " + truncate(text, 200), text.contains("\"expand\""));
    // Avatar size keys should be gone too.
    assertFalse("ResponseTrimmer left '48x48' avatar key in", text.contains("\"48x48\""));
  }

  // ========================================================================
  // 6 — get_issue returns structuredContent and tool advertises widget URI
  // ========================================================================

  @Test
  public void test06_getIssueReturnsStructuredContentForWidget() {
    Assume.assumeNotNull("no live issue discovered on this Jira", knownIssueKey);

    // First — assert the tool advertises the MCP Apps widget URI in its meta.
    // (This is on Tool.meta(), not on CallToolResult — the resourceUri is
    // tied to the tool itself, not to a particular call.)
    Tool getIssueTool = findTool("get_issue");
    assertNotNull("get_issue tool not advertised", getIssueTool);
    Map<String, Object> meta = getIssueTool.meta();
    assertNotNull("get_issue tool has no _meta — MCP Apps wiring broken", meta);

    @SuppressWarnings("unchecked")
    Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
    assertNotNull("get_issue tool has no _meta.ui — MCP Apps wiring broken", ui);
    Object resourceUri = ui.get("resourceUri");
    assertNotNull("get_issue tool has no _meta.ui.resourceUri", resourceUri);
    assertTrue(
        "resourceUri '" + resourceUri + "' does not start with ui://jira/issue-card",
        resourceUri.toString().startsWith("ui://jira/issue-card"));

    // Now — call the tool and verify structuredContent is populated for the widget.
    CallToolResult result = call("get_issue", Map.of("issue_key", knownIssueKey));
    assertNotErrored("get_issue", result);

    Object structured = result.structuredContent();
    assertNotNull("get_issue result has no structuredContent — widget cannot render", structured);

    // structuredContent shape contains an 'issues' array (the widget data contract).
    JsonNode json = MAPPER.valueToTree(structured);
    assertTrue(
        "structuredContent missing 'issues' array",
        json.has("issues") && json.get("issues").isArray());
    assertTrue("structuredContent.issues empty", json.get("issues").size() > 0);

    JsonNode issue = json.get("issues").get(0);
    assertEquals(
        "structuredContent.issues[0].key mismatch", knownIssueKey, issue.path("key").asText());
    assertFalse(
        "structuredContent.issues[0].summary empty", issue.path("summary").asText().isEmpty());
    assertNotNull("structuredContent.issues[0].status missing", issue.path("status"));
  }

  // ========================================================================
  // 7 — search filters by JQL
  // ========================================================================

  @Test
  public void test07_searchReturnsFilteredResults() {
    CallToolResult result = call("search", Map.of("jql", "project = " + PROJECT_KEY, "limit", 5));
    assertNotErrored("search", result);

    String text = firstText(result);
    // All returned issue keys should belong to the requested project.
    assertContains("project key prefix in search results", text, "\"" + PROJECT_KEY + "-");
  }

  // ========================================================================
  // 8 — unknown tool surfaces an SDK-level error
  // ========================================================================

  @Test
  public void test08_unknownToolReturnsProperError() {
    try {
      CallToolResult result = call("definitely_not_a_real_tool", Map.of());
      // Allowed path: SDK returns CallToolResult with isError=true.
      Boolean err = result.isError();
      assertTrue(
          "unknown tool did not surface as error (isError=" + err + ")", Boolean.TRUE.equals(err));
      String text = firstText(result);
      assertContains(
          "error mentions tool name or 'unknown'",
          text.toLowerCase(),
          "definitely_not_a_real_tool");
    } catch (RuntimeException e) {
      // Equally allowed: SDK raises an exception. Verify it mentions the tool.
      String msg = (String.valueOf(e.getMessage()) + " " + rootCauseMessage(e)).toLowerCase();
      assertTrue(
          "SDK exception did not reference the unknown tool name: " + msg,
          msg.contains("definitely_not_a_real_tool")
              || msg.contains("unknown tool")
              || msg.contains("not found"));
    }
  }

  // ========================================================================
  // 9 — Missing required param → schema validation error
  // ========================================================================

  @Test
  public void test09_missingRequiredParamReturnsValidationError() {
    // get_user_profile.inputSchema declares user_identifier as required.
    // The SDK's JsonSchemaValidator (client side) — and the server's, if any —
    // should reject this call.
    try {
      CallToolResult result = call("get_user_profile", Map.of());
      // If we got a result, it must be flagged as an error.
      Boolean err = result.isError();
      assertTrue(
          "missing required param did not surface as error (isError="
              + err
              + ", content="
              + truncate(firstTextOrEmpty(result), 200)
              + ")",
          Boolean.TRUE.equals(err));
      String text = firstTextOrEmpty(result).toLowerCase();
      assertTrue(
          "error text did not mention missing/required field: " + truncate(text, 200),
          text.contains("user_identifier")
              || text.contains("required")
              || text.contains("missing"));
    } catch (RuntimeException e) {
      String msg = (String.valueOf(e.getMessage()) + " " + rootCauseMessage(e)).toLowerCase();
      assertTrue(
          "SDK validation error did not mention user_identifier/required: " + msg,
          msg.contains("user_identifier")
              || msg.contains("required")
              || msg.contains("missing")
              || msg.contains("validation"));
    }
  }

  // ========================================================================
  // 10 — OAuth metadata endpoint is reachable anonymously
  // ========================================================================

  @Test
  public void test10_oauthMetadataAnonymousWorks() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + "/.well-known/oauth-authorization-server"))
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    assertEquals(
        "anonymous GET on oauth metadata not 200 — OAuthAnonymousFilter regressed",
        200,
        resp.statusCode());
    String body = resp.body();
    assertContains("authorization_endpoint advertised", body, "\"authorization_endpoint\"");
    assertContains("token_endpoint advertised", body, "\"token_endpoint\"");
    assertContains("registration_endpoint advertised", body, "\"registration_endpoint\"");
    // PKCE S256 is mandatory in our impl.
    assertContains("S256 PKCE advertised", body, "S256");
  }

  // ========================================================================
  // 11 — DCR register endpoint accepts anonymous POST
  // ========================================================================

  @Test
  public void test11_oauthRegisterAnonymousWorks() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    String body =
        "{"
            + "\"client_name\":\"jira-mcp-e2e-suite\","
            + "\"redirect_uris\":[\"http://localhost:9999/cb\"],"
            + "\"token_endpoint_auth_method\":\"none\""
            + "}";

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + "/plugins/servlet/mcp-oauth/register"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    int code = resp.statusCode();
    assertTrue(
        "anonymous DCR register returned "
            + code
            + " — expected 200/201. body="
            + truncate(resp.body(), 200),
        code == 200 || code == 201);

    JsonNode json = MAPPER.readTree(resp.body());
    assertTrue("DCR response missing client_id", json.has("client_id"));
    assertFalse("DCR client_id was empty", json.get("client_id").asText().isEmpty());
  }

  // ========================================================================
  // 12 — Forbidden-on-blocked-user — requires staging instance
  // ========================================================================

  /**
   * Verifies that a user not in allowedUsers/allowedGroups is rejected with a 403-equivalent error.
   * Cannot run against production without flipping config that would lock out the admin running the
   * test, so it requires a dedicated staging instance signalled by {@code STAGING=1}.
   */
  @Test
  public void test12_forbiddenWhenUserBlocked() {
    // TODO: requires staging instance — locking out the admin running these
    //       tests against prod would brick the suite.
    Assume.assumeTrue(
        "staging-only test — set STAGING=1 to enable", System.getenv("STAGING") != null);

    String blockedPat = System.getenv("JIRA_PAT_BLOCKED");
    Assume.assumeTrue(
        "STAGING set but JIRA_PAT_BLOCKED missing", blockedPat != null && !blockedPat.isEmpty());

    McpSyncClient blockedClient = newClientWithToken(blockedPat);
    try {
      blockedClient.initialize();
      // If initialize succeeded, the user isn't actually blocked.
      fail("expected blocked user to be rejected, but initialize() succeeded");
    } catch (RuntimeException e) {
      String msg = (String.valueOf(e.getMessage()) + " " + rootCauseMessage(e)).toLowerCase();
      assertTrue(
          "blocked user error did not mention forbidden/403: " + msg,
          msg.contains("forbidden")
              || msg.contains("403")
              || msg.contains("not allowed")
              || msg.contains("access denied"));
    } finally {
      try {
        blockedClient.close();
      } catch (Exception ignored) {
        /* best-effort */
      }
    }
  }

  // ========================================================================
  // 13 — resources/list advertises the issue-card widget with dual metadata
  // ========================================================================

  @Test
  public void test13_resourcesListReturnsIssueCard() {
    McpSchema.ListResourcesResult result = client.listResources();
    assertNotNull("listResources returned null", result);

    List<McpSchema.Resource> resources = result.resources();
    assertNotNull("resources list is null", resources);
    assertEquals("expected exactly one resource (the issue-card widget)", 1, resources.size());

    McpSchema.Resource r = resources.get(0);
    assertTrue(
        "resource.uri '" + r.uri() + "' does not start with ui://jira/issue-card@",
        r.uri().startsWith("ui://jira/issue-card@"));
    assertNotNull("resource.mimeType is null", r.mimeType());
    assertTrue(
        "resource.mimeType '" + r.mimeType() + "' is not an HTML type",
        r.mimeType().startsWith("text/html"));
    assertNotNull("resource.name is null", r.name());
    assertFalse("resource.name is empty", r.name().isEmpty());

    Map<String, Object> meta = r.meta();
    assertNotNull("resource._meta is null — MCP Apps metadata missing", meta);

    // Claude / nested ui block
    @SuppressWarnings("unchecked")
    Map<String, Object> ui = (Map<String, Object>) meta.get("ui");
    assertNotNull("resource._meta.ui is missing — Claude widget metadata absent", ui);
    assertTrue("resource._meta.ui has no entries", !ui.isEmpty());

    // ChatGPT / flat openai widget keys
    assertTrue(
        "resource._meta missing 'openai/widgetDescription'",
        meta.containsKey("openai/widgetDescription"));
    assertTrue(
        "resource._meta missing 'openai/widgetPrefersBorder'",
        meta.containsKey("openai/widgetPrefersBorder"));
    assertTrue("resource._meta missing 'openai/widgetCSP'", meta.containsKey("openai/widgetCSP"));
    assertTrue(
        "resource._meta missing 'openai/widgetDomain'", meta.containsKey("openai/widgetDomain"));
  }

  // ========================================================================
  // 14 — resources/read returns the widget HTML wrapped in TextResourceContents
  // ========================================================================

  @Test
  public void test14_resourcesReadReturnsWidgetHtml() {
    // Discover the live URI via list first — hash changes per build.
    McpSchema.ListResourcesResult list = client.listResources();
    assertEquals("expected exactly one resource", 1, list.resources().size());
    String uri = list.resources().get(0).uri();

    McpSchema.ReadResourceResult result =
        client.readResource(new McpSchema.ReadResourceRequest(uri));
    assertNotNull("readResource returned null", result);

    List<McpSchema.ResourceContents> contents = result.contents();
    assertNotNull("readResource result has null contents", contents);
    assertEquals("expected exactly one ResourceContents entry", 1, contents.size());

    McpSchema.ResourceContents first = contents.get(0);
    assertTrue(
        "expected TextResourceContents, got " + first.getClass().getName(),
        first instanceof McpSchema.TextResourceContents);

    McpSchema.TextResourceContents text = (McpSchema.TextResourceContents) first;
    assertEquals("contents[0].uri does not match request", uri, text.uri());
    assertTrue(
        "contents[0].mimeType '" + text.mimeType() + "' not HTML",
        text.mimeType().startsWith("text/html"));

    String body = text.text();
    assertNotNull("contents[0].text is null", body);
    assertTrue(
        "contents[0].text too short ("
            + body.length()
            + " bytes) — looks like a stub, not the real widget bundle",
        body.length() > 1000);
    String lower = body.trim().toLowerCase();
    assertTrue(
        "contents[0].text does not start with <!doctype html> / <html: "
            + body.substring(0, Math.min(80, body.length())),
        lower.startsWith("<!doctype html") || lower.startsWith("<html"));

    // Dual metadata also attached to the read result
    Map<String, Object> meta = result.meta();
    assertNotNull("readResource result._meta is null", meta);
    assertNotNull("readResource result._meta.ui is null", meta.get("ui"));
    assertTrue(
        "readResource result._meta missing 'openai/widgetDescription'",
        meta.containsKey("openai/widgetDescription"));
  }

  // ========================================================================
  // 15 — Unauthenticated POST returns a spec-compliant 401, not a login redirect
  // ========================================================================

  /**
   * On a login-required instance Seraph 302-redirects any path not exempt from login enforcement.
   * The MCP authorization spec (RFC 9728 discovery) requires a {@code 401 + WWW-Authenticate} so a
   * non-browser MCP client can discover the OAuth flow. This guards the {@code @UnrestrictedAccess}
   * annotation on the six MCP filters: without it the anonymous request is 302'd before {@code
   * AccessControlFilter} can emit the 401.
   */
  @Test
  public void test15_unauthenticatedReturns401NotLoginRedirect() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
            .timeout(REQUEST_TIMEOUT)
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    assertEquals(
        "unauthenticated POST must be 401, not a 302 login redirect", 401, resp.statusCode());
    String wwwAuth = resp.headers().firstValue("WWW-Authenticate").orElse("");
    assertFalse("401 must carry WWW-Authenticate", wwwAuth.isEmpty());
    // The advertised scope must be exactly the token registered on the Jira "MCP" Application
    // Link (WRITE, which already grants read). Advertising "read write" makes clients request
    // a separate "read" token the OAuth provider can reject with invalid_scope.
    assertTrue(
        "WWW-Authenticate must advertise scope=\"WRITE\", was: " + wwwAuth,
        wwwAuth.contains("scope=\"WRITE\""));
    assertFalse(
        "WWW-Authenticate must not advertise the unregistered 'read' scope, was: " + wwwAuth,
        wwwAuth.toLowerCase().contains("read"));
    assertFalse(
        "must not be a login redirect HTML page", resp.body().toLowerCase().contains("<html"));
  }

  // ========================================================================
  // 16 — Invalid PAT also returns 401, not a login redirect
  // ========================================================================

  @Test
  public void test16_invalidPatReturns401NotLoginRedirect() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
            .header("Authorization", "Bearer not-a-real-pat-token")
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
            .timeout(REQUEST_TIMEOUT)
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

    assertEquals("invalid-PAT POST must be 401, not a 302 login redirect", 401, resp.statusCode());
    String wwwAuth = resp.headers().firstValue("WWW-Authenticate").orElse("");
    assertFalse("401 must carry WWW-Authenticate", wwwAuth.isEmpty());
    assertTrue(
        "WWW-Authenticate must advertise scope=\"WRITE\", was: " + wwwAuth,
        wwwAuth.contains("scope=\"WRITE\""));
    assertFalse(
        "WWW-Authenticate must not advertise the unregistered 'read' scope, was: " + wwwAuth,
        wwwAuth.toLowerCase().contains("read"));
    assertFalse(
        "must not be a login redirect HTML page", resp.body().toLowerCase().contains("<html"));
  }

  // ========================================================================
  // 17 — Every discovery document advertises exactly the registered WRITE scope
  // ========================================================================

  /**
   * Every OAuth/OIDC discovery document must advertise exactly the scope registered on the Jira
   * "MCP" Application Link — only {@code WRITE} (which already grants read). Advertising {@code
   * READ} as a separately requestable scope makes MCP clients request a token the OAuth provider
   * can reject with {@code invalid_scope}. Regression guard for the bug where the consent flow
   * advertised {@code ["WRITE","READ"]} / {@code scope="read write"} against a WRITE-only
   * Application Link.
   */
  @Test
  public void test17_discoveryAdvertisesOnlyRegisteredWriteScope() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    for (String path :
        List.of(
            "/plugins/servlet/mcp-oauth/metadata",
            "/.well-known/oauth-authorization-server",
            "/.well-known/openid-configuration",
            "/plugins/servlet/mcp-oauth/openid-configuration")) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(JIRA_URL + path))
              .timeout(REQUEST_TIMEOUT)
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      assertEquals(path + " must return 200", 200, resp.statusCode());

      JsonNode doc = MAPPER.readTree(resp.body());
      // Every discovery document must carry a non-empty issuer (RFC 8414 / OIDC Discovery).
      assertTrue(
          path + " must advertise a non-empty issuer, body=" + truncate(resp.body(), 300),
          doc.path("issuer").isTextual() && !doc.path("issuer").asText().isEmpty());

      JsonNode scopes = doc.path("scopes_supported");
      assertTrue(
          path + " must advertise scopes_supported, body=" + truncate(resp.body(), 300),
          scopes.isArray());
      assertEquals(path + " must advertise exactly one scope, was: " + scopes, 1, scopes.size());
      assertEquals(
          path + " must advertise only the registered WRITE scope, was: " + scopes,
          "WRITE",
          scopes.get(0).asText());
    }
  }

  // ========================================================================
  // 18 — Oversized fixed-length body rejected with 413
  // ========================================================================

  /**
   * The body cap (1 MiB) must be enforced on the actual bytes, not a trusted {@code
   * Content-Length}. This is the fixed-length fast path.
   */
  @Test
  public void test18_oversizedFixedLengthBodyReturns413() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
            .header("Authorization", "Bearer " + JIRA_PAT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(oversizedJson()))
            .timeout(REQUEST_TIMEOUT)
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals("oversized fixed-length body must be rejected with 413", 413, resp.statusCode());
  }

  // ========================================================================
  // 19 — Oversized chunked body (no Content-Length) rejected with 413
  // ========================================================================

  /** Guards the bypass where omitting Content-Length (chunked) slips an oversized body past. */
  @Test
  public void test19_oversizedChunkedBodyReturns413() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
            .header("Authorization", "Bearer " + JIRA_PAT)
            .header("Content-Type", "application/json")
            .POST(streamingOversizedPublisher())
            .timeout(REQUEST_TIMEOUT)
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals("oversized chunked body must be rejected with 413", 413, resp.statusCode());
  }

  // ========================================================================
  // 20 — Oversized unknown-length (InputStream) body rejected with 413
  // ========================================================================

  @Test
  public void test20_oversizedNoContentLengthBodyReturns413() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    byte[] payload = oversizedJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
            .header("Authorization", "Bearer " + JIRA_PAT)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofInputStream(
                    () -> new java.io.ByteArrayInputStream(payload)))
            .timeout(REQUEST_TIMEOUT)
            .build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(
        "oversized no-Content-Length body must be rejected with 413", 413, resp.statusCode());
  }

  // ========================================================================
  // 21 — CIMD SSRF: client_id resolving to an internal address is rejected
  // ========================================================================

  /**
   * The CIMD {@code client_id} URL is fetched server-side from an unauthenticated {@code
   * /authorize} request, so a URL whose host resolves to loopback / private / cloud-metadata space
   * must be rejected (SSRF) with {@code invalid_client}, not fetched.
   */
  @Test
  public void test21_cimdSsrfAuthorizeReturnsInvalidClient() throws Exception {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    for (String host : List.of("localhost", "10.0.0.1", "169.254.169.254")) {
      String clientId = "https://" + host + "/.well-known/oauth-client";
      String url =
          JIRA_URL
              + "/plugins/servlet/mcp-oauth/authorize"
              + "?client_id="
              + enc(clientId)
              + "&redirect_uri="
              + enc("http://localhost:9999/cb")
              + "&response_type=code"
              + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
              + "&code_challenge_method=S256"
              + "&state=xyz";
      HttpRequest req =
          HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

      assertEquals("CIMD SSRF to " + host + " must be rejected with 400", 400, resp.statusCode());
      assertTrue(
          "CIMD SSRF to " + host + " must be invalid_client, body=" + resp.body(),
          resp.body().contains("invalid_client"));
    }
  }

  // =======================================================================
  // helpers
  // =======================================================================

  private static String enc(String s) {
    return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
  }

  /** A JSON document well over the 1 MiB body cap. */
  private static String oversizedJson() {
    int padLen = 1_200_000;
    StringBuilder sb = new StringBuilder(padLen + 64);
    sb.append("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\",\"params\":{\"x\":\"");
    for (int i = 0; i < padLen; i++) sb.append('a');
    sb.append("\"}}");
    return sb.toString();
  }

  /**
   * Streaming publisher with unknown length → chunked transfer encoding (no Content-Length header),
   * emitting more than the 1 MiB cap.
   */
  private static HttpRequest.BodyPublisher streamingOversizedPublisher() {
    return HttpRequest.BodyPublishers.ofByteArrays(
        () ->
            new java.util.Iterator<byte[]>() {
              int remaining = 1_300_000;
              final byte[] chunk = new byte[16_384];

              {
                java.util.Arrays.fill(chunk, (byte) 'a');
              }

              @Override
              public boolean hasNext() {
                return remaining > 0;
              }

              @Override
              public byte[] next() {
                int n = Math.min(chunk.length, remaining);
                remaining -= n;
                if (n == chunk.length) return chunk;
                byte[] tail = new byte[n];
                java.util.Arrays.fill(tail, (byte) 'a');
                return tail;
              }
            });
  }

  /** Build a fresh SDK sync client wired to $JIRA_URL with the admin PAT. */
  private static McpSyncClient newClient() {
    return newClientWithToken(JIRA_PAT);
  }

  private static McpSyncClient newClientWithToken(String token) {
    McpClientTransport transport =
        HttpClientStreamableHttpTransport.builder(JIRA_URL)
            .endpoint(MCP_ENDPOINT)
            .connectTimeout(Duration.ofSeconds(5))
            .openConnectionOnStartup(false)
            .httpRequestCustomizer(
                (builder, method, uri, body, ctx) ->
                    builder.header("Authorization", "Bearer " + token))
            .build();

    return McpClient.sync(transport)
        .requestTimeout(REQUEST_TIMEOUT)
        .initializationTimeout(REQUEST_TIMEOUT)
        .clientInfo(new McpSchema.Implementation("jira-mcp-e2e", "1.0"))
        .build();
  }

  private static CallToolResult call(String name, Map<String, Object> args) {
    return client.callTool(new CallToolRequest(name, args));
  }

  /** Asserts the result is not flagged as error; surfaces content if it is. */
  private static void assertNotErrored(String toolName, CallToolResult result) {
    if (Boolean.TRUE.equals(result.isError())) {
      fail(toolName + " returned isError=true. content=" + truncate(firstTextOrEmpty(result), 400));
    }
  }

  /** Extract the first text-content block, fail if absent. */
  private static String firstText(CallToolResult result) {
    return firstTextOpt(result)
        .orElseThrow(() -> new AssertionError("CallToolResult had no text content block"));
  }

  private static String firstTextOrEmpty(CallToolResult result) {
    return firstTextOpt(result).orElse("");
  }

  private static Optional<String> firstTextOpt(CallToolResult result) {
    if (result == null || result.content() == null) {
      return Optional.empty();
    }
    for (Content c : result.content()) {
      if (c instanceof TextContent) {
        return Optional.ofNullable(((TextContent) c).text());
      }
    }
    return Optional.empty();
  }

  private static Tool findTool(String name) {
    for (Tool t : client.listTools().tools()) {
      if (name.equals(t.name())) return t;
    }
    return null;
  }

  private static void assertContains(String what, String haystack, String needle) {
    if (haystack == null || !haystack.contains(needle)) {
      fail("Expected " + what + " ('" + needle + "') in response, got: " + truncate(haystack, 400));
    }
  }

  private static String truncate(String s, int n) {
    if (s == null) return "<null>";
    return s.length() <= n ? s : s.substring(0, n) + "…[" + s.length() + " chars total]";
  }

  private static String rootCauseMessage(Throwable t) {
    Throwable cur = t;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    return cur == t ? "" : String.valueOf(cur.getMessage());
  }

  /**
   * Discover a real issue key on this Jira via the REST API. Returns null if no issues are visible
   * to the test user.
   */
  private static String discoverIssueKey() {
    try {
      HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(
                  URI.create(
                      JIRA_URL
                          + "/rest/api/2/search"
                          + "?jql=project="
                          + PROJECT_KEY
                          + "&maxResults=1&fields=summary"))
              .timeout(REQUEST_TIMEOUT)
              .header("Authorization", "Bearer " + JIRA_PAT)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) return null;
      JsonNode json = MAPPER.readTree(resp.body());
      JsonNode issues = json.path("issues");
      if (issues.isArray() && issues.size() > 0) {
        return issues.get(0).path("key").asText();
      }
    } catch (Exception ignored) {
      /* fall through */
    }
    return null;
  }
}
