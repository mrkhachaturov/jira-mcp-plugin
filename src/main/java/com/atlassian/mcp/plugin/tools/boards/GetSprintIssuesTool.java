package com.atlassian.mcp.plugin.tools.boards;

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

public class GetSprintIssuesTool extends DeclarativeTool {

  private static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

  private static final ToolParam<String> SPRINT_ID =
      ToolParam.string("sprint_id", "The id of sprint (e.g., '10001')").required();
  private static final ToolParam<String> FIELDS =
      ToolParam.string(
              "fields",
              "Comma-separated fields to return in the results. Use '*all' for all fields, or"
                  + " specify individual fields like 'summary,status,assignee,priority'")
          .withDefault(DEFAULT_FIELDS);
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(10);

  private static final int MAX_LIMIT = 50;

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetSprintIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetSprintIssuesTool(JiraRestClient client, UiBinding ui) {
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
    return "get_sprint_issues";
  }

  @Override
  public String description() {
    return "Get jira issues from sprint.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.jira.plugins.jira-software-plugin";
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(SPRINT_ID, FIELDS, START_AT, LIMIT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String sprintId = args.require(SPRINT_ID);
    String fields = args.get(FIELDS);
    int startAt = args.get(START_AT);
    int limit = Math.min(args.get(LIMIT), MAX_LIMIT);

    StringBuilder query = new StringBuilder("?maxResults=").append(limit);
    query.append("&startAt=").append(startAt);
    query.append("&fields=").append(encode(fields));

    return client.get("/rest/agile/1.0/sprint/" + sprintId + "/issue" + query, authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
