package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
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

  @Test
  public void defaultsApplyWhenNothingIsPassed() throws Exception {
    String url = urlFor(Map.of("project_key", "PROJ"));

    assertTrue(url, url.contains("maxResults=10"));
    assertTrue(url, url.contains("startAt=0"));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(urlFor(Map.of("project_key", "PROJ", "limit", 5000)).contains("maxResults=50"));
  }

  @Test(expected = McpToolException.class)
  public void projectKeyIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(Set.of("project_key", "limit", "start_at"), props.keySet());
  }
}
