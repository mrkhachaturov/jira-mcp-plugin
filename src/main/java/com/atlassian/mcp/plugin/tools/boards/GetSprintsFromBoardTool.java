package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetSprintsFromBoardTool implements McpTool {
    private final JiraRestClient client;

    public GetSprintsFromBoardTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_sprints_from_board"; }

    @Override
    public String description() {
        return "Get jira sprints from board by state.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "board_id", Map.of("type", "string", "description", "The id of board (e.g., '1000')"),
                        "state", Map.of("type", "string", "description", "Sprint state (e.g., 'active', 'future', 'closed')"),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10)
                ),
                "required", List.of("board_id")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String boardId = (String) args.get("board_id");
        if (boardId == null || boardId.isBlank()) {
            throw new McpToolException("'board_id' parameter is required");
        }
        String state = (String) args.get("state");
        int startAt = getInt(args, "start_at", 0);
        int limit = Math.min(getInt(args, "limit", 10), 50);

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (state != null && !state.isBlank()) {
            query.append(sep).append("state=").append(encode(state));
            sep = "&";
        }
        query.append(sep).append("maxResults=").append(limit);
        sep = "&";
        query.append(sep).append("startAt=").append(startAt);

        return client.get("/rest/agile/1.0/board/" + boardId + "/sprint" + query, authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
