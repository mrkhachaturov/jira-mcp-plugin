package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.sal.api.ApplicationProperties;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filter with location="before-login" that:
 * 1. Passes through /plugins/servlet/mcp-oauth/* requests (handled by OAuthServlet)
 * 2. Directly serves /.well-known/oauth-* responses (can't use servlets at root)
 */
@UnrestrictedAccess
public class OAuthAnonymousFilter implements Filter {

    public OAuthAnonymousFilter() {
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        // Handle /.well-known/* directly — servlets can't serve at root
        if (uri.contains("/.well-known/oauth-")) {
            handleWellKnown(uri, resp);
            return;
        }

        // Everything else (mcp-oauth servlet) — pass through
        chain.doFilter(request, response);
    }

    private void handleWellKnown(String uri, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");

        String baseUrl = getBaseUrl();
        String oauthBase = baseUrl + "/plugins/servlet/mcp-oauth";

        if (uri.contains("oauth-protected-resource")) {
            resp.getWriter().write("{\"resource\":\"" + baseUrl + "/rest/mcp/1.0/\","
                    + "\"authorization_servers\":[\"" + oauthBase + "\"]}");

        } else if (uri.contains("oauth-authorization-server")) {
            resp.getWriter().write("{\"issuer\":\"" + oauthBase + "\","
                    + "\"authorization_endpoint\":\"" + oauthBase + "/authorize\","
                    + "\"token_endpoint\":\"" + oauthBase + "/token\","
                    + "\"registration_endpoint\":\"" + oauthBase + "/register\","
                    + "\"response_types_supported\":[\"code\"],"
                    + "\"grant_types_supported\":[\"authorization_code\"],"
                    + "\"token_endpoint_auth_methods_supported\":[\"none\"],"
                    + "\"code_challenge_methods_supported\":[\"S256\"],"
                    + "\"scopes_supported\":[\"WRITE\",\"READ\"]}");
        } else {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Not found\"}");
        }
    }

    private String getBaseUrl() {
        McpPluginConfig config = getConfig();
        if (config != null) {
            String override = config.getJiraBaseUrlOverride();
            if (override != null && !override.isEmpty()) return override;
        }
        try {
            ApplicationProperties props = ComponentAccessor.getOSGiComponentInstanceOfType(ApplicationProperties.class);
            if (props != null) return props.getBaseUrl().toString();
        } catch (Exception e) { /* fall through */ }
        return "";
    }

    private McpPluginConfig getConfig() {
        try {
            return ComponentAccessor.getOSGiComponentInstanceOfType(McpPluginConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void destroy() {
    }
}
