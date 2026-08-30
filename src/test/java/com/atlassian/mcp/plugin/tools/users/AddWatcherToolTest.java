package com.atlassian.mcp.plugin.tools.users;

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

public class AddWatcherToolTest {

  private JiraRestClient client;
  private AddWatcherTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("");
    tool = new AddWatcherTool(client);
  }

  @Test
  public void bothParamsReachTheRequest() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123", "user_identifier", "jsmith"), "Bearer t");

    verify(client).post("/rest/api/2/issue/PROJ-123/watchers", "\"jsmith\"", "Bearer t");
  }

  @Test
  public void missingIssueKeyIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("user_identifier", "jsmith"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  public void missingUserIdentifierIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-1"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("user_identifier"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-1", "user_identifier", "jsmith", "notify", true),
                    "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("notify"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "user_identifier"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key", "user_identifier"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }

  @Test
  public void isMarkedWrite() {
    assertTrue(tool.isWriteTool());
  }
}
