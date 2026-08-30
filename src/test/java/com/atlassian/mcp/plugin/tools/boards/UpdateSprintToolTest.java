package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class UpdateSprintToolTest {

  private JiraRestClient client;
  private UpdateSprintTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.put(anyString(), anyString(), any())).thenReturn("{}");
    tool = new UpdateSprintTool(client);
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("sprint_id", 10001);
    args.put("name", "Sprint 2");
    args.put("state", "closed");
    args.put("start_date", "2026-02-01");
    args.put("end_date", "2026-02-14");
    args.put("goal", "Land the migration");

    tool.execute(args, "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).put(url.capture(), body.capture(), any());

    assertEquals("/rest/agile/1.0/sprint/10001", url.getValue());
    String json = body.getValue();
    assertTrue(json, json.contains("\"name\":\"Sprint 2\""));
    assertTrue(json, json.contains("\"state\":\"closed\""));
    assertTrue(json, json.contains("\"startDate\":\"2026-02-01\""));
    assertTrue(json, json.contains("\"endDate\":\"2026-02-14\""));
    assertTrue(json, json.contains("\"goal\":\"Land the migration\""));
  }

  @Test
  public void absentOptionalsAreOmittedFromTheBody() throws Exception {
    tool.execute(Map.of("sprint_id", 10001, "goal", "only this"), "Bearer t");

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).put(anyString(), body.capture(), any());

    assertEquals("{\"goal\":\"only this\"}", body.getValue());
  }

  @Test
  public void sprintIdIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("name", "Sprint 2"), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("sprint_id"));
    verifyNoInteractions(client);
  }

  @Test
  public void stateOutsideTheDeclaredEnumIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("sprint_id", 10001, "state", "paused"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("state"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParameterIsRejectedRatherThanIgnored() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("sprint_id", 10001, "sprintId", 10001), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("sprintId"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        Set.of("sprint_id", "name", "state", "start_date", "end_date", "goal"), props.keySet());
    assertEquals(Set.of("sprint_id"), Set.copyOf((List<String>) schema.get("required")));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesTheSprintStates() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> state = (Map<String, Object>) props.get("state");

    assertEquals(List.of("future", "active", "closed"), state.get("enum"));
  }
}
