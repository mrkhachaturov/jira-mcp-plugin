package com.atlassian.mcp.plugin.tools.servicedesk;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import java.util.List;

public class GetQueueIssuesTool extends DeclarativeTool {

  private static final ToolParam<String> SERVICE_DESK_ID =
      ToolParam.string("service_desk_id", "Service desk ID (e.g., '4')").required();
  private static final ToolParam<String> QUEUE_ID =
      ToolParam.string("queue_id", "Queue ID (e.g., '47')").required();
  private static final ToolParam<Integer> START_AT =
      ToolParam.integer("start_at", "Starting index for pagination (0-based)").withDefault(0);
  private static final ToolParam<Integer> LIMIT =
      ToolParam.integer("limit", "Maximum number of results (1-50)").withDefault(50);

  private final JiraRestClient client;

  public GetQueueIssuesTool(JiraRestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return "get_queue_issues";
  }

  @Override
  public String description() {
    return "Get issues from a Jira Service Desk queue. Server/Data Center only. Not available on Jira Cloud.";
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
  public List<ToolParam<?>> params() {
    return List.of(SERVICE_DESK_ID, QUEUE_ID, START_AT, LIMIT);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String serviceDeskId = args.require(SERVICE_DESK_ID);
    String queueId = args.require(QUEUE_ID);
    int startAt = args.get(START_AT);
    int limit = args.get(LIMIT);

    // The ServiceDesk API pages with start/limit, not the platform API's startAt/maxResults.
    return client.get(
        "/rest/servicedeskapi/servicedesk/"
            + serviceDeskId
            + "/queue/"
            + queueId
            + "/issue?start="
            + startAt
            + "&limit="
            + limit,
        authHeader);
  }
}
