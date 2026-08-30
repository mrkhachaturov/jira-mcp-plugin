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

public class GetBoardIssuesTool extends DeclarativeTool {

  private static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

  private static final ToolParam<String> BOARD_ID =
      ToolParam.string("board_id", "The id of the board (e.g., '1001')").required();
  private static final ToolParam<String> JQL =
      ToolParam.string(
              "jql",
              "JQL query string (Jira Query Language). Examples: - Find Epics: \"issuetype = Epic"
                  + " AND project = PROJ\" - Find issues in Epic: \"parent = PROJ-123\" - Find by"
                  + " status: \"status = 'In Progress' AND project = PROJ\" - Find by assignee:"
                  + " \"assignee = currentUser()\" - Find recently updated: \"updated >= -7d AND"
                  + " project = PROJ\" - Find by label: \"labels = frontend AND project = PROJ\" -"
                  + " Find by priority: \"priority = High AND project = PROJ\"")
          .required();
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
  private static final ToolParam<String> EXPAND =
      ToolParam.string("expand", "Optional fields to expand in the response (e.g., 'changelog').")
          .withDefault("version");

  private static final int MAX_LIMIT = 50;

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetBoardIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetBoardIssuesTool(JiraRestClient client, UiBinding ui) {
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
    return "get_board_issues";
  }

  @Override
  public String description() {
    return "Get all issues linked to a specific board filtered by JQL.";
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
    return List.of(BOARD_ID, JQL, FIELDS, START_AT, LIMIT, EXPAND);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String boardId = args.require(BOARD_ID);
    String jql = args.require(JQL);
    String fields = args.get(FIELDS);
    int startAt = args.get(START_AT);
    int limit = Math.min(args.get(LIMIT), MAX_LIMIT);
    String expand = args.get(EXPAND);

    StringBuilder query = new StringBuilder("?jql=").append(encode(jql));
    query.append("&maxResults=").append(limit);
    query.append("&startAt=").append(startAt);
    query.append("&fields=").append(encode(fields));
    query.append("&expand=").append(encode(expand));

    return client.get("/rest/agile/1.0/board/" + boardId + "/issue" + query, authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
