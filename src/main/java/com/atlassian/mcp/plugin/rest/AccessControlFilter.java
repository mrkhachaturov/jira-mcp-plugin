package com.atlassian.mcp.plugin.rest;

import com.atlassian.annotations.security.UnrestrictedAccess;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
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
import java.util.Collection;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces the admin-config access policy:
 *
 * <ul>
 *   <li>503 if MCP is globally disabled.
 *   <li>401 if no authenticated user. If OAuth is configured, includes a {@code WWW-Authenticate:
 *       Bearer resource_metadata=...} header.
 *   <li>403 if the authenticated user is not in {@code allowedUsers} nor in any {@code
 *       allowedGroups} (when either list is non-empty).
 * </ul>
 *
 * <p>Ports the logic from {@code McpResource.checkAuth} + {@code McpResource.isAccessAllowed} on
 * the pre-Task-2 branch.
 */
@UnrestrictedAccess
@Named("mcpAccessControlFilter")
public class AccessControlFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(AccessControlFilter.class);

  private final McpPluginConfig config;
  private final UserManager userManager;
  private final GroupManager groupManager;

  @Inject
  public AccessControlFilter(
      McpPluginConfig config,
      @ComponentImport UserManager userManager,
      @ComponentImport GroupManager groupManager) {
    this.config = config;
    this.userManager = userManager;
    this.groupManager = groupManager;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpReq = (HttpServletRequest) req;
    HttpServletResponse httpResp = (HttpServletResponse) resp;

    if (!config.isEnabled()) {
      log.debug("[MCP-SEC] request rejected — MCP server disabled");
      httpResp.setHeader("Content-Type", "application/json");
      httpResp.setStatus(503);
      httpResp.getWriter().write("{\"error\":\"MCP server is disabled\"}");
      return;
    }

    UserProfile user;
    try {
      user = userManager.getRemoteUser(httpReq);
    } catch (Exception e) {
      user = null;
    }
    if (user == null) {
      log.warn("[MCP-SEC] unauthenticated request from {}", clientIp(httpReq));
      httpResp.setHeader("Content-Type", "application/json");
      httpResp.setStatus(401);
      // F-06 / SEP-835: advertise the scope a client should request. This must match the
      // single scope registered on the Jira "MCP" Application Link (WRITE — which already
      // grants read), since the OAuth provider validates the requested scope against the
      // registered set. The plugin's access model is binary (allowlisted user = full
      // access), so we cannot indicate which specific scope was insufficient mid-request.
      // Full per-tool scope enforcement is deferred to v1.5+.
      String challenge = "Bearer realm=\"jira-mcp\", scope=\"WRITE\"";
      if (config.isOAuthEnabled()) {
        String resourceMetadata = "/plugins/servlet/mcp-oauth/protected-resource";
        challenge = challenge + ", resource_metadata=\"" + resourceMetadata + "\"";
      }
      httpResp.setHeader("WWW-Authenticate", challenge);
      httpResp.getWriter().write("{\"error\":\"Authentication required\"}");
      return;
    }

    UserKey userKey = userManager.getRemoteUserKey(httpReq);
    String username = user.getUsername();
    if (!isAccessAllowed(username, userKey == null ? null : userKey.getStringValue())) {
      log.warn("[MCP-SEC] user '{}' not allowed", username);
      // F-06 / SEP-835: on 403 we don't know which scope was insufficient — emit a bare
      // Bearer challenge per RFC 6750 §3. Per-tool insufficient_scope challenges deferred
      // to v1.5+ once a real scope ↔ tool mapping is wired through tool dispatch.
      httpResp.setHeader("WWW-Authenticate", "Bearer realm=\"jira-mcp\"");
      httpResp.sendError(HttpServletResponse.SC_FORBIDDEN, "User not allowed");
      return;
    }
    chain.doFilter(req, resp);
  }

  /** Ported verbatim from McpResource.isAccessAllowed. */
  private boolean isAccessAllowed(String username, String userKey) {
    if (userKey != null && config.isUserAllowed(userKey)) return true;
    if (username != null && config.isUserAllowed(username)) return true;
    Set<String> allowedGroups = config.getAllowedGroups();
    if (!allowedGroups.isEmpty() && username != null) {
      Collection<Group> userGroups = groupManager.getGroupsForUser(username);
      for (Group group : userGroups) {
        if (allowedGroups.contains(group.getName())) return true;
      }
    }
    return false;
  }

  private static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }
}
