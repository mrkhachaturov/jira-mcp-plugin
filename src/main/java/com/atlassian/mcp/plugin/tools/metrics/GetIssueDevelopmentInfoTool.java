package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpContext;
import com.atlassian.mcp.plugin.tools.ToolArg;
import com.atlassian.mcp.plugin.tools.TypedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GetIssueDevelopmentInfoTool extends TypedTool<GetIssueDevelopmentInfoTool.Args> {

  static final String APPLICATION_TYPE_DESCRIPTION =
      "(Optional) Restrict the lookup to one application, e.g. 'stash' (Bitbucket Server),"
          + " 'bitbucket', 'github' or 'gitlab'. When omitted, every one of those four is probed"
          + " and the results merged.";
  static final String DATA_TYPE_DESCRIPTION =
      "(Optional) Restrict the lookup to one kind of development data. When omitted, all three"
          + " are probed.";
  static final String PULL_REQUEST = "pullrequest";
  static final String BRANCH = "branch";
  static final String REPOSITORY = "repository";

  public record Args(
      @ToolArg(value = "Jira issue key (e.g., 'PROJ-123')", required = true) String issueKey,
      @ToolArg(APPLICATION_TYPE_DESCRIPTION) String applicationType,
      @ToolArg(
              value = DATA_TYPE_DESCRIPTION,
              allowed = {PULL_REQUEST, BRANCH, REPOSITORY})
          String dataType) {}

  /** Application types probed when the caller names none. */
  private static final String[] APP_TYPES = {"stash", "bitbucket", "github", "gitlab"};

  /** Data types probed for each application type when the caller names none. */
  private static final String[] DATA_TYPES = {PULL_REQUEST, BRANCH, REPOSITORY};

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssueDevelopmentInfoTool(JiraRestClient client) {
    super(Args.class);
    this.client = client;
  }

  @Override
  public String name() {
    return "get_issue_development_info";
  }

  @Override
  public String description() {
    return "Get development information (PRs, commits, branches) linked to a Jira issue. This retrieves the development panel information that shows linked pull requests, branches, and commits from connected source control systems like Bitbucket, GitHub, or GitLab.";
  }

  @Override
  public boolean isWriteTool() {
    return false;
  }

  @Override
  protected String run(Args args, McpContext context) throws McpToolException {
    String issueId = resolveIssueId(args.issueKey(), context.authHeader());

    if (args.applicationType() != null) {
      return fetchDevInfo(
          args.issueKey(), issueId, args.applicationType(), args.dataType(), context.authHeader());
    }

    // The endpoint answers for one application and one data type at a time, so with no application
    // named every one is probed and the results merged — restricted to the caller's data type when
    // they named one.
    List<Object> detail = new ArrayList<>();
    String[] dataTypes = args.dataType() != null ? new String[] {args.dataType()} : DATA_TYPES;
    for (String appType : APP_TYPES) {
      for (String dt : dataTypes) {
        try {
          collectDetail(detail, fetchDevInfoRaw(issueId, appType, dt, context.authHeader()));
        } catch (Exception e) {
          // An application that is not connected answers with an error; the others still count.
        }
      }
    }

    Map<String, Object> merged = new LinkedHashMap<>();
    merged.put("issue_key", args.issueKey());
    merged.put("detail", detail);
    try {
      return mapper.writeValueAsString(merged);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize dev info: " + e.getMessage());
    }
  }

  /** The dev-status API keys off the numeric issue ID; it does not accept an issue key. */
  private String resolveIssueId(String issueKey, String authHeader) throws McpToolException {
    String issueJson =
        client.get("/rest/api/2/issue/" + encode(issueKey) + "?fields=id", authHeader);

    String issueId;
    try {
      issueId = mapper.readTree(issueJson).path("id").asText(null);
    } catch (Exception e) {
      throw new McpToolException(
          "Failed to resolve issue ID for " + issueKey + ": " + e.getMessage());
    }
    if (issueId == null || issueId.isEmpty()) {
      throw new McpToolException("Could not get numeric issue ID for " + issueKey);
    }
    return issueId;
  }

  private String fetchDevInfo(
      String issueKey, String issueId, String appType, String dataType, String authHeader)
      throws McpToolException {
    String json = fetchDevInfoRaw(issueId, appType, dataType, authHeader);
    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("issue_key", issueKey);
      JsonNode node = mapper.readTree(json);
      if (node.isObject()) {
        node.properties().forEach(e -> result.put(e.getKey(), e.getValue()));
      }
      return mapper.writeValueAsString(result);
    } catch (Exception e) {
      return json;
    }
  }

  private String fetchDevInfoRaw(String issueId, String appType, String dataType, String authHeader)
      throws McpToolException {
    StringBuilder query = new StringBuilder("?issueId=").append(encode(issueId));
    query.append("&applicationType=").append(encode(appType));
    if (dataType != null) {
      query.append("&dataType=").append(encode(dataType));
    }
    return client.get("/rest/dev-status/1.0/issue/detail" + query, authHeader);
  }

  private void collectDetail(List<Object> detail, String json) {
    try {
      JsonNode node = mapper.readTree(json);
      if (node.path("detail").isArray()) {
        for (JsonNode entry : node.get("detail")) {
          detail.add(mapper.treeToValue(entry, Object.class));
        }
      }
    } catch (Exception e) {
      // A malformed answer from one application does not invalidate the others.
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
