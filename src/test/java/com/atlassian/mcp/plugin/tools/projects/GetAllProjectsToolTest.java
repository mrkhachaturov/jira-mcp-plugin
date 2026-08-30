package com.atlassian.mcp.plugin.tools.projects;

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

public class GetAllProjectsToolTest {

  private JiraRestClient client;
  private GetAllProjectsTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn("[]");
    tool = new GetAllProjectsTool(client);
  }

  private String urlFor(Map<String, Object> args) throws Exception {
    tool.execute(args, "Bearer t");
    ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
    verify(client).get(url.capture(), eq("Bearer t"));
    return url.getValue();
  }

  @Test
  public void includeArchivedReachesTheRequest() throws Exception {
    assertEquals(
        "/rest/api/2/project?includeArchived=true", urlFor(Map.of("include_archived", true)));
  }

  @Test
  public void includeArchivedAcceptsTheStringForm() throws Exception {
    assertTrue(urlFor(Map.of("include_archived", "true")).contains("includeArchived=true"));
  }

  @Test
  public void includeArchivedDefaultsToFalse() throws Exception {
    assertEquals("/rest/api/2/project?includeArchived=false", urlFor(Map.of()));
  }

  @Test
  public void anUndeclaredParamIsRejected() {
    McpToolException e =
        assertThrows(
            McpToolException.class, () -> tool.execute(Map.of("expand", "lead"), "Bearer t"));

    assertTrue(e.getMessage(), e.getMessage().contains("expand"));
    verifyNoInteractions(client);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");

    assertEquals(Set.of("include_archived"), props.keySet());
    assertEquals(List.of(), schema.get("required"));

    Map<String, Object> includeArchived = (Map<String, Object>) props.get("include_archived");
    assertEquals("boolean", includeArchived.get("type"));
    assertEquals(Boolean.FALSE, includeArchived.get("default"));
  }
}
