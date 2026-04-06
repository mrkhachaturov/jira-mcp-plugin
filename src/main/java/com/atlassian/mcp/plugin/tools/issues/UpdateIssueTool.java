package com.atlassian.mcp.plugin.tools.issues;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:update_issue
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

public class UpdateIssueTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateIssueTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "update_issue"; }
    @Override public String description() {
        return "Update an existing Jira issue. Provide the fields to update as a JSON object.";
    }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "fields", Map.of("type", "string", "description", "JSON string of fields to update (e.g., '{\"summary\":\"New title\"}')")),
                "required", List.of("issue_key", "fields"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String fieldsJson = (String) args.get("fields");
        if (issueKey == null || fieldsJson == null) {
            throw new McpToolException("issue_key and fields are required");
        }
        try {
            // Validate JSON
            mapper.readTree(fieldsJson);
            String body = "{\"fields\":" + fieldsJson + "}";
            return client.put("/rest/api/2/issue/" + issueKey, body, authHeader);
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Invalid fields JSON: " + e.getMessage());
        }
    }
}
