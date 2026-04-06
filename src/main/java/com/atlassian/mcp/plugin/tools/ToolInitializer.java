package com.atlassian.mcp.plugin.tools;

import com.atlassian.mcp.plugin.JiraRestClient;
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
import javax.inject.Inject;
import javax.inject.Named;

@Named
public class ToolInitializer {

    @Inject
    public ToolInitializer(ToolRegistry registry, JiraRestClient client) {
        // Issues & Search (7)
        registry.register(new SearchTool(client));
        registry.register(new GetIssueTool(client));
        registry.register(new CreateIssueTool(client));
        registry.register(new UpdateIssueTool(client));
        registry.register(new DeleteIssueTool(client));
        registry.register(new BatchCreateIssuesTool(client));
        registry.register(new BatchGetChangelogsTool(client));

        // Comments (2)
        registry.register(new AddCommentTool(client));
        registry.register(new EditCommentTool(client));

        // Transitions (2)
        registry.register(new GetTransitionsTool(client));
        registry.register(new TransitionIssueTool(client));

        // Worklogs (2)
        registry.register(new GetWorklogTool(client));
        registry.register(new AddWorklogTool(client));

        // Boards & Sprints (4) — require Jira Software
        registry.register(new GetAgileBoardsTool(client));
        registry.register(new GetBoardIssuesTool(client));
        registry.register(new GetSprintsFromBoardTool(client));
        registry.register(new GetSprintIssuesTool(client));

        // Links (4)
        registry.register(new GetLinkTypesTool(client));
        registry.register(new CreateIssueLinkTool(client));
        registry.register(new CreateRemoteIssueLinkTool(client));
        registry.register(new RemoveIssueLinkTool(client));

        // Epics (1)
        registry.register(new LinkToEpicTool(client));

        // Projects & Versions (6)
        registry.register(new GetAllProjectsTool(client));
        registry.register(new GetProjectIssuesTool(client));
        registry.register(new GetProjectVersionsTool(client));
        registry.register(new GetProjectComponentsTool(client));
        registry.register(new CreateVersionTool(client));
        registry.register(new BatchCreateVersionsTool(client));

        // Users & Watchers (4)
        registry.register(new GetUserProfileTool(client));
        registry.register(new GetIssueWatchersTool(client));
        registry.register(new AddWatcherTool(client));
        registry.register(new RemoveWatcherTool(client));

        // Attachments (2)
        registry.register(new DownloadAttachmentsTool(client));
        registry.register(new GetIssueImagesTool(client));

        // Fields (2)
        registry.register(new SearchFieldsTool(client));
        registry.register(new GetFieldOptionsTool(client));

        // Service Desk (3) — require JSM
        registry.register(new GetServiceDeskForProjectTool(client));
        registry.register(new GetServiceDeskQueuesTool(client));
        registry.register(new GetQueueIssuesTool(client));

        // Forms (3) — require Proforma
        registry.register(new GetIssueProformaFormsTool(client));
        registry.register(new GetProformaFormDetailsTool(client));
        registry.register(new UpdateProformaFormAnswersTool(client));

        // Dates & Metrics (4)
        registry.register(new GetIssueDatesTool(client));
        registry.register(new GetIssueSlaTool(client));
        registry.register(new GetIssueDevelopmentInfoTool(client));
        registry.register(new GetIssuesDevelopmentInfoTool(client));

        // Total: 46 tools
    }
}
