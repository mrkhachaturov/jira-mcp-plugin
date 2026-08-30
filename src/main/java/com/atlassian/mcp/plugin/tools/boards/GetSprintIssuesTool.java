package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.atlassian.mcp.plugin.tools.UiBinding;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GetSprintIssuesTool extends TypedTool<GetSprintIssuesTool.Args> {

  private static final int MAX_LIMIT = 50;

  static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

  public record Args(
      @ToolArg(value = "The id of the sprint (e.g. 10001)", required = true) long sprintId,
      @ToolArg(
              value =
                  "(Optional) Comma-separated fields to return. Use '*all' for all fields, or name"
                      + " individual fields like 'summary,status,assignee,priority'",
              defaultValue = DEFAULT_FIELDS)
          String fields,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0") int startAt,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit) {}

  private final JiraRestClient client;

  public GetSprintIssuesTool(JiraRestClient client) {
    this(client, null);
  }

  public GetSprintIssuesTool(JiraRestClient client, UiBinding ui) {
    super(Args.class, ui);
    this.client = client;
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
  protected String run(Args args, McpContext context) throws McpToolException {
    StringBuilder query =
        new StringBuilder("?maxResults=").append(Math.min(args.limit(), MAX_LIMIT));
    query.append("&startAt=").append(args.startAt());
    query.append("&fields=").append(encode(args.fields()));

    return client.get(
        "/rest/agile/1.0/sprint/" + args.sprintId() + "/issue" + query, context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
