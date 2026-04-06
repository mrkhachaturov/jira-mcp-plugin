package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetBoardIssuesTool implements McpTool {
    private final JiraRestClient client;
    public GetBoardIssuesTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "get_board_issues"; }
    @Override public String description() { return "Get issues on a Jira Agile board."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "board_id", Map.of("type", "integer", "description", "Board ID"),
                        "maxResults", Map.of("type", "integer", "default", 50),
                        "startAt", Map.of("type", "integer", "default", 0)),
                "required", List.of("board_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        Object boardId = args.get("board_id");
        if (boardId == null) throw new McpToolException("board_id is required");
        return client.get("/rest/agile/1.0/board/" + boardId + "/issue?maxResults=50", authHeader);
    }
}
