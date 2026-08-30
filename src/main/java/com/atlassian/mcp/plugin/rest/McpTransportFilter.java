package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter-based transport for the MCP SDK servlet.
 *
 * <p>Atlassian's plugin framework hardcodes {@code asyncSupported=false} on {@code <servlet>}
 * modules: {@code ServletModuleDescriptor} does not override the base class's {@code
 * getDefaultAsyncSupported()} (verified via javap, base returns {@code iconst_0}). There is no XML
 * attribute or system property to enable async on a plugin {@code <servlet>} — servlets registered
 * that way are structurally async-unsupported.
 *
 * <p>{@code <servlet-filter>} modules, in contrast, honor the JVM property {@code
 * atlassian.plugins.filter.async.default=true} (read via {@code Boolean.getBoolean} in {@code
 * ServletFilterModuleDescriptor}'s static init). When that flag is set system-wide, plugin filters
 * become async-supported and can call {@code request.startAsync()}.
 *
 * <p>This filter owns its URL pattern ({@code /plugins/servlet/mcp}), calls the SDK transport
 * servlet's {@link HttpServlet#service(ServletRequest, ServletResponse)} method directly, and never
 * invokes {@link FilterChain#doFilter} — it IS the endpoint, not a wrapper around one. The SDK's
 * transport calls {@code req.startAsync()} for non-initialize JSON-RPC requests; with the JVM flag
 * set, the chain is async-supported and the call succeeds.
 */
@UnrestrictedAccess
@Named("mcpTransportFilter")
public class McpTransportFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(McpTransportFilter.class);

  private final McpBootstrap bootstrap;
  private volatile HttpServlet delegate;

  @Inject
  public McpTransportFilter(McpBootstrap bootstrap) {
    this.bootstrap = bootstrap;
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    HttpServlet d = bootstrap.buildTransport();
    // The SDK servlet expects a real ServletConfig (delegate.getServletConfig() is
    // consulted internally). Wrap the FilterConfig's ServletContext in a synthetic
    // ServletConfig so delegate.init() is satisfied.
    d.init(new FilterBackedServletConfig(filterConfig));
    this.delegate = d;
    log.info("[MCP] McpTransportFilter initialized — delegate={}", d.getClass().getName());
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServlet d = delegate;
    if (d == null) {
      throw new ServletException("McpTransportFilter not initialized");
    }
    HttpServletRequest httpReq = (HttpServletRequest) req;
    HttpServletResponse httpResp = (HttpServletResponse) resp;
    if (log.isDebugEnabled()) {
      log.debug(
          "[MCP] doFilter uri={} dispatcher={} asyncSupported={}",
          httpReq.getRequestURI(),
          httpReq.getDispatcherType(),
          httpReq.isAsyncSupported());
    }
    // Do NOT call chain.doFilter(). This filter is the endpoint — the SDK
    // transport's service() owns the response (including AsyncContext for
    // non-initialize requests).
    d.service(httpReq, httpResp);
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
  }

  /** Synthetic ServletConfig wrapping a FilterConfig — enough for the SDK servlet's init(). */
  private static final class FilterBackedServletConfig implements ServletConfig {
    private final FilterConfig filterConfig;

    FilterBackedServletConfig(FilterConfig filterConfig) {
      this.filterConfig = filterConfig;
    }

    @Override
    public String getServletName() {
      return "mcp-transport";
    }

    @Override
    public ServletContext getServletContext() {
      return filterConfig.getServletContext();
    }

    @Override
    public String getInitParameter(String name) {
      return filterConfig.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return filterConfig.getInitParameterNames();
    }
  }
}
