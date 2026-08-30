# Tools

| Package        | Count | Toolset                         | Plugin Requirement |
| -------------- | ----- | ------------------------------- | ------------------ |
| `issues/`      | 8     | `jira_issues`                   | —                  |
| `comments/`    | 2     | `jira_comments`                 | —                  |
| `transitions/` | 2     | `jira_transitions`              | —                  |
| `worklogs/`    | 2     | `jira_worklog`                  | —                  |
| `boards/`      | 7     | `jira_agile`                    | Jira Software      |
| `links/`       | 5     | `jira_links`                    | —                  |
| `projects/`    | 5     | `jira_projects`                 | —                  |
| `users/`       | 4     | `jira_watchers` / `jira_users`  | —                  |
| `attachments/` | 2     | `jira_attachments`              | —                  |
| `fields/`      | 2     | `jira_fields`                   | —                  |
| `servicedesk/` | 3     | `jira_service_desk`             | JSM                |
| `forms/`       | 3     | `jira_forms`                    | Proforma           |
| `metrics/`     | 4     | `jira_sla` / `jira_development` | JSM (SLA only)     |

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

## Writing `run` bodies

How a tool declares its parameters is the contract in
[tool-authoring.md](tool-authoring.md); this section covers only what the body
does with them.

Tools call the Jira REST API directly via `JiraRestClient.get/post/put/delete()`.

- **GET tools**: build the query string, return
  `client.get(path + query, context.authHeader())`.
- **POST/PUT tools**: build a `Map<String, Object>`, serialise it with Jackson,
  send it as the body.
- **Create/Update issue**: fields must be wrapped in
  `{"fields": {"project": {"key": "..."}, ...}}` — a Jira API requirement.
- **Structured parameters arrive already bound.** A list is a `List<String>` and
  an object is a `Map<String, Object>` or a nested record; the body never parses
  a comma-separated string or a string holding JSON. Mapping `List.of("Frontend",
  "API")` onto `[{"name": "Frontend"}, {"name": "API"}]` is Jira's payload shape,
  not argument parsing.
