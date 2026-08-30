# wiki/

Locally-mirrored documentation for fast offline / semantic search via
[Miyo](https://miyo.ai) — the same engine that powers the global
`claude-code-docs` skill.

## Layout

```text
wiki/
├── README.md               # this file (committed)
├── .gitignore              # ignores everything below — content is regenerable
└── mcp-docs/               # ← curated subset of
                            #   github.com/modelcontextprotocol/modelcontextprotocol
                            #   ~197 .md files, upstream structure preserved
```

Only `README.md` + `.gitignore` are tracked. The `mcp-docs/` subtree is **not
committed** — regenerate any time with:

```bash
just wiki-sync
```

## What's mirrored from MCP repo

Curated for usefulness when building MCP servers. The sync script
(`scripts/sync-mcp-docs.sh`) keeps only:

- `docs/clients.md`, `docs/examples.md` — landing pages
- `docs/docs/` — developer guides (build, learn, tutorials, tools, sdk)
- `docs/extensions/` — apps / auth / tasks extensions
- `docs/specification/` — protocol spec, 5 versions (2024-11-05 → 2025-11-25 + draft)
- `seps/` — SEP source files (43)
- `schema/` — JSON Schema docs (3)
- `blog/content/posts/` — Anthropic blog posts on MCP

Filtered out: `docs/community`, `docs/development`, `docs/registry`,
`docs/seps` (duplicate of top-level), `docs/snippets`, Hugo scaffolding
under `blog/`.

`.mdx` → `.md` so Miyo's markdown chunker indexes them (JSX tags survive as
literal text — slightly noisy in snippets, harmless for search).

## Sync workflow

`scripts/sync-mcp-docs.sh` (called by `just wiki-sync`):

1. Sparse-clones `modelcontextprotocol/modelcontextprotocol` into
   `/tmp/mcp-docs-sync/` — only the curated paths land on disk (~5 MB vs
   ~70 MB full repo).
2. Wipes `wiki/mcp-docs/*` (gitignored, safe).
3. Mirrors selected paths into `wiki/mcp-docs/` preserving upstream
   directory structure, filters to `.md`/`.mdx`, renames `.mdx` → `.md`,
   drops empty dirs.
4. Never reads from `.upstream/` (that's reference-only and may be stale).

Cold run ~10-20 s, warm refresh ~1.5 s. Configurable via env vars at the
top of the script (`MCP_REPO_URL`, `MCP_REPO_REF`, `MCP_TMP_DIR`,
`WIKI_TARGET`).

## Miyo index

Configure Miyo to index `wiki/`. The project-level skill
`.claude/skills/mcp-docs/SKILL.md` searches with `folder_path: "wiki"` +
`path: "mcp-docs/"` (further narrowable, e.g. `path: "mcp-docs/seps/"`).

## Why mirror inside the project

The global `claude-code-docs` lives at `~/claude-code-docs/` because it's
user-global. `wiki/mcp-docs/` lives inside this repo because the **skill**
that queries it (`.claude/skills/mcp-docs/`) ships with the repo, so any
teammate who clones runs `just wiki-sync` once and has the same search
experience without configuring anything globally.

Future wikis (e.g., `wiki/jira-rest/`, `wiki/atlassian-platform/`) can live
alongside.
