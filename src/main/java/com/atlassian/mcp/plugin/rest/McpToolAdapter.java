package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts our internal {@link McpTool} interface to the MCP SDK's
 * {@link McpServerFeatures.SyncToolSpecification}.
 *
 * <p>For Task 2 this only handles non-streaming, no-progress execution and a
 * simple {@code TextContent}-based {@code CallToolResult}. Task 3 expands this to
 * (a) full 49-tool registration and (b) progress-aware execution via
 * {@link McpSyncServerExchange#progressNotification(McpSchema.ProgressNotification)}.
 *
 * <p>Per-request auth header and Jira user are read from the
 * {@link io.modelcontextprotocol.common.McpTransportContext} populated by
 * {@link JiraAuthContextExtractor}.
 */
public final class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private McpToolAdapter() {}

    /** Build a {@link McpServerFeatures.SyncToolSpecification} from an internal {@link McpTool}. */
    public static McpServerFeatures.SyncToolSpecification adapt(McpTool tool) {
        McpSchema.ToolAnnotations annotations = new McpSchema.ToolAnnotations(
                /* title */         null,
                /* readOnlyHint */  !tool.isWriteTool(),
                /* destructiveHint */ tool.isDestructiveTool(),
                /* idempotentHint */ null,
                /* openWorldHint */  null,
                /* returnDirect */   null);

        McpSchema.Tool schemaTool = McpSchema.Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .annotations(annotations)
                .build();

        return new McpServerFeatures.SyncToolSpecification(schemaTool, (exchange, request) -> dispatch(tool, exchange, request));
    }

    private static McpSchema.CallToolResult dispatch(McpTool tool,
                                                     McpSyncServerExchange exchange,
                                                     McpSchema.CallToolRequest request) {
        String authHeader = readContext(exchange, JiraAuthContextExtractor.CTX_AUTH_HEADER);
        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
        try {
            String resultText = tool.execute(args, authHeader);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(resultText)),
                    /* isError */ Boolean.FALSE,
                    /* structuredContent */ null,
                    /* meta */ null);
        } catch (McpToolException e) {
            log.debug("[MCP] tool '{}' failed: {}", tool.name(), e.getMessage());
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                    Boolean.TRUE, null, null);
        } catch (RuntimeException e) {
            log.warn("[MCP] tool '{}' threw unexpectedly", tool.name(), e);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Internal error: " + e.getMessage())),
                    Boolean.TRUE, null, null);
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
