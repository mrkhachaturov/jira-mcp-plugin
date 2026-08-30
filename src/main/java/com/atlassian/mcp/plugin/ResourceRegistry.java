package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.rest.JiraAuthContextExtractor;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for ui:// MCP Apps resources.
 *
 * <p>Loads the issue-card widget HTML from classpath on plugin init, computes a SHA-256 content
 * hash for cache-busting, and serves both the resources/list and resources/read MCP responses.
 *
 * <p>If the HTML file is absent the registry stays empty — MCP Apps is silently disabled and all
 * other plugin functionality continues unaffected.
 */
@Named
public class ResourceRegistry {

  private static final Logger log = LoggerFactory.getLogger(ResourceRegistry.class);
  private static final String HTML_CLASSPATH = "/mcp-app/issue-card.html";
  private static final String MIME_TYPE = "text/html;profile=mcp-app";

  private final McpPluginConfig config;
  private final ApplicationProperties applicationProperties;
  private final JiraRestClient jiraClient;
  private final ObjectMapper mapper = new ObjectMapper();

  private String html;
  private String resourceUri;

  // ── Constructor ──────────────────────────────────────────────────────

  @Inject
  public ResourceRegistry(
      McpPluginConfig config,
      @ComponentImport ApplicationProperties applicationProperties,
      JiraRestClient jiraClient) {
    this.config = config;
    this.applicationProperties = applicationProperties;
    this.jiraClient = jiraClient;
    loadWidget();
  }

  // ── Init ─────────────────────────────────────────────────────────────

  private void loadWidget() {
    try (InputStream is = getClass().getResourceAsStream(HTML_CLASSPATH)) {
      if (is == null) {
        log.warn(
            "[MCP-APPS] Widget HTML not found at classpath:{} — MCP Apps disabled", HTML_CLASSPATH);
        return;
      }
      html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      String hash = sha256(html).substring(0, 12);
      resourceUri = "ui://jira/issue-card@" + hash;
      log.info("[MCP-APPS] Widget loaded: {} ({} bytes)", resourceUri, html.length());
    } catch (Exception e) {
      log.warn("[MCP-APPS] Failed to load widget HTML: {}", e.getMessage());
    }
  }

  // ── Public API ────────────────────────────────────────────────────────

  /** True when the widget HTML was loaded successfully. */
  public boolean isAvailable() {
    return html != null && resourceUri != null;
  }

  /** The canonical ui:// URI for this widget, e.g. {@code ui://jira/issue-card@abc123def456}. */
  public String getResourceUri() {
    return resourceUri;
  }

  /**
   * Build the JSON result payload for a {@code resources/list} response. Returns {@code null} when
   * MCP Apps is unavailable.
   */
  public String buildResourcesList() {
    if (!isAvailable()) {
      return null;
    }
    try {
      ObjectNode result = mapper.createObjectNode();
      ArrayNode resources = mapper.createArrayNode();
      resources.add(buildResourceNode());
      result.set("resources", resources);
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("[MCP-APPS] Failed to serialize resources/list: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Build the JSON result payload for a {@code resources/read} response. Returns {@code null} when
   * the URI does not match or MCP Apps is unavailable.
   */
  public String buildResourceRead(String uri) {
    if (!isAvailable() || !resourceUri.equals(uri)) {
      return null;
    }
    try {
      ObjectNode result = mapper.createObjectNode();
      ArrayNode contents = mapper.createArrayNode();
      ObjectNode content = mapper.createObjectNode();
      content.put("uri", resourceUri);
      content.put("mimeType", MIME_TYPE);
      content.put("text", html);
      content.set("_meta", buildResourceMeta());
      contents.add(content);
      result.set("contents", contents);
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      log.warn("[MCP-APPS] Failed to serialize resources/read: {}", e.getMessage());
      return null;
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  /** Build the resource node for resources/list. */
  private ObjectNode buildResourceNode() {
    ObjectNode node = mapper.createObjectNode();
    node.put("uri", resourceUri);
    node.put("name", "Jira Issue Card");
    node.put("description", "Interactive Jira issue viewer with status transitions and comments");
    node.put("mimeType", MIME_TYPE);
    node.set("_meta", buildResourceMeta());
    return node;
  }

  /**
   * Build the _meta object for resource content — shared between resources/list and resources/read.
   */
  private ObjectNode buildResourceMeta() {
    String baseUrl = resolveBaseUrl();
    ObjectNode meta = mapper.createObjectNode();

    // MCP Apps standard fields (Claude, VS Code, Goose)
    ObjectNode ui = mapper.createObjectNode();
    ui.put("prefersBorder", true);
    ObjectNode csp = mapper.createObjectNode();
    csp.set("connectDomains", mapper.createArrayNode());
    csp.set("resourceDomains", mapper.createArrayNode());
    ui.set("csp", csp);
    meta.set("ui", ui);

    // OpenAI / ChatGPT compatibility fields
    meta.put(
        "openai/widgetDescription",
        "Interactive Jira issue viewer with status transitions and comments");
    meta.put("openai/widgetPrefersBorder", true);
    ObjectNode widgetCsp = mapper.createObjectNode();
    widgetCsp.set("connect_domains", mapper.createArrayNode());
    widgetCsp.set("resource_domains", mapper.createArrayNode());
    meta.set("openai/widgetCSP", widgetCsp);
    meta.put("openai/widgetDomain", baseUrl);

    return meta;
  }

  /**
   * Resolve the Jira base URL using the same fallback chain as {@link rest.McpResource}: config
   * override → SAL ApplicationProperties → empty string.
   */
  private String resolveBaseUrl() {
    String override = config.getJiraBaseUrlOverride();
    if (override != null && !override.isEmpty()) {
      return override;
    }
    try {
      if (applicationProperties != null) {
        return applicationProperties.getBaseUrl(UrlMode.CANONICAL).toString();
      }
    } catch (Exception e) {
      // fall through
    }
    return "";
  }

  /**
   * Build SDK {@code SyncResourceSpecification}s for the resources exposed by the server.
   *
   * <p>Currently emits a single spec for the {@code ui://jira/issue-card@{hash}} widget. The read
   * handler matches the request URI against the registered widget and returns the cached HTML
   * wrapped in a {@link McpSchema.TextResourceContents}, with the same dual metadata block ({@code
   * _meta.ui.*} + {@code _meta.openai/widget*}) attached to both the {@code Resource} (for {@code
   * resources/list}) and the read result (for {@code resources/read}). Returns an empty list when
   * the widget HTML failed to load.
   */
  public List<SyncResourceSpecification> toSpecifications() {
    if (!isAvailable()) {
      return List.of();
    }

    Map<String, Object> metaMap = jsonObjectToMap(buildResourceMeta());

    McpSchema.Resource resource =
        McpSchema.Resource.builder(resourceUri, "Jira Issue Card")
            .description("Interactive Jira issue viewer with status transitions and comments")
            .mimeType(MIME_TYPE)
            .meta(metaMap)
            .build();

    java.util.function.BiFunction<
            io.modelcontextprotocol.server.McpSyncServerExchange,
            McpSchema.ReadResourceRequest,
            McpSchema.ReadResourceResult>
        readHandler =
            (exchange, request) -> {
              if (request == null || !resourceUri.equals(request.uri())) {
                String requested = request == null ? "null" : request.uri();
                throw McpError.RESOURCE_NOT_FOUND.apply(requested);
              }
              McpSchema.TextResourceContents content =
                  McpSchema.TextResourceContents.builder(resourceUri, html)
                      .mimeType(MIME_TYPE)
                      .meta(metaMap)
                      .build();
              return McpSchema.ReadResourceResult.builder(List.of(content)).meta(metaMap).build();
            };

    return List.of(new SyncResourceSpecification(resource, readHandler));
  }

  /**
   * Convert a Jackson {@link ObjectNode} to a plain {@link Map} so it can be passed to SDK record
   * constructors that expect {@code Map<String, Object>} (the SDK's Jackson serializer handles
   * either shape, but Maps avoid an extra round-trip).
   */
  private Map<String, Object> jsonObjectToMap(ObjectNode node) {
    try {
      return mapper.convertValue(
          node, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      log.warn("[MCP-APPS] Failed to convert _meta to Map: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  /**
   * F-10: Resource templates exposed by the server.
   *
   * <p>Currently a single template, {@code jira://issue/{issueKey}}, which fetches an issue by key
   * from Jira's REST API on read and returns the trimmed JSON (response trimming is applied
   * automatically inside {@link JiraRestClient}). The read handler resolves the user via the {@link
   * McpSchema.ReadResourceRequest} URI rather than via the request's arguments — clients fill the
   * {@code {issueKey}} placeholder client-side and send a concrete URI like {@code
   * jira://issue/PROJ-123} on {@code resources/read}. The auth header is pulled from the
   * per-request transport context populated by {@link JiraAuthContextExtractor}.
   */
  public List<SyncResourceTemplateSpecification> toResourceTemplateSpecifications() {
    McpSchema.ResourceTemplate template =
        McpSchema.ResourceTemplate.builder("jira://issue/{issueKey}", "Jira Issue")
            .description(
                "Fetch a Jira issue by key (e.g. jira://issue/PROJ-123). "
                    + "Returns the trimmed Jira REST v2 issue JSON.")
            .mimeType("application/json")
            .build();

    java.util.function.BiFunction<
            io.modelcontextprotocol.server.McpSyncServerExchange,
            McpSchema.ReadResourceRequest,
            McpSchema.ReadResourceResult>
        readHandler =
            (exchange, request) -> {
              String uri = request == null ? null : request.uri();
              if (uri == null || !uri.startsWith("jira://issue/")) {
                throw McpError.RESOURCE_NOT_FOUND.apply(String.valueOf(uri));
              }
              String key = uri.substring("jira://issue/".length()).trim();
              if (key.isEmpty()) {
                throw McpError.RESOURCE_NOT_FOUND.apply(uri);
              }

              String authHeader = null;
              try {
                Object v =
                    exchange.transportContext().get(JiraAuthContextExtractor.CTX_AUTH_HEADER);
                authHeader = v instanceof String s ? s : null;
              } catch (Exception ignored) {
                // best-effort
              }

              try {
                String json = jiraClient.get("/rest/api/2/issue/" + key, authHeader);
                McpSchema.TextResourceContents content =
                    McpSchema.TextResourceContents.builder(uri, json)
                        .mimeType("application/json")
                        .build();
                return McpSchema.ReadResourceResult.builder(List.of(content)).build();
              } catch (McpToolException ex) {
                log.debug("[MCP] resource template read failed for {}: {}", uri, ex.getMessage());
                throw McpError.RESOURCE_NOT_FOUND.apply(uri);
              }
            };

    return List.of(new SyncResourceTemplateSpecification(template, readHandler));
  }

  private static String sha256(String input) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder();
    for (byte b : hash) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
