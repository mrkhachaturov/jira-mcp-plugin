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
                "username", "jsmith",
                "account_id", "557058:abc"));

    assertTrue(url, url.startsWith("/rest/api/2/issue/PROJ-123/watchers?"));
    assertTrue(url, url.contains("username=jsmith"));
    assertTrue(url, url.contains("accountId=557058%3Aabc"));
  }

  @Test
  public void optionalParamsAreOmittedWhenAbsent() throws Exception {
    assertEquals("/rest/api/2/issue/PROJ-123/watchers", urlFor(Map.of("issue_key", "PROJ-123")));
  }

  @Test
  public void accountIdAloneOpensTheQuery() throws Exception {
    String url = urlFor(Map.of("issue_key", "PROJ-1", "account_id", "557058:abc"));

    assertEquals("/rest/api/2/issue/PROJ-1/watchers?accountId=557058%3Aabc", url);
  }

  @Test
  public void missingIssueKeyIsRejected() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of("username", "j"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "username", "account_id"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
  }

  @Test
  public void isMarkedWriteAndDestructive() {
    assertTrue(tool.isWriteTool());
    assertTrue(tool.isDestructiveTool());
  }
}
