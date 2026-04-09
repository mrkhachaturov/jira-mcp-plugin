# MCP Apps Support for Jira MCP Plugin

**Date:** 2026-04-08
**Status:** Design approved, pending implementation
**Branch:** main

## Summary

Add MCP Apps (interactive UI) support to the Jira MCP plugin. When a user asks
about Jira issues, the AI client (Claude, ChatGPT, VS Code Copilot, etc.)
renders an interactive Issue Card widget inline in the conversation instead of
plain text. The widget displays issue details, status badges, and allows
lightweight actions (transitions, comments, assignment) without leaving the chat.

## Goals

- Render Jira issues as interactive cards in Claude, ChatGPT, and other MCP
  Apps-compatible clients
- Support lightweight write actions from the widget (transition, comment, assign)
- Maintain full backward compatibility with text-only clients
- Follow the Todoist production pattern: one codebase, dual metadata, no client
  branching

## Non-Goals

- Board/kanban view widget (future iteration)
- Project dashboard widget (future iteration)
- Full inline field editing (summary, description, priority)
- Custom themes beyond host style integration

## Architecture

```
MCP Client (Claude, ChatGPT, VS Code Copilot, etc.)
  │
  │ 1. tools/list → sees _meta.ui.resourceUri on 5 issue tools
  │ 2. tools/call → gets { content + structuredContent }
  │ 3. resources/read → fetches ui://jira/issue-card@{hash} HTML
  │ 4. Renders HTML in sandboxed iframe
  │ 5. Widget calls tools via postMessage (transitions, etc.)
  │
  ▼
Jira MCP Plugin (Java / JAX-RS)
  │
  ├── McpResource.java         POST /rest/mcp/1.0/
  ├── JsonRpcHandler.java      routes: initialize, tools/*, resources/*
  ├── ResourceRegistry.java    manages ui:// resources (NEW)
  ├── ToolRegistry.java        49 tools, 5 with _meta.ui
  │
  └── classpath: /mcp-app/issue-card.html (built by Vite)

mcp-app/ (repo root, separate React project)
  │
  ├── React 19 + Vite + viteSingleFile
  ├── src/issue-card/          components, styles
  └── dist/issue-card.html     single-file build output → copied to JAR
```

### Data Flow

1. Client calls `tools/list` -- sees `get_issue` has
   `_meta.ui.resourceUri: "ui://jira/issue-card@{hash}"`
2. Client calls `tools/call` for `get_issue` -- gets back `content` (text
   fallback) + `structuredContent` (typed JSON with issue data)
3. Client requests `resources/read` for `ui://jira/issue-card@{hash}` -- server
   reads bundled HTML from classpath, returns as `TextResourceContents`
4. Client renders HTML in sandboxed iframe, passes `structuredContent` via
   `ui/notifications/tool-result`
5. User clicks "Transition to In Progress" in widget -- widget calls
   `app.callServerTool({name: "transition_issue", arguments: {...}})` -- server
   executes, widget updates

## Server-Side Changes (Java)

### JsonRpcHandler: Two New Methods

The handler's method switch gets two additions:

**`resources/list`** -- returns the list of registered `ui://` resources with
metadata.

**`resources/read`** -- takes `{ uri }`, reads bundled HTML from classpath,
returns:

```json
{
  "contents": [{
    "uri": "ui://jira/issue-card@abc123",
    "mimeType": "text/html;profile=mcp-app",
    "text": "<!doctype html>...",
    "_meta": {
      "ui": { "prefersBorder": true, "csp": { "connectDomains": [], "resourceDomains": [] } },
      "openai/widgetDescription": "Interactive Jira issue viewer with transitions and comments",
      "openai/widgetPrefersBorder": true,
      "openai/widgetCSP": { "connect_domains": [], "resource_domains": [] },
      "openai/widgetDomain": "https://bpm.astrateam.net"
    }
  }]
}
```

### Initialize: Advertise Resources + Extensions

Current initialize only declares `capabilities.tools`. Add:

```json
{
  "capabilities": {
    "tools": { "listChanged": false },
    "resources": {},
    "experimental": {
      "io.modelcontextprotocol/ui": {}
    }
  }
}
```

No per-session capability gating. `_meta.ui` is always included on UI-linked
tools when the widget resource is registered. Clients that do not support MCP
Apps ignore unknown fields. This follows the Todoist pattern: no client
branching, no conditional logic.

### tools/list: Add _meta and annotations

**annotations** on every tool:

```json
"annotations": {
  "readOnlyHint": true,
  "destructiveHint": false
}
```

Derived from existing `isWriteTool()`. A new `isDestructiveTool()` default
method on `McpTool` interface returns `false` unless overridden.

Exhaustive destructive tool list (all others get `destructiveHint: false`):
- `delete_issue`
- `remove_issue_link`
- `remove_watcher`

**_meta** on the 6 issue-returning tools (always, when resource is registered):

```json
"_meta": {
  "ui": { "resourceUri": "ui://jira/issue-card@abc123" }
}
```

### Tool Results: Add structuredContent

UI-linked tools return `structuredContent` alongside existing `content`.
The `content[0].text` is the existing response (backward compatible). The
`structuredContent` is additive -- clients that don't understand it ignore it.

#### Normalization contract

All 6 UI-linked tools produce the same `structuredContent` envelope regardless
of which Jira REST endpoint they call internally. The server normalizes before
returning:

```json
{
  "content": [{ "type": "text", "text": "PROJ-123: Fix login bug\nStatus: In Progress\n..." }],
  "structuredContent": {
    "currentUser": { "name": "rkadmin", "displayName": "Ruben" },
    "issues": [{
      "key": "PROJ-123",
      "summary": "Fix login bug",
      "status": { "name": "In Progress", "category": "indeterminate" },
      "priority": { "name": "High" },
      "issue_type": { "name": "Bug" },
      "assignee": { "displayName": "Ruben", "name": "rkadmin" },
      "reporter": { "displayName": "..." },
      "description": "...",
      "comments": [{ "author": "...", "body": "...", "created": "..." }],
      "created": "2026-04-01T10:00:00Z",
      "updated": "2026-04-08T14:30:00Z"
    }],
    "totalCount": 1,
    "baseUrl": "https://bpm.astrateam.net"
  }
}
```

**Mandatory fields** (always present in each issue, even if empty/null):
`key`, `summary`, `status.name`, `priority.name`, `issue_type.name`,
`assignee`, `reporter`, `created`, `updated`.

**Optional fields** (included when available): `description`, `comments`,
`status.category`, `labels`, `components`, `fix_versions`.

**Per-tool normalization:**

| Tool | Jira API | Normalization |
|------|----------|---------------|
| `get_issue` | `/rest/api/2/issue/{key}` | Single issue wrapped in `issues[]` |
| `search` | `/rest/api/2/search` | `response.issues` extracted, forced field set via `fields` param |
| `get_project_issues` | `/rest/api/2/search` (JQL) | Same as `search` |
| `get_board_issues` | `/rest/agile/1.0/board/{id}/issue` | Agile envelope unwrapped, mapped to common shape |
| `get_sprint_issues` | `/rest/agile/1.0/sprint/{id}/issue` | Same as board |

All UI-linked tools ensure a minimum Jira field set is present by unioning the
caller's requested fields with the UI-required fields:
`summary,status,assignee,reporter,priority,issuetype,created,updated,description,comment`.
If the caller requests `*all` or a custom field list, those are preserved and
the UI fields are added. The `content[0].text` response is built from the full
field set (caller + UI), maintaining backward compatibility. The
`structuredContent` is derived from the same superset response.

**`currentUser`** is resolved from the authenticated user on the request (via
Jira's `UserManager.getRemoteUser()`). This gives the widget the identity
needed for "Assign to me" without an extra tool call.

`baseUrl` lets the widget construct browse URLs (`/browse/PROJ-123`).

### ResourceRegistry: New Class

Simple registry managing `ui://` resources:

- Loads HTML from classpath (`/mcp-app/issue-card.html`) on plugin init
- Computes SHA256 hash (first 12 chars) of HTML content for cache-busting URI
- Provides lookup by URI for `resources/read`
- Holds resource metadata (CSP, widget descriptions, dual Claude/OpenAI fields)
- If HTML file is missing from classpath: does NOT register the resource, does
  NOT attach `_meta.ui` to any tool. The plugin works normally without MCP Apps
  rather than serving broken widget content. Logs a warning at startup.

### Tools Linked to Issue Card Widget

| Tool | Returns | Gets _meta.ui |
|------|---------|:---:|
| `get_issue` | Single issue | Yes |
| `search` | Issue list | Yes |
| `get_project_issues` | Issue list | Yes |
| `get_board_issues` | Issue list | Yes |
| `get_sprint_issues` | Issue list | Yes |

`get_queue_issues` is excluded from v1. The JSM queue endpoint has a different
response shape, no `fields` parameter, and JSM comments have public/internal
semantics that the widget does not handle. Queue support is deferred to a future
iteration.

All other tools (44) get `annotations` but not `_meta.ui`.

## React Widget (Issue Card)

### Project Structure

```
mcp-app/
  package.json              # React 19, @modelcontextprotocol/ext-apps, vite
  vite.config.ts            # viteSingleFile, inline everything
  tsconfig.json
  src/
    issue-card/
      index.html            # Entry point
      main.tsx              # React DOM render
      app.tsx               # Root: useApp(), ontoolinput/ontoolresult
      types.ts              # TypeScript types matching structuredContent
      components/
        issue-list.tsx      # List view (search, get_project_issues, etc.)
        issue-detail.tsx    # Single issue expanded view
        status-badge.tsx    # Colored status pill
        priority-icon.tsx   # Priority indicator
        action-bar.tsx      # Transition, comment, assign buttons
        comment-form.tsx    # Inline comment input
        transition-picker.tsx  # Status transition dropdown
        loading.tsx         # Loading skeleton
        empty.tsx           # Empty state
      styles/
        global.css          # CSS variables, host style integration
        *.module.css        # Component-scoped styles
```

### Two Rendering Modes

The widget detects the `structuredContent` shape and renders accordingly:

**List mode** -- `structuredContent.issues` has 2+ items (from `search`,
`get_project_issues`, etc.):
- Compact rows: issue key (link), summary, status badge, priority icon, assignee
- Click a row to expand inline to detail view
- Shows `totalCount` and pagination info

**Detail mode** -- `structuredContent.issues` has 1 item (from `get_issue`):
- Full card: key, summary, status, priority, assignee, reporter
- Description (plain text, truncated with expand)
- Latest comments (3-5)
- Action bar at bottom

### Interactive Actions

Three actions calling server tools via `app.callServerTool()`:

| Action | UI Element | Server Tool | Arguments |
|--------|-----------|-------------|-----------|
| Transition | Status badge click -> dropdown | `transition_issue` | `issue_key`, `transition_id` |
| Comment | Action bar -> text input | `add_comment` | `issue_key`, `body` |
| Assign to me | Action bar -> button | `update_issue` | `issue_key`, `fields` |

The transition dropdown is populated dynamically by calling `get_transitions`
when the user clicks the status badge. The server enriches the transition
response with a `hasRequiredFields` boolean per transition (derived from the
Jira transition's `fields` property — if any field has `required: true`, the
transition has required fields). The widget only shows transitions where
`hasRequiredFields` is `false`. Transitions that require additional screen
fields (e.g., resolution when closing) are excluded from the dropdown — the
user can still perform those via chat.

**Post-action refresh flow:**

After any write action succeeds, the widget calls `get_issue` with the issue
key to fetch a fresh snapshot, then replaces the card state with the new data.
This avoids stale UI and ensures transitions, comments, and assignment changes
are immediately visible.

```text
User clicks action → callServerTool(write_tool) → success?
  → yes: callServerTool("get_issue", {issue_key}) → update widget state
  → no:  show inline error message, keep current state
```

**Assign to me:**

The widget reads `structuredContent.currentUser.name` (populated by the server
from the authenticated user) and constructs the payload:
`{"assignee": "<currentUser.name>"}`. This matches the existing `update_issue`
contract where `fields` is a JSON string with flat field values. No extra tool
call needed to discover user identity.

### Host Style Integration

Uses `useHostStyles()` from `@modelcontextprotocol/ext-apps/react` to inherit
the host's color scheme. CSS variables as fallbacks:

```css
:root {
  --bg: var(--host-bg, #ffffff);
  --text: var(--host-text, #1a1a1a);
  --border: var(--host-border, #e5e5e5);
  --accent: var(--host-accent, #0052cc);  /* Jira blue */
}
```

### Bridge Compatibility

The widget uses the MCP Apps standard bridge, with optional ChatGPT extensions:

```typescript
// Standard MCP Apps -- works on Claude, ChatGPT, VS Code, Goose
const { app } = useApp({
  appInfo: { name: 'jira-issue-card', version: '1.0.0' },
  capabilities: {},
})

// Optional ChatGPT extension -- feature-detect, graceful fallback
const openai = typeof window !== 'undefined' ? window.openai : undefined
if (openai?.requestDisplayMode) {
  await openai.requestDisplayMode({ mode: 'fullscreen' })
}
```

## Compatibility

### Verified Client Support

| Client | MCP Apps | Source |
|--------|:---:|---|
| Claude (web + desktop) | Yes | modelcontextprotocol.io client matrix |
| ChatGPT | Yes | developers.openai.com MCP Apps compatibility |
| VS Code GitHub Copilot | Yes | modelcontextprotocol.io client matrix |
| Goose | Yes | modelcontextprotocol.io client matrix |
| Postman | Yes | modelcontextprotocol.io client matrix |
| Claude Code / Cursor | No | CLI only, no iframe rendering |

### Dual Metadata (Following Todoist Pattern)

One `_meta` object containing both Claude and ChatGPT fields. Each client
ignores what it does not understand. No client detection, no branching.

```json
{
  "ui": {
    "prefersBorder": true,
    "csp": { "connectDomains": [], "resourceDomains": [] }
  },
  "openai/widgetDescription": "Interactive Jira issue viewer with transitions and comments",
  "openai/widgetPrefersBorder": true,
  "openai/widgetCSP": { "connect_domains": [], "resource_domains": [] },
  "openai/widgetDomain": "<jiraBaseUrl>"
}
```

The `openai/widgetDomain` and `baseUrl` in `structuredContent` are resolved at
runtime from `McpPluginConfig` (the configured Jira base URL override, or the
detected Jira base URL). Not hardcoded.

### Text Fallback

Nothing changes for non-UI clients. `content[0].text` is the same response
returned today. `structuredContent` and `_meta.ui` are additive. Zero
regression risk.

### Tool Annotations on All 49 Tools

`readOnlyHint` and `destructiveHint` go on every tool regardless of UI support.
Benefits all clients -- ChatGPT uses `readOnlyHint` for safety classification,
Claude uses it for confirmation decisions.

| Category | readOnlyHint | destructiveHint | Examples |
|----------|:---:|:---:|---|
| Read tools | true | false | `search`, `get_issue`, `get_all_projects` |
| Write tools | false | false | `create_issue`, `update_issue`, `add_comment` |
| Destructive tools | false | true | `delete_issue`, `remove_issue_link`, `remove_watcher` |

Exhaustive destructive tool list (matches the `isDestructiveTool()` section
above): `delete_issue`, `remove_issue_link`, `remove_watcher`. All other write
tools get `destructiveHint: false`.

## Build Pipeline

### justfile Additions

```makefile
build-app       # cd mcp-app && npm run build
                # copies dist/issue-card.html -> src/main/resources/mcp-app/

build           # build-app first, then atlas-package (HTML in classpath before JAR)

deploy          # build + upload JAR (unchanged, now includes widget)

dev-app         # cd mcp-app && npm run dev (Vite dev server for widget iteration)
```

### Maven Integration

The `justfile` is the local developer workflow. For CI (GitHub Actions), the
frontend build must also run before `mvn package`. Two approaches (choose
during implementation):

1. **Maven exec plugin** -- add an `exec-maven-plugin` execution in
   `generate-resources` phase that runs `npm ci && npm run build` in
   `mcp-app/` and copies output to `src/main/resources/mcp-app/`. This makes
   `mvn package` self-contained.
2. **CI workflow step** -- add a step before `mvn package` in the GitHub
   Actions workflow that runs `just build-app`. Simpler but requires `just`
   and `node` in CI.

Either way, the build must fail if `src/main/resources/mcp-app/issue-card.html`
is missing after the frontend build step. The plugin degrades gracefully at
runtime (no resource registered), but a missing widget in a release build is a
packaging error.

### Development Workflow

**Widget development** (fast iteration):
`just dev-app` starts Vite dev server with hot reload. Test against mock data
using the `basic-host` example from the ext-apps repo.

**Full integration:**
`just deploy-and-test` builds app + JAR, deploys to Jira, runs e2e tests.

### Git Tracking

```text
mcp-app/                      # committed (source)
mcp-app/dist/                 # gitignored (build artifact)
mcp-app/node_modules/         # gitignored
src/main/resources/mcp-app/   # gitignored (copied from dist during build)
```

### E2E Test Additions

| Test | Verifies |
|------|----------|
| `resources/list` returns issue-card resource | Resource discovery |
| `resources/read` with valid URI returns HTML | Resource serving |
| `resources/read` with unknown URI returns error | Error handling |
| `tools/list` includes `_meta.ui` on issue tools | Tool-UI binding |
| `tools/list` includes `annotations` on all tools | Annotation support |
| `get_issue` result includes `structuredContent` | Structured data |
| Initialize advertises `resources` capability | Capability negotiation |

### Version Bump

Feature release: bump to next minor version (e.g., 1.1.1 -> 1.2.0).

## Reference Implementations

- **Todoist MCP server** (`.upstream/todoist-ai/`): Production MCP Apps with
  React widget, dual metadata, `registerAppTool`/`registerAppResource` pattern
- **Java MCP SDK** (`.upstream/java-sdk/`): `McpSchema.java` defines `Tool`,
  `ToolAnnotations`, `Resource`, `TextResourceContents`, `ReadResourceResult`
- **MCP Apps spec**: modelcontextprotocol.io/extensions/apps
- **OpenAI MCP Apps compat**: developers.openai.com/apps-sdk/mcp-apps-in-chatgpt
