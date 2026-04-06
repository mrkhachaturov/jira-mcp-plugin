package com.atlassian.mcp.plugin.tools.transitions;

// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:transition_issue
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class TransitionIssueTool implements McpTool {
    private final JiraRestClient client;
    public TransitionIssueTool(JiraRestClient client) { this.client = client; }

    @Override public String name() { return "transition_issue"; }
    @Override public String description() { return "Transition a Jira issue to a new status."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                        "transition_id", Map.of("type", "string", "description", "Transition ID (get from get_transitions)"),
                        "comment", Map.of("type", "string", "description", "Optional comment to add with the transition")),
                "required", List.of("issue_key", "transition_id"));
    }
    @Override public boolean isWriteTool() { return true; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String transitionId = (String) args.get("transition_id");
        if (issueKey == null || transitionId == null)
            throw new McpToolException("issue_key and transition_id are required");
        String body = "{\"transition\":{\"id\":\"" + transitionId + "\"}}";
        return client.post("/rest/api/2/issue/" + issueKey + "/transitions", body, authHeader);
    }
}
