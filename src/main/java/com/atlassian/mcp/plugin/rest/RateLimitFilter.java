package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-user (or per-IP for anonymous) rate limit on the MCP endpoint. 120/min.
 * Returns 429 on overflow. Reuses the shared {@link RateLimiter} component.
 */
@Named("mcpRateLimitFilter")
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int LIMIT_PER_MIN = 120;
    private static final String ENDPOINT = "mcp";

    private final RateLimiter rateLimiter;
    private final UserManager userManager;

    @Inject
    public RateLimitFilter(RateLimiter rateLimiter,
                           @ComponentImport UserManager userManager) {
        this.rateLimiter = rateLimiter;
        this.userManager = userManager;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        String bucket;
        try {
            UserKey key = userManager.getRemoteUserKey(httpReq);
            bucket = (key != null) ? "u:" + key.getStringValue() : "ip:" + clientIp(httpReq);
        } catch (Exception e) {
            bucket = "ip:" + clientIp(httpReq);
        }
        if (!rateLimiter.isAllowed(bucket, ENDPOINT, LIMIT_PER_MIN)) {
            log.warn("[MCP-SEC] rate limit exceeded for bucket={}", bucket);
            ((HttpServletResponse) resp).sendError(429, "Rate limit exceeded");
            return;
        }
        chain.doFilter(req, resp);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
