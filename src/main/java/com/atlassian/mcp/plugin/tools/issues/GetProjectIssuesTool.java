package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.atlassian.mcp.plugin.tools.UiBinding;
import com.atlassian.mcp.plugin.tools.UiToolDefaults;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GetProjectIssuesTool extends TypedTool<GetProjectIssuesTool.Args> {

  private static final int MAX_LIMIT = 50;

  public record Args(
      @ToolArg(value = "Jira project key (e.g. 'PROJ', 'ACV2')", required = true) String projectKey,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0")
          int startAt) {}

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetProjectIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetProjectIssuesTool(JiraRestClient client, UiBinding ui) {
    super(Args.class);
    this.client = client;
    this.ui = ui;
  }

  @Override
  public String uiResourceUri() {
    return ui == null ? null : ui.resourceUri();
  }

  @Override
  public String iconUri() {
    return ui == null ? null : IconConstants.JIRA_LOGO_DATA_URI;
  }

  @Override
  public Map<String, Object> outputSchema() {
    return ui == null ? null : UiToolDefaults.ISSUE_LIST_OUTPUT_SCHEMA;
  }

  @Override
  public ObjectNode structuredContent(
      Map<String, Object> args, String executeResult, String jiraUsername, String jiraUserDisplay) {
    if (ui == null || ui.contextBuilder == null || executeResult == null) return null;
    return ui.contextBuilder.build(name(), executeResult, jiraUsername, jiraUserDisplay);
  }

  @Override
  public String name() {
    return "get_project_issues";
  }

  @Override
  public String description() {
    return "Get all issues for a specific Jira project.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    int limit = Math.min(args.limit(), MAX_LIMIT);
    String jql = "project=" + args.projectKey() + " ORDER BY created DESC";
    String query = "?jql=" + encode(jql) + "&maxResults=" + limit + "&startAt=" + args.startAt();
    return client.get("/rest/api/2/search" + query, context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
