package com.atlassian.mcp.plugin.tools.issues;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class GetIssueToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetIssueTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("{\"key\":\"PROJ-1\"}");
    tool = new GetIssueTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), any());
    return url.getValue();
  }

  private static String issueWithComments(int count) {
    StringBuilder sb = new StringBuilder("{\"key\":\"PROJ-1\",\"fields\":{\"comment\":{");
    sb.append("\"total\":").append(count).append(",\"comments\":[");
    for (int i = 0; i < count; i++) {
      if (i > 0) sb.append(",");
      sb.append("{\"id\":\"").append(i).append("\"}");
    }
    return sb.append("]}}}").toString();
  }

  @Test
  public void everyDeclaredParamReachesTheRequest() throws Exception {
    String url =
        urlFor(
            Map.of(
                "issue_key", "PROJ-123",
                "fields", "summary,status",
                "expand", "changelog",
                "properties", "prop-a,prop-b",
                "comment_limit", 3,
                "update_history", false));

    assertTrue(url, url.startsWith("/rest/api/2/issue/PROJ-123?"));
    assertTrue(url, url.contains("fields=summary%2Cstatus"));
    assertTrue(url, url.contains("expand=changelog"));
    assertTrue(url, url.contains("properties=prop-a%2Cprop-b"));
    assertTrue(url, url.contains("updateHistory=false"));
  }

  @Test
  public void defaultsApplyWhenNothingIsPassed() throws Exception {
    String url = urlFor(Map.of("issue_key", "PROJ-1"));

    assertTrue(url, url.contains("fields=summary%2Cstatus%2Cassignee"));
    assertTrue(url, url.contains("updateHistory=true"));
    assertFalse(url, url.contains("expand="));
    assertFalse(url, url.contains("properties="));
  }

  @Test
  public void commentLimitTrimsTheResponseToTheMostRecentComments() throws Exception {
    when(client.get(anyString(), any())).thenReturn(issueWithComments(5));

    String result = tool.execute(Map.of("issue_key", "PROJ-1", "comment_limit", 2), "Bearer t");

    JsonNode comments = MAPPER.readTree(result).path("fields").path("comment").path("comments");
    assertEquals(2, comments.size());
    assertEquals("3", comments.get(0).path("id").asText());
    assertEquals("4", comments.get(1).path("id").asText());
  }

  @Test
  public void commentLimitDefaultsToTen() throws Exception {
    when(client.get(anyString(), any())).thenReturn(issueWithComments(25));

    String result = tool.execute(Map.of("issue_key", "PROJ-1"), "Bearer t");

    assertEquals(
        10, MAPPER.readTree(result).path("fields").path("comment").path("comments").size());
  }

  @Test(expected = McpToolException.class)
  public void issueKeyIsRequired() throws Exception {
    tool.execute(Map.of(), "Bearer t");
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> props = (Map<String, Object>) tool.inputSchema().get("properties");

    assertEquals(
        Set.of("issue_key", "fields", "expand", "comment_limit", "properties", "update_history"),
        props.keySet());
  }
}
