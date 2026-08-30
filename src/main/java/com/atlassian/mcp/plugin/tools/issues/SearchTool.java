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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchTool extends DeclarativeTool {
  private static final Pattern ORDER_BY =
      Pattern.compile("\\s+order\\s+by\\s+", Pattern.CASE_INSENSITIVE);

  private static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

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
              "(Optional) Comma-separated fields to return in the results. Use '*all' for all"
                  + " fields, or specify individual fields like"
                  + " 'summary,status,assignee,priority'")
          .withDefault(DEFAULT_FIELDS);
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(10);
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);
  private static final ToolParam<String> PROJECTS_FILTER =
      ToolParam.string(
          "projects_filter",
          "(Optional) Comma-separated list of project keys to restrict results to. Applied by"
              + " narrowing the JQL with 'project in (...)'.");
  private static final ToolParam<String> EXPAND =
      ToolParam.string(
          "expand",
          "(Optional) fields to expand. Examples: 'renderedFields', 'transitions', 'changelog'");

  private final JiraRestClient client;
  private final UiBinding ui;

  public SearchTool(JiraRestClient client) {
    this(client, null);
  }

  public SearchTool(JiraRestClient client, UiBinding ui) {
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
    return "search";
  }

  @Override
  public String description() {
    return "Search Jira issues using JQL (Jira Query Language).";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(JQL, FIELDS, LIMIT, START_AT, PROJECTS_FILTER, EXPAND);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String jql = args.require(JQL);
    String fields = args.get(FIELDS);
    int limit = args.get(LIMIT);
    int startAt = args.get(START_AT);
    String projectsFilter = args.get(PROJECTS_FILTER);
    String expand = args.get(EXPAND);

    jql = restrictToProjects(jql, projectsFilter);

    StringBuilder query = new StringBuilder();
    String sep = "?";
    if (!jql.isBlank()) {
      query.append(sep).append("jql=").append(encode(jql));
      sep = "&";
    }
    query.append(sep).append("maxResults=").append(limit);
    sep = "&";
    query.append(sep).append("startAt=").append(startAt);
    if (fields != null && !fields.isBlank()) {
      query.append(sep).append("fields=").append(encode(fields));
    }
    if (expand != null && !expand.isBlank()) {
      query.append(sep).append("expand=").append(encode(expand));
    }

    return client.get("/rest/api/2/search" + query, authHeader);
  }

  /**
   * Narrows a JQL query to the given comma-separated project keys. Jira's search endpoint has no
   * project parameter, so the restriction has to go into the JQL itself. The trailing ORDER BY
   * clause is split off first — it must stay outside the parenthesised condition.
   */
  private static String restrictToProjects(String jql, String projectsFilter) {
    if (projectsFilter == null || projectsFilter.isBlank()) return jql;

    List<String> keys = new ArrayList<>();
    for (String key : projectsFilter.split(",")) {
      String trimmed = key.trim();
      if (!trimmed.isEmpty()) keys.add("\"" + trimmed.replace("\"", "\\\"") + "\"");
    }
    if (keys.isEmpty()) return jql;

    Matcher orderBy = ORDER_BY.matcher(jql);
    String condition = jql;
    String suffix = "";
    if (orderBy.find()) {
      condition = jql.substring(0, orderBy.start());
      suffix = jql.substring(orderBy.start());
    }

    String restriction = "project in (" + String.join(", ", keys) + ")";
    return condition.isBlank()
        ? restriction + suffix
        : "(" + condition.trim() + ") AND " + restriction + suffix;
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
