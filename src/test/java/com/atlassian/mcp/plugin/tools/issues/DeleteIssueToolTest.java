package com.atlassian.mcp.plugin.tools.issues;

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

public class DeleteIssueToolTest {

  private JiraRestClient client;
  private DeleteIssueTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.delete(anyString(), any())).thenReturn("");
    tool = new DeleteIssueTool(client);
  }

  @Test
  public void issueKeyReachesTheDeletePath() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    verify(client).delete(path.capture(), eq("Bearer t"));
    assertEquals("/rest/api/2/issue/PROJ-123", path.getValue());
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
            () ->
                tool.execute(Map.of("issue_key", "PROJ-123", "delete_subtasks", true), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("delete_subtasks"));
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
