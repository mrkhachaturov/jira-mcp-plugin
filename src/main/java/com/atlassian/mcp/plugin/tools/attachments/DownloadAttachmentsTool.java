package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class DownloadAttachmentsTool extends TypedTool<DownloadAttachmentsTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key, e.g. 'PROJ-123'", required = true) String issueKey) {}

  private final JiraRestClient client;

  public DownloadAttachmentsTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "download_attachments";
  }

  @Override
  public String description() {
    return "List every file attached to a Jira issue: filename, size, MIME type, author, creation"
        + " time and the Jira URL the file is served from. The bytes are not inlined — fetch a"
        + " file from its content URL with the same credentials.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    return client.get(
        "/rest/api/2/issue/" + args.issueKey() + "?fields=attachment", context.authHeader());
  }
}
