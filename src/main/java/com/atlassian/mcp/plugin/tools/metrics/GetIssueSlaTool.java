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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes SLA metrics for a Jira issue from its changelog. Deliberately does not call the Service
 * Desk SLA API: these metrics are derived from status history so they are available on every issue,
 * not just the ones covered by a JSM SLA configuration.
 */
public class GetIssueSlaTool extends TypedTool<GetIssueSlaTool.Args> {

  private static final String CYCLE_TIME = "cycle_time";
  private static final String LEAD_TIME = "lead_time";
  private static final String TIME_IN_STATUS = "time_in_status";
  private static final String DUE_DATE_COMPLIANCE = "due_date_compliance";
  private static final String RESOLUTION_TIME = "resolution_time";
  private static final String FIRST_RESPONSE_TIME = "first_response_time";

  private static final List<String> AVAILABLE_METRICS =
      List.of(
          CYCLE_TIME,
          LEAD_TIME,
          TIME_IN_STATUS,
          DUE_DATE_COMPLIANCE,
          RESOLUTION_TIME,
          FIRST_RESPONSE_TIME);

  private static final List<String> DEFAULT_METRICS = List.of(CYCLE_TIME, TIME_IN_STATUS);

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              "SLA metrics to calculate. One or more of cycle_time, lead_time, time_in_status,"
                  + " due_date_compliance, resolution_time, first_response_time. Defaults to"
                  + " cycle_time and time_in_status.")
          List<String> metrics,
      @ToolArg(value = "Include raw date values in the response", defaultValue = "false")
          boolean includeRawDates) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssueSlaTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_sla";
  }

  @Override
  public String description() {
    return "Calculate SLA metrics for a Jira issue. Computes time-based metrics including cycle time (first in-progress to resolution), lead time (creation to resolution or now), time spent in each status, due date compliance, resolution time, and first response time. All metrics are computed from issue changelog data.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    List<String> requestedMetrics = requestedMetrics(args.metrics());

    String issueJson =
        client.get(
            "/rest/api/2/issue/"
                + args.issueKey()
                + "?fields=status,created,updated,duedate,resolutiondate&expand=changelog",
            context.authHeader());

    try {
      JsonNode issue = mapper.readTree(issueJson);
      JsonNode fields = issue.path("fields");

      String createdStr = fields.path("created").asText(null);
      String updatedStr = fields.path("updated").asText(null);
      String dueDateStr = nullableText(fields, "duedate");
      String resolutionDateStr = nullableText(fields, "resolutiondate");
      String currentStatus = fields.path("status").path("name").asText(null);

      OffsetDateTime created = createdStr != null ? parseDate(createdStr) : null;
      OffsetDateTime resolutionDate =
          resolutionDateStr != null ? parseDate(resolutionDateStr) : null;
      OffsetDateTime now = OffsetDateTime.now();

      List<StatusTransition> transitions = parseStatusTransitions(issue);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("issue_key", args.issueKey());
      result.put("current_status", currentStatus);

      Map<String, Object> metricsResult = new LinkedHashMap<>();

      if (requestedMetrics.contains(CYCLE_TIME)) {
        metricsResult.put(CYCLE_TIME, computeCycleTime(transitions, resolutionDate, now));
      }
      if (requestedMetrics.contains(LEAD_TIME)) {
        metricsResult.put(LEAD_TIME, computeLeadTime(created, resolutionDate, now));
      }
      if (requestedMetrics.contains(TIME_IN_STATUS)) {
        metricsResult.put(TIME_IN_STATUS, computeTimeInStatus(transitions, created, now));
      }
      if (requestedMetrics.contains(DUE_DATE_COMPLIANCE)) {
        metricsResult.put(
            DUE_DATE_COMPLIANCE, computeDueDateCompliance(dueDateStr, resolutionDate, now));
      }
      if (requestedMetrics.contains(RESOLUTION_TIME)) {
        metricsResult.put(RESOLUTION_TIME, computeResolutionTime(created, resolutionDate));
      }
      if (requestedMetrics.contains(FIRST_RESPONSE_TIME)) {
        metricsResult.put(FIRST_RESPONSE_TIME, computeFirstResponseTime(created, transitions));
      }

      result.put("metrics", metricsResult);

      if (args.includeRawDates()) {
        Map<String, Object> rawDates = new LinkedHashMap<>();
        rawDates.put("created", createdStr);
        rawDates.put("updated", updatedStr);
        rawDates.put("due_date", dueDateStr);
        rawDates.put("resolution_date", resolutionDateStr);
        result.put("raw_dates", rawDates);
      }

      return mapper.writeValueAsString(result);

    } catch (Exception e) {
      throw new McpToolException("Failed to compute SLA metrics: " + e.getMessage());
    }
  }

  /**
   * The metrics to compute, defaulted when the caller names none. The schema layer cannot advertise
   * a multi-valued default, so the default is named in the parameter description and applied here.
   */
  private static List<String> requestedMetrics(List<String> requested) throws McpToolException {
    if (requested == null || requested.isEmpty()) return DEFAULT_METRICS;
    for (String metric : requested) {
      if (!AVAILABLE_METRICS.contains(metric)) {
        throw new McpToolException(
            "'metrics' contains unknown value '"
                + metric
                + "'; available metrics are "
                + String.join(", ", AVAILABLE_METRICS));
      }
    }
    return requested;
  }

  /** Simple record for a status transition. */
  private record StatusTransition(
      String fromStatus,
      String toStatus,
      String fromCategoryKey,
      String toCategoryKey,
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
          transitions.add(
              new StatusTransition(
                  item.path("fromString").asText(null),
                  item.path("toString").asText(null),
                  // Jira DC changelog doesn't always include category keys,
                  // but when available they are useful
                  item.path("from").asText(null),
                  item.path("to").asText(null),
                  ts));
        }
      }
    }
    transitions.sort((a, b) -> a.timestamp.compareTo(b.timestamp));
    return transitions;
  }

  /** Cycle time: first in-progress to resolution (or now). */
  private Map<String, Object> computeCycleTime(
      List<StatusTransition> transitions, OffsetDateTime resolutionDate, OffsetDateTime now) {
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
  private Map<String, Object> computeLeadTime(
      OffsetDateTime created, OffsetDateTime resolutionDate, OffsetDateTime now) {
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
  private List<Map<String, Object>> computeTimeInStatus(
      List<StatusTransition> transitions, OffsetDateTime created, OffsetDateTime now) {
    // Build status entries with durations
    List<Map<String, Object>> entries = new ArrayList<>();
    Map<String, long[]> aggregated = new LinkedHashMap<>(); // [totalMinutes, visitCount]

    // Initial status
    if (created != null && !transitions.isEmpty()) {
      String initialStatus = transitions.get(0).fromStatus;
      if (initialStatus != null) {
        OffsetDateTime exitedAt = transitions.get(0).timestamp;
        long minutes = ChronoUnit.MINUTES.between(created, exitedAt);
        aggregated.computeIfAbsent(initialStatus, k -> new long[] {0, 0});
        aggregated.get(initialStatus)[0] += minutes;
        aggregated.get(initialStatus)[1]++;
      }
    }

    // Each transition
    for (int i = 0; i < transitions.size(); i++) {
      String status = transitions.get(i).toStatus;
      if (status == null) continue;
      OffsetDateTime enteredAt = transitions.get(i).timestamp;
      OffsetDateTime exitedAt =
          (i + 1 < transitions.size()) ? transitions.get(i + 1).timestamp : now;
      long minutes = ChronoUnit.MINUTES.between(enteredAt, exitedAt);

      aggregated.computeIfAbsent(status, k -> new long[] {0, 0});
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

    entries.sort(
        (a, b) ->
            Long.compare(
                ((Number) b.get("total_duration_minutes")).longValue(),
                ((Number) a.get("total_duration_minutes")).longValue()));
    return entries;
  }

  /** Due date compliance check. */
  private Map<String, Object> computeDueDateCompliance(
      String dueDateStr, OffsetDateTime resolutionDate, OffsetDateTime now) {
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
  private Map<String, Object> computeResolutionTime(
      OffsetDateTime created, OffsetDateTime resolutionDate) {
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
  private Map<String, Object> computeFirstResponseTime(
      OffsetDateTime created, List<StatusTransition> transitions) {
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
      return OffsetDateTime.parse(
          dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }
  }

  private static String nullableText(JsonNode node, String field) {
    JsonNode val = node.path(field);
    return val.isNull() || val.isMissingNode() ? null : val.asText(null);
  }
}
