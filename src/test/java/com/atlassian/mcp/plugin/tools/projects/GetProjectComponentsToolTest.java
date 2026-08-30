package com.atlassian.mcp.plugin.tools.projects;

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

public class GetProjectComponentsToolTest {

  private JiraRestClient client;
  private GetProjectComponentsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("[]");
    tool = new GetProjectComponentsTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    return url.getValue();
  }

  @Test
  public void projectKeyReachesTheRequestPath() throws Exception {
    assertEquals("/rest/api/2/project/PROJ/components", urlFor(Map.of("project_key", "PROJ")));
  }

  @Test
  public void anotherProjectKeyChangesThePath() throws Exception {
    assertEquals("/rest/api/2/project/ACV2/components", urlFor(Map.of("project_key", "ACV2")));
  }

  @Test
  public void projectKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("project_key"));
  }

  @Test
  public void blankProjectKeyIsRejected() {
    assertThrows(
        McpToolException.class, () -> tool.execute(Map.of("project_key", "   "), "Bearer t"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("project_key"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("project_key"), schema.get("required"));
  }
}
