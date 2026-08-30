package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.BatchProgressBridge;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchCreateIssuesTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUES =
      ToolParam.string(
              "issues",
              "JSON array of issue objects. Each object should contain: - project_key (required):"
                  + " The project key (e.g., 'PROJ') - summary (required): Issue summary/title -"
                  + " issue_type (required): Type of issue (e.g., 'Task', 'Bug') - description"
                  + " (optional): Issue description in Markdown format - assignee (optional):"
                  + " Assignee username or email - components (optional): Array of component names"
                  + " Example: [ {\"project_key\": \"PROJ\", \"summary\": \"Issue 1\","
                  + " \"issue_type\": \"Task\"}, {\"project_key\": \"PROJ\", \"summary\": \"Issue"
                  + " 2\", \"issue_type\": \"Bug\", \"components\": [\"Frontend\"]} ]")
          .required();
  private static final ToolParam<Boolean> VALIDATE_ONLY =
      ToolParam.bool(
              "validate_only",
              "If true, only validates the issues without creating them — each entry is checked for"
                  + " the required project_key, summary and issue_type and the request body is"
                  + " built, but nothing is sent to Jira")
          .withDefault(false);

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public BatchCreateIssuesTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "batch_create_issues";
  }

  @Override
  public String description() {
    return "Create multiple Jira issues in a batch.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  public boolean supportsProgress() {
    return true;
  }

  @Override
  public List<ToolParam<?>> params() {
    return List.of(ISSUES, VALIDATE_ONLY);
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
    String issuesJson = args.require(ISSUES);
    boolean validateOnly = args.get(VALIDATE_ONLY);

    List<Map<String, Object>> issues;
    try {
      issues = mapper.readValue(issuesJson, new TypeReference<>() {});
    } catch (Exception e) {
      throw new McpToolException("Invalid issues JSON: " + e.getMessage());
    }

    int total = issues.size();
    List<Object> succeeded = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      Map<String, Object> issue = issues.get(i);
      String summary = (String) issue.getOrDefault("summary", "?");
      String verb = validateOnly ? "Validating" : "Creating";

      progress.report(i, total, verb + " issue " + (i + 1) + " of " + total + ": " + summary);

      try {
        Map<String, Object> fields = new HashMap<>();
        fields.put("project", Map.of("key", requireEntry(issue, "project_key")));
        fields.put("summary", requireEntry(issue, "summary"));
        fields.put("issuetype", Map.of("name", requireEntry(issue, "issue_type")));

        if (issue.containsKey("description")) fields.put("description", issue.get("description"));
        if (issue.containsKey("assignee")) {
          fields.put("assignee", Map.of("name", issue.get("assignee")));
        }

        String body = mapper.writeValueAsString(Map.of("fields", fields));
        if (validateOnly) {
          // Jira DC exposes no server-side dry run for issue creation, so validation stops at the
          // request body we would have sent — an accepted entry here can still be rejected by
          // project-specific field configuration on a real create.
          succeeded.add(mapper.readTree(body));
        } else {
          String result = client.post("/rest/api/2/issue", body, authHeader);
          succeeded.add(mapper.readValue(result, new TypeReference<Map<String, Object>>() {}));
        }
      } catch (Exception e) {
        errors.add(
            Map.of(
                "index", i,
                "summary", summary,
                "error", String.valueOf(e.getMessage())));
      }
    }

    String done = validateOnly ? " validated, " : " created, ";
    progress.report(
        total, total, "Completed: " + succeeded.size() + done + errors.size() + " errors");

    Map<String, Object> result = new LinkedHashMap<>();
    if (validateOnly) {
      result.put("validate_only", true);
      result.put("valid", succeeded.size());
    } else {
      result.put("created", succeeded.size());
    }
    result.put("errors", errors.size());
    result.put("issues", succeeded);
    if (!errors.isEmpty()) {
      result.put("failed", errors);
    }

    try {
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize result: " + e.getMessage());
    }
  }

  private static Object requireEntry(Map<String, Object> issue, String key)
      throws McpToolException {
    Object value = issue.get(key);
    if (value == null || (value instanceof String s && s.isBlank())) {
      throw new McpToolException("missing required '" + key + "'");
    }
    return value;
  }
}
