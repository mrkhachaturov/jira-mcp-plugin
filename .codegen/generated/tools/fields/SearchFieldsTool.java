package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class SearchFieldsTool implements McpTool {
    private final JiraRestClient client;

    public SearchFieldsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "search_fields"; }

    @Override
    public String description() {
        return "Search Jira fields by keyword with fuzzy match.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "Keyword for fuzzy search. If left empty, lists the first 'limit' available fields in their default order.", "default", ""),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results", "default", 10),
                        "refresh", Map.of("type", "boolean", "description", "Whether to force refresh the field list", "default", false)
                ),
                "required", List.of()
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String keyword = (String) args.get("keyword");
        int limit = getInt(args, "limit", 10);
        boolean refresh = getBoolean(args, "refresh", false);

        return client.get("/rest/api/2/field", authHeader);
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
