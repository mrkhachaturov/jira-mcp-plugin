package com.atlassian.mcp.plugin.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;

/**
 * End-to-end tests for the MCP endpoint running on a live Jira instance.
 *
 * Requires environment variables:
 *   JIRA_URL          — e.g. https://bpm.astrateam.net
 *   JIRA_PAT_RKADMIN  — PAT for an admin user with MCP access
 *
 * Optional:
 *   JIRA_PAT_CEO      — PAT for a non-admin user (access control tests)
 *   JIRA_PROJECT_KEY   — project key for issue CRUD tests (default: TES)
 *
 * Skipped automatically when env vars are not set.
 *
 * Run: just e2e
 *  Or: source .credentials/jira.env && atlas-mvn test -Dtest=McpEndpointE2ETest
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class McpEndpointE2ETest {

    private static final String JIRA_URL = System.getenv("JIRA_URL");
    private static final String JIRA_PAT = System.getenv("JIRA_PAT_RKADMIN");
    private static final String JIRA_PAT_CEO = System.getenv("JIRA_PAT_CEO");
    private static final String PROJECT_KEY = System.getenv().getOrDefault("JIRA_PROJECT_KEY", "TES");

    private static final String MCP_ENDPOINT = "/rest/mcp/1.0/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** All 49 upstream tool names (source of truth). */
    private static final Set<String> ALL_UPSTREAM_TOOLS = Set.of(
            "get_user_profile", "get_issue_watchers", "add_watcher", "remove_watcher",
            "get_issue", "search", "search_fields", "get_field_options",
            "get_project_issues", "get_transitions", "get_worklog",
            "download_attachments", "get_issue_images",
            "get_agile_boards", "get_board_issues", "get_sprints_from_board", "get_sprint_issues",
            "create_sprint", "update_sprint", "add_issues_to_sprint",
            "get_link_types", "create_issue_link", "create_remote_issue_link", "remove_issue_link",
            "link_to_epic",
            "create_issue", "batch_create_issues", "batch_get_changelogs",
            "update_issue", "delete_issue",
            "add_comment", "edit_comment",
            "add_worklog",
            "transition_issue",
            "get_project_versions", "get_project_components", "get_all_projects",
            "create_version", "batch_create_versions",
            "get_service_desk_for_project", "get_service_desk_queues", "get_queue_issues",
            "get_issue_proforma_forms", "get_proforma_form_details", "update_proforma_form_answers",
            "get_issue_dates", "get_issue_sla",
            "get_issue_development_info", "get_issues_development_info"
    );

    /** Issue key created during CRUD lifecycle test, cleaned up at end. */
    private static String createdIssueKey;

    @BeforeClass
    public static void checkEnvironment() {
        Assume.assumeTrue("JIRA_URL not set — skipping e2e tests", JIRA_URL != null);
        Assume.assumeTrue("JIRA_PAT_RKADMIN not set — skipping e2e tests", JIRA_PAT != null);
    }

    // ── Protocol Tests ───────────────────────────────────────────────

    @Test
    public void t01_initialize() throws Exception {
        JsonNode result = mcpCall("initialize", MAPPER.createObjectNode());

        assertTrue("Should have result", result.has("result"));
        JsonNode serverInfo = result.path("result").path("serverInfo");
        assertEquals("jira-mcp", serverInfo.path("name").asText());
        assertTrue("Should have capabilities.tools", result.path("result").has("capabilities"));
    }

    @Test
    public void t02_ping() throws Exception {
        JsonNode result = mcpCall("ping", MAPPER.createObjectNode());
        // ping should return a valid JSON-RPC response (not error)
        assertFalse("ping should not error", result.has("error"));
    }

    @Test
    public void t03_invalidMethod_returnsError() throws Exception {
        JsonNode result = mcpCall("nonexistent/method", MAPPER.createObjectNode());
        assertTrue("Should return error for invalid method", result.has("error"));
    }

    // ── Tools List Tests ─────────────────────────────────────────────

    @Test
    public void t10_toolsList_returnsTools() throws Exception {
        JsonNode result = mcpCall("tools/list", MAPPER.createObjectNode());

        assertTrue("Should have result", result.has("result"));
        JsonNode tools = result.path("result").path("tools");
        assertTrue("tools should be array", tools.isArray());
        assertTrue("Should have at least 40 tools", tools.size() >= 40);

        System.out.println("[e2e] tools/list returned " + tools.size() + " tools");
    }

    @Test
    public void t11_toolsList_coversUpstreamTools() throws Exception {
        JsonNode tools = mcpCall("tools/list", MAPPER.createObjectNode())
                .path("result").path("tools");

        Set<String> visibleNames = new HashSet<>();
        tools.forEach(t -> visibleNames.add(t.path("name").asText()));

        // Every visible tool must be in the upstream set
        for (String name : visibleNames) {
            assertTrue("Unexpected tool not in upstream: " + name,
                    ALL_UPSTREAM_TOOLS.contains(name));
        }

        // Check which upstream tools are hidden (due to missing plugins)
        Set<String> hidden = new HashSet<>(ALL_UPSTREAM_TOOLS);
        hidden.removeAll(visibleNames);
        if (!hidden.isEmpty()) {
            System.out.println("[e2e] Hidden tools (plugin not installed): " + hidden);
        }

        // At minimum, core tools must be present
        for (String core : List.of("search", "get_issue", "create_issue", "get_all_projects",
                "add_comment", "get_user_profile", "create_sprint")) {
            assertTrue("Core tool missing: " + core, visibleNames.contains(core));
        }
    }

    @Test
    public void t12_toolsList_eachToolHasSchemaAndDescription() throws Exception {
        JsonNode tools = mcpCall("tools/list", MAPPER.createObjectNode())
                .path("result").path("tools");

        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            assertTrue(name + " missing description", tool.has("description"));
            assertFalse(name + " has empty description",
                    tool.path("description").asText().isBlank());
            assertTrue(name + " missing inputSchema", tool.has("inputSchema"));
            assertEquals(name + " inputSchema.type should be 'object'",
                    "object", tool.path("inputSchema").path("type").asText());
        }
    }

    // ── Read Tool Tests ──────────────────────────────────────────────

    @Test
    public void t20_getAllProjects() throws Exception {
        JsonNode result = callTool("get_all_projects", Map.of());

        assertFalse("Should not error", isError(result));
        String text = getContentText(result);
        assertNotNull("Should have content text", text);

        JsonNode projects = MAPPER.readTree(text);
        assertTrue("Should be array", projects.isArray());
        System.out.println("[e2e] get_all_projects: " + projects.size() + " projects");
    }

    @Test
    public void t21_getUserProfile() throws Exception {
        JsonNode result = callTool("get_user_profile", Map.of("user_identifier", "rkadmin"));

        assertFalse("Should not error", isError(result));
        String text = getContentText(result);
        assertNotNull("Should have content text", text);
    }

    @Test
    public void t22_searchFields() throws Exception {
        JsonNode result = callTool("search_fields", Map.of("keyword", "summary"));

        assertFalse("Should not error", isError(result));
        String text = getContentText(result);
        assertNotNull("Should have content text", text);
    }

    @Test
    public void t23_getLinkTypes() throws Exception {
        JsonNode result = callTool("get_link_types", Map.of());

        assertFalse("Should not error", isError(result));
        String text = getContentText(result);
        assertNotNull("Should have content text", text);
    }

    @Test
    public void t24_search_emptyResult() throws Exception {
        JsonNode result = callTool("search", Map.of(
                "jql", "project = " + PROJECT_KEY + " AND summary ~ \"e2e_nonexistent_xyz\"",
                "limit", "5"
        ));

        assertFalse("Should not error", isError(result));
        String text = getContentText(result);
        JsonNode parsed = MAPPER.readTree(text);
        assertTrue("Should have total field", parsed.has("total"));
    }

    @Test
    public void t25_getAgileBoards() throws Exception {
        JsonNode result = callTool("get_agile_boards", Map.of());
        assertFalse("Should not error", isError(result));
    }

    // ── Response Trimming Tests ──────────────────────────────────────

    @Test
    public void t30_responseTrimming_noSelfLinks() throws Exception {
        JsonNode result = callTool("get_all_projects", Map.of());
        String raw = getContentText(result);
        assertNotNull(raw);

        assertFalse("Response should not contain \"self\" links",
                raw.contains("\"self\""));
    }

    @Test
    public void t31_responseTrimming_noAvatarUrls() throws Exception {
        JsonNode result = callTool("get_all_projects", Map.of());
        String raw = getContentText(result);
        assertNotNull(raw);

        assertFalse("Response should not contain avatarUrls",
                raw.contains("avatarUrls"));
    }

    @Test
    public void t32_responseTrimming_noIconUrl() throws Exception {
        JsonNode result = callTool("get_user_profile", Map.of("user_identifier", "rkadmin"));
        String raw = getContentText(result);
        assertNotNull(raw);

        assertFalse("Response should not contain iconUrl",
                raw.contains("iconUrl"));
    }

    // ── Issue CRUD Lifecycle Test ────────────────────────────────────

    @Test
    public void t40_createIssue() throws Exception {
        JsonNode result = callTool("create_issue", Map.of(
                "project_key", PROJECT_KEY,
                "summary", "[E2E Test] Auto-created by McpEndpointE2ETest",
                "issue_type", "Task"
        ));

        assertFalse("Create should not error: " + getContentText(result), isError(result));
        String text = getContentText(result);
        JsonNode parsed = MAPPER.readTree(text);

        // Extract issue key from response
        createdIssueKey = parsed.has("key") ? parsed.path("key").asText() :
                parsed.path("issue").path("key").asText(null);
        if (createdIssueKey == null && parsed.has("id")) {
            // Fallback: some responses return id instead of key
            createdIssueKey = parsed.path("key").asText();
        }
        assertNotNull("Should return created issue key", createdIssueKey);
        assertFalse("Issue key should not be empty", createdIssueKey.isBlank());

        System.out.println("[e2e] Created issue: " + createdIssueKey);
    }

    @Test
    public void t41_getCreatedIssue() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("get_issue", Map.of("issue_key", createdIssueKey));

        assertFalse("Get should not error", isError(result));
        String text = getContentText(result);
        assertTrue("Response should contain issue key",
                text.contains(createdIssueKey));
    }

    @Test
    public void t42_addComment() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("add_comment", Map.of(
                "issue_key", createdIssueKey,
                "body", "E2E test comment — verifying add_comment tool"
        ));

        assertFalse("Add comment should not error: " + getContentText(result), isError(result));
    }

    @Test
    public void t43_updateIssue() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("update_issue", Map.of(
                "issue_key", createdIssueKey,
                "fields", "{\"summary\":\"[E2E Test] Updated by McpEndpointE2ETest\"}"
        ));

        assertFalse("Update should not error: " + getContentText(result), isError(result));
    }

    @Test
    public void t44_getTransitions() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("get_transitions", Map.of("issue_key", createdIssueKey));
        assertFalse("Get transitions should not error", isError(result));
    }

    @Test
    public void t45_getIssueDates() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("get_issue_dates", Map.of("issue_key", createdIssueKey));
        assertFalse("Get issue dates should not error", isError(result));
    }

    @Test
    public void t48_deleteCreatedIssue() throws Exception {
        Assume.assumeTrue("No issue created", createdIssueKey != null);

        JsonNode result = callTool("delete_issue", Map.of("issue_key", createdIssueKey));
        assertFalse("Delete should not error: " + getContentText(result), isError(result));

        System.out.println("[e2e] Deleted issue: " + createdIssueKey);
        createdIssueKey = null;
    }

    // ── Service Desk Tests ───────────────────────────────────────────

    @Test
    public void t50_getServiceDeskForProject() throws Exception {
        JsonNode result = callTool("get_service_desk_for_project",
                Map.of("project_key", PROJECT_KEY));
        // May error if project isn't a service desk — that's OK
        String text = getContentText(result);
        System.out.println("[e2e] get_service_desk_for_project: " +
                (isError(result) ? "not a service desk" : "OK"));
    }

    // ── Error Handling Tests ─────────────────────────────────────────

    @Test
    public void t60_missingRequiredParam_returnsError() throws Exception {
        // Call get_issue without issue_key
        JsonNode result = callTool("get_issue", Map.of());
        assertTrue("Should error on missing required param", isError(result));
    }

    @Test
    public void t61_invalidIssueKey_returnsError() throws Exception {
        JsonNode result = callTool("get_issue", Map.of("issue_key", "INVALID-999999"));
        assertTrue("Should error on invalid issue key", isError(result));
    }

    @Test
    public void t62_unknownTool_returnsError() throws Exception {
        JsonNode result = callTool("nonexistent_tool", Map.of());
        assertTrue("Should error on unknown tool", isError(result));
    }

    // ── Streamable HTTP Transport Tests ─────────────────────────────

    @Test
    public void t80_streamableHttp_initializeReturnsSessionId() throws Exception {
        // Per MCP spec: client sends Accept: application/json, text/event-stream
        // Server returns JSON for single responses + MCP-Session-Id header
        HttpResponse<String> response = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);

        assertEquals("Should return 200", 200, response.statusCode());

        // Server should return JSON (not SSE) for single responses
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue("Should return JSON for single response, got: " + contentType,
                contentType.contains("application/json"));

        // Must include session ID
        String sessionId = response.headers().firstValue("MCP-Session-Id").orElse(null);
        assertNotNull("Should return MCP-Session-Id header", sessionId);

        // Parse response
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals("jira-mcp", parsed.path("result").path("serverInfo").path("name").asText());

        System.out.println("[e2e] Streamable HTTP initialize: OK, session " + sessionId.substring(0, 8) + "...");
    }

    @Test
    public void t81_streamableHttp_toolCallWithSession() throws Exception {
        // Initialize to get session
        HttpResponse<String> initResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        String sessionId = initResp.headers().firstValue("MCP-Session-Id").orElse(null);
        Assume.assumeTrue("No session ID", sessionId != null);

        // Call a tool with session
        HttpResponse<String> toolResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"get_all_projects\",\"arguments\":{}}}",
                sessionId);

        assertEquals("Should return 200", 200, toolResp.statusCode());
        JsonNode parsed = MAPPER.readTree(toolResp.body());
        assertFalse("Should not be error", parsed.path("result").path("isError").asBoolean(false));

        System.out.println("[e2e] Streamable HTTP tool call: OK");
    }

    @Test
    public void t82_streamableHttp_toolsListWithSession() throws Exception {
        HttpResponse<String> initResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        String sessionId = initResp.headers().firstValue("MCP-Session-Id").orElse(null);
        Assume.assumeTrue("No session ID", sessionId != null);

        HttpResponse<String> listResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
                sessionId);

        // Per spec: single response → always JSON (SSE is for multiple messages)
        String contentType = listResp.headers().firstValue("Content-Type").orElse("");
        assertTrue("Single response should be JSON, got: " + contentType,
                contentType.contains("application/json"));

        JsonNode parsed = MAPPER.readTree(listResp.body());
        JsonNode tools = parsed.path("result").path("tools");
        assertTrue("Should have tools array", tools.isArray());
        assertTrue("Should have 40+ tools", tools.size() >= 40);

        System.out.println("[e2e] Streamable HTTP tools/list: " + tools.size() + " tools");
    }

    @Test
    public void t83_streamableHttp_deleteSession() throws Exception {
        // Create session
        HttpResponse<String> initResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        String sessionId = initResp.headers().firstValue("MCP-Session-Id").orElse(null);
        Assume.assumeTrue("No session ID", sessionId != null);

        // Delete it
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
                .header("Authorization", "Bearer " + JIRA_PAT)
                .header("MCP-Session-Id", sessionId)
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> deleteResp = HTTP.send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals("Delete should return 200", 200, deleteResp.statusCode());

        // Verify session is gone
        HttpResponse<String> staleResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}",
                sessionId);
        assertEquals("Stale session should return 404", 404, staleResp.statusCode());

        System.out.println("[e2e] Streamable HTTP session delete: OK");
    }

    @Test
    public void t84_streamableHttp_originValidation() throws Exception {
        // Valid request without Origin header — should work (curl, server-to-server)
        HttpResponse<String> noOrigin = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        assertEquals("No Origin header should be allowed", 200, noOrigin.statusCode());

        // Request with invalid Origin — should be 403
        HttpRequest badOriginReq = HttpRequest.newBuilder()
                .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
                .header("Authorization", "Bearer " + JIRA_PAT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Origin", "https://evil-site.example.com")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"))
                .build();
        HttpResponse<String> badOrigin = HTTP.send(badOriginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals("Invalid Origin should return 403", 403, badOrigin.statusCode());

        System.out.println("[e2e] Origin validation: no-origin=allowed, bad-origin=403");
    }

    @Test
    public void t86_streamableHttp_batchWithProgress() throws Exception {
        // Initialize to get session
        HttpResponse<String> initResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        String sessionId = initResp.headers().firstValue("MCP-Session-Id").orElse(null);
        Assume.assumeTrue("No session ID", sessionId != null);

        // Call batch_create_issues with progressToken
        String batchRequest = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{"
                + "\"name\":\"batch_create_issues\","
                + "\"_meta\":{\"progressToken\":\"test-progress-1\"},"
                + "\"arguments\":{\"issues\":\"["
                + "{\\\"project_key\\\":\\\"" + PROJECT_KEY + "\\\",\\\"summary\\\":\\\"[E2E] Batch 1\\\",\\\"issue_type\\\":\\\"Task\\\"},"
                + "{\\\"project_key\\\":\\\"" + PROJECT_KEY + "\\\",\\\"summary\\\":\\\"[E2E] Batch 2\\\",\\\"issue_type\\\":\\\"Task\\\"}"
                + "]\"}}}";

        HttpResponse<String> resp = streamablePost(batchRequest, sessionId);

        assertEquals("Should return 200", 200, resp.statusCode());
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue("Should return SSE for batch with progressToken, got: " + contentType,
                contentType.contains("text/event-stream"));

        // Parse all SSE events
        String body = resp.body();
        String[] lines = body.split("\n");
        int progressCount = 0;
        int responseCount = 0;
        String lastResponseData = null;

        for (String line : lines) {
            if (line.startsWith("data: ") && line.length() > 6) {
                String data = line.substring(6).trim();
                if (data.isEmpty()) continue; // priming event
                try {
                    JsonNode event = MAPPER.readTree(data);
                    if (event.has("method") && event.get("method").asText().contains("progress")) {
                        progressCount++;
                    } else if (event.has("result")) {
                        responseCount++;
                        lastResponseData = data;
                    }
                } catch (Exception e) {
                    // skip non-JSON data lines
                }
            }
        }

        assertTrue("Should have at least 1 progress notification, got " + progressCount,
                progressCount >= 1);
        assertEquals("Should have exactly 1 final response", 1, responseCount);

        // Clean up created issues
        if (lastResponseData != null) {
            JsonNode result = MAPPER.readTree(lastResponseData);
            JsonNode issues = result.path("result").path("content").get(0);
            String text = issues.path("text").asText();
            JsonNode parsed = MAPPER.readTree(text);
            if (parsed.has("issues")) {
                for (JsonNode issue : parsed.get("issues")) {
                    String key = issue.path("key").asText(null);
                    if (key != null) {
                        callTool("delete_issue", Map.of("issue_key", key));
                    }
                }
            }
        }

        System.out.println("[e2e] Batch streaming: " + progressCount + " progress events + 1 response");
    }

    @Test
    public void t87_streamableHttp_noProgressTokenNoStream() throws Exception {
        // Same batch call but WITHOUT progressToken → should return plain JSON
        HttpResponse<String> initResp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                null);
        String sessionId = initResp.headers().firstValue("MCP-Session-Id").orElse(null);
        Assume.assumeTrue("No session ID", sessionId != null);

        // No _meta/progressToken → no streaming
        HttpResponse<String> resp = streamablePost(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"get_all_projects\",\"arguments\":{}}}",
                sessionId);

        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        assertTrue("Without progressToken should return JSON, got: " + contentType,
                contentType.contains("application/json"));

        System.out.println("[e2e] No progressToken: JSON response (correct)");
    }

    @Test
    public void t85_streamableHttp_worksWithoutSession() throws Exception {
        // Stateless mode — no session header, should still work
        JsonNode result = mcpCall("initialize", MAPPER.createObjectNode());
        assertTrue("Stateless initialize should work", result.has("result"));
        assertEquals("jira-mcp", result.path("result").path("serverInfo").path("name").asText());
    }

    // ── Access Control Tests ─────────────────────────────────────────

    @Test
    public void t70_ceoAccess() throws Exception {
        Assume.assumeTrue("JIRA_PAT_CEO not set", JIRA_PAT_CEO != null);

        JsonNode result = mcpCallAs("initialize", MAPPER.createObjectNode(), JIRA_PAT_CEO);
        assertTrue("CEO should have access (via group)",
                result.has("result"));
        assertFalse("CEO should not get error", result.has("error"));

        System.out.println("[e2e] CEO access: GRANTED");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private JsonNode mcpCall(String method, JsonNode params) throws Exception {
        return mcpCallAs(method, params, JIRA_PAT);
    }

    private JsonNode mcpCallAs(String method, JsonNode params, String pat) throws Exception {
        var body = MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", method);
        body.set("params", params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
                .header("Authorization", "Bearer " + pat)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body());
    }

    private JsonNode callTool(String toolName, Map<String, String> args) throws Exception {
        var params = MAPPER.createObjectNode();
        params.put("name", toolName);
        var argsNode = MAPPER.valueToTree(args);
        params.set("arguments", argsNode);

        return mcpCall("tools/call", params);
    }

    private boolean isError(JsonNode response) {
        if (response.has("error")) return true;
        JsonNode result = response.path("result");
        return result.path("isError").asBoolean(false);
    }

    private String getContentText(JsonNode response) {
        JsonNode content = response.path("result").path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText(null);
        }
        return null;
    }

    // ── Streamable HTTP helpers ─────────────────────────────────────

    /** POST with both Accept types per MCP Streamable HTTP spec. */
    private HttpResponse<String> streamablePost(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(JIRA_URL + MCP_ENDPOINT))
                .header("Authorization", "Bearer " + JIRA_PAT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (sessionId != null) {
            builder.header("MCP-Session-Id", sessionId);
        }

        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
