package com.atlassian.mcp.plugin.tools.comments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EditCommentTool extends TypedTool<EditCommentTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(value = "The ID of the comment to edit, e.g. 10100", required = true) long commentId,
      @ToolArg(value = "Updated comment text in Markdown format", required = true) String body,
      @ToolArg(
              "(Optional) Restricts who can read the comment. Omit to let everyone who can see the"
                  + " issue read it.")
          AddCommentTool.Visibility visibility) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public EditCommentTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    String body = AddCommentTool.serialize(mapper, args.body(), args.visibility());
    return client.put(
        "/rest/api/2/issue/" + args.issueKey() + "/comment/" + args.commentId(),
        body,
        context.authHeader());
  }
}
