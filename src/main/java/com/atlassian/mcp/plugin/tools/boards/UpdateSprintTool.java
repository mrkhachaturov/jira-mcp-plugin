package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateSprintTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateSprintTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "update_sprint"; }

    @Override
    public String description() {
        return "Update jira sprint.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "sprint_id", Map.of("type", "string", "description", "The id of sprint (e.g., '10001')"),
                        "name", Map.of("type", "string", "description", "(Optional) New name for the sprint"),
                        "state", Map.of("type", "string", "description", "(Optional) New state for the sprint (future|active|closed)"),
                        "start_date", Map.of("type", "string", "description", "(Optional) New start date for the sprint"),
                        "end_date", Map.of("type", "string", "description", "(Optional) New end date for the sprint"),
                        "goal", Map.of("type", "string", "description", "(Optional) New goal for the sprint")
                ),
                "required", List.of("sprint_id")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String sprintId = (String) args.get("sprint_id");
        if (sprintId == null || sprintId.isBlank()) {
            throw new McpToolException("'sprint_id' parameter is required");
        }
        String name = (String) args.get("name");
        String state = (String) args.get("state");
        String startDate = (String) args.get("start_date");
        String endDate = (String) args.get("end_date");
        String goal = (String) args.get("goal");

        Map<String, Object> requestBody = new HashMap<>();
        if (name != null) requestBody.put("name", name);
        if (state != null) requestBody.put("state", state);
        if (startDate != null) requestBody.put("start_date", startDate);
        if (endDate != null) requestBody.put("end_date", endDate);
        if (goal != null) requestBody.put("goal", goal);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.put("/rest/agile/1.0/sprint/" + sprintId, jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
