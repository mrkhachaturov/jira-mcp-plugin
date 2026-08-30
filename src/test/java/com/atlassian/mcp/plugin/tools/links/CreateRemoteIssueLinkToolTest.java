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

public class CreateRemoteIssueLinkToolTest {

  private JiraRestClient client;
  private CreateRemoteIssueLinkTool tool;
  private ArgumentCaptor<String> path;
  private ArgumentCaptor<String> body;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new CreateRemoteIssueLinkTool(client);
    path = ArgumentCaptor.forClass(String.class);
    body = ArgumentCaptor.forClass(String.class);
  }

  private JsonNode postFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    verify(client).post(path.capture(), body.capture(), any());
    return new ObjectMapper().readTree(body.getValue());
  }

  private static Map<String, Object> required() {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-7");
    args.put("url", "https://example.com/page");
    args.put("title", "Docs");
    return args;
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = required();
    args.put("summary", "the design doc");
    args.put("relationship", "documentation");
    args.put("icon_url", "https://example.com/icon.png");

    JsonNode json = postFor(args);

    assertEquals("/rest/api/2/issue/PROJ-7/remotelink", path.getValue());
    assertEquals("https://example.com/page", json.path("object").path("url").asText());
    assertEquals("Docs", json.path("object").path("title").asText());
    assertEquals("the design doc", json.path("object").path("summary").asText());
    assertEquals("documentation", json.path("relationship").asText());
    assertEquals(
        "https://example.com/icon.png", json.path("object").path("icon").path("url16x16").asText());
  }

  @Test
  public void optionalParamsAreOmittedWhenAbsent() throws Exception {
    JsonNode json = postFor(required());

    assertFalse(json.toString(), json.path("object").has("summary"));
    assertFalse(json.toString(), json.has("relationship"));
    assertFalse(json.toString(), json.has("icon_url"));
  }

  @Test
  public void missingRequiredParamIsRejected() {
    Map<String, Object> args = required();
    args.remove("title");

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("title"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "url", "title", "summary", "relationship", "icon_url"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("issue_key", "url", "title"),
        Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
