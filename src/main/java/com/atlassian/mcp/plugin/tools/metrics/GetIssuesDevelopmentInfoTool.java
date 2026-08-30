package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.BatchProgressBridge;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Batch version of get_issue_development_info, driven one issue at a time. */
public class GetIssuesDevelopmentInfoTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEYS =
      ToolParam.string(
              "issue_keys", "Comma-separated list of Jira issue keys (e.g., 'PROJ-123,PROJ-456')")
          .required();
  private static final ToolParam<String> APPLICATION_TYPE =
      ToolParam.string(
          "application_type",
          "(Optional) Filter by application type. Examples: 'stash' (Bitbucket Server),"
              + " 'bitbucket', 'github', 'gitlab'");
  private static final ToolParam<String> DATA_TYPE =
      ToolParam.string(
          "data_type",
          "(Optional) Filter by data type. Examples: 'pullrequest', 'branch', 'repository'");

  private final GetIssueDevelopmentInfoTool singleTool;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssuesDevelopmentInfoTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEYS, APPLICATION_TYPE, DATA_TYPE);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    return run(args, authHeader, (current, total, message) -> {});
  }

  @Override
  public String executeWithSdkProgress(
      Map<String, Object> args,
      String authHeader,
      McpSyncServerExchange exchange,
      Object progressToken)
      throws McpToolException {
    return run(
        new ToolArgs(params(), args),
        authHeader,
        BatchProgressBridge.bridge(exchange, progressToken));
  }

  @Override
  public String executeWithProgress(
      Map<String, Object> args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    return run(new ToolArgs(params(), args), authHeader, progress);
  }

  private String run(ToolArgs args, String authHeader, ProgressCallback progress)
      throws McpToolException {
    String issueKeys = args.require(ISSUE_KEYS);
    String applicationType = args.get(APPLICATION_TYPE);
    String dataType = args.get(DATA_TYPE);

    String[] keys = issueKeys.split(",");
    List<String> trimmedKeys = new ArrayList<>();
    for (String k : keys) {
      String t = k.trim();
      if (!t.isEmpty()) trimmedKeys.add(t);
    }

    int total = trimmedKeys.size();
    List<Object> results = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      String key = trimmedKeys.get(i);
      progress.report(
          i, total, "Fetching dev info for " + key + " (" + (i + 1) + "/" + total + ")");

      try {
        // The single-issue tool owns the numeric-ID resolution and the per-application probing.
        Map<String, Object> singleArgs = new LinkedHashMap<>();
        singleArgs.put("issue_key", key);
        if (applicationType != null) singleArgs.put("application_type", applicationType);
        if (dataType != null) singleArgs.put("data_type", dataType);

        String devInfo = singleTool.execute(singleArgs, authHeader);
        results.add(mapper.readTree(devInfo));
      } catch (Exception e) {
        Map<String, Object> errorResult = new LinkedHashMap<>();
        errorResult.put("issue_key", key);
        errorResult.put("error", e.getMessage());
        errorResult.put("pullRequests", List.of());
        errorResult.put("branches", List.of());
        errorResult.put("commits", List.of());
        results.add(errorResult);
      }
    }

    progress.report(total, total, "Completed: " + total + " issues processed");

    try {
      return mapper.writeValueAsString(results);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize results: " + e.getMessage());
    }
  }
}
