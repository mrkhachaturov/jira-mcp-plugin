package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.atlassian.mcp.plugin.tools.UiBinding;
import com.atlassian.mcp.plugin.tools.UiToolDefaults;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetProjectIssuesTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "Jira project key (e.g., 'PROJ', 'ACV2')").required();
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(10);
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);

  private static final int MAX_LIMIT = 50;

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetProjectIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetProjectIssuesTool(JiraRestClient client, UiBinding ui) {
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
  public List<ToolParam<?>> params() {
    return List.of(PROJECT_KEY, LIMIT, START_AT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String projectKey = args.require(PROJECT_KEY);
    int limit = Math.min(args.get(LIMIT), MAX_LIMIT);
    int startAt = args.get(START_AT);

    String jql = "project=" + encode(projectKey) + " ORDER BY created DESC";
    String query = "?jql=" + encode(jql) + "&maxResults=" + limit + "&startAt=" + startAt;
    return client.get("/rest/api/2/search" + query, authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
