package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public class CreateVersionTool extends TypedTool<CreateVersionTool.Args> {

  static final String PROJECT_KEY = "Jira project key (e.g. 'PROJ', 'ACV2')";
  static final String NAME = "Name of the version, e.g. 'v1.0'";
  static final String START_DATE = "(Optional) Start date, as YYYY-MM-DD";
  static final String RELEASE_DATE = "(Optional) Release date, as YYYY-MM-DD";
  static final String DESCRIPTION = "(Optional) Description of the version";

  public record Args(
      @ToolArg(value = PROJECT_KEY, required = true) String projectKey,
      @ToolArg(value = NAME, required = true) String name,
      @ToolArg(START_DATE) String startDate,
      @ToolArg(RELEASE_DATE) String releaseDate,
      @ToolArg(DESCRIPTION) String description) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateVersionTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "create_version";
  }

  @Override
  public String description() {
    return "Create a new fix version in a Jira project.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  /**
   * The body Jira's version resource accepts: it names the owning project "project" and takes its
   * key there, and the camelCase date fields are the only ones it recognises. Shared with {@code
   * batch_create_versions} so both tools describe a version to Jira the same way.
   */
  static Map<String, Object> versionBody(
      String projectKey, String name, String startDate, String releaseDate, String description) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("project", projectKey);
    body.put("name", name);
    if (startDate != null) body.put("startDate", startDate);
    if (releaseDate != null) body.put("releaseDate", releaseDate);
    if (description != null) body.put("description", description);
    return body;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    Map<String, Object> requestBody =
        versionBody(
            args.projectKey(),
            args.name(),
            args.startDate(),
            args.releaseDate(),
            args.description());

    String body;
    try {
      body = mapper.writeValueAsString(requestBody);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
    return client.post("/rest/api/2/version", body, context.authHeader());
  }
}
