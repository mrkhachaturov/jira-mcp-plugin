# wiki/project/

Project-specific reference docs that used to live in `CLAUDE.md`. Moved
here per the Claude Code recommendation to keep `CLAUDE.md` under
200 lines (loaded into every session) and offload deep reference into
on-demand material.

Indexed by Miyo alongside the rest of `wiki/`, so the content is
semantically searchable. Unlike `CLAUDE.md` and `.claude/rules/`, these
files are **not** auto-loaded — they're read explicitly (by humans or by
Claude when you ask).

## Index

- [architecture.md](architecture.md) — layer-by-layer breakdown (endpoint, OAuth proxy, tools, resources, auth)
- [mcp-transport.md](mcp-transport.md) — Streamable HTTP transport: methods, SSE rules, session management, security
- [tools.md](tools.md) — 49-tool inventory, `McpTool` interface, `execute()` patterns
- [response-trimming.md](response-trimming.md) — what `ResponseTrimmer` strips/renames and why
- [admin-config.md](admin-config.md) — `PluginSettings` keys with defaults
- [e2e-tests.md](e2e-tests.md) — e2e test categories and prerequisites
- [structure.md](structure.md) — annotated source tree

## When to look here

- Picking up the project after a break and need the wider mental model.
- Onboarding a teammate (or a fresh Claude session) to a specific subsystem.
- Refreshing the exact list of fields the response trimmer touches, the
  PluginSettings keys, etc.

For day-to-day work, [CLAUDE.md](../../CLAUDE.md) at the repo root has the
short-form rules, build commands, hard-won gotchas, and pointers back here.
