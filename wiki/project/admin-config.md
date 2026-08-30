# Admin Configuration

Backed by Jira `PluginSettings` via `McpPluginConfig`. Edit via the admin
UI at `/plugins/servlet/mcp-admin` or REST at `/rest/mcp-admin/1.0/`.

| Key                                          | Default | Purpose                              |
| -------------------------------------------- | ------- | ------------------------------------ |
| `com.atlassian.mcp.plugin.enabled`           | false   | Global MCP on/off                    |
| `com.atlassian.mcp.plugin.allowedUsers`      | ""      | Comma-separated usernames            |
| `com.atlassian.mcp.plugin.allowedGroups`     | ""      | Comma-separated group names          |
| `com.atlassian.mcp.plugin.disabledTools`     | ""      | Comma-separated tool names           |
| `com.atlassian.mcp.plugin.readOnlyMode`      | false   | Hide write tools                     |
| `com.atlassian.mcp.plugin.jiraBaseUrl`       | ""      | Override internal base URL           |
| `com.atlassian.mcp.plugin.oauthClientId`     | ""      | OAuth Application Link client ID     |
| `com.atlassian.mcp.plugin.oauthClientSecret` | ""      | OAuth Application Link client secret |
