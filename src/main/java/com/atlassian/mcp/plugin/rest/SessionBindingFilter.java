package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces the security invariant: an {@code MCP-Session-Id} issued to one Jira
 * user may not be used by another. Cross-user replay returns 403; unknown or
 * expired sessions on non-initialize POSTs return 401.
 *
 * <p>Captures the session id from the {@code initialize} response and binds it
 * to the authenticated Jira user. DELETE removes the binding on success.
 */
@Named("mcpSessionBindingFilter")
public class SessionBindingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SessionBindingFilter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BINDINGS = 200;
    private static final long TTL_MILLIS = 4L * 60 * 60 * 1000; // 4 hours

    private static final ConcurrentHashMap<String, SessionBinding> BINDINGS = new ConcurrentHashMap<>();

    private final UserManager userManager;

    @Inject
    public SessionBindingFilter(@ComponentImport UserManager userManager) {
        this.userManager = userManager;
    }

    private record SessionBinding(String username, long createdAtMillis) {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;

        String currentUser = resolveUsername(httpReq);
        if (currentUser == null) {
            // AccessControlFilter ran before us and should have already rejected anonymous.
            // Belt-and-suspenders: 401 here as well.
            httpResp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String incomingSid = httpReq.getHeader("MCP-Session-Id");
        boolean isPost = "POST".equalsIgnoreCase(httpReq.getMethod());

        // Buffer the body for POSTs so we can both inspect "method":"initialize" AND
        // let the SDK servlet read it intact downstream.
        BufferedRequestWrapper buffered = isPost ? new BufferedRequestWrapper(httpReq) : null;
        boolean isInitialize = isPost && looksLikeInitialize(buffered);

        if (!isInitialize && incomingSid != null) {
            SessionBinding b = BINDINGS.get(incomingSid);
            if (b == null || expired(b)) {
                if (b != null) BINDINGS.remove(incomingSid);
                log.warn("[MCP-SEC] unknown/expired session id from user={}", currentUser);
                httpResp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "session unknown or expired");
                return;
            }
            if (!b.username().equals(currentUser)) {
                log.warn("[MCP-SEC] session-user binding violation: session bound to {} but request from {}",
                        b.username(), currentUser);
                httpResp.sendError(HttpServletResponse.SC_FORBIDDEN, "session bound to a different user");
                return;
            }
        }

        if (isInitialize) {
            CapturingResponseWrapper wrapped = new CapturingResponseWrapper(httpResp);
            chain.doFilter(buffered, wrapped);
            String issuedSid = wrapped.capturedSessionId();
            if (issuedSid != null && wrapped.getStatus() < 400) {
                evictIfFull();
                BINDINGS.put(issuedSid, new SessionBinding(currentUser, System.currentTimeMillis()));
                log.debug("[MCP-SEC] bound session {} to user {}", issuedSid, currentUser);
            }
            return;
        }

        if ("DELETE".equalsIgnoreCase(httpReq.getMethod()) && incomingSid != null) {
            chain.doFilter(req, resp);
            if (httpResp.getStatus() < 400) {
                BINDINGS.remove(incomingSid);
            }
            return;
        }

        chain.doFilter(buffered != null ? buffered : req, resp);
    }

    private boolean looksLikeInitialize(BufferedRequestWrapper buffered) {
        if (buffered == null) return false;
        try {
            JsonNode node = JSON.readTree(buffered.body());
            return node != null && "initialize".equals(node.path("method").asText(null));
        } catch (IOException e) {
            return false; // malformed body — let SDK return 400 on its own
        }
    }

    private boolean expired(SessionBinding b) {
        return System.currentTimeMillis() - b.createdAtMillis() > TTL_MILLIS;
    }

    private void evictIfFull() {
        if (BINDINGS.size() < MAX_BINDINGS) return;
        BINDINGS.entrySet().removeIf(e -> expired(e.getValue()));
        if (BINDINGS.size() >= MAX_BINDINGS) {
            BINDINGS.entrySet().stream()
                    .min((a, b) -> Long.compare(a.getValue().createdAtMillis(), b.getValue().createdAtMillis()))
                    .ifPresent(e -> BINDINGS.remove(e.getKey()));
        }
    }

    private String resolveUsername(HttpServletRequest request) {
        try {
            UserKey key = userManager.getRemoteUserKey(request);
            if (key == null) return null;
            UserProfile profile = userManager.getUserProfile(key);
            return profile == null ? null : profile.getUsername();
        } catch (Exception e) {
            return null;
        }
    }
}
