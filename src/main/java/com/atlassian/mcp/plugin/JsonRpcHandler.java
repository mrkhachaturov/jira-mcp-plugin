package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.atlassian.mcp.plugin.tools.ToolRegistry;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class JsonRpcHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcHandler.class);

    private static final String JSONRPC = "2.0";
    private static final String SERVER_NAME = "jira-mcp";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private static final Set<String> UI_TOOLS = Set.of(
            "get_issue", "search", "get_project_issues", "get_board_issues", "get_sprint_issues");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolRegistry toolRegistry;
    private final McpPluginConfig config;
    private final ResourceRegistry resourceRegistry;
    private final ApplicationProperties applicationProperties;

    @Inject
    public JsonRpcHandler(ToolRegistry toolRegistry, McpPluginConfig config, ResourceRegistry resourceRegistry,
                          @ComponentImport ApplicationProperties applicationProperties) {
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.resourceRegistry = resourceRegistry;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Handle a JSON-RPC request. Returns null for notifications (caller should return 202).
     */
    public String handle(String jsonBody, String userKey, String username, String userDisplayName, String authHeader) {
        JsonNode request;
        try {
            request = mapper.readTree(jsonBody);
        } catch (Exception e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }

        // Reject batch requests
        if (request.isArray()) {
            return errorResponse(null, -32600, "Batch requests are not supported");
        }

        String method = request.has("method") ? request.get("method").asText() : null;
        JsonNode id = request.has("id") ? request.get("id") : null;
        JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();

        if (method == null) {
            return errorResponse(id, -32600, "Missing 'method' field");
        }

        // Notifications (no id) return null -> caller returns 202
        boolean isNotification = (id == null || id.isNull());

        return switch (method) {
            case "initialize" -> handleInitialize(id);
            case "notifications/initialized" -> null; // notification
            case "ping" -> successResponse(id, mapper.createObjectNode());
            case "tools/list" -> handleToolsList(id, userKey);
            case "tools/call" -> handleToolsCall(id, params, userKey, username, userDisplayName, authHeader);
            case "resources/list" -> handleResourcesList(id);
            case "resources/read" -> handleResourcesRead(id, params);
            default -> {
                if (isNotification) yield null;
                yield errorResponse(id, -32601, "Method not found: " + method);
            }
        };
    }

    private String handleInitialize(JsonNode id) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode serverInfo = mapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = mapper.createObjectNode();
        ObjectNode tools = mapper.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);

        if (resourceRegistry.isAvailable()) {
            capabilities.set("resources", mapper.createObjectNode());
            ObjectNode extensions = mapper.createObjectNode();
            extensions.set("io.modelcontextprotocol/ui", mapper.createObjectNode());
            capabilities.set("extensions", extensions);
        }

        result.set("capabilities", capabilities);
        return successResponse(id, result);
    }

    private String handleToolsList(JsonNode id, String userKey) {
        List<McpTool> tools = toolRegistry.listTools(userKey);
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsArray = mapper.createArrayNode();

        for (McpTool tool : tools) {
            ObjectNode toolNode = mapper.createObjectNode();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", mapper.valueToTree(tool.inputSchema()));

            // Annotations (on every tool)
            ObjectNode annotations = mapper.createObjectNode();
            annotations.put("readOnlyHint", !tool.isWriteTool());
            annotations.put("destructiveHint", tool.isDestructiveTool());
            toolNode.set("annotations", annotations);

            // _meta.ui (on UI-linked tools, only when widget is available)
            if (resourceRegistry.isAvailable() && UI_TOOLS.contains(tool.name())) {
                ObjectNode meta = mapper.createObjectNode();
                ObjectNode ui = mapper.createObjectNode();
                ui.put("resourceUri", resourceRegistry.getResourceUri());
                meta.set("ui", ui);
                toolNode.set("_meta", meta);
            }

            toolsArray.add(toolNode);
        }

        result.set("tools", toolsArray);
        return successResponse(id, result);
    }

    private String handleResourcesList(JsonNode id) {
        String result = resourceRegistry.buildResourcesList();
        if (result == null) {
            return successResponse(id, mapper.createObjectNode());
        }
        try {
            return successResponse(id, mapper.readTree(result));
        } catch (Exception e) {
            return errorResponse(id, -32603, "Failed to build resources list");
        }
    }

    private String handleResourcesRead(JsonNode id, JsonNode params) {
        String uri = params.has("uri") ? params.get("uri").asText() : null;
        if (uri == null || uri.isEmpty()) {
            return errorResponse(id, -32602, "Missing 'uri' in params");
        }
        String result = resourceRegistry.buildResourceRead(uri);
        if (result == null) {
            return errorResponse(id, -32602, "Resource not found: " + uri);
        }
        try {
            return successResponse(id, mapper.readTree(result));
        } catch (Exception e) {
            return errorResponse(id, -32603, "Failed to read resource");
        }
    }

    @SuppressWarnings("unchecked")
    private String handleToolsCall(JsonNode id, JsonNode params, String userKey, String username, String userDisplayName, String authHeader) {
        String toolName = params.has("name") ? params.get("name").asText() : null;
        if (toolName == null) {
            return errorResponse(id, -32602, "Missing 'name' in params");
        }

        String accessError = toolRegistry.checkToolAccess(toolName, userKey);
        if (accessError != null) {
            return errorResponse(id, -32602, accessError);
        }

        McpTool tool = toolRegistry.getTool(toolName);

        Map<String, Object> args = new HashMap<>();
        if (params.has("arguments") && params.get("arguments").isObject()) {
            try {
                args = mapper.convertValue(params.get("arguments"), Map.class);
            } catch (Exception e) {
                return errorResponse(id, -32602, "Invalid arguments: " + e.getMessage());
            }
        }

        try {
            String resultText = tool.execute(args, authHeader);
            // Tool success: return CallToolResult with isError=false
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = mapper.createArrayNode();
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", resultText);
            content.add(textContent);
            result.set("content", content);
            result.put("isError", false);

            // Structured content for UI-linked tools (MCP Apps)
            if (resourceRegistry.isAvailable() && UI_TOOLS.contains(toolName)) {
                try {
                    JsonNode parsed = mapper.readTree(resultText);
                    ObjectNode structured = buildStructuredContent(toolName, parsed, username, userDisplayName);
                    if (structured != null) {
                        result.set("structuredContent", structured);
                    }
                } catch (Exception e) {
                    log.debug("[MCP-APPS] Failed to build structuredContent for {}: {}", toolName, e.getMessage());
                }
            }

            return successResponse(id, result);
        } catch (McpToolException e) {
            // Tool execution error: return CallToolResult with isError=true
            // This is NOT a JSON-RPC error — the protocol call succeeded, the tool itself failed
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = mapper.createArrayNode();
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", "Error: " + e.getMessage());
            content.add(textContent);
            result.set("content", content);
            result.put("isError", true);
            return successResponse(id, result);
        }
    }

    // ── Structured content normalization (MCP Apps) ──────────────────────

    /**
     * Resolve the Jira base URL using the same fallback chain as McpResource:
     * config override → SAL ApplicationProperties → empty string.
     */
    private String resolveBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        try {
            if (applicationProperties != null) {
                return applicationProperties.getBaseUrl().toString();
            }
        } catch (Exception e) {
            // fall through
        }
        return "";
    }

    /**
     * Build the structuredContent node for a UI-linked tool result.
     * Returns null if the data cannot be normalized.
     */
    private ObjectNode buildStructuredContent(String toolName, JsonNode data, String username, String userDisplayName) {
        ObjectNode sc = mapper.createObjectNode();

        // currentUser (needed for "Assign to me" actions)
        ObjectNode currentUser = mapper.createObjectNode();
        currentUser.put("name", username != null ? username : "");
        currentUser.put("displayName", userDisplayName != null ? userDisplayName : "");
        sc.set("currentUser", currentUser);

        // baseUrl (needed to build browse URLs and API calls in the widget)
        sc.put("baseUrl", resolveBaseUrl());

        // Normalize issues
        ArrayNode issues = mapper.createArrayNode();
        int totalCount;

        if ("get_issue".equals(toolName)) {
            // Single issue object at the root
            ObjectNode normalized = normalizeIssue(data);
            if (normalized != null) {
                issues.add(normalized);
            }
            totalCount = issues.size();
        } else {
            // Search-style response: {issues: [...], total: N}
            // Handles: search, get_project_issues, get_board_issues, get_sprint_issues
            JsonNode issuesNode = data.has("issues") ? data.get("issues") : null;
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    ObjectNode normalized = normalizeIssue(issueNode);
                    if (normalized != null) {
                        issues.add(normalized);
                    }
                }
            }
            // Use Jira's total field when available (may be larger than the page)
            if (data.has("total") && data.get("total").isInt()) {
                totalCount = data.get("total").asInt();
            } else {
                totalCount = issues.size();
            }
        }

        sc.set("issues", issues);
        sc.put("totalCount", totalCount);

        return sc;
    }

    /**
     * Normalize a single Jira issue node into the structuredContent issue schema.
     * Returns null if the node does not look like a Jira issue.
     */
    private ObjectNode normalizeIssue(JsonNode issue) {
        if (issue == null || !issue.isObject()) return null;

        String key = issue.has("key") ? issue.get("key").asText(null) : null;
        if (key == null || key.isEmpty()) return null;

        ObjectNode out = mapper.createObjectNode();
        out.put("key", key);

        JsonNode fields = issue.has("fields") ? issue.get("fields") : mapper.createObjectNode();

        // Summary
        out.put("summary", fields.has("summary") ? fields.get("summary").asText("") : "");

        // Status
        ObjectNode statusOut = mapper.createObjectNode();
        JsonNode status = fields.has("status") ? fields.get("status") : null;
        if (status != null && status.isObject()) {
            statusOut.put("name", status.has("name") ? status.get("name").asText("") : "");
            JsonNode statusCategory = status.has("statusCategory") ? status.get("statusCategory") : null;
            if (statusCategory != null && statusCategory.isObject()) {
                statusOut.put("category",
                        statusCategory.has("key") ? statusCategory.get("key").asText("") : "");
                statusOut.put("colorName",
                        statusCategory.has("colorName") ? statusCategory.get("colorName").asText("") : "");
                statusOut.put("categoryName",
                        statusCategory.has("name") ? statusCategory.get("name").asText("") : "");
            } else {
                statusOut.put("category", "");
                statusOut.put("colorName", "");
                statusOut.put("categoryName", "");
            }
        } else {
            statusOut.put("name", "");
            statusOut.put("category", "");
            statusOut.put("colorName", "");
            statusOut.put("categoryName", "");
        }
        out.set("status", statusOut);

        // Priority (as object with name)
        ObjectNode priorityOut = mapper.createObjectNode();
        JsonNode priority = fields.has("priority") ? fields.get("priority") : null;
        priorityOut.put("name", (priority != null && priority.isObject() && priority.has("name"))
                ? priority.get("name").asText("") : "");
        out.set("priority", priorityOut);

        // Issue type — ResponseTrimmer renames issuetype → issue_type, handle both
        JsonNode issueType = null;
        if (fields.has("issue_type")) {
            issueType = fields.get("issue_type");
        } else if (fields.has("issuetype")) {
            issueType = fields.get("issuetype");
        }
        ObjectNode issueTypeOut = mapper.createObjectNode();
        issueTypeOut.put("name", (issueType != null && issueType.isObject() && issueType.has("name"))
                ? issueType.get("name").asText("") : "");
        out.set("issue_type", issueTypeOut);

        // Assignee (nullable)
        JsonNode assignee = fields.has("assignee") ? fields.get("assignee") : null;
        if (assignee != null && assignee.isObject()) {
            ObjectNode assigneeOut = mapper.createObjectNode();
            assigneeOut.put("name", assignee.has("name") ? assignee.get("name").asText("") : "");
            assigneeOut.put("displayName",
                    assignee.has("displayName") ? assignee.get("displayName").asText("") : "");
            out.set("assignee", assigneeOut);
        } else {
            out.set("assignee", null);
        }

        // Reporter (nullable)
        JsonNode reporter = fields.has("reporter") ? fields.get("reporter") : null;
        if (reporter != null && reporter.isObject()) {
            ObjectNode reporterOut = mapper.createObjectNode();
            reporterOut.put("name", reporter.has("name") ? reporter.get("name").asText("") : "");
            reporterOut.put("displayName",
                    reporter.has("displayName") ? reporter.get("displayName").asText("") : "");
            out.set("reporter", reporterOut);
        } else {
            out.set("reporter", null);
        }

        // Description (nullable)
        JsonNode description = fields.has("description") ? fields.get("description") : null;
        if (description != null && !description.isNull()) {
            out.put("description", description.asText(""));
        } else {
            out.set("description", null);
        }

        // Comments — nested as fields.comment.comments[]
        ArrayNode commentsOut = mapper.createArrayNode();
        JsonNode commentWrapper = fields.has("comment") ? fields.get("comment") : null;
        if (commentWrapper != null && commentWrapper.isObject()) {
            JsonNode commentsList = commentWrapper.has("comments")
                    ? commentWrapper.get("comments") : null;
            if (commentsList != null && commentsList.isArray()) {
                for (JsonNode c : commentsList) {
                    ObjectNode cOut = mapper.createObjectNode();
                    // Author
                    JsonNode author = c.has("author") ? c.get("author") : null;
                    if (author != null && author.isObject()) {
                        ObjectNode authorOut = mapper.createObjectNode();
                        authorOut.put("name", author.has("name") ? author.get("name").asText("") : "");
                        authorOut.put("displayName",
                                author.has("displayName") ? author.get("displayName").asText("") : "");
                        cOut.set("author", authorOut);
                    } else {
                        cOut.set("author", null);
                    }
                    cOut.put("body", c.has("body") ? c.get("body").asText("") : "");
                    cOut.put("created", c.has("created") ? c.get("created").asText("") : "");
                    cOut.put("updated", c.has("updated") ? c.get("updated").asText("") : "");
                    commentsOut.add(cOut);
                }
            }
        }
        out.set("comments", commentsOut);

        // Timestamps
        out.put("created", fields.has("created") ? fields.get("created").asText("") : "");
        out.put("updated", fields.has("updated") ? fields.get("updated").asText("") : "");

        return out;
    }

    private String successResponse(JsonNode id, Object result) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", JSONRPC);
            response.set("id", id);
            response.set("result", mapper.valueToTree(result));
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}";
        }
    }

    /** Resolve a tools/call request into its components, for streaming execution by McpResource. */
    @SuppressWarnings("unchecked")
    public ToolCallInfo resolveToolCall(String jsonBody, String userKey) {
        try {
            JsonNode request = mapper.readTree(jsonBody);
            String method = request.has("method") ? request.get("method").asText() : null;
            if (!"tools/call".equals(method)) return null;

            JsonNode id = request.has("id") ? request.get("id") : null;
            JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();
            String toolName = params.has("name") ? params.get("name").asText() : null;
            if (toolName == null) return null;

            String accessError = toolRegistry.checkToolAccess(toolName, userKey);
            if (accessError != null) return null;

            McpTool tool = toolRegistry.getTool(toolName);
            if (tool == null || !tool.supportsProgress()) return null;

            // Extract progressToken from params._meta.progressToken
            JsonNode meta = params.path("_meta");
            if (!meta.has("progressToken")) return null;

            JsonNode tokenNode = meta.get("progressToken");
            Object progressToken = tokenNode.isTextual() ? tokenNode.asText() : tokenNode.asInt();

            Map<String, Object> args = new HashMap<>();
            if (params.has("arguments") && params.get("arguments").isObject()) {
                args = mapper.convertValue(params.get("arguments"), Map.class);
            }

            return new ToolCallInfo(id, tool, args, progressToken);
        } catch (Exception e) {
            return null;
        }
    }

    /** Holds resolved tool call components for streaming execution. */
    public record ToolCallInfo(JsonNode id, McpTool tool, Map<String, Object> args, Object progressToken) {}

    /** Build a progress notification JSON-RPC message. */
    public String buildProgressNotification(Object progressToken, int progress, int total, String message) {
        try {
            ObjectNode notification = mapper.createObjectNode();
            notification.put("jsonrpc", JSONRPC);
            notification.put("method", "notifications/progress");
            ObjectNode params = mapper.createObjectNode();
            if (progressToken instanceof String s) {
                params.put("progressToken", s);
            } else {
                params.put("progressToken", ((Number) progressToken).intValue());
            }
            params.put("progress", progress);
            if (total >= 0) params.put("total", total);
            if (message != null) params.put("message", message);
            notification.set("params", params);
            return mapper.writeValueAsString(notification);
        } catch (Exception e) {
            return null;
        }
    }

    /** Build a CallToolResult JSON-RPC response. */
    public String buildToolResult(JsonNode id, String resultText, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = mapper.createArrayNode();
        ObjectNode textContent = mapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", isError ? "Error: " + resultText : resultText);
        content.add(textContent);
        result.set("content", content);
        result.put("isError", isError);
        return successResponse(id, result);
    }

    String errorResponse(JsonNode id, int code, String message) {
        try {
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", JSONRPC);
            response.set("id", id);
            ObjectNode error = mapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            response.set("error", error);
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Serialization error\"}}";
        }
    }
}
