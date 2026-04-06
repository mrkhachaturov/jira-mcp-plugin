# Jira MCP Plugin Design Spec

**Date:** 2026-04-06
**Status:** Draft
**Author:** RK + CC

## Goal

Build a native Jira Data Center plugin that embeds an MCP (Model Context Protocol) server
directly inside Jira. Users connect AI agents to Jira's own URL using their Personal Access
Token. Admins control access and tool availability from within Jira's admin UI.

## Context

- Jira DC 10.7.4, self-hosted behind Traefik, going public
- Upstream reference: mcp-atlassian v0.21.0 (Python, 49 Jira tools, full DC support)
- MCP Java SDK v2.0.0-SNAPSHOT (reference only, no runtime dependency)
- BigPicture plugin analyzed for patterns (REST module, servlet, filters)

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Transport | Streamable HTTP, JSON responses only (no SSE) | Jira async servlets broken; Jira ops are fast |
| Endpoint | `<rest>` module at `/rest/mcp/1.0/` | Cleaner URL, follows BigPicture pattern |
| Tool implementation | Call Jira's own REST API internally | Near-direct port from upstream Python; no need to learn 20+ Java API classes |
| Tool count | All 46 from upstream v0.21.0 | Each tool is a thin REST call wrapper; mechanical translation |
| Auth | Jira PAT passthrough | Zero custom auth code; permissions are automatically correct |
| Admin control | Global toggle + user allowlist (designed for per-user granularity later) | Simple v1, extensible structure |
| MCP SDK | No runtime dependency | Avoids OSGi conflicts; protocol is 3 methods for tools-only server |
| Plugin key | `atlassian-mcp-plugin` | Keeps door open for Confluence support |

## Upstream Version Tracking

The plugin ports tool definitions from the open-source mcp-atlassian project. We track
what version we ported from in `upstream-versions.json` at the repo root:

```json
{
  "mcp-atlassian": {
    "version": "0.21.0",
    "source": "https://github.com/sooperset/mcp-atlassian",
    "ported": "2026-04-06",
    "notes": "46 Jira tools ported, tool names and input schemas match upstream"
  },
  "mcp-java-sdk": {
    "version": "2.0.0-SNAPSHOT",
    "source": "https://github.com/modelcontextprotocol/java-sdk",
    "usage": "reference only, no runtime dependency"
  }
}
```

Each tool class includes a comment referencing the upstream source file and function.
When upstream releases a new version, diff their tool definitions against ours and port
any changes.

## Architecture

```
+-----------------------------------------------------+
| Jira DC 10.7.4 (bpm.yourdomain.com)                 |
|                                                      |
|  +-----------------------------------------------+   |
|  | atlassian-mcp-plugin                           |   |
|  |                                                |   |
|  |  McpResource (JAX-RS)                          |   |
|  |    POST /rest/mcp/1.0/                         |   |
|  |    +-- initialize -> server info               |   |
|  |    +-- tools/list -> filtered tool list         |   |
|  |    +-- tools/call -> dispatch to tool           |   |
|  |                                                |   |
|  |  ToolRegistry                                  |   |
|  |    46 tools, each calls Jira REST API          |   |
|  |                                                |   |
|  |  JiraRestClient (shared HTTP client)           |   |
|  |    Calls localhost/rest/api/2/...              |   |
|  |    Forwards user's PAT from request            |   |
|  |                                                |   |
|  |  AdminServlet + ConfigResource                 |   |
|  |    /plugins/servlet/mcp-admin                  |   |
|  |    /rest/mcp-admin/1.0/                        |   |
|  |    PluginSettings persistence                  |   |
|  +-----------------------------------------------+   |
|                                                      |
|  Jira REST API (already exists)                      |
|    /rest/api/2/search, /rest/api/2/issue, etc.       |
+-----------------------------------------------------+
```

### Components

**McpResource** -- single JAX-RS resource class. Handles all MCP protocol traffic.
Parses JSON-RPC messages, dispatches by method name. Stateless -- no server-side
session tracking. Each request is independent (sessions deferred to future SSE support).

**ToolRegistry** -- holds `Map<String, McpTool>` of all 46 tools as a static catalog.
At startup, performs capability detection: tools requiring Jira Software, JSM, or
Proforma are tagged as unavailable if the product is not installed. Admin config
filtering (disabled tools, user allowlist, read-only mode) happens at **request time**
in `tools/list` and `tools/call`, not at startup, so config changes take effect
immediately without restart.

**JiraRestClient** -- lightweight HTTP client wrapper. Base URL resolved from
`ApplicationProperties.getBaseUrl()` at startup, with an admin-configurable override
for environments where the internal URL differs from the public one (e.g., Docker
containers). Copies the user's `Authorization` header from the incoming MCP request.
Shared by all tools.

**AdminServlet** -- renders Velocity template for the admin config page. System admin
access only.

**ConfigResource** -- JAX-RS resource for admin config CRUD. Reads/writes PluginSettings.

**McpPluginConfig** -- reads PluginSettings and exposes typed accessors: `isEnabled()`,
`isUserAllowed(userKey)`, `isToolEnabled(toolName)`, `isReadOnlyMode()`,
`getJiraBaseUrlOverride()`.

## MCP Protocol

### POST /rest/mcp/1.0/

Receives JSON-RPC 2.0 messages. Returns `application/json`.

| Method | Action | Response |
|---|---|---|
| `initialize` | Return server info + capabilities (tools only). No session ID (stateless). | `InitializeResult` |
| `notifications/initialized` | Acknowledge. | 202 Accepted |
| `tools/list` | Return tool definitions filtered by user + admin config. | `ListToolsResult` |
| `tools/call` | Dispatch to tool, return result. | `CallToolResult` |
| `ping` | Keep-alive. | Empty result |

Malformed JSON or unparseable JSON-RPC returns error code `-32700` (Parse Error).

### Protocol Rules

- **Batch requests:** Not supported. If the request body is a JSON array, return
  `-32600` Invalid Request.
- **Protocol version:** `MCP-Protocol-Version` header is required after `initialize`.
  If missing or unsupported, return HTTP 400 with a JSON body explaining the issue.
  During `initialize`, the version is negotiated (we accept `2025-06-18`).
- **Notifications:** Return HTTP 202 with empty body. No JSON-RPC response.
- **Auth failures:** Jira returns HTTP 401 before our code runs. If the plugin's
  access control rejects the user (not in allowlist), return HTTP 403 with a JSON
  body (not a JSON-RPC error, since this is an HTTP-level concern).

### GET /rest/mcp/1.0/

Returns 405 Method Not Allowed. SSE streaming deferred to future version.

### DELETE /rest/mcp/1.0/

Returns 405 Method Not Allowed. Sessions deferred to future version.

### Headers

| Header | Direction | Purpose |
|---|---|---|
| `MCP-Protocol-Version` | Request | Client declares protocol version (accept `2025-06-18`) |
| `Authorization` | Request | Jira PAT, validated by Jira before our code runs |

Note: `MCP-Session-Id` is not used in v1 (stateless). Clients that send it are not
rejected; the header is simply ignored.

### Error Codes

| Code | Meaning |
|---|---|
| -32700 | Parse Error (malformed JSON) |
| -32600 | Invalid Request (valid JSON but not a valid JSON-RPC message) |
| -32601 | Method Not Found |
| -32602 | Invalid Params (bad tool arguments) |
| -32603 | Internal Error (Jira API failure) |

## Tool Architecture

### Interface

```java
public interface McpTool {
    String name();
    String description();
    Map<String, Object> inputSchema();
    String execute(Map<String, Object> args, String authHeader);
}
```

Each tool returns a JSON string (text content for CallToolResult). This matches the
upstream Python project's output format (`json.dumps(result, indent=2)`). Tools return
the Jira REST API response as-is or lightly simplified. Binary content (attachments,
images) is base64-encoded with metadata.

The `authHeader` is the user's `Authorization: Bearer <PAT>` forwarded from the incoming
MCP request.

### JiraRestClient

```java
class JiraRestClient {
    String get(String path, String authHeader);
    String post(String path, String body, String authHeader);
    String put(String path, String body, String authHeader);
    String delete(String path, String authHeader);
}
```

Base URL defaults to `ApplicationProperties.getBaseUrl()` (e.g.,
`https://bpm.example.com`). Admin can override in plugin config for environments
where internal and external URLs differ (e.g., `http://localhost:8080/jira`).
The effective base URL is resolved from config **on each call**, not cached at
startup, so admin changes take effect immediately. No hardcoded URLs.

Connection timeout: 5 seconds. Read timeout: 30 seconds (matches tool response timeout).
On timeout or connection failure, the tool returns a `-32603` Internal Error with a
message describing the failure. Redirects are not followed (Jira REST API should not
redirect on localhost calls).

### Tool Categories (46 tools, ported from upstream v0.21.0)

| Category | Tools | Count |
|---|---|---|
| Issues and Search | search, get_issue, create_issue, update_issue, delete_issue, batch_create_issues, batch_get_changelogs | 7 |
| Comments | add_comment, edit_comment | 2 |
| Transitions | get_transitions, transition_issue | 2 |
| Worklogs | get_worklog, add_worklog | 2 |
| Boards and Sprints | get_agile_boards, get_board_issues, get_sprints_from_board, get_sprint_issues | 4 |
| Links | get_link_types, create_issue_link, create_remote_issue_link, remove_issue_link | 4 |
| Epics | link_to_epic | 1 |
| Projects and Versions | get_all_projects, get_project_issues, get_project_versions, get_project_components, create_version, batch_create_versions | 6 |
| Users and Watchers | get_user_profile, get_issue_watchers, add_watcher, remove_watcher | 4 |
| Attachments | download_attachments, get_issue_images | 2 |
| Fields | search_fields, get_field_options | 2 |
| Service Desk | get_service_desk_for_project, get_service_desk_queues, get_queue_issues | 3 |
| Forms | get_issue_proforma_forms, get_proforma_form_details, update_proforma_form_answers | 3 |
| Dates and Metrics | get_issue_dates, get_issue_sla, get_issue_development_info, get_issues_development_info | 4 |

Write tools (create, update, delete, transition, add_comment, etc.) are tagged so
`readOnlyMode` can filter them out.

### Capability Detection

Not all tools work on every Jira instance. At plugin startup, ToolRegistry checks
which products/apps are installed and only registers applicable tools:

| Requirement | Detection | Tools affected |
|---|---|---|
| Jira Software | Check if `com.atlassian.jira.plugins.jira-software-plugin` is installed | Boards, sprints (4 tools) |
| Jira Service Management | Check if `com.atlassian.servicedesk` is installed | Service desk, queues, SLA (4 tools) |
| Proforma / Forms | Check if `com.atlassian.proforma` is installed | Forms (3 tools) |

Development info tools (2) are always registered -- availability varies per issue/project,
so errors are handled at call time rather than gated at startup.

All 46 tools are registered in an internal catalog. Tools whose requirements are not met
are excluded from `tools/list` but remain in the catalog. If a client calls a hidden tool
directly, the error message specifies what is missing (e.g., "Tool 'get_agile_boards'
requires Jira Software which is not installed on this instance").

### Response Limits

Tools that return collections enforce default limits to prevent oversized responses:

| Parameter | Default | Hard cap |
|---|---|---|
| `maxResults` (search, board issues, sprint issues, etc.) | 50 | 200 |
| Attachment download size | 5 MB per file | 10 MB |
| Response timeout (per tool call) | 30 seconds | 60 seconds |

Tools that support pagination accept `startAt` and `maxResults` parameters matching
the upstream Python project's interface.

Each tool class includes a comment: `// Ported from mcp-atlassian v0.21.0 -- src/mcp_atlassian/servers/jira.py:{function_name}`

## Admin Configuration

### Storage (PluginSettings)

| Key | Type | Default | Description |
|---|---|---|---|
| `com.atlassian.mcp.plugin.enabled` | boolean | false | Global MCP on/off |
| `com.atlassian.mcp.plugin.allowedUsers` | string | "" | Comma-separated user keys (stable Jira identity); empty = all authenticated users |
| `com.atlassian.mcp.plugin.disabledTools` | string | "" | Comma-separated tool names to hide |
| `com.atlassian.mcp.plugin.readOnlyMode` | boolean | false | Hide all write tools |
| `com.atlassian.mcp.plugin.jiraBaseUrl` | string | "" | Override internal Jira base URL for REST calls; empty = use ApplicationProperties.getBaseUrl() |

Note: `allowedUsers` uses Jira `userKey` (stable internal identifier), not username
(which can change). The admin UI resolves display names for readability but stores keys.

### Access Control Flow (every MCP request)

1. Jira validates PAT (before our code)
2. Check `enabled` -- if false, return 503 Service Unavailable
3. Check `allowedUsers` -- if non-empty and user not in list, return 403
4. `tools/list` filters out disabled tools + write tools if read-only mode
5. `tools/call` rejects calls to disabled/hidden tools

### Admin UI

- Location: Jira Admin sidebar > System > "MCP Server Configuration"
- Condition: `UserIsAdminCondition` (system admins only)
- Fields: enable toggle, allowed users textarea (resolves display names), disabled tools textarea, read-only checkbox, internal base URL override (optional)
- Save via AJAX to `/rest/mcp-admin/1.0/`

## Project Structure

```
atlassian-mcp-plugin/
+-- .mise.toml
+-- justfile
+-- upstream-versions.json
+-- pom.xml
+-- src/
|   +-- main/
|   |   +-- java/com/atlassian/mcp/plugin/
|   |   |   +-- McpResource.java
|   |   |   +-- JsonRpcHandler.java
|   |   |   +-- JiraRestClient.java
|   |   |   +-- admin/
|   |   |   |   +-- AdminServlet.java
|   |   |   |   +-- ConfigResource.java
|   |   |   +-- config/
|   |   |   |   +-- McpPluginConfig.java
|   |   |   +-- tools/
|   |   |       +-- McpTool.java
|   |   |       +-- ToolRegistry.java
|   |   |       +-- issues/
|   |   |       |   +-- SearchTool.java
|   |   |       |   +-- GetIssueTool.java
|   |   |       |   +-- CreateIssueTool.java
|   |   |       |   +-- ...
|   |   |       +-- comments/
|   |   |       +-- transitions/
|   |   |       +-- boards/
|   |   |       +-- projects/
|   |   |       +-- users/
|   |   |       +-- ...
|   |   +-- resources/
|   |       +-- atlassian-plugin.xml
|   |       +-- mcp-plugin.properties
|   |       +-- templates/admin.vm
|   |       +-- css/admin.css
|   |       +-- js/admin.js
|   +-- test/
|       +-- java/com/atlassian/mcp/plugin/
|           +-- McpResourceTest.java
|           +-- JsonRpcHandlerTest.java
|           +-- tools/SearchToolTest.java
+-- docs/
+-- .upstream/                    # gitignored
    +-- mcp-atlassian/            # v0.21.0
    +-- java-sdk/                 # reference only
    +-- myplugin/                 # sample plugin
```

## Build and Toolchain

**`.mise.toml`:**

```toml
[tools]
just = "latest"
java = "temurin-17"
maven = "3.9"
```

Atlassian Plugin SDK (`atlas-*` commands) installed via nix.

**Key Maven dependencies (all provided scope):**

- `com.atlassian.jira:jira-api`
- `com.atlassian.sal:sal-api`
- `com.atlassian.templaterenderer:atlassian-template-renderer-api`
- `jakarta.servlet:jakarta.servlet-api`
- `jakarta.ws.rs:jakarta.ws.rs-api`
- `jakarta.inject:jakarta.inject-api`

No external runtime dependencies. Jackson provided by Jira.

**Justfile recipes:**

```just
build:    atlas-package
run:      atlas-run
debug:    atlas-debug
clean:    atlas-clean
test:     atlas-unit-test
```

## Deployment

1. Build: `just build` produces OBR/JAR in `target/`
2. Upload to Jira via UPM (Manage Apps > Upload)
3. Enable and configure in Jira Admin > System > MCP Server Configuration
4. Users connect AI agents to: `https://bpm.yourdomain.com/rest/mcp/1.0/`

## MCP Client Configuration

Users configure their MCP client (Claude, Cursor, etc.):

```json
{
  "mcpServers": {
    "jira": {
      "url": "https://bpm.yourdomain.com/rest/mcp/1.0/",
      "transport": "streamable-http",
      "headers": {
        "Authorization": "Bearer <JIRA_PAT>"
      }
    }
  }
}
```

## Future Extensions

- SSE streaming (GET endpoint) for long-running operations
- Per-user tool permissions via Active Objects
- Confluence support (second product in same plugin or separate plugin)
- Tool usage analytics and audit logging
- Rate limiting per user
