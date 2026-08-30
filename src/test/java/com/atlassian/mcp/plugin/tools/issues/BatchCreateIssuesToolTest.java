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

public class BatchCreateIssuesToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TWO_ISSUES =
      "[{\"project_key\":\"PROJ\",\"summary\":\"One\",\"issue_type\":\"Task\"},"
          + "{\"project_key\":\"PROJ\",\"summary\":\"Two\",\"issue_type\":\"Bug\","
          + "\"description\":\"d\",\"assignee\":\"jdoe\"}]";

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
  public void validateOnlyReportsEntriesMissingARequiredField() throws Exception {
    String result =
        tool.execute(
            Map.of(
                "issues",
                "[{\"project_key\":\"PROJ\",\"summary\":\"No type\"}]",
                "validate_only",
                true),
            "Bearer t");

    JsonNode parsed = MAPPER.readTree(result);
    assertEquals(0, parsed.path("valid").asInt());
    assertEquals(1, parsed.path("errors").asInt());
    assertTrue(result, parsed.path("failed").get(0).path("error").asText().contains("issue_type"));
  }

  @Test
  public void validateOnlyDefaultsToCreating() throws Exception {
    tool.execute(Map.of("issues", TWO_ISSUES), "Bearer t");

    verify(client, times(2)).post(anyString(), anyString(), any());
  }

  @Test(expected = McpToolException.class)
  public void issuesIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test(expected = McpToolException.class)
  public void invalidIssuesJsonIsRejected() throws Exception {
    tool.execute(Map.of("issues", "not json"), "Bearer t");
  }

  @Test
  public void progressIsReportedForEveryIssue() throws Exception {
    List<String> messages = new java.util.ArrayList<>();
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
    List<String> messages = new java.util.ArrayList<>();
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
}
