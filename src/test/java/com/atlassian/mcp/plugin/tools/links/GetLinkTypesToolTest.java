package com.atlassian.mcp.plugin.tools.links;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class GetLinkTypesToolTest {

  private static final String ALL_TYPES =
      "{\"issueLinkTypes\":["
          + "{\"id\":\"1\",\"name\":\"Blocks\"},"
          + "{\"id\":\"2\",\"name\":\"Relates\"},"
          + "{\"id\":\"3\",\"name\":\"Duplicate\"}]}";

  private JiraRestClient client;
  private GetLinkTypesTool tool;

  @Before
  public void setUp() throws Exception {
    client = mock(JiraRestClient.class);
    when(client.get(anyString(), any())).thenReturn(ALL_TYPES);
    tool = new GetLinkTypesTool(client);
  }

  private JsonNode resultFor(Map<String, Object> args) throws Exception {
    return new ObjectMapper().readTree(tool.execute(args, "Bearer t"));
  }

  @Test
  public void nameFilterNarrowsTheResultClientSide() throws Exception {
    JsonNode types = resultFor(Map.of("name_filter", "blo")).path("issueLinkTypes");

    assertEquals(1, types.size());
    assertEquals("Blocks", types.get(0).path("name").asText());
    // The endpoint takes no filter parameter, so the request must stay unqualified.
    verify(client).get("/rest/api/2/issueLinkType", "Bearer t");
  }

  @Test
  public void absentNameFilterReturnsEveryType() throws Exception {
    assertEquals(3, resultFor(Map.of()).path("issueLinkTypes").size());
  }

  @Test
  public void blankNameFilterIsTreatedAsAbsent() throws Exception {
    assertEquals(3, resultFor(Map.of("name_filter", "   ")).path("issueLinkTypes").size());
  }

  @Test
  public void unrecognisedResponseShapeIsPassedThrough() throws Exception {
    when(client.get(anyString(), any())).thenReturn("[]");

    assertEquals("[]", tool.execute(Map.of("name_filter", "blo"), "Bearer t"));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void schemaAdvertisesExactlyTheDeclaredParams() {
    Map<String, Object> schema = tool.inputSchema();

    assertEquals(Set.of("name_filter"), ((Map<String, Object>) schema.get("properties")).keySet());
    assertTrue(((java.util.List<String>) schema.get("required")).isEmpty());
  }
}
