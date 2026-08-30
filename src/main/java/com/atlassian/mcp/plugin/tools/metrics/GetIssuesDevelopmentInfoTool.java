package com.atlassian.mcp.plugin.tools.metrics;

import static com.atlassian.mcp.plugin.tools.metrics.GetIssueDevelopmentInfoTool.APPLICATION_TYPE_DESCRIPTION;
import static com.atlassian.mcp.plugin.tools.metrics.GetIssueDevelopmentInfoTool.BRANCH;
import static com.atlassian.mcp.plugin.tools.metrics.GetIssueDevelopmentInfoTool.DATA_TYPE_DESCRIPTION;
import static com.atlassian.mcp.plugin.tools.metrics.GetIssueDevelopmentInfoTool.PULL_REQUEST;
import static com.atlassian.mcp.plugin.tools.metrics.GetIssueDevelopmentInfoTool.REPOSITORY;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch version of get_issue_development_info, driven one issue at a time. */
public class GetIssuesDevelopmentInfoTool extends TypedTool<GetIssuesDevelopmentInfoTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue keys to look up, e.g. ['PROJ-123', 'PROJ-456']", required = true)
          List<String> issueKeys,
      @ToolArg(APPLICATION_TYPE_DESCRIPTION) String applicationType,
      @ToolArg(
              value = DATA_TYPE_DESCRIPTION,
              allowed = {PULL_REQUEST, BRANCH, REPOSITORY})
          String dataType) {}

  private final GetIssueDevelopmentInfoTool singleTool;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssuesDevelopmentInfoTool(JiraRestClient client) {
    super(Args.class);
    this.singleTool = new GetIssueDevelopmentInfoTool(client);
  }

  @Override
  public String name() {
    return "get_issues_development_info";
  }

  @Override
  public String description() {
    return "Get development information for multiple Jira issues. Batch retrieves development panel information (PRs, commits, branches) for multiple issues at once.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public boolean supportsProgress() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    if (args.issueKeys().isEmpty()) {
      throw new McpToolException("'issue_keys' must name at least one issue");
    }

    int total = args.issueKeys().size();
    List<Object> results = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      String key = args.issueKeys().get(i);
      context.reportProgress(
          i, total, "Fetching dev info for " + key + " (" + (i + 1) + "/" + total + ")");

      try {
        // The single-issue tool owns the numeric-ID resolution and the per-application probing.
        String devInfo =
            singleTool.run(
                new GetIssueDevelopmentInfoTool.Args(key, args.applicationType(), args.dataType()),
                context);
        results.add(mapper.readTree(devInfo));
      } catch (Exception e) {
        Map<String, Object> errorResult = new LinkedHashMap<>();
        errorResult.put("issue_key", key);
        errorResult.put("error", e.getMessage());
        results.add(errorResult);
      }
    }

    context.reportProgress(total, total, "Completed: " + total + " issues processed");

    try {
      return mapper.writeValueAsString(results);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize results: " + e.getMessage());
    }
  }
}
