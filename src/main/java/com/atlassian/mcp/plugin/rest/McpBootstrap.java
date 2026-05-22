package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.ResourceRegistry;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServlet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds (lazily, once) the MCP SDK transport + sync server, holding the configured
 * {@link HttpServlet} for the wrapper servlet ({@link McpServletWrapper}) to delegate into.
 *
 * <p>This replaces the hand-rolled {@code JsonRpcHandler} dispatch and {@code McpResource}
 * JAX-RS endpoint with the official MCP Java SDK (2.0.0-M2). Tools are adapted to
 * {@code SyncToolSpecification} via {@link McpToolAdapter} and registered on the
 * sync server at build time.
 *
 * <p>Smoke-test wiring registers two read-only tools (Task 2). Task 3 expands the
 * registration to the full 49-tool list and adds resource specifications.
 */
@Named("mcpBootstrap")
public class McpBootstrap {

    private static final Logger log = LoggerFactory.getLogger(McpBootstrap.class);

    private static final String SERVER_NAME = "jira-mcp-plugin";
    private static final String SERVER_VERSION = "1.3.0";
    private static final String SERVER_TITLE = "Jira MCP Server";
    private static final String SERVER_DESCRIPTION =
            "Connect AI agents to Jira Data Center — 49 tools across issues, sprints, "
            + "boards, comments, worklogs, attachments, service desk, forms, and dev info. "
            + "Interactive issue cards rendered as MCP Apps widgets in compatible clients.";
    private static final String SERVER_WEBSITE = "https://github.com/mrkhachaturov/jira-mcp-plugin";
    private static final String SERVER_INSTRUCTIONS =
            "This server exposes tools for Jira Data Center.\n"
            + "- For a single issue lookup: use `get_issue` with the issue_key (e.g. PROJ-123).\n"
            + "- For searching across projects: use `search` with a JQL query.\n"
            + "- For project- or board-scoped listings: prefer `get_project_issues`, "
            + "`get_board_issues`, or `get_sprint_issues` over a broad `search`.\n"
            + "- UI-linked tools (`get_issue`, `search`, `get_project_issues`, "
            + "`get_board_issues`, `get_sprint_issues`) return `structuredContent` "
            + "that renders as an interactive widget in MCP-Apps-capable clients "
            + "(Claude Desktop, ChatGPT, VS Code Copilot).\n"
            + "- Write tools (create/update/delete issue, transition, comment, worklog, "
            + "batch operations) are hidden when the admin enables read-only mode.";

    /**
     * Official Jira Software wordmark, used as the server-level icon. The SVG
     * and base64 data-URI live in {@link IconConstants} so they can be reused
     * as per-tool icons on UI-linked tools (F-12 / SEP-973).
     */
    private static final String LOGO_DATA_URI = IconConstants.JIRA_LOGO_DATA_URI;

    private final ToolRegistry toolRegistry;
    private final ResourceRegistry resourceRegistry;
    private final McpPluginConfig config;
    private final JiraAuthContextExtractor authExtractor;

    private volatile HttpServletStreamableServerTransportProvider transport;
    private volatile McpSyncServer server;

    @Inject
    public McpBootstrap(ToolRegistry toolRegistry,
                        ResourceRegistry resourceRegistry,
                        McpPluginConfig config,
                        JiraAuthContextExtractor authExtractor) {
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.config = config;
        this.authExtractor = authExtractor;
    }

    /** Build (idempotent). Returns the configured servlet for the wrapper to delegate into. */
    public synchronized HttpServlet buildTransport() {
        if (transport != null) {
            return transport;
        }

        ObjectMapper mapper = new ObjectMapper();
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
        JsonSchemaValidator schemaValidator = new DefaultJsonSchemaValidator(mapper);

        this.transport = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/plugins/servlet/mcp")
                .contextExtractor(authExtractor)
                .build();

        var serverInfo = McpSchema.Implementation.builder(SERVER_NAME, SERVER_VERSION)
                .title(SERVER_TITLE)
                .description(SERVER_DESCRIPTION)
                .websiteUrl(SERVER_WEBSITE)
                .icons(List.of(McpSchema.Icon.builder(LOGO_DATA_URI)
                        .mimeType("image/svg+xml")
                        .sizes(List.of("any"))
                        .build()))
                .build();

        var spec = McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(schemaValidator)
                .serverInfo(serverInfo)
                .instructions(SERVER_INSTRUCTIONS)
                // F-01 / F-02: we never emit notifications/{tools,resources}/list_changed,
                // so listChanged=false on both. F-16: declare `logging` capability so the
                // SDK auto-wires the `logging/setLevel` handler (McpAsyncServer registers it
                // when serverCapabilities.logging() != null) — clients can set min level,
                // and tool bodies can emit logging notifications via the SyncServerExchange.
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .resources(false, false)
                        .logging()
                        .build())
                .tools(toolRegistry.toSpecifications());

        var resourceSpecs = resourceRegistry.toSpecifications();
        if (resourceSpecs != null && !resourceSpecs.isEmpty()) {
            spec = spec.resources(resourceSpecs);
        }

        this.server = spec.build();

        log.info("[MCP] SDK transport built ({} tools, {} resources)",
                toolRegistry.toSpecifications().size(),
                resourceSpecs == null ? 0 : resourceSpecs.size());

        return transport;
    }

    public synchronized void close() {
        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                log.warn("[MCP] error closing SDK server", e);
            }
            server = null;
        }
        transport = null;
    }
}
