package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GetAgileBoardsTool extends TypedTool<GetAgileBoardsTool.Args> {

  private static final int MAX_LIMIT = 50;

  public record Args(
      @ToolArg("(Optional) The name of board, supports fuzzy search") String boardName,
      @ToolArg("(Optional) Jira project key (e.g. 'PROJ', 'ACV2')") String projectKey,
      @ToolArg(
              value = "(Optional) The type of jira board",
              allowed = {"scrum", "kanban", "simple"})
          String boardType,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0") int startAt,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit) {}

  private final JiraRestClient client;

  public GetAgileBoardsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_agile_boards";
  }

  @Override
  public String description() {
    return "Get jira agile boards by name, project key, or type.";
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
    StringBuilder query = new StringBuilder("?startAt=").append(args.startAt());
    query.append("&maxResults=").append(Math.min(args.limit(), MAX_LIMIT));
    if (args.boardName() != null) query.append("&name=").append(encode(args.boardName()));
    if (args.projectKey() != null) {
      query.append("&projectKeyOrId=").append(encode(args.projectKey()));
    }
    if (args.boardType() != null) query.append("&type=").append(encode(args.boardType()));

    return client.get("/rest/agile/1.0/board" + query, context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
