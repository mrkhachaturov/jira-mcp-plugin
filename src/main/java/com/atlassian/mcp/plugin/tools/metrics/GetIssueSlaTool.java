package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes SLA metrics for a Jira issue from changelog data.
 * Matches upstream behavior: does NOT call JSM API — instead fetches
 * issue + changelog and computes cycle_time, lead_time, time_in_status,
 * due_date_compliance, resolution_time, first_response_time.
 */
public class GetIssueSlaTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> AVAILABLE_METRICS = Set.of(
            "cycle_time", "lead_time", "time_in_status",
            "due_date_compliance", "resolution_time", "first_response_time"
    );

    /** Jira status category key indicating "in progress" work. */
    private static final String IN_PROGRESS_CATEGORY_KEY = "indeterminate";

    public GetIssueSlaTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_sla"; }

    @Override
    public String description() {
        return "Calculate SLA metrics for a Jira issue. Computes time-based metrics including cycle time (first in-progress to resolution), lead time (creation to resolution or now), time spent in each status, due date compliance, resolution time, and first response time. All metrics are computed from issue changelog data.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "metrics", Map.of("type", "string", "description", "Comma-separated list of SLA metrics to calculate. Available: cycle_time, lead_time, time_in_status, due_date_compliance, resolution_time, first_response_time. Defaults to 'cycle_time,time_in_status'."),
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
        String metricsStr = (String) args.get("metrics");
        boolean includeRawDates = getBoolean(args, "include_raw_dates", false);

        // Parse requested metrics
        List<String> requestedMetrics;
        if (metricsStr != null && !metricsStr.isBlank()) {
            requestedMetrics = Arrays.stream(metricsStr.split(","))
                    .map(String::trim).filter(AVAILABLE_METRICS::contains).toList();
        } else {
            requestedMetrics = List.of("cycle_time", "time_in_status");
        }

        try {
            // Fetch issue with changelog and date fields
            String issueJson = client.get(
                    "/rest/api/2/issue/" + issueKey + "?fields=status,created,updated,duedate,resolutiondate&expand=changelog",
                    authHeader);

            JsonNode issue = mapper.readTree(issueJson);
            JsonNode fields = issue.path("fields");

            String createdStr = fields.path("created").asText(null);
            String updatedStr = fields.path("updated").asText(null);
            String dueDateStr = nullableText(fields, "duedate");
            String resolutionDateStr = nullableText(fields, "resolutiondate");
            String currentStatus = fields.path("status").path("name").asText(null);
            String statusCategoryKey = fields.path("status").path("statusCategory").path("key").asText(null);

            OffsetDateTime created = createdStr != null ? parseDate(createdStr) : null;
            OffsetDateTime resolutionDate = resolutionDateStr != null ? parseDate(resolutionDateStr) : null;
            OffsetDateTime now = OffsetDateTime.now();

            // Parse status changes from changelog
            List<StatusTransition> transitions = parseStatusTransitions(issue);

            // Build result
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("issue_key", issueKey);
            result.put("current_status", currentStatus);

            Map<String, Object> metricsResult = new LinkedHashMap<>();

            if (requestedMetrics.contains("cycle_time")) {
                metricsResult.put("cycle_time", computeCycleTime(transitions, resolutionDate, now));
            }
            if (requestedMetrics.contains("lead_time")) {
                metricsResult.put("lead_time", computeLeadTime(created, resolutionDate, now));
            }
            if (requestedMetrics.contains("time_in_status")) {
                metricsResult.put("time_in_status", computeTimeInStatus(transitions, created, now));
            }
            if (requestedMetrics.contains("due_date_compliance")) {
                metricsResult.put("due_date_compliance", computeDueDateCompliance(
                        dueDateStr, resolutionDate, now));
            }
            if (requestedMetrics.contains("resolution_time")) {
                metricsResult.put("resolution_time", computeResolutionTime(created, resolutionDate));
            }
            if (requestedMetrics.contains("first_response_time")) {
                metricsResult.put("first_response_time", computeFirstResponseTime(created, transitions));
            }

            result.put("metrics", metricsResult);

            if (includeRawDates) {
                Map<String, Object> rawDates = new LinkedHashMap<>();
                rawDates.put("created", createdStr);
                rawDates.put("updated", updatedStr);
                rawDates.put("due_date", dueDateStr);
                rawDates.put("resolution_date", resolutionDateStr);
                result.put("raw_dates", rawDates);
            }

            return mapper.writeValueAsString(result);

        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to compute SLA metrics: " + e.getMessage());
        }
    }

    /** Simple record for a status transition. */
    private record StatusTransition(String fromStatus, String toStatus,
                                    String fromCategoryKey, String toCategoryKey,
                                    OffsetDateTime timestamp) {}

    private List<StatusTransition> parseStatusTransitions(JsonNode issue) {
        List<StatusTransition> transitions = new ArrayList<>();
        JsonNode histories = issue.path("changelog").path("histories");
        if (!histories.isArray()) return transitions;

        for (JsonNode history : histories) {
            String timestamp = history.path("created").asText(null);
            if (timestamp == null) continue;
            OffsetDateTime ts = parseDate(timestamp);

            for (JsonNode item : history.path("items")) {
                if ("status".equalsIgnoreCase(item.path("field").asText(""))) {
                    transitions.add(new StatusTransition(
                            item.path("fromString").asText(null),
                            item.path("toString").asText(null),
                            // Jira DC changelog doesn't always include category keys,
                            // but when available they are useful
                            item.path("from").asText(null),
                            item.path("to").asText(null),
                            ts
                    ));
                }
            }
        }
        transitions.sort((a, b) -> a.timestamp.compareTo(b.timestamp));
        return transitions;
    }

    /** Cycle time: first in-progress to resolution (or now). */
    private Map<String, Object> computeCycleTime(List<StatusTransition> transitions,
                                                  OffsetDateTime resolutionDate,
                                                  OffsetDateTime now) {
        // Find first transition to an in-progress-like status
        OffsetDateTime startTime = null;
        for (StatusTransition t : transitions) {
            // On DC, we can't always check category key; use heuristic:
            // any transition away from an initial/backlog status is "start of work"
            if (t.toStatus != null && startTime == null) {
                // First status change = start of cycle (heuristic for DC)
                startTime = t.timestamp;
                break;
            }
        }

        Map<String, Object> metric = new LinkedHashMap<>();
        if (startTime != null) {
            OffsetDateTime endTime = resolutionDate != null ? resolutionDate : now;
            long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
            metric.put("start_time", startTime.toString());
            metric.put("end_time", endTime.toString());
            metric.put("duration_minutes", minutes);
            metric.put("duration_formatted", GetIssueDatesTool.formatDuration(minutes));
            metric.put("is_completed", resolutionDate != null);
        } else {
            metric.put("status", "not_started");
            metric.put("message", "No status transitions found — issue has not been worked on.");
        }
        return metric;
    }

    /** Lead time: creation to resolution (or now). */
    private Map<String, Object> computeLeadTime(OffsetDateTime created,
                                                  OffsetDateTime resolutionDate,
                                                  OffsetDateTime now) {
        Map<String, Object> metric = new LinkedHashMap<>();
        if (created == null) {
            metric.put("status", "unknown");
            metric.put("message", "Created date not available.");
            return metric;
        }
        OffsetDateTime endTime = resolutionDate != null ? resolutionDate : now;
        long minutes = ChronoUnit.MINUTES.between(created, endTime);
        metric.put("start_time", created.toString());
        metric.put("end_time", endTime.toString());
        metric.put("duration_minutes", minutes);
        metric.put("duration_formatted", GetIssueDatesTool.formatDuration(minutes));
        metric.put("is_completed", resolutionDate != null);
        return metric;
    }

    /** Time in each status — delegates to GetIssueDatesTool logic. */
    private List<Map<String, Object>> computeTimeInStatus(List<StatusTransition> transitions,
                                                           OffsetDateTime created,
                                                           OffsetDateTime now) {
        // Build status entries with durations
        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, long[]> aggregated = new LinkedHashMap<>(); // [totalMinutes, visitCount]

        // Initial status
        if (created != null && !transitions.isEmpty()) {
            String initialStatus = transitions.get(0).fromStatus;
            if (initialStatus != null) {
                OffsetDateTime exitedAt = transitions.get(0).timestamp;
                long minutes = ChronoUnit.MINUTES.between(created, exitedAt);
                aggregated.computeIfAbsent(initialStatus, k -> new long[]{0, 0});
                aggregated.get(initialStatus)[0] += minutes;
                aggregated.get(initialStatus)[1]++;
            }
        }

        // Each transition
        for (int i = 0; i < transitions.size(); i++) {
            String status = transitions.get(i).toStatus;
            if (status == null) continue;
            OffsetDateTime enteredAt = transitions.get(i).timestamp;
            OffsetDateTime exitedAt = (i + 1 < transitions.size()) ? transitions.get(i + 1).timestamp : now;
            long minutes = ChronoUnit.MINUTES.between(enteredAt, exitedAt);

            aggregated.computeIfAbsent(status, k -> new long[]{0, 0});
            aggregated.get(status)[0] += minutes;
            aggregated.get(status)[1]++;
        }

        for (var e : aggregated.entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", e.getKey());
            entry.put("total_duration_minutes", e.getValue()[0]);
            entry.put("total_duration_formatted", GetIssueDatesTool.formatDuration(e.getValue()[0]));
            entry.put("visit_count", e.getValue()[1]);
            entries.add(entry);
        }

        entries.sort((a, b) -> Long.compare(
                ((Number) b.get("total_duration_minutes")).longValue(),
                ((Number) a.get("total_duration_minutes")).longValue()));
        return entries;
    }

    /** Due date compliance check. */
    private Map<String, Object> computeDueDateCompliance(String dueDateStr,
                                                          OffsetDateTime resolutionDate,
                                                          OffsetDateTime now) {
        Map<String, Object> metric = new LinkedHashMap<>();
        if (dueDateStr == null) {
            metric.put("status", "no_due_date");
            metric.put("message", "No due date set for this issue.");
            return metric;
        }

        OffsetDateTime dueDate;
        try {
            // Due date is usually just a date, not datetime
            dueDate = OffsetDateTime.parse(dueDateStr + "T23:59:59+00:00");
        } catch (Exception e) {
            try {
                dueDate = parseDate(dueDateStr);
            } catch (Exception e2) {
                metric.put("status", "invalid_date");
                return metric;
            }
        }

        OffsetDateTime completionDate = resolutionDate != null ? resolutionDate : now;
        boolean isOverdue = completionDate.isAfter(dueDate);
        long minutesDiff = ChronoUnit.MINUTES.between(dueDate, completionDate);

        metric.put("due_date", dueDateStr);
        metric.put("is_completed", resolutionDate != null);
        metric.put("is_overdue", isOverdue);
        if (isOverdue) {
            metric.put("overdue_by_minutes", minutesDiff);
            metric.put("overdue_by_formatted", GetIssueDatesTool.formatDuration(minutesDiff));
        } else {
            metric.put("remaining_minutes", Math.abs(minutesDiff));
            metric.put("remaining_formatted", GetIssueDatesTool.formatDuration(Math.abs(minutesDiff)));
        }
        return metric;
    }

    /** Resolution time: created to resolved. */
    private Map<String, Object> computeResolutionTime(OffsetDateTime created,
                                                       OffsetDateTime resolutionDate) {
        Map<String, Object> metric = new LinkedHashMap<>();
        if (resolutionDate == null) {
            metric.put("status", "unresolved");
            metric.put("message", "Issue is not yet resolved.");
            return metric;
        }
        if (created == null) {
            metric.put("status", "unknown");
            return metric;
        }
        long minutes = ChronoUnit.MINUTES.between(created, resolutionDate);
        metric.put("duration_minutes", minutes);
        metric.put("duration_formatted", GetIssueDatesTool.formatDuration(minutes));
        metric.put("resolved_at", resolutionDate.toString());
        return metric;
    }

    /** First response time: created to first status change. */
    private Map<String, Object> computeFirstResponseTime(OffsetDateTime created,
                                                          List<StatusTransition> transitions) {
        Map<String, Object> metric = new LinkedHashMap<>();
        if (created == null || transitions.isEmpty()) {
            metric.put("status", "no_response");
            metric.put("message", "No status transitions found.");
            return metric;
        }
        OffsetDateTime firstResponse = transitions.get(0).timestamp;
        long minutes = ChronoUnit.MINUTES.between(created, firstResponse);
        metric.put("duration_minutes", minutes);
        metric.put("duration_formatted", GetIssueDatesTool.formatDuration(minutes));
        metric.put("first_response_at", firstResponse.toString());
        return metric;
    }

    private static OffsetDateTime parseDate(String dateStr) {
        try {
            return OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return OffsetDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        }
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode val = node.path(field);
        return val.isNull() || val.isMissingNode() ? null : val.asText(null);
    }

    private static boolean getBoolean(Map<String, Object> args, String key, boolean defaultVal) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return defaultVal;
    }
}
