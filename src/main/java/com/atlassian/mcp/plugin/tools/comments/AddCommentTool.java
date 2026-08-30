package com.atlassian.mcp.plugin.tools.comments;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public class AddCommentTool extends TypedTool<AddCommentTool.Args> {

  /**
   * Who may read a comment. Shared with {@link EditCommentTool}, which restricts it the same way.
   */
  public record Visibility(
      @ToolArg(
              value = "Whether the comment is restricted to a group or to a project role",
              required = true,
              allowed = {"group", "role"})
          String type,
      @ToolArg(
              value =
                  "Name of the group or project role that may read the comment, e.g."
                      + " 'jira-users'",
              required = true)
          String value) {}

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(value = "Comment text in Markdown format", required = true) String body,
      @ToolArg(
              "(Optional) Restricts who can read the comment. Omit to let everyone who can see the"
                  + " issue read it.")
          Visibility visibility) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddCommentTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    String body = serialize(mapper, args.body(), args.visibility());
    return client.post(
        "/rest/api/2/issue/" + args.issueKey() + "/comment", body, context.authHeader());
  }

  /**
   * Builds the comment body both comment tools send; Jira's shape is the same on create and edit.
   */
  static String serialize(ObjectMapper mapper, String markdown, Visibility visibility)
      throws McpToolException {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("body", JiraMarkupConverter.markdownToJira(markdown));
    if (visibility != null) {
      requestBody.put("visibility", Map.of("type", visibility.type(), "value", visibility.value()));
    }
    try {
      return mapper.writeValueAsString(requestBody);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
