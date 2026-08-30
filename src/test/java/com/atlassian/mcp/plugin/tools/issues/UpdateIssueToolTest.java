package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
                "issue_key", "PROJ-1",
                "fields", "{\"summary\":\"New summary\"}",
                "additional_fields", "{\"customfield_10010\":\"x\"}",
                "components", "Frontend, API"));

    assertEquals("New summary", fields.path("summary").asText());
    assertEquals("x", fields.path("customfield_10010").asText());
    assertEquals(2, fields.path("components").size());
    assertEquals("Frontend", fields.path("components").get(0).path("name").asText());
  }

  @Test
  public void onlyTheFieldsJsonIsSentWhenNothingElseIsPassed() throws Exception {
    JsonNode fields = putFields(Map.of("issue_key", "PROJ-1", "fields", "{\"summary\":\"S\"}"));

    assertEquals(Set.of("summary"), fieldNames(fields));
  }

  @Test
  public void markdownDescriptionIsConvertedToJiraMarkup() throws Exception {
    JsonNode fields =
        putFields(
            Map.of("issue_key", "PROJ-1", "fields", "{\"description\":\"## Updated\\ntext\"}"));

    String description = fields.path("description").asText();
    assertTrue(description, description.contains("h2. Updated"));
    assertTrue(description, description.contains("text"));
  }

  @Test
  public void theUpdatedIssueIsReReadAndWrapped() throws Exception {
    String result =
        tool.execute(Map.of("issue_key", "PROJ-1", "fields", "{\"summary\":\"S\"}"), "Bearer t");

    verify(client).get("/rest/api/2/issue/PROJ-1", "Bearer t");
    JsonNode parsed = MAPPER.readTree(result);
    assertTrue(parsed.path("success").asBoolean());
    assertEquals("PROJ-1", parsed.path("issue").path("key").asText());
  }

  @Test(expected = McpToolException.class)
  public void issueKeyIsRequired() throws Exception {
    tool.execute(Map.of("fields", "{}"), "Bearer t");
  }

  @Test(expected = McpToolException.class)
  public void fieldsIsRequired() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-1"), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(Set.of("issue_key", "fields", "additional_fields", "components"), props.keySet());
    assertEquals(
        Set.of("issue_key", "fields"),
        Set.copyOf((java.util.List<String>) tool.inputSchema().get("required")));
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new java.util.LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
