package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetProjectIssuesToolTest {

  private JiraRestClient client;
  private GetProjectIssuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"issues\":[]}");
    tool = new GetProjectIssuesTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url =
        urlFor(
            Map.of(
                "project_key", "PROJ",
                "limit", 25,
                "start_at", 30));

    assertTrue(url, url.startsWith("/rest/api/2/search?jql="));
    assertTrue(url, url.contains("project%3DPROJ"));
    assertTrue(url, url.contains("maxResults=25"));
    assertTrue(url, url.contains("startAt=30"));
  }

  /** The JQL is percent-encoded exactly once, on its way into the query string. */
  @Test
  public void defaultsApplyAndTheJqlIsEncodedOnce() throws Exception {
    assertEquals(
        "/rest/api/2/search?jql=project%3DPROJ+ORDER+BY+created+DESC&maxResults=10&startAt=0",
        urlFor(Map.of("project_key", "PROJ")));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(urlFor(Map.of("project_key", "PROJ", "limit", 5000)).contains("maxResults=50"));
  }

  @Test
  public void projectKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("project_key"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParametersAreRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("project_key", "PROJ", "order_by", "created"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("order_by"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("project_key", "limit", "start_at"), props.keySet());
    assertEquals(List.of("project_key"), schema.get("required"));
    assertEquals(10, ((Map<String, Object>) props.get("limit")).get("default"));
    assertEquals(0, ((Map<String, Object>) props.get("start_at")).get("default"));
  }
}
