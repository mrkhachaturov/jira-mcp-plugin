package com.atlassian.mcp.plugin.tools.worklogs;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddWorklogTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')").required();
  private static final ToolParam<String> TIME_SPENT =
      ToolParam.string(
              "time_spent",
              "Time spent in Jira format. Examples: '1h 30m' (1 hour and 30 minutes), '1d' (1 day),"
                  + " '30m' (30 minutes), '4h' (4 hours)")
          .required();
  private static final ToolParam<String> COMMENT =
      ToolParam.string("comment", "(Optional) Comment for the worklog in Markdown format");
  private static final ToolParam<String> STARTED =
      ToolParam.string(
          "started",
          "(Optional) Start time in ISO format. If not provided, the current time will be used."
              + " Example: '2023-08-01T12:00:00.000+0000'");
  private static final ToolParam<String> ORIGINAL_ESTIMATE =
      ToolParam.string("original_estimate", "(Optional) New value for the original estimate");
  private static final ToolParam<String> REMAINING_ESTIMATE =
      ToolParam.string("remaining_estimate", "(Optional) New value for the remaining estimate");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public AddWorklogTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, TIME_SPENT, COMMENT, STARTED, ORIGINAL_ESTIMATE, REMAINING_ESTIMATE);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String timeSpent = args.require(TIME_SPENT);
    String comment = args.get(COMMENT);
    String started = args.get(STARTED);
    // Jira's worklog resource carries no original-estimate field and rejects any body property it
    // does not know, so this value has nowhere to go on this call.
    args.get(ORIGINAL_ESTIMATE);
    String remainingEstimate = args.get(REMAINING_ESTIMATE);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("timeSpent", timeSpent);
    if (comment != null) requestBody.put("comment", comment);
    if (started != null) requestBody.put("started", started);

    // The remaining estimate is not part of the worklog itself: the resource takes it as
    // adjustEstimate=new plus the new value in newEstimate.
    String query = "";
    if (remainingEstimate != null) {
      query =
          "?adjustEstimate=new&newEstimate="
              + URLEncoder.encode(remainingEstimate, StandardCharsets.UTF_8);
    }

    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post(
          "/rest/api/2/issue/" + issueKey + "/worklog" + query, jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
