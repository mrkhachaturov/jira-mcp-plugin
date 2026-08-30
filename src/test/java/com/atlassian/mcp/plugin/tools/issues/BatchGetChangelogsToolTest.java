package com.atlassian.mcp.plugin.tools.issues;

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

public class BatchGetChangelogsToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Three histories, oldest first: status, then assignee, then status + summary. */
  private static final String ISSUE_WITH_HISTORY =
      "{\"key\":\"PROJ-1\",\"changelog\":{\"histories\":["
          + "{\"id\":\"1\",\"items\":[{\"field\":\"status\"}]},"
          + "{\"id\":\"2\",\"items\":[{\"field\":\"assignee\"}]},"
          + "{\"id\":\"3\",\"items\":[{\"field\":\"status\"},{\"field\":\"summary\"}]}]}}";

  private JiraRestClient client;
  private BatchGetChangelogsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ISSUE_WITH_HISTORY);
    tool = new BatchGetChangelogsTool(client);
  }

  private JsonNode historiesFor(Map<String, Object> args) throws Exception {
    String result = tool.execute(args, "Bearer t");
    return MAPPER
        .readTree(result)
        .path("changelogs")
        .path("PROJ-1")
        .path("changelog")
        .path("histories");
  }

  @Test
  public void everyKeyInTheListIsFetched() throws Exception {
    String result =
        tool.execute(Map.of("issue_ids_or_keys", List.of("PROJ-1", " PROJ-2 ")), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).get(url.capture(), eq("Bearer t"));

    List<String> urls = url.getAllValues();
    assertEquals("/rest/api/2/issue/PROJ-1?expand=changelog&fields=key,summary", urls.get(0));
    assertEquals("/rest/api/2/issue/PROJ-2?expand=changelog&fields=key,summary", urls.get(1));
    assertEquals(2, MAPPER.readTree(result).path("fetched").asInt());
  }

  @Test
  public void changedFieldsKeepsOnlyMatchingChangelogItems() throws Exception {
    JsonNode histories =
        historiesFor(
            Map.of("issue_ids_or_keys", List.of("PROJ-1"), "changed_fields", List.of("status")));

    assertEquals(2, histories.size());
    assertEquals("1", histories.get(0).path("id").asText());
    assertEquals("3", histories.get(1).path("id").asText());
    assertEquals(1, histories.get(1).path("items").size());
    assertEquals("status", histories.get(1).path("items").get(0).path("field").asText());
  }

  @Test
  public void changedFieldsAcceptsSeveralNames() throws Exception {
    JsonNode histories =
        historiesFor(
            Map.of(
                "issue_ids_or_keys",
                List.of("PROJ-1"),
                "changed_fields",
                List.of("assignee", "summary")));

    assertEquals(2, histories.size());
    assertEquals("2", histories.get(0).path("id").asText());
    assertEquals(1, histories.get(1).path("items").size());
    assertEquals("summary", histories.get(1).path("items").get(0).path("field").asText());
  }

  @Test
  public void limitKeepsTheMostRecentHistories() throws Exception {
    JsonNode histories = historiesFor(Map.of("issue_ids_or_keys", List.of("PROJ-1"), "limit", 1));

    assertEquals(1, histories.size());
    assertEquals("3", histories.get(0).path("id").asText());
  }

  @Test
  public void changedFieldsAndLimitCombine() throws Exception {
    JsonNode histories =
        historiesFor(
            Map.of(
                "issue_ids_or_keys",
                List.of("PROJ-1"),
                "changed_fields",
                List.of("status"),
                "limit",
                1));

    assertEquals(1, histories.size());
    assertEquals("3", histories.get(0).path("id").asText());
  }

  @Test
  public void defaultsReturnTheWholeChangelogUntouched() throws Exception {
    String result = tool.execute(Map.of("issue_ids_or_keys", List.of("PROJ-1")), "Bearer t");

    assertEquals(
        MAPPER.readTree(ISSUE_WITH_HISTORY),
        MAPPER.readTree(result).path("changelogs").path("PROJ-1"));
  }

  @Test
  public void aFailedIssueIsReportedWithoutLosingTheOthers() throws Exception {
    when(client.get(contains("PROJ-2"), any()))
        .thenThrow(new McpToolException("Jira API error (404): issue does not exist"));

    JsonNode result =
        MAPPER.readTree(
            tool.execute(Map.of("issue_ids_or_keys", List.of("PROJ-1", "PROJ-2")), "Bearer t"));

    assertEquals(1, result.path("fetched").asInt());
    assertEquals(1, result.path("errors").asInt());
    assertTrue(result.path("changelogs").has("PROJ-1"));
    assertTrue(
        result.toString(),
        result.path("failed").path("PROJ-2").path("error").asText().contains("404"));
  }

  @Test
  public void issueKeysAreRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("issue_ids_or_keys"));
  }

  @Test
  public void anEmptyKeyListIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_ids_or_keys", List.of()), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_ids_or_keys"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParametersAreRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_ids_or_keys", List.of("PROJ-1"), "fields", "status"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("fields"));
    verifyNoInteractions(client);
  }

  @Test
  public void progressIsReportedForEveryKey() throws Exception {
    List<String> messages = new java.util.ArrayList<>();
    tool.executeWithProgress(
        Map.of("issue_ids_or_keys", List.of("PROJ-1", "PROJ-2")),
        "Bearer t",
        (current, total, message) -> messages.add(message));

    assertEquals(3, messages.size());
    assertTrue(messages.get(2), messages.get(2).contains("2 fetched"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_ids_or_keys", "changed_fields", "limit"), props.keySet());
    assertEquals(List.of("issue_ids_or_keys"), schema.get("required"));
    assertEquals("array", ((Map<String, Object>) props.get("issue_ids_or_keys")).get("type"));
    assertEquals("array", ((Map<String, Object>) props.get("changed_fields")).get("type"));
    assertEquals(-1, ((Map<String, Object>) props.get("limit")).get("default"));
  }
}
