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

public class GetServiceDeskQueuesToolTest {

  private JiraRestClient client;
  private GetServiceDeskQueuesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"values\":[]}");
    tool = new GetServiceDeskQueuesTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url = urlFor(Map.of("service_desk_id", "4", "start_at", 10, "limit", 25));

    assertEquals("/rest/servicedeskapi/servicedesk/4/queue?start=10&limit=25", url);
  }

  @Test
  public void defaultsApplyWhenPaginationIsOmitted() throws Exception {
    assertEquals(
        "/rest/servicedeskapi/servicedesk/4/queue?start=0&limit=50",
        urlFor(Map.of("service_desk_id", "4")));
  }

  @Test
  public void limitIsClampedToThePageSize() throws Exception {
    assertTrue(urlFor(Map.of("service_desk_id", "4", "limit", 5000)).endsWith("limit=50"));
  }

  @Test
  public void serviceDeskIdIsRequired() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "t"));
    assertEquals("'service_desk_id' parameter is required", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("service_desk_id", "start_at", "limit"), props.keySet());
    assertEquals(List.of("service_desk_id"), schema.get("required"));
  }
}
