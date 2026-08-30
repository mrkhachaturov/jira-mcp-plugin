package com.atlassian.mcp.plugin.tools.metrics;

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

public class GetIssueDatesToolTest {

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
           "author": {"displayName": "Ann"},
           "items": [{"field": "status", "fromString": "To Do", "toString": "In Progress"}]},
          {"created": "2024-01-02T02:00:00.000+0000",
           "author": {"displayName": "Bob"},
           "items": [{"field": "status", "fromString": "In Progress", "toString": "Done"}]}
        ]}
      }
      """;

  private JiraRestClient client;
  private GetIssueDatesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ISSUE);
    tool = new GetIssueDatesTool(client);
  }

  private JsonNode run(Map<String, Object> args) throws Exception {
    return MAPPER.readTree(tool.execute(args, "Bearer t"));
  }

  private String lastUrl() throws Exception {
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  @Test
  public void issueKeyReachesTheRequestAndTheResult() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123"));

    assertTrue(lastUrl(), lastUrl().startsWith("/rest/api/2/issue/PROJ-123?"));
    assertEquals("PROJ-123", result.path("issue_key").asText());
    assertEquals("Done", result.path("current_status").asText());
    assertEquals("2024-01-10", result.path("due_date").asText());
  }

  @Test
  public void bothHistorySectionsAreIncludedByDefault() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123"));

    assertTrue(lastUrl(), lastUrl().contains("expand=changelog"));
    assertTrue(result.toString(), result.has("status_changes"));
    assertTrue(result.toString(), result.has("status_summary"));
    assertEquals(3, result.path("status_changes").size());
    assertEquals("In Progress", result.path("status_summary").get(0).path("status").asText());
    assertEquals(
        1440, result.path("status_summary").get(0).path("total_duration_minutes").asLong());
  }

  @Test
  public void includeStatusChangesFalseDropsTheTransitionList() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123", "include_status_changes", false));

    assertFalse(result.toString(), result.has("status_changes"));
    assertTrue(result.toString(), result.has("status_summary"));
  }

  @Test
  public void includeStatusSummaryFalseDropsTheAggregate() throws Exception {
    JsonNode result = run(Map.of("issue_key", "PROJ-123", "include_status_summary", false));

    assertTrue(result.toString(), result.has("status_changes"));
    assertFalse(result.toString(), result.has("status_summary"));
  }

  @Test
  public void changelogIsNotExpandedWhenNeitherSectionIsWanted() throws Exception {
    JsonNode result =
        run(
            Map.of(
                "issue_key", "PROJ-123",
                "include_status_changes", false,
                "include_status_summary", false));

    assertFalse(lastUrl(), lastUrl().contains("expand=changelog"));
    assertFalse(result.toString(), result.has("status_changes"));
    assertFalse(result.toString(), result.has("status_summary"));
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

    assertEquals(
        Set.of("issue_key", "include_status_changes", "include_status_summary"), props.keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
    assertEquals(
        Boolean.TRUE, ((Map<String, Object>) props.get("include_status_changes")).get("default"));
    assertEquals(
        Boolean.TRUE, ((Map<String, Object>) props.get("include_status_summary")).get("default"));
  }
}
