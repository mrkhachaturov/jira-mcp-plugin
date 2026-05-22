package com.atlassian.mcp.plugin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared icon data URIs.
 *
 * <p>The Jira Software wordmark is used both as the server-level icon (via
 * {@code Implementation.icons}) and as the per-tool icon for the five
 * UI-linked tools whose {@code structuredContent} renders as an MCP Apps
 * widget in compatible clients (Claude Desktop, ChatGPT, VS Code Copilot).
 */
public final class IconConstants {

    private IconConstants() {}

    /** Official Jira Software wordmark, Atlassian blue {@code #0052CC}, raw SVG. */
    public static final String JIRA_LOGO_SVG =
            "<svg fill=\"#0052CC\" role=\"img\" viewBox=\"0 0 24 24\" "
            + "xmlns=\"http://www.w3.org/2000/svg\">"
            + "<title>Jira MCP</title>"
            + "<path d=\"M12.004 0c-2.35 2.395-2.365 6.185.133 8.585l3.412 3.413-3.197 "
            + "3.198a6.501 6.501 0 0 1 1.412 7.04l9.566-9.566a.95.95 0 0 0 0-1.344L12.004 0zm"
            + "-1.748 1.74L.67 11.327a.95.95 0 0 0 0 1.344C4.45 16.44 8.22 20.244 12 24c2.295"
            + "-2.298 2.395-6.096-.08-8.533l-3.47-3.469 3.2-3.2c-1.918-1.955-2.363-4.725-1.394"
            + "-7.057z\"/></svg>";

    /** Same wordmark as a {@code data:image/svg+xml;base64} URI for use in MCP icon fields. */
    public static final String JIRA_LOGO_DATA_URI =
            "data:image/svg+xml;base64,"
            + Base64.getEncoder().encodeToString(JIRA_LOGO_SVG.getBytes(StandardCharsets.UTF_8));
}
