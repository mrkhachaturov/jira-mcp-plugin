package com.atlassian.mcp.plugin.tools.attachments;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.McpToolException;
import com.atlassian.mcp.plugin.tools.McpTool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GetIssueImagesTool implements McpTool {
    private final JiraRestClient client;

    public GetIssueImagesTool(JiraRestClient client) {
        this.client = client;
    }

    @Override public String name() { return "get_issue_images"; }

    @Override
    public String description() {
        return "Get all images attached to a Jira issue as inline image content. Filters attachments to images only (PNG, JPEG, GIF, WebP, SVG, BMP) and returns them as base64-encoded ImageContent that clients can render directly. Non-image attachments are excluded. Files with ambiguous MIME types (application/octet-stream) are detected by filename extension as a fallback. Images larger than 50 MB are skipped with an error entry in the summary.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "issue_key", Map.of("type", "string", "description", "Jira issue key (e.g., 'PROJ-123'). Returns image attachments as inline ImageContent for LLM vision.")
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
