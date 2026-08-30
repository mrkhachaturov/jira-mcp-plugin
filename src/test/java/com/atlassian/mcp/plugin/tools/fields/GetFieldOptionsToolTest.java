package com.atlassian.mcp.plugin.tools.fields;

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

public class GetFieldOptionsToolTest {

  private static final String ISSUE_TYPES =
      "{\"values\":[{\"id\":\"10001\",\"name\":\"Bug\"},{\"id\":\"10002\",\"name\":\"Task\"}]}";

  private static final String CREATE_META_FIELDS =
      "{\"values\":[{\"fieldId\":\"priority\",\"allowedValues\":["
          + "{\"name\":\"Highest\"},"
          + "{\"name\":\"High\"},"
          + "{\"name\":\"Low\"},"
          + "{\"value\":\"Hardware\",\"children\":["
          + "{\"value\":\"Highend laptop\"},{\"value\":\"Mouse\"}]}"
          + "]},"
          + "{\"fieldId\":\"summary\"}]}";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetFieldOptionsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(contains("/issuetypes"), any())).thenReturn(ISSUE_TYPES);
    when(client.get(contains("/issuetypes/"), any())).thenReturn(CREATE_META_FIELDS);
    tool = new GetFieldOptionsTool(client);
  }

  private static Map<String, Object> args(Object... keyValues) {
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("field_id", "priority");
    args.put("project_key", "PROJ");
    args.put("issue_type", "Bug");
    for (int i = 0; i < keyValues.length; i += 2) {
      args.put((String) keyValues[i], keyValues[i + 1]);
    }
    return args;
  }

  private JsonNode result(Map<String, Object> args) throws Exception {
    return MAPPER.readTree(tool.execute(args, "Bearer t"));
  }

  @Test
  public void projectKeyAndIssueTypeReachTheCreateMetaUrls() throws Exception {
    tool.execute(args(), "Bearer t");

    ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).get(urls.capture(), eq("Bearer t"));

    assertEquals("/rest/api/2/issue/createmeta/PROJ/issuetypes", urls.getAllValues().get(0));
    // The issue type is resolved to its id, which is what the field endpoint takes.
    assertEquals("/rest/api/2/issue/createmeta/PROJ/issuetypes/10001", urls.getAllValues().get(1));
  }

  @Test
  public void fieldIdSelectsTheOptionListAndIsEchoedBack() throws Exception {
    JsonNode result = result(args());

    assertEquals("priority", result.path("field_id").asText());
    assertEquals("PROJ", result.path("project_key").asText());
    assertEquals("Bug", result.path("issue_type").asText());
    assertEquals(4, result.path("total").asInt());
  }

  @Test
  public void anUnknownFieldIdIsRejected() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(args("field_id", "nosuch"), "t"));
    assertTrue(e.getMessage(), e.getMessage().contains("'nosuch' is not on the create screen"));
  }

  @Test
  public void anUnknownIssueTypeIsRejectedWithTheOnesThatExist() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(args("issue_type", "Epic"), "t"));
    assertTrue(e.getMessage(), e.getMessage().contains("'Epic' not found in project 'PROJ'"));
    assertTrue(e.getMessage(), e.getMessage().contains("Bug, Task"));
  }

  @Test
  public void containsFiltersOnValuesAndOnCascadingChildren() throws Exception {
    JsonNode result = result(args("contains", "high"));

    assertEquals(3, result.path("total").asInt());
    assertEquals("Highest", result.path("options").get(0).path("name").asText());
    assertEquals("High", result.path("options").get(1).path("name").asText());
    // The parent matched only through a child, so the non-matching children are dropped.
    JsonNode cascading = result.path("options").get(2);
    assertEquals("Hardware", cascading.path("value").asText());
    assertEquals(1, cascading.path("children").size());
    assertEquals("Highend laptop", cascading.path("children").get(0).path("value").asText());
  }

  @Test
  public void returnLimitCapsTheFilteredList() throws Exception {
    assertEquals(2, result(args("return_limit", 2)).path("total").asInt());
    assertEquals(1, result(args("contains", "high", "return_limit", 1)).path("total").asInt());
  }

  @Test
  public void valuesOnlyReplacesTheOptionObjectsWithTheirLabels() throws Exception {
    JsonNode result = result(args("values_only", true));

    assertTrue(result.path("options").isMissingNode());
    assertEquals(
        List.of("Highest", "High", "Low", "Hardware"),
        MAPPER.convertValue(result.path("values"), List.class));
  }

  @Test
  public void defaultsReturnEveryOptionAsAFullObject() throws Exception {
    JsonNode result = result(args());

    assertTrue(result.path("values").isMissingNode());
    assertEquals(4, result.path("options").size());
  }

  @Test
  public void theThreeIdentifyingParamsAreRequired() {
    for (String param : List.of("field_id", "project_key", "issue_type")) {
      Map<String, Object> args = args();
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
        assertThrows(McpToolException.class, () -> tool.execute(args("start_at", 5), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'start_at'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        Set.of("field_id", "project_key", "issue_type", "contains", "return_limit", "values_only"),
        props.keySet());
    assertEquals(List.of("field_id", "project_key", "issue_type"), schema.get("required"));
    assertEquals(0, ((Map<String, Object>) props.get("return_limit")).get("default"));
    assertEquals(Boolean.FALSE, ((Map<String, Object>) props.get("values_only")).get("default"));
  }
}
