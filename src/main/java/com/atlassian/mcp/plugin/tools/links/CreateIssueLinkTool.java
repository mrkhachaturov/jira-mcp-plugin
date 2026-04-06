package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class CreateIssueLinkTool implements McpTool {
    private final JiraRestClient client;
    public CreateIssueLinkTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "create_issue_link"; }
    @Override public String description() { return "Create a link between two Jira issues."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "type_name", Map.of("type", "string", "description", "Link type name (e.g., 'Blocks', 'Relates')"),
                "inward_issue", Map.of("type", "string", "description", "Inward issue key"),
                "outward_issue", Map.of("type", "string", "description", "Outward issue key")),
                "required", List.of("type_name", "inward_issue", "outward_issue"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String typeName = (String) args.get("type_name");
        String inward = (String) args.get("inward_issue");
        String outward = (String) args.get("outward_issue");
        if (typeName == null || inward == null || outward == null) throw new McpToolException("type_name, inward_issue, outward_issue required");
        String body = "{\"type\":{\"name\":\"" + typeName + "\"},\"inwardIssue\":{\"key\":\"" + inward + "\"},\"outwardIssue\":{\"key\":\"" + outward + "\"}}";
        return client.post("/rest/api/2/issueLink", body, authHeader);
    }
}
