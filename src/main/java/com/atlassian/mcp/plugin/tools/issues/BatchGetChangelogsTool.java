package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.BatchProgressBridge;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BatchGetChangelogsTool extends DeclarativeTool {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final ToolParam<String> ISSUE_IDS_OR_KEYS =
      ToolParam.string(
              "issue_ids_or_keys",
              "Comma-separated list of Jira issue IDs or keys (e.g. 'PROJ-123,PROJ-124')")
          .required();
  private static final ToolParam<String> FIELDS =
      ToolParam.string(
          "fields",
          "(Optional) Comma-separated list of fields to filter changelogs by (e.g."
              + " 'status,assignee'). Default to None for all fields.");
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer(
              "limit",
              "Maximum number of changelogs to return in result for each issue. Default to -1 for"
                  + " all changelogs.")
          .withDefault(-1);

  private final JiraRestClient client;

  public BatchGetChangelogsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "batch_get_changelogs";
  }

  @Override
  public String description() {
    return "Get changelogs for multiple Jira issues. Retrieves the change history for each issue showing field changes, status transitions, and who made each change.";
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_IDS_OR_KEYS, FIELDS, LIMIT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    return run(args, authHeader, (current, total, message) -> {});
  }

  @Override
  public String executeWithSdkProgress(
      Map<String, Object> args,
      String authHeader,
      McpSyncServerExchange exchange,
      Object progressToken)
      throws McpToolException {
    return run(
        new ToolArgs(params(), args),
        authHeader,
        BatchProgressBridge.bridge(exchange, progressToken));
  }

  @Override
  public String executeWithProgress(
      Map<String, Object> args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    return run(new ToolArgs(params(), args), authHeader, progress);
  }

  private String run(ToolArgs args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    String issueIdsOrKeys = args.require(ISSUE_IDS_OR_KEYS);
    Set<String> fieldFilter = parseFieldFilter(args.get(FIELDS));
    int limit = args.get(LIMIT);

    List<String> trimmedKeys = new ArrayList<>();
    for (String k : issueIdsOrKeys.split(",")) {
      String t = k.trim();
      if (!t.isEmpty()) trimmedKeys.add(t);
    }

    int total = trimmedKeys.size();
    List<String> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      String key = trimmedKeys.get(i);
      progress.report(
          i, total, "Fetching changelog for " + key + " (" + (i + 1) + "/" + total + ")");

      try {
        // expand=changelog works on both Cloud and DC; the /changelog sub-resource is Cloud-only.
        String changelog =
            client.get(
                "/rest/api/2/issue/" + key + "?expand=changelog&fields=key,summary", authHeader);
        results.add("\"" + key + "\":" + narrowChangelog(changelog, fieldFilter, limit));
      } catch (Exception e) {
        errors.add("\"" + key + "\":{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
      }
    }

    progress.report(
        total, total, "Completed: " + results.size() + " fetched, " + errors.size() + " errors");

    StringBuilder sb = new StringBuilder("{\"changelogs\":{");
    sb.append(String.join(",", results));
    sb.append("},\"fetched\":").append(results.size());
    sb.append(",\"errors\":").append(errors.size());
    if (!errors.isEmpty()) {
      sb.append(",\"failed\":{").append(String.join(",", errors)).append("}");
    }
    sb.append("}");
    return sb.toString();
  }

  private static Set<String> parseFieldFilter(String fields) {
    if (fields == null || fields.isBlank()) return Set.of();
    Set<String> names = new LinkedHashSet<>();
    for (String field : fields.split(",")) {
      String trimmed = field.trim();
      if (!trimmed.isEmpty()) names.add(trimmed.toLowerCase(Locale.ROOT));
    }
    return names;
  }

  /**
   * Applies the field filter and the per-issue cap to one issue's {@code changelog}. Both are done
   * here rather than on the request because Jira only ever returns the whole history for {@code
   * expand=changelog} — it takes neither a field filter nor a page size on that expansion. The cap
   * keeps the most recent entries, since Jira orders histories oldest-first.
   */
  private static String narrowChangelog(String response, Set<String> fieldFilter, int limit) {
    if (fieldFilter.isEmpty() && limit < 0) return response;

    JsonNode root;
    try {
      root = MAPPER.readTree(response);
    } catch (Exception e) {
      return response;
    }
    JsonNode changelog = root.path("changelog");
    if (!changelog.isObject() || !changelog.path("histories").isArray()) return response;

    ArrayNode histories = (ArrayNode) changelog.get("histories");
    ArrayNode kept = MAPPER.createArrayNode();
    for (JsonNode history : histories) {
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

    try {
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      return response;
    }
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
