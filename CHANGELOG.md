# Changelog

## [1.1.1] - 2026-04-07

### Added

- **OAuth refresh token support** — token endpoint accepts `grant_type=refresh_token`, enabling silent token renewal. User authenticates once, session stays alive indefinitely via automatic refresh
- Real `expires_in` from Jira passed through to clients (was hardcoded 3600)
- E2e test for refresh token grant type: metadata validation + error paths (44 tests total)
- Reference docs: MCP authorization spec, Claude connector docs, Jira OAuth 2.0 DB schema

### Changed

- OAuth metadata advertises `grant_types_supported: ["authorization_code", "refresh_token"]`
- Token exchange captures both `access_token` and `refresh_token` from Jira's response
- `handleToken()` split into `handleAuthorizationCodeGrant()` + `handleRefreshTokenGrant()`
- Refresh token lifecycle managed by Jira's database — stateless on plugin side, survives restarts
- Deploy recipe: `clean` before `build`, resolve JAR glob via variable, skip tests on build
- Removed unused `Import-Package` entries (`spring.osgi`, `gemini.blueprint`, `jakarta.inject`)

## [1.1.0] - 2026-04-07

### Added

- **Security hardening** for public-facing deployment:
  - IP-based rate limiter: `/register` 5/min, `/token` 20/min, `/authorize` 10/min, MCP 120/min per user
  - Request body size limits: 1 MB for MCP, 64 KB for register, 8 KB for token
  - Security event logging with `[MCP-SEC]` prefix for incident response
  - Security response headers: `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Frame-Options: DENY`
  - 8 security e2e tests (auth on GET/DELETE, body limits, session binding, PKCE enforcement, redirect validation)
- **Claude Desktop connectivity**: `claude.ai`/`claude.com` added to Origin allowlist, 307 redirect for missing trailing slash

### Changed

- Auth required on GET (SSE) and DELETE endpoints — previously unauthenticated
- Session-user binding: sessions are tied to the authenticated user, preventing cross-user session hijacking
- PKCE S256 now mandatory on OAuth authorize (was silently skipped if `code_challenge` omitted)
- `redirect_uri` validated against registered client URIs on `/authorize` (closes open redirect / token theft)
- `redirect_uri` now mandatory on `/token` per RFC 6749 §4.1.3
- In-memory maps capped: sessions (200, 4h TTL), DCR clients (1000, 24h TTL), pending auths/codes (500, 10min TTL)
- Token exchange HttpClient hardened: no redirects, 5s connect timeout, 10s request timeout
- XSS fix: OAuth callback error page now HTML-encodes user input

### Fixed

- Claude Desktop could not connect — `/rest/mcp/1.0` (no trailing slash) was redirected to Jira login page by Jira's auth filter
- Reflected XSS in OAuth callback error parameter
- Open redirect via unvalidated `redirect_uri` in OAuth authorize flow

## [1.0.1] - 2026-04-07

### Added

- SSE event taxonomy: `heartbeat`, `progress`, `message`, `error` event types
- `Last-Event-ID` reconnect handling for GET SSE streams
- SSE lifecycle metrics: active streams, total events sent, reconnects, active sessions
- Partial failure handling in `batch_get_changelogs` and `get_issues_development_info` (was fail-all-on-first-error)
- Structured logging for session creation, SSE stream open/close, reconnects

### Changed

- SSE events now use distinct event types (`event: progress` instead of `event: message` for progress notifications)
- All SSE events have globally unique, monotonically increasing IDs for reconnection support
- Heartbeat events on GET streams use `event: heartbeat` with empty data

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
