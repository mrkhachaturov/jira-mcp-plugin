package com.atlassian.mcp.plugin.tools.transitions;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Get available status transitions for a Jira issue.
 * Simplifies the raw Jira response to {id, name, to_status} dicts
 * matching upstream get_available_transitions() behavior.
 */
public class GetTransitionsTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetTransitionsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_transitions"; }

    @Override
    public String description() {
        return "Get available status transitions for a Jira issue.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')")
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

        try {
            String rawJson = client.get("/rest/api/2/issue/" + issueKey + "/transitions", authHeader);
            JsonNode root = mapper.readTree(rawJson);
            JsonNode transitions = root.path("transitions");

            // Simplify to {id, name, to_status} matching upstream
            List<Map<String, Object>> simplified = new ArrayList<>();
            if (transitions.isArray()) {
                for (JsonNode t : transitions) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", t.path("id").asText(""));
                    entry.put("name", t.path("name").asText(""));

                    // Extract target status name from various formats
                    String toStatus = null;
                    if (t.has("to") && t.get("to").isObject()) {
                        toStatus = t.path("to").path("name").asText(null);
                    } else if (t.has("to_status")) {
                        toStatus = t.path("to_status").asText(null);
                    } else if (t.has("status")) {
                        toStatus = t.path("status").asText(null);
                    }
                    if (toStatus != null) {
                        entry.put("to_status", toStatus);
                    }

                    simplified.add(entry);
                }
            }

            return mapper.writeValueAsString(simplified);
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to get transitions: " + e.getMessage());
        }
    }
}
