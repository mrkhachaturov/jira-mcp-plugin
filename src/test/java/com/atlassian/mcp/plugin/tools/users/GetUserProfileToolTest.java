package com.atlassian.mcp.plugin.tools.users;

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

public class GetUserProfileToolTest {

  private JiraRestClient client;
  private GetUserProfileTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"name\":\"jsmith\"}");
    tool = new GetUserProfileTool(client);
  }

  @Test
  public void userIdentifierReachesTheQuery() throws Exception {
    tool.execute(Map.of("user_identifier", "jsmith"), "Bearer t");

    verify(client).get("/rest/api/2/user?username=jsmith", "Bearer t");
  }

  @Test
  public void userIdentifierIsUrlEncoded() throws Exception {
    tool.execute(Map.of("user_identifier", "user@example.com"), "Bearer t");

    verify(client).get("/rest/api/2/user?username=user%40example.com", "Bearer t");
  }

  @Test
  public void missingUserIdentifierIsRejected() {
    McpToolException e = assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("user_identifier"));
    verifyNoInteractions(client);
  }

  @Test
  public void anUndeclaredParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () ->
                tool.execute(Map.of("user_identifier", "jsmith", "account_id", "5b10ac8d82"), "B"));

    assertTrue(e.getMessage(), e.getMessage().contains("account_id"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(
        Set.of("user_identifier"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("user_identifier"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }
}
