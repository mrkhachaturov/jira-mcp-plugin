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

public class CreateRemoteIssueLinkTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string(
              "issue_key", "The key of the issue to add the link to (e.g., 'PROJ-123', 'ACV2-642')")
          .required();
  private static final ToolParam<String> URL =
      ToolParam.string(
              "url", "The URL to link to (e.g., 'https://example.com/page' or Confluence page URL)")
          .required();
  private static final ToolParam<String> TITLE =
      ToolParam.string(
              "title", "The title/name of the link (e.g., 'Documentation Page', 'Confluence Page')")
          .required();
  private static final ToolParam<String> SUMMARY =
      ToolParam.string("summary", "(Optional) Description of the link");
  private static final ToolParam<String> RELATIONSHIP =
      ToolParam.string(
          "relationship",
          "(Optional) Relationship description (e.g., 'causes', 'relates to', 'documentation')");
  private static final ToolParam<String> ICON_URL =
      ToolParam.string("icon_url", "(Optional) URL to a 16x16 icon for the link");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateRemoteIssueLinkTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, URL, TITLE, SUMMARY, RELATIONSHIP, ICON_URL);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String url = args.require(URL);
    String title = args.require(TITLE);
    String summary = args.get(SUMMARY);
    String relationship = args.get(RELATIONSHIP);
    String iconUrl = args.get(ICON_URL);

    // Everything describing the link target lives under "object"; only relationship sits at the
    // top level. Sent flat, Jira sees neither url nor title and rejects both as missing.
    Map<String, Object> linkTarget = new HashMap<>();
    linkTarget.put("url", url);
    linkTarget.put("title", title);
    if (summary != null) linkTarget.put("summary", summary);
    if (iconUrl != null) linkTarget.put("icon", Map.of("url16x16", iconUrl));

    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("object", linkTarget);
    if (relationship != null) requestBody.put("relationship", relationship);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/api/2/issue/" + issueKey + "/remotelink", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
