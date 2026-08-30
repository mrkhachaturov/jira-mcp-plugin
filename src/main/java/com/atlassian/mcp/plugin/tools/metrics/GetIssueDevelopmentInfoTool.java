package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.DeclarativeTool;
import com.atlassian.mcp.plugin.tools.ToolArgs;
import com.atlassian.mcp.plugin.tools.ToolParam;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GetIssueDevelopmentInfoTool extends DeclarativeTool {

  private static final ToolParam<String> ISSUE_KEY =
      ToolParam.string("issue_key", "Jira issue key (e.g., 'PROJ-123')").required();
  private static final ToolParam<String> APPLICATION_TYPE =
      ToolParam.string(
          "application_type",
          "(Optional) Filter by application type. Examples: 'stash' (Bitbucket Server),"
              + " 'bitbucket', 'github', 'gitlab'");
  private static final ToolParam<String> DATA_TYPE =
      ToolParam.string(
          "data_type",
          "(Optional) Filter by data type. Examples: 'pullrequest', 'branch', 'repository'");

  /** Application types probed when the caller names none. */
  private static final String[] APP_TYPES = {"stash", "bitbucket", "github", "gitlab"};

  /** Data types probed for each application type. */
  private static final String[] DATA_TYPES = {"pullrequest", "branch", "repository"};

  private final JiraRestClient client;
  private final ObjectMapper mapper = new ObjectMapper();

  public GetIssueDevelopmentInfoTool(JiraRestClient client) {
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
  public List<ToolParam<?>> params() {
    return List.of(ISSUE_KEY, APPLICATION_TYPE, DATA_TYPE);
  }

  @Override
  public String run(ToolArgs args, String authHeader) throws McpToolException {
    String issueKey = args.require(ISSUE_KEY);
    String applicationType = args.get(APPLICATION_TYPE);
    String dataType = args.get(DATA_TYPE);

    // The dev-status API keys off the numeric issue ID; it does not accept an issue key.
    String issueId;
    try {
      String issueJson =
          client.get("/rest/api/2/issue/" + encode(issueKey) + "?fields=id", authHeader);
      JsonNode issueNode = mapper.readTree(issueJson);
      issueId = issueNode.path("id").asText(null);
      if (issueId == null || issueId.isEmpty()) {
        throw new McpToolException("Could not get numeric issue ID for " + issueKey);
      }
    } catch (McpToolException e) {
      throw e;
    } catch (Exception e) {
      throw new McpToolException(
          "Failed to resolve issue ID for " + issueKey + ": " + e.getMessage());
    }

    if (applicationType != null) {
      return fetchDevInfo(issueKey, issueId, applicationType, dataType, authHeader);
    }

    // The endpoint answers for one application and one data type at a time, so with no application
    // named every one is probed and the results merged — restricted to the caller's data type when
    // they named one.
    Map<String, Object> merged = new LinkedHashMap<>();
    merged.put("issue_key", issueKey);
    merged.put("detail", new ArrayList<>());
    merged.put("pullRequests", new ArrayList<>());
    merged.put("branches", new ArrayList<>());
    merged.put("commits", new ArrayList<>());
    merged.put("repositories", new ArrayList<>());

    String[] dataTypes = dataType != null ? new String[] {dataType} : DATA_TYPES;
    for (String appType : APP_TYPES) {
      for (String dt : dataTypes) {
        try {
          String json = fetchDevInfoRaw(issueId, appType, dt, authHeader);
          mergeDevResults(merged, json);
        } catch (Exception e) {
          // An application that is not connected answers with an error; the others still count.
        }
      }
    }

    try {
      return mapper.writeValueAsString(merged);
    } catch (Exception e) {
      throw new McpToolException("Failed to serialize dev info: " + e.getMessage());
    }
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
        node.fields().forEachRemaining(e -> result.put(e.getKey(), e.getValue()));
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

  @SuppressWarnings("unchecked")
  private void mergeDevResults(Map<String, Object> merged, String json) {
    try {
      JsonNode node = mapper.readTree(json);
      if (node.has("detail") && node.get("detail").isArray()) {
        for (JsonNode detail : node.get("detail")) {
          ((List<Object>) merged.get("detail")).add(mapper.treeToValue(detail, Object.class));
        }
      }
    } catch (Exception e) {
      // Ignore parse errors for individual results
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
