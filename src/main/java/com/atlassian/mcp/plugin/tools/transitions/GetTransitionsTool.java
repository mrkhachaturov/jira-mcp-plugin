package com.atlassian.mcp.plugin.tools.transitions;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Get available status transitions for a Jira issue, trimmed to {id, name, to_status} so an agent
 * choosing a transition id is not handed the full workflow payload.
 */
public class GetTransitionsTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetTransitionsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_transitions";
  }

  @Override
  public String description() {
    return "Get available status transitions for a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);

    try {
      String rawJson = client.get("/rest/api/2/issue/" + issueKey + "/transitions", authHeader);
      JsonNode root = mapper.readTree(rawJson);
      JsonNode transitions = root.path("transitions");

      List<Map<String, Object>> simplified = new ArrayList<>();
      if (transitions.isArray()) {
        for (JsonNode t : transitions) {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("id", t.path("id").asText(""));
          entry.put("name", t.path("name").asText(""));

          // The target status has appeared under three shapes across Jira versions and workflow
          // configurations; picking only "to" would silently drop to_status for the others.
          String toStatus = null;
          if (t.has("to") && t.get("to").isObject()) {
            toStatus = t.path("to").path("name").asText(null);
          } else if (t.has("to_status")) {
            toStatus = t.path("to_status").asText(null);
          } else if (t.has("status")) {
            toStatus = t.path("status").asText(null);
          }
          if (toStatus != null) {
            entry.put("to_status", toStatus);
          }

          simplified.add(entry);
        }
      }

      return mapper.writeValueAsString(simplified);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to get transitions: " + e.getMessage());
    }
  }
}
