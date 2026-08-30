package com.atlassian.mcp.plugin.tools.projects;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateVersionTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "Jira project key (e.g., 'PROJ', 'ACV2')").required();
  private static final ToolParam<String> NAME =
      ToolParam.string("name", "Name of the version").required();
  private static final ToolParam<String> START_DATE =
      ToolParam.string("start_date", "Start date (YYYY-MM-DD)");
  private static final ToolParam<String> RELEASE_DATE =
      ToolParam.string("release_date", "Release date (YYYY-MM-DD)");
  private static final ToolParam<String> DESCRIPTION =
      ToolParam.string("description", "Description of the version");

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public CreateVersionTool(JiraRestClient client) {
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

  @Override
  public List<ToolParam<?>> params() {
    return List.of(PROJECT_KEY, NAME, START_DATE, RELEASE_DATE, DESCRIPTION);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String projectKey = args.require(PROJECT_KEY);
    String name = args.require(NAME);
    String startDate = args.get(START_DATE);
    String releaseDate = args.get(RELEASE_DATE);
    String description = args.get(DESCRIPTION);

    // Jira's version resource names the owning project "project" and takes its key there; the
    // camelCase date fields are the only ones it recognises.
    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("project", projectKey);
    requestBody.put("name", name);
    if (startDate != null) requestBody.put("startDate", startDate);
    if (releaseDate != null) requestBody.put("releaseDate", releaseDate);
    if (description != null) requestBody.put("description", description);
    try {
      String jsonBody = mapper.writeValueAsString(requestBody);
      return client.post("/rest/api/2/version", jsonBody, authHeader);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }
  }
}
