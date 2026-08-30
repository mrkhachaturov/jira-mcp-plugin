package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GetSprintsFromBoardTool extends TypedTool<GetSprintsFromBoardTool.Args> {

  private static final int MAX_LIMIT = 50;

  public record Args(
      @ToolArg(value = "The id of the board (e.g. 1000)", required = true) long boardId,
      @ToolArg(
              "(Optional) Sprint states to include; one or more of 'future', 'active', 'closed'."
                  + " Omit for every state.")
          List<String> state,
      @ToolArg(value = "Starting index for pagination (0-based)", defaultValue = "0") int startAt,
      @ToolArg(value = "Maximum number of results (1-50)", defaultValue = "10") int limit) {}

  private final JiraRestClient client;

  public GetSprintsFromBoardTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_sprints_from_board";
  }

  @Override
  public String description() {
    return "Get jira sprints from board by state.";
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
    // Jira rejects an unknown sprint state with 400, so only forward states the caller gave.
    if (args.state() != null && !args.state().isEmpty()) {
      query.append("&state=").append(encode(String.join(",", args.state())));
    }

    return client.get(
        "/rest/agile/1.0/board/" + args.boardId() + "/sprint" + query, context.authHeader());
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
