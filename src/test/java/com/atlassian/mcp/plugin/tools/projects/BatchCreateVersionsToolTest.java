package com.atlassian.mcp.plugin.tools.projects;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class BatchCreateVersionsToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TWO_VERSIONS =
      "[{\"name\":\"v1.0\",\"startDate\":\"2025-01-01\",\"releaseDate\":\"2025-02-01\","
          + "\"description\":\"First release\"},{\"name\":\"v2.0\"}]";

  private JiraRestClient client;
  private BatchCreateVersionsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{\"id\":\"10000\"}");
    tool = new BatchCreateVersionsTool(client);
  }

  @Test
  public void everyVersionInTheListIsPostedUnderTheGivenProject() throws Exception {
    String result =
        tool.execute(Map.of("project_key", "PROJ", "versions", TWO_VERSIONS), "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).post(eq("/rest/api/2/version"), body.capture(), eq("Bearer t"));

    JsonNode first = MAPPER.readTree(body.getAllValues().get(0));
    assertEquals("PROJ", first.path("project").asText());
    assertEquals("v1.0", first.path("name").asText());
    assertEquals("2025-01-01", first.path("startDate").asText());
    assertEquals("2025-02-01", first.path("releaseDate").asText());
    assertEquals("First release", first.path("description").asText());

    JsonNode second = MAPPER.readTree(body.getAllValues().get(1));
    assertEquals("PROJ", second.path("project").asText());
    assertEquals("v2.0", second.path("name").asText());

    assertEquals(2, MAPPER.readTree(result).path("created").asInt());
  }

  @Test
  public void projectKeyChangesWhereTheVersionsLand() throws Exception {
    tool.execute(Map.of("project_key", "ACV2", "versions", "[{\"name\":\"v1.0\"}]"), "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(anyString(), body.capture(), any());

    assertEquals("ACV2", MAPPER.readTree(body.getValue()).path("project").asText());
  }

  @Test
  public void aFailedVersionIsReportedWithoutStoppingTheBatch() throws Exception {
    when(client.post(anyString(), anyString(), any()))
        .thenReturn("{\"id\":\"10000\"}")
        .thenThrow(new McpToolException("version name already exists"));

    String result =
        tool.execute(Map.of("project_key", "PROJ", "versions", TWO_VERSIONS), "Bearer t");

    JsonNode parsed = MAPPER.readTree(result);
    assertEquals(1, parsed.path("created").asInt());
    assertEquals(1, parsed.path("errors").asInt());
    assertEquals("v2.0", parsed.path("failed").get(0).path("name").asText());
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    assertThrows(
        McpToolException.class, () -> tool.execute(Map.of("versions", TWO_VERSIONS), "Bearer t"));
    assertThrows(
        McpToolException.class, () -> tool.execute(Map.of("project_key", "PROJ"), "Bearer t"));
  }

  @Test(expected = McpToolException.class)
  public void invalidVersionsJsonIsRejected() throws Exception {
    tool.execute(Map.of("project_key", "PROJ", "versions", "not json"), "Bearer t");
  }

  @Test
  public void progressIsReportedForEveryVersion() throws Exception {
    List<String> messages = new ArrayList<>();

    tool.executeWithProgress(
        Map.of("project_key", "PROJ", "versions", TWO_VERSIONS),
        "Bearer t",
        (current, total, message) -> messages.add(message));

    assertEquals(3, messages.size());
    assertTrue(messages.get(0), messages.get(0).startsWith("Creating version 1 of 2"));
    assertTrue(messages.get(2), messages.get(2).contains("2 created"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("project_key", "versions"),
        ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(
        Set.of("project_key", "versions"), Set.copyOf((List<String>) schema.get("required")));
  }
}
