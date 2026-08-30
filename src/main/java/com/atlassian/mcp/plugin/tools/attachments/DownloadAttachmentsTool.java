package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class DownloadAttachmentsTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();

  private final JiraRestClient client;

  public DownloadAttachmentsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "download_attachments";
  }

  @Override
  public String description() {
    return "Download attachments from a Jira issue. Returns attachment contents as base64-encoded embedded resources so that they are available over the MCP protocol without requiring filesystem access on the server.";
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

    return client.get("/rest/api/2/issue/" + issueKey + "?fields=attachment", authHeader);
  }
}
