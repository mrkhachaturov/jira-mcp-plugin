# Jakarta + MCP Java SDK rebuild — design

| | |
|---|---|
| **Date** | 2026-05-21 |
| **Branch** | `feature/jakarta-jira-11` (worktree at `.worktrees/jakarta-migration`) |
| **Trigger** | Jira DC upgraded to 11.x. Tomcat 10.1 + Spring 6 + Jakarta EE 10 reject the existing javax-compiled plugin at OSGi bundle-resolve time. Plugin is broken in production. |
| **Status** | Approved through brainstorming, pending dual-review |

## Summary

Rebuild `jira-mcp-plugin` for Jira 11 (jakarta) using the official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp-core` 2.0.0-M2) as the transport and protocol foundation. The hand-rolled JSON-RPC dispatcher and Streamable HTTP servlet (~1,200 lines) get deleted. The 49 tool implementations, OAuth proxy, MCP Apps widget, admin UI, response trimmer, and rate limiter all stay — they become building blocks wired into the SDK through a thin adapter layer.

This is framed as a rebuild rather than a migration because the SDK is jakarta-native (Spring 6.2.1, `jakarta.servlet` 6.1.0) and was never compatible with our javax codebase. The forced jakarta move unblocks adopting the SDK for the first time, so doing both as a single change is cleaner than sequencing.

## Goal

Make the plugin load and function on Jira 11.x DC with behavioral parity for all 49 tools, all 54 e2e tests, the OAuth proxy, the MCP Apps issue-card widget, and the admin page. Replace our hand-rolled MCP protocol implementation with the official SDK.

## Non-goals

- New tools, behavior changes visible to MCP clients, or response-shape changes
- `@ScopesAllowed` / OAuth 2.0 client-credentials (2LO) support — separate follow-up
- Calling native Jira Java APIs instead of REST — separate follow-up
- SSE-by-default for non-batch tools — separate follow-up
- AUI Dropdown 2 / Atlaskit migration — orthogonal
- JS/CSS changes to the React widget or admin page
- Bumping `mcp-core` to 2.0 GA when it ships

## Context

The plugin currently targets Jira 10.7.4 (Tomcat 9, Spring 5, Java 17, javax). It implements MCP Streamable HTTP transport via a hand-rolled JAX-RS endpoint (`McpResource`) and JSON-RPC dispatcher (`JsonRpcHandler`). Session management, origin validation, body-size limits, and rate limiting all live inside the same class.

Three earlier design choices were specifically javax-era workarounds, documented in `docs/rkstack/specs/2026-04-06-jira-mcp-plugin-design.md`:

1. **Hand-rolled JSON-RPC dispatcher** — chosen because the official MCP Java SDK requires jakarta. Couldn't be used on Jira 10.
2. **"Streamable HTTP, JSON only (no SSE) — Jira async servlets broken"** — Tomcat 9 async servlet support was unreliable.
3. **REST API instead of native Jira APIs** — sidestepped Spring 5 / OSGi classloader conflicts.

(1) is unblocked by this change. (2) and (3) remain out of scope here — they get separate design cycles once we're on the new platform.

Reference material that grounds this design:

- `.upstream/atlassian-spring-scanner` on branch `6.0.x` — the jakarta-era scanner pom, used as the source of truth for pinned versions (Java 21, AMPS 9.1.9, scanner 6.0.2, platform BOM 8.1.13)
- `.upstream/atlassian-platform-bom/platform-public-api-8.1.13.pom` and siblings — the Atlassian platform BOM that manages all jakarta + Spring + Jackson + Atlassian dep versions transitively
- `.upstream/java-sdk` on `2.0.0-SNAPSHOT` — the MCP Java SDK source, used to verify the servlet transport, the `McpTransportContextExtractor` hook for auth injection, and the OSGi bundle manifest
- `docs/jira 11.md` — Atlassian's "Preparing for Jira 11.0" page, downloaded for offline reference

## Architecture

### The two halves

The plugin splits cleanly into two zones that don't share state.

**Zone A — MCP transport + tools.** This is what's rebuilt on the SDK. A single Jira `<servlet>` at `/plugins/servlet/mcp` wraps the SDK's `HttpServletStreamableServerTransportProvider`. A Spring-injected bootstrap component (`McpBootstrap`) builds the `McpSyncServer`, registers all 49 tools as `SyncToolSpecification`s and MCP App resources as `SyncResourceSpecification`s, applies config-driven filtering (disabled tools, read-only mode, plugin-capability gating), and binds the server to the transport provider. A `McpTransportContextExtractor` pulls the `Authorization` header off each request and stashes the principal plus token in the per-request context that tool handlers then read. A chain of `jakarta.servlet.Filter`s in front of the transport servlet handles origin validation, body-size limits, rate limiting, session-user binding, and security headers.

**Zone B — Everything else.** The OAuth proxy servlet at `/plugins/servlet/mcp-oauth/*` is entirely separate from MCP; it's the standard DCR/PKCE/code-exchange flow already in place. The admin servlet, admin REST endpoint, Velocity template, and `admin.js` are untouched apart from the jakarta import sweep. The React widget under `mcp-app/` is pure frontend and doesn't change at all. `McpPluginConfig`, `OAuthStateStore`, `RateLimiter`, `JiraRestClient`, and `ResponseTrimmer` are used by both zones and stay as utility classes.

### Data flow for a tool call

```
Client POST /plugins/servlet/mcp
   ↓
Filter chain (jakarta.servlet.Filter)
   • OAuthAnonymousFilter — skip auth on OAuth paths
   • OriginValidationFilter — Origin header against allowlist
   • RateLimitFilter — per-user / per-IP
   • BodySizeLimitFilter — 1 MB cap
   ↓
HttpServletStreamableServerTransportProvider (SDK)
   • parse JSON-RPC
   • route to tools/call handler
   • McpTransportContextExtractor builds {jiraUser, authHeader}
   ↓
SyncToolSpecification call handler (McpToolAdapter)
   • read context → authHeader
   • dispatch to McpTool.execute(args, authHeader)
   ↓
Tool execute() body — unchanged
   • JiraRestClient call → Jira REST
   • ResponseTrimmer cleans the response
   • return CallToolResult.builder().content(TextContent(...)).build()
   ↓
SDK serializes JSON-RPC response, transport writes it back
```

### The bridge layer

One class. If this is right, the rest of the change is fill-in-the-blanks.

```java
final class McpToolAdapter {
    static SyncToolSpecification adapt(McpTool tool) {
        return SyncToolSpecification.builder()
            .tool(Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build())
            .callHandler((exchange, request) -> {
                String authHeader = exchange.transportContext()
                    .get("authHeader", String.class);
                try {
                    String json = tool.execute(request.arguments(), authHeader);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(json)))
                        .build();
                } catch (McpToolException e) {
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(e.getMessage())))
                        .isError(true)
                        .build();
                }
            })
            .build();
    }
}
```

### Atlassian descriptor vs SDK servlet

Atlassian's `<servlet>` descriptor instantiates servlets via a no-args constructor and Spring-injects dependencies. The SDK's `HttpServletStreamableServerTransportProvider` is configured via a builder, not a constructor. A thin wrapper bridges this:

```java
@Named("jiraMcpServlet")
public class JiraMcpServlet extends HttpServlet {
    private final HttpServletStreamableServerTransportProvider delegate;

    @Inject
    public JiraMcpServlet(McpBootstrap bootstrap) {
        this.delegate = bootstrap.buildTransport();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        delegate.service(req, resp);
    }
}
```

This is the one place where the SDK shape meets Atlassian's plugin framework. It is the single highest-risk piece of the rebuild and is treated as a spike at the start of the transport commit (see Risks below).

## Dependencies

Pinned versions, taken from the on-disk references rather than guessed:

| Property | Value | Source |
|---|---|---|
| `jira.version` | `11.3.6` | latest Jira 11 GA from Maven Central |
| `java` | 21 | Jira 11 requirement |
| `amps.version` | `9.1.9` | `.upstream/atlassian-spring-scanner` 6.0.x |
| `atlassian.spring.scanner.version` | `6.0.2` | jakarta line, latest released tag |
| `platform.version` | `8.1.13` | Atlassian platform BOM |
| `mcp.sdk.version` | `2.0.0-M2` | Maven Central |

`jakarta.servlet-api 6.0.0`, `jakarta.ws.rs-api 3.1.0`, `jakarta.inject-api 2.0.1`, `jakarta.annotation-api 2.1.1`, `spring 6.2.15`, `jackson 2.19.4`, `atlassian-rest 9.0.5` — all managed by the platform BOM, no explicit versions in our pom.

The OSGi `Import-Package` directive is flipped from `javax.inject*;resolution:="optional"` to `jakarta.inject*;resolution:="optional"`. `DynamicImport-Package: *` stays for now and is narrowed only if it bites.

## Class-by-class disposition

**D**eleted, **M**odified, **W**rapped, **U**ntouched (jakarta import sweep only), or **N**ew.

### Transport layer

| File | Disp. | Notes |
|---|---|---|
| `rest/McpResource.java` | D | JAX-RS endpoint — replaced by SDK transport servlet |
| `JsonRpcHandler.java` | D | Hand-rolled JSON-RPC dispatch — SDK owns this |
| `rest/JiraMcpServlet.java` | N | Thin `HttpServlet` wrapper that delegates to SDK provider |
| `McpBootstrap.java` | N | `@Named` component, builds `McpSyncServer` from `ToolRegistry` + config |
| `McpToolAdapter.java` | N | Static `adapt(McpTool) → SyncToolSpecification` |
| `JiraAuthContextExtractor.java` | N | Implements `McpTransportContextExtractor` |

### Filters (extracted from `McpResource` into reusable `jakarta.servlet.Filter`s)

| File | Disp. | Notes |
|---|---|---|
| `rest/OAuthAnonymousFilter.java` | M | jakarta sweep, URL pattern unchanged |
| `rest/OriginValidationFilter.java` | N | Origin allowlist (Jira base URL + claude.ai + claude.com + localhost) |
| `rest/BodySizeLimitFilter.java` | N | 1 MB for MCP POST, 64 KB DCR register, 8 KB token exchange |
| `rest/RateLimitFilter.java` | N | Delegates to existing `RateLimiter` util |
| `rest/SecurityHeadersFilter.java` | N | `X-Content-Type-Options`, `Cache-Control: no-store`, `X-Frame-Options: DENY` |
| `rest/RateLimiter.java` | U | Stays as the utility called by the filter |

### Tools

| File | Disp. | Notes |
|---|---|---|
| `tools/McpTool.java` | M | Interface stays nearly identical; `inputSchema()` already returns `Map<String,Object>` ✓ |
| `tools/ToolRegistry.java` | M | Stops being a dispatcher; emits `List<SyncToolSpecification>` for `McpBootstrap` |
| `tools/{issues,comments,...}/*.java` × 49 | M | `javax.inject.*` → `jakarta.inject.*` sweep; `execute()` bodies untouched |
| `tools/batch/*.java` × 4 | M | Above plus rewire `supportsProgress()` callers to `exchange.progressNotification(...)` |
| `McpToolException.java` | U | Pure java |

### MCP Apps resources

| File | Disp. | Notes |
|---|---|---|
| `ResourceRegistry.java` | M | Emits `SyncResourceSpecification`s. Dual-metadata (`_meta.ui` for Claude + `openai/widget*` for ChatGPT) handled either by including `_meta` at `Resource.builder()` time if the 2.0-M2 builder supports it, or by a custom `SyncResourceSpecification` subclass whose `resource()` accessor returns a `Resource` with the dual-metadata fields populated at wire time |

### Zone B (jakarta import sweep, no behavior change)

| File | Disp. |
|---|---|
| `rest/OAuthServlet.java`, `admin/AdminServlet.java`, `admin/ConfigResource.java`, `config/McpPluginConfig.java`, `config/OAuthStateStore.java`, `JiraRestClient.java` | M (imports only) |
| `ResponseTrimmer.java`, `mcp-app/` | U |
| `src/main/resources/atlassian-plugin.xml` | M (servlet decl + filter decls) |

## Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Atlassian `<servlet>` descriptor doesn't compose with SDK provider | Medium | High — blocks everything | Build `JiraMcpServlet` wrapper as a spike at the start of the transport commit. Fallback: register the servlet programmatically via `ServletModuleManager` at plugin enable. |
| OSGi `Import-Package` for `io.modelcontextprotocol.*` | Low | Medium | SDK ships with proper bnd manifest (`Export-Package: io.modelcontextprotocol.*`). Start with current `DynamicImport-Package: *`; narrow only if conflicts surface. |
| Jackson version drift between Jira-bundled and BOM-managed | Medium | Low | Pin Jackson `provided` scope; let Jira's runtime version win |
| MCP App resource dual-metadata wire emission | Medium | Medium — degrades to text-only widget if unfixed | Either include `_meta` at `Resource.builder()` if the M2 builder exposes it, or write a custom `SyncResourceSpecification` whose `resource()` accessor returns the dual-metadata-bearing record. Spike at the start of commit 4. |
| E2E test JSON-shape assertions | High | Low | Most tests check tool *content*, not transport envelope. Expect 5–10 tests to need updates. Fix as discovered. |
| Session-user binding | Medium | High *(security)* | Verify SDK session model exposes session-creator. If not, enforce in a pre-transport filter. |
| Progress streaming API differences | Low | Low | SDK provides `exchange.progressNotification(...)`. Refactor per tool (~5 lines each). |
| SDK 2.0-M2 milestone churn | Low | Low | Pin exact version. Revisit only on 2.0 GA. |

## Verification

`just build-app && just deploy && just e2e` against the live Jira 11 instance referenced by `.credentials/jira.env`. The 54 e2e tests are the safety net. Plus one manual smoke test:

- Open Claude Desktop pointed at the plugin's MCP endpoint, call `get_issue`, confirm the MCP App widget renders the issue card and that subsequent tool calls work.

No `atlas-run` is needed — the user's real Jira 11 IS the test bed. The plugin's current state on that instance is broken (javax classes refused by Tomcat 10.1), so the migration's success criterion is "plugin loads, all 54 e2e tests green, widget renders in Claude Desktop."

## Commit shape

Five atomic commits on `feature/jakarta-jira-11`:

1. **`chore(deps): bump to Jira 11 + Jakarta EE 10 + Java 21 + MCP SDK 2.0.0-M2 + platform BOM 8.1.13`** — pom only. Branch broken to compile, expected.
2. **`feat(transport): replace hand-rolled JSON-RPC with MCP SDK servlet transport`** — delete `McpResource` + `JsonRpcHandler`; add `JiraMcpServlet`, `McpBootstrap`, `McpToolAdapter`, `JiraAuthContextExtractor`; extract filters; update `atlassian-plugin.xml`. Branch compiles with 1–2 tools wired for smoke testing.
3. **`refactor(tools): adapt 49 tools to SyncToolSpecification + jakarta sweep`** — `ToolRegistry` emits specs; all 49 tools get `jakarta.inject.*`; 4 batch tools migrate to `exchange.progressNotification(...)`. All 54 e2e tests should pass after this commit.
4. **`refactor(resources+rest): MCP Apps → SyncResourceSpecification + jakarta sweep for OAuth/admin`** — `ResourceRegistry` emits specs with dual-metadata mutator; `OAuthServlet`, `OAuthAnonymousFilter`, `AdminServlet`, `ConfigResource` get jakarta imports.
5. **`docs: flip CLAUDE.md rules + bump version + update oauth-proxy plan`** — grep `.upstream/atlassian-spring-scanner/6.0.x` to confirm/refute each Hard-Won Lesson and rewrite with citations; bump pom version (cache busts JS/CSS); update `docs/rkstack/plans/2026-04-06-oauth-proxy.md:9` from "javax not jakarta" to the inverse.

The branch will not compile between commits 1 and 2. That's a feature, not a bug — the deps commit on its own documents intent without mixing it with the code rewrite.

## Rollout

Single PR from `feature/jakarta-jira-11` → `main`. The plugin's current state on the production Jira 11 is broken, so reverting wouldn't restore service — the e2e suite green-light is a hard gate before merging. If anything misbehaves post-merge, fix forward.

## Out of scope (recorded for future work)

- `@ScopesAllowed` for OAuth 2.0 client credentials (2LO) — separate additive PR
- Native Jira Java APIs for hot-path read tools — perf optimization, separate PR
- SSE-default for non-batch tools — separate PR once SDK transport behavior is well-understood
- Atlaskit / AUI Dropdown 2 migration — orthogonal UI work
- Bump `mcp-core` to 2.0 GA when published
