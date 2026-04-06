package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetFieldOptionsTool implements McpTool {
    private final JiraRestClient client;

    public GetFieldOptionsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_field_options"; }

    @Override
    public String description() {
        return "Get allowed option values for a custom field. Returns the list of valid options for select, multi-select, radio, checkbox, and cascading select custom fields. Cloud: Uses the Field Context Option API. If context_id is not provided, automatically resolves to the global context. Server/DC: Uses createmeta to get allowedValues. Requires project_key and issue_type parameters.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "field_id", Map.of("type", "string", "description", "Custom field ID (e.g., 'customfield_10001'). Use jira_search_fields to find field IDs."),
                        "context_id", Map.of("type", "string", "description", "Field context ID (Cloud only). If omitted, auto-resolves to the global context."),
                        "project_key", Map.of("type", "string", "description", "Project key (required for Server/DC). Example: 'PROJ'"),
                        "issue_type", Map.of("type", "string", "description", "Issue type name (required for Server/DC). Example: 'Bug'"),
                        "contains", Map.of("type", "string", "description", "Case-insensitive substring filter on option values. Also matches child values in cascading selects."),
                        "return_limit", Map.of("type", "integer", "description", "Maximum number of results to return (applied after filtering)."),
                        "values_only", Map.of("type", "boolean", "description", "If true, return only value strings in a compact JSON format instead of full option objects.", "default", false)
                ),
                "required", List.of("field_id")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String fieldId = (String) args.get("field_id");
        if (fieldId == null || fieldId.isBlank()) {
            throw new McpToolException("'field_id' parameter is required");
        }
        String contextId = (String) args.get("context_id");
        String projectKey = (String) args.get("project_key");
        String issueType = (String) args.get("issue_type");
        String contains = (String) args.get("contains");
        int returnLimit = getInt(args, "return_limit", 0);
        boolean valuesOnly = getBoolean(args, "values_only", false);

        return client.get("/rest/api/2/field/" + fieldKey + "/option", authHeader);
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
