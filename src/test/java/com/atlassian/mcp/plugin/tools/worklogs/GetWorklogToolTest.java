package com.atlassian.mcp.plugin.tools.worklogs;

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

public class GetWorklogToolTest {

  private JiraRestClient client;
  private GetWorklogTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"worklogs\":[]}");
    tool = new GetWorklogTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    return url.getValue();
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    assertEquals("/rest/api/2/issue/PROJ-123/worklog", urlFor(Map.of("issue_key", "PROJ-123")));
  }

  @Test
  public void anotherIssueKeyChangesThePath() throws Exception {
    assertEquals("/rest/api/2/issue/ACV2-642/worklog", urlFor(Map.of("issue_key", "ACV2-642")));
  }

  @Test
  public void issueKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParametersAreRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "limit", 5), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("limit"));
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
