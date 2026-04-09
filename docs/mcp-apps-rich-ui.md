# MCP Apps — Rich UI Rendering in Claude/ChatGPT

Findings from analyzing Todoist AI MCP server (`.upstream/todoist-ai/`).

## What It Is

MCP Apps is an extension to MCP that lets servers ship embedded UI widgets alongside their tools. When a tool returns results, the client (Claude.ai, ChatGPT) renders them using the widget instead of plain text.

Example: Todoist's `find-tasks-by-date` renders an interactive task list with checkboxes instead of raw JSON.

## How Todoist Implements It

### Three layers:

**1. React widget** (`src/mcp-apps/task-list/`)

A self-contained React app built with Vite into a single HTML file:
- `task-list.tsx` — renders the task list
- `task-item.tsx` — individual task row with checkbox
- `styles.css` — styling
- `index.html` — entry point

Built via `vite.config.ts` into one bundled HTML file (no external assets).

**2. MCP App Resource registration** (`src/mcp-apps/resources.ts`)

```typescript
const taskListResourceUri = `ui://todoist/task-list@${hash}`

registerAppResource(server, 'todoist-task-list', taskListResourceUri, {
    description: 'Interactive task list widget',
    _meta: {
        ui: {
            prefersBorder: true,
            csp: { connectDomains: [], resourceDomains: [] },
        },
    },
}, async () => ({
    contents: [{
        uri: taskListResourceUri,
        mimeType: RESOURCE_MIME_TYPE,  // 'application/vnd.mcp.app+html'
        text: taskListHtml,            // the bundled HTML
    }],
}))
```

**3. Tool → UI binding** (`src/mcp-server.ts:188-194`)

```typescript
const findTasksByDateToolWithUi = {
    ...findTasksByDate,
    _meta: {
        ui: { resourceUri: taskListResourceUri },
    },
}
```

This tells Claude: "when this tool returns results, render them using the task-list widget."

### Data flow

```
Claude calls find-tasks-by-date
  → tool returns { textContent, structuredContent }
  → tool._meta.ui.resourceUri → points to the HTML widget resource
  → Claude loads the widget, passes structuredContent as data
  → widget renders the rich card (checkboxes, task names, due dates)
```

### Tool output structure

Each tool returns both formats (`src/todoist-tool.ts`):
- `textContent` — plain text summary (for LLMs to reason about)
- `structuredContent` — typed JSON matching `outputSchema` (for UI rendering)
- `contentItems` — optional extra content blocks (images, embedded resources)

The `getToolOutput()` helper (`src/mcp-helpers.ts`) assembles the response, with legacy fallback for clients that don't support `structuredContent`.

## Key Libraries

- `@modelcontextprotocol/ext-apps/server` — `registerAppTool()`, `registerAppResource()`, `RESOURCE_MIME_TYPE`
- `@modelcontextprotocol/sdk/server/mcp.js` — standard MCP server
- Vite — builds the React widget into self-contained HTML

## Client Support

- **Claude.ai** — renders MCP App widgets as rich cards
- **ChatGPT** — supported via `openai/widget*` metadata fields in `_meta`
- **Claude Desktop** — likely supported (connectors configured via claude.ai)
- **Claude Code / Cursor** — CLI/terminal, no rich rendering

## Considerations for Jira MCP Plugin

### Challenges
- Our plugin is Java (JAX-RS), not Node.js — can't use the TypeScript SDK helpers directly
- MCP Apps registration happens at the MCP protocol level (`_meta` on tool definitions and resources)
- We'd need to serve the widget HTML as an MCP resource via our endpoint

### Potential approach
- Build a standalone HTML/JS widget (Jira issue card with status, priority, assignee)
- Bundle it into the plugin JAR as a static resource
- Serve it as an MCP resource when clients request it
- Add `_meta.ui.resourceUri` to relevant tools (e.g., `get_issue`, `search`)
- Return `structuredContent` alongside `textContent` in tool responses

### Candidate tools for rich rendering
- `get_issue` — issue card with status badge, priority, assignee, dates
- `search` — issue list with compact rows
- `get_project_issues` — project board view
- `get_sprint_issues` — sprint board
- `get_agile_boards` — board selector
