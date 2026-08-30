package com.atlassian.mcp.plugin.tools.forms;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UpdateProformaFormAnswersToolTest {

  private static final String FORM_UUID = "1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a";
  private static final String ANSWERS =
      "[{\"questionId\":\"q1\",\"type\":\"TEXT\",\"value\":\"Updated\"}]";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private UpdateProformaFormAnswersTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");
    tool = new UpdateProformaFormAnswersTool(client);
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    tool.execute(
        Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID, "answers", ANSWERS), "Bearer t");

    ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).put(path.capture(), body.capture(), any());

    assertEquals("/rest/api/2/issue/PROJ-123/properties/proforma.forms", path.getValue());
    JsonNode sent = MAPPER.readTree(body.getValue());
    assertEquals(FORM_UUID, sent.path("form_id").asText());
    assertEquals(ANSWERS, sent.path("answers").asText());
  }

  @Test
  public void everyParamIsRequired() {
    Map<String, Object> full =
        Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID, "answers", ANSWERS);
    for (String param : List.of("issue_key", "form_id", "answers")) {
      Map<String, Object> args = new java.util.LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
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

    assertEquals(java.util.Set.of("issue_key", "form_id", "answers"), props.keySet());
    assertEquals(List.of("issue_key", "form_id", "answers"), schema.get("required"));
  }
}
