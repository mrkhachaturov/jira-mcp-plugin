package com.atlassian.mcp.plugin.tools.transitions;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetTransitionsToolTest {

  private JiraRestClient client;
  private GetTransitionsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"transitions\":[]}");
    tool = new GetTransitionsTool(client);
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());

    assertEquals("/rest/api/2/issue/PROJ-123/transitions", url.getValue());
  }

  @Test
  public void responseIsTrimmedToIdNameAndTargetStatus() throws Exception {
    when(client.get(anyString(), any()))
        .thenReturn(
            "{\"expand\":\"transitions\",\"transitions\":[{\"id\":\"11\",\"name\":\"Start\","
                + "\"hasScreen\":false,\"to\":{\"name\":\"In Progress\",\"id\":\"3\","
                + "\"description\":\"noise\"}}]}");

    String result = tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    assertEquals("[{\"id\":\"11\",\"name\":\"Start\",\"to_status\":\"In Progress\"}]", result);
  }

  @Test
  public void issueKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("issue_key"), props.keySet());
    assertEquals(
        java.util.Set.of("issue_key"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
