package com.atlassian.mcp.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.UiToolDefaults;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class ResourceContextBuilderTest {

  private static final String ONE_ISSUE =
      """
      {
        "key": "PROJ-1",
        "fields": {
          "summary": "A summary",
          "status": {
            "name": "In Progress",
            "statusCategory": {"key": "indeterminate", "colorName": "yellow", "name": "In Progress"}
          },
          "priority": {"name": "High"},
          "issuetype": {"name": "Bug"},
          "assignee": {"name": "jdoe", "displayName": "J Doe"},
          "reporter": {"name": "asmith", "displayName": "A Smith"},
          "description": "Some description",
          "comment": {"comments": [
            {"author": {"name": "jdoe", "displayName": "J Doe"},
             "body": "A comment", "created": "2026-01-01", "updated": "2026-01-02"}
          ]},
          "created": "2026-01-01",
          "updated": "2026-01-02"
        }
      }
      """;

  private ResourceContextBuilder builder;

  @Before
  public void setUp() {
    McpPluginConfig config = mock(McpPluginConfig.class);
    when(config.getJiraBaseUrlOverride()).thenReturn("https://jira.example");
    builder = new ResourceContextBuilder(config, mock(ApplicationProperties.class));
  }

  private ObjectNode buildOne() throws Exception {
    return builder.build("get_issue", ONE_ISSUE, "jdoe", "J Doe");
  }

  @Test
  public void theWidgetReceivesTheFieldNamesItReads() throws Exception {
    ObjectNode sc = buildOne();

    assertEquals(Set.of("currentUser", "baseUrl", "issues", "totalCount"), names(sc));
    assertEquals("https://jira.example", sc.path("baseUrl").asText());
    assertEquals("jdoe", sc.path("currentUser").path("name").asText());
    assertEquals(1, sc.path("totalCount").asInt());

    JsonNode issue = sc.path("issues").get(0);
    assertEquals(
        Set.of(
            "key",
            "summary",
            "status",
            "priority",
            "issue_type",
            "assignee",
            "reporter",
            "description",
            "comments",
            "created",
            "updated"),
        names(issue));
  }

  @Test
  public void aJiraIssueIsNormalisedIntoTheCardShape() throws Exception {
    JsonNode issue = buildOne().path("issues").get(0);

    assertEquals("PROJ-1", issue.path("key").asText());
    assertEquals("In Progress", issue.path("status").path("name").asText());
    assertEquals("indeterminate", issue.path("status").path("category").asText());
    assertEquals("yellow", issue.path("status").path("colorName").asText());
    assertEquals("High", issue.path("priority").path("name").asText());
    assertEquals("Bug", issue.path("issue_type").path("name").asText());
    assertEquals("J Doe", issue.path("assignee").path("displayName").asText());
    assertEquals("A comment", issue.path("comments").get(0).path("body").asText());
  }

  @Test
  public void anAbsentAssigneeStaysNullRatherThanVanishing() throws Exception {
    ObjectNode sc = builder.build("get_issue", "{\"key\":\"PROJ-2\",\"fields\":{}}", "u", "U");
    JsonNode issue = sc.path("issues").get(0);

    assertTrue(issue.has("assignee"));
    assertTrue(issue.path("assignee").isNull());
    assertTrue(issue.path("description").isNull());
    assertEquals(0, issue.path("comments").size());
  }

  @Test
  public void aListToolCarriesJirasOwnTotal() throws Exception {
    String search = "{\"total\": 42, \"issues\": [" + ONE_ISSUE + "]}";
    ObjectNode sc = builder.build("search", search, "u", "U");

    assertEquals(1, sc.path("issues").size());
    assertEquals(42, sc.path("totalCount").asInt());
  }

  @Test
  public void thePayloadMatchesTheSchemaTheToolAdvertises() throws Exception {
    List<String> problems = new ArrayList<>();
    conform(UiToolDefaults.ISSUE_LIST_OUTPUT_SCHEMA, buildOne(), "", problems);

    assertEquals(List.of(), problems);
  }

  /** Every property the schema declares must be present in the payload with the declared type. */
  @SuppressWarnings("unchecked")
  private static void conform(
      Map<String, Object> schema, JsonNode node, String path, List<String> problems) {
    Map<String, Object> properties =
        (Map<String, Object>) schema.getOrDefault("properties", Map.of());

    for (Map.Entry<String, Object> property : properties.entrySet()) {
      String at = path + "." + property.getKey();
      Map<String, Object> declared = (Map<String, Object>) property.getValue();
      JsonNode value = node.get(property.getKey());

      if (value == null) {
        problems.add(at + " is declared but absent");
        continue;
      }
      if (value.isNull()) continue;

      String type = String.valueOf(declared.get("type"));
      switch (type) {
        case "object" -> {
          if (!value.isObject()) {
            problems.add(at + " should be an object, got " + value.getNodeType());
          } else {
            conform(declared, value, at, problems);
          }
        }
        case "array" -> {
          if (!value.isArray()) {
            problems.add(at + " should be an array, got " + value.getNodeType());
          } else {
            Map<String, Object> items = (Map<String, Object>) declared.get("items");
            for (int i = 0; i < value.size(); i++) {
              conform(items, value.get(i), at + "[" + i + "]", problems);
            }
          }
        }
        case "string" -> {
          if (!value.isTextual()) {
            problems.add(at + " should be a string, got " + value.getNodeType());
          }
        }
        case "integer" -> {
          if (!value.isIntegralNumber()) {
            problems.add(at + " should be an integer, got " + value.getNodeType());
          }
        }
        default -> problems.add(at + " has an unexpected declared type " + type);
      }
    }
  }

  private static Set<String> names(JsonNode node) {
    Set<String> names = new LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
