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

public class EditCommentToolTest {

  private JiraRestClient client;
  private EditCommentTool tool;
  private ArgumentCaptor<String> path;
  private ArgumentCaptor<String> body;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");
    tool = new EditCommentTool(client);
    path = ArgumentCaptor.forClass(String.class);
    body = ArgumentCaptor.forClass(String.class);
  }

  private JsonNode putFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    verify(client).put(path.capture(), body.capture(), any());
    return new ObjectMapper().readTree(body.getValue());
  }

  private static Map<String, Object> required() {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-3");
    args.put("comment_id", "10100");
    args.put("body", "**revised**");
    return args;
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = required();
    args.put("visibility", "{\"type\":\"group\",\"value\":\"jira-users\"}");

    JsonNode json = putFor(args);

    assertEquals("/rest/api/2/issue/PROJ-3/comment/10100", path.getValue());
    // Markdown is converted to Jira markup before it is sent.
    assertEquals("*revised*", json.path("body").asText());
    assertEquals("{\"type\":\"group\",\"value\":\"jira-users\"}", json.path("visibility").asText());
  }

  @Test
  public void visibilityIsOmittedWhenAbsent() throws Exception {
    assertFalse(putFor(required()).has("visibility"));
  }

  @Test
  public void missingCommentIdIsRejected() {
    Map<String, Object> args = required();
    args.remove("comment_id");

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("comment_id"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "comment_id", "body", "visibility"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("issue_key", "comment_id", "body"),
        Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
