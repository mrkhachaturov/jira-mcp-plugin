package com.atlassian.mcp.plugin.tools.attachments;

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

public class DownloadAttachmentsToolTest {

  private JiraRestClient client;
  private DownloadAttachmentsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"fields\":{\"attachment\":[]}}");
    tool = new DownloadAttachmentsTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    return url.getValue();
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    assertEquals(
        "/rest/api/2/issue/PROJ-123?fields=attachment", urlFor(Map.of("issue_key", "PROJ-123")));
  }

  @Test
  public void anotherIssueKeyChangesThePath() throws Exception {
    assertEquals(
        "/rest/api/2/issue/ACV2-642?fields=attachment", urlFor(Map.of("issue_key", "ACV2-642")));
  }

  @Test
  public void issueKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertEquals("'issue_key' parameter is required", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  public void blankIssueKeyIsRejected() {
    assertThrows(McpToolException.class, () -> tool.execute(Map.of("issue_key", "  "), "Bearer t"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "target_dir", "/tmp"), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'target_dir'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("issue_key"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }
}
