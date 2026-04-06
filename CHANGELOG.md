# Changelog

## [1.0.0] - 2026-04-07

### Added

- **49 MCP tools** mirrored 1:1 from upstream mcp-atlassian — issues, projects, boards, sprints, comments, worklogs, links, fields, attachments, service desk, forms, metrics
- **Streamable HTTP transport** — MCP spec 2025-06-18 compliant. Session management via `MCP-Session-Id`, Origin validation, SSE streaming for batch tools with progress notifications
- **OAuth 2.0 proxy** — users authenticate via browser consent. RFC 9728 protected resource metadata, RFC 8414 authorization server metadata, PKCE (S256) support
- **PAT authentication** — Personal Access Tokens as alternative to OAuth
- **Group and user access control** — allowlists via Jira groups or individual users
- **Per-tool management** — enable/disable individual tools, read-only mode
- **Response trimming** — strips verbose fields (`self`, `avatarUrls`, `iconUrl`, `groups`, `applicationRoles`) matching upstream's `to_simplified_dict()` behavior
- **Fuzzy field search** — `search_fields` with keyword matching and limit
- **Admin UI** — tabbed interface (General, Access Control, Tools, OAuth) at `/plugins/servlet/mcp-admin`
- **Code generator** — `python3 .codegen/translate.py` parses upstream Python tool definitions and generates Java tool classes
- **E2E test suite** — 35 tests covering protocol, tools, streaming, sessions, access control, and security
- **CI/CD** — GitHub Actions for build (on push/PR) and release (on tag)
