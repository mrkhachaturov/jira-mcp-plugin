package com.atlassian.mcp.plugin.tools.servicedesk;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetServiceDeskForProjectToolTest {

  private static final String ONE_PAGE =
      "{\"isLastPage\":true,\"values\":["
          + "{\"id\":\"9\",\"projectId\":\"10000\",\"projectKey\":\"OPS\"},"
          + "{\"id\":\"4\",\"projectId\":\"10001\",\"projectKey\":\"SUP\"}]}";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetServiceDeskForProjectTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ONE_PAGE);
    tool = new GetServiceDeskForProjectTool(client);
  }

  private JsonNode deskFor(String projectKey) throws Exception {
    return MAPPER.readTree(tool.execute(Map.of("project_key", projectKey), "Bearer t"));
  }

  @Test
  public void projectKeySelectsOneServiceDeskOutOfTheList() throws Exception {
    assertEquals("4", deskFor("SUP").path("id").asText());
    assertEquals("9", deskFor("OPS").path("id").asText());
  }

  @Test
  public void theProjectKeyIsMatchedWithoutRegardToCase() throws Exception {
    assertEquals("4", deskFor("sup").path("id").asText());
  }

  @Test
  public void aProjectWithoutAServiceDeskIsReportedWithTheProjectsThatHaveOne() {
    McpToolException e = assertThrows(McpToolException.class, () -> deskFor("NOPE"));

    assertTrue(e.getMessage(), e.getMessage().contains("No service desk serves project 'NOPE'"));
    assertTrue(e.getMessage(), e.getMessage().contains("OPS"));
    assertTrue(e.getMessage(), e.getMessage().contains("SUP"));
  }

  @Test
  public void theListingIsPagedUntilTheMatchIsFound() throws Exception {
    when(client.get(contains("start=0"), any()))
        .thenReturn("{\"isLastPage\":false,\"values\":[{\"id\":\"9\",\"projectKey\":\"OPS\"}]}");
    when(client.get(contains("start=50"), any()))
        .thenReturn("{\"isLastPage\":true,\"values\":[{\"id\":\"4\",\"projectKey\":\"SUP\"}]}");

    assertEquals("4", deskFor("SUP").path("id").asText());

    ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).get(urls.capture(), eq("Bearer t"));
    assertEquals(
        List.of(
            "/rest/servicedeskapi/servicedesk?start=0&limit=50",
            "/rest/servicedeskapi/servicedesk?start=50&limit=50"),
        urls.getAllValues());
  }

  @Test
  public void projectKeyIsRequired() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "t"));
    assertEquals("'project_key' parameter is required", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("project_key", "SUP", "expand", "queues"), "t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'expand'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("project_key"), props.keySet());
    assertEquals(List.of("project_key"), schema.get("required"));
  }
}
