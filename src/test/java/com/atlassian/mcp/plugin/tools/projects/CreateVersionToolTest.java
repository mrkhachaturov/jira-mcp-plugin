package com.atlassian.mcp.plugin.tools.projects;

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

public class CreateVersionToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private CreateVersionTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"id\":\"10000\"}");
    tool = new CreateVersionTool(client);
  }

  private static Map<String, Object> validArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("project_key", "PROJ");
    args.put("name", "v1.0");
    return args;
  }

  private JsonNode bodyFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(eq("/rest/api/2/version"), body.capture(), eq("Bearer t"));
    return MAPPER.readTree(body.getValue());
  }

  @Test
  public void everyDeclaredParamReachesTheRequestBody() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("start_date", "2025-01-01");
    args.put("release_date", "2025-02-01");
    args.put("description", "First release");

    JsonNode body = bodyFor(args);

    assertEquals("PROJ", body.path("project").asText());
    assertEquals("v1.0", body.path("name").asText());
    assertEquals("2025-01-01", body.path("startDate").asText());
    assertEquals("2025-02-01", body.path("releaseDate").asText());
    assertEquals("First release", body.path("description").asText());
  }

  /** Jira answers "Unrecognized field ... not marked as ignorable" for anything else. */
  @Test
  public void bodyCarriesOnlyThePropertyNamesJiraKnows() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("start_date", "2025-01-01");
    args.put("release_date", "2025-02-01");

    JsonNode body = bodyFor(args);

    assertFalse(body.toString(), body.has("project_key"));
    assertFalse(body.toString(), body.has("start_date"));
    assertFalse(body.toString(), body.has("release_date"));
  }

  @Test
  public void optionalFieldsAreOmittedWhenAbsent() throws Exception {
    JsonNode body = bodyFor(validArgs());

    assertFalse(body.toString(), body.has("startDate"));
    assertFalse(body.toString(), body.has("releaseDate"));
    assertFalse(body.toString(), body.has("description"));
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    for (String missing : new String[] {"project_key", "name"}) {
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
        Set.of("project_key", "name", "start_date", "release_date", "description"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(Set.of("project_key", "name"), Set.copyOf((List<String>) schema.get("required")));
  }
}
