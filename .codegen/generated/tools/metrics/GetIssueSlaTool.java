package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.util.List;
import java.util.Map;

public class GetIssueSlaTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueSlaTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_sla"; }

    @Override
    public String description() {
        return "Calculate SLA metrics for a Jira issue. Computes various time-based metrics including cycle time, lead time, time spent in each status, due date compliance, and more. Working hours can be configured via environment variables: - JIRA_SLA_WORKING_HOURS_ONLY: Enable working hours filtering (true/false) - JIRA_SLA_WORKING_HOURS_START: Start time (e.g., \"09:00\") - JIRA_SLA_WORKING_HOURS_END: End time (e.g., \"17:00\") - JIRA_SLA_WORKING_DAYS: Working days (e.g., \"1,2,3,4,5\" for Mon-Fri) - JIRA_SLA_TIMEZONE: Timezone for calculations (e.g., \"America/New_York\")";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "metrics", Map.of("type", "string", "description", "Comma-separated list of SLA metrics to calculate. Available: cycle_time, lead_time, time_in_status, due_date_compliance, resolution_time, first_response_time. Defaults to configured metrics or 'cycle_time,time_in_status'."),
                        "working_hours_only", Map.of("type", "boolean", "description", "Calculate using working hours only (excludes weekends/non-business hours). Defaults to value from JIRA_SLA_WORKING_HOURS_ONLY environment variable."),
                        "include_raw_dates", Map.of("type", "boolean", "description", "Include raw date values in the response", "default", false)
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
        String metrics = (String) args.get("metrics");
        boolean workingHoursOnly = getBoolean(args, "working_hours_only", false);
        boolean includeRawDates = getBoolean(args, "include_raw_dates", false);

        return client.get("/rest/servicedeskapi/request/" + issueKey + "/sla", authHeader);
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
