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

public class AddIssuesToSprintToolTest {

  private JiraRestClient client;
  private AddIssuesToSprintTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new AddIssuesToSprintTool(client);
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    tool.execute(Map.of("sprint_id", "10042", "issue_keys", "PROJ-1,PROJ-2"), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(url.capture(), body.capture(), any());

    assertEquals("/rest/agile/1.0/sprint/10042/issue", url.getValue());
    assertTrue(body.getValue(), body.getValue().contains("\"issues\":[\"PROJ-1\",\"PROJ-2\"]"));
  }

  @Test
  public void sprintIdIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_keys", "PROJ-1"), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("sprint_id"));
  }

  @Test
  public void issueKeysIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("sprint_id", "10042"), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("issue_keys"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("sprint_id", "issue_keys"), props.keySet());
    assertEquals(
        java.util.Set.of("sprint_id", "issue_keys"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
