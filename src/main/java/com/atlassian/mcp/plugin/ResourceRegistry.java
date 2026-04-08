package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Registry for ui:// MCP Apps resources.
 *
 * Loads the issue-card widget HTML from classpath on plugin init, computes a
 * SHA-256 content hash for cache-busting, and serves both the resources/list
 * and resources/read MCP responses.
 *
 * If the HTML file is absent the registry stays empty — MCP Apps is silently
 * disabled and all other plugin functionality continues unaffected.
 */
@Named
public class ResourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ResourceRegistry.class);
    private static final String HTML_CLASSPATH = "/mcp-app/issue-card.html";
    private static final String MIME_TYPE = "text/html;profile=mcp-app";

    private final McpPluginConfig config;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper mapper = new ObjectMapper();

    private String html;
    private String resourceUri;

    // ── Constructor ──────────────────────────────────────────────────────

    @Inject
    public ResourceRegistry(McpPluginConfig config,
                            @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.applicationProperties = applicationProperties;
        loadWidget();
    }

    // ── Init ─────────────────────────────────────────────────────────────

    private void loadWidget() {
        try (InputStream is = getClass().getResourceAsStream(HTML_CLASSPATH)) {
            if (is == null) {
                log.warn("[MCP-APPS] Widget HTML not found at classpath:{} — MCP Apps disabled",
                        HTML_CLASSPATH);
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
     * Build the JSON result payload for a {@code resources/list} response.
     * Returns {@code null} when MCP Apps is unavailable.
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
     * Build the JSON result payload for a {@code resources/read} response.
     * Returns {@code null} when the URI does not match or MCP Apps is unavailable.
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

    /** Build the _meta object for resource content — shared between resources/list and resources/read. */
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
        meta.put("openai/widgetDescription",
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
     * Resolve the Jira base URL using the same fallback chain as {@link rest.McpResource}:
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
