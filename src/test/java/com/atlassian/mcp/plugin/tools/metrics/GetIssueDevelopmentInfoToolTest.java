package com.atlassian.mcp.plugin.tools.metrics;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetIssueDevelopmentInfoToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetIssueDevelopmentInfoTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"detail\":[]}");
    when(client.get(contains("fields=id"), any())).thenReturn("{\"id\":\"10001\"}");
    tool = new GetIssueDevelopmentInfoTool(client);
  }

  private List<String> devStatusUrls() throws Exception {
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client, atLeastOnce()).get(url.capture(), any());
    List<String> devStatus = new ArrayList<>();
    for (String value : url.getAllValues()) {
      if (value.startsWith("/rest/dev-status/")) devStatus.add(value);
    }
    return devStatus;
  }

  @Test
  public void issueKeyIsResolvedToTheNumericIdTheDevStatusApiNeeds() throws Exception {
    String result =
        tool.execute(Map.of("issue_key", "PROJ-123", "application_type", "github"), "B");

    verify(client).get("/rest/api/2/issue/PROJ-123?fields=id", "B");
    assertEquals("PROJ-123", MAPPER.readTree(result).path("issue_key").asText());
    assertTrue(devStatusUrls().get(0), devStatusUrls().get(0).contains("issueId=10001"));
  }

  @Test
  public void applicationTypeNarrowsTheProbeToOneApplication() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-1", "application_type", "github"), "B");

    List<String> urls = devStatusUrls();
    assertEquals(urls.toString(), 1, urls.size());
    assertTrue(urls.get(0), urls.get(0).contains("applicationType=github"));
    assertFalse(urls.get(0), urls.get(0).contains("dataType="));
  }

  @Test
  public void dataTypeReachesTheQuery() throws Exception {
    tool.execute(
        Map.of("issue_key", "PROJ-1", "application_type", "github", "data_type", "pullrequest"),
        "B");

    assertTrue(devStatusUrls().get(0), devStatusUrls().get(0).contains("dataType=pullrequest"));
  }

  @Test
  public void everyApplicationAndDataTypeIsProbedWhenNeitherFilterIsGiven() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-1"), "B");

    assertEquals(devStatusUrls().toString(), 12, devStatusUrls().size());
  }

  @Test
  public void dataTypeAloneNarrowsTheProbeAcrossApplications() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-1", "data_type", "branch"), "B");

    List<String> urls = devStatusUrls();
    assertEquals(urls.toString(), 4, urls.size());
    for (String url : urls) {
      assertTrue(url, url.contains("dataType=branch"));
    }
  }

  @Test
  public void unresolvableIssueIdIsReported() throws Exception {
    when(client.get(contains("fields=id"), any())).thenReturn("{}");

    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-1"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("PROJ-1"));
  }

  @Test
  public void missingIssueKeyIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("issue_key", "application_type", "data_type"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
  }

  @Test
  public void mergedResultCarriesTheDetailFromEveryApplication() throws Exception {
    when(client.get(contains("applicationType=github"), any()))
        .thenReturn("{\"detail\":[{\"pullRequests\":[{\"id\":\"7\"}]}]}");

    JsonNode result = MAPPER.readTree(tool.execute(Map.of("issue_key", "PROJ-1"), "B"));

    assertEquals(3, result.path("detail").size());
  }
}
