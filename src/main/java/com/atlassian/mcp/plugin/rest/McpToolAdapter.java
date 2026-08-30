package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.CancellationSignal;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts our internal {@link McpTool} interface to the MCP SDK's {@link
 * McpServerFeatures.SyncToolSpecification}.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Wrap each tool with a {@link McpSchema.ToolAnnotations} record built via the SDK's builder
 *       API.
 *   <li>Attach the tool's {@code uiResourceUri()} (if any) as both Claude-style {@code
 *       _meta.ui.resourceUri} and ChatGPT-style {@code openai/widgetResource} hints on the {@link
 *       McpSchema.Tool#meta()} map.
 *   <li>Dispatch {@code tools/call} into {@code tool.executeWithSdkProgress(...)}, reading the auth
 *       header off the per-request {@link io.modelcontextprotocol.common.McpTransportContext}.
 *   <li>Optionally attach a {@code structuredContent} payload from {@link
 *       McpTool#structuredContent(Map, String, String, String)} on success.
 * </ul>
 */
public final class McpToolAdapter {

  private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

  /** Per MCP 2025-11-25 (SEP-1613), the spec defaults to JSON Schema 2020-12. */
  private static final String JSON_SCHEMA_2020_12 = "https://json-schema.org/draft/2020-12/schema";

  private McpToolAdapter() {}

  /**
   * Inject {@code "$schema": "<2020-12 URI>"} at the front of a tool's input/output schema map if
   * not already declared. Returns {@code null} passthrough so callers can use it on optional
   * values.
   */
  private static Map<String, Object> withSchemaDialect(Map<String, Object> raw) {
    if (raw == null || raw.containsKey("$schema")) return raw;
    Map<String, Object> copy = new LinkedHashMap<>(raw.size() + 1);
    copy.put("$schema", JSON_SCHEMA_2020_12);
    copy.putAll(raw);
    return copy;
  }

  /** Infer {@code mimeType} for an icon data URI. Falls back to {@code image/svg+xml}. */
  private static String inferIconMimeType(String uri) {
    if (uri == null) return "image/svg+xml";
    int comma = uri.indexOf(',');
    if (uri.startsWith("data:") && comma > 5) {
      String head = uri.substring(5, comma);
      int semi = head.indexOf(';');
      return semi > 0 ? head.substring(0, semi) : head;
    }
    if (uri.endsWith(".png")) return "image/png";
    if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
    return "image/svg+xml";
  }

  /** Build a {@link McpServerFeatures.SyncToolSpecification} from an internal {@link McpTool}. */
  public static McpServerFeatures.SyncToolSpecification adapt(
      McpTool tool, McpPluginConfig config, McpCancellationRegistry cancellations) {
    McpSchema.ToolAnnotations annotations =
        McpSchema.ToolAnnotations.builder()
            .title(tool.title())
            .readOnlyHint(!tool.isWriteTool())
            .destructiveHint(tool.isDestructiveTool())
            .idempotentHint(tool.idempotentHint())
            .openWorldHint(tool.openWorldHint())
            .build();

    McpSchema.Tool.Builder builder =
        McpSchema.Tool.builder(tool.name(), withSchemaDialect(tool.inputSchema()))
            .title(tool.title())
            .description(tool.description())
            .annotations(annotations);

    Map<String, Object> outputSchema = tool.outputSchema();
    if (outputSchema != null) {
      builder.outputSchema(withSchemaDialect(outputSchema));
    }

    String iconUri = tool.iconUri();
    if (iconUri != null && !iconUri.isEmpty()) {
      builder.icons(
          List.of(
              McpSchema.Icon.builder(iconUri)
                  .mimeType(inferIconMimeType(iconUri))
                  .sizes(List.of("any"))
                  .build()));
    }

    // MCP Apps UI binding (Claude + ChatGPT shapes)
    String uiUri = tool.uiResourceUri();
    if (uiUri != null && !uiUri.isEmpty()) {
      Map<String, Object> meta = new HashMap<>();
      Map<String, Object> uiBlock = new HashMap<>();
      uiBlock.put("resourceUri", uiUri);
      // MCP Apps 2026-01-26 spec §Tool Metadata (L324-344): optional visibility array.
      // Null = omit field, host treats as ["model", "app"] (default per spec L328).
      java.util.List<String> visibility = tool.uiVisibility();
      if (visibility != null && !visibility.isEmpty()) {
        uiBlock.put("visibility", visibility);
      }
      meta.put("ui", uiBlock);
      meta.put("openai/widgetResource", uiUri);
      meta.put("openai/outputTemplate", uiUri);
      builder.meta(meta);
    }

    McpSchema.Tool schemaTool = builder.build();

    return McpServerFeatures.SyncToolSpecification.builder()
        .tool(schemaTool)
        .callHandler(
            (exchange, request) -> dispatch(tool, config, cancellations, exchange, request))
        .build();
  }

  private static McpSchema.CallToolResult dispatch(
      McpTool tool,
      McpPluginConfig config,
      McpCancellationRegistry cancellations,
      McpSyncServerExchange exchange,
      McpSchema.CallToolRequest request) {
    // Call-time guard: the SDK sync server's tool list is frozen at filter init
    // (McpBootstrap.buildTransport -> ToolRegistry.toSpecifications). Re-check admin
    // config here so runtime toggles of read-only mode / disabled tools (via the admin page
    // or the admin REST resource) block write/disabled tools immediately, without a plugin
    // reload — restoring the per-request enforcement the old JAX-RS endpoint provided.
    if (!config.isToolEnabled(tool.name())) {
      return McpSchema.CallToolResult.builder()
          .addTextContent("Error: tool '" + tool.name() + "' is disabled by the administrator")
          .isError(Boolean.TRUE)
          .build();
    }
    if (config.isReadOnlyMode() && tool.isWriteTool()) {
      return McpSchema.CallToolResult.builder()
          .addTextContent(
              "Error: server is in read-only mode; write tool '"
                  + tool.name()
                  + "' is not available")
          .isError(Boolean.TRUE)
          .build();
    }

    String authHeader = readContext(exchange, JiraAuthContextExtractor.CTX_AUTH_HEADER);
    String jiraUser = readContext(exchange, JiraAuthContextExtractor.CTX_JIRA_USER);
    String jiraUserDisplay = readContext(exchange, JiraAuthContextExtractor.CTX_JIRA_USER_DISPLAY);
    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
    Object progressToken = extractProgressToken(request);

    // The call is registered under the id McpTransportFilter read off the wire, so a
    // notifications/cancelled naming it — arriving later, on its own request and its own thread —
    // reaches the tool's next checkpoint. A call with no id (nothing read the envelope) simply
    // cannot be cancelled.
    String requestId = readContext(exchange, JiraAuthContextExtractor.CTX_REQUEST_ID);
    String callKey =
        requestId == null
            ? null
            : McpCancellationRegistry.key(
                readContext(exchange, JiraAuthContextExtractor.CTX_SESSION_ID), requestId);
    CancellationSignal cancellation = CancellationSignal.NONE;
    if (callKey != null) {
      cancellations.begin(callKey);
      cancellation = cancellations.signalFor(callKey);
    }

    try {
      String resultText =
          tool.executeWithSdkProgress(args, authHeader, exchange, progressToken, cancellation);

      Object structured = null;
      try {
        ObjectNode sc = tool.structuredContent(args, resultText, jiraUser, jiraUserDisplay);
        if (sc != null) {
          structured = sc;
        }
      } catch (Exception e) {
        log.debug("[MCP] structuredContent for '{}' failed: {}", tool.name(), e.getMessage());
      }

      McpSchema.CallToolResult.Builder okBuilder =
          McpSchema.CallToolResult.builder().addTextContent(resultText).isError(Boolean.FALSE);
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
    } finally {
      if (callKey != null) {
        cancellations.end(callKey);
      }
    }
  }

  /**
   * Extract the {@code progressToken} from the request's {@code _meta} map, if any. Returns {@code
   * null} when the client did not request progress notifications.
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
