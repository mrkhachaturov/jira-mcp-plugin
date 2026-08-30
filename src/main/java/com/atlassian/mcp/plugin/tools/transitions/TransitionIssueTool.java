package com.atlassian.mcp.plugin.tools.transitions;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransitionIssueTool extends TypedTool<TransitionIssueTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              value =
                  "Id of the transition to perform. Call get_transitions first for the ids"
                      + " available on this issue, e.g. 11, 21, 31.",
              required = true)
          long transitionId,
      @ToolArg(
              "(Optional) Fields to set during the transition. Some transitions require one, for"
                  + " example {\"resolution\": {\"name\": \"Fixed\"}}.")
          Map<String, Object> fields,
      @ToolArg(
              "(Optional) Comment to add during the transition, in Markdown. It is visible in the"
                  + " issue history.")
          String comment) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public TransitionIssueTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "transition_issue";
  }

  @Override
  public String description() {
    return "Transition a Jira issue to a new status.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("transition", Map.of("id", String.valueOf(args.transitionId())));

    if (args.fields() != null && !args.fields().isEmpty()) {
      requestBody.put("fields", args.fields());
    }
    if (args.comment() != null) {
      requestBody.put(
          "update",
          Map.of(
              "comment",
              List.of(
                  Map.of(
                      "add", Map.of("body", JiraMarkupConverter.markdownToJira(args.comment()))))));
    }

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize the transition request: " + e.getMessage());
    }

    client.post(
        "/rest/api/2/issue/" + args.issueKey() + "/transitions", body, context.authHeader());
    // The transition answers 204 with no body, so the issue is re-read for its new state.
    return client.get("/rest/api/2/issue/" + args.issueKey(), context.authHeader());
  }
}
