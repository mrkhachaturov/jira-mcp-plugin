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

public class EditCommentTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> COMMENT_ID =
      ToolParam.string("comment_id", "The ID of the comment to edit").required();
  private static final ToolParam<String> BODY =
      ToolParam.string("body", "Updated comment text in Markdown format").required();
  private static final ToolParam<String> VISIBILITY =
      ToolParam.string(
          "visibility",
          "(Optional) Comment visibility as JSON string (e.g."
              + " '{\"type\":\"group\",\"value\":\"jira-users\"}')");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public EditCommentTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "edit_comment";
  }

  @Override
  public String description() {
    return "Edit an existing comment on a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, COMMENT_ID, BODY, VISIBILITY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String commentId = args.require(COMMENT_ID);
    String body = args.require(BODY);
    String visibility = args.get(VISIBILITY);

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("body", JiraMarkupConverter.markdownToJira(body));
    if (visibility != null) {
      requestBody.put("visibility", jsonObject(mapper, visibility, "visibility"));
    }
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.put(
          "/rest/api/2/issue/" + issueKey + "/comment/" + commentId, jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
