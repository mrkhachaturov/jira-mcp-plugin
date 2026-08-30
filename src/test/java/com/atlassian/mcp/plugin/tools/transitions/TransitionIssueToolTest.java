package com.atlassian.mcp.plugin.tools.transitions;

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

public class TransitionIssueToolTest {

  private JiraRestClient client;
  private TransitionIssueTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("");
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-123\"}");
    tool = new TransitionIssueTool(client);
  }

  private String postBodyFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(eq("/rest/api/2/issue/PROJ-123/transitions"), body.capture(), any());
    return body.getValue();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    Map<String, Object> args = new HashMap<>();
    args.put("issue_key", "PROJ-123");
    args.put("transition_id", "31");
    args.put("fields", Map.of("resolution", Map.of("name", "Fixed")));
    args.put("comment", "Closing this out");

    String body = postBodyFor(args);

    assertTrue(body, body.contains("\"transition\":{\"id\":\"31\"}"));
    assertTrue(body, body.contains("\"resolution\":{\"name\":\"Fixed\"}"));
    assertTrue(body, body.contains("\"comment\""));
    assertTrue(body, body.contains("Closing this out"));
  }

  @Test
  public void absentOptionalsLeaveOnlyTheTransition() throws Exception {
    String body = postBodyFor(Map.of("issue_key", "PROJ-123", "transition_id", "11"));

    assertEquals("{\"transition\":{\"id\":\"11\"}}", body);
  }

  @Test
  public void aFieldsValueThatIsNotAnObjectIsRejectedBeforeAnyWrite() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-123", "transition_id", "11", "fields", "not json"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("fields"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRejectedBeforeAnyWrite() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("issue_key", "PROJ-123", "transition_id", "11", "resolution", "Fixed"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("resolution"));
    verifyNoMoreInteractions(client);
  }

  @Test
  public void theUpdatedIssueIsReturnedAfterTheTransition() throws Exception {
    String result =
        tool.execute(Map.of("issue_key", "PROJ-123", "transition_id", "11"), "Bearer t");

    verify(client).get("/rest/api/2/issue/PROJ-123", "Bearer t");
    assertEquals("{\"key\":\"PROJ-123\"}", result);
  }

  @Test
  public void requiredParamsAreEnforced() {
    McpToolException noKey =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("transition_id", "11"), "Bearer t"));
    assertTrue(noKey.getMessage(), noKey.getMessage().contains("issue_key"));

    McpToolException noTransition =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("issue_key", "PROJ-1"), "Bearer t"));
    assertTrue(noTransition.getMessage(), noTransition.getMessage().contains("transition_id"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(
        java.util.Set.of("issue_key", "transition_id", "fields", "comment"), props.keySet());
    assertEquals(
        java.util.Set.of("issue_key", "transition_id"),
        java.util.Set.copyOf((java.util.List<String>) schema.get("required")));
  }
}
