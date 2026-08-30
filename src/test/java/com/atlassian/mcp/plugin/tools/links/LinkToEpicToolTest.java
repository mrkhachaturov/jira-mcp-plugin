package com.atlassian.mcp.plugin.tools.links;

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

public class LinkToEpicToolTest {

  private JiraRestClient client;
  private LinkToEpicTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("");
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-9\"}");
    tool = new LinkToEpicTool(client);
  }

  @Test
  public void bothDeclaredParamsReachTheRequest() throws Exception {
    String result = tool.execute(Map.of("issue_key", "PROJ-9", "epic_key", "PROJ-1"), "Bearer t");

    ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(path.capture(), body.capture(), any());

    assertEquals("/rest/agile/1.0/epic/PROJ-1/issue", path.getValue());
    JsonNode sent = new ObjectMapper().readTree(body.getValue());
    assertEquals(1, sent.path("issues").size());
    assertEquals("PROJ-9", sent.path("issues").get(0).asText());

    verify(client).get("/rest/api/2/issue/PROJ-9", "Bearer t");

    JsonNode json = new ObjectMapper().readTree(result);
    assertEquals("Issue PROJ-9 has been linked to epic PROJ-1.", json.path("message").asText());
    assertEquals("PROJ-9", json.path("issue").path("key").asText());
  }

  @Test
  public void missingEpicKeyIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-9"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("epic_key"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParamIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-9", "epic_key", "PROJ-1", "epic_link_field", "cf1"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("epic_link_field"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "epic_key"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("issue_key", "epic_key"),
        Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
