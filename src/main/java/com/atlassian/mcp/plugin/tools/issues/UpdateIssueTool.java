package com.atlassian.mcp.plugin.tools.issues;

import com.atlassian.mcp.plugin.JiraMarkupConverter;
import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UpdateIssueTool extends TypedTool<UpdateIssueTool.Args> {

  public record Args(
      @ToolArg(value = "Jira issue key (e.g. 'PROJ-123', 'ACV2-642')", required = true)
          String issueKey,
      @ToolArg(
              value =
                  "The fields to set, as they are named in Jira. For 'assignee' provide the"
                      + " username, for 'description' provide Markdown. Examples: {\"summary\":"
                      + " \"New summary\"}, {\"priority\": {\"name\": \"High\"}}, {\"labels\":"
                      + " [\"urgent\"]}, {\"customfield_10010\": \"value\"}",
              required = true)
          Map<String, Object> fields,
      @ToolArg("(Optional) Component names to set, e.g. ['Frontend', 'API']")
          List<String> components) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public UpdateIssueTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "update_issue";
  }

  @Override
  public String description() {
    return "Update an existing Jira issue including changing status, adding Epic links, updating"
        + " fields, etc.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> fields = new LinkedHashMap<>(args.fields());

    if (fields.get("description") instanceof String description) {
      fields.put("description", JiraMarkupConverter.markdownToJira(description));
    }
    if (args.components() != null && !args.components().isEmpty()) {
      fields.put("components", args.components().stream().map(c -> Map.of("name", c)).toList());
    }

    String body;
    try {
      body = mapper.writeValueAsString(Map.of("fields", fields));
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }

    client.put("/rest/api/2/issue/" + args.issueKey(), body, context.authHeader());
    // Jira's PUT returns 204 with no body, so the updated issue has to be re-read.
    String updated = client.get("/rest/api/2/issue/" + args.issueKey(), context.authHeader());

    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("success", true);
      result.put("issue", mapper.readTree(updated));
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize result: " + e.getMessage());
    }
  }
}
