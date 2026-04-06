package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetServiceDeskQueuesTool implements McpTool {
    private final JiraRestClient client;
    public GetServiceDeskQueuesTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_service_desk_queues"; }
    @Override public String description() { return "Get queues for a Jira Service Desk."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "service_desk_id", Map.of("type", "string", "description", "Service desk ID")),
                "required", List.of("service_desk_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.servicedesk"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String id = (String) args.get("service_desk_id");
        if (id == null) throw new McpToolException("service_desk_id is required");
        return client.get("/rest/servicedeskapi/servicedesk/" + id + "/queue", authHeader);
    }
}
