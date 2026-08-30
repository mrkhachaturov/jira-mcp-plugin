package com.atlassian.mcp.plugin.rest;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Pulls the Authorization header and the resolved Jira username off each request and stashes them
 * into the per-request {@link McpTransportContext}. Tool handlers (and adapter glue in {@link
 * McpToolAdapter}) read these via {@code
 * exchange.transportContext().contextAsMap().get("authHeader")} etc.
 *
 * <p>This is the principal extraction point. Session-user binding enforcement lives in {@code
 * SessionBindingFilter}, not here.
 */
@Named("jiraAuthContextExtractor")
public class JiraAuthContextExtractor implements McpTransportContextExtractor<HttpServletRequest> {

  public static final String CTX_AUTH_HEADER = "authHeader";
  public static final String CTX_JIRA_USER = "jiraUser";
  public static final String CTX_JIRA_USER_DISPLAY = "jiraUserDisplay";
  public static final String CTX_JIRA_USER_KEY = "jiraUserKey";

  private final UserManager userManager;

  @Inject
  public JiraAuthContextExtractor(@ComponentImport UserManager userManager) {
    this.userManager = userManager;
  }

  @Override
  public McpTransportContext extract(HttpServletRequest request) {
    Map<String, Object> ctx = new HashMap<>(8);
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && !authHeader.isEmpty()) {
      ctx.put(CTX_AUTH_HEADER, authHeader);
    }
    try {
      UserKey key = userManager.getRemoteUserKey(request);
      if (key != null) {
        ctx.put(CTX_JIRA_USER_KEY, key.getStringValue());
        UserProfile profile = userManager.getUserProfile(key);
        if (profile != null) {
          ctx.put(CTX_JIRA_USER, profile.getUsername());
          String full = profile.getFullName();
          ctx.put(CTX_JIRA_USER_DISPLAY, full == null ? profile.getUsername() : full);
        }
      }
    } catch (Exception ignored) {
      // Best-effort: never throw out of the extractor.
    }
    return McpTransportContext.create(ctx);
  }
}
