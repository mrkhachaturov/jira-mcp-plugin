package com.atlassian.mcp.plugin.tools.boards;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateSprintTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CreateSprintTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "create_sprint"; }

    @Override
    public String description() {
        return "Create Jira sprint for a board.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "board_id", Map.of("type", "string", "description", "The id of board (e.g., '1000')"),
                        "name", Map.of("type", "string", "description", "Name of the sprint (e.g., 'Sprint 1')"),
                        "start_date", Map.of("type", "string", "description", "Start time for sprint (ISO 8601 format)"),
                        "end_date", Map.of("type", "string", "description", "End time for sprint (ISO 8601 format)"),
                        "goal", Map.of("type", "string", "description", "(Optional) Goal of the sprint")
                ),
                "required", List.of("board_id", "name", "start_date", "end_date")
        );
    }

    @Override public boolean isWriteTool() { return true; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.jira.plugins.jira-software-plugin"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String boardId = (String) args.get("board_id");
        if (boardId == null || boardId.isBlank()) {
            throw new McpToolException("'board_id' parameter is required");
        }
        String name = (String) args.get("name");
        if (name == null || name.isBlank()) {
            throw new McpToolException("'name' parameter is required");
        }
        String startDate = (String) args.get("start_date");
        if (startDate == null || startDate.isBlank()) {
            throw new McpToolException("'start_date' parameter is required");
        }
        String endDate = (String) args.get("end_date");
        if (endDate == null || endDate.isBlank()) {
            throw new McpToolException("'end_date' parameter is required");
        }
        String goal = (String) args.get("goal");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("board_id", boardId);
        requestBody.put("name", name);
        requestBody.put("start_date", startDate);
        requestBody.put("end_date", endDate);
        if (goal != null) requestBody.put("goal", goal);
        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            return client.post("/rest/agile/1.0/sprint", jsonBody, authHeader);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize request: " + e.getMessage());
        }
    }
}
