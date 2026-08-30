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
    args.put("public", true);

    JsonNode json = postFor(args);

    assertEquals("/rest/api/2/issue/PROJ-3/comment", path.getValue());
    // Markdown is converted to Jira markup before it is sent.
    assertEquals("*shipped*", json.path("body").asText());
    assertEquals("{\"type\":\"group\",\"value\":\"jira-users\"}", json.path("visibility").asText());
    assertTrue(json.path("public").asBoolean());
  }

  @Test
  public void publicDefaultsToFalseAndVisibilityIsOmittedWhenAbsent() throws Exception {
    JsonNode json = postFor(Map.of("issue_key", "PROJ-3", "body", "plain"));

    assertFalse(json.path("public").asBoolean());
    assertFalse(json.toString(), json.has("visibility"));
  }

  @Test
  public void publicAcceptsAStringFlag() throws Exception {
    assertTrue(
        postFor(Map.of("issue_key", "PROJ-3", "body", "plain", "public", "true"))
            .path("public")
            .asBoolean());
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

    assertEquals(Set.of("issue_key", "body", "visibility", "public"), props.keySet());
    assertEquals(
        Set.of("issue_key", "body"), Set.copyOf((java.util.List<String>) schema.get("required")));
    assertEquals(Boolean.FALSE, ((Map<String, Object>) props.get("public")).get("default"));
  }
}
