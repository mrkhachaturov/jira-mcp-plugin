package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public class CreateRemoteIssueLinkTool extends TypedTool<CreateRemoteIssueLinkTool.Args> {

  public record Args(
      @ToolArg(value = "The key of the issue to add the link to (e.g. 'PROJ-123')", required = true)
          String issueKey,
      @ToolArg(
              value = "The URL to link to (e.g. 'https://example.com/page' or a Confluence page)",
              required = true)
          String url,
      @ToolArg(value = "The title of the link, shown on the issue", required = true) String title,
      @ToolArg("(Optional) Description of the link") String summary,
      @ToolArg("(Optional) Relationship description (e.g. 'causes', 'relates to', 'documentation')")
          String relationship,
      @ToolArg("(Optional) URL of a 16x16 icon for the link") String iconUrl) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateRemoteIssueLinkTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "create_remote_issue_link";
  }

  @Override
  public String description() {
    return "Create a remote issue link (web link or Confluence link) for a Jira issue. This tool"
        + " allows you to add web links and Confluence links to Jira issues. The links will appear"
        + " in the issue's \"Links\" section and can be clicked to navigate to external resources.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    // Everything describing the link target lives under "object"; only relationship sits at the
    // top level. Sent flat, Jira sees neither url nor title and rejects both as missing.
    Map<String, Object> linkTarget = new LinkedHashMap<>();
    linkTarget.put("url", args.url());
    linkTarget.put("title", args.title());
    if (args.summary() != null) linkTarget.put("summary", args.summary());
    if (args.iconUrl() != null) linkTarget.put("icon", Map.of("url16x16", args.iconUrl()));

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("object", linkTarget);
    if (args.relationship() != null) requestBody.put("relationship", args.relationship());

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post(
        "/rest/api/2/issue/" + args.issueKey() + "/remotelink", body, context.authHeader());
  }
}
