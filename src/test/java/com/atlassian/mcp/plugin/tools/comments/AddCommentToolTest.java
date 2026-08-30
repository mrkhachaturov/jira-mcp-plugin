package com.atlassian.mcp.plugin.tools.comments;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AddCommentToolTest {

  private JiraRestClient client;
  private AddCommentTool tool;
  private ArgumentCaptor<String> path;
  private ArgumentCaptor<String> body;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new AddCommentTool(client);
    path = ArgumentCaptor.forClass(String.class);
    body = ArgumentCaptor.forClass(String.class);
  }

  private JsonNode postFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    verify(client).post(path.capture(), body.capture(), any());
    return new ObjectMapper().readTree(body.getValue());
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-3");
    args.put("body", "**shipped**");
    args.put("visibility", "{\"type\":\"group\",\"value\":\"jira-users\"}");

    JsonNode json = postFor(args);

    assertEquals("/rest/api/2/issue/PROJ-3/comment", path.getValue());
    // Markdown is converted to Jira markup before it is sent.
    assertEquals("*shipped*", json.path("body").asText());
    assertEquals("group", json.path("visibility").path("type").asText());
    assertEquals("jira-users", json.path("visibility").path("value").asText());
  }

  @Test
  public void visibilityIsOmittedWhenAbsent() throws Exception {
    assertFalse(postFor(Map.of("issue_key", "PROJ-3", "body", "plain")).has("visibility"));
  }

  @Test
  public void malformedVisibilityIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-3", "body", "plain", "visibility", "jira-users"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("visibility"));
  }

  @Test
  public void missingBodyIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-3"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("body"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "body", "visibility"), props.keySet());
    assertEquals(
        Set.of("issue_key", "body"), Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
