package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.JiraRestClient;
import com.atlassian.mcp.plugin.config.McpPluginConfig;
import com.atlassian.mcp.plugin.tools.issues.*;
import com.atlassian.mcp.plugin.tools.comments.*;
import com.atlassian.mcp.plugin.tools.transitions.*;
import com.atlassian.mcp.plugin.tools.worklogs.*;
import com.atlassian.mcp.plugin.tools.boards.*;
import com.atlassian.mcp.plugin.tools.links.*;
import com.atlassian.mcp.plugin.tools.epics.*;
import com.atlassian.mcp.plugin.tools.projects.*;
import com.atlassian.mcp.plugin.tools.users.*;
import com.atlassian.mcp.plugin.tools.attachments.*;
import com.atlassian.mcp.plugin.tools.fields.*;
import com.atlassian.mcp.plugin.tools.servicedesk.*;
import com.atlassian.mcp.plugin.tools.forms.*;
import com.atlassian.mcp.plugin.tools.metrics.*;
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
            McpPluginConfig config,
            JiraRestClient client) {
        this.pluginAccessor = pluginAccessor;
        this.config = config;
        registerAllTools(client);
    }

    private void registerAllTools(JiraRestClient client) {
        // Issues & Search (7)
        register(new SearchTool(client));
        register(new GetIssueTool(client));
        register(new CreateIssueTool(client));
        register(new UpdateIssueTool(client));
        register(new DeleteIssueTool(client));
        register(new BatchCreateIssuesTool(client));
        register(new BatchGetChangelogsTool(client));

        // Comments (2)
        register(new AddCommentTool(client));
        register(new EditCommentTool(client));

        // Transitions (2)
        register(new GetTransitionsTool(client));
        register(new TransitionIssueTool(client));

        // Worklogs (2)
        register(new GetWorklogTool(client));
        register(new AddWorklogTool(client));

        // Boards & Sprints (4)
        register(new GetAgileBoardsTool(client));
        register(new GetBoardIssuesTool(client));
        register(new GetSprintsFromBoardTool(client));
        register(new GetSprintIssuesTool(client));

        // Links (4)
        register(new GetLinkTypesTool(client));
        register(new CreateIssueLinkTool(client));
        register(new CreateRemoteIssueLinkTool(client));
        register(new RemoveIssueLinkTool(client));

        // Epics (1)
        register(new LinkToEpicTool(client));

        // Projects & Versions (6)
        register(new GetAllProjectsTool(client));
        register(new GetProjectIssuesTool(client));
        register(new GetProjectVersionsTool(client));
        register(new GetProjectComponentsTool(client));
        register(new CreateVersionTool(client));
        register(new BatchCreateVersionsTool(client));

        // Users & Watchers (4)
        register(new GetUserProfileTool(client));
        register(new GetIssueWatchersTool(client));
        register(new AddWatcherTool(client));
        register(new RemoveWatcherTool(client));

        // Attachments (2)
        register(new DownloadAttachmentsTool(client));
        register(new GetIssueImagesTool(client));

        // Fields (2)
        register(new SearchFieldsTool(client));
        register(new GetFieldOptionsTool(client));

        // Service Desk (3)
        register(new GetServiceDeskForProjectTool(client));
        register(new GetServiceDeskQueuesTool(client));
        register(new GetQueueIssuesTool(client));

        // Forms (3)
        register(new GetIssueProformaFormsTool(client));
        register(new GetProformaFormDetailsTool(client));
        register(new UpdateProformaFormAnswersTool(client));

        // Dates & Metrics (4)
        register(new GetIssueDatesTool(client));
        register(new GetIssueSlaTool(client));
        register(new GetIssueDevelopmentInfoTool(client));
        register(new GetIssuesDevelopmentInfoTool(client));
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

    /** Returns all registered tools (unfiltered). Used by admin UI. */
    public Collection<McpTool> getAllTools() {
        return allTools.values();
    }

    public int totalRegistered() {
        return allTools.size();
    }
}
