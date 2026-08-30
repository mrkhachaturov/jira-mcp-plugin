package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LinkToEpicTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "The key of the issue to link (e.g., 'PROJ-123', 'ACV2-642')")
          .required();
  private static final ToolParam<String> EPIC_KEY =
      ToolParam.string("epic_key", "The key of the epic to link to (e.g., 'PROJ-456')").required();

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public LinkToEpicTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "link_to_epic";
  }

  @Override
  public String description() {
    return "Link an existing issue to an epic.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, EPIC_KEY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String epicKey = args.require(EPIC_KEY);

    // Jira Agile API expects: {"issues": ["PROJ-123"]}
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("issues", List.of(issueKey));
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      client.post("/rest/agile/1.0/epic/" + epicKey + "/issue", jsonBody, authHeader);
      String updatedIssue = client.get("/rest/api/2/issue/" + issueKey, authHeader);
      Map<String, Object> result = new HashMap<>();
      result.put("message", "Issue " + issueKey + " has been linked to epic " + epicKey + ".");
      result.put("issue", mapper.readTree(updatedIssue));
      return mapper.writeValueAsString(result);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to link issue to epic: " + e.getMessage());
    }
  }
}
