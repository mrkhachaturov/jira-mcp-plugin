package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetSprintIssuesToolTest {

  private JiraRestClient client;
  private GetSprintIssuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"issues\":[]}");
    tool = new GetSprintIssuesTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("sprint_id", 10001);
    args.put("fields", "summary,status");
    args.put("start_at", 7);
    args.put("limit", 25);

    String url = urlFor(args);

    assertTrue(url, url.startsWith("/rest/agile/1.0/sprint/10001/issue?"));
    assertTrue(url, url.contains("fields=summary%2Cstatus"));
    assertTrue(url, url.contains("startAt=7"));
    assertTrue(url, url.contains("maxResults=25"));
  }

  @Test
  public void defaultsApplyWhenOptionalsAreAbsent() throws Exception {
    String url = urlFor(Map.of("sprint_id", 10001));

    assertTrue(url, url.contains("startAt=0"));
    assertTrue(url, url.contains("maxResults=10"));
    assertTrue(url, url.contains("fields=summary%2Cstatus%2Cassignee"));
    assertTrue(url, url.contains("%2Cparent"));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(urlFor(Map.of("sprint_id", 1, "limit", 5000)).contains("maxResults=50"));
  }

  @Test
  public void sprintIdIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("sprint_id"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParameterIsRejectedRatherThanIgnored() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("sprint_id", 10001, "expand", "changelog"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("expand"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("sprint_id", "fields", "start_at", "limit"), props.keySet());
    assertEquals(Set.of("sprint_id"), Set.copyOf((List<String>) schema.get("required")));
  }
}
