package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class CreateSprintToolTest {

  private JiraRestClient client;
  private CreateSprintTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new CreateSprintTool(client);
  }

  private static Map<String, Object> validArgs() {
    Map<String, Object> args = new HashMap<>();
    args.put("board_id", "1000");
    args.put("name", "Sprint 1");
    args.put("start_date", "2026-01-01T00:00:00.000+0000");
    args.put("end_date", "2026-01-14T00:00:00.000+0000");
    return args;
  }

  private String bodyFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(eq("/rest/agile/1.0/sprint"), body.capture(), any());
    return body.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequestBody() throws Exception {
    Map<String, Object> args = validArgs();
    args.put("goal", "Ship the thing");

    String body = bodyFor(args);

    assertTrue(body, body.contains("\"originBoardId\":1000"));
    assertTrue(body, body.contains("\"name\":\"Sprint 1\""));
    assertTrue(body, body.contains("\"startDate\":\"2026-01-01T00:00:00.000+0000\""));
    assertTrue(body, body.contains("\"endDate\":\"2026-01-14T00:00:00.000+0000\""));
    assertTrue(body, body.contains("\"goal\":\"Ship the thing\""));
  }

  @Test
  public void optionalGoalIsOmittedWhenAbsent() throws Exception {
    assertFalse(bodyFor(validArgs()).contains("goal"));
  }

  @Test
  public void everyRequiredParamIsEnforced() {
    for (String missing : new String[] {"board_id", "name", "start_date", "end_date"}) {
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
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        java.util.Set.of("board_id", "name", "start_date", "end_date", "goal"), props.keySet());
    assertEquals(
        java.util.Set.of("board_id", "name", "start_date", "end_date"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
