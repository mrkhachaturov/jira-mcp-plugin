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

public class GetIssuesDevelopmentInfoToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<String> TWO_KEYS = List.of("PROJ-1", "PROJ-2");

  private JiraRestClient client;
  private GetIssuesDevelopmentInfoTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"detail\":[]}");
    when(client.get(contains("fields=id"), any())).thenReturn("{\"id\":\"10001\"}");
    tool = new GetIssuesDevelopmentInfoTool(client);
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
  public void everyKeyInTheListIsFetched() throws Exception {
    JsonNode results =
        MAPPER.readTree(
            tool.execute(Map.of("issue_keys", TWO_KEYS, "application_type", "github"), "Bearer t"));

    verify(client).get("/rest/api/2/issue/PROJ-1?fields=id", "Bearer t");
    verify(client).get("/rest/api/2/issue/PROJ-2?fields=id", "Bearer t");
    assertEquals(2, results.size());
    assertEquals("PROJ-1", results.get(0).path("issue_key").asText());
    assertEquals("PROJ-2", results.get(1).path("issue_key").asText());
  }

  @Test
  public void applicationTypeIsForwardedForEveryIssue() throws Exception {
    tool.execute(Map.of("issue_keys", TWO_KEYS, "application_type", "gitlab"), "B");

    List<String> urls = devStatusUrls();
    assertEquals(urls.toString(), 2, urls.size());
    for (String url : urls) {
      assertTrue(url, url.contains("applicationType=gitlab"));
    }
  }

  @Test
  public void dataTypeIsForwardedForEveryIssue() throws Exception {
    tool.execute(
        Map.of(
            "issue_keys", TWO_KEYS,
            "application_type", "gitlab",
            "data_type", "pullrequest"),
        "B");

    for (String url : devStatusUrls()) {
      assertTrue(url, url.contains("dataType=pullrequest"));
    }
  }

  @Test
  public void everyApplicationIsProbedPerIssueWhenNoFilterIsGiven() throws Exception {
    tool.execute(Map.of("issue_keys", TWO_KEYS), "B");

    assertEquals(devStatusUrls().toString(), 24, devStatusUrls().size());
  }

  @Test
  public void aFailingIssueBecomesAnErrorEntryRatherThanFailingTheBatch() throws Exception {
    when(client.get(eq("/rest/api/2/issue/PROJ-2?fields=id"), any())).thenReturn("{}");

    JsonNode results =
        MAPPER.readTree(
            tool.execute(Map.of("issue_keys", TWO_KEYS, "application_type", "github"), "B"));

    assertEquals(2, results.size());
    assertFalse(results.get(0).toString(), results.get(0).has("error"));
    assertEquals("PROJ-2", results.get(1).path("issue_key").asText());
    assertTrue(results.get(1).path("error").asText(), results.get(1).has("error"));
  }

  @Test
  public void progressIsReportedPerIssue() throws Exception {
    List<String> reports = new ArrayList<>();

    tool.executeWithProgress(
        Map.of("issue_keys", TWO_KEYS, "application_type", "github"),
        "B",
        (current, total, message) -> reports.add(current + "/" + total));

    assertEquals(List.of("0/2", "1/2", "2/2"), reports);
  }

  @Test
  public void theSameResultIsProducedWhenNoProgressIsRequested() throws Exception {
    String withoutProgress =
        tool.execute(Map.of("issue_keys", TWO_KEYS, "application_type", "github"), "B");

    assertEquals(2, MAPPER.readTree(withoutProgress).size());
  }

  @Test
  public void missingIssueKeysIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_keys"));
    verifyNoInteractions(client);
  }

  @Test
  public void anEmptyIssueKeysListIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_keys", new ArrayList<String>()), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_keys"));
    verifyNoInteractions(client);
  }

  @Test
  public void aDataTypeOutsideTheEnumIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_keys", TWO_KEYS, "data_type", "commit"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("data_type"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_keys", TWO_KEYS, "issue_key", "PROJ-1"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_key"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_keys", "application_type", "data_type"), props.keySet());
    assertEquals(List.of("issue_keys"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void issueKeysIsAdvertisedAsAnArrayOfStrings() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> issueKeys = (Map<String, Object>) props.get("issue_keys");

    assertEquals("array", issueKeys.get("type"));
    assertEquals("string", ((Map<String, Object>) issueKeys.get("items")).get("type"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void theFiltersMatchTheSingleIssueToolExactly() {
    Map<String, Object> batch = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> single =
        (Map<String, Object>)
            new GetIssueDevelopmentInfoTool(client).inputSchema().get("properties");

    assertEquals(single.get("application_type"), batch.get("application_type"));
    assertEquals(single.get("data_type"), batch.get("data_type"));
  }

  @Test
  public void supportsProgressIsAdvertised() {
    assertTrue(tool.supportsProgress());
  }
}
