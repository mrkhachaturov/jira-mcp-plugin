package com.atlassian.mcp.plugin.tools.forms;

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

public class GetIssueProformaFormsToolTest {

  private JiraRestClient client;
  private GetIssueProformaFormsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"proforma.forms\"}");
    tool = new GetIssueProformaFormsTool(client);
  }

  @Test
  public void issueKeyReachesTheRequest() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    assertEquals("/rest/api/2/issue/PROJ-123/properties/proforma.forms", url.getValue());
  }

  @Test
  public void issueKeyIsRequired() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "t"));
    assertEquals("'issue_key' parameter is required", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "form_id", "x"), "t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'form_id'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key"), props.keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
    assertEquals("string", ((Map<String, Object>) props.get("issue_key")).get("type"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }
}
