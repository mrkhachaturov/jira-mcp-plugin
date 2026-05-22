# MCP Spec Compliance Audit — 2026-05-22

Audit of `jira-mcp-plugin` against the official Model Context Protocol specification.

- **Plugin currently targets:** MCP `2025-06-18` (per [CLAUDE.md](../CLAUDE.md))
- **Latest stable spec:** MCP `2025-11-25` (`/Volumes/Devops/Git/Github/mrkhachaturov/atlassian-mcp-plugin/.upstream/modelcontextprotocol/docs/specification/2025-11-25/`)
- **MCP Java SDK in use:** `io.modelcontextprotocol.sdk:mcp-core` `2.0.0-M2`
- **Auditor:** Claude Code (Opus 4.7), spec-text-verified

> **Verification note.** This document was produced by an initial Explore-agent audit followed by **two** verification passes:
> 1. Spec-text verification against `.upstream/modelcontextprotocol/docs/specification/2025-11-25/`.
> 2. **Real-code verification** against `src/main/java/` *and* the React widget at `mcp-app/`.
>
> Items marked **[VERIFIED]** were re-checked against the actual source. Items marked **[CORRECTED]** are where the original audit (or my first pass) was wrong. Findings prefixed **[REAL-CODE]** were discovered only during the source-code pass — the agent didn't have eyes on the relevant file.

---

## 1. Spec version posture

The plugin is **one minor revision behind** the latest stable spec. The gap is `2025-06-18 → 2025-11-25`, ~5 months.

Per [`.upstream/modelcontextprotocol/docs/specification/2025-11-25/changelog.mdx`](../.upstream/modelcontextprotocol/docs/specification/2025-11-25/changelog.mdx), 9 major and 10 minor changes landed in `2025-11-25`. None are wire-breaking for an existing `2025-06-18` server — but several are new opt-in capabilities the plugin should consider.

**Action:** plan a version bump to `2025-11-25` once the P0/P1 items below are addressed. The `MCP-Protocol-Version` header value advertised by the server should be updated atomically with feature parity.

---

## 2. Compliance matrix

Legend: ✅ compliant · ⚠️ partial · ❌ missing · 🆕 new feature in `2025-11-25`

| Area | Status | Note |
|------|--------|------|
| Streamable HTTP transport (POST/GET/DELETE, SSE) | ✅ | Implemented by SDK + plugin's `McpTransportFilter`. |
| Session management (`MCP-Session-Id`, TTL, caps) | ✅ | Plugin enforces 200 sessions / 4 h, session-user binding. |
| `Origin` header validation | ✅ | `OriginValidationFilter` — returns 403 on invalid (matches `2025-11-25` clarification PR #1439). |
| `MCP-Protocol-Version` header | ⚠️ | Validated, but only `2025-06-18` advertised. |
| Lifecycle (`initialize` / `initialized` / capabilities) | ✅ | Via SDK. |
| OAuth 2.1 + DCR + PKCE (S256) | ✅ | `OAuthServlet` + `RateLimiter`. |
| Protected Resource Metadata (RFC 9728) | ✅ | Present at well-known endpoint. |
| Incremental scope consent (`WWW-Authenticate` challenge) | ❌ 🆕 | New in `2025-11-25` (SEP-835). Not wired. |
| OpenID Connect Discovery 1.0 | ❌ 🆕 | New in `2025-11-25` (PR #797). Not exposed. |
| OAuth Client ID Metadata Documents | ❌ 🆕 | New in `2025-11-25` (SEP-991). Not supported. |
| Tools — `tools/list` + `tools/call` | ✅ | 49 tools, dynamic filtering by capability. |
| Tools — `readOnlyHint`, `destructiveHint` annotations | ✅ | Set correctly in [`McpToolAdapter.java:49-50`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java). |
| Tools — `idempotentHint`, `openWorldHint`, tool `title` | ❌ | All hardcoded `null` in [`McpToolAdapter.java:48,51,52`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java). |
| Tools — per-tool `icons` field | ❌ 🆕 | New in `2025-11-25` (SEP-973). Not set on any of the 49 tools — `McpToolAdapter` doesn't reference `Icon`. |
| **Server-level `icons` + `title` + `websiteUrl`** | ✅ 🆕 | **[CORRECTED]** — plugin already sets all of these via `Implementation.builder()` at [`McpBootstrap.java:121-129`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java). SDK `2.0.0-M2` forward-supports these `2025-11-25` fields. |
| Server-level `instructions` | ✅ | Set at [`McpBootstrap.java:135`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java). |
| Tools — `outputSchema` + `structuredContent` | ✅ | Wired via `tool.structuredContent(...)`. |
| Tools — `listChanged` capability | ⚠️ | Declared `true` in [`McpBootstrap.java:91`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java), but no notification ever emitted. |
| Tools — pagination | ⚠️ | SDK handles single page; tool count (49) fits, no cursor needed today. |
| Resources — `resources/list` + `resources/read` | ✅ | `ResourceRegistry` serves `ui://jira/issue-card@{hash}`. |
| Resources — templates | ❌ | No `resources/templates/list` handler — only static URIs. |
| Resources — subscriptions | ❌ | `subscribe = false` in capabilities — by design, no Jira event bus wired. |
| Resources — `listChanged` capability | ⚠️ | Declared `true` in [`McpBootstrap.java:92`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java), but no notification ever emitted. |
| Prompts | ✅ | Not implemented — out of scope for plugin (no capability declared). |
| Utilities — `ping` | ✅ | SDK. |
| Utilities — `progress` notifications | ✅ | 4 streaming tools (`batch_create_issues`, etc.). |
| Utilities — `notifications/cancelled` | ⚠️ | Spec says "supports **optional** cancellation". `grep` confirms zero references to `cancel*` in `src/main/java/`. Long-running batch tools cannot be aborted mid-flight. |
| Utilities — `completion/complete` | ❌ | **[REAL-CODE VERIFIED]** zero references to `CompletionSpec` / `completion/complete` in `src/main/java/`. |
| Utilities — `logging` | ❌ | **[REAL-CODE VERIFIED]** zero references to `logging/setLevel` in `src/main/java/`. |
| Tasks (long-running operations) | ❌ 🆕 | **Experimental** in `2025-11-25` (SEP-1686). Not implemented. |
| `Implementation.description` field | ❌ 🆕 | New optional metadata in `2025-11-25` to align with registry server.json. Not set. |
| JSON Schema 2020-12 default dialect | ⚠️ 🆕 | New in `2025-11-25` (SEP-1613). Confirm `schema-validator` is on 2020-12; tool inputSchemas may need `$schema` declaration. |
| Tool input validation as **Tool Execution Errors** (not Protocol Errors) | ⚠️ 🆕 | Clarified in `2025-11-25` (SEP-1303). [`McpToolAdapter.java:106-110`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java) correctly returns `isError=true` rather than JSON-RPC error — already matches new guidance. ✅ on closer read. |
| Sampling / Elicitation (client features) | n/a | Server doesn't initiate these. |
| Stdio transport guidance (stderr logging) | n/a | Plugin uses Streamable HTTP only. |
| Security — rate limiting | ✅ | `RateLimiter` per-IP + per-user. |
| Security — body size limits | ✅ | 1 MB MCP / 64 KB DCR / 8 KB token. |
| Security — `X-Content-Type-Options: nosniff` + `Cache-Control: no-store` + `X-Frame-Options: DENY` | ✅ | `SecurityHeadersFilter`. |
| Security — session-user binding | ✅ | `SessionBindingFilter`. |
| Security — redirect URI validation | ✅ | `/authorize` validates against registered URIs. |

---

## 3. Concrete findings — prioritised

### P0 — spec MUSTs or active bugs

#### F-01. `resources.listChanged` capability declared but notification never emitted
- **Type:** Bug — silent spec violation.
- **Spec:** `2025-11-25/server/resources.mdx` — if `capabilities.resources.listChanged = true`, the server **MUST** emit `notifications/resources/list_changed` when the resource list changes.
- **Current:** [`McpBootstrap.java:92`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java) declares `.resources(false, true)`. No notification is fired anywhere in the codebase (grep confirms — no callers of any list-changed emit on `McpSyncServer`).
- **Fix options:** (a) set capability to `false` since the resource set is static at boot, or (b) emit on widget-hash change (the issue-card hash is content-derived at startup, so the list is effectively immutable for a given JAR — option **(a) is correct**).
- **Effort:** S (one-line change).

#### F-02. `tools.listChanged` capability declared but never emitted
- **Type:** Bug — silent spec violation, same shape as F-01.
- **Spec:** `2025-11-25/server/tools.mdx#capabilities`.
- **Current:** [`McpBootstrap.java:91`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java) declares `.tools(true)`.
- **Caveat:** the tool list **is** dynamic — `ToolRegistry` filters by config (`readOnlyMode`, `disabledTools`, plugin-presence). When the admin toggles config in `/plugins/servlet/mcp-admin`, the visible tool set changes — but no notification is emitted.
- **Fix:** wire `ConfigResource` PUT handler to call `server.notifyToolsListChanged()` (or SDK equivalent in `2.0.0-M2`). If SDK doesn't expose the emit hook, set capability to `false` until M3.
- **Effort:** S–M depending on SDK affordance.

#### F-03. `MCP-Protocol-Version` advertises `2025-06-18` only
- **Type:** Forward-compat gap.
- **Spec:** `2025-11-25/basic/lifecycle.mdx` — version negotiation must reflect the actual feature set the server implements.
- **Current:** Plugin pins to `2025-06-18`.
- **Fix:** once at least the bug-class items (F-01, F-02) plus the high-value 🆕 items (F-06, F-07, F-08) are merged, bump to `2025-11-25`.
- **Effort:** S (header bump after feature parity).

---

### P1 — high-value features, mostly small effort

#### F-04. `idempotentHint` on tools is always `null` **[VERIFIED]**
- **Spec:** `2025-11-25/server/tools.mdx#tool-annotations`.
- **Current:** [`McpToolAdapter.java:51`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java) — hardcoded `null`.
- **Why it matters:** clients use this to decide whether a failed call can be transparently retried. Most read-only Jira tools (`get_issue`, `search`, `get_user_profile`, …) are trivially idempotent.
- **Fix:** add `default boolean isIdempotent() { return !isWriteTool(); }` to `McpTool`, override on a few specific write tools that are idempotent by virtue of resource identity (e.g. `update_issue` with a stable issue key is idempotent; `add_comment` is not).
- **Effort:** S.

#### F-05. Tool `title` field always `null` **[VERIFIED]**
- **Spec:** `2025-11-25/server/tools.mdx` line 195 — "Optional human-readable name of the tool for display purposes".
- **Current:** [`McpToolAdapter.java:48`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java).
- **Fix:** add `default String title() { return null; }` to `McpTool`. Populate from upstream Python `@jira_mcp.tool(title=...)` where present; otherwise leave null. May warrant updating `.codegen/translate.py` to extract titles.
- **Effort:** S (interface change + codegen tweak).

#### F-06. No incremental scope consent via `WWW-Authenticate` 🆕 **[VERIFIED — new in 2025-11-25]**
- **Spec:** `2025-11-25/basic/authorization.mdx`, SEP-835 in changelog.
- **Current:** Plugin returns flat 401s on missing/invalid token; no `WWW-Authenticate` scope challenge.
- **Fix:** when an authenticated user lacks scope `X` required for a specific tool, return `401 WWW-Authenticate: Bearer error="insufficient_scope", scope="X"`. Today the plugin model is binary (PAT or OAuth token = full access), so scope mapping needs design first.
- **Effort:** M (requires defining scope ↔ tool mapping).

#### F-07. Completion API not implemented
- **Spec:** `2025-11-25/server/utilities/completion.mdx`.
- **Current:** No `completion/complete` handler.
- **Why it matters:** very high LLM-accuracy win for Jira-shaped args — project keys, issue types, statuses, sprint IDs, custom field IDs. Today the LLM has to guess or call a `get_*` tool first.
- **Fix:** implement `SyncCompletionSpecification` for at least: `project_key`, `assignee` (username), `status` (per project), `issue_type` (per project).
- **Effort:** M.

#### F-08. `notifications/cancelled` not honoured **[CORRECTED — P1 not P0]**
- **Spec:** `2025-11-25/basic/utilities/cancellation.mdx` line 7 — *"supports **optional** cancellation"*. Receivers **SHOULD** stop processing but **MAY** ignore. So this is **not** a hard MUST as the original audit claimed.
- **Current:** Plugin's tool execution path has no cancellation hook. Long-running batch tools (`batch_create_issues`, `batch_get_changelogs`, `search` over large result sets) keep running until completion.
- **Fix:** thread a `volatile boolean cancelled` flag through the tool execution context, wired to the SDK's cancellation listener. Break out of batch loops between iterations.
- **Effort:** M.

#### F-09. `RateLimit-*` response headers not returned
- **Spec:** Recommended by [IETF draft-ietf-httpapi-ratelimit-headers](https://datatracker.ietf.org/doc/draft-ietf-httpapi-ratelimit-headers/) (referenced from MCP security best practices).
- **Current:** Plugin enforces limits via `RateLimiter` but does not surface remaining quota in response headers.
- **Fix:** emit `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` on every authenticated MCP response.
- **Effort:** S.

#### F-10. Resource templates not supported
- **Spec:** `2025-11-25/server/resources.mdx` — `resources/templates/list` enables parameterised URIs like `jira://issue/{issueKey}`.
- **Current:** Only static `ui://jira/issue-card@{hash}`.
- **Why it matters:** lets clients fetch a Jira issue as a resource without calling a tool. Pairs naturally with MCP Apps' UI binding.
- **Fix:** define one or two templates (`jira://issue/{key}`, `jira://project/{key}`) backed by existing `JiraRestClient` calls. Trim with `ResponseTrimmer`.
- **Effort:** M.

#### ~~F-11. `Implementation.description` not set 🆕~~ **[WITHDRAWN]**
- **Status:** my first pass got this wrong.
- **Truth:** [`McpBootstrap.java:121-129`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java) uses `Implementation.builder()` and already sets `title`, `description`, `websiteUrl`, **and** server-level `icons` — all `2025-11-25` additions. SDK `2.0.0-M2` exposes these forward-compat. No action.

---

### P2 — nice-to-have / future

#### F-12. **Per-tool** `icons` field not set 🆕 **[new in 2025-11-25, SEP-973]** **[REAL-CODE VERIFIED]**
- Helps clients render branded tool lists. **Server-level icon is already set** ([`McpBootstrap.java:125`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java)) — only per-tool icons are missing. `McpToolAdapter` doesn't reference `Icon` at all.
- Could pull from Jira favicon or per-toolset category icons (8 toolsets in `ToolRegistry`).
- **Effort:** S.

#### F-13. OpenID Connect Discovery 1.0 endpoint missing 🆕
- **Spec:** `2025-11-25/basic/authorization.mdx`. Mirror existing OAuth metadata at `/.well-known/openid-configuration` in addition to the current `/plugins/servlet/mcp-oauth/metadata`.
- **Effort:** S (alias route).

#### F-14. OAuth Client ID Metadata Documents 🆕
- **Spec:** SEP-991 — recommended client registration mechanism in `2025-11-25`.
- **Current:** DCR (`/register`) accepts client_name + redirect_uris only.
- **Effort:** M.

#### F-15. Tasks (long-running ops) 🆕 **[experimental in 2025-11-25, SEP-1686]**
- **Spec:** `2025-11-25/basic/utilities/tasks.mdx`. Lets the server return a task ID immediately and have the client poll for results — solves the >30s timeout problem.
- **Eligible plugin operations:** `batch_create_issues` over large lists, exhaustive `search` with deep pagination, big `batch_get_changelogs`.
- **Caveat:** marked **experimental** in the spec. Watch for changes before investing.
- **Effort:** L.

#### F-16. `logging/setLevel` handler not implemented
- **Spec:** `2025-11-25/server/utilities/logging.mdx`.
- Lets clients dynamically adjust server log verbosity. Useful for support debugging.
- **Effort:** S.

#### F-17. `openWorldHint` always `null`
- **Spec:** `2025-11-25/server/tools.mdx`.
- Tells clients whether the tool interacts with an open world (external systems) vs a closed sandbox. For this plugin **every** tool talks to Jira — should be `true` universally.
- **Effort:** S (one-line in adapter).

#### F-18. JSON Schema 2020-12 dialect 🆕
- **Spec:** SEP-1613. Spec now defaults to JSON Schema 2020-12.
- **Current:** Tool `inputSchema` maps don't declare `$schema`. Need to verify the SDK's `schema-validator` is on 2020-12.
- **Effort:** S (audit + maybe declare).

#### F-19. **MCP Apps SDK `@modelcontextprotocol/ext-apps` is five minor versions behind** 🆕 **[REAL-CODE — agent missed entirely]**
- **Spec:** N/A (this is the host-SDK side of MCP Apps, governed by package releases, not the wire protocol).
- **Current:** [`mcp-app/package.json`](../mcp-app/package.json) line 11 — `"@modelcontextprotocol/ext-apps": "^1.2.2"`.
- **Latest on npm:** `1.7.2` (checked 2026-05-22 via `npm view`).
- **Risk:** missing host-feature additions and bug fixes shipped in `1.3 → 1.7`. The `^` lets minor updates float, but the lockfile likely pins to `1.2.x` — a `npm update` + lockfile refresh is needed.
- **Fix:** bump to `^1.7.2`, re-run `npm install` in `mcp-app/`, retest widget rendering in Claude Desktop and ChatGPT.
- **Effort:** S (smoke-test widget after bump).

#### F-20. Widget `useApp` declares empty `capabilities: {}` **[REAL-CODE]**
- **Source:** [`mcp-app/src/issue-card/app.tsx:19`](../mcp-app/src/issue-card/app.tsx) — `capabilities: {}` passed to `useApp(...)`.
- **Observation:** the widget already uses `app.callServerTool(...)` for the refresh-issue flow (line 43) and handles `ontoolinput` / `ontoolresult`. With empty `capabilities`, the host has no explicit signal of what the widget supports.
- **Why it matters:** ext-apps `1.5+` introduced richer capability negotiation. Some hosts (notably ChatGPT) gate UX based on declared widget capabilities.
- **Fix:** after F-19, declare what the widget actually does (e.g. `capabilities: { toolCalls: true }`). Verify against the `ext-apps` `1.7.2` README.
- **Effort:** S.

#### F-21. Widget bundle is built as a single inlined HTML — large `resources/read` payloads **[REAL-CODE]**
- **Source:** [`mcp-app/vite.config.ts`](../mcp-app/vite.config.ts) uses `vite-plugin-singlefile`. The resulting `issue-card.html` (cached on the classpath) inlines all JS + CSS.
- **Observation:** acceptable today (CLAUDE.md notes the design choice), but worth measuring against the spec's recommendation that resources be cacheable. The current `ui://jira/issue-card@{hash}` cache-busting URI is the right primitive — make sure clients honour the hash.
- **Action:** non-urgent — measure rendered bundle size with `just build-app` and add it to the README if > 200 KB.
- **Effort:** S (measure only).

---

## 4. Verification of original agent findings

| Original finding | Status after re-check |
|---|---|
| Cancellation is a P0 MUST | **CORRECTED** — spec says "optional" / SHOULD-process / MAY-ignore. Demoted to P1 (F-08). |
| "Resource Indicators (RFC 8707)" as a missing MCP resource feature | **DROPPED** — RFC 8707 is *OAuth* Resource Indicators, unrelated to MCP resource templates. Original audit conflated two things. Resource *templates* (different concept) are the real gap → F-10. |
| Tasks new in 2025-11-25 | **VERIFIED** — SEP-1686, marked experimental. |
| Incremental scope consent new in 2025-11-25 | **VERIFIED** — SEP-835. |
| Tool `idempotentHint` always null | **VERIFIED** — [`McpToolAdapter.java:51`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java). |
| Tool `title` always null | **VERIFIED** — [`McpToolAdapter.java:48`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java). |
| `listChanged` declared but never emitted | **VERIFIED** — applies to both resources and tools. |
| OAuth/DCR/PKCE solid | **VERIFIED** — no issues found. |
| Origin validation correct | **VERIFIED** — matches `2025-11-25` 403 clarification. |
| Streamable HTTP transport solid | **VERIFIED** — SDK-driven, plugin filter chain correct. |
| "RateLimit-* headers missing" | **VERIFIED** — F-09. |
| OpenID Connect Discovery missing 🆕 | **VERIFIED** — F-13. |
| OAuth Client ID Metadata Documents missing 🆕 | **VERIFIED** — F-14. |
| Original audit missed: JSON Schema 2020-12 default | **ADDED** — F-18. |
| Original audit missed: `Implementation.description` field | **ADDED then WITHDRAWN** — F-11. Real-code pass showed plugin already sets this via `Implementation.builder()`. |
| Original audit missed: input validation = Tool Execution Errors (SEP-1303) | **ADDED** — plugin already does this correctly (`isError=true` rather than JSON-RPC error). No fix needed. |
| Original audit missed: server-level `icons` + `title` + `websiteUrl` already set | **REAL-CODE** — discovered only during source-code pass. Plugin is ahead of its declared `2025-06-18` target here. |
| Original audit missed: React widget `mcp-app/` entirely | **REAL-CODE** — F-19, F-20, F-21 added. `@modelcontextprotocol/ext-apps` is at `1.2.2`, latest is `1.7.2`. |
| Original audit missed: zero refs to cancel/completion/logging in `src/main/java/` | **REAL-CODE VERIFIED** — confirmed by `grep`. Strengthens F-07, F-08, F-16. |

---

## 5. What the plugin does well — do not touch

1. **Streamable HTTP transport plumbing** — `McpTransportFilter` correctly registers as `<servlet-filter>` owning its URL, avoiding the `<servlet>` async-supported limitation (documented hard-won lesson in CLAUDE.md).
2. **OAuth proxy design** — stateless passthrough of refresh tokens, IP-based rate limiting on anonymous endpoints, PKCE S256 enforced.
3. **Session-user binding** — anti-replay protection across users sharing a host.
4. **Security headers filter** — `nosniff`, `no-store`, `X-Frame-Options: DENY` applied consistently.
5. **Response trimming** — `ResponseTrimmer` matches upstream Python's `to_simplified_dict()` 1:1, keeping wire size sane.
6. **Codegen pipeline** — `.codegen/translate.py` keeps tool definitions in lockstep with upstream `mcp-atlassian`.
7. **49-tool registry shape** — clean `McpTool` interface, plugin-capability gating (`requiredPluginKey()`), per-toolset categorisation.
8. **MCP Apps integration** — dual `_meta.ui.resourceUri` (Claude) + `openai/widgetResource` (ChatGPT) metadata on tools that have UI.
9. **Tool execution errors returned as `isError=true`** — already matches new `2025-11-25` clarification (SEP-1303) without intending to.

---

## 6. Recommended landing order

1. **F-01 + F-02** — fix the false-positive `listChanged` capabilities (either emit or unset). One PR.
2. **F-04 + F-05 + F-17** — populate `idempotentHint`, `title`, `openWorldHint`. Codegen tweak + interface defaults. One PR.
3. **F-09** — add `RateLimit-*` response headers. One PR, isolated to `RateLimitFilter`.
4. **F-07** — Completion API for project keys, statuses, issue types, users. Self-contained.
5. **F-08** — wire cancellation through batch tools. Requires careful interruption design.
6. **F-10** — resource templates (`jira://issue/{key}`).
7. **F-06** — scope consent (needs scope ↔ tool mapping design first).
8. **F-12, F-13, F-14, F-16, F-18** — polish bundle once SDK supports the underlying APIs (some require waiting on `mcp-core` post-M2).
9. **F-15 (Tasks)** — defer until experimental designation is dropped, **or** prototype behind a feature flag if `batch_create_issues` UX pain is acute.
10. **F-03 + F-11** — bump `MCP-Protocol-Version` to `2025-11-25` last, only once feature parity meets your bar.

---

## 7. Source files referenced

- [`src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java`](../src/main/java/com/atlassian/mcp/plugin/rest/McpBootstrap.java) — capability declaration
- [`src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java`](../src/main/java/com/atlassian/mcp/plugin/rest/McpToolAdapter.java) — tool annotation construction
- [`src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java`](../src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java) — resource list
- [`src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java`](../src/main/java/com/atlassian/mcp/plugin/tools/McpTool.java) — tool interface
- [`.upstream/modelcontextprotocol/docs/specification/2025-11-25/changelog.mdx`](../.upstream/modelcontextprotocol/docs/specification/2025-11-25/changelog.mdx) — full spec delta
- [`.upstream/modelcontextprotocol/docs/specification/2025-11-25/server/tools.mdx`](../.upstream/modelcontextprotocol/docs/specification/2025-11-25/server/tools.mdx) — tool annotations / icons / title
- [`.upstream/modelcontextprotocol/docs/specification/2025-11-25/basic/utilities/cancellation.mdx`](../.upstream/modelcontextprotocol/docs/specification/2025-11-25/basic/utilities/cancellation.mdx) — cancellation semantics
- [`.upstream/modelcontextprotocol/docs/specification/2025-11-25/basic/utilities/tasks.mdx`](../.upstream/modelcontextprotocol/docs/specification/2025-11-25/basic/utilities/tasks.mdx) — experimental long-running ops
