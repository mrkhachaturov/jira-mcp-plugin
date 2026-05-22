package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts our internal {@link McpTool} interface to the MCP SDK's
 * {@link McpServerFeatures.SyncToolSpecification}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Wrap each tool with a {@link McpSchema.ToolAnnotations} record built via
 *       the SDK's builder API.</li>
 *   <li>Attach the tool's {@code uiResourceUri()} (if any) as both Claude-style
 *       {@code _meta.ui.resourceUri} and ChatGPT-style {@code openai/widgetResource}
 *       hints on the {@link McpSchema.Tool#meta()} map.</li>
 *   <li>Dispatch {@code tools/call} into {@code tool.executeWithSdkProgress(...)},
 *       reading the auth header off the per-request {@link io.modelcontextprotocol.common.McpTransportContext}.</li>
 *   <li>Optionally attach a {@code structuredContent} payload from
 *       {@link McpTool#structuredContent(Map, String, String, String)} on success.</li>
 * </ul>
 */
public final class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private McpToolAdapter() {}

    /** Build a {@link McpServerFeatures.SyncToolSpecification} from an internal {@link McpTool}. */
    public static McpServerFeatures.SyncToolSpecification adapt(McpTool tool) {
        McpSchema.ToolAnnotations annotations = McpSchema.ToolAnnotations.builder()
                .readOnlyHint(!tool.isWriteTool())
                .destructiveHint(tool.isDestructiveTool())
                .build();

        McpSchema.Tool.Builder builder = McpSchema.Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .annotations(annotations);

        // MCP Apps UI binding (Claude + ChatGPT shapes)
        String uiUri = tool.uiResourceUri();
        if (uiUri != null && !uiUri.isEmpty()) {
            Map<String, Object> meta = new HashMap<>();
            Map<String, Object> uiBlock = new HashMap<>();
            uiBlock.put("resourceUri", uiUri);
            meta.put("ui", uiBlock);
            meta.put("openai/widgetResource", uiUri);
            meta.put("openai/outputTemplate", uiUri);
            builder.meta(meta);
        }

        McpSchema.Tool schemaTool = builder.build();

        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(schemaTool)
                .callHandler((exchange, request) -> dispatch(tool, exchange, request))
                .build();
    }

    private static McpSchema.CallToolResult dispatch(McpTool tool,
                                                     McpSyncServerExchange exchange,
                                                     McpSchema.CallToolRequest request) {
        String authHeader = readContext(exchange, JiraAuthContextExtractor.CTX_AUTH_HEADER);
        String jiraUser = readContext(exchange, JiraAuthContextExtractor.CTX_JIRA_USER);
        String jiraUserDisplay = readContext(exchange, JiraAuthContextExtractor.CTX_JIRA_USER_DISPLAY);
        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
        Object progressToken = extractProgressToken(request);

        try {
            String resultText = tool.executeWithSdkProgress(args, authHeader, exchange, progressToken);

            Object structured = null;
            try {
                ObjectNode sc = tool.structuredContent(args, resultText, jiraUser, jiraUserDisplay);
                if (sc != null) {
                    structured = sc;
                }
            } catch (Exception e) {
                log.debug("[MCP] structuredContent for '{}' failed: {}", tool.name(), e.getMessage());
            }

            McpSchema.CallToolResult.Builder okBuilder = McpSchema.CallToolResult.builder()
                    .addTextContent(resultText)
                    .isError(Boolean.FALSE);
            if (structured != null) {
                okBuilder.structuredContent(structured);
            }
            return okBuilder.build();
        } catch (McpToolException e) {
            log.debug("[MCP] tool '{}' failed: {}", tool.name(), e.getMessage());
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Error: " + e.getMessage())
                    .isError(Boolean.TRUE)
                    .build();
        } catch (RuntimeException e) {
            log.warn("[MCP] tool '{}' threw unexpectedly", tool.name(), e);
            return McpSchema.CallToolResult.builder()
                    .addTextContent("Internal error: " + e.getMessage())
                    .isError(Boolean.TRUE)
                    .build();
        }
    }

    /**
     * Extract the {@code progressToken} from the request's {@code _meta} map, if any.
     * Returns {@code null} when the client did not request progress notifications.
     */
    private static Object extractProgressToken(McpSchema.CallToolRequest request) {
        try {
            Map<String, Object> meta = request.meta();
            if (meta == null) return null;
            return meta.get("progressToken");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readContext(McpSyncServerExchange exchange, String key) {
        try {
            Object v = exchange.transportContext().get(key);
            return v instanceof String s ? s : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
