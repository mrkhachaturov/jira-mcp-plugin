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

public class GetQueueIssuesToolTest {

  private JiraRestClient client;
  private GetQueueIssuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"values\":[]}");
    tool = new GetQueueIssuesTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url =
        urlFor(Map.of("service_desk_id", "4", "queue_id", "47", "start_at", 20, "limit", 5));

    assertEquals("/rest/servicedeskapi/servicedesk/4/queue/47/issue?start=20&limit=5", url);
  }

  @Test
  public void defaultsApplyWhenPaginationIsOmitted() throws Exception {
    String url = urlFor(Map.of("service_desk_id", "4", "queue_id", "47"));

    assertEquals("/rest/servicedeskapi/servicedesk/4/queue/47/issue?start=0&limit=50", url);
  }

  @Test
  public void bothIdentifiersAreRequired() {
    Map<String, Object> full = Map.of("service_desk_id", "4", "queue_id", "47");
    for (String param : List.of("service_desk_id", "queue_id")) {
      Map<String, Object> args = new java.util.LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        java.util.Set.of("service_desk_id", "queue_id", "start_at", "limit"), props.keySet());
    assertEquals(List.of("service_desk_id", "queue_id"), schema.get("required"));
  }
}
