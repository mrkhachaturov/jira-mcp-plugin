package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GetSprintsFromBoardTool extends DeclarativeTool {

  private static final ToolParam<String> BOARD_ID =
      ToolParam.string("board_id", "The id of board (e.g., '1000')").required();
  private static final ToolParam<String> STATE =
      ToolParam.string("state", "Sprint state (e.g., 'active', 'future', 'closed')");
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(10);

  private static final int MAX_LIMIT = 50;

  private final JiraRestClient client;

  public GetSprintsFromBoardTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(BOARD_ID, STATE, START_AT, LIMIT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String boardId = args.require(BOARD_ID);
    String state = args.get(STATE);
    int startAt = args.get(START_AT);
    int limit = Math.min(args.get(LIMIT), MAX_LIMIT);

    StringBuilder query = new StringBuilder("?maxResults=").append(limit);
    query.append("&startAt=").append(startAt);
    // Jira rejects an unknown sprint state with 400, so only forward a value the caller gave.
    if (state != null) query.append("&state=").append(encode(state));

    return client.get("/rest/agile/1.0/board/" + boardId + "/sprint" + query, authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
