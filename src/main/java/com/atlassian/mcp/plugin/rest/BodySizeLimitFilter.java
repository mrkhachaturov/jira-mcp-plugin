package com.atlassian.mcp.plugin.rest;

import jakarta.inject.Named;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caps the MCP POST body at 1 MiB. Returns 413 on overflow. */
@Named("mcpBodySizeLimitFilter")
public class BodySizeLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(BodySizeLimitFilter.class);
    private static final long MAX_BYTES = 1024L * 1024L;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        long len = httpReq.getContentLengthLong();
        if (len > MAX_BYTES) {
            log.warn("[MCP-SEC] body too large: {} bytes (max {} bytes)", len, MAX_BYTES);
            ((HttpServletResponse) resp).sendError(
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request body too large");
            return;
        }
        chain.doFilter(req, resp);
    }
}
