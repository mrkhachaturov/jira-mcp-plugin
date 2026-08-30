package com.atlassian.mcp.plugin.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared {@link McpTool#outputSchema()} and {@link McpTool#iconUri()} defaults for the five
 * UI-linked tools whose {@code structuredContent} payload renders as an MCP Apps widget in
 * compatible clients.
 *
 * <p>The structuredContent shape is produced by {@code
 * com.atlassian.mcp.plugin.ResourceContextBuilder} and is identical across the five tools — {@code
 * get_issue} returns a one-element {@code issues} array while the others return many. A single
 * schema covers both cases.
 */
public final class UiToolDefaults {

  private UiToolDefaults() {}

  /** JSON Schema describing the issue-card structuredContent payload. */
  public static final Map<String, Object> ISSUE_LIST_OUTPUT_SCHEMA = buildIssueListSchema();

  private static Map<String, Object> buildIssueListSchema() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();

    props.put(
        "currentUser",
        obj(
            Map.of(
                "name", typed("string"),
                "displayName", typed("string"))));
    props.put("baseUrl", typed("string"));
    props.put("totalCount", typed("integer"));

    Map<String, Object> issueProps = new LinkedHashMap<>();
    issueProps.put("key", typed("string"));
    issueProps.put("summary", typed("string"));
    issueProps.put("status", namedRef());
    issueProps.put("priority", namedRef());
    issueProps.put("issue_type", namedRef());
    issueProps.put("assignee", userOrNull());
    issueProps.put("reporter", userOrNull());
    issueProps.put("description", nullableString());
    issueProps.put("created", typed("string"));
    issueProps.put("updated", typed("string"));
    Map<String, Object> issue = new LinkedHashMap<>();
    issue.put("type", "object");
    issue.put("properties", issueProps);

    Map<String, Object> issues = new LinkedHashMap<>();
    issues.put("type", "array");
    issues.put("items", issue);
    props.put("issues", issues);

    root.put("properties", props);
    root.put("required", List.of("currentUser", "baseUrl", "issues", "totalCount"));
    return root;
  }

  private static Map<String, Object> typed(String type) {
    return Map.of("type", type);
  }

  private static Map<String, Object> obj(Map<String, Object> props) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "object");
    m.put("properties", props);
    return m;
  }

  private static Map<String, Object> namedRef() {
    return obj(Map.of("name", typed("string")));
  }

  private static Map<String, Object> userOrNull() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", List.of("object", "null"));
    m.put(
        "properties",
        Map.of(
            "name", typed("string"),
            "displayName", typed("string")));
    return m;
  }

  private static Map<String, Object> nullableString() {
    return Map.of("type", List.of("string", "null"));
  }
}
