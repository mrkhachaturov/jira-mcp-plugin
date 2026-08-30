package com.atlassian.mcp.plugin.tools.attachments;

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

public class GetIssueImagesToolTest {

  private static final String ATTACHMENTS =
      "{\"fields\":{\"attachment\":["
          + "{\"filename\":\"screenshot.png\",\"mimeType\":\"image/png\"},"
          + "{\"filename\":\"spec.pdf\",\"mimeType\":\"application/pdf\"},"
          + "{\"filename\":\"diagram.SVG\",\"mimeType\":\"application/octet-stream\"},"
          + "{\"filename\":\"notes.txt\",\"mimeType\":\"application/octet-stream\"},"
          + "{\"filename\":\"nodots\",\"mimeType\":\"application/octet-stream\"}]}}";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JiraRestClient client;
  private GetIssueImagesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ATTACHMENTS);
    tool = new GetIssueImagesTool(client);
  }

  private JsonNode imagesOf(String issueKey) throws Exception {
    return MAPPER.readTree(tool.execute(Map.of("issue_key", issueKey), "Bearer t"));
  }

  private static List<String> filenames(JsonNode result) {
    List<String> names = new ArrayList<>();
    for (JsonNode image : result.path("images")) names.add(image.path("filename").asText());
    return names;
  }

  @Test
  public void issueKeyReachesTheRequestPath() throws Exception {
    tool.execute(Map.of("issue_key", "PROJ-123"), "Bearer t");

    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    assertEquals("/rest/api/2/issue/PROJ-123?fields=attachment", url.getValue());
  }

  @Test
  public void onlyTheImagesComeBack() throws Exception {
    JsonNode result = imagesOf("PROJ-123");

    assertEquals("PROJ-123", result.path("issue_key").asText());
    assertEquals(2, result.path("total").asInt());
    assertEquals(List.of("screenshot.png", "diagram.SVG"), filenames(result));
  }

  /** Jira types an unrecognised upload application/octet-stream, so the extension decides. */
  @Test
  public void anAmbiguousMimeTypeFallsBackToTheFilenameExtension() throws Exception {
    assertTrue(filenames(imagesOf("PROJ-123")).contains("diagram.SVG"));
    assertFalse(filenames(imagesOf("PROJ-123")).contains("notes.txt"));
  }

  @Test
  public void anIssueWithNoAttachmentsReturnsAnEmptyList() throws Exception {
    when(client.get(anyString(), any())).thenReturn("{\"fields\":{\"attachment\":[]}}");

    JsonNode result = imagesOf("PROJ-123");

    assertEquals(0, result.path("total").asInt());
    assertTrue(result.path("images").isArray());
  }

  @Test
  public void issueKeyIsRequired() {
    McpToolException e =
        assertThrows(McpToolException.class, () -> tool.execute(Map.of(), "Bearer t"));
    assertEquals("'issue_key' parameter is required", e.getMessage());
    verifyNoInteractions(client);
  }

  @Test
  public void anUnknownParameterIsRefused() {
    McpToolException e =
        assertThrows(
            McpToolException.class,
            () -> tool.execute(Map.of("issue_key", "PROJ-123", "max_bytes", 1000), "Bearer t"));
    assertTrue(e.getMessage(), e.getMessage().contains("Unknown parameter 'max_bytes'"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("issue_key"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertEquals(List.of("issue_key"), schema.get("required"));
    assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
  }
}
