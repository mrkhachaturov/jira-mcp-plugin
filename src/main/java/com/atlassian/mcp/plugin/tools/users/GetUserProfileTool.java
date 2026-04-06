package com.atlassian.mcp.plugin.tools.users;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetUserProfileTool implements McpTool {
    private final JiraRestClient client;
    public GetUserProfileTool(JiraRestClient client) { this.client = client; }
    @Override public String name() { return "get_user_profile"; }
    @Override public String description() { return "Get profile information for a Jira user."; }
    @Override public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "username", Map.of("type", "string", "description", "Username or user key")),
                "required", List.of("username"));
    }
    @Override public boolean isWriteTool() { return false; }
    @Override public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String username = (String) args.get("username");
        if (username == null) throw new McpToolException("username is required");
        return client.get("/rest/api/2/user?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8), authHeader);
    }
}
