package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetServiceDeskForProjectTool extends TypedTool<GetServiceDeskForProjectTool.Args> {

  public record Args(
      @ToolArg(value = "Jira project key, e.g. 'SUP'", required = true) String projectKey) {}

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JiraRestClient client;

  public GetServiceDeskForProjectTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_service_desk_for_project";
  }

  @Override
  public String description() {
    return "Get the Jira Service Desk that serves a project, selected by project key. Returns that"
        + " one service desk, whose id the other service desk tools take. Server/Data Center only.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  public String requiredPluginKey() {
    return "com.atlassian.servicedesk";
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    List<String> known = new ArrayList<>();
    int page = GetServiceDeskQueuesTool.MAX_PAGE_SIZE;
    int start = 0;
    boolean more = true;

    while (more) {
      JsonNode listing =
          read(
              client.get(
                  "/rest/servicedeskapi/servicedesk?start=" + start + "&limit=" + page,
                  context.authHeader()));

      JsonNode desks = listing.path("values");
      for (JsonNode desk : desks) {
        String key = desk.path("projectKey").asText("");
        if (args.projectKey().equalsIgnoreCase(key)) return desk.toString();
        known.add(key);
      }

      more = desks.isArray() && !desks.isEmpty() && !listing.path("isLastPage").asBoolean(true);
      start += page;
    }

    throw new McpToolException(
        "No service desk serves project '"
            + args.projectKey()
            + "'. Projects with a service desk: "
            + String.join(", ", known));
  }

  private static JsonNode read(String json) throws McpToolException {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new McpToolException(
          "The service desk API returned an unreadable list: " + e.getMessage());
    }
  }
}
