package com.atlassian.mcp.plugin.tools.projects;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.CancellationSignal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class BatchCreateVersionsToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final List<Map<String, Object>> TWO_VERSIONS =
      List.of(
          Map.of(
              "name", "v1.0",
              "start_date", "2025-01-01",
              "release_date", "2025-02-01",
              "description", "First release"),
          Map.of("name", "v2.0"));

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
    assertFalse(second.toString(), second.has("startDate"));
    assertFalse(second.toString(), second.has("releaseDate"));
    assertFalse(second.toString(), second.has("description"));

    assertEquals(2, MAPPER.readTree(result).path("created").asInt());
  }

  /** Jira answers "Unrecognized field ... not marked as ignorable" for anything else. */
  @Test
  public void bodyCarriesOnlyThePropertyNamesJiraKnows() throws Exception {
    tool.execute(Map.of("project_key", "PROJ", "versions", TWO_VERSIONS), "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client, times(2)).post(anyString(), body.capture(), any());

    JsonNode first = MAPPER.readTree(body.getAllValues().get(0));
    assertFalse(first.toString(), first.has("project_key"));
    assertFalse(first.toString(), first.has("start_date"));
    assertFalse(first.toString(), first.has("release_date"));
  }

  @Test
  public void projectKeyChangesWhereTheVersionsLand() throws Exception {
    tool.execute(
        Map.of("project_key", "ACV2", "versions", List.of(Map.of("name", "v1.0"))), "Bearer t");

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
    verifyNoInteractions(client);
  }

  @Test
  public void aVersionMissingItsNameIsRejectedByIndex() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of(
                        "project_key",
                        "PROJ",
                        "versions",
                        List.of(Map.of("name", "v1.0"), Map.of("description", "no name"))),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("versions[1].name"));
    verifyNoInteractions(client);
  }

  @Test
  public void aVersionCarryingAnUndeclaredFieldIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of(
                        "project_key",
                        "PROJ",
                        "versions",
                        List.of(Map.of("name", "v1.0", "released", true))),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("released"));
    verifyNoInteractions(client);
  }

  @Test(expected = McpToolException.class)
  public void aVersionsValueThatIsNotAnObjectListIsRejected() throws Exception {
    tool.execute(Map.of("project_key", "PROJ", "versions", "not json"), "Bearer t");
  }

  /** Fires once the batch has reached its {@code items}-th checkpoint. */
  private static CancellationSignal stopAfter(int items) {
    AtomicInteger reached = new AtomicInteger();
    return () ->
        reached.getAndIncrement() < items ? Optional.empty() : Optional.of("caller went away");
  }

  @Test
  public void aBatchStoppedPartWayReportsTheVersionsItAlreadyCreated() throws Exception {
    String result =
        tool.executeWithSdkProgress(
            Map.of("project_key", "PROJ", "versions", TWO_VERSIONS),
            "Bearer t",
            null,
            null,
            stopAfter(1));

    verify(client, times(1)).post(anyString(), anyString(), any());

    JsonNode parsed = MAPPER.readTree(result);
    assertEquals(1, parsed.path("created").asInt());
    assertEquals(1, parsed.path("versions").size());
    assertTrue(parsed.toString(), parsed.path("cancelled").asBoolean());
    assertEquals("caller went away", parsed.path("cancelled_reason").asText());
    assertEquals(1, parsed.path("processed").asInt());
    assertEquals(2, parsed.path("total").asInt());
  }

  @Test
  public void aBatchStoppedBeforeItsFirstItemCreatesNothing() throws Exception {
    String result =
        tool.executeWithSdkProgress(
            Map.of("project_key", "PROJ", "versions", TWO_VERSIONS),
            "Bearer t",
            null,
            null,
            stopAfter(0));

    verify(client, never()).post(anyString(), anyString(), any());

    JsonNode parsed = MAPPER.readTree(result);
    assertEquals(0, parsed.path("created").asInt());
    assertEquals(0, parsed.path("processed").asInt());
    assertTrue(parsed.toString(), parsed.path("cancelled").asBoolean());
  }

  @Test
  public void aBatchThatRunsToTheEndIsNotMarkedCancelled() throws Exception {
    String result =
        tool.executeWithSdkProgress(
            Map.of("project_key", "PROJ", "versions", TWO_VERSIONS),
            "Bearer t",
            null,
            null,
            CancellationSignal.NONE);

    JsonNode parsed = MAPPER.readTree(result);
    assertEquals(2, parsed.path("created").asInt());
    assertFalse(parsed.toString(), parsed.has("cancelled"));
    assertFalse(parsed.toString(), parsed.has("processed"));
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

  @Test
  @SuppressWarnings("unchecked")
  public void schemaDescribesTheShapeOfOneVersion() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> versions = (Map<String, Object>) props.get("versions");
    Map<String, Object> item = (Map<String, Object>) versions.get("items");

    assertEquals("array", versions.get("type"));
    assertEquals("object", item.get("type"));
    assertEquals(Boolean.FALSE, item.get("additionalProperties"));
    assertEquals(
        Set.of("name", "start_date", "release_date", "description"),
        ((Map<String, Object>) item.get("properties")).keySet());
    assertEquals(List.of("name"), item.get("required"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void oneVersionIsDescribedTheWayCreateVersionDescribesIt() {
    Map<String, Object> item =
        (Map<String, Object>)
            ((Map<String, Object>)
                    ((Map<String, Object>) tool.inputSchema().get("properties")).get("versions"))
                .get("items");
    Map<String, Object> perVersion = (Map<String, Object>) item.get("properties");
    Map<String, Object> single =
        (Map<String, Object>) new CreateVersionTool(client).inputSchema().get("properties");

    for (String field : List.of("name", "start_date", "release_date", "description")) {
      assertEquals(field, single.get(field), perVersion.get(field));
    }
  }
}
