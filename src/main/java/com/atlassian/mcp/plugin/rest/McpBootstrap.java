package com.atlassian.mcp.plugin.rest;

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
 *
 * <p>Plan divergences (vs docs/rkstack/plans/2026-05-21-jakarta-sdk-rebuild.md):
 * <ul>
 *   <li>Json mapper construction uses {@code new JacksonMcpJsonMapper(mapper)} (direct
 *       constructor verified via javap) instead of {@code new JacksonMcpJsonMapperSupplier(mapper).get()}
 *       — {@code JacksonMcpJsonMapperSupplier} only has a no-arg constructor.</li>
 *   <li>Schema validator uses {@code new DefaultJsonSchemaValidator(mapper)} directly.</li>
 *   <li>{@code .resources(List)} is omitted when empty — the SDK builder rejects a
 *       null/empty Map argument; we pass the list only when non-empty.</li>
 * </ul>
 */
@Named("mcpBootstrap")
public class McpBootstrap {

    private static final Logger log = LoggerFactory.getLogger(McpBootstrap.class);

    private static final String SERVER_NAME = "jira-mcp-plugin";
    private static final String SERVER_VERSION = "1.3.0-SNAPSHOT";

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

        var spec = McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .jsonSchemaValidator(schemaValidator)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, true)
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
