package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.atlassian.mcp.plugin.tools.ToolRegistry;
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

@Named
public class JsonRpcHandler {

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

    @Inject
    public JsonRpcHandler(ToolRegistry toolRegistry, McpPluginConfig config, ResourceRegistry resourceRegistry) {
        this.toolRegistry = toolRegistry;
        this.config = config;
        this.resourceRegistry = resourceRegistry;
    }

    /**
     * Handle a JSON-RPC request. Returns null for notifications (caller should return 202).
     */
    public String handle(String jsonBody, String userKey, String authHeader) {
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
            case "tools/call" -> handleToolsCall(id, params, userKey, authHeader);
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
            ObjectNode experimental = mapper.createObjectNode();
            experimental.set("io.modelcontextprotocol/ui", mapper.createObjectNode());
            capabilities.set("experimental", experimental);
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
    private String handleToolsCall(JsonNode id, JsonNode params, String userKey, String authHeader) {
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
