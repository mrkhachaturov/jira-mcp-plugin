# Tools — 49 Total

| Package | Count | Toolset | Plugin Requirement |
|---------|-------|---------|--------------------|
| `issues/` | 8 | `jira_issues` | — |
| `comments/` | 2 | `jira_comments` | — |
| `transitions/` | 2 | `jira_transitions` | — |
| `worklogs/` | 2 | `jira_worklog` | — |
| `boards/` | 7 | `jira_agile` | Jira Software |
| `links/` | 5 | `jira_links` | — |
| `projects/` | 5 | `jira_projects` | — |
| `users/` | 4 | `jira_watchers` / `jira_users` | — |
| `attachments/` | 2 | `jira_attachments` | — |
| `fields/` | 2 | `jira_fields` | — |
| `servicedesk/` | 3 | `jira_service_desk` | JSM |
| `forms/` | 3 | `jira_forms` | Proforma |
| `metrics/` | 4 | `jira_sla` / `jira_development` | JSM (SLA only) |

Tools with a plugin requirement are automatically hidden from `tools/list`
if that plugin isn't installed.

## Tool interface

Every tool implements `McpTool`:

```java
public interface McpTool {
    String name();                          // snake_case
    String description();
    Map<String, Object> inputSchema();      // JSON Schema for parameters
    boolean isWriteTool();                  // true = hidden in read-only mode
    default String requiredPluginKey() { return null; }
    String execute(Map<String, Object> args, String authHeader) throws McpToolException;
}
```

Additional optional methods on `McpTool` cover progress streaming
(`supportsProgress`, `executeWithSdkProgress`), MCP Apps UI binding
(`uiResourceUri`, `uiVisibility`), tool annotations (`title`,
`idempotentHint`, `openWorldHint`, `isDestructiveTool`, `iconUri`), and
structured content (`outputSchema`, `structuredContent`).

## Writing `execute()` bodies

Tools call Jira REST API directly via `JiraRestClient.get/post/put/delete()`.
Key patterns:

- **GET tools**: build query string, return `client.get(path + query, authHeader)`.
- **POST/PUT tools**: build `Map<String, Object>`, serialize with Jackson, send as body.
- **Create/Update issue**: must wrap fields in
  `{"fields": {"project": {"key": "..."}, ...}}` — Jira API requirement.
- **JSON string params** (like `fields`, `additional_fields`): parse with
  `mapper.readValue(str, Map.class)` before sending.
- **Components param**: parse comma-separated string into
  `[{"name": "Frontend"}, {"name": "API"}]`.
