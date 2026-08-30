package com.atlassian.mcp.plugin.tools.links;

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
    tool.execute(Map.of("link_id", 10042), "Bearer t");

    verify(client).delete("/rest/api/2/issueLink/10042", "Bearer t");
  }

  @Test
  public void linkIdAcceptsTheStringForm() throws Exception {
    tool.execute(Map.of("link_id", "10042"), "Bearer t");

    verify(client).delete("/rest/api/2/issueLink/10042", "Bearer t");
  }

  @Test
  public void aLinkIdThatIsNotANumberIsRejected() {
    assertThrows(McpToolException.class, () -> tool.execute(Map.of("link_id", "abc"), "Bearer t"));
    verifyNoInteractions(client);
  }

  @Test
  public void missingLinkIdIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("link_id"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParamIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("link_id", 10042, "issue_key", "PROJ-1"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("link_id"), props.keySet());
    assertEquals(List.of("link_id"), schema.get("required"));
    assertEquals("integer", ((Map<String, Object>) props.get("link_id")).get("type"));
  }

  @Test
  public void isMarkedWriteAndDestructive() {
    assertTrue(tool.isWriteTool());
    assertTrue(tool.isDestructiveTool());
  }
}
