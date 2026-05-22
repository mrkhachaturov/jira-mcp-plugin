package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Adds security headers to every MCP response. */
@Named("mcpSecurityHeadersFilter")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResp = (HttpServletResponse) resp;
        httpResp.setHeader("X-Content-Type-Options", "nosniff");
        httpResp.setHeader("Cache-Control", "no-store");
        httpResp.setHeader("X-Frame-Options", "DENY");
        chain.doFilter(req, resp);
    }
}
