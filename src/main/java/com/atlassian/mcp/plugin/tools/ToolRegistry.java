package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Named
public class ToolRegistry {

    private final Map<String, McpTool> allTools = new ConcurrentHashMap<>();
    private final PluginAccessor pluginAccessor;
    private final McpPluginConfig config;

    @Inject
    public ToolRegistry(
            @ComponentImport PluginAccessor pluginAccessor,
            McpPluginConfig config) {
        this.pluginAccessor = pluginAccessor;
        this.config = config;
    }

    public void register(McpTool tool) {
        allTools.put(tool.name(), tool);
    }

    /**
     * Returns tools visible to the given user, filtered by:
     * - capability (required plugin installed)
     * - admin disabled list
     * - read-only mode (hides write tools)
     */
    public List<McpTool> listTools(String userKey) {
        return allTools.values().stream()
                .filter(this::isCapabilityMet)
                .filter(t -> config.isToolEnabled(t.name()))
                .filter(t -> !config.isReadOnlyMode() || !t.isWriteTool())
                .collect(Collectors.toList());
    }

    /**
     * Get a tool by name for execution. Returns null if tool doesn't exist.
     */
    public McpTool getTool(String name) {
        return allTools.get(name);
    }

    /**
     * Check if a tool is callable by the given user right now.
     * Returns an error message if not, null if OK.
     */
    public String checkToolAccess(String toolName, String userKey) {
        McpTool tool = allTools.get(toolName);
        if (tool == null) {
            return "Unknown tool: " + toolName;
        }
        if (!isCapabilityMet(tool)) {
            return "Tool '" + toolName + "' requires a Jira product/app that is not installed on this instance";
        }
        if (!config.isToolEnabled(toolName)) {
            return "Tool '" + toolName + "' is disabled by the administrator";
        }
        if (config.isReadOnlyMode() && tool.isWriteTool()) {
            return "Tool '" + toolName + "' is a write operation and the server is in read-only mode";
        }
        return null;
    }

    private boolean isCapabilityMet(McpTool tool) {
        String requiredPlugin = tool.requiredPluginKey();
        if (requiredPlugin == null) {
            return true;
        }
        return pluginAccessor.isPluginEnabled(requiredPlugin);
    }

    public int totalRegistered() {
        return allTools.size();
    }
}
