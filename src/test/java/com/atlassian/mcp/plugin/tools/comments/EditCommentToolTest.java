package com.atlassian.mcp.plugin.tools.comments;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
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
    args.put("comment_id", 10100L);
    args.put("body", "**revised**");
    return args;
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = required();
    args.put("visibility", Map.of("type", "role", "value", "Developers"));

    JsonNode json = putFor(args);

    assertEquals("/rest/api/2/issue/PROJ-3/comment/10100", path.getValue());
    // Markdown is converted to Jira markup before it is sent.
    assertEquals("*revised*", json.path("body").asText());
    assertEquals("role", json.path("visibility").path("type").asText());
    assertEquals("Developers", json.path("visibility").path("value").asText());
  }

  @Test
  public void visibilityIsOmittedWhenAbsent() throws Exception {
    assertFalse(putFor(required()).has("visibility"));
  }

  /** A caller that quotes the numeric id still reaches the same comment. */
  @Test
  public void aQuotedCommentIdIsAccepted() throws Exception {
    Map<String, Object> args = required();
    args.put("comment_id", "10100");

    putFor(args);

    assertEquals("/rest/api/2/issue/PROJ-3/comment/10100", path.getValue());
  }

  @Test
  public void aNonNumericCommentIdIsRefused() {
    Map<String, Object> args = required();
    args.put("comment_id", "not-a-number");

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("comment_id"));
    verifyNoInteractions(client);
  }

  @Test
  public void visibilityIsRestrictedTheSameWayAsOnAddComment() {
    Map<String, Object> args = required();
    args.put("visibility", Map.of("type", "user", "value", "bob"));

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("visibility.type"));
    verifyNoInteractions(client);
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    for (String missing : new String[] {"issue_key", "comment_id", "body"}) {
      Map<String, Object> args = required();
      args.remove(missing);

      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertTrue(e.getMessage(), e.getMessage().contains(missing));
    }
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParametersAreRefused() {
    Map<String, Object> args = required();
    args.put("notify_users", true);

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("notify_users"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "comment_id", "body", "visibility"), props.keySet());
    assertEquals(
        Set.of("issue_key", "comment_id", "body"),
        Set.copyOf((List<String>) schema.get("required")));
    assertEquals("integer", ((Map<String, Object>) props.get("comment_id")).get("type"));

    Map<String, Object> visibility = (Map<String, Object>) props.get("visibility");
    assertEquals("object", visibility.get("type"));
    assertEquals(
        Set.of("type", "value"), ((Map<String, Object>) visibility.get("properties")).keySet());
  }
}
