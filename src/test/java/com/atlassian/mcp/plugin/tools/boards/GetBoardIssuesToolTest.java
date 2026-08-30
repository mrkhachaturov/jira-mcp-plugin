package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetBoardIssuesToolTest {

  private JiraRestClient client;
  private GetBoardIssuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"issues\":[]}");
    tool = new GetBoardIssuesTool(client);
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
    args.put("board_id", "1001");
    args.put("jql", "status = Done");
    args.put("fields", "summary,status");
    args.put("start_at", 20);
    args.put("limit", 30);
    args.put("expand", "changelog");

    String url = urlFor(args);

    assertTrue(url, url.startsWith("/rest/agile/1.0/board/1001/issue?"));
    assertTrue(url, url.contains("jql=status+%3D+Done"));
    assertTrue(url, url.contains("fields=summary%2Cstatus"));
    assertTrue(url, url.contains("startAt=20"));
    assertTrue(url, url.contains("maxResults=30"));
    assertTrue(url, url.contains("expand=changelog"));
  }

  @Test
  public void defaultsApplyWhenOptionalsAreAbsent() throws Exception {
    String url = urlFor(Map.of("board_id", "1001", "jql", "project = PROJ"));

    assertTrue(url, url.contains("startAt=0"));
    assertTrue(url, url.contains("maxResults=10"));
    assertTrue(url, url.contains("expand=version"));
    assertTrue(url, url.contains("fields=summary%2Cstatus%2Cassignee"));
    assertTrue(url, url.contains("%2Cparent"));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(
        urlFor(Map.of("board_id", "1", "jql", "x", "limit", 5000)).contains("maxResults=50"));
  }

  @Test
  public void boardIdAndJqlAreRequired() {
    McpToolException noBoard =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of("jql", "x"), "Bearer t"));
    assertTrue(noBoard.getMessage(), noBoard.getMessage().contains("board_id"));

    McpToolException noJql =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("board_id", "1"), "Bearer t"));
    assertTrue(noJql.getMessage(), noJql.getMessage().contains("jql"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        java.util.Set.of("board_id", "jql", "fields", "start_at", "limit", "expand"),
        props.keySet());
    assertEquals(
        java.util.Set.of("board_id", "jql"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
