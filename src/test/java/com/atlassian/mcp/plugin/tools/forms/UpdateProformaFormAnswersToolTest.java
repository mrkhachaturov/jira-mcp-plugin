package com.atlassian.mcp.plugin.tools.forms;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UpdateProformaFormAnswersToolTest {

  private static final String FORM_UUID = "1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a";
  private static final List<Map<String, Object>> ANSWERS =
      List.of(Map.of("questionId", "q1", "type", "TEXT", "value", "Updated"));

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private UpdateProformaFormAnswersTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");
    tool = new UpdateProformaFormAnswersTool(client);
  }

  private JsonNode sentFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client)
        .put(
            eq("/rest/api/2/issue/PROJ-123/properties/proforma.forms"),
            body.capture(),
            eq("Bearer t"));
    return MAPPER.readTree(body.getValue());
  }

  private static Map<String, Object> args(Object answers) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("issue_key", "PROJ-123");
    args.put("form_id", FORM_UUID);
    args.put("answers", answers);
    return args;
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    JsonNode sent = sentFor(args(ANSWERS));

    assertEquals(FORM_UUID, sent.path("form_id").asText());
    assertEquals(1, sent.path("answers").size());

    JsonNode answer = sent.path("answers").get(0);
    assertEquals("q1", answer.path("questionId").asText());
    assertEquals("TEXT", answer.path("type").asText());
    assertEquals("Updated", answer.path("value").asText());
  }

  /** An answer is a JSON object, not a string holding JSON. */
  @Test
  public void answersTravelAsObjectsRatherThanAsAString() throws Exception {
    JsonNode answers = sentFor(args(ANSWERS)).path("answers");

    assertTrue(answers.toString(), answers.isArray());
    assertTrue(answers.get(0).toString(), answers.get(0).isObject());
  }

  @Test
  public void aNonStringAnswerValueKeepsItsJsonType() throws Exception {
    JsonNode sent =
        sentFor(
            args(
                List.of(
                    Map.of("questionId", "q3", "type", "NUMBER", "value", 42),
                    Map.of(
                        "questionId", "q4", "type", "MULTI_SELECT", "value", List.of("A", "B")))));

    assertEquals(42, sent.path("answers").get(0).path("value").asInt());
    assertTrue(sent.path("answers").get(1).path("value").isArray());
    assertEquals(2, sent.path("answers").get(1).path("value").size());
  }

  @Test
  public void anIso8601DateAnswerIsConvertedToEpochMillis() throws Exception {
    JsonNode sent =
        sentFor(
            args(
                List.of(
                    Map.of("questionId", "d1", "type", "DATE", "value", "2024-12-17"),
                    Map.of("questionId", "d2", "type", "DATETIME", "value", "2024-12-17T19:00:00Z"),
                    Map.of(
                        "questionId",
                        "d3",
                        "type",
                        "DATETIME",
                        "value",
                        "2026-01-09T11:50:00-08:00"))));

    assertEquals(1734393600000L, sent.path("answers").get(0).path("value").asLong());
    assertEquals(1734462000000L, sent.path("answers").get(1).path("value").asLong());
    assertEquals(1767988200000L, sent.path("answers").get(2).path("value").asLong());
  }

  @Test
  public void aDateAnswerAlreadyInEpochMillisIsLeftAlone() throws Exception {
    JsonNode sent =
        sentFor(args(List.of(Map.of("questionId", "d1", "type", "DATE", "value", 1734465600000L))));

    assertEquals(1734465600000L, sent.path("answers").get(0).path("value").asLong());
  }

  @Test
  public void anUnreadableDateIsLeftForJiraToReject() throws Exception {
    JsonNode sent =
        sentFor(args(List.of(Map.of("questionId", "d1", "type", "DATE", "value", "last Tuesday"))));

    assertEquals("last Tuesday", sent.path("answers").get(0).path("value").asText());
  }

  @Test
  public void anIso8601StringOnANonDateAnswerIsLeftAlone() throws Exception {
    JsonNode sent =
        sentFor(args(List.of(Map.of("questionId", "q1", "type", "TEXT", "value", "2024-12-17"))));

    assertEquals("2024-12-17", sent.path("answers").get(0).path("value").asText());
  }

  @Test
  public void everyParamIsRequired() {
    Map<String, Object> full = args(ANSWERS);
    for (String param : List.of("issue_key", "form_id", "answers")) {
      Map<String, Object> args = new LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
    verifyNoInteractions(client);
  }

  @Test
  public void anEmptyAnswerListIsRefused() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(args(List.of()), "Bearer t"));
    assertEquals("'answers' must carry at least one answer", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    Map<String, Object> args = args(ANSWERS);
    args.put("retain_history", true);

    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'retain_history'"));
    verifyNoInteractions(client);
  }

  @Test
  public void itIsAWriteTool() {
    assertTrue(tool.isWriteTool());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "form_id", "answers"), props.keySet());
    assertEquals(List.of("issue_key", "form_id", "answers"), schema.get("required"));

    Map<String, Object> answers = (Map<String, Object>) props.get("answers");
    assertEquals("array", answers.get("type"));
    assertEquals(Map.of("type", "object"), answers.get("items"));
  }
}
