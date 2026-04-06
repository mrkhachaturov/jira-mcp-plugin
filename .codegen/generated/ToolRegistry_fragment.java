    // Auto-generated tool registration — paste into ToolRegistry.registerAllTools()
    private void registerAllTools(JiraRestClient client) {
        // attachments
        register(new DownloadAttachmentsTool(client));
        register(new GetIssueImagesTool(client));

        // boards
        register(new AddIssuesToSprintTool(client));
        register(new CreateSprintTool(client));
        register(new GetAgileBoardsTool(client));
        register(new GetBoardIssuesTool(client));
        register(new GetSprintIssuesTool(client));
        register(new GetSprintsFromBoardTool(client));
        register(new UpdateSprintTool(client));

        // comments
        register(new AddCommentTool(client));
        register(new EditCommentTool(client));

        // fields
        register(new GetFieldOptionsTool(client));
        register(new SearchFieldsTool(client));

        // forms
        register(new GetIssueProformaFormsTool(client));
        register(new GetProformaFormDetailsTool(client));
        register(new UpdateProformaFormAnswersTool(client));

        // issues
        register(new BatchCreateIssuesTool(client));
        register(new BatchGetChangelogsTool(client));
        register(new CreateIssueTool(client));
        register(new DeleteIssueTool(client));
        register(new GetIssueTool(client));
        register(new GetProjectIssuesTool(client));
        register(new SearchTool(client));
        register(new UpdateIssueTool(client));

        // links
        register(new CreateIssueLinkTool(client));
        register(new CreateRemoteIssueLinkTool(client));
        register(new GetLinkTypesTool(client));
        register(new LinkToEpicTool(client));
        register(new RemoveIssueLinkTool(client));

        // metrics
        register(new GetIssueDatesTool(client));
        register(new GetIssueDevelopmentInfoTool(client));
        register(new GetIssueSlaTool(client));
        register(new GetIssuesDevelopmentInfoTool(client));

        // projects
        register(new BatchCreateVersionsTool(client));
        register(new CreateVersionTool(client));
        register(new GetAllProjectsTool(client));
        register(new GetProjectComponentsTool(client));
        register(new GetProjectVersionsTool(client));

        // servicedesk
        register(new GetQueueIssuesTool(client));
        register(new GetServiceDeskForProjectTool(client));
        register(new GetServiceDeskQueuesTool(client));

        // transitions
        register(new GetTransitionsTool(client));
        register(new TransitionIssueTool(client));

        // users
        register(new AddWatcherTool(client));
        register(new GetIssueWatchersTool(client));
        register(new GetUserProfileTool(client));
        register(new RemoveWatcherTool(client));

        // worklogs
        register(new AddWorklogTool(client));
        register(new GetWorklogTool(client));

    }