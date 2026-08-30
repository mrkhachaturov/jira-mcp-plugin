package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.BatchResult;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BatchCreateIssuesTool extends TypedTool<BatchCreateIssuesTool.Args> {

  public record NewIssue(
      @ToolArg(value = "The project key, e.g. 'PROJ'", required = true) String projectKey,
      @ToolArg(value = "Issue summary/title", required = true) String summary,
      @ToolArg(value = "Type of issue, e.g. 'Task' or 'Bug'", required = true) String issueType,
      @ToolArg("Issue description in Markdown format") String description,
      @ToolArg("Assignee username or email") String assignee,
      @ToolArg("Component names to assign, e.g. ['Frontend', 'API']") List<String> components) {}

  public record Args(
      @ToolArg(value = "The issues to create", required = true) List<NewIssue> issues,
      @ToolArg(
              value =
                  "If true, build and return each request body without sending anything to Jira."
                      + " Project-specific field configuration is still only checked on a real"
                      + " create.",
              defaultValue = "false")
          boolean validateOnly) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public BatchCreateIssuesTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    int total = args.issues().size();
    List<Object> succeeded = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    String stopped = null;
    int processed = 0;

    for (int i = 0; i < total; i++) {
      Optional<String> cancellation = context.cancellation();
      if (cancellation.isPresent()) {
        stopped = cancellation.get();
        break;
      }
      processed = i + 1;

      NewIssue issue = args.issues().get(i);
      String verb = args.validateOnly() ? "Validating" : "Creating";
      context.reportProgress(
          i, total, verb + " issue " + (i + 1) + " of " + total + ": " + issue.summary());

      try {
        String body = mapper.writeValueAsString(Map.of("fields", fieldsOf(issue)));
        if (args.validateOnly()) {
          succeeded.add(mapper.readTree(body));
        } else {
          String created = client.post("/rest/api/2/issue", body, context.authHeader());
          succeeded.add(mapper.readValue(created, new TypeReference<Map<String, Object>>() {}));
        }
      } catch (Exception e) {
        errors.add(
            Map.of(
                "index", i,
                "summary", issue.summary(),
                "error", String.valueOf(e.getMessage())));
      }
    }

    String done = args.validateOnly() ? " validated, " : " created, ";
    context.reportProgress(
        processed,
        total,
        (stopped == null ? "Completed: " : "Stopped: ")
            + succeeded.size()
            + done
            + errors.size()
            + " errors");

    Map<String, Object> result = new LinkedHashMap<>();
    if (args.validateOnly()) {
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
    if (stopped != null) {
      result.put(BatchResult.CANCELLED, true);
      result.put(BatchResult.CANCELLED_REASON, stopped);
      result.put(BatchResult.PROCESSED, processed);
      result.put(BatchResult.TOTAL, total);
    }

    try {
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize result: " + e.getMessage());
    }
  }

  private static Map<String, Object> fieldsOf(NewIssue issue) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("project", Map.of("key", issue.projectKey()));
    fields.put("summary", issue.summary());
    fields.put("issuetype", Map.of("name", issue.issueType()));

    if (issue.description() != null) {
      fields.put("description", JiraMarkupConverter.markdownToJira(issue.description()));
    }
    if (issue.assignee() != null) {
      fields.put("assignee", Map.of("name", issue.assignee()));
    }
    if (issue.components() != null && !issue.components().isEmpty()) {
      fields.put("components", issue.components().stream().map(c -> Map.of("name", c)).toList());
    }
    return fields;
  }
}
