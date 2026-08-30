package com.atlassian.mcp.plugin.tools.boards;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AddIssuesToSprintToolTest {

  private JiraRestClient client;
  private AddIssuesToSprintTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.post(anyString(), anyString(), any())).thenReturn("{}");
    tool = new AddIssuesToSprintTool(client);
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    tool.execute(Map.of("sprint_id", 10042, "issue_keys", List.of("PROJ-1", "PROJ-2")), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(client).post(url.capture(), body.capture(), any());

    assertEquals("/rest/agile/1.0/sprint/10042/issue", url.getValue());
    assertEquals("{\"issues\":[\"PROJ-1\",\"PROJ-2\"]}", body.getValue());
  }

  @Test
  public void sprintIdIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_keys", List.of("PROJ-1")), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("sprint_id"));
    verifyNoInteractions(client);
  }

  @Test
  public void issueKeysIsRequired() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("sprint_id", 10042), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("issue_keys"));
    verifyNoInteractions(client);
  }

  @Test
  public void anEmptyIssueListIsRefusedBeforeCallingJira() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("sprint_id", 10042, "issue_keys", List.of()), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("issue_keys"));
    verifyNoInteractions(client);
  }

  @Test
  public void unknownParameterIsRejectedRatherThanIgnored() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(
                    Map.of("sprint_id", 10042, "issue_keys", List.of("PROJ-1"), "issues", "PROJ-2"),
                    "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("issues"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("sprint_id", "issue_keys"), props.keySet());
    assertEquals(
        Set.of("sprint_id", "issue_keys"), Set.copyOf((List<String>) schema.get("required")));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void issueKeysIsAdvertisedAsAnArrayOfStrings() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");
    Map<String, Object> issueKeys = (Map<String, Object>) props.get("issue_keys");

    assertEquals("array", issueKeys.get("type"));
    assertEquals(Map.of("type", "string"), issueKeys.get("items"));
  }
}
