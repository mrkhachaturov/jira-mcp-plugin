package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GetAgileBoardsTool extends DeclarativeTool {

  private static final ToolParam<String> BOARD_NAME =
      ToolParam.string("board_name", "(Optional) The name of board, support fuzzy search");
  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "(Optional) Jira project key (e.g., 'PROJ', 'ACV2')");
  private static final ToolParam<String> BOARD_TYPE =
      ToolParam.string("board_type", "(Optional) The type of jira board")
          .allowing("scrum", "kanban", "simple");
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(10);

  private static final int MAX_LIMIT = 50;

  private final JiraRestClient client;

  public GetAgileBoardsTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(BOARD_NAME, PROJECT_KEY, BOARD_TYPE, START_AT, LIMIT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String boardName = args.get(BOARD_NAME);
    String projectKey = args.get(PROJECT_KEY);
    String boardType = args.get(BOARD_TYPE);
    int startAt = args.get(START_AT);
    int limit = Math.min(args.get(LIMIT), MAX_LIMIT);

    StringBuilder query = new StringBuilder("?startAt=").append(startAt);
    query.append("&maxResults=").append(limit);
    if (boardName != null) query.append("&name=").append(encode(boardName));
    if (projectKey != null) query.append("&projectKeyOrId=").append(encode(projectKey));
    // Jira rejects an unknown board type with 400, so only forward a value the caller gave.
    if (boardType != null) query.append("&type=").append(encode(boardType));

    return client.get("/rest/agile/1.0/board" + query, authHeader);
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
