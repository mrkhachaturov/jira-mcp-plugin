package com.atlassian.mcp.plugin.tools.fields;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class SearchFieldsToolTest {

  private static final String FIELDS =
      "[{\"id\":\"summary\",\"key\":\"summary\",\"name\":\"Summary\",\"clauseNames\":[\"summary\"]},"
          + "{\"id\":\"customfield_1\",\"key\":\"customfield_1\",\"name\":\"Story Points\","
          + "\"clauseNames\":[\"cf[1]\"]},"
          + "{\"id\":\"customfield_2\",\"key\":\"customfield_2\",\"name\":\"Story Type\","
          + "\"clauseNames\":[\"cf[2]\"]},"
          + "{\"id\":\"description\",\"key\":\"description\",\"name\":\"Description\","
          + "\"clauseNames\":[\"description\"]}]";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private SearchFieldsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(FIELDS);
    tool = new SearchFieldsTool(client);
  }

  private JsonNode result(Map<String, Object> args) throws Exception {
    return MAPPER.readTree(tool.execute(args, "Bearer t"));
  }

  private static List<String> names(JsonNode result) {
    List<String> names = new java.util.ArrayList<>();
    for (JsonNode field : result) names.add(field.path("name").asText());
    return names;
  }

  @Test
  public void keywordNarrowsAndRanksTheFields() throws Exception {
    assertEquals(List.of("Story Points", "Story Type"), names(result(Map.of("keyword", "story"))));
    assertEquals(List.of("Summary"), names(result(Map.of("keyword", "summary"))));
  }

  @Test
  public void limitCapsTheKeywordMatches() throws Exception {
    assertEquals(1, result(Map.of("keyword", "story", "limit", 1)).size());
  }

  @Test
  public void limitCapsTheUnfilteredListToo() throws Exception {
    assertEquals(List.of("Summary", "Story Points"), names(result(Map.of("limit", 2))));
  }

  @Test
  public void defaultsListEveryFieldUpToTen() throws Exception {
    assertEquals(4, result(Map.of()).size());
  }

  /**
   * The field list is fetched from Jira on every call, so 'refresh' has nothing cached to
   * invalidate — it is advertised but cannot change the result.
   */
  @Test
  public void refreshDoesNotChangeTheResult() throws Exception {
    assertEquals(
        result(Map.of("keyword", "story", "refresh", false)),
        result(Map.of("keyword", "story", "refresh", true)));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(java.util.Set.of("keyword", "limit", "refresh"), props.keySet());
    assertEquals(List.of(), schema.get("required"));
    assertEquals(10, ((Map<String, Object>) props.get("limit")).get("default"));
  }
}
