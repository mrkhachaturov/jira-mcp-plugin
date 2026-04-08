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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes date information and status transition history for a Jira issue.
 * Matches upstream behavior: fetches issue + changelog, then computes
 * status change durations and aggregated time-in-status summaries.
 */
public class GetIssueDatesTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GetIssueDatesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_dates"; }

    @Override
    public String description() {
        return "Get date information and status transition history for a Jira issue. Returns dates (created, updated, due date, resolution date) and optionally status change history with time tracking for workflow analysis.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')"),
                        "include_status_changes", Map.of("type", "boolean", "description", "Include status change history with timestamps and durations", "default", true),
                        "include_status_summary", Map.of("type", "boolean", "description", "Include aggregated time spent in each status", "default", true)
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
        boolean includeStatusChanges = getBoolean(args, "include_status_changes", true);
        boolean includeStatusSummary = getBoolean(args, "include_status_summary", true);

        try {
            // Fetch issue with changelog (matches upstream fields)
            String expand = (includeStatusChanges || includeStatusSummary) ? "&expand=changelog" : "";
            String issueJson = client.get(
                    "/rest/api/2/issue/" + issueKey + "?fields=status,created,updated,duedate,resolutiondate" + expand,
                    authHeader);

            JsonNode issue = mapper.readTree(issueJson);
            JsonNode fields = issue.path("fields");

            // Build response matching upstream IssueDatesResponse
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("issue_key", issueKey);
            result.put("created", fields.path("created").asText(null));
            result.put("updated", fields.path("updated").asText(null));
            result.put("due_date", nullableText(fields, "duedate"));
            result.put("resolution_date", nullableText(fields, "resolutiondate"));

            String currentStatus = fields.path("status").path("name").asText(null);
            result.put("current_status", currentStatus);

            // Parse changelog for status transitions
            if (includeStatusChanges || includeStatusSummary) {
                String createdStr = fields.path("created").asText(null);
                OffsetDateTime createdDate = createdStr != null ? parseDate(createdStr) : null;

                List<Map<String, Object>> statusChanges = parseStatusChanges(issue, createdDate);

                if (includeStatusChanges) {
                    result.put("status_changes", statusChanges);
                }
                if (includeStatusSummary) {
                    result.put("status_summary", aggregateStatusTimes(statusChanges));
                }
            }

            // Remove null values (matches upstream exclude_none=True)
            result.values().removeIf(v -> v == null);

            return mapper.writeValueAsString(result);

        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to compute issue dates: " + e.getMessage());
        }
    }

    /**
     * Parse changelog to extract status transitions with durations.
     * Matches upstream _parse_changelog_to_status_changes().
     */
    private List<Map<String, Object>> parseStatusChanges(JsonNode issue, OffsetDateTime createdDate) {
        List<Map<String, Object>> transitions = new ArrayList<>();

        // Extract raw status change events from changelog
        JsonNode histories = issue.path("changelog").path("histories");
        if (!histories.isArray()) return transitions;

        List<Map<String, Object>> rawChanges = new ArrayList<>();
        for (JsonNode history : histories) {
            String timestamp = history.path("created").asText(null);
            if (timestamp == null) continue;
            String author = history.path("author").path("displayName").asText(null);

            for (JsonNode item : history.path("items")) {
                if ("status".equalsIgnoreCase(item.path("field").asText(""))) {
                    Map<String, Object> change = new LinkedHashMap<>();
                    change.put("from_status", item.path("fromString").asText(null));
                    change.put("to_status", item.path("toString").asText(null));
                    change.put("timestamp", timestamp);
                    change.put("transitioned_by", author);
                    rawChanges.add(change);
                }
            }
        }

        // Sort by timestamp ascending
        rawChanges.sort(Comparator.comparing(c -> (String) c.get("timestamp")));

        List<Map<String, Object>> entries = new ArrayList<>();

        // Add initial status entry
        if (createdDate != null && !rawChanges.isEmpty()) {
            String initialStatus = (String) rawChanges.get(0).get("from_status");
            if (initialStatus != null) {
                OffsetDateTime firstTransition = parseDate((String) rawChanges.get(0).get("timestamp"));
                long durationMinutes = ChronoUnit.MINUTES.between(createdDate, firstTransition);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("status", initialStatus);
                entry.put("entered_at", createdDate.toString());
                entry.put("exited_at", firstTransition.toString());
                entry.put("duration_minutes", durationMinutes);
                entry.put("duration_formatted", formatDuration(durationMinutes));
                entries.add(entry);
            }
        }

        // Process each status transition
        for (int i = 0; i < rawChanges.size(); i++) {
            Map<String, Object> change = rawChanges.get(i);
            String toStatus = (String) change.get("to_status");
            if (toStatus == null) continue;

            OffsetDateTime enteredAt = parseDate((String) change.get("timestamp"));
            OffsetDateTime exitedAt = null;
            if (i + 1 < rawChanges.size()) {
                exitedAt = parseDate((String) rawChanges.get(i + 1).get("timestamp"));
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", toStatus);
            entry.put("entered_at", enteredAt.toString());
            if (exitedAt != null) {
                entry.put("exited_at", exitedAt.toString());
                long durationMinutes = ChronoUnit.MINUTES.between(enteredAt, exitedAt);
                entry.put("duration_minutes", durationMinutes);
                entry.put("duration_formatted", formatDuration(durationMinutes));
            }
            String transitionedBy = (String) change.get("transitioned_by");
            if (transitionedBy != null) {
                entry.put("transitioned_by", transitionedBy);
            }
            entries.add(entry);
        }

        return entries;
    }

    /**
     * Aggregate time spent in each status.
     * Matches upstream _aggregate_status_times().
     */
    private List<Map<String, Object>> aggregateStatusTimes(List<Map<String, Object>> statusChanges) {
        Map<String, long[]> statusTimes = new LinkedHashMap<>(); // [totalMinutes, visitCount]

        for (Map<String, Object> entry : statusChanges) {
            String status = (String) entry.get("status");
            if (status == null) continue;

            statusTimes.computeIfAbsent(status, k -> new long[]{0, 0});
            long[] data = statusTimes.get(status);

            Object durObj = entry.get("duration_minutes");
            if (durObj instanceof Number) {
                data[0] += ((Number) durObj).longValue();
                data[1]++;
            } else if (!entry.containsKey("exited_at")) {
                // Current status — count visit but no duration
                data[1]++;
            }
        }

        List<Map<String, Object>> summaries = new ArrayList<>();
        for (var e : statusTimes.entrySet()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("status", e.getKey());
            summary.put("total_duration_minutes", e.getValue()[0]);
            summary.put("total_duration_formatted", formatDuration(e.getValue()[0]));
            summary.put("visit_count", e.getValue()[1]);
            summaries.add(summary);
        }

        // Sort by total duration descending (matches upstream)
        summaries.sort((a, b) -> Long.compare(
                ((Number) b.get("total_duration_minutes")).longValue(),
                ((Number) a.get("total_duration_minutes")).longValue()));

        return summaries;
    }

    private static OffsetDateTime parseDate(String dateStr) {
        // Jira dates can be ISO 8601 with various formats
        try {
            return OffsetDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            // Try Jira's format: "2024-01-01T12:00:00.000+0000"
            return OffsetDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
        }
    }

    /** Format minutes into human-readable string matching upstream. */
    static String formatDuration(long minutes) {
        if (minutes <= 0) return "0m";
        long days = minutes / (24 * 60);
        long remaining = minutes % (24 * 60);
        long hours = remaining / 60;
        long mins = remaining % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(mins).append("m");
        return sb.toString().trim();
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
