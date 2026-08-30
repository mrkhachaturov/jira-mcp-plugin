package com.atlassian.mcp.plugin.tools.projects;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchCreateVersionsTool extends DeclarativeTool {

  private static final ToolParam<String> PROJECT_KEY =
      ToolParam.string("project_key", "Jira project key (e.g., 'PROJ', 'ACV2')").required();
  private static final ToolParam<String> VERSIONS =
      ToolParam.string(
              "versions",
              "JSON array of version objects. Each object should contain: - name (required): Name"
                  + " of the version - startDate (optional): Start date (YYYY-MM-DD) - releaseDate"
                  + " (optional): Release date (YYYY-MM-DD) - description (optional): Description"
                  + " of the version Example: [ {\"name\": \"v1.0\", \"startDate\": \"2025-01-01\","
                  + " \"releaseDate\": \"2025-02-01\", \"description\": \"First release\"},"
                  + " {\"name\": \"v2.0\"} ]")
          .required();

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public BatchCreateVersionsTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "batch_create_versions";
  }

  @Override
  public String description() {
    return "Batch create multiple versions in a Jira project.";
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
    return List.of(PROJECT_KEY, VERSIONS);
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
    String projectKey = args.require(PROJECT_KEY);
    String versionsJson = args.require(VERSIONS);

    List<Map<String, Object>> versions;
    try {
      versions = mapper.readValue(versionsJson, new TypeReference<>() {});
    } catch (Exception e) {
      throw new McpToolException("Invalid versions JSON: " + e.getMessage());
    }

    int total = versions.size();
    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();

    for (int i = 0; i < total; i++) {
      Map<String, Object> version = versions.get(i);
      String name = (String) version.getOrDefault("name", "?");

      progress.report(i, total, "Creating version " + (i + 1) + " of " + total + ": " + name);

      try {
        // Jira's version resource names the owning project "project" and takes its key there.
        Map<String, Object> body = new LinkedHashMap<>(version);
        body.put("project", projectKey);
        String jsonBody = mapper.writeValueAsString(body);
        String result = client.post("/rest/api/2/version", jsonBody, authHeader);
        created.add(mapper.readValue(result, new TypeReference<Map<String, Object>>() {}));
      } catch (Exception e) {
        errors.add(Map.of("index", i, "name", name, "error", String.valueOf(e.getMessage())));
      }
    }

    progress.report(
        total, total, "Completed: " + created.size() + " created, " + errors.size() + " errors");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("created", created.size());
    result.put("errors", errors.size());
    result.put("versions", created);
    if (!errors.isEmpty()) result.put("failed", errors);

    try {
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize result: " + e.getMessage());
    }
  }
}
