package com.atlassian.mcp.plugin.tools.servicedesk;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetServiceDeskForProjectToolTest {

  private JiraRestClient client;
  private GetServiceDeskForProjectTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"values\":[]}");
    tool = new GetServiceDeskForProjectTool(client);
  }

  @Test
  public void projectKeyGatesTheRequest() throws Exception {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "t"));
    assertEquals("'project_key' parameter is required", e.getMessage());
    verifyNoInteractions(client);

    tool.execute(Map.of("project_key", "SUP"), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    assertEquals("/rest/servicedeskapi/servicedesk", url.getValue());
  }

  /**
   * The full service desk list comes back whatever the key is: the ServiceDesk API is described in
   * a spec this build cannot check, so no narrowing is attempted here.
   */
  @Test
  public void theSameListComesBackForAnyProjectKey() throws Exception {
    String forSup = tool.execute(Map.of("project_key", "SUP"), "Bearer t");
    String forOps = tool.execute(Map.of("project_key", "OPS"), "Bearer t");

    assertEquals(forSup, forOps);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("project_key"), props.keySet());
    assertEquals(List.of("project_key"), schema.get("required"));
  }
}
