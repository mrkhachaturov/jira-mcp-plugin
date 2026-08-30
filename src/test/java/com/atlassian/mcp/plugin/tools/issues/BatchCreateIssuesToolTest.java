package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class BatchCreateIssuesToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<Map<String, Object>> TWO_ISSUES =
      List.of(
          Map.of("project_key", "PROJ", "summary", "One", "issue_type", "Task"),
          Map.of(
              "project_key", "PROJ",
              "summary", "Two",
              "issue_type", "Bug",
              "description", "d",
              "assignee", "jdoe"));

  private JiraRestClient client;
  private BatchCreateIssuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"key\":\"PROJ-1\"}");
    tool = new BatchCreateIssuesTool(client);
  }

  @Test
  public void everyIssueInTheListIsPosted() throws Exception {
    String result = tool.execute(Map.of("issues", TWO_ISSUES), "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).post(eq("/rest/api/2/issue"), body.capture(), eq("Bearer t"));

    List<String> bodies = body.getAllValues();
    assertEquals("One", MAPPER.readTree(bodies.get(0)).path("fields").path("summary").asText());
    JsonNode second = MAPPER.readTree(bodies.get(1)).path("fields");
    assertEquals("Bug", second.path("issuetype").path("name").asText());
    assertEquals("jdoe", second.path("assignee").path("name").asText());
    assertEquals("d", second.path("description").asText());

    assertEquals(2, MAPPER.readTree(result).path("created").asInt());
  }

  @Test
  public void componentsDeclaredOnAnIssueReachTheRequestBody() throws Exception {
    tool.execute(
        Map.of(
            "issues",
            List.of(
                Map.of(
                    "project_key", "PROJ",
                    "summary", "One",
                    "issue_type", "Task",
                    "components", List.of("Frontend", "API")))),
        "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(anyString(), body.capture(), any());

    JsonNode components = MAPPER.readTree(body.getValue()).path("fields").path("components");
    assertEquals(2, components.size());
    assertEquals("Frontend", components.get(0).path("name").asText());
    assertEquals("API", components.get(1).path("name").asText());
  }

  @Test
  public void validateOnlySkipsEveryWrite() throws Exception {
    String result = tool.execute(Map.of("issues", TWO_ISSUES, "validate_only", true), "Bearer t");

    verify(client, never()).post(anyString(), anyString(), any());

    JsonNode parsed = MAPPER.readTree(result);
    assertTrue(parsed.path("validate_only").asBoolean());
    assertEquals(2, parsed.path("valid").asInt());
    assertEquals(0, parsed.path("errors").asInt());
    assertEquals("One", parsed.path("issues").get(0).path("fields").path("summary").asText());
  }

  @Test
  public void validateOnlyDefaultsToCreating() throws Exception {
    tool.execute(Map.of("issues", TWO_ISSUES), "Bearer t");

    verify(client, times(2)).post(anyString(), anyString(), any());
  }

  @Test
  public void anIssueMissingARequiredFieldIsRejectedByIndex() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issues", List.of(Map.of("project_key", "PROJ", "summary", "No type"))),
                    "Bearer t"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("issues[0].issue_type"));
    verifyNoInteractions(client);
  }

  @Test
  public void anIssueCarryingAnUndeclaredFieldIsRejected() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of(
                        "issues",
                        List.of(
                            Map.of(
                                "project_key", "PROJ",
                                "summary", "One",
                                "issue_type", "Task",
                                "priority", "High"))),
                    "Bearer t"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("priority"));
    verifyNoInteractions(client);
  }

  @Test(expected = McpToolException.class)
  public void issuesIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test(expected = McpToolException.class)
  public void anIssuesValueThatIsNotAnObjectListIsRejected() throws Exception {
    tool.execute(Map.of("issues", "not json"), "Bearer t");
  }

  @Test
  public void progressIsReportedForEveryIssue() throws Exception {
    List<String> messages = new ArrayList<>();
    tool.executeWithProgress(
        Map.of("issues", TWO_ISSUES),
        "Bearer t",
        (current, total, message) -> messages.add(message));

    assertEquals(3, messages.size());
    assertTrue(messages.get(0), messages.get(0).startsWith("Creating issue 1 of 2"));
    assertTrue(messages.get(2), messages.get(2).contains("2 created"));
  }

  @Test
  public void progressWordingFollowsValidateOnly() throws Exception {
    List<String> messages = new ArrayList<>();
    tool.executeWithProgress(
        Map.of("issues", TWO_ISSUES, "validate_only", true),
        "Bearer t",
        (current, total, message) -> messages.add(message));

    assertTrue(messages.get(0), messages.get(0).startsWith("Validating issue 1 of 2"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(Set.of("issues", "validate_only"), props.keySet());
    assertEquals(List.of("issues"), tool.inputSchema().get("required"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaDescribesTheShapeOfOneIssue() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> issues = (Map<String, Object>) props.get("issues");
    Map<String, Object> item = (Map<String, Object>) issues.get("items");

    assertEquals("array", issues.get("type"));
    assertEquals("object", item.get("type"));
    assertEquals(Boolean.FALSE, item.get("additionalProperties"));
    assertEquals(
        Set.of("project_key", "summary", "issue_type", "description", "assignee", "components"),
        ((Map<String, Object>) item.get("properties")).keySet());
    assertEquals(List.of("project_key", "summary", "issue_type"), item.get("required"));
  }
}
