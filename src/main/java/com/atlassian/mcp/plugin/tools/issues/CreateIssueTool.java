package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateIssueTool extends TypedTool<CreateIssueTool.Args> {

  public record Args(
      @ToolArg(
              value =
                  "The JIRA project key (e.g. 'PROJ', 'DEV', 'ACV2'). This is the prefix of issue"
                      + " keys in your project. Never assume what it might be, always ask the user.",
              required = true)
          String projectKey,
      @ToolArg(value = "Summary/title of the issue", required = true) String summary,
      @ToolArg(
              value =
                  "Issue type (e.g. 'Task', 'Bug', 'Story', 'Epic', 'Subtask'). The available types"
                      + " depend on your project configuration. For subtasks, use 'Subtask' (not"
                      + " 'Sub-task') and include parent in additional_fields.",
              required = true)
          String issueType,
      @ToolArg(
              "(Optional) Assignee's user identifier: email, display name, or account ID (e.g."
                  + " 'user@example.com', 'John Doe', 'accountid:...')")
          String assignee,
      @ToolArg("Issue description in Markdown format") String description,
      @ToolArg("(Optional) Component names to assign, e.g. ['Frontend', 'API']")
          List<String> components,
      @ToolArg(
              "(Optional) Additional fields to set. Examples: set priority with {\"priority\":"
                  + " {\"name\": \"High\"}}, add labels with {\"labels\": [\"frontend\","
                  + " \"urgent\"]}, link to a parent with {\"parent\": \"PROJ-123\"}, link to an"
                  + " epic with {\"epicKey\": \"EPIC-123\"}, set fix versions with"
                  + " {\"fixVersions\": [{\"id\": \"10020\"}]}, or set a custom field with"
                  + " {\"customfield_10010\": \"value\"}")
          Map<String, Object> additionalFields) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateIssueTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "create_issue";
  }

  @Override
  public String description() {
    return "Create a new Jira issue with optional Epic link or parent for subtasks.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("project", Map.of("key", args.projectKey()));
    fields.put("summary", args.summary());
    fields.put("issuetype", Map.of("name", args.issueType()));

    if (args.description() != null) {
      fields.put("description", JiraMarkupConverter.markdownToJira(args.description()));
    }
    if (args.assignee() != null) {
      fields.put("assignee", Map.of("name", args.assignee()));
    }
    if (args.components() != null && !args.components().isEmpty()) {
      fields.put("components", args.components().stream().map(c -> Map.of("name", c)).toList());
    }
    if (args.additionalFields() != null) {
      fields.putAll(args.additionalFields());
    }

    try {
      String body = mapper.writeValueAsString(Map.of("fields", fields));
      String created = client.post("/rest/api/2/issue", body, context.authHeader());
      JsonNode node = mapper.readTree(created);
      String key = node.path("key").asText(null);
      if (key == null) return created;

      // The create response carries only id/key/self, so re-read the issue for the full payload.
      String issue = client.get("/rest/api/2/issue/" + key, context.authHeader());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", true);
      result.put("issue", mapper.readTree(issue));
      return mapper.writeValueAsString(result);
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException("Failed to create issue: " + e.getMessage());
    }
  }
}
