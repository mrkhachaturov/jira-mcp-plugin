package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetAgileBoardsTool implements McpTool {
    private final JiraRestClient client;

    public GetAgileBoardsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_agile_boards"; }

    @Override
    public String description() {
        return "Get jira agile boards by name, project key, or type.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "board_name", Map.of("type", "string", "description", "(Optional) The name of board, support fuzzy search"),
                        "project_key", Map.of("type", "string", "description", "(Optional) Jira project key (e.g., 'PROJ', 'ACV2')"),
                        "board_type", Map.of("type", "string", "description", "(Optional) The type of jira board (e.g., 'scrum', 'kanban')"),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10)
                ),
                "required", List.of()
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String boardName = (String) args.get("board_name");
        String projectKey = (String) args.get("project_key");
        String boardType = (String) args.get("board_type");
        int startAt = getInt(args, "start_at", 0);
        int limit = Math.min(getInt(args, "limit", 10), 50);

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (boardName != null && !boardName.isBlank()) {
            query.append(sep).append("name=").append(encode(boardName));
            sep = "&";
        }
        if (projectKey != null && !projectKey.isBlank()) {
            query.append(sep).append("projectKeyOrId=").append(encode(projectKey));
            sep = "&";
        }

        return client.get("/rest/agile/1.0/board" + query, authHeader);
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
