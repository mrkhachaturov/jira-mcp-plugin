package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.IconConstants;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.atlassian.mcp.plugin.tools.UiBinding;
import com.atlassian.mcp.plugin.tools.UiToolDefaults;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueTool extends DeclarativeTool {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String DEFAULT_FIELDS =
      "summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment,"
          + "labels,components,fixVersions,resolution,subtasks,issuelinks,attachment,parent";

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> FIELDS =
      ToolParam.string(
              "fields",
              "(Optional) Comma-separated list of fields to return (e.g.,"
                  + " 'summary,status,customfield_10010'). You may also provide a single field as a"
                  + " string (e.g., 'duedate'). Use '*all' for all fields (including custom"
                  + " fields), or omit for essential fields only.")
          .withDefault(DEFAULT_FIELDS);
  private static final ToolParam<String> EXPAND =
      ToolParam.string(
          "expand",
          "(Optional) Fields to expand. Examples: 'renderedFields' (for rendered content),"
              + " 'transitions' (for available status transitions), 'changelog' (for history)");
  private static final ToolParam<Integer> COMMENT_LIMIT =
      ToolParam.integer(
              "comment_limit", "Maximum number of comments to include (0 or null for no comments)")
          .withDefault(10);
  private static final ToolParam<String> PROPERTIES =
      ToolParam.string(
          "properties", "(Optional) A comma-separated list of issue properties to return");
  private static final ToolParam<Boolean> UPDATE_HISTORY =
      ToolParam.bool(
              "update_history", "Whether to update the issue view history for the requesting user")
          .withDefault(true);

  private static final int MAX_COMMENTS = 100;

  private final JiraRestClient client;
  private final UiBinding ui;

  public GetIssueTool(JiraRestClient client) {
    this(client, null);
  }

  public GetIssueTool(JiraRestClient client, UiBinding ui) {
    this.client = client;
    this.ui = ui;
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
  public String name() {
    return "get_issue";
  }

  @Override
  public String description() {
    return "Get details of a specific Jira issue including its Epic links and relationship information.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, FIELDS, EXPAND, COMMENT_LIMIT, PROPERTIES, UPDATE_HISTORY);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String fields = args.get(FIELDS);
    String expand = args.get(EXPAND);
    int commentLimit = Math.min(args.get(COMMENT_LIMIT), MAX_COMMENTS);
    String properties = args.get(PROPERTIES);
    boolean updateHistory = args.get(UPDATE_HISTORY);

    StringBuilder query = new StringBuilder();
    String sep = "?";
    if (fields != null && !fields.isBlank()) {
      query.append(sep).append("fields=").append(encode(fields));
      sep = "&";
    }
    if (expand != null && !expand.isBlank()) {
      query.append(sep).append("expand=").append(encode(expand));
      sep = "&";
    }
    if (properties != null && !properties.isBlank()) {
      query.append(sep).append("properties=").append(encode(properties));
      sep = "&";
    }
    query.append(sep).append("updateHistory=").append(updateHistory);

    String response = client.get("/rest/api/2/issue/" + issueKey + query, authHeader);
    return trimComments(response, commentLimit);
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
