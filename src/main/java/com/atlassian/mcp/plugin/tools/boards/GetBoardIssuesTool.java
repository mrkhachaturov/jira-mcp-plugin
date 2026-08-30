package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.atlassian.mcp.plugin.tools.UiBinding;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GetBoardIssuesTool extends TypedTool<GetBoardIssuesTool.Args> {

  private static final int MAX_LIMIT = 50;

  public record Args(
      @ToolArg(value = "The id of the board (e.g. 1001)", required = true) long boardId,
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
              defaultValue = GetSprintIssuesTool.DEFAULT_FIELDS)
          String fields,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0") int startAt,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit,
      @ToolArg("(Optional) Fields to expand, e.g. 'renderedFields', 'transitions', 'changelog'")
          String expand) {}

  private final JiraRestClient client;

  public GetBoardIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetBoardIssuesTool(JiraRestClient client, UiBinding ui) {
    super(Args.class, ui);
    this.client = client;
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
  protected String run(Args args, McpContext context) throws McpToolException {
    StringBuilder query = new StringBuilder("?jql=").append(encode(args.jql()));
    query.append("&maxResults=").append(Math.min(args.limit(), MAX_LIMIT));
    query.append("&startAt=").append(args.startAt());
    query.append("&fields=").append(encode(args.fields()));
    if (args.expand() != null) query.append("&expand=").append(encode(args.expand()));

    return client.get(
        "/rest/agile/1.0/board/" + args.boardId() + "/issue" + query, context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
