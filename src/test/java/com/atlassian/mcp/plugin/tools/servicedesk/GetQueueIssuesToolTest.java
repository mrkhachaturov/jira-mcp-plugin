package com.atlassian.mcp.plugin.tools.servicedesk;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    verify(client).get(url.capture(), eq("Bearer t"));
    return url.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url = urlFor(Map.of("service_desk_id", 4, "queue_id", 47, "start_at", 20, "limit", 5));

    assertEquals("/rest/servicedeskapi/servicedesk/4/queue/47/issue?start=20&limit=5", url);
  }

  @Test
  public void defaultsApplyWhenPaginationIsOmitted() throws Exception {
    String url = urlFor(Map.of("service_desk_id", 4, "queue_id", 47));

    assertEquals("/rest/servicedeskapi/servicedesk/4/queue/47/issue?start=0&limit=50", url);
  }

  @Test
  public void aQueueIdSentAsTextIsStillANumber() throws Exception {
    assertEquals(
        "/rest/servicedeskapi/servicedesk/4/queue/47/issue?start=0&limit=50",
        urlFor(Map.of("service_desk_id", 4, "queue_id", "47")));
  }

  @Test
  public void aQueueIdThatIsNotANumberIsRefused() {
    assertThrows(
        McpToolException.class,
        () -> tool.execute(Map.of("service_desk_id", 4, "queue_id", "urgent"), "Bearer t"));
    verifyNoInteractions(client);
  }

  /** The service desk API serves 50 rows a page however many are asked for. */
  @Test
  public void limitIsClampedToThePageSize() throws Exception {
    assertTrue(
        urlFor(Map.of("service_desk_id", 4, "queue_id", 47, "limit", 5000)).endsWith("limit=50"));
  }

  @Test
  public void limitIsClampedUpToOneRow() throws Exception {
    assertTrue(
        urlFor(Map.of("service_desk_id", 4, "queue_id", 47, "limit", 0)).endsWith("limit=1"));
  }

  @Test
  public void bothIdentifiersAreRequired() {
    Map<String, Object> full = Map.of("service_desk_id", 4, "queue_id", 47);
    for (String param : List.of("service_desk_id", "queue_id")) {
      Map<String, Object> args = new LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("service_desk_id", 4, "queue_id", 47, "max_results", 5), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'max_results'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("service_desk_id", "queue_id", "start_at", "limit"), props.keySet());
    assertEquals(List.of("service_desk_id", "queue_id"), schema.get("required"));
    assertEquals("integer", ((Map<String, Object>) props.get("service_desk_id")).get("type"));
    assertEquals("integer", ((Map<String, Object>) props.get("queue_id")).get("type"));
    assertEquals(0, ((Map<String, Object>) props.get("start_at")).get("default"));
    assertEquals(50, ((Map<String, Object>) props.get("limit")).get("default"));
  }
}
