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

public class CreateIssueLinkTool extends DeclarativeTool {

  private static final ToolParam<String> LINK_TYPE =
      ToolParam.string(
              "link_type", "The type of link to create (e.g., 'Duplicate', 'Blocks', 'Relates to')")
          .required();
  private static final ToolParam<String> INWARD_ISSUE_KEY =
      ToolParam.string(
              "inward_issue_key", "The key of the inward issue (e.g., 'PROJ-123', 'ACV2-642')")
          .required();
  private static final ToolParam<String> OUTWARD_ISSUE_KEY =
      ToolParam.string("outward_issue_key", "The key of the outward issue (e.g., 'PROJ-456')")
          .required();
  private static final ToolParam<String> COMMENT =
      ToolParam.string("comment", "(Optional) Comment to add to the link");
  private static final ToolParam<String> COMMENT_VISIBILITY =
      ToolParam.string(
          "comment_visibility",
          "(Optional) Visibility settings for the comment as JSON string (e.g."
              + " '{\"type\":\"group\",\"value\":\"jira-users\"}')");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateIssueLinkTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "create_issue_link";
  }

  @Override
  public String description() {
    return "Create a link between two Jira issues.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(LINK_TYPE, INWARD_ISSUE_KEY, OUTWARD_ISSUE_KEY, COMMENT, COMMENT_VISIBILITY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String linkType = args.require(LINK_TYPE);
    String inwardIssueKey = args.require(INWARD_ISSUE_KEY);
    String outwardIssueKey = args.require(OUTWARD_ISSUE_KEY);
    String comment = args.get(COMMENT);
    String commentVisibility = args.get(COMMENT_VISIBILITY);

    // The MCP parameter names are flat snake_case for the agent's benefit; Jira's
    // LinkIssueRequestJsonBean accepts only type/inwardIssue/outwardIssue/comment and rejects
    // anything else outright, so the body has to be rebuilt rather than passed through.
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("type", Map.of("name", linkType));
    requestBody.put("inwardIssue", Map.of("key", inwardIssueKey));
    requestBody.put("outwardIssue", Map.of("key", outwardIssueKey));
    if (comment != null) {
      Map<String, Object> commentBody = new HashMap<>();
      commentBody.put("body", comment);
      if (commentVisibility != null) {
        commentBody.put("visibility", jsonObject(mapper, commentVisibility, "comment_visibility"));
      }
      requestBody.put("comment", commentBody);
    }

    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/api/2/issueLink", jsonBody, authHeader);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
