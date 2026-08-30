package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
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
 * Computes date information and status transition history for a Jira issue: fetches the issue with
 * its changelog, then derives per-transition durations and aggregated time-in-status summaries.
 */
public class GetIssueDatesTool extends TypedTool<GetIssueDatesTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              value = "Include status change history with timestamps and durations",
              defaultValue = "true")
          boolean includeStatusChanges,
      @ToolArg(value = "Include aggregated time spent in each status", defaultValue = "true")
          boolean includeStatusSummary) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssueDatesTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_dates";
  }

  @Override
  public String description() {
    return "Get date information and status transition history for a Jira issue. Returns dates (created, updated, due date, resolution date) and optionally status change history with time tracking for workflow analysis.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    boolean wantsHistory = args.includeStatusChanges() || args.includeStatusSummary();

    // The changelog is only expanded when something in the response needs it — it is by far the
    // heaviest part of the issue payload.
    String issueJson =
        client.get(
            "/rest/api/2/issue/"
                + args.issueKey()
                + "?fields=status,created,updated,duedate,resolutiondate"
                + (wantsHistory ? "&expand=changelog" : ""),
            context.authHeader());

    try {
      JsonNode issue = mapper.readTree(issueJson);
      JsonNode fields = issue.path("fields");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("issue_key", args.issueKey());
      result.put("created", fields.path("created").asText(null));
      result.put("updated", fields.path("updated").asText(null));
      result.put("due_date", nullableText(fields, "duedate"));
      result.put("resolution_date", nullableText(fields, "resolutiondate"));
      result.put("current_status", fields.path("status").path("name").asText(null));

      if (wantsHistory) {
        String createdStr = fields.path("created").asText(null);
        OffsetDateTime createdDate = createdStr != null ? parseDate(createdStr) : null;

        List<Map<String, Object>> statusChanges = parseStatusChanges(issue, createdDate);

        if (args.includeStatusChanges()) {
          result.put("status_changes", statusChanges);
        }
        if (args.includeStatusSummary()) {
          result.put("status_summary", aggregateStatusTimes(statusChanges));
        }
      }

      result.values().removeIf(v -> v == null);

      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new McpToolException("Failed to compute issue dates: " + e.getMessage());
    }
  }

  /** Parses the changelog into status transitions with the duration spent in each. */
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

  /** Aggregates the total time spent in each status across repeat visits. */
  private List<Map<String, Object>> aggregateStatusTimes(List<Map<String, Object>> statusChanges) {
    Map<String, long[]> statusTimes = new LinkedHashMap<>(); // [totalMinutes, visitCount]

    for (Map<String, Object> entry : statusChanges) {
      String status = (String) entry.get("status");
      if (status == null) continue;

      statusTimes.computeIfAbsent(status, k -> new long[] {0, 0});
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

    summaries.sort(
        (a, b) ->
            Long.compare(
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
      return OffsetDateTime.parse(
          dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }
  }

  /** Formats a minute count as a human-readable "1d 2h 3m" string. */
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
}
