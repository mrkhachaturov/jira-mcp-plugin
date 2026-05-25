---
name: mcp-docs
description: This skill should be used when the user asks about the Model Context Protocol — "what does the MCP spec say about X", "look up SEP-991", "how does Streamable HTTP work", "MCP Apps SDK lifecycle", "JSON-RPC method shape", "capability negotiation", "OAuth + RFC 8707", "session management", "completion/complete", "resource templates", "sampling", or any wording in the spec or extensions. Provides semantic search over the locally mirrored MCP specification (all versions), SEPs, Apps SDK / Auth / Tasks extensions, and Anthropic MCP blog posts. Use BEFORE WebFetch'ing modelcontextprotocol.io — the local mirror is fresher and rate-limit-free.
---

# MCP Documentation Search

Search the official Model Context Protocol docs mirrored locally and indexed in Miyo. Cite source files inline so the user can open them.

## Searching the Docs

Always pass `folder_path: "mcp-docs"` — without it Miyo searches across every indexed folder (RouterOS, codex-docs, etc.) and pollutes the results.

```
mcp__miyo__search(
  query: "<natural-language question>",
  folder_path: "mcp-docs",
  path: "<optional subtree filter>",
  limit: 5
)
```

Synthesize from the top 3–5 hits. Cite source files inline as `[file.md](wiki/mcp-docs/<returned-path>/file.md)`.

## Path Scoping

Narrow with `path:` when the area is known. The filter is a substring match against the returned path.

| Topic | `path:` filter |
|---|---|
| Current stable spec | `specification/2025-11-25/` |
| Draft spec (in-flight changes) | `specification/draft/` |
| Spec history (all versions) | `specification/` |
| Spec Enhancement Proposals | `seps/` |
| MCP Apps SDK | `extensions/apps/` |
| Authorization extension (OAuth, SEP-991, RFC 8707) | `extensions/auth/` |
| Tasks extension | `extensions/tasks/` |
| Developer guides & tutorials | `docs/docs/` |
| Anthropic MCP blog posts | `blog/` |

Spec versions available: `2024-11-05`, `2025-03-26`, `2025-06-18`, `2025-11-25` (current stable), `draft`.

## Reading Full Files

Search returns chunks, not whole files. Read the raw file when the snippet misses:

- exact JSON-RPC method or capability shape (`tools.listChanged`, `resources.subscribe`)
- full SEP text for direct citation
- a complete spec section for code generation or verbatim quote

Miyo returns paths like `mcp-docs/docs/specification/2025-11-25/server/tools.md`. Prepend `wiki/` and the repo root, then use the standard `Read` tool — never `mcp__miyo__read_file` (it returns the whole file as one blob and truncates):

```
Read("<repo>/wiki/mcp-docs/docs/specification/2025-11-25/server/tools.md")
Read("<repo>/wiki/mcp-docs/seps/991-enable-url-based-client-registration-using-oauth-c.md")
```

For surgical reads of long files, pass `offset` / `limit` to `Read`.

## Sub-commands

| Form | Behavior |
|------|----------|
| `/mcp-docs <question>` | Search + synthesize from top 3–5 chunks |
| `/mcp-docs explain "<concept>"` | Broader search (`limit: 10`), group hits by category, cover definition + version differences + gotchas |
| `/mcp-docs sep <number>` | Locate `SEP-<number>` file under `seps/`, summarize motivation + design + status |
| `/mcp-docs spec <topic> [version]` | Scope to `path: "specification/<version>/"` (default: `2025-11-25`) |
| `/mcp-docs` (no args) | `ls wiki/mcp-docs/` and suggest a question |

## Common Mistakes to Avoid

1. **Missing `folder_path`** — `mcp__miyo__search` without `folder_path: "mcp-docs"` leaks results from RouterOS / codex-docs / claude-code-docs / ASTRA. Always set it.
2. **Wrong folder name** — the folder is `"mcp-docs"`, not `"wiki"`. (`wiki/mcp-docs/` is the on-disk path; the Miyo index is named after the leaf.)
3. **Reading before searching** — don't `Read` raw spec files to "find" something. Search first; read only when a snippet misses a specific detail.
4. **Using `mcp__miyo__read_file` for spec sections** — it truncates large files. Use `Read` with `offset` / `limit` instead.
5. **Citing without a usable path** — Miyo returns `mcp-docs/...`; the repo path needs `wiki/` prepended. Cite as `[file.md](wiki/mcp-docs/<...>/file.md)`.
6. **WebFetch'ing modelcontextprotocol.io** — the local mirror is fresher (latest spec drafts land here before the docs site rebuilds) and has no rate limit. Use the local mirror.
7. **Confusing with `/claude-code-docs`** — that skill is for the Claude Code CLI / Agent SDK at `~/claude-code-docs/`. This skill is for the MCP **protocol** itself.
