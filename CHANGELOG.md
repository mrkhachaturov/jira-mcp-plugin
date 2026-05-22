# Changelog

## [1.3.0] - 2026-05-22

### Changed — breaking

- **MCP endpoint URL moved** from `POST /rest/mcp/1.0/` to `POST /plugins/servlet/mcp`. Existing MCP clients must update their configuration. (The new URL is necessary because the MCP transport now runs as an async-supported `servlet-filter`, and Atlassian's REST module framework on Jira 11 cannot host an async-supported servlet — `ServletModuleDescriptor` hardcodes `isAsyncSupported=false`.)
- **Requires Jira Data Center 11.x.** Earlier Jira versions are no longer supported (the `javax.servlet` API namespace was dropped on Jira 11).
- **Requires JVM flag on the Jira instance:** `-Datlassian.plugins.filter.async.default=true`. The official MCP Java SDK's transport uses `request.startAsync()`; without this flag the Atlassian plugin filter chain reports `isAsyncSupported=false` and rejects the call. See the README for `setenv.sh` / docker-compose examples.

### Added

- Adopted the official **MCP Java SDK 2.0.0-M2** (`io.modelcontextprotocol.sdk:mcp-core` + `mcp-json-jackson2`) as the transport foundation. Replaces the hand-rolled JSON-RPC dispatcher (`JsonRpcHandler`) and Streamable HTTP endpoint (`McpResource`) — ~1,200 lines deleted. SDK-provided features now in use:
  - Streamable HTTP transport with single-event SSE envelope for non-`initialize` responses
  - Built-in JSON Schema validation of tool inputs
  - Session management (`MCP-Session-Id`) and protocol-version negotiation
  - Native async / SSE handling (no manual `startAsync()` plumbing)
  - Standards-compliant `Accept`-header negotiation
- 14-test e2e suite redesigned around the SDK's Java client (`HttpClientStreamableHttpTransport` + `McpSyncClient`). Asserts on real Jira values (`Ruben Khachaturov`, `JIRAUSER10000`, `Europe/Moscow`, real issue keys with `structuredContent` for the widget). The previous 1,216-line hand-rolled HTTP suite has been removed.

### Changed

- Migrated from **Java 17 → Java 21** (Jira 11 platform requirement).
- Migrated from **Spring 5 → Spring 6.2.15**, **Tomcat 9 → Tomcat 10.1**, **Jakarta EE 9 → Jakarta EE 10**.
- AMPS **9.9.1 → 9.1.9** (the post-jakarta line).
- Atlassian Spring Scanner **3.0.4 → 6.0.2**.
- Imports flipped from `javax.servlet`, `javax.ws.rs`, `javax.inject`, `javax.annotation` to their `jakarta.*` equivalents across all Java sources.
- Atlassian Platform BOM **8.1.13** imported in `pom.xml`; manages versions of jakarta + Spring + Jackson + `atlassian-rest-v2-api` + `sal-api` transitively (instead of pinning each manually).
- MCP transport registered as a `<servlet-filter>` on `/plugins/servlet/mcp` (weight 600, after the security filters); calls the SDK servlet's `service()` directly and never invokes `chain.doFilter()`.
- `ResourceRegistry.toSpecifications()` now emits the real `ui://jira/issue-card@{hash}` resource via `SyncResourceSpecification` with dual metadata: Claude (`_meta.ui` nested) + ChatGPT (`openai/widgetDescription`, `openai/widgetPrefersBorder`, `openai/widgetCSP`, `openai/widgetDomain` flat keys). `resources/read` returns the bundled widget HTML straight from the plugin jar.
- OSGi `Private-Package` widened to embed all `compile`-scope SDK transitives (`reactor.*`, `org.reactivestreams.*`, `com.networknt.*`, `com.ethlo.time.*`, `com.fasterxml.jackson.dataformat.yaml.*`, `org.yaml.snakeyaml.*`) so Jira's OSGi container does not need to export them. `slf4j-api` pinned `provided` to avoid split-package logging.

### Removed

- Hand-rolled `JsonRpcHandler` + `McpResource` (~600 lines): SDK transport replaces them.
- `<rest key="mcp-rest">` JAX-RS module from `atlassian-plugin.xml`.
- Old e2e suite (1,216 lines of hand-rolled HTTP) replaced by 591 lines of SDK-client-driven tests.
- `<async-supported>` XML elements from `<servlet-filter>` declarations — the Atlassian plugin XML parser silently ignores them (verified via `javap` on `BaseServletModuleDescriptor.init()`); they were dead config.

### Security (preserved)

All security controls from 1.2.x carried over to the new transport:

- Origin validation, body size limits, IP / user rate limiting, session-user binding, security headers (`X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Frame-Options: DENY`), access control (allowed users / groups, plugin-enabled gate).
- OAuth proxy (`OAuthServlet`, PKCE S256, DCR, `refresh_token` grant) untouched.
- Anonymous-access filter (`OAuthAnonymousFilter` + `@UnrestrictedAccess`) for OAuth discovery endpoints preserved.

## [1.2.0] - 2026-04-09

### Added

- **MCP Apps interactive UI** — Jira issues render as rich interactive cards inside Claude Desktop, ChatGPT, VS Code Copilot, and other MCP Apps-compatible clients
  - Issue Card widget with list view (expand/collapse) and detail view
  - Status transition dropdown (click status badge to change workflow state)
  - Inline comment form
  - "Assign to me" link
  - Markdown rendering in descriptions and comments
  - Issue type icons (Bug, Epic, Task, Story, Sub-task, IT Help, Service Request)
  - Priority icons from Jira (Highest, High, Medium, Low, Lowest, Blocker, Minor)
  - Status badge colors from Jira's `statusCategory.colorName` (not hardcoded)
  - Clickable issue keys open in Jira via `app.openLink()`
  - i18n support (English + Russian)
- **MCP resources protocol** — `resources/list` and `resources/read` for `ui://` resources
- **Tool annotations** on all 49 tools (`readOnlyHint`, `destructiveHint`)
- **`structuredContent`** normalization for 5 issue-returning tools
- **`extensions` capability** — `io.modelcontextprotocol/ui` advertised in initialize
- **Dual metadata** for Claude (`_meta.ui`) and ChatGPT (`openai/widget*`) compatibility
- **ChatGPT MCP integration** — Origin allowlist includes `chatgpt.com`, `openai.com`
- **Widget build pipeline** — React 19 + Vite + viteSingleFile, integrated in justfile and GitHub Actions CI
- 10 new e2e tests (54 total): resources, annotations, structuredContent, extensions capability

### Changed

- `isDestructiveTool()` added to McpTool interface; `delete_issue`, `remove_issue_link`, `remove_watcher` marked destructive
- `JsonRpcHandler.handle()` accepts `username` and `userDisplayName` for structuredContent
- `ResourceRegistry` loads widget HTML from classpath, degrades gracefully if absent
- Upstream parity: 11 tools aligned with mcp-atlassian behavior
- Bidirectional Markdown/Jira wiki markup conversion (`JiraMarkupConverter`)

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
