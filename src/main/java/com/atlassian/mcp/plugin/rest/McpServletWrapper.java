package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlassian-managed wrapper servlet that delegates every request to the MCP SDK
 * transport servlet ({@link io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider}).
 *
 * <p>Declared in {@code atlassian-plugin.xml} as the {@code mcp-transport} servlet
 * with url-pattern {@code /mcp}. Atlassian's plugin framework instantiates this
 * via spring-scanner (Spring DI), so {@link McpBootstrap} is injected and the
 * SDK transport is built lazily on first request.
 *
 * <p>Plan divergence: The plan called for {@code McpPluginLifecycle} +
 * {@code ServletModuleManager.registerServlet(...)} programmatic registration.
 * The Jira 11 plugins-servlet API exposes
 * {@code addServlet(Plugin, String, HttpServlet, ServletContext)} — not
 * {@code registerServlet(String, String, HttpServlet)} — and requires acquiring
 * both a {@link com.atlassian.plugin.Plugin} reference and a
 * {@link jakarta.servlet.ServletContext} at lifecycle-start time. The wrapper
 * pattern lets us re-use the standard {@code <servlet>} descriptor + spring-scanner
 * DI flow (same as {@code OAuthServlet} and {@code AdminServlet}) and is
 * functionally equivalent: the wrapper IS the registered servlet, and its
 * {@code service()} delegates to the SDK transport's {@code service()}.
 */
@Named("mcpServletWrapper")
public class McpServletWrapper extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(McpServletWrapper.class);

    private final McpBootstrap bootstrap;
    private volatile HttpServlet delegate;

    @Inject
    public McpServletWrapper(McpBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        // Eagerly build so the first request doesn't pay the cost.
        delegate = bootstrap.buildTransport();
        // Forward the ServletConfig so the SDK transport sees the same servlet context.
        delegate.init(getServletConfig());
        log.info("[MCP] McpServletWrapper initialized — delegate={}", delegate.getClass().getName());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpServlet d = delegate;
        if (d == null) {
            // Lazy fallback (init() may not have been called for non-Atlassian containers).
            d = bootstrap.buildTransport();
            d.init(getServletConfig());
            delegate = d;
        }
        d.service(req, resp);
    }

    @Override
    public void destroy() {
        HttpServlet d = delegate;
        if (d != null) {
            try {
                d.destroy();
            } catch (Exception e) {
                log.warn("[MCP] error destroying SDK transport", e);
            }
        }
        bootstrap.close();
        delegate = null;
        super.destroy();
    }
}
