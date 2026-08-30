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

public class GetIssueWatchersToolTest {

  private JiraRestClient client;
  private GetIssueWatchersTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"watchCount\":0}");
    tool = new GetIssueWatchersTool(client);
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    verify(client).get("/rest/api/2/issue/PROJ-123/watchers", "Bearer t");
  }

  @Test
  public void missingIssueKeyIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("issue_key"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
  }
}
