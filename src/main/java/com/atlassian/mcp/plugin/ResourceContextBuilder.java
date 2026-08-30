package com.atlassian.mcp.plugin;

import com.atlassian.mcp.plugin.IssueCardPayload.Comment;
import com.atlassian.mcp.plugin.IssueCardPayload.Issue;
import com.atlassian.mcp.plugin.IssueCardPayload.Named;
import com.atlassian.mcp.plugin.IssueCardPayload.Status;
import com.atlassian.mcp.plugin.IssueCardPayload.User;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the {@link IssueCardPayload} that UI-linked tools send as {@code structuredContent}.
 *
 * <p>Every UI-linked tool sends the same shape: {@code get_issue} puts one element in {@code
 * issues}, the list tools put many, and both carry {@code totalCount} so the widget can render
 * either with one component.
 */
@jakarta.inject.Named("resourceContextBuilder")
public class ResourceContextBuilder {

  /** Tools whose results get wrapped in structuredContent. */
  public static final Set<String> UI_TOOLS =
      Set.of("get_issue", "search", "get_project_issues", "get_board_issues", "get_sprint_issues");

  private final ObjectMapper mapper = new ObjectMapper();
  private final McpPluginConfig config;
  private final ApplicationProperties applicationProperties;

  @Inject
  public ResourceContextBuilder(
      McpPluginConfig config, @ComponentImport ApplicationProperties applicationProperties) {
    this.config = config;
    this.applicationProperties = applicationProperties;
  }

  public boolean isUiLinked(String toolName) {
    return UI_TOOLS.contains(toolName);
  }

  /**
   * Build the structuredContent payload for a UI-linked tool result. Returns null if the data
   * cannot be normalized.
   */
  public ObjectNode build(
      String toolName, String resultJson, String username, String userDisplayName) {
    try {
      JsonNode data = mapper.readTree(resultJson);
      return build(toolName, data, username, userDisplayName);
    } catch (Exception e) {
      return null;
    }
  }

  public ObjectNode build(String toolName, JsonNode data, String username, String userDisplayName) {
    return mapper.valueToTree(payload(toolName, data, username, userDisplayName));
  }

  public IssueCardPayload payload(
      String toolName, JsonNode data, String username, String userDisplayName) {
    List<Issue> issues = new ArrayList<>();
    int totalCount;

    if ("get_issue".equals(toolName)) {
      Issue normalized = normalizeIssue(data);
      if (normalized != null) issues.add(normalized);
      totalCount = issues.size();
    } else {
      JsonNode issuesNode = data != null && data.has("issues") ? data.get("issues") : null;
      if (issuesNode != null && issuesNode.isArray()) {
        for (JsonNode issueNode : issuesNode) {
          Issue normalized = normalizeIssue(issueNode);
          if (normalized != null) issues.add(normalized);
        }
      }
      totalCount =
          data != null && data.has("total") && data.get("total").isInt()
              ? data.get("total").asInt()
              : issues.size();
    }

    return new IssueCardPayload(
        new User(text(username), text(userDisplayName)), resolveBaseUrl(), issues, totalCount);
  }

  private String resolveBaseUrl() {
    String override = config.getJiraBaseUrlOverride();
    if (override != null && !override.isEmpty()) {
      return override;
    }
    try {
      if (applicationProperties != null) {
        return applicationProperties.getBaseUrl(UrlMode.CANONICAL).toString();
      }
    } catch (Exception ignored) {
      // fall through
    }
    return "";
  }

  /** Returns null when the node does not look like a Jira issue. */
  private Issue normalizeIssue(JsonNode issue) {
    if (issue == null || !issue.isObject()) return null;

    String key = field(issue, "key");
    if (key.isEmpty()) return null;

    JsonNode fields = issue.has("fields") ? issue.get("fields") : mapper.createObjectNode();

    // ResponseTrimmer renames issuetype to issue_type, so both spellings reach this point.
    JsonNode issueType = fields.has("issue_type") ? fields.get("issue_type") : null;
    if (issueType == null && fields.has("issuetype")) issueType = fields.get("issuetype");

    return new Issue(
        key,
        field(fields, "summary"),
        status(fields.get("status")),
        new Named(field(object(fields.get("priority")), "name")),
        new Named(field(object(issueType), "name")),
        user(fields.get("assignee")),
        user(fields.get("reporter")),
        description(fields.get("description")),
        comments(fields.get("comment")),
        field(fields, "created"),
        field(fields, "updated"));
  }

  private Status status(JsonNode status) {
    JsonNode node = object(status);
    JsonNode category = object(node == null ? null : node.get("statusCategory"));
    return new Status(
        field(node, "name"),
        field(category, "key"),
        field(category, "colorName"),
        field(category, "name"));
  }

  private User user(JsonNode node) {
    JsonNode user = object(node);
    return user == null ? null : new User(field(user, "name"), field(user, "displayName"));
  }

  private String description(JsonNode node) {
    return node == null || node.isNull() ? null : node.asText("");
  }

  private List<Comment> comments(JsonNode wrapper) {
    List<Comment> out = new ArrayList<>();
    JsonNode node = object(wrapper);
    JsonNode list = node == null ? null : node.get("comments");
    if (list == null || !list.isArray()) return out;

    for (JsonNode comment : list) {
      out.add(
          new Comment(
              user(comment.get("author")),
              field(comment, "body"),
              field(comment, "created"),
              field(comment, "updated")));
    }
    return out;
  }

  private static JsonNode object(JsonNode node) {
    return node != null && node.isObject() ? node : null;
  }

  private static String field(JsonNode node, String name) {
    return node != null && node.has(name) ? node.get(name).asText("") : "";
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
