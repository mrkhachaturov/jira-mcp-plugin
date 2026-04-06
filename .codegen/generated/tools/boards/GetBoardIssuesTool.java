package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetBoardIssuesTool implements McpTool {
    private final JiraRestClient client;

    public GetBoardIssuesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_board_issues"; }

    @Override
    public String description() {
        return "Get all issues linked to a specific board filtered by JQL.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "board_id", Map.of("type", "string", "description", "The id of the board (e.g., '1001')"),
                        "jql", Map.of("type", "string", "description", "JQL query string (Jira Query Language). Examples: - Find Epics: \"issuetype = Epic AND project = PROJ\" - Find issues in Epic: \"parent = PROJ-123\" - Find by status: \"status = 'In Progress' AND project = PROJ\" - Find by assignee: \"assignee = currentUser()\" - Find recently updated: \"updated >= -7d AND project = PROJ\" - Find by label: \"labels = frontend AND project = PROJ\" - Find by priority: \"priority = High AND project = PROJ\""),
                        "fields", Map.of("type", "string", "description", "Comma-separated fields to return in the results. Use '*all' for all fields, or specify individual fields like 'summary,status,assignee,priority'", "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent"),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10),
                        "expand", Map.of("type", "string", "description", "Optional fields to expand in the response (e.g., 'changelog').", "default", "version")
                ),
                "required", List.of("board_id", "jql")
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
        String jql = (String) args.get("jql");
        if (jql == null || jql.isBlank()) {
            throw new McpToolException("'jql' parameter is required");
        }
        String fields = (String) args.getOrDefault("fields", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent");
        int startAt = getInt(args, "start_at", 0);
        int limit = Math.min(getInt(args, "limit", 10), 50);
        String expand = (String) args.getOrDefault("expand", "version");

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (jql != null && !jql.isBlank()) {
            query.append(sep).append("jql=").append(encode(jql));
            sep = "&";
        }
        if (maxResults != null && !maxResults.isBlank()) {
            query.append(sep).append("maxResults=").append(encode(maxResults));
            sep = "&";
        }
        query.append(sep).append("startAt=").append(startAt);
        sep = "&";

        return client.get("/rest/agile/1.0/board/" + boardId + "/issue" + query, authHeader);
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
