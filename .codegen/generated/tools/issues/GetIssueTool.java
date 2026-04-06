package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue"; }

    @Override
    public String description() {
        return "Get details of a specific Jira issue including its Epic links and relationship information.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "fields", Map.of("type", "string", "description", "(Optional) Comma-separated list of fields to return (e.g., 'summary,status,customfield_10010'). You may also provide a single field as a string (e.g., 'duedate'). Use '*all' for all fields (including custom fields), or omit for essential fields only.", "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent"),
                        "expand", Map.of("type", "string", "description", "(Optional) Fields to expand. Examples: 'renderedFields' (for rendered content), 'transitions' (for available status transitions), 'changelog' (for history)"),
                        "comment_limit", Map.of("type", "integer", "description", "Maximum number of comments to include (0 or null for no comments)", "default", 10),
                        "properties", Map.of("type", "string", "description", "(Optional) A comma-separated list of issue properties to return"),
                        "update_history", Map.of("type", "boolean", "description", "Whether to update the issue view history for the requesting user", "default", true)
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String fields = (String) args.getOrDefault("fields", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent");
        String expand = (String) args.get("expand");
        int commentLimit = Math.min(getInt(args, "comment_limit", 10), 100);
        String properties = (String) args.get("properties");
        boolean updateHistory = getBoolean(args, "update_history", true);

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (fields != null && !fields.isBlank()) {
            query.append(sep).append("fields=").append(encode(fields));
            sep = "&";
        }
        if (expand != null && !expand.isBlank()) {
            query.append(sep).append("expand=").append(encode(expand));
            sep = "&";
        }
        if (properties != null && !properties.isBlank()) {
            query.append(sep).append("properties=").append(encode(properties));
            sep = "&";
        }
        query.append(sep).append("updateHistory=").append(updateHistory);
        sep = "&";

        return client.get("/rest/api/2/issue/" + issueKey + query, authHeader);
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

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
