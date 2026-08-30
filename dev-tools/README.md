# dev-tools

Local development harness for visually testing MCP Apps widgets (the Issue
Card) without deploying to Claude.ai / ChatGPT / VS Code Copilot.

```text
dev-tools/
├── basic-host/          # Vendored from @modelcontextprotocol/ext-apps@1.7.2,
│                        # examples/basic-host. Local mini-host: shows tools,
│                        # calls them, renders widget in sandboxed iframe.
└── dev-host-proxy.mjs   # ~100-line Node proxy. basic-host can't send the
                         # Authorization header our plugin requires; this
                         # injects Bearer $JIRA_PAT_RKADMIN and forwards to
                         # $JIRA_URL/plugins/servlet/mcp. Env vars loaded by
                         # mise from .credentials/jira.env (gitignored).

../Procfile.dev-host     # Process manifest for hivemind. Lives at repo root
                         # so hivemind cwd = repo root and paths resolve
                         # cleanly. Follows the same convention as the
                         # am-web project (Procfile.<mode>).
```

## Usage

```bash
mise run dev:host          # one-shot: builds basic-host + starts proxy + serves UI
```

Then open <http://localhost:8080>:

1. Server: pick `jira-mcp-plugin` (the proxy at `http://localhost:3001/mcp`)
2. Tool: pick one of the five UI-linked tools — `get_issue`, `search`,
   `get_project_issues`, `get_board_issues`, `get_sprint_issues`
3. Fill the JSON input (the form pre-populates schema `default` values; required
   fields without defaults — like `issue_key` — you must add manually)
4. Click **Call Tool**. Tool result panel shows the JSON; widget iframe
   renders the Issue Card.

## What basic-host is

A reference host implementation from Anthropic's official MCP Apps SDK — a
stripped-down clone of how Claude.ai talks to an MCP server, **without an
LLM**. It's a "Postman for MCP" + widget renderer. Use it to iterate on the
widget without the 5-minute deploy-and-test loop through a real host.

See <https://github.com/modelcontextprotocol/ext-apps/tree/main/examples/basic-host>
for the upstream source.

## Why vendored, not symlinked

`.upstream/` is gitignored (reference material), so a symlink wouldn't survive
a fresh clone. We vendor only the seven source files (`serve.ts`, `src/`,
`*.html`, `*.config.ts`, `package.json`) — ~10 KB. `node_modules/` and `dist/`
are gitignored and rebuilt on `mise run dev:host`.

To pull updates from upstream, copy the same files again from
`.upstream/ext-apps/examples/basic-host/`.

## Secrets

Nothing in this folder is secret. The PAT lives in `.credentials/jira.env`
(gitignored) and is injected at runtime by `mise` into the proxy's env.
