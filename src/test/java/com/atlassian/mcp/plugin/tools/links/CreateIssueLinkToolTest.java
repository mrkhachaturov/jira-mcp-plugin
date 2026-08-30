package com.atlassian.mcp.plugin.tools.links;

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

public class CreateIssueLinkToolTest {

  private JiraRestClient client;
  private CreateIssueLinkTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new CreateIssueLinkTool(client);
  }

  private JsonNode bodyFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(eq("/rest/api/2/issueLink"), body.capture(), any());
    return new ObjectMapper().readTree(body.getValue());
  }

  private static Map<String, Object> required() {
    Map<String, Object> args = new HashMap<>();
    args.put("link_type", "Blocks");
    args.put("inward_issue_key", "PROJ-1");
    args.put("outward_issue_key", "PROJ-2");
    return args;
  }

  @Test
  public void everyDeclaredParamReachesTheRequestBody() throws Exception {
    Map<String, Object> args = required();
    args.put("comment", "linking these");
    args.put("comment_visibility", "{\"type\":\"group\",\"value\":\"jira-users\"}");

    JsonNode body = bodyFor(args);

    assertEquals("Blocks", body.path("type").path("name").asText());
    assertEquals("PROJ-1", body.path("inwardIssue").path("key").asText());
    assertEquals("PROJ-2", body.path("outwardIssue").path("key").asText());
    assertEquals("linking these", body.path("comment").path("body").asText());
    assertEquals("group", body.path("comment").path("visibility").path("type").asText());
    assertEquals("jira-users", body.path("comment").path("visibility").path("value").asText());
  }

  @Test
  public void optionalParamsAreOmittedWhenAbsent() throws Exception {
    JsonNode body = bodyFor(required());

    assertFalse(body.toString(), body.has("comment"));
    assertFalse(body.toString(), body.path("comment").has("visibility"));
  }

  @Test
  public void missingRequiredParamIsRejected() {
    Map<String, Object> args = required();
    args.remove("outward_issue_key");

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("outward_issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        Set.of(
            "link_type", "inward_issue_key", "outward_issue_key", "comment", "comment_visibility"),
        props.keySet());
    assertEquals(
        Set.of("link_type", "inward_issue_key", "outward_issue_key"),
        Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
