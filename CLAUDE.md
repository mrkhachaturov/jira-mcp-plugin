# jira-mcp-plugin

Native Jira Data Center plugin that embeds an MCP (Model Context Protocol) server. AI agents connect via OAuth 2.0 or PATs. 49 tools mirrored 1:1 from the upstream [mcp-atlassian](https://github.com/sooperset/mcp-atlassian) Python project.

## Upstream Parity

The upstream Python project at `.upstream/mcp-atlassian/` is the **source of truth** for all tool definitions. Our plugin is a Java translation — same tool names, same parameters, same descriptions, same behavior. Never invent new tools or modify tool interfaces without checking upstream first.

### Code Generation

Tool classes are generated from the upstream Python definitions:

```bash
just codegen          # runs .codegen/translate.py
```

The translator (`python3 .codegen/translate.py`):
1. Parses all `@jira_mcp.tool()` definitions from `.upstream/mcp-atlassian/src/mcp_atlassian/servers/jira.py` via Python AST
2. Extracts names, descriptions, parameter schemas, read/write flags, toolset categories
3. Generates Java `McpTool` classes into `.codegen/generated/tools/`
4. Produces a parity report at `.codegen/generated/report.txt`
5. Produces a `ToolRegistry_fragment.java` with registration code

After generation, copy files to `src/main/java/.../tools/` and update `ToolRegistry.java`. Some tools need hand-tuned `execute()` bodies (especially write tools that must construct Jira REST API payloads like `{"fields": {...}}`).

### When Upstream Updates

1. Pull latest upstream into `.upstream/mcp-atlassian/`
2. Run `just codegen`
3. Review `.codegen/generated/report.txt` for new/changed tools
4. Copy generated files, fix any `execute()` bodies for new tools
5. Update `ToolRegistry.java` if new tools were added
6. Run `just e2e` to verify

## Architecture

| Layer | What |
|-------|------|
| MCP endpoint | JAX-RS at `/rest/mcp/1.0/` — Streamable HTTP (JSON-RPC 2.0 + SSE) |
| OAuth proxy | Servlet at `/plugins/servlet/mcp-oauth/` — bridges MCP client OAuth with Jira OAuth 2.0. Supports `authorization_code` + `refresh_token` grants. Tokens passed through from Jira (stateless — Jira's DB manages lifecycle) |
| Tools | 49 classes in `tools/` — each calls Jira REST API internally via `JiraRestClient` |
| Response trimmer | `ResponseTrimmer` — strips verbose fields (`self`, `avatarUrls`, `iconUrl`, `groups`, `applicationRoles`) to match upstream's `to_simplified_dict()` |
| Admin | Servlet at `/plugins/servlet/mcp-admin` + REST at `/rest/mcp-admin/1.0/` |
| Config | `McpPluginConfig` backed by Jira `PluginSettings` (key-value) |
| Auth | OAuth 2.0 (via Application Link) or PAT — Jira validates tokens, plugin checks access control |

## Build & Deploy

All commands via `just`. Env vars auto-loaded by mise from `.credentials/jira.env`.

```bash
just build            # atlas-package (compile + JAR)
just deploy           # build + upload JAR to Jira UPM + verify enabled
just test             # unit tests (excludes e2e)
just e2e              # 44 e2e tests against live Jira instance
just deploy-and-test  # build + deploy + e2e in one shot
just codegen          # regenerate tools from upstream
just clean            # atlas-clean
```

**Local builds must use `atlas-mvn`** (not plain `mvn`). The Atlassian SDK wrapper includes the Atlassian Maven repository. Plain `mvn` only works when Atlassian repos are configured in `~/.m2/settings.xml` (which CI does via GitHub Actions).

## Key Identifiers

| What | Value |
|------|-------|
| Plugin key | `com.atlassian.mcp.jira-mcp-plugin` |
| Maven coordinates | `com.atlassian.mcp:jira-mcp-plugin` |
| MCP endpoint | `POST /rest/mcp/1.0/` |
| OAuth endpoints | `/plugins/servlet/mcp-oauth/{metadata,register,authorize,callback,token}` |
| Admin REST | `GET/PUT /rest/mcp-admin/1.0/` |
| Admin page | `/plugins/servlet/mcp-admin` |
| Target Jira | Data Center 10.x |

## MCP Protocol — Streamable HTTP

Single endpoint `/rest/mcp/1.0/` supporting Streamable HTTP transport (MCP spec 2025-06-18).

| Method | Action |
|--------|--------|
| `initialize` | Return server info + capabilities + `MCP-Session-Id` header |
| `notifications/initialized` | Return 202 |
| `tools/list` | Return filtered tool list |
| `tools/call` | Dispatch to tool, return result |
| `ping` | Keep-alive |

### Transport behavior

- **POST** — client sends JSON-RPC. Server returns `application/json` for single responses
- **POST with `progressToken`** — if tool supports progress (`supportsProgress() = true`), server returns `text/event-stream` with progress notifications followed by final result
- **GET** — SSE stream for server-initiated notifications (requires `MCP-Session-Id`)
- **DELETE** — close session

### When SSE streaming is used

The server decides per-request. SSE is ONLY used when:
1. The client sends a `progressToken` in `params._meta.progressToken`
2. AND the tool implements `supportsProgress() = true`

Otherwise, the response is always plain JSON. SSE is for sending **multiple JSON-RPC messages** (progress notifications before the final result), not for wrapping big responses.

### Streaming-capable tools

| Tool | What it streams |
|------|----------------|
| `batch_create_issues` | Progress per issue created |
| `batch_create_versions` | Progress per version created |
| `batch_get_changelogs` | Progress per issue's changelog fetched |
| `get_issues_development_info` | Progress per issue's dev info fetched |

### Session management

- `MCP-Session-Id` returned on `initialize`, required on subsequent requests
- Sessions stored in static `ConcurrentHashMap` (survives JAX-RS per-request instantiation)
- Session-user binding: each session is tied to the authenticated user; cross-user access returns 403
- Sessions capped at 200 with 4-hour TTL; expired sessions cleaned lazily
- DELETE closes session (requires auth + user match), 404 returned for expired/unknown sessions

### Security

- **Auth on all methods**: POST, GET (SSE), and DELETE all require valid auth + access control
- **Origin validation** (MUST per spec): `Origin` header checked against Jira base URL + `claude.ai`/`claude.com`. Invalid Origin → 403. Localhost always allowed
- **MCP-Protocol-Version** header validated on non-initialize requests
- **Rate limiting**: IP-based for anonymous endpoints (`/register` 5/min, `/token` 20/min, `/authorize` 10/min), per-user for MCP (120/min). Implemented in `RateLimiter.java`
- **Request body size limits**: 1 MB for MCP POST, 64 KB for DCR register, 8 KB for token exchange
- **Session-user binding**: sessions cannot be used by a different user than the one who created them
- **PKCE S256 mandatory**: `code_challenge` required on `/authorize`, only `S256` method accepted
- **Redirect URI validation**: `/authorize` validates `redirect_uri` against registered client URIs (prevents open redirect / token theft)
- **Security event logging**: all rejections logged with `[MCP-SEC]` prefix and client IP
- **Security headers**: `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Frame-Options: DENY`
- **In-memory map caps**: DCR clients (1000, 24h TTL), pending auths (500, 10min), proxy codes (500, 10min)
- **Token exchange hardened**: `HttpClient` with `Redirect.NEVER`, 5s connect timeout, 10s request timeout
- **Refresh tokens**: passed through from Jira — no proxy state, Jira's DB manages lifecycle and rotation

## Tools — 49 Total

| Package | Count | Toolset | Plugin Requirement |
|---------|-------|---------|--------------------|
| `issues/` | 8 | `jira_issues` | — |
| `comments/` | 2 | `jira_comments` | — |
| `transitions/` | 2 | `jira_transitions` | — |
| `worklogs/` | 2 | `jira_worklog` | — |
| `boards/` | 7 | `jira_agile` | Jira Software |
| `links/` | 5 | `jira_links` | — |
| `projects/` | 5 | `jira_projects` | — |
| `users/` | 4 | `jira_watchers` / `jira_users` | — |
| `attachments/` | 2 | `jira_attachments` | — |
| `fields/` | 2 | `jira_fields` | — |
| `servicedesk/` | 3 | `jira_service_desk` | JSM |
| `forms/` | 3 | `jira_forms` | Proforma |
| `metrics/` | 4 | `jira_sla` / `jira_development` | JSM (SLA only) |

Tools with a plugin requirement are automatically hidden from `tools/list` if that plugin isn't installed.

### Tool Interface

Every tool implements `McpTool`:

```java
public interface McpTool {
    String name();                          // snake_case, matches upstream
    String description();                   // from upstream docstring
    Map<String, Object> inputSchema();      // JSON Schema from upstream Field() annotations
    boolean isWriteTool();                  // true = hidden in read-only mode
    default String requiredPluginKey() { return null; }
    String execute(Map<String, Object> args, String authHeader) throws McpToolException;
}
```

### Writing execute() Bodies

Tools call Jira REST API directly via `JiraRestClient.get/post/put/delete()`. Key patterns:

- **GET tools**: Build query string, return `client.get(path + query, authHeader)`
- **POST/PUT tools**: Build `Map<String, Object>`, serialize with Jackson, send as body
- **Create/Update issue**: Must wrap fields in `{"fields": {"project": {"key": "..."}, ...}}` — Jira API requirement
- **JSON string params** (like `fields`, `additional_fields`): Parse with `mapper.readValue(str, Map.class)` before sending
- **Components param**: Parse comma-separated string into `[{"name": "Frontend"}, {"name": "API"}]`

## Response Trimming

`ResponseTrimmer` runs on all `JiraRestClient` responses. It strips fields that the upstream's Pydantic `to_simplified_dict()` never includes:

**Stripped recursively:** `avatarUrls`, `iconUrl`, `expand`, `groups`, `applicationRoles`, avatar size keys (`48x48`, `32x32`, etc.)

**Note:** `self` links are **kept** — upstream's `JiraIssueLinkType.to_simplified_dict()` includes them, and `JiraUser` converts the 48x48 avatar URL to `avatar_url`.

**Stripped at top level:** `renderedFields`, `names`, `schema`, `editmeta`, `versionedRepresentations`, `operations`

**Field renames:** `issuetype` → `issue_type`, `fixVersions` → `fix_versions`

## Admin Config (PluginSettings keys)

| Key | Default | Purpose |
|-----|---------|---------|
| `com.atlassian.mcp.plugin.enabled` | false | Global MCP on/off |
| `com.atlassian.mcp.plugin.allowedUsers` | "" | Comma-separated usernames |
| `com.atlassian.mcp.plugin.allowedGroups` | "" | Comma-separated group names |
| `com.atlassian.mcp.plugin.disabledTools` | "" | Comma-separated tool names |
| `com.atlassian.mcp.plugin.readOnlyMode` | false | Hide write tools |
| `com.atlassian.mcp.plugin.jiraBaseUrl` | "" | Override internal base URL |
| `com.atlassian.mcp.plugin.oauthClientId` | "" | OAuth Application Link client ID |
| `com.atlassian.mcp.plugin.oauthClientSecret` | "" | OAuth Application Link client secret |

## E2E Tests

44 tests in `src/test/java/.../e2e/McpEndpointE2ETest.java`. Requires env vars from `.credentials/jira.env` (auto-loaded by mise).

| Category | What |
|----------|------|
| Protocol | initialize, ping, invalid method |
| Tools list | count, upstream parity, schema validation |
| Read tools | get_all_projects, get_user_profile, search_fields, get_link_types, search, get_agile_boards |
| Response trimming | no self, no avatarUrls, no iconUrl |
| Issue CRUD | create → get → comment → update → delete lifecycle |
| Service desk | get_service_desk_for_project |
| Error handling | missing param, invalid key, unknown tool |
| Access control | CEO user via group allowlist |
| Security | GET/DELETE without auth → 401, oversized body → 413, session-user binding → 403, trailing slash redirect → 307, OAuth well-known endpoints, DCR + PKCE enforcement, security headers |
| OAuth refresh | metadata advertises refresh_token grant, error paths (missing token, bogus token, unsupported grant) |

Tests skip automatically when `JIRA_URL`/`JIRA_PAT_RKADMIN` are not set.

## Project Structure

```
src/main/java/com/atlassian/mcp/plugin/
├── rest/
│   ├── McpResource.java              # JAX-RS MCP endpoint (POST/GET/DELETE)
│   ├── OAuthServlet.java             # OAuth proxy servlet
│   ├── OAuthAnonymousFilter.java     # before-login filter for anonymous OAuth access
│   └── RateLimiter.java              # IP-based rate limiter for anonymous + authenticated endpoints
├── JsonRpcHandler.java                # JSON-RPC dispatch
├── JiraRestClient.java                # HTTP client → Jira REST API (+ ResponseTrimmer)
├── ResponseTrimmer.java               # Strip verbose fields from Jira JSON responses
├── McpToolException.java              # Checked exception for tool failures
├── config/
│   ├── McpPluginConfig.java           # PluginSettings-backed configuration
│   └── OAuthStateStore.java           # In-memory OAuth state
├── admin/
│   ├── AdminServlet.java              # Admin page (Velocity)
│   └── ConfigResource.java           # Admin REST API
└── tools/
    ├── McpTool.java                   # Tool interface
    ├── ToolRegistry.java              # 49 tools registered, filtered by capability/config
    ├── issues/                        # 8 tools
    ├── comments/                      # 2 tools
    ├── transitions/                   # 2 tools
    ├── worklogs/                      # 2 tools
    ├── boards/                        # 7 tools (require Jira Software)
    ├── links/                         # 5 tools (includes link_to_epic)
    ├── projects/                      # 5 tools
    ├── users/                         # 4 tools
    ├── attachments/                   # 2 tools
    ├── fields/                        # 2 tools
    ├── servicedesk/                   # 3 tools (require JSM)
    ├── forms/                         # 3 tools (require Proforma)
    └── metrics/                       # 4 tools

.codegen/
├── translate.py                       # Upstream Python → Java translator
└── generated/                         # Output of translate.py (not committed)

.upstream/
├── mcp-atlassian/                     # Upstream Python project (git subtree/submodule)
└── java-sdk/                          # Official MCP Java SDK (reference)

.credentials/                          # gitignored — PATs, OAuth config, deploy workflow
```

## Hard-Won Lessons

### javax, NOT jakarta
Jira 10.x API JARs use `javax.servlet`, `javax.ws.rs`, `javax.inject`. Always use `javax.*` imports.

### Spring Scanner requires scan-indexes XML
`@ComponentImport` requires `src/main/resources/META-INF/spring/plugin-context.xml` with `<atlassian-scanner:scan-indexes/>`.

### Plugin key must match Bundle-SymbolicName
`atlassian-plugin.xml` key must be `${atlassian.plugin.key}` = `com.atlassian.mcp.jira-mcp-plugin`.

### DynamicImport-Package is required
Without `<DynamicImport-Package>*</DynamicImport-Package>` in pom.xml, runtime class resolution fails.

### Anonymous REST access in Jira 10
Use `@UnrestrictedAccess` from `com.atlassian.annotations.security` (NOT the old `@AnonymousAllowed`). Combined with a `before-login` servlet filter for full anonymous access.

### REST package scan must be specific
Use `<package>com.atlassian.mcp.plugin.rest</package>` — never the parent package.

### Version bumps bust JS/CSS cache
Jira CDN caches web resources by plugin version. Bump version in pom.xml to force browsers to load new JS/CSS.

### Plugin enable timeout
Jira's internal `jira-migration` plugin can cause timeout during enable. Disable it temporarily, or click "Enable" manually in UPM.

### Write tools must structure Jira payloads correctly
The code generator produces flat `requestBody.put("field", value)` for POST/PUT tools. Jira's REST API often expects nested structures like `{"fields": {"project": {"key": "..."}, "issuetype": {"name": "..."}}}`. Always verify write tool payloads against Jira REST API docs.

## Critical Rules

- **Always use `javax.*`** imports, never `jakarta.*`
- **Plugin key is `com.atlassian.mcp.jira-mcp-plugin`** everywhere
- **Use `atlas-mvn`** for local builds, never plain `mvn`
- **Use `just`** for all workflows — build, deploy, test, codegen
- **Bump version** in pom.xml when changing JS/CSS (cache busting)
- **Run `just e2e`** after any tool changes to verify against live Jira
- **Mirror upstream exactly** — same tool names, params, descriptions, behavior
