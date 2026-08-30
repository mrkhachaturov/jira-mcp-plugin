package com.atlassian.mcp.plugin.tools.fields;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchFieldsTool extends TypedTool<SearchFieldsTool.Args> {

  public record Args(
      @ToolArg(
              "(Optional) Keyword matched against a field's id, key, name and JQL clause names."
                  + " Omitted, the first 'limit' fields are listed in Jira's own order.")
          String keyword,
      @ToolArg(value = "Maximum number of fields to return", defaultValue = "10") int limit) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public SearchFieldsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "search_fields";
  }

  @Override
  public String description() {
    return "Search Jira fields by keyword, ranking an exact match above a prefix above a substring.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    List<Map<String, Object>> fields =
        readFields(client.get("/rest/api/2/field", context.authHeader()));

    List<Map<String, Object>> result;
    if (args.keyword() == null) {
      result = fields.stream().limit(args.limit()).collect(Collectors.toList());
    } else {
      String needle = args.keyword().toLowerCase(Locale.ROOT);
      result =
          fields.stream()
              .map(field -> Map.entry(field, similarity(needle, field)))
              .filter(scored -> scored.getValue() > 0)
              .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
              .limit(args.limit())
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());
    }

    try {
      return mapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize the matching fields: " + e.getMessage());
    }
  }

  private List<Map<String, Object>> readFields(String raw) throws McpToolException {
    try {
      return mapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
    } catch (IOException e) {
      throw new McpToolException("Jira returned an unreadable field list: " + e.getMessage());
    }
  }

  /**
   * Score a field against a keyword. Higher = better match. Checks id, key, name, and clauseNames.
   */
  private static int similarity(String needle, Map<String, Object> field) {
    int best = 0;
    best = Math.max(best, score(needle, str(field.get("id"))));
    best = Math.max(best, score(needle, str(field.get("key"))));
    best = Math.max(best, score(needle, str(field.get("name"))));

    Object clauses = field.get("clauseNames");
    if (clauses instanceof List<?> list) {
      for (Object clause : list) {
        best = Math.max(best, score(needle, str(clause)));
      }
    }
    return best;
  }

  /** Simple fuzzy score: exact match > starts with > contains > every word present > no match. */
  private static int score(String needle, String candidate) {
    if (candidate.isEmpty()) return 0;
    String lower = candidate.toLowerCase(Locale.ROOT);
    if (lower.equals(needle)) return 100;
    if (lower.startsWith(needle)) return 80;
    if (lower.contains(needle)) return 60;
    if (needle.length() > 2) {
      for (String word : needle.split("[_\\s-]+")) {
        if (!lower.contains(word)) return 0;
      }
      return 40;
    }
    return 0;
  }

  private static String str(Object value) {
    return value != null ? value.toString() : "";
  }
}
