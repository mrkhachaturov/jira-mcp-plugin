package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetSprintsFromBoardTool implements McpTool {
    private final JiraRestClient client;
    public GetSprintsFromBoardTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "get_sprints_from_board"; }
    @Override public String description() { return "Get sprints from a Jira Agile board."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "board_id", Map.of("type", "integer", "description", "Board ID"),
                        "state", Map.of("type", "string", "description", "Filter by state: active, closed, future")),
                "required", List.of("board_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        Object boardId = args.get("board_id");
        if (boardId == null) throw new McpToolException("board_id is required");
        String state = (String) args.get("state");
        String url = "/rest/agile/1.0/board/" + boardId + "/sprint";
        if (state != null && !state.isBlank()) url += "?state=" + state;
        return client.get(url, authHeader);
    }
}
