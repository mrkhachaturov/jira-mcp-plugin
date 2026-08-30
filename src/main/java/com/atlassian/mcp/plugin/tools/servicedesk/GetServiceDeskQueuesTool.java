package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetServiceDeskQueuesTool extends TypedTool<GetServiceDeskQueuesTool.Args> {

  /** The service desk API serves at most this many rows per page, whatever limit is asked for. */
  static final String PAGE_LIMIT = "50";

  static final int MAX_PAGE_SIZE = Integer.parseInt(PAGE_LIMIT);

  public record Args(
      @ToolArg(
              value =
                  "The id of the service desk, e.g. 4. get_service_desk_for_project turns a"
                      + " project key into one.",
              required = true)
          long serviceDeskId,
      @ToolArg(value = "Index of the first queue to return, counting from 0", defaultValue = "0")
          int startAt,
      @ToolArg(
              value = "Maximum number of queues to return, from 1 to " + PAGE_LIMIT,
              defaultValue = PAGE_LIMIT)
          int limit) {}

  private final JiraRestClient client;

  public GetServiceDeskQueuesTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_service_desk_queues";
  }

  @Override
  public String description() {
    return "List the queues of a Jira Service Desk. Server/Data Center only.";
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
    // The ServiceDesk API pages with start/limit, not the platform API's startAt/maxResults.
    return client.get(
        "/rest/servicedeskapi/servicedesk/"
            + args.serviceDeskId()
            + "/queue?start="
            + args.startAt()
            + "&limit="
            + clampToPage(args.limit()),
        context.authHeader());
  }

  static int clampToPage(int limit) {
    return Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
  }
}
