package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SearchTool implements McpTool {
    private final JiraRestClient client;

    public SearchTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "search"; }

    @Override
    public String description() {
        return "Search Jira issues using JQL (Jira Query Language).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "jql", Map.of("type", "string", "description", "JQL query string (Jira Query Language). Examples: - Find Epics: \"issuetype = Epic AND project = PROJ\" - Find issues in Epic: \"parent = PROJ-123\" - Find by status: \"status = 'In Progress' AND project = PROJ\" - Find by assignee: \"assignee = currentUser()\" - Find recently updated: \"updated >= -7d AND project = PROJ\" - Find by label: \"labels = frontend AND project = PROJ\" - Find by priority: \"priority = High AND project = PROJ\""),
                        "fields", Map.of("type", "string", "description", "(Optional) Comma-separated fields to return in the results. Use '*all' for all fields, or specify individual fields like 'summary,status,assignee,priority'", "default", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent"),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 10),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "projects_filter", Map.of("type", "string", "description", "(Optional) Comma-separated list of project keys to filter results by. Overrides the environment variable JIRA_PROJECTS_FILTER if provided."),
                        "expand", Map.of("type", "string", "description", "(Optional) fields to expand. Examples: 'renderedFields', 'transitions', 'changelog'"),
                        "page_token", Map.of("type", "string", "description", "(Optional) Pagination token from a previous search result. Cloud only — Server/DC uses start_at for pagination.")
                ),
                "required", List.of("jql")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String jql = (String) args.get("jql");
        if (jql == null || jql.isBlank()) {
            throw new McpToolException("'jql' parameter is required");
        }
        String fields = (String) args.getOrDefault("fields", "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent");
        int limit = getInt(args, "limit", 10);
        int startAt = getInt(args, "start_at", 0);
        String projectsFilter = (String) args.get("projects_filter");
        String expand = (String) args.get("expand");
        String pageToken = (String) args.get("page_token");

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (jql != null && !jql.isBlank()) {
            query.append(sep).append("jql=").append(encode(jql));
            sep = "&";
        }
        query.append(sep).append("maxResults=").append(limit);
        sep = "&";
        query.append(sep).append("startAt=").append(startAt);
        sep = "&";
        if (fields != null && !fields.isBlank()) {
            query.append(sep).append("fields=").append(encode(fields));
            sep = "&";
        }
        if (expand != null && !expand.isBlank()) {
            query.append(sep).append("expand=").append(encode(expand));
            sep = "&";
        }

        return client.get("/rest/api/2/search" + query, authHeader);
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
