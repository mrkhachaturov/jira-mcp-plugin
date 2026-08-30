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

public class GetSprintsFromBoardToolTest {

  private JiraRestClient client;
  private GetSprintsFromBoardTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"values\":[]}");
    tool = new GetSprintsFromBoardTool(client);
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
    args.put("board_id", "1000");
    args.put("state", "active");
    args.put("start_at", 3);
    args.put("limit", 15);

    String url = urlFor(args);

    assertTrue(url, url.startsWith("/rest/agile/1.0/board/1000/sprint?"));
    assertTrue(url, url.contains("state=active"));
    assertTrue(url, url.contains("startAt=3"));
    assertTrue(url, url.contains("maxResults=15"));
  }

  @Test
  public void defaultsApplyAndStateIsOmittedWhenAbsent() throws Exception {
    String url = urlFor(Map.of("board_id", "1000"));

    assertTrue(url, url.contains("startAt=0"));
    assertTrue(url, url.contains("maxResults=10"));
    assertFalse(url, url.contains("state="));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(urlFor(Map.of("board_id", "1", "limit", 5000)).contains("maxResults=50"));
  }

  @Test
  public void boardIdIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("state", "active"), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("board_id"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("board_id", "state", "start_at", "limit"), props.keySet());
    assertEquals(
        java.util.Set.of("board_id"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
