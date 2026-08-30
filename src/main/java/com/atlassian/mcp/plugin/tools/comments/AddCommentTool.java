package com.atlassian.mcp.plugin.tools.comments;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddCommentTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> BODY =
      ToolParam.string("body", "Comment text in Markdown format").required();
  private static final ToolParam<String> VISIBILITY =
      ToolParam.string(
          "visibility",
          "(Optional) Comment visibility as JSON string (e.g."
              + " '{\"type\":\"group\",\"value\":\"jira-users\"}')");
  private static final ToolParam<Boolean> PUBLIC =
      ToolParam.bool(
              "public",
              "(Optional) For JSM/Service Desk issues only. Set to true for customer-visible"
                  + " comment, false for internal agent-only comment. Uses the ServiceDesk API"
                  + " (plain text, not Markdown). Cannot be combined with visibility.")
          .withDefault(Boolean.FALSE);

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddCommentTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "add_comment";
  }

  @Override
  public String description() {
    return "Add a comment to a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, BODY, VISIBILITY, PUBLIC);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String body = args.require(BODY);
    String visibility = args.get(VISIBILITY);
    boolean isPublic = args.get(PUBLIC);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("body", JiraMarkupConverter.markdownToJira(body));
    if (visibility != null) requestBody.put("visibility", visibility);
    requestBody.put("public", isPublic);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/api/2/issue/" + issueKey + "/comment", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
