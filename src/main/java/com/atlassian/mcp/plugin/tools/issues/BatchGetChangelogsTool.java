package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BatchGetChangelogsTool extends TypedTool<BatchGetChangelogsTool.Args> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public record Args(
      @ToolArg(value = "Jira issue IDs or keys, e.g. ['PROJ-123', 'PROJ-124']", required = true)
          List<String> issueIdsOrKeys,
      @ToolArg(
              "(Optional) Keep only history entries that changed one of these fields, e.g."
                  + " ['status', 'assignee']. Omit to keep every change.")
          List<String> changedFields,
      @ToolArg(
              value =
                  "Maximum number of history entries to keep per issue, keeping the most recent."
                      + " -1 keeps the whole history.",
              defaultValue = "-1")
          int limit) {}

  private final JiraRestClient client;

  public BatchGetChangelogsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "batch_get_changelogs";
  }

  @Override
  public String description() {
    return "Get changelogs for multiple Jira issues. Retrieves the change history for each issue"
        + " showing field changes, status transitions, and who made each change.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public boolean supportsProgress() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    List<String> keys = trimmed(args.issueIdsOrKeys());
    if (keys.isEmpty()) {
      throw new McpToolException("'issue_ids_or_keys' must name at least one issue");
    }
    Set<String> fieldFilter = lowercased(args.changedFields());

    ObjectNode changelogs = MAPPER.createObjectNode();
    ObjectNode failed = MAPPER.createObjectNode();
    int total = keys.size();

    for (int i = 0; i < total; i++) {
      String key = keys.get(i);
      context.reportProgress(
          i, total, "Fetching changelog for " + key + " (" + (i + 1) + "/" + total + ")");

      try {
        // expand=changelog works on both Cloud and DC; the /changelog sub-resource is Cloud-only.
        String response =
            client.get(
                "/rest/api/2/issue/" + key + "?expand=changelog&fields=key,summary",
                context.authHeader());
        changelogs.set(key, narrowChangelog(MAPPER.readTree(response), fieldFilter, args.limit()));
      } catch (Exception e) {
        failed.set(key, MAPPER.createObjectNode().put("error", String.valueOf(e.getMessage())));
      }
    }

    context.reportProgress(
        total, total, "Completed: " + changelogs.size() + " fetched, " + failed.size() + " errors");

    ObjectNode result = MAPPER.createObjectNode();
    result.set("changelogs", changelogs);
    result.put("fetched", changelogs.size());
    result.put("errors", failed.size());
    if (!failed.isEmpty()) {
      result.set("failed", failed);
    }
    return result.toString();
  }

  private static List<String> trimmed(List<String> values) {
    List<String> kept = new ArrayList<>();
    for (String value : values) {
      String trimmed = value == null ? "" : value.trim();
      if (!trimmed.isEmpty()) kept.add(trimmed);
    }
    return kept;
  }

  private static Set<String> lowercased(List<String> values) {
    if (values == null) return Set.of();
    Set<String> names = new LinkedHashSet<>();
    for (String value : trimmed(values)) {
      names.add(value.toLowerCase(Locale.ROOT));
    }
    return names;
  }

  /**
   * Applies the field filter and the per-issue cap to one issue's {@code changelog}. Both are done
   * here rather than on the request because Jira only ever returns the whole history for {@code
   * expand=changelog} — it takes neither a field filter nor a page size on that expansion. The cap
   * keeps the most recent entries, since Jira orders histories oldest-first.
   */
  private static JsonNode narrowChangelog(JsonNode issue, Set<String> fieldFilter, int limit) {
    if (fieldFilter.isEmpty() && limit < 0) return issue;

    JsonNode changelog = issue.path("changelog");
    if (!changelog.isObject() || !changelog.path("histories").isArray()) return issue;

    ArrayNode kept = MAPPER.createArrayNode();
    for (JsonNode history : changelog.get("histories")) {
      JsonNode narrowed = fieldFilter.isEmpty() ? history : withOnlyFields(history, fieldFilter);
      if (narrowed != null) kept.add(narrowed);
    }
    if (limit >= 0 && kept.size() > limit) {
      ArrayNode capped = MAPPER.createArrayNode();
      for (int i = kept.size() - limit; i < kept.size(); i++) {
        capped.add(kept.get(i));
      }
      kept = capped;
    }

    ObjectNode changelogNode = (ObjectNode) changelog;
    changelogNode.set("histories", kept);
    changelogNode.put("returned", kept.size());
    return issue;
  }

  /** Returns the history with only the matching items, or null when none of them match. */
  private static JsonNode withOnlyFields(JsonNode history, Set<String> fieldFilter) {
    if (!history.isObject() || !history.path("items").isArray()) return null;

    ArrayNode items = MAPPER.createArrayNode();
    for (JsonNode item : history.get("items")) {
      String field = item.path("field").asText("");
      if (fieldFilter.contains(field.toLowerCase(Locale.ROOT))) items.add(item);
    }
    if (items.isEmpty()) return null;

    ObjectNode copy = ((ObjectNode) history).deepCopy();
    copy.set("items", items);
    return copy;
  }
}
