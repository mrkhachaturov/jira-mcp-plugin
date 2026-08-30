package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.IssueCardPayload;
import java.util.Map;

/** Shared {@link McpTool#outputSchema()} for the tools that render as an Issue Card widget. */
public final class UiToolDefaults {

  private UiToolDefaults() {}

  public static final Map<String, Object> ISSUE_LIST_OUTPUT_SCHEMA =
      ResultSchema.of(IssueCardPayload.class);
}
