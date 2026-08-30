package com.atlassian.mcp.plugin.tools.worklogs;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AddWorklogToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private AddWorklogTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"id\":\"100028\"}");
    tool = new AddWorklogTool(client);
  }

  private static Map<String, Object> validArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-123");
    args.put("time_spent", "1h 30m");
    return args;
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).post(url.capture(), anyString(), eq("Bearer t"));
    return url.getValue();
  }

  private JsonNode bodyFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(anyString(), body.capture(), eq("Bearer t"));
    return MAPPER.readTree(body.getValue());
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    assertEquals("/rest/api/2/issue/PROJ-123/worklog", urlFor(validArgs()));
  }

  @Test
  public void worklogFieldsReachTheRequestBody() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("comment", "Fixed the parser");
    args.put("started", "2023-08-01T12:00:00.000+0000");

    JsonNode body = bodyFor(args);

    assertEquals("1h 30m", body.path("timeSpent").asText());
    assertEquals("Fixed the parser", body.path("comment").asText());
    assertEquals("2023-08-01T12:00:00.000+0000", body.path("started").asText());
  }

  /** Jira answers "Unrecognized field ... not marked as ignorable" for anything else. */
  @Test
  public void bodyCarriesOnlyThePropertyNamesJiraKnows() throws Exception {
    JsonNode body = bodyFor(validArgs());

    assertFalse(body.toString(), body.has("time_spent"));
    assertEquals(Set.of("timeSpent"), fieldNames(body));
  }

  @Test
  public void remainingEstimateBecomesAnEstimateAdjustment() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("remaining_estimate", "2d 4h");

    String url = urlFor(args);

    assertTrue(url, url.contains("adjustEstimate=new"));
    assertTrue(url, url.contains("newEstimate=2d+4h"));
  }

  @Test
  public void noEstimateAdjustmentIsSentWhenRemainingEstimateIsAbsent() throws Exception {
    assertFalse(urlFor(validArgs()).contains("adjustEstimate"));
  }

  /**
   * Jira's worklog resource has no original-estimate field and rejects unknown body properties, so
   * this parameter must not reach the request until a verified endpoint carries it.
   */
  @Test
  public void originalEstimateIsNotSerialisedIntoTheWorklog() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("original_estimate", "5d");

    tool.execute(args, "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(url.capture(), body.capture(), any());

    assertFalse(body.getValue(), body.getValue().contains("5d"));
    assertFalse(url.getValue(), url.getValue().contains("5d"));
  }

  @Test
  public void optionalFieldsAreOmittedWhenAbsent() throws Exception {
    JsonNode body = bodyFor(validArgs());

    assertFalse(body.toString(), body.has("comment"));
    assertFalse(body.toString(), body.has("started"));
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    for (String missing : new String[] {"issue_key", "time_spent"}) {
      Map<String, Object> args = validArgs();
      args.remove(missing);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertTrue(e.getMessage(), e.getMessage().contains(missing));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of(
            "issue_key",
            "time_spent",
            "comment",
            "started",
            "original_estimate",
            "remaining_estimate"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("issue_key", "time_spent"), Set.copyOf((List<String>) schema.get("required")));
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new java.util.LinkedHashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
