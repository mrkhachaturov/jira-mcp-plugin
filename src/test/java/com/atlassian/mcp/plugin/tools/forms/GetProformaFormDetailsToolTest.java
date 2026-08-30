package com.atlassian.mcp.plugin.tools.forms;

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

public class GetProformaFormDetailsToolTest {

  private static final String FORM_UUID = "1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a";

  private JiraRestClient client;
  private GetProformaFormDetailsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"proforma.forms\"}");
    tool = new GetProformaFormDetailsTool(client);
  }

  @Test
  public void issueKeyReachesTheRequest() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    assertEquals("/rest/api/2/issue/PROJ-123/properties/proforma.forms", url.getValue());
  }

  @Test
  public void bothParamsAreRequired() {
    Map<String, Object> full = Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID);
    for (String param : List.of("issue_key", "form_id")) {
      Map<String, Object> args = new java.util.LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
    verifyNoInteractions(client);
  }

  /**
   * The whole proforma.forms issue property comes back whatever the form id is: the Proforma API is
   * described in a spec this build cannot check, so no selection is attempted here.
   */
  @Test
  public void theSamePropertyComesBackForAnyFormId() throws Exception {
    String first = tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "Bearer t");
    String second = tool.execute(Map.of("issue_key", "PROJ-123", "form_id", "other"), "Bearer t");

    assertEquals(first, second);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("issue_key", "form_id"), props.keySet());
    assertEquals(List.of("issue_key", "form_id"), schema.get("required"));
  }
}
