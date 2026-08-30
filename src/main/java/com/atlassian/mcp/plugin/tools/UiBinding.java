package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.ResourceContextBuilder;
import com.atlassian.mcp.plugin.ResourceRegistry;

/**
 * Bundle of dependencies passed to UI-linked tools so they can:
 *
 * <ul>
 *   <li>advertise their widget via {@link McpTool#uiResourceUri()} (using {@link
 *       ResourceRegistry#getResourceUri()}), and
 *   <li>emit {@code structuredContent} for the issue-card widget via {@link
 *       ResourceContextBuilder#build(String, String, String, String)}.
 * </ul>
 *
 * <p>Both fields may be {@code null} when MCP Apps is unavailable (no widget HTML on the classpath)
 * — UI-linked tools must treat both as nullable and short-circuit gracefully.
 */
public final class UiBinding {
  public final ResourceRegistry resourceRegistry;
  public final ResourceContextBuilder contextBuilder;

  public UiBinding(ResourceRegistry resourceRegistry, ResourceContextBuilder contextBuilder) {
    this.resourceRegistry = resourceRegistry;
    this.contextBuilder = contextBuilder;
  }

  /**
   * @return the widget URI, or {@code null} if MCP Apps is unavailable.
   */
  public String resourceUri() {
    return resourceRegistry == null ? null : resourceRegistry.getResourceUri();
  }
}
