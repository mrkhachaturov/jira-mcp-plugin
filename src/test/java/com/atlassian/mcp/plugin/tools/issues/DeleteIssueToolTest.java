package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
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

  @Test(expected = McpToolException.class)
  public void issueKeyIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(Set.of("issue_key"), props.keySet());
    assertEquals(java.util.List.of("issue_key"), tool.inputSchema().get("required"));
  }
}
