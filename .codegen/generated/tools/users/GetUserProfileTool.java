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

    public GetUserProfileTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_user_profile"; }

    @Override
    public String description() {
        return "Retrieve profile information for a specific Jira user.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "user_identifier", Map.of("type", "string", "description", "Identifier for the user (e.g., email address 'user@example.com', username 'johndoe', account ID 'accountid:...', or key for Server/DC).")
                ),
                "required", List.of("user_identifier")
        );
    }

    @Override public boolean isWriteTool() { return false; }

    @Override
    public String execute(Map<String, Object> args, String authHeader) throws McpToolException {
        String userIdentifier = (String) args.get("user_identifier");
        if (userIdentifier == null || userIdentifier.isBlank()) {
            throw new McpToolException("'user_identifier' parameter is required");
        }

        StringBuilder query = new StringBuilder();
        String sep = "?";
        if (userIdentifier != null && !userIdentifier.isBlank()) {
            query.append(sep).append("username=").append(encode(userIdentifier));
            sep = "&";
        }

        return client.get("/rest/api/2/user" + query, authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
