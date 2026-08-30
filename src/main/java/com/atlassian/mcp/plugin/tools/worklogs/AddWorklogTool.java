package com.atlassian.mcp.plugin.tools.worklogs;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class AddWorklogTool extends TypedTool<AddWorklogTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              value =
                  "Time spent in Jira format. Examples: '1h 30m' (1 hour and 30 minutes), '1d' (1"
                      + " day), '30m' (30 minutes), '4h' (4 hours)",
              required = true)
          String timeSpent,
      @ToolArg("(Optional) Comment for the worklog in Markdown format") String comment,
      @ToolArg(
              "(Optional) Start time in ISO format. If not provided, the current time will be used."
                  + " Example: '2023-08-01T12:00:00.000+0000'")
          String started,
      @ToolArg(
              "(Optional) Remaining estimate to leave on the issue once the work is logged, in Jira"
                  + " format, e.g. '2d 4h'. Omit to let Jira reduce the existing estimate by the"
                  + " time spent.")
          String remainingEstimate) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddWorklogTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "add_worklog";
  }

  @Override
  public String description() {
    return "Add a worklog entry to a Jira issue.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("timeSpent", args.timeSpent());
    if (args.comment() != null) {
      requestBody.put("comment", JiraMarkupConverter.markdownToJira(args.comment()));
    }
    if (args.started() != null) {
      requestBody.put("started", args.started());
    }

    // The remaining estimate is not part of the worklog itself: the resource takes it as
    // adjustEstimate=new plus the new value in newEstimate.
    String query = "";
    if (args.remainingEstimate() != null) {
      query =
          "?adjustEstimate=new&newEstimate="
              + URLEncoder.encode(args.remainingEstimate(), StandardCharsets.UTF_8);
    }

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }

    return client.post(
        "/rest/api/2/issue/" + args.issueKey() + "/worklog" + query, body, context.authHeader());
  }
}
