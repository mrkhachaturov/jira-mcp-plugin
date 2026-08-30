package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.atlassian.mcp.plugin.tools.UiBinding;
import com.atlassian.mcp.plugin.tools.UiToolDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GetIssueTool extends TypedTool<GetIssueTool.Args> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_COMMENTS = 100;

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              value =
                  "(Optional) Comma-separated list of fields to return (e.g."
                      + " 'summary,status,customfield_10010'), a single field name (e.g."
                      + " 'duedate'), or '*all' for every field including custom ones. Omit for"
                      + " essential fields only.",
              defaultValue = SearchTool.DEFAULT_FIELDS)
          String fields,
      @ToolArg(
              "(Optional) Fields to expand: 'renderedFields' for rendered content, 'transitions'"
                  + " for available status transitions, 'changelog' for history")
          String expand,
      @ToolArg(
              value = "Maximum number of comments to include (0 for no comments)",
              defaultValue = "10")
          int commentLimit,
      @ToolArg("(Optional) Comma-separated list of issue properties to return") String properties,
      @ToolArg(
              value = "Whether to update the issue view history for the requesting user",
              defaultValue = "true")
          boolean updateHistory) {}

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetIssueTool(JiraRestClient client) {
    this(client, null);
  }

  public GetIssueTool(JiraRestClient client, UiBinding ui) {
    super(Args.class);
    this.client = client;
    this.ui = ui;
  }

  @Override
  public String name() {
    return "get_issue";
  }

  @Override
  public String description() {
    return "Get details of a specific Jira issue including its Epic links and relationship"
        + " information.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String uiResourceUri() {
    return ui == null ? null : ui.resourceUri();
  }

  @Override
  public String iconUri() {
    return ui == null ? null : IconConstants.JIRA_LOGO_DATA_URI;
  }

  @Override
  public Map<String, Object> outputSchema() {
    return ui == null ? null : UiToolDefaults.ISSUE_LIST_OUTPUT_SCHEMA;
  }

  @Override
  public ObjectNode structuredContent(
      Map<String, Object> args, String executeResult, String jiraUsername, String jiraUserDisplay) {
    if (ui == null || ui.contextBuilder == null || executeResult == null) return null;
    return ui.contextBuilder.build(name(), executeResult, jiraUsername, jiraUserDisplay);
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    StringBuilder query = new StringBuilder();
    String sep = "?";
    if (args.fields() != null && !args.fields().isBlank()) {
      query.append(sep).append("fields=").append(encode(args.fields()));
      sep = "&";
    }
    if (args.expand() != null && !args.expand().isBlank()) {
      query.append(sep).append("expand=").append(encode(args.expand()));
      sep = "&";
    }
    if (args.properties() != null && !args.properties().isBlank()) {
      query.append(sep).append("properties=").append(encode(args.properties()));
      sep = "&";
    }
    query.append(sep).append("updateHistory=").append(args.updateHistory());

    String response =
        client.get("/rest/api/2/issue/" + args.issueKey() + query, context.authHeader());
    return trimComments(response, Math.min(args.commentLimit(), MAX_COMMENTS));
  }

  /**
   * Keeps only the most recent {@code limit} comments. Jira embeds the whole comment list in the
   * issue resource and accepts no limiting parameter there, so an issue with hundreds of comments
   * would otherwise blow the response budget. A limit of 0 drops the comments entirely; a response
   * without a comment field is returned untouched.
   */
  static String trimComments(String response, int limit) throws McpToolException {
    JsonNode root;
    try {
      root = MAPPER.readTree(response);
    } catch (IOException e) {
      return response;
    }
    JsonNode comment = root.path("fields").path("comment");
    if (!comment.isObject() || !comment.path("comments").isArray()) {
      return response;
    }

    ArrayNode all = (ArrayNode) comment.get("comments");
    if (all.size() <= limit) return response;

    ArrayNode kept = MAPPER.createArrayNode();
    for (int i = Math.max(0, all.size() - limit); i < all.size(); i++) {
      kept.add(all.get(i));
    }
    ObjectNode commentNode = (ObjectNode) comment;
    commentNode.set("comments", kept);
    commentNode.put("returned", kept.size());

    try {
      return MAPPER.writeValueAsString(root);
    } catch (IOException e) {
      throw new McpToolException("Failed to serialize trimmed issue: " + e.getMessage());
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
