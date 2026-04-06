package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetQueueIssuesTool implements McpTool {
    private final JiraRestClient client;

    public GetQueueIssuesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_queue_issues"; }

    @Override
    public String description() {
        return "Get issues from a Jira Service Desk queue. Server/Data Center only. Not available on Jira Cloud.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service_desk_id", Map.of("type", "string", "description", "Service desk ID (e.g., '4')"),
                        "queue_id", Map.of("type", "string", "description", "Queue ID (e.g., '47')"),
                        "start_at", Map.of("type", "integer", "description", "Starting index for pagination (0-based)", "default", 0),
                        "limit", Map.of("type", "integer", "description", "Maximum number of results (1-50)", "default", 50)
                ),
                "required", List.of("service_desk_id", "queue_id")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String requiredPluginKey() { return "com.atlassian.servicedesk"; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String serviceDeskId = (String) args.get("service_desk_id");
        if (serviceDeskId == null || serviceDeskId.isBlank()) {
            throw new McpToolException("'service_desk_id' parameter is required");
        }
        String queueId = (String) args.get("queue_id");
        if (queueId == null || queueId.isBlank()) {
            throw new McpToolException("'queue_id' parameter is required");
        }
        int startAt = getInt(args, "start_at", 0);
        int limit = getInt(args, "limit", 50);

        return client.get("/rest/servicedeskapi/servicedesk/" + serviceDeskId + "/queue/" + queueId + "/issue", authHeader);
    }

    private static int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
