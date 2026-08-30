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

public class CreateIssueToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private CreateIssueTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"key\":\"PROJ-1\"}");
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-1\"}");
    tool = new CreateIssueTool(client);
  }

  private JsonNode postedFields(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(eq("/rest/api/2/issue"), body.capture(), eq("Bearer t"));
    return MAPPER.readTree(body.getValue()).path("fields");
  }

  @Test
  public void everyDeclaredParamReachesTheRequestBody() throws Exception {
    JsonNode fields =
        postedFields(
            Map.of(
                "project_key", "PROJ",
                "summary", "A summary",
                "issue_type", "Bug",
                "assignee", "jdoe",
                "description", "hello world",
                "components", "Frontend, API",
                "additional_fields", "{\"labels\":[\"urgent\"]}"));

    assertEquals("PROJ", fields.path("project").path("key").asText());
    assertEquals("A summary", fields.path("summary").asText());
    assertEquals("Bug", fields.path("issuetype").path("name").asText());
    assertEquals("jdoe", fields.path("assignee").path("name").asText());
    assertTrue(
        fields.path("description").asText(),
        fields.path("description").asText().contains("hello world"));
    assertEquals(2, fields.path("components").size());
    assertEquals("Frontend", fields.path("components").get(0).path("name").asText());
    assertEquals("API", fields.path("components").get(1).path("name").asText());
    assertEquals("urgent", fields.path("labels").get(0).asText());
  }

  @Test
  public void optionalFieldsAreOmittedWhenAbsent() throws Exception {
    JsonNode fields =
        postedFields(Map.of("project_key", "PROJ", "summary", "S", "issue_type", "Task"));

    assertEquals(Set.of("project", "summary", "issuetype"), fieldNames(fields));
  }

  @Test
  public void theCreatedIssueIsReReadAndWrapped() throws Exception {
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-1\",\"fields\":{}}");

    String result =
        tool.execute(
            Map.of("project_key", "PROJ", "summary", "S", "issue_type", "Task"), "Bearer t");

    verify(client).get("/rest/api/2/issue/PROJ-1", "Bearer t");
    JsonNode parsed = MAPPER.readTree(result);
    assertTrue(parsed.path("success").asBoolean());
    assertEquals("PROJ-1", parsed.path("issue").path("key").asText());
  }

  @Test
  public void invalidAdditionalFieldsJsonIsRejected() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of(
                        "project_key", "PROJ",
                        "summary", "S",
                        "issue_type", "Task",
                        "additional_fields", "not json"),
                    "Bearer t"));
    assertTrue(thrown.getMessage(), thrown.getMessage().contains("additional_fields"));
  }

  @Test(expected = McpToolException.class)
  public void projectKeyIsRequired() throws Exception {
    tool.execute(Map.of("summary", "S", "issue_type", "Task"), "Bearer t");
  }

  @Test(expected = McpToolException.class)
  public void summaryIsRequired() throws Exception {
    tool.execute(Map.of("project_key", "PROJ", "issue_type", "Task"), "Bearer t");
  }

  @Test(expected = McpToolException.class)
  public void issueTypeIsRequired() throws Exception {
    tool.execute(Map.of("project_key", "PROJ", "summary", "S"), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(
        Set.of(
            "project_key",
            "summary",
            "issue_type",
            "assignee",
            "description",
            "components",
            "additional_fields"),
        props.keySet());
    assertEquals(
        Set.of("project_key", "summary", "issue_type"),
        Set.copyOf((java.util.List<String>) tool.inputSchema().get("required")));
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new java.util.LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
