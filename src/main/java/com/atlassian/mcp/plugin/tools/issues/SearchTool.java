package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
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

public class SearchTool implements McpTool {
  private static final Pattern ORDER_BY =
      Pattern.compile("\\s+order\\s+by\\s+", Pattern.CASE_INSENSITIVE);

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
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type", "object",
        "properties",
            Map.of(
                "jql",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "JQL query string (Jira Query Language). Examples: - Find Epics: \"issuetype = Epic AND project = PROJ\" - Find issues in Epic: \"parent = PROJ-123\" - Find by status: \"status = 'In Progress' AND project = PROJ\" - Find by assignee: \"assignee = currentUser()\" - Find recently updated: \"updated >= -7d AND project = PROJ\" - Find by label: \"labels = frontend AND project = PROJ\" - Find by priority: \"priority = High AND project = PROJ\""),
                "fields",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "(Optional) Comma-separated fields to return in the results. Use '*all' for all fields, or specify individual fields like 'summary,status,assignee,priority'",
                        "default",
                        "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent"),
                "limit",
                    Map.of(
                        "type",
                        "integer",
                        "description",
                        "Maximum number of results (1-50)",
                        "default",
                        10),
                "start_at",
                    Map.of(
                        "type",
                        "integer",
                        "description",
                        "Starting index for pagination (0-based)",
                        "default",
                        0),
                "projects_filter",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "(Optional) Comma-separated list of project keys to restrict results to. Applied by narrowing the JQL with 'project in (...)'."),
                "expand",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "(Optional) fields to expand. Examples: 'renderedFields', 'transitions', 'changelog'")),
        "required", List.of("jql"));
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
    String jql = (String) args.get("jql");
    if (jql == null || jql.isBlank()) {
      throw new McpToolException("'jql' parameter is required");
    }
    String fields =
        (String)
            args.getOrDefault(
                "fields",
                "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent");
    int limit = getInt(args, "limit", 10);
    int startAt = getInt(args, "start_at", 0);
    String projectsFilter = (String) args.get("projects_filter");
    String expand = (String) args.get("expand");

    jql = restrictToProjects(jql, projectsFilter);

    StringBuilder query = new StringBuilder();
    String sep = "?";
    if (jql != null && !jql.isBlank()) {
      query.append(sep).append("jql=").append(encode(jql));
      sep = "&";
    }
    query.append(sep).append("maxResults=").append(limit);
    sep = "&";
    query.append(sep).append("startAt=").append(startAt);
    sep = "&";
    if (fields != null && !fields.isBlank()) {
      query.append(sep).append("fields=").append(encode(fields));
      sep = "&";
    }
    if (expand != null && !expand.isBlank()) {
      query.append(sep).append("expand=").append(encode(expand));
      sep = "&";
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

  private static int getInt(Map<String, Object> args, String key, int defaultVal) {
    Object val = args.get(key);
    if (val instanceof Number n) return n.intValue();
    if (val instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        return defaultVal;
      }
    }
    return defaultVal;
  }
}
