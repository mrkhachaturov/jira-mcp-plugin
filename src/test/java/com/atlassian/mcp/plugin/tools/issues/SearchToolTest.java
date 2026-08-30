package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class SearchToolTest {

  private JiraRestClient client;
  private SearchTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"issues\":[]}");
    tool = new SearchTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  private String jqlOf(String url) {
    String jql = url.substring(url.indexOf("jql=") + 4);
    int end = jql.indexOf('&');
    return URLDecoder.decode(end < 0 ? jql : jql.substring(0, end), StandardCharsets.UTF_8);
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url =
        urlFor(
            Map.of(
                "jql", "status = Open",
                "fields", "summary,status",
                "limit", 25,
                "start_at", 30,
                "projects_filter", "ALPHA, BETA",
                "expand", "changelog"));

    assertTrue(url, url.startsWith("/rest/api/2/search?jql="));
    assertEquals("(status = Open) AND project in (\"ALPHA\", \"BETA\")", jqlOf(url));
    assertTrue(url, url.contains("maxResults=25"));
    assertTrue(url, url.contains("startAt=30"));
    assertTrue(url, url.contains("fields=summary%2Cstatus"));
    assertTrue(url, url.contains("expand=changelog"));
  }

  @Test
  public void defaultsApplyWhenNothingElseIsPassed() throws Exception {
    String url = urlFor(Map.of("jql", "status = Open"));

    assertEquals("status = Open", jqlOf(url));
    assertTrue(url, url.contains("maxResults=10"));
    assertTrue(url, url.contains("startAt=0"));
    assertTrue(url, url.contains("fields=summary%2Cstatus%2Cassignee"));
    assertFalse(url, url.contains("expand="));
  }

  @Test
  public void projectsFilterKeepsTheOrderByClauseOutsideTheRestriction() throws Exception {
    String url =
        urlFor(Map.of("jql", "status = Open ORDER BY created DESC", "projects_filter", "ALPHA"));

    assertEquals("(status = Open) AND project in (\"ALPHA\") ORDER BY created DESC", jqlOf(url));
  }

  @Test(expected = McpToolException.class)
  public void jqlIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(
        Set.of("jql", "fields", "limit", "start_at", "projects_filter", "expand"), props.keySet());
  }
}
