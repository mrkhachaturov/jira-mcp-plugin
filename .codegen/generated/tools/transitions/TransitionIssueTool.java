package com.atlassian.mcp.plugin.tools.transitions;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransitionIssueTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public TransitionIssueTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "transition_issue"; }

    @Override
    public String description() {
        return "Transition a Jira issue to a new status.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "transition_id", Map.of("type", "string", "description", "ID of the transition to perform. Use the jira_get_transitions tool first to get the available transition IDs for the issue. Example values: '11', '21', '31'"),
                        "fields", Map.of("type", "string", "description", "(Optional) JSON string of fields to update during the transition. Some transitions require specific fields to be set (e.g., resolution). Example: '{\"resolution\": {\"name\": \"Fixed\"}}'"),
                        "comment", Map.of("type", "string", "description", "(Optional) Comment to add during the transition in Markdown format. This will be visible in the issue history.")
                ),
                "required", List.of("issue_key", "transition_id")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String transitionId = (String) args.get("transition_id");
        if (transitionId == null || transitionId.isBlank()) {
            throw new McpToolException("'transition_id' parameter is required");
        }
        String fields = (String) args.get("fields");
        String comment = (String) args.get("comment");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("transition_id", transitionId);
        if (fields != null) requestBody.put("fields", fields);
        if (comment != null) requestBody.put("comment", comment);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/api/2/issue/" + issueKey + "/transitions", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
