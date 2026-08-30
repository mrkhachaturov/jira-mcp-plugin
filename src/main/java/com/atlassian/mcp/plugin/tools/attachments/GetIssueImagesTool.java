package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetIssueImagesTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string(
              "issue_key",
              "Jira issue key (e.g., 'PROJ-123'). Returns image attachments as inline ImageContent"
                  + " for LLM vision.")
          .required();

  private final JiraRestClient client;

  public GetIssueImagesTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_images";
  }

  @Override
  public String description() {
    return "Get all images attached to a Jira issue as inline image content. Filters attachments to images only (PNG, JPEG, GIF, WebP, SVG, BMP) and returns them as base64-encoded ImageContent that clients can render directly. Non-image attachments are excluded. Files with ambiguous MIME types (application/octet-stream) are detected by filename extension as a fallback. Images larger than 50 MB are skipped with an error entry in the summary.";
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
