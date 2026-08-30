package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
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

  /** Absent from the SDK at every published version, so the plugin routes it itself. */
  private static final String METHOD_CANCELLED = "notifications/cancelled";

  private static final String MCP_SESSION_ID = HttpHeaders.MCP_SESSION_ID;
  private static final String METHOD_CALL_TOOL = McpSchema.METHOD_TOOLS_CALL;

  /** Enough for any JSON-RPC envelope this plugin answers; a larger body is left to the SDK. */
  private static final int MAX_PEEKED_BODY = 1024 * 1024;

  static final String ATTR_REQUEST_ID = "com.atlassian.mcp.requestId";
  static final String ATTR_SESSION_ID = "com.atlassian.mcp.sessionId";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final McpBootstrap bootstrap;
  private final McpCancellationRegistry registry;
  private volatile HttpServlet delegate;

  @Inject
  public McpTransportFilter(McpBootstrap bootstrap, McpCancellationRegistry registry) {
    this.bootstrap = bootstrap;
    this.registry = registry;
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
    if ("POST".equalsIgnoreCase(httpReq.getMethod())) {
      httpReq = CachedBodyHttpServletRequest.caching(httpReq, MAX_PEEKED_BODY);
      routeCancellation(httpReq);
    }
    // Do NOT call chain.doFilter(). This filter is the endpoint — the SDK
    // transport's service() owns the response (including AsyncContext for
    // non-initialize requests).
    d.service(httpReq, httpResp);
  }

  /**
   * Reads the JSON-RPC envelope and acts on the two methods the SDK cannot pass on.
   *
   * <p>The SDK has no notion of {@code notifications/cancelled} — the method appears nowhere in it
   * — so a caller pressing stop reaches a handler only if the notification is read here, before the
   * transport routes the message and drops it as unknown. The id of a {@code tools/call} is stashed
   * alongside it so the same call can be recognised when the cancellation arrives.
   *
   * <p>Never throws: a body that is absent, oversized or malformed simply means no cancellation
   * routing for that request, and the SDK still answers it as it always did.
   */
  private void routeCancellation(HttpServletRequest request) {
    String body = CachedBodyHttpServletRequest.bodyOf(request);
    if (body == null || body.isBlank()) {
      return;
    }
    try {
      JsonNode envelope = MAPPER.readTree(body);
      String method = envelope.path("method").asText("");
      String sessionId = request.getHeader(MCP_SESSION_ID);

      if (METHOD_CANCELLED.equals(method)) {
        JsonNode cancelled = envelope.path("params").path("requestId");
        if (!cancelled.isMissingNode()) {
          String reason = envelope.path("params").path("reason").asText("cancelled by the caller");
          registry.cancel(McpCancellationRegistry.key(sessionId, cancelled.asText()), reason);
        }
      } else if (METHOD_CALL_TOOL.equals(method) && !envelope.path("id").isMissingNode()) {
        request.setAttribute(ATTR_REQUEST_ID, envelope.path("id").asText());
        request.setAttribute(ATTR_SESSION_ID, sessionId);
      }
    } catch (Exception e) {
      log.debug("[MCP] could not read the JSON-RPC envelope for cancellation routing", e);
    }
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
