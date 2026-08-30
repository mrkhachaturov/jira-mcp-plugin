package com.atlassian.mcp.plugin.tools.metrics;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetIssueSlaToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String ISSUE =
      """
      {
        "fields": {
          "created": "2024-01-01T00:00:00.000+0000",
          "updated": "2024-01-03T00:00:00.000+0000",
          "duedate": "2024-01-10",
          "resolutiondate": null,
          "status": {"name": "Done"}
        },
        "changelog": {"histories": [
          {"created": "2024-01-01T02:00:00.000+0000",
            "items": [{"field": "status", "fromString": "To Do", "toString": "In Progress"}]},
          {"created": "2024-01-02T02:00:00.000+0000",
            "items": [{"field": "status", "fromString": "In Progress", "toString": "Done"}]}
        ]}
      }
      """;

  private JiraRestClient client;
  private GetIssueSlaTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ISSUE);
    tool = new GetIssueSlaTool(client);
  }

  private JsonNode run(Map<String, Object> args) throws Exception {
    return MAPPER.readTree(tool.execute(args, "Bearer t"));
  }

  private static Set<String> keysOf(JsonNode node) {
    Set<String> names = new LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  @Test
  public void issueKeyReachesTheRequestAndTheResult() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123"));

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    assertTrue(url.getValue(), url.getValue().startsWith("/rest/api/2/issue/PROJ-123?"));
    assertTrue(url.getValue(), url.getValue().contains("expand=changelog"));
    assertEquals("PROJ-123", result.path("issue_key").asText());
    assertEquals("Done", result.path("current_status").asText());
  }

  @Test
  public void defaultMetricsAreCycleTimeAndTimeInStatus() throws Exception {
    JsonNode metrics = run(Map.of("issue_key", "PROJ-123")).path("metrics");

    assertEquals(Set.of("cycle_time", "time_in_status"), keysOf(metrics));
  }

  @Test
  public void metricsParamSelectsWhatIsComputed() throws Exception {
    JsonNode metrics =
        run(Map.of("issue_key", "PROJ-123", "metrics", "first_response_time,resolution_time"))
            .path("metrics");

    assertEquals(Set.of("first_response_time", "resolution_time"), keysOf(metrics));
    // Created 00:00, first status change 02:00.
    assertEquals(120, metrics.path("first_response_time").path("duration_minutes").asLong());
    assertEquals("unresolved", metrics.path("resolution_time").path("status").asText());
  }

  @Test
  public void unknownMetricNamesAreDroppedRatherThanComputed() throws Exception {
    JsonNode metrics = run(Map.of("issue_key", "PROJ-123", "metrics", "bogus")).path("metrics");

    assertEquals(new ArrayList<>(), new ArrayList<>(keysOf(metrics)));
  }

  @Test
  public void includeRawDatesAddsTheSourceValues() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123", "include_raw_dates", true));

    assertEquals("2024-01-01T00:00:00.000+0000", result.path("raw_dates").path("created").asText());
    assertEquals("2024-01-10", result.path("raw_dates").path("due_date").asText());
  }

  @Test
  public void rawDatesAreOmittedByDefault() throws Exception {
    assertFalse(run(Map.of("issue_key", "PROJ-123")).has("raw_dates"));
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
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "metrics", "include_raw_dates"), props.keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
    assertEquals(
        Boolean.FALSE, ((Map<String, Object>) props.get("include_raw_dates")).get("default"));
  }
}
