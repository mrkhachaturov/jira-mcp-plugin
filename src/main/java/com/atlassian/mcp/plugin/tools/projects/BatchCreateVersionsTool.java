package com.atlassian.mcp.plugin.tools.projects;

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

public class BatchCreateVersionsTool extends TypedTool<BatchCreateVersionsTool.Args> {

  public record NewVersion(
      @ToolArg(value = CreateVersionTool.NAME, required = true) String name,
      @ToolArg(CreateVersionTool.START_DATE) String startDate,
      @ToolArg(CreateVersionTool.RELEASE_DATE) String releaseDate,
      @ToolArg(CreateVersionTool.DESCRIPTION) String description) {}

  public record Args(
      @ToolArg(value = CreateVersionTool.PROJECT_KEY, required = true) String projectKey,
      @ToolArg(value = "The versions to create in that project", required = true)
          List<NewVersion> versions) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public BatchCreateVersionsTool(JiraRestClient client) {
    super(Args.class);
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
  protected String run(Args args, McpContext context) throws McpToolException {
    int total = args.versions().size();
    List<Map<String, Object>> created = new ArrayList<>();
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

      NewVersion version = args.versions().get(i);
      context.reportProgress(
          i, total, "Creating version " + (i + 1) + " of " + total + ": " + version.name());

      try {
        String body =
            mapper.writeValueAsString(
                CreateVersionTool.versionBody(
                    args.projectKey(),
                    version.name(),
                    version.startDate(),
                    version.releaseDate(),
                    version.description()));
        String result = client.post("/rest/api/2/version", body, context.authHeader());
        created.add(mapper.readValue(result, new TypeReference<Map<String, Object>>() {}));
      } catch (Exception e) {
        errors.add(
            Map.of("index", i, "name", version.name(), "error", String.valueOf(e.getMessage())));
      }
    }

    context.reportProgress(
        processed,
        total,
        (stopped == null ? "Completed: " : "Stopped: ")
            + created.size()
            + " created, "
            + errors.size()
            + " errors");

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("created", created.size());
    result.put("errors", errors.size());
    result.put("versions", created);
    if (!errors.isEmpty()) result.put("failed", errors);
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
}
