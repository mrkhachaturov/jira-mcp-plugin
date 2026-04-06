package com.atlassian.mcp.plugin.tools.links;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.util.List;
import java.util.Map;

public class CreateRemoteIssueLinkTool implements McpTool {
    private final JiraRestClient client;
    public CreateRemoteIssueLinkTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "create_remote_issue_link"; }
    @Override public String description() { return "Create a remote link on a Jira issue (link to external URL)."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "issue_key", Map.of("type", "string", "description", "Jira issue key"),
                "url", Map.of("type", "string", "description", "Remote URL"),
                "title", Map.of("type", "string", "description", "Link title")),
                "required", List.of("issue_key", "url", "title"));
    }
    @Override public boolean isWriteTool() { return true; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String issueKey = (String) args.get("issue_key");
        String url = (String) args.get("url");
        String title = (String) args.get("title");
        if (issueKey == null || url == null || title == null) throw new McpToolException("issue_key, url, title required");
        String body = "{\"object\":{\"url\":\"" + url + "\",\"title\":\"" + title.replace("\"", "\\\"") + "\"}}";
        return client.post("/rest/api/2/issue/" + issueKey + "/remotelink", body, authHeader);
    }
}
