package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class GetQueueIssuesTool implements McpTool {
    private final JiraRestClient client;
    public GetQueueIssuesTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_queue_issues"; }
    @Override public String description() { return "Get issues in a Jira Service Desk queue."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "service_desk_id", Map.of("type", "string", "description", "Service desk ID"),
                "queue_id", Map.of("type", "string", "description", "Queue ID")),
                "required", List.of("service_desk_id", "queue_id"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String requiredPluginKey() { return "com.atlassian.servicedesk"; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String sdId = (String) args.get("service_desk_id");
        String qId = (String) args.get("queue_id");
        if (sdId == null || qId == null) throw new McpToolException("service_desk_id and queue_id required");
        return client.get("/rest/servicedeskapi/servicedesk/" + sdId + "/queue/" + qId + "/issue", authHeader);
    }
}
