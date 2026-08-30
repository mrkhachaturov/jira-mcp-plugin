package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;

public class GetQueueIssuesTool extends TypedTool<GetQueueIssuesTool.Args> {

  public record Args(
      @ToolArg(
              value =
                  "The id of the service desk, e.g. 4. get_service_desk_for_project turns a"
                      + " project key into one.",
              required = true)
          long serviceDeskId,
      @ToolArg(value = "The id of the queue, e.g. 47", required = true) long queueId,
      @ToolArg(value = "Index of the first issue to return, counting from 0", defaultValue = "0")
          int startAt,
      @ToolArg(
              value =
                  "Maximum number of issues to return, from 1 to "
                      + GetServiceDeskQueuesTool.PAGE_LIMIT,
              defaultValue = GetServiceDeskQueuesTool.PAGE_LIMIT)
          int limit) {}

  private final JiraRestClient client;

  public GetQueueIssuesTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_queue_issues";
  }

  @Override
  public String description() {
    return "List the issues sitting in a Jira Service Desk queue. Server/Data Center only.";
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
            + "/queue/"
            + args.queueId()
            + "/issue?start="
            + args.startAt()
            + "&limit="
            + GetServiceDeskQueuesTool.clampToPage(args.limit()),
        context.authHeader());
  }
}
