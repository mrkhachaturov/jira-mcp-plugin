package com.atlassian.mcp.plugin.tools.links;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class RemoveIssueLinkToolTest {

  private JiraRestClient client;
  private RemoveIssueLinkTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.delete(anyString(), any())).thenReturn("{}");
    tool = new RemoveIssueLinkTool(client);
  }

  @Test
  public void linkIdReachesTheRequestPath() throws Exception {
    tool.execute(Map.of("link_id", "10042"), "Bearer t");

    verify(client).delete("/rest/api/2/issueLink/10042", "Bearer t");
  }

  @Test
  public void missingLinkIdIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("link_id"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("link_id"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(java.util.List.of("link_id"), (java.util.List<String>) schema.get("required"));
  }

  @Test
  public void isMarkedWriteAndDestructive() {
    assertTrue(tool.isWriteTool());
    assertTrue(tool.isDestructiveTool());
  }
}
