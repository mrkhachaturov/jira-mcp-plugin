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

public class CreateIssueLinkTool extends TypedTool<CreateIssueLinkTool.Args> {

  public record Args(
      @ToolArg(
              value = "The type of link to create (e.g. 'Duplicate', 'Blocks', 'Relates')",
              required = true)
          String linkType,
      @ToolArg(value = "The key of the inward issue (e.g. 'PROJ-123')", required = true)
          String inwardIssueKey,
      @ToolArg(value = "The key of the outward issue (e.g. 'PROJ-456')", required = true)
          String outwardIssueKey,
      @ToolArg("(Optional) Comment to add to the link") String comment,
      @ToolArg(
              "(Optional) Visibility of the comment, e.g. {\"type\": \"group\", \"value\":"
                  + " \"jira-users\"}")
          Map<String, Object> commentVisibility) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateIssueLinkTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    // Jira's LinkIssueRequestJsonBean accepts only type/inwardIssue/outwardIssue/comment and
    // rejects anything else outright, so the flat parameters are rebuilt into that shape.
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("type", Map.of("name", args.linkType()));
    requestBody.put("inwardIssue", Map.of("key", args.inwardIssueKey()));
    requestBody.put("outwardIssue", Map.of("key", args.outwardIssueKey()));
    if (args.comment() != null) {
      Map<String, Object> comment = new LinkedHashMap<>();
      comment.put("body", args.comment());
      if (args.commentVisibility() != null) {
        comment.put("visibility", args.commentVisibility());
      }
      requestBody.put("comment", comment);
    }

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post("/rest/api/2/issueLink", body, context.authHeader());
  }
}
