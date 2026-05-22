package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Set;

/**
 * Builds the structuredContent payload for MCP Apps UI-linked tools.
 *
 * <p>Extracted verbatim from {@code JsonRpcHandler.buildStructuredContent} and
 * {@code JsonRpcHandler.normalizeIssue} (deleted in Task 2 Step 2.10). Lives in
 * {@link com.atlassian.mcp.plugin} (not {@code rest}) because Task 3 will hook
 * it from {@code McpToolAdapter} for SDK-driven structured content emission.
 *
 * <p>UI-linked tools (verified from {@code JsonRpcHandler.UI_TOOLS} before deletion):
 * <ul>
 *   <li>{@code get_issue} — single-issue shape (object at root)</li>
 *   <li>{@code search} — list shape ({@code issues[]}, {@code total})</li>
 *   <li>{@code get_project_issues} — list shape</li>
 *   <li>{@code get_board_issues} — list shape</li>
 *   <li>{@code get_sprint_issues} — list shape</li>
 * </ul>
 *
 * <p>Output shape (mirrors the deleted {@code buildStructuredContent}):
 * <pre>{@code
 * {
 *   "currentUser": { "name": "...", "displayName": "..." },
 *   "baseUrl":     "https://...",
 *   "issues":      [ { ...normalizeIssue(...) } ],
 *   "totalCount":  N
 * }
 * }</pre>
 *
 * <p>Note (per Codex R2-F4): even single-issue tools produce
 * {@code issues[]} + {@code totalCount}; the widget reuses one component for both
 * single + list rendering.
 */
@Named("resourceContextBuilder")
public class ResourceContextBuilder {

    /** Tools whose results get wrapped in structuredContent. */
    public static final Set<String> UI_TOOLS = Set.of(
            "get_issue", "search", "get_project_issues", "get_board_issues", "get_sprint_issues");

    private final ObjectMapper mapper = new ObjectMapper();
    private final McpPluginConfig config;
    private final ApplicationProperties applicationProperties;

    @Inject
    public ResourceContextBuilder(McpPluginConfig config,
                                  @ComponentImport ApplicationProperties applicationProperties) {
        this.config = config;
        this.applicationProperties = applicationProperties;
    }

    public boolean isUiLinked(String toolName) {
        return UI_TOOLS.contains(toolName);
    }

    /**
     * Build the structuredContent payload for a UI-linked tool result.
     * Returns null if the data cannot be normalized.
     */
    public ObjectNode build(String toolName, String resultJson, String username, String userDisplayName) {
        try {
            JsonNode data = mapper.readTree(resultJson);
            return build(toolName, data, username, userDisplayName);
        } catch (Exception e) {
            return null;
        }
    }

    public ObjectNode build(String toolName, JsonNode data, String username, String userDisplayName) {
        ObjectNode sc = mapper.createObjectNode();

        ObjectNode currentUser = mapper.createObjectNode();
        currentUser.put("name", username != null ? username : "");
        currentUser.put("displayName", userDisplayName != null ? userDisplayName : "");
        sc.set("currentUser", currentUser);

        sc.put("baseUrl", resolveBaseUrl());

        ArrayNode issues = mapper.createArrayNode();
        int totalCount;

        if ("get_issue".equals(toolName)) {
            ObjectNode normalized = normalizeIssue(data);
            if (normalized != null) {
                issues.add(normalized);
            }
            totalCount = issues.size();
        } else {
            JsonNode issuesNode = data != null && data.has("issues") ? data.get("issues") : null;
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    ObjectNode normalized = normalizeIssue(issueNode);
                    if (normalized != null) {
                        issues.add(normalized);
                    }
                }
            }
            if (data != null && data.has("total") && data.get("total").isInt()) {
                totalCount = data.get("total").asInt();
            } else {
                totalCount = issues.size();
            }
        }

        sc.set("issues", issues);
        sc.put("totalCount", totalCount);
        return sc;
    }

    private String resolveBaseUrl() {
        String override = config.getJiraBaseUrlOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        try {
            if (applicationProperties != null) {
                return applicationProperties.getBaseUrl().toString();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    /**
     * Normalize a single Jira issue node into the structuredContent issue schema.
     * Returns null if the node does not look like a Jira issue.
     *
     * <p>PORTED VERBATIM from {@code JsonRpcHandler.normalizeIssue} pre-Task-2.
     */
    private ObjectNode normalizeIssue(JsonNode issue) {
        if (issue == null || !issue.isObject()) return null;

        String key = issue.has("key") ? issue.get("key").asText(null) : null;
        if (key == null || key.isEmpty()) return null;

        ObjectNode out = mapper.createObjectNode();
        out.put("key", key);

        JsonNode fields = issue.has("fields") ? issue.get("fields") : mapper.createObjectNode();

        // Summary
        out.put("summary", fields.has("summary") ? fields.get("summary").asText("") : "");

        // Status
        ObjectNode statusOut = mapper.createObjectNode();
        JsonNode status = fields.has("status") ? fields.get("status") : null;
        if (status != null && status.isObject()) {
            statusOut.put("name", status.has("name") ? status.get("name").asText("") : "");
            JsonNode statusCategory = status.has("statusCategory") ? status.get("statusCategory") : null;
            if (statusCategory != null && statusCategory.isObject()) {
                statusOut.put("category",
                        statusCategory.has("key") ? statusCategory.get("key").asText("") : "");
                statusOut.put("colorName",
                        statusCategory.has("colorName") ? statusCategory.get("colorName").asText("") : "");
                statusOut.put("categoryName",
                        statusCategory.has("name") ? statusCategory.get("name").asText("") : "");
            } else {
                statusOut.put("category", "");
                statusOut.put("colorName", "");
                statusOut.put("categoryName", "");
            }
        } else {
            statusOut.put("name", "");
            statusOut.put("category", "");
            statusOut.put("colorName", "");
            statusOut.put("categoryName", "");
        }
        out.set("status", statusOut);

        // Priority (as object with name)
        ObjectNode priorityOut = mapper.createObjectNode();
        JsonNode priority = fields.has("priority") ? fields.get("priority") : null;
        priorityOut.put("name", (priority != null && priority.isObject() && priority.has("name"))
                ? priority.get("name").asText("") : "");
        out.set("priority", priorityOut);

        // Issue type — ResponseTrimmer renames issuetype → issue_type, handle both
        JsonNode issueType = null;
        if (fields.has("issue_type")) {
            issueType = fields.get("issue_type");
        } else if (fields.has("issuetype")) {
            issueType = fields.get("issuetype");
        }
        ObjectNode issueTypeOut = mapper.createObjectNode();
        issueTypeOut.put("name", (issueType != null && issueType.isObject() && issueType.has("name"))
                ? issueType.get("name").asText("") : "");
        out.set("issue_type", issueTypeOut);

        // Assignee (nullable)
        JsonNode assignee = fields.has("assignee") ? fields.get("assignee") : null;
        if (assignee != null && assignee.isObject()) {
            ObjectNode assigneeOut = mapper.createObjectNode();
            assigneeOut.put("name", assignee.has("name") ? assignee.get("name").asText("") : "");
            assigneeOut.put("displayName",
                    assignee.has("displayName") ? assignee.get("displayName").asText("") : "");
            out.set("assignee", assigneeOut);
        } else {
            out.set("assignee", null);
        }

        // Reporter (nullable)
        JsonNode reporter = fields.has("reporter") ? fields.get("reporter") : null;
        if (reporter != null && reporter.isObject()) {
            ObjectNode reporterOut = mapper.createObjectNode();
            reporterOut.put("name", reporter.has("name") ? reporter.get("name").asText("") : "");
            reporterOut.put("displayName",
                    reporter.has("displayName") ? reporter.get("displayName").asText("") : "");
            out.set("reporter", reporterOut);
        } else {
            out.set("reporter", null);
        }

        // Description (nullable)
        JsonNode description = fields.has("description") ? fields.get("description") : null;
        if (description != null && !description.isNull()) {
            out.put("description", description.asText(""));
        } else {
            out.set("description", null);
        }

        // Comments — nested as fields.comment.comments[]
        ArrayNode commentsOut = mapper.createArrayNode();
        JsonNode commentWrapper = fields.has("comment") ? fields.get("comment") : null;
        if (commentWrapper != null && commentWrapper.isObject()) {
            JsonNode commentsList = commentWrapper.has("comments")
                    ? commentWrapper.get("comments") : null;
            if (commentsList != null && commentsList.isArray()) {
                for (JsonNode c : commentsList) {
                    ObjectNode cOut = mapper.createObjectNode();
                    JsonNode author = c.has("author") ? c.get("author") : null;
                    if (author != null && author.isObject()) {
                        ObjectNode authorOut = mapper.createObjectNode();
                        authorOut.put("name", author.has("name") ? author.get("name").asText("") : "");
                        authorOut.put("displayName",
                                author.has("displayName") ? author.get("displayName").asText("") : "");
                        cOut.set("author", authorOut);
                    } else {
                        cOut.set("author", null);
                    }
                    cOut.put("body", c.has("body") ? c.get("body").asText("") : "");
                    cOut.put("created", c.has("created") ? c.get("created").asText("") : "");
                    cOut.put("updated", c.has("updated") ? c.get("updated").asText("") : "");
                    commentsOut.add(cOut);
                }
            }
        }
        out.set("comments", commentsOut);

        // Timestamps
        out.put("created", fields.has("created") ? fields.get("created").asText("") : "");
        out.put("updated", fields.has("updated") ? fields.get("updated").asText("") : "");

        return out;
    }
}
