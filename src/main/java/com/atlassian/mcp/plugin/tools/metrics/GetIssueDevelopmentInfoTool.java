package com.atlassian.mcp.plugin.tools.metrics;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GetIssueDevelopmentInfoTool implements McpTool {
    private final JiraRestClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Application types to try when none is specified (matches upstream). */
    private static final String[] APP_TYPES = {"stash", "bitbucket", "github", "gitlab"};
    /** Data types to try for each application type (matches upstream). */
    private static final String[] DATA_TYPES = {"pullrequest", "branch", "repository"};

    public GetIssueDevelopmentInfoTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_development_info"; }

    @Override
    public String description() {
        return "Get development information (PRs, commits, branches) linked to a Jira issue. This retrieves the development panel information that shows linked pull requests, branches, and commits from connected source control systems like Bitbucket, GitHub, or GitLab.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123')"),
                        "application_type", Map.of("type", "string", "description", "(Optional) Filter by application type. Examples: 'stash' (Bitbucket Server), 'bitbucket', 'github', 'gitlab'"),
                        "data_type", Map.of("type", "string", "description", "(Optional) Filter by data type. Examples: 'pullrequest', 'branch', 'repository'")
                ),
                "required", List.of("issue_key")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpToolException("'issue_key' parameter is required");
        }
        String applicationType = (String) args.get("application_type");
        String dataType = (String) args.get("data_type");

        // Step 1: Get the numeric issue ID (dev-status API requires it, not the key)
        String issueId;
        try {
            String issueJson = client.get("/rest/api/2/issue/" + encode(issueKey) + "?fields=id", authHeader);
            JsonNode issueNode = mapper.readTree(issueJson);
            issueId = issueNode.path("id").asText(null);
            if (issueId == null || issueId.isEmpty()) {
                throw new McpToolException("Could not get numeric issue ID for " + issueKey);
            }
        } catch (McpToolException e) {
            throw e;
        } catch (Exception e) {
            throw new McpToolException("Failed to resolve issue ID for " + issueKey + ": " + e.getMessage());
        }

        // Step 2: Fetch dev info — single app type or try all (matches upstream)
        if (applicationType != null && !applicationType.isBlank()) {
            return fetchDevInfo(issueKey, issueId, applicationType, dataType, authHeader);
        }

        // No app type specified: try all combinations and merge results (matches upstream)
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("issue_key", issueKey);
        merged.put("detail", new ArrayList<>());
        merged.put("pullRequests", new ArrayList<>());
        merged.put("branches", new ArrayList<>());
        merged.put("commits", new ArrayList<>());
        merged.put("repositories", new ArrayList<>());

        for (String appType : APP_TYPES) {
            for (String dt : DATA_TYPES) {
                try {
                    String json = fetchDevInfoRaw(issueId, appType, dt, authHeader);
                    mergeDevResults(merged, json);
                } catch (Exception e) {
                    // Continue trying other combinations (matches upstream behavior)
                }
            }
        }

        try {
            return mapper.writeValueAsString(merged);
        } catch (Exception e) {
            throw new McpToolException("Failed to serialize dev info: " + e.getMessage());
        }
    }

    private String fetchDevInfo(String issueKey, String issueId, String appType,
                                String dataType, String authHeader) throws McpToolException {
        String json = fetchDevInfoRaw(issueId, appType, dataType, authHeader);
        // Wrap with issue_key for context (matches upstream)
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

    private String fetchDevInfoRaw(String issueId, String appType,
                                   String dataType, String authHeader) throws McpToolException {
        StringBuilder query = new StringBuilder("?issueId=").append(encode(issueId));
        query.append("&applicationType=").append(encode(appType));
        if (dataType != null && !dataType.isBlank()) {
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
