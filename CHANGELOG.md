# Changelog

## [Unreleased]

## [1.4.3] - 2026-05-31

### Added

- **Opt-in Live mode for the Issue Card.** Compact pulsing toggle next to the dates row in the single-issue view; when enabled, the widget calls `get_issue` through the SDK App bridge every 30 s. Paused automatically when the document is hidden (`visibilityState !== 'visible'`) so a backgrounded tab doesn't burn cycles. Toggle is `role="switch"`, keyboard-accessible (Space/Enter), with a CSS keyframe pulse (`mcp-live-pulse`). i18n: `liveOn`, `liveTooltipOn`, `liveTooltipOff` for en + ru. Only the single-issue view exposes this — list views don't need per-row polling.
- **Tool visibility metadata advertised** per MCP Apps spec 2026-01-26 §Tool Metadata (L324-344). New `McpTool.uiVisibility(): List<String>` default-method (returns `null` = host default `["model", "app"]`). `McpToolAdapter` threads the value into `_meta.ui.visibility`. No tool overrides the default yet — purely a contract addition that unlocks `["app"]`-only (widget-internal) or `["model"]`-only tool declarations without further plumbing.
- **Local MCP-Apps dev host** (`dev-tools/basic-host/`) for visually testing the Issue Card widget without deploying to Claude.ai / ChatGPT / VS Code Copilot. Vendored from `@modelcontextprotocol/ext-apps-basic-host@1.7.2`. Iteration loop on widget changes drops from ~5 min (deploy + reconnect) to ~5 s (rebuild + reload browser).
- **`dev-tools/dev-host-proxy.mjs`** — pure-Node 100-line HTTP proxy that injects `Authorization: Bearer $JIRA_PAT_RKADMIN` (loaded by mise from `.credentials/jira.env`) and forwards basic-host calls to `${JIRA_URL}/plugins/servlet/mcp`. basic-host has no native auth-header hook; this bridges it. Dev-only, never enable on a public network.
- **`Procfile.dev-host`** at repo root + **`just dev-host`** recipe — launches proxy + host together via Hivemind (added to `.mise.toml` as `hivemind = "latest"`). Prefixed coloured output, one Ctrl-C tears down both. See `dev-tools/README.md` for usage. Not part of the shipped plugin — JAR build untouched.
- **Tests** — unit `CimdValidatorTest` (blocked-address rejection for localhost / `169.254.169.254`, host-exact `redirect_uri` matching, bounded cache); e2e regression guards: unauthenticated → `401` + `WWW-Authenticate` (no login-redirect HTML), invalid PAT → `401`, every discovery document advertises exactly `["WRITE"]` and a non-empty `issuer`, oversized body (fixed-length, chunked, and unknown-length) → `413`, and CIMD `client_id` resolving to an internal address → `400 invalid_client`.

### Fixed

- **Unauthenticated MCP requests now return a spec-compliant `401`, not a `302` login redirect.** On a login-required instance, Seraph intercepted the anonymous `POST /plugins/servlet/mcp` and 302-redirected it to the HTML login page *before* `AccessControlFilter` could run — so the RFC 9728 `401 + WWW-Authenticate: Bearer … resource_metadata=…` challenge that MCP clients need for OAuth discovery was dead code for anonymous callers. All six MCP servlet-filters (`BodySizeLimitFilter`, `RateLimitFilter`, `AccessControlFilter`, `SessionBindingFilter`, `SecurityHeadersFilter`, `McpTransportFilter`) now carry `@UnrestrictedAccess`, which exempts the path from the login *redirect* while still authenticating any presented token. Anonymous becomes *reachable* (the chain emits the 401 challenge) but never *authorized* — `AccessControlFilter` still returns 401 when the principal is null.
- **Discovery now advertises only the registered `WRITE` scope.** The `WWW-Authenticate` challenge and every OAuth/OIDC discovery document advertised `scope="read write"` / `["WRITE","READ"]` / `["read","write"]`, but the Jira "MCP" Application Link registers a single `Write` right (which already grants read). Advertising a separately-requestable `read` scope makes clients request a token the OAuth provider can reject with `invalid_scope`. The 401 challenge (`AccessControlFilter`), `/metadata`, `/openid-configuration`, and both `/.well-known/*` documents now advertise exactly `WRITE`.
- **`.well-known/oauth-authorization-server` brought to parity with `/metadata`** — now advertises `refresh_token` in `grant_types_supported` and `client_id_metadata_document_supported: true`. The anonymous filter also serves `/.well-known/openid-configuration` (previously only the servlet path was reachable).
- **Read-only mode / disabled-tool toggles take effect at call time without a plugin reload.** The SDK sync server freezes its tool list at filter `init()`, so admin toggles of `readOnlyMode` / `disabledTools` only applied to `tools/list`, not `tools/call`. `McpToolAdapter.dispatch` now re-checks `McpPluginConfig` per call and returns an error result for a disabled tool or a write tool in read-only mode.

### Security

- **CIMD fetch now has an SSRF address guard.** The CIMD `client_id` is an attacker-supplied HTTPS URL fetched server-side from the **unauthenticated** `/authorize` request. [CimdValidator](src/main/java/com/atlassian/mcp/plugin/rest/oauth/CimdValidator.java) already enforced HTTPS-only + no-redirects + 8 KB cap + timeouts; it now also **resolves the host and rejects loopback / link-local / RFC-1918 private / CGNAT / unique-local / `169.254.169.254` cloud-metadata** addresses *before* the fetch. This blocks the obvious SSRF vectors (internal IPs, localhost services, cloud metadata). Residual DNS-rebinding risk is accepted (the `/authorize` entry point is rate-limited; full closure needs connection-level IP pinning).
- **CIMD `redirect_uri` matching hardened.** Replaced naive `startsWith("http://localhost")` (which accepted `http://localhost.evil.example/…`) with `URI`-parsed **host-exact** matching; embedded-credential URIs (`http://user@localhost/…`) are rejected. Added **negative caching** (5 min) so a repeatedly-failing CIMD URL can't be replayed to hammer the resolver.
- **Body-size cap enforced on actual bytes, not `Content-Length`.** [BodySizeLimitFilter](src/main/java/com/atlassian/mcp/plugin/rest/BodySizeLimitFilter.java) previously trusted the `Content-Length` header, so a chunked / unknown-length / under-declared request slipped an oversized body past the 1 MiB gate. It now drains the stream up to the cap and returns 413 on overflow, re-wrapping the read bytes so the SDK transport still reads the body intact.

## [1.4.1] - 2026-05-22

### Fixed

- **`protected-resource` advertised the wrong URL.** Both the OAuth proxy servlet and the anonymous-filter fast path returned `resource: "<base>/rest/mcp/1.0/"` — the pre-jakarta REST endpoint that no longer exists. The MCP transport has lived at `<base>/plugins/servlet/mcp` since 1.3.0, so strict clients (Claude Code's SDK validator) refused to connect with: *"Protected resource does not match expected"*. Both files now return the correct `/plugins/servlet/mcp` URL. Claude Desktop was unaffected because it discovered the endpoint by another path.
- **`completion/complete` returned `Prompt not found: project_key`.** The handler was registered as a `PromptReference("project_key")`, but the SDK's dispatcher requires the prompt to actually exist in the server's registered prompts (we have none). Re-wired to `ResourceReference("jira://issue/{issueKey}")` — the existing F-10 resource template — so completion is now spec-correct: clients ask for completion of the `issueKey` URI variable and receive up to 20 project-key prefixes (`PROJ-`, `TEMP-`, …) matching the typed prefix.

## [1.4.0] - 2026-05-22

### MCP spec 2025-11-25 compliance sprint

22 audit findings (F-01 … F-24, with F-11 withdrawn and F-22 superseded by F-24) plus two deferred SDK-adoption tasks landed in one branch. The MCP-Protocol-Version negotiated at `initialize` is now `2025-11-25` — courtesy of the M3 SDK transport's `protocolVersions()` list.

### Added — new protocol surface

- **Per-tool `iconUri` + `outputSchema` advertised** on the five UI-linked tools (`get_issue`, `search`, `get_project_issues`, `get_board_issues`, `get_sprint_issues`) per SEP-973 (icons) and SEP-1330 (outputSchema). Schemas describe the issue-card structuredContent payload shape. (F-12, outputSchema)
- **Tool annotation hints populated**: `title`, `idempotentHint` (`!isWriteTool()` by default), `openWorldHint` (`true` for all tools — every tool talks to Jira REST). (F-04, F-05, F-17)
- **JSON Schema 2020-12 dialect declared** on every tool's input/output schema via `$schema` (auto-injected by `McpToolAdapter`). (F-18, SEP-1613)
- **`RateLimit-*` response headers** (`RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`) emitted on every authenticated MCP and OAuth response per draft-ietf-httpapi-ratelimit-headers-09. 429 responses additionally set `Retry-After`. (F-09)
- **`completion/complete` handler for `project_key`** argument. Caches the project list per Authorization header for 60 seconds, prefix-matches case-insensitively, returns top 20. Status / issue_type / assignee completions deferred (need per-project context not in the request shape). (F-07)
- **`jira://issue/{issueKey}` resource template** — clients can fetch any Jira issue by key via `resources/read` without burning a tool call. (F-10)
- **`logging/setLevel` handler** auto-wired by declaring `.logging()` on `ServerCapabilities`. Tool bodies can emit `notifications/message` via `McpSyncServerExchange.loggingNotification(...)`. (F-16)
- **OpenID Connect Discovery 1.0 endpoint** at `/plugins/servlet/mcp-oauth/openid-configuration`, alongside the existing RFC 8414 metadata endpoint. (F-13)
- **OAuth Client ID Metadata Documents (CIMD)** support per SEP-991 — clients may use an HTTPS URL as `client_id`; the server fetches the metadata document (8 KB cap, 5s/10s timeouts, 1-hour cache, 1000-entry LRU) and uses its `redirect_uris` for the allowlist check. Advertised via `client_id_metadata_document_supported: true` in both metadata endpoints. (F-14)
- **`WWW-Authenticate` scope challenges** on 401/403 per SEP-835: 401 advertises `scope="read write"`; 403 emits a bare Bearer challenge. Per-tool insufficient_scope deferred to v1.5+ once a real scope ↔ tool mapping is wired. (F-06)
- **MCP Apps widget capabilities declared** via `useApp(...)` — `availableDisplayModes: ['inline']` + `tools: { listChanged: false }` per ext-apps `McpUiAppCapabilitiesSchema`. (F-20)

### Changed

- **Switched build BOM** from `com.atlassian.platform.dependencies:platform-public-api:8.1.13` → `com.atlassian.jira:jira-api-bom:11.3.6` (which transitively imports `platform-deps:8.3.16`). Aligns the plugin's compile classpath with what Jira 11.3.6 actually ships. Resolves: Jackson 2.19.4→2.21.2, Spring 6.2.15→6.2.17, atlassian-plugins 9.0.2→9.1.4, atlassian-rest-v2-api 9.0.5→9.1.4, sal-api 7.0.1→7.0.4. (F-24, supersedes F-22)
- **Bumped AMPS** 9.1.9 → 9.12.4 (latest jakarta-line). Tomcat 11 / Jira 11 launcher mappings, JDK 25-ready ASM, and `rerunFailingTestsCount=2` for the flaky live-Jira e2e suite. (F-23)
- **Adopted MCP Java SDK 2.0.0-M3** (from M2). Builder APIs for `Tool`, `ToolAnnotations`, `CallToolResult`, `SyncToolSpecification`, `TextResourceContents`, `ReadResourceResult` replace canonical-constructor calls.
- **Server capabilities accuracy**: `tools.listChanged=false` and `resources.listChanged=false` — the plugin never emits the corresponding notifications. Removes a silent spec violation. (F-01, F-02)
- **Origin validation delegated to SDK** `DefaultServerTransportSecurityValidator`. `OriginValidationFilter.java` deleted (~96 LOC). Allowlist preserved verbatim.
- **MCP Apps SDK** `@modelcontextprotocol/ext-apps` 1.2.2 → 1.7.2 in `mcp-app/`. Widget bundle measured at ~571 KB (gzip 159 KB) — acceptable because the resource is content-hashed via `ui://jira/issue-card@{hash}`. (F-19, F-21)

### Documented gaps

- **F-08 (`notifications/cancelled`)**: MCP Java SDK 2.0.0-M3 does NOT surface cancellation to call handlers (no `exchange.isCancelled()`). The four batch tools therefore run to completion. Spec allows this (`SHOULD process / MAY ignore`). TODO documented in `McpToolAdapter`.
- **F-15 (Tasks extension)**: experimental in 2025-11-25. Deferred per audit recommendation.

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
- Behavior alignment: 11 tools refined for consistency
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

- **49 MCP tools** covering issues, projects, boards, sprints, comments, worklogs, links, fields, attachments, service desk, forms, metrics
- **Streamable HTTP transport** — MCP spec 2025-06-18 compliant. Session management via `MCP-Session-Id`, Origin validation, SSE streaming for batch tools with progress notifications
- **OAuth 2.0 proxy** — users authenticate via browser consent. RFC 9728 protected resource metadata, RFC 8414 authorization server metadata, PKCE (S256) support
- **PAT authentication** — Personal Access Tokens as alternative to OAuth
- **Group and user access control** — allowlists via Jira groups or individual users
- **Per-tool management** — enable/disable individual tools, read-only mode
- **Response trimming** — strips verbose fields (`self`, `avatarUrls`, `iconUrl`, `groups`, `applicationRoles`) to reduce payload size for AI agents
- **Fuzzy field search** — `search_fields` with keyword matching and limit
- **Admin UI** — tabbed interface (General, Access Control, Tools, OAuth) at `/plugins/servlet/mcp-admin`
- **E2E test suite** — 35 tests covering protocol, tools, streaming, sessions, access control, and security
- **CI/CD** — GitHub Actions for build (on push/PR) and release (on tag)
