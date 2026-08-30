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

public class GetProformaFormDetailsToolTest {

  private static final String FORM_UUID = "1946b8b7-8f03-4dc0-ac2d-5fac0d960c6a";
  private static final String OTHER_UUID = "2a57c9c8-9014-4ed1-bd3e-6fbd1ea71d7b";

  private static final String PROPERTY =
      "{\"key\":\"proforma.forms\",\"value\":{\"forms\":["
          + "{\"uuid\":\""
          + FORM_UUID
          + "\",\"design\":{\"questions\":{\"1\":{}}}},"
          + "{\"uuid\":\""
          + OTHER_UUID
          + "\",\"design\":{\"questions\":{\"2\":{}}}}]}}";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetProformaFormDetailsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(PROPERTY);
    tool = new GetProformaFormDetailsTool(client);
  }

  @Test
  public void issueKeyReachesTheRequest() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    assertEquals("/rest/api/2/issue/PROJ-123/properties/proforma.forms", url.getValue());
  }

  @Test
  public void formIdSelectsOneFormOutOfTheProperty() throws Exception {
    JsonNode form =
        MAPPER.readTree(tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "t"));

    assertEquals(FORM_UUID, form.path("uuid").asText());
    assertTrue(form.path("design").path("questions").has("1"));
  }

  @Test
  public void anotherFormIdSelectsTheOtherForm() throws Exception {
    JsonNode form =
        MAPPER.readTree(tool.execute(Map.of("issue_key", "PROJ-123", "form_id", OTHER_UUID), "t"));

    assertEquals(OTHER_UUID, form.path("uuid").asText());
  }

  /** ProForma has named a form's identifier both 'id' and 'uuid' across its releases. */
  @Test
  public void aFormIdentifiedByIdIsFoundToo() throws Exception {
    when(client.get(anyString(), any()))
        .thenReturn("{\"value\":[{\"id\":\"" + FORM_UUID + "\",\"name\":\"Onboarding\"}]}");

    JsonNode form =
        MAPPER.readTree(tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "t"));

    assertEquals("Onboarding", form.path("name").asText());
  }

  @Test
  public void aFormIdNotOnTheIssueIsReportedWithTheIdsThatAre() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "form_id", "nosuch"), "t"));

    assertTrue(e.getMessage(), e.getMessage().contains("'nosuch' is not attached to PROJ-123"));
    assertTrue(e.getMessage(), e.getMessage().contains(FORM_UUID));
    assertTrue(e.getMessage(), e.getMessage().contains(OTHER_UUID));
  }

  @Test
  public void aPropertyWithoutFormsIsReported() throws Exception {
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"proforma.forms\",\"value\":{}}");

    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID), "t"));

    assertTrue(e.getMessage(), e.getMessage().contains("holds no list of forms"));
  }

  @Test
  public void bothParamsAreRequired() {
    Map<String, Object> full = Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID);
    for (String param : List.of("issue_key", "form_id")) {
      Map<String, Object> args = new LinkedHashMap<>(full);
      args.remove(param);
      McpToolException e =
          assertThrows(McpToolException.class, () -> tool.execute(args, "Bearer t"));
      assertEquals("'" + param + "' parameter is required", e.getMessage());
    }
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-123", "form_id", FORM_UUID, "expand", "design"),
                    "t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'expand'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("issue_key", "form_id"), props.keySet());
    assertEquals(List.of("issue_key", "form_id"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }
}
