package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LinkToEpicTool extends TypedTool<LinkToEpicTool.Args> {

  public record Args(
      @ToolArg(value = "The key of the issue to link (e.g. 'PROJ-123')", required = true)
          String issueKey,
      @ToolArg(value = "The key of the epic to link it to (e.g. 'PROJ-456')", required = true)
          String epicKey) {}

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public LinkToEpicTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "link_to_epic";
  }

  @Override
  public String description() {
    return "Link an existing issue to an epic.";
  }

  @Override
  public boolean isWriteTool() {
    return true;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    String body;
    try {
      body = mapper.writeValueAsString(Map.of("issues", List.of(args.issueKey())));
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize request: " + e.getMessage());
    }

    client.post("/rest/agile/1.0/epic/" + args.epicKey() + "/issue", body, context.authHeader());
    String updatedIssue = client.get("/rest/api/2/issue/" + args.issueKey(), context.authHeader());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put(
        "message", "Issue " + args.issueKey() + " has been linked to epic " + args.epicKey() + ".");
    try {
      result.put("issue", mapper.readTree(updatedIssue));
      return mapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new McpToolException("Failed to serialize result: " + e.getMessage());
    }
  }
}
