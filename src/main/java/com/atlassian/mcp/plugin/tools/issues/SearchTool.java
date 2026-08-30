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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchTool extends TypedTool<SearchTool.Args> {

  private static final Pattern ORDER_BY =
      Pattern.compile("\\s+order\\s+by\\s+", Pattern.CASE_INSENSITIVE);

  static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

  public record Args(
      @ToolArg(
              value =
                  "JQL query string (Jira Query Language). Examples: find Epics with \"issuetype ="
                      + " Epic AND project = PROJ\", find issues in an Epic with \"parent ="
                      + " PROJ-123\", find by status with \"status = 'In Progress' AND project ="
                      + " PROJ\", find by assignee with \"assignee = currentUser()\", find recently"
                      + " updated with \"updated >= -7d AND project = PROJ\"",
              required = true)
          String jql,
      @ToolArg(
              value =
                  "(Optional) Comma-separated fields to return. Use '*all' for all fields, or name"
                      + " individual fields like 'summary,status,assignee,priority'",
              defaultValue = DEFAULT_FIELDS)
          String fields,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0") int startAt,
      @ToolArg(
              "(Optional) Project keys to restrict results to, e.g. ['PROJ', 'DEV']. Applied by"
                  + " narrowing the JQL with 'project in (...)'.")
          List<String> projectsFilter,
      @ToolArg("(Optional) Fields to expand, e.g. 'renderedFields', 'transitions', 'changelog'")
          String expand) {}

  private final JiraRestClient client;
  private final UiBinding ui;

  public SearchTool(JiraRestClient client) {
    this(client, null);
  }

  public SearchTool(JiraRestClient client, UiBinding ui) {
    super(Args.class);
    this.client = client;
    this.ui = ui;
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
  protected String run(Args args, McpContext context) throws McpToolException {
    String jql = restrictToProjects(args.jql(), args.projectsFilter());

    StringBuilder query = new StringBuilder();
    String sep = "?";
    if (!jql.isBlank()) {
      query.append(sep).append("jql=").append(encode(jql));
      sep = "&";
    }
    query.append(sep).append("maxResults=").append(args.limit());
    sep = "&";
    query.append(sep).append("startAt=").append(args.startAt());
    if (args.fields() != null && !args.fields().isBlank()) {
      query.append(sep).append("fields=").append(encode(args.fields()));
    }
    if (args.expand() != null && !args.expand().isBlank()) {
      query.append(sep).append("expand=").append(encode(args.expand()));
    }

    return client.get("/rest/api/2/search" + query, context.authHeader());
  }

  /**
   * Narrows a JQL query to the given project keys. Jira's search endpoint has no project parameter,
   * so the restriction has to go into the JQL itself. The trailing ORDER BY clause is split off
   * first — it must stay outside the parenthesised condition.
   */
  private static String restrictToProjects(String jql, List<String> projectsFilter) {
    if (projectsFilter == null || projectsFilter.isEmpty()) return jql;

    List<String> keys = new ArrayList<>();
    for (String key : projectsFilter) {
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
