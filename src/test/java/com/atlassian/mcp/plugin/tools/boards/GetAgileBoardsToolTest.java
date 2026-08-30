package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetAgileBoardsToolTest {

  private JiraRestClient client;
  private GetAgileBoardsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"values\":[]}");
    tool = new GetAgileBoardsTool(client);
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
                "board_name", "Sprint Board",
                "project_key", "PROJ",
                "board_type", "scrum",
                "start_at", 5,
                "limit", 25));

    assertTrue(url, url.contains("name=Sprint+Board"));
    assertTrue(url, url.contains("projectKeyOrId=PROJ"));
    assertTrue(url, url.contains("type=scrum"));
    assertTrue(url, url.contains("startAt=5"));
    assertTrue(url, url.contains("maxResults=25"));
  }

  @Test
  public void defaultsApplyWhenNothingIsPassed() throws Exception {
    String url = urlFor(Map.of());

    assertTrue(url, url.contains("startAt=0"));
    assertTrue(url, url.contains("maxResults=10"));
    assertFalse(url, url.contains("type="));
    assertFalse(url, url.contains("name="));
  }

  @Test
  public void limitIsClampedToJiraPageSize() throws Exception {
    assertTrue(urlFor(Map.of("limit", 5000)).contains("maxResults=50"));
  }

  @Test
  public void boardTypeOutsideTheDeclaredEnumIsRejected() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("board_type", "waterfall"), "t"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("board_type"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParameterIsRejectedRatherThanIgnored() {
    McpToolException thrown =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("boardName", "Sprint"), "t"));

    assertTrue(thrown.getMessage(), thrown.getMessage().contains("boardName"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(
        java.util.Set.of("board_name", "project_key", "board_type", "start_at", "limit"),
        props.keySet());
  }
}
