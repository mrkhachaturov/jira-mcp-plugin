package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UpdateIssueToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private UpdateIssueTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.put(anyString(), anyString(), any())).thenReturn("");
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-1\"}");
    tool = new UpdateIssueTool(client);
  }

  private JsonNode putFields(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).put(path.capture(), body.capture(), eq("Bearer t"));
    assertEquals("/rest/api/2/issue/PROJ-1", path.getValue());
    return MAPPER.readTree(body.getValue()).path("fields");
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    JsonNode fields =
        putFields(
            Map.of(
                "issue_key",
                "PROJ-1",
                "fields",
                Map.of("summary", "New summary", "customfield_10010", "x"),
                "components",
                List.of("Frontend", "API")));

    assertEquals("New summary", fields.path("summary").asText());
    assertEquals("x", fields.path("customfield_10010").asText());
    assertEquals(2, fields.path("components").size());
    assertEquals("Frontend", fields.path("components").get(0).path("name").asText());
    assertEquals("API", fields.path("components").get(1).path("name").asText());
  }

  /** A nested field value keeps its structure instead of being flattened into a string. */
  @Test
  public void structuredFieldValuesAreForwardedAsObjects() throws Exception {
    JsonNode fields =
        putFields(
            Map.of(
                "issue_key",
                "PROJ-1",
                "fields",
                Map.of("priority", Map.of("name", "High"), "labels", List.of("urgent", "api"))));

    assertEquals("High", fields.path("priority").path("name").asText());
    assertEquals(2, fields.path("labels").size());
    assertEquals("urgent", fields.path("labels").get(0).asText());
  }

  @Test
  public void onlyTheDeclaredFieldsAreSent() throws Exception {
    JsonNode fields = putFields(Map.of("issue_key", "PROJ-1", "fields", Map.of("summary", "S")));

    assertEquals(Set.of("summary"), fieldNames(fields));
  }

  @Test
  public void markdownDescriptionIsConvertedToJiraMarkup() throws Exception {
    JsonNode fields =
        putFields(
            Map.of("issue_key", "PROJ-1", "fields", Map.of("description", "## Updated\ntext")));

    String description = fields.path("description").asText();
    assertTrue(description, description.contains("h2. Updated"));
    assertTrue(description, description.contains("text"));
  }

  @Test
  public void theUpdatedIssueIsReReadAndWrapped() throws Exception {
    String result =
        tool.execute(Map.of("issue_key", "PROJ-1", "fields", Map.of("summary", "S")), "Bearer t");

    verify(client).get("/rest/api/2/issue/PROJ-1", "Bearer t");
    JsonNode parsed = MAPPER.readTree(result);
    assertTrue(parsed.path("success").asBoolean());
    assertEquals("PROJ-1", parsed.path("issue").path("key").asText());
  }

  /** A rejected update must not be reported as a serialisation problem. */
  @Test
  public void aJiraFailureIsReportedAsItself() throws Exception {
    when(client.put(anyString(), anyString(), any()))
        .thenThrow(new McpToolException("Jira API error (403): you lack permission"));

    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-1", "fields", Map.of("summary", "S")), "Bearer t"));

    assertEquals("Jira API error (403): you lack permission", e.getMessage());
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    for (Map<String, Object> args :
        List.of(
            Map.<String, Object>of("fields", Map.of()),
            Map.<String, Object>of("issue_key", "PROJ-1"))) {
      assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
    }
    verifyNoInteractions(client);
  }

  @Test
  public void fieldsMustBeAnObject() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-1", "fields", "{\"summary\":\"S\"}"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("fields"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParametersAreRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of(
                        "issue_key",
                        "PROJ-1",
                        "fields",
                        Map.of("summary", "S"),
                        "additional_fields",
                        Map.of("customfield_10010", "x")),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("additional_fields"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "fields", "components"), props.keySet());
    assertEquals(Set.of("issue_key", "fields"), Set.copyOf((List<String>) schema.get("required")));
    assertEquals("object", ((Map<String, Object>) props.get("fields")).get("type"));
    assertEquals("array", ((Map<String, Object>) props.get("components")).get("type"));
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new java.util.LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
