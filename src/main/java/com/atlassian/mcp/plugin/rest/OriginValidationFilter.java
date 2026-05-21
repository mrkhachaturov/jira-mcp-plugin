package com.atlassian.mcp.plugin.rest;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the {@code Origin} header against an allowlist: Jira's own base URL,
 * loopback addresses, and known MCP client hosts (claude.ai / claude.com /
 * chatgpt.com / chat.openai.com). Requests without an Origin (non-browser clients
 * like PAT-using curl or MCP CLI tools) pass through.
 *
 * <p>Returns 403 on rejection. Per MCP spec MUST.
 */
@Named("mcpOriginValidationFilter")
public class OriginValidationFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(OriginValidationFilter.class);
    private static final Set<String> ALWAYS_ALLOWED = Set.of(
            "claude.ai", "claude.com", "chatgpt.com", "chat.openai.com");

    private final McpPluginConfig config;
    private final ApplicationProperties applicationProperties;

    @Inject
    public OriginValidationFilter(McpPluginConfig config,
                                  @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;
        String origin = httpReq.getHeader("Origin");
        if (origin == null || origin.isEmpty()) {
            chain.doFilter(req, resp);
            return;
        }
        try {
            URI u = URI.create(origin);
            String host = u.getHost();
            if (host == null) {
                rejectOrigin(httpResp, origin);
                return;
            }
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                chain.doFilter(req, resp);
                return;
            }
            String jiraHost = jiraHost();
            if (jiraHost != null && jiraHost.equalsIgnoreCase(host)) {
                chain.doFilter(req, resp);
                return;
            }
            if (ALWAYS_ALLOWED.contains(host.toLowerCase())) {
                chain.doFilter(req, resp);
                return;
            }
        } catch (Exception ignored) {
            // fall through to reject
        }
        rejectOrigin(httpResp, origin);
    }

    private String jiraHost() {
        try {
            String override = config.getJiraBaseUrlOverride();
            String base = (override != null && !override.isEmpty())
                    ? override
                    : applicationProperties.getBaseUrl().toString();
            return URI.create(base).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private void rejectOrigin(HttpServletResponse resp, String origin) throws IOException {
        log.warn("[MCP-SEC] origin rejected: {}", origin);
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Origin not allowed");
    }
}
