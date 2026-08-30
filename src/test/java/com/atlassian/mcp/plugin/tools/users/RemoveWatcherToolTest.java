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
import org.mockito.ArgumentCaptor;

public class RemoveWatcherToolTest {

  private JiraRestClient client;
  private RemoveWatcherTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.delete(anyString(), any())).thenReturn("");
    tool = new RemoveWatcherTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).delete(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url =
        urlFor(
            Map.of(
                "issue_key", "PROJ-123",
                "user_identifier", "jsmith"));

    assertEquals("/rest/api/2/issue/PROJ-123/watchers?username=jsmith", url);
  }

  @Test
  public void theWatcherNameIsUrlEncoded() throws Exception {
    String url = urlFor(Map.of("issue_key", "PROJ-123", "user_identifier", "a b&c"));

    assertEquals("/rest/api/2/issue/PROJ-123/watchers?username=a+b%26c", url);
  }

  @Test
  public void missingUserIdentifierIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-123"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("user_identifier"));
    verifyNoInteractions(client);
  }

  @Test
  public void missingIssueKeyIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("user_identifier", "j"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-1", "user_identifier", "j", "account_id", "5b1"),
                    "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("account_id"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "user_identifier"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("issue_key", "user_identifier"), Set.copyOf((List<String>) schema.get("required")));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }

  @Test
  public void isMarkedWriteAndDestructive() {
    assertTrue(tool.isWriteTool());
    assertTrue(tool.isDestructiveTool());
  }
}
