package com.atlassian.mcp.plugin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The {@code structuredContent} the Issue Card widget receives. The advertised output schema is
 * derived from these records, so the payload the widget is promised and the payload it is sent are
 * one declaration.
 *
 * <p>Single-issue and list tools share the shape: {@code get_issue} sends one element in {@code
 * issues}, the others send many.
 */
public record IssueCardPayload(
    User currentUser, String baseUrl, List<Issue> issues, int totalCount) {

  public record User(String name, String displayName) {}

  public record Named(String name) {}

  public record Status(String name, String category, String colorName, String categoryName) {}

  public record Comment(User author, String body, String created, String updated) {}

  public record Issue(
      String key,
      String summary,
      Status status,
      Named priority,
      @JsonProperty("issue_type") Named issueType,
      User assignee,
      User reporter,
      String description,
      List<Comment> comments,
      String created,
      String updated) {}
}
