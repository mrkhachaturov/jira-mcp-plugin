package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class DownloadAttachmentsTool implements McpTool {
    private final JiraRestClient client;

    public DownloadAttachmentsTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "download_attachments"; }

    @Override
    public String description() {
        return "Download attachments from a Jira issue. Returns attachment contents as base64-encoded embedded resources so that they are available over the MCP protocol without requiring filesystem access on the server.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123', 'ACV2-642')")
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

        return client.get("/rest/api/2/issue/" + issueKey + "?fields=attachment", authHeader);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
