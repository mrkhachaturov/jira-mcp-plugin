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

- New tools, tool behavior changes, or `CallToolResult` shape changes
- `@ScopesAllowed` / OAuth 2.0 client-credentials (2LO) support — separate follow-up
- Calling native Jira Java APIs instead of REST — separate follow-up
- AUI Dropdown 2 / Atlaskit migration — orthogonal
- JS/CSS changes to the React widget or admin page
- Bumping `mcp-core` to 2.0 GA when it ships

### Behavioral changes that ARE in scope (not non-goals)

The SDK transport enforces the MCP Streamable HTTP spec (2025-06-18) more strictly than our hand-rolled implementation. These wire-level changes are accepted as part of the rebuild:

- **`Accept` header is now strictly required** to contain both `application/json` and `text/event-stream` on every POST. Our hand-rolled transport accepted requests with only `application/json`. Clients that violated the spec will receive `400 Bad Request`. Claude Desktop and ChatGPT already comply.
- **Response `Content-Type` is `text/event-stream` for most JSON-RPC responses after initialize**, not `application/json`. The body is still a single JSON-RPC message, framed as a one-event SSE stream. E2E tests that assert `Content-Type: application/json` on tool calls need to accept either content type and parse the SSE envelope when present (single `event: message` followed by `data: <json-rpc>`).
- **SSE-by-default is now in effect** for non-initialize responses. Our previous "JSON unless `progressToken`" rule is dropped — we accept the SDK's defaults because (a) Tomcat 10.1's async servlets work, (b) the MCP spec favors this shape, (c) maintaining a custom transport mode for legacy JSON-only clients would mean re-implementing what we're trying to delete.

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

**Zone A — MCP transport + tools.** This is what's rebuilt on the SDK. The SDK's `HttpServletStreamableServerTransportProvider` (built by `McpBootstrap`) **is** the servlet — no wrapper class. `McpPluginLifecycle` (a `LifecycleAware` Spring component) registers it programmatically with Atlassian's `ServletModuleManager` at `/plugins/servlet/mcp` on plugin enable, and unregisters on disable. `McpBootstrap` also builds the `McpSyncServer`, registers all 49 tools as `SyncToolSpecification`s and MCP App resources as `SyncResourceSpecification`s, and applies config-driven filtering (disabled tools, read-only mode, plugin-capability gating). The `JiraAuthContextExtractor` (an `McpTransportContextExtractor`) pulls the `Authorization` header off each request and stashes the principal plus token in the per-request transport context that tool handlers then read. A chain of `jakarta.servlet.Filter`s registered in `atlassian-plugin.xml` runs in front of the transport servlet: origin validation, body-size limits, rate limiting, **`SessionBindingFilter`** (the security boundary — see Session-user binding), and security headers.

**Zone B — Everything else.** The OAuth proxy servlet at `/plugins/servlet/mcp-oauth/*` is entirely separate from MCP; it's the standard DCR/PKCE/code-exchange flow already in place. The admin servlet, admin REST endpoint, Velocity template, and `admin.js` are untouched apart from the jakarta import sweep. The React widget under `mcp-app/` is pure frontend and doesn't change at all. `McpPluginConfig`, `OAuthStateStore`, `RateLimiter`, `JiraRestClient`, and `ResponseTrimmer` are used by both zones and stay as utility classes.

### Data flow for a tool call

```
Client POST /plugins/servlet/mcp
   ↓
Filter chain (jakarta.servlet.Filter, configured in atlassian-plugin.xml)
   • OAuthAnonymousFilter      — skip auth on OAuth paths
   • OriginValidationFilter    — Origin header against allowlist
   • BodySizeLimitFilter       — 1 MB cap
   • RateLimitFilter           — per-user / per-IP (delegates to RateLimiter)
   • SessionBindingFilter      — auth resolved → bind/validate sid before SDK
   • SecurityHeadersFilter     — adds nosniff, no-store, DENY
   ↓
HttpServletStreamableServerTransportProvider (SDK, registered programmatically
   via ServletModuleManager — the SDK provider IS the servlet, no wrapper)
   • requires Accept: application/json + text/event-stream
   • JiraAuthContextExtractor.extract() → ctx{authHeader, jiraUser}
   • parse JSON-RPC, route by method
   ↓
SyncToolSpecification call handler (McpToolAdapter)
   • read ctx.authHeader from McpSyncServerExchange.transportContext()
   • dispatch to McpTool.execute(args, authHeader) → ToolResponse{text, structuredContent?}
   ↓
Tool execute() body — unchanged
   • JiraRestClient → Jira REST
   • ResponseTrimmer cleans the response
   • return ToolResponse
   ↓
McpToolAdapter wraps as CallToolResult (TextContent + structuredContent for UI-linked tools)
   ↓
SDK serializes JSON-RPC response as a one-event SSE stream (text/event-stream)
   for non-initialize POSTs; application/json only for the initial initialize response
```

### The bridge layer

Two responsibilities live in the adapter: convert each `McpTool` to a `SyncToolSpecification`, and preserve the MCP Apps contract for the 5 UI-linked tools.

**Plain tool adaptation (44 of 49 tools):**

```java
final class McpToolAdapter {
    static SyncToolSpecification adapt(McpTool tool) {
        Tool.Builder t = Tool.builder()
            .name(tool.name())
            .description(tool.description())
            .inputSchema(tool.inputSchema());
        // UI-linked tools attach _meta.ui.resourceUri pointing at the widget resource
        if (tool.uiResourceUri() != null) {
            t.meta(Map.of("ui", Map.of("resourceUri", tool.uiResourceUri())));
        }
        return SyncToolSpecification.builder()
            .tool(t.build())
            .callHandler((exchange, request) -> invoke(tool, exchange, request))
            .build();
    }

    private static CallToolResult invoke(McpTool tool, McpSyncServerExchange exchange, CallToolRequest request) {
        String authHeader = exchange.transportContext().get("authHeader", String.class);
        try {
            ToolResponse r = tool.execute(request.arguments(), authHeader);
            CallToolResult.Builder b = CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(r.text())));
            // UI-linked tools also populate structuredContent so the widget can render
            if (r.structuredContent() != null) b.structuredContent(r.structuredContent());
            return b.build();
        } catch (McpToolException e) {
            return CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(e.getMessage())))
                .isError(true)
                .build();
        }
    }
}
```

The `McpTool` interface gains two methods that the existing implementations either implement (UI-linked tools) or return `null` (everything else):

```java
default String uiResourceUri() { return null; }              // e.g. "ui://jira/issue-card@{hash}"
// execute() return type changes from String → ToolResponse (record of text + Map<String,Object> structuredContent)
```

For the 44 non-UI tools, `structuredContent` is `null` and `uiResourceUri` is `null` — identical wire output to today's `{ content: [{ type: text, text: "..." }] }` shape. For the 5 UI-linked tools (`get_issue`, plus the four other widget-linked tools currently listed in `McpToolUiMeta` or equivalent), the adapter copies `structuredContent` straight through and attaches `_meta.ui.resourceUri` on the tool definition. Behavior parity with the current widget is preserved.

**Acceptance criteria for MCP Apps:**

- `resources/list` returns the `ui://jira/issue-card@{hash}` resource with the full set of `_meta` keys (see Class disposition).
- `tools/list` returns each UI-linked tool with `_meta.ui.resourceUri` pointing at the widget resource.
- `tools/call` for `get_issue` returns `content` (text) **and** `structuredContent` (issue map). The Claude Desktop widget renders the issue card on a real call.

### Atlassian descriptor vs SDK servlet

Atlassian's `<servlet>` descriptor instantiates servlets via a no-args constructor and Spring-injects dependencies. The SDK's `HttpServletStreamableServerTransportProvider` has a **private** constructor (verified at `.upstream/java-sdk/mcp-core/src/main/java/io/modelcontextprotocol/server/transport/HttpServletStreamableServerTransportProvider.java:142`) — it can only be instantiated via `HttpServletStreamableServerTransportProvider.builder().build()`. Subclassing is therefore not possible.

There is exactly one approach: **programmatic registration via `ServletModuleManager`.** At plugin enable, a `LifecycleAware` Spring component asks the OSGi service registry for `ServletModuleManager` and registers the SDK transport instance directly. This is the path the SDK conformance tests use against Tomcat and matches what production Atlassian plugins (e.g. BigPicture) do for servlets that need builder-configured instances. The Atlassian framework cooperates because `ServletModuleManager.addServlet(...)` accepts an `HttpServlet` instance, not a class name.

```java
@Named("mcpPluginLifecycle")
public class McpPluginLifecycle implements LifecycleAware {
    @Inject
    public McpPluginLifecycle(McpBootstrap bootstrap, ServletModuleManager smm) {
        this.bootstrap = bootstrap;
        this.smm = smm;
    }
    @Override public void onStart() {
        HttpServlet sdkTransport = bootstrap.buildTransport();
        // Wrap the SDK transport with a session-binding filter chain (see Session-user binding section)
        ref = smm.addServlet("mcp-transport", "/plugins/servlet/mcp", sdkTransport,
            Map.of("asyncSupported", "true"));
    }
    @Override public void onStop() { ref.unregister(); }
}
```

`asyncSupported=true` is required because the SDK transport calls `request.startAsync()` for SSE responses. If the registration API does not accept that attribute, the fallback is to register the servlet via a `ServletContextListener` that adds it through `ServletContext.addServlet(...).setAsyncSupported(true)` during context init. The transport-commit spike confirms which API surface Atlassian's `ServletModuleManager` actually exposes on Jira 11 and pins the chosen call.

The `<servlet>` descriptor in `atlassian-plugin.xml` is **not** used for the MCP transport. The OAuth proxy, admin servlet, and OAuth-anonymous filter continue to use the descriptor as before — only the MCP transport endpoint moves to programmatic registration.

This is the single highest-risk piece of the rebuild. The transport-commit spike must produce: a deployed plugin that accepts an `initialize` POST at `/plugins/servlet/mcp` (with the required `Accept: application/json, text/event-stream` header) and returns a valid MCP `initialize` response, plus a one-line confirmation in the commit message of the exact `ServletModuleManager` call used.

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

Two MCP SDK artifacts are required:

- `io.modelcontextprotocol.sdk:mcp-core:2.0.0-M2` — protocol types, server builder, transport providers
- `io.modelcontextprotocol.sdk:mcp-json-jackson2:2.0.0-M2` — Jackson 2 implementations of `McpJsonMapperSupplier` and `JsonSchemaValidatorSupplier`

**The plugin does not rely on `ServiceLoader` or OSGi SCR to discover the JSON provider.** In an Atlassian plugin, third-party libraries are typically embedded in the plugin jar via OSGi `Private-Package` or `Include-Resource`, not installed as independent OSGi bundles. Embedded jars do not get OSGi SCR processing, and `ServiceLoader` discovery of `META-INF/services` entries inside embedded jars is unreliable across plugin classloader configurations. To avoid runtime "no `McpJsonMapper` available" failures, the plugin **explicitly constructs the Jackson mapper and passes it to the SDK builder**:

```java
ObjectMapper jackson = ...;                            // either a fresh instance or Jira's bundled mapper
McpJsonMapper jsonMapper = JacksonMcpJsonMapper.from(jackson);            // from mcp-json-jackson2
JsonSchemaValidator schemaValidator = JacksonJsonSchemaValidator.from(jackson);

HttpServletStreamableServerTransportProvider transport =
    HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(jsonMapper)
        .schemaValidator(schemaValidator)
        .mcpEndpoint("/plugins/servlet/mcp")
        .build();

McpSyncServer server = McpServer.sync(transport)
    .jsonMapper(jsonMapper)                            // also passed to the server for tool I/O
    .serverInfo("jira-mcp-plugin", pluginVersion)
    .capabilities(...)
    .tools(toolRegistry.toSpecifications())
    .resources(resourceRegistry.toSpecifications())
    .build();
```

(Exact class names — `JacksonMcpJsonMapper`, `JacksonJsonSchemaValidator` — to be verified in commit 1 against the published `mcp-json-jackson2:2.0.0-M2` jar. If the public factory names differ, adjust the call sites.)

The `mcp-json-jackson2` module brings in `com.networknt:json-schema-validator` transitively. The platform BOM 8.1.13 does not pin this; it resolves to whatever the SDK declares. Verify during the deps commit that no Jira-bundled bundle exports a clashing `com.networknt.schema` package, and embed the validator inside our plugin jar (`Private-Package: com.networknt.schema.*`).

`jakarta.servlet-api 6.0.0`, `jakarta.ws.rs-api 3.1.0`, `jakarta.inject-api 2.0.1`, `jakarta.annotation-api 2.1.1`, `spring 6.2.15`, `jackson 2.19.4`, `atlassian-rest 9.0.5` — all managed by the platform BOM, no explicit versions in our pom.

The OSGi `Import-Package` directive is flipped from `javax.inject*;resolution:="optional"` to `jakarta.inject*;resolution:="optional"`. `DynamicImport-Package: *` stays for now and is narrowed only if it bites. The SDK's bnd manifest exports `io.modelcontextprotocol.*`, so no explicit Import-Package entry for SDK packages is needed if `DynamicImport-Package: *` stays.

### Dependency verification — required step in commit 1

The API claims in this spec (private constructor on `HttpServletStreamableServerTransportProvider`, request-only `McpTransportContextExtractor`, `Resource.builder().meta(Map)`, OSGi exports, the `mcp-json-jackson2` SCR services) were verified against the on-disk source at `.upstream/java-sdk` which is on `2.0.0-SNAPSHOT` (between M2 and unreleased GA), and against `.upstream/atlassian-spring-scanner` on `6.0.3-SNAPSHOT` (between 6.0.2 and unreleased 6.0.3). The pins target released artifacts: `2.0.0-M2` and `6.0.2`. Snapshot trees are generally a superset of the release line they branched from, so claims that hold on SNAPSHOT are highly likely to hold on M2/6.0.2 — but "highly likely" is not "verified."

The deps commit (commit 1) must include a five-minute verification step before merging:

1. `mvn dependency:get -DgroupId=io.modelcontextprotocol.sdk -DartifactId=mcp-core -Dversion=2.0.0-M2`
2. Extract the jar; confirm `HttpServletStreamableServerTransportProvider.class` shows a private constructor (`javap -p`), confirm `McpTransportContextExtractor.class` shows a single `extract` method, confirm `McpSchema$Resource$Builder.class` shows a `meta(Map)` method.
3. Same for `mcp-json-jackson2:2.0.0-M2` — confirm the OSGi `Service-Component` entries in the jar's MANIFEST.MF.
4. Same for `atlassian-spring-scanner-runtime:6.0.2` — confirm jakarta imports in its MANIFEST.MF.

If any verification fails, escalate (bump pin to M3 if released, or fall back to `2.0.0-SNAPSHOT` via Sonatype OSS snapshots repo, or extend the design discussion). Record the verification output in the commit message.

## Class-by-class disposition

**D**eleted, **M**odified, **W**rapped, **U**ntouched (jakarta import sweep only), or **N**ew.

### Transport layer

| File | Disp. | Notes |
|---|---|---|
| `rest/McpResource.java` | D | JAX-RS endpoint — replaced by SDK transport servlet |
| `JsonRpcHandler.java` | D | Hand-rolled JSON-RPC dispatch — SDK owns this |
| `McpBootstrap.java` | N | `@Named` component. Builds `McpSyncServer` + the SDK `HttpServletStreamableServerTransportProvider` instance (configured with an explicitly-constructed `McpJsonMapper` from Jackson — see Dependencies). Exposes the configured `HttpServlet` for `McpPluginLifecycle` to register. |
| `McpPluginLifecycle.java` | N | `@Named` `LifecycleAware`. On `onStart`, registers the SDK transport instance directly with Atlassian's `ServletModuleManager` at `/plugins/servlet/mcp`. On `onStop`, unregisters. **No `JiraMcpServlet` wrapper class exists** — the SDK provider IS the servlet. |
| `McpToolAdapter.java` | N | Static `adapt(McpTool) → SyncToolSpecification`. Preserves `_meta.ui.resourceUri` and `structuredContent` for UI-linked tools (see The bridge layer). |
| `JiraAuthContextExtractor.java` | N | Implements `McpTransportContextExtractor`. Extracts `Authorization` header into the transport context for tool handlers. Does **not** enforce session-user binding (that lives in `SessionBindingFilter`). |

### Filters (extracted from `McpResource` into reusable `jakarta.servlet.Filter`s)

| File | Disp. | Notes |
|---|---|---|
| `rest/OAuthAnonymousFilter.java` | M | jakarta sweep, URL pattern unchanged |
| `rest/OriginValidationFilter.java` | N | Origin allowlist (Jira base URL + claude.ai + claude.com + localhost) |
| `rest/BodySizeLimitFilter.java` | N | 1 MB for MCP POST, 64 KB DCR register, 8 KB token exchange |
| `rest/RateLimitFilter.java` | N | Delegates to existing `RateLimiter` util |
| `rest/SessionBindingFilter.java` | N | **Security boundary.** Captures the SDK-issued session ID on initialize, binds it to the authenticated Jira user, and rejects cross-user replay with 403 before SDK dispatch. See dedicated Session-user binding section. Order: AFTER auth resolution, BEFORE the SDK transport. |
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
| `ResourceRegistry.java` | M | Emits `SyncResourceSpecification`s. Metadata is set via `McpSchema.Resource.builder(uri, name).meta(metaMap).build()` (verified at `.upstream/java-sdk/mcp-core/src/main/java/io/modelcontextprotocol/spec/McpSchema.java:1429`). The metaMap preserves today's exact key shape: nested `ui` object for Claude (`{ui: {resourceUri, …}}`), plus the **flat OpenAI keys** as they exist today — `openai/widgetDescription`, `openai/widgetPrefersBorder`, `openai/widgetCSP`, `openai/widgetDomain` (per `src/main/java/com/atlassian/mcp/plugin/ResourceRegistry.java:155-162`). No subclassing — `SyncResourceSpecification` and `Resource` are both records and final |

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
| MCP App resource dual-metadata wire emission | Low | Medium — degrades to text-only widget if unfixed | `Resource.builder().meta(Map<String,Object>)` is confirmed present in the SDK. Acceptance check: `resources/list` response includes both `_meta.ui` and `_meta["openai/widget"]` keys for the issue-card resource. |
| E2E test JSON-shape assertions | High | Low | Most tests check tool *content*, not transport envelope. Expect 5–10 tests to need updates. Fix as discovered. |
| Session-user binding implementation pitfalls | Medium | High *(security)* | See dedicated **Session-user binding** section below. The risk is implementing it incorrectly, not whether it's possible. |
| Progress streaming API differences | Low | Low | SDK provides `exchange.progressNotification(...)`. Refactor per tool (~5 lines each). |
| SDK 2.0-M2 milestone churn | Low | Low | Pin exact version. Revisit only on 2.0 GA. |

## Session-user binding (security invariant)

Today's plugin holds a hard guarantee: an `MCP-Session-Id` issued to one Jira user cannot be used by another. Cross-user replay returns 403. The SDK's `HttpServletStreamableServerTransportProvider` creates session IDs internally during `initialize` and keeps them in a private map keyed only by session ID — it does **not** know about Jira users and does not enforce per-user binding.

The `McpTransportContextExtractor` cannot enforce this either. Its interface is `McpTransportContext extract(T request)` (verified at `.upstream/java-sdk/mcp-core/src/main/java/io/modelcontextprotocol/server/McpTransportContextExtractor.java`) — request-only, no response access, no defined contract for mapping thrown exceptions to HTTP status codes. The extractor is the right place to surface the authenticated principal to tool handlers, but it is the wrong place to enforce a security invariant that depends on observing the SDK's response.

**The mechanism therefore lives in a `jakarta.servlet.Filter` that sits between Atlassian's filter chain and the SDK transport servlet.** This filter is `SessionBindingFilter`. It is the security boundary; the extractor stays narrow.

```java
public class SessionBindingFilter implements Filter {
    private static final ConcurrentHashMap<String, SessionBinding> bindings = new ConcurrentHashMap<>();
    // SessionBinding = { username, createdAtMillis }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        HttpServletResponse httpResp = (HttpServletResponse) resp;

        String currentUser = resolveJiraUser(httpReq);              // throws -> 401
        String incomingSid = httpReq.getHeader("MCP-Session-Id");

        boolean isInitialize = looksLikeInitialize(httpReq);        // POST + body method == "initialize"

        if (!isInitialize && incomingSid != null) {
            SessionBinding b = bindings.get(incomingSid);
            if (b == null || expired(b)) { httpResp.sendError(401, "session unknown or expired"); return; }
            if (!b.username.equals(currentUser)) { httpResp.sendError(403, "session bound to a different user"); return; }
        }

        if (isInitialize) {
            // Wrap response to capture the session ID the SDK writes
            CapturingResponse wrapped = new CapturingResponse(httpResp);
            chain.doFilter(req, wrapped);
            String issuedSid = wrapped.capturedHeader("MCP-Session-Id");
            if (issuedSid != null && wrapped.getStatus() < 400) {
                bindings.put(issuedSid, new SessionBinding(currentUser, System.currentTimeMillis()));
                evictIfOverCapAndExpire();                          // cap 200, TTL 4h
            }
            return;
        }

        chain.doFilter(req, resp);
    }
}
```

`looksLikeInitialize` requires reading the POST body to inspect `"method": "initialize"`. To avoid consuming the body before the SDK reads it, the filter uses a `BufferedRequestWrapper` that caches the body bytes and exposes a re-readable `InputStream` to the downstream SDK. This is standard servlet practice (`HttpServletRequestWrapper` + cached body).

`DELETE` removes the entry on success — the filter inspects the response status after `chain.doFilter` and calls `bindings.remove(sid)` if 2xx.

The filter is registered in `atlassian-plugin.xml` as a `<servlet-filter>` with URL pattern `/plugins/servlet/mcp` and location `before-dispatch` so it runs before the SDK transport servlet receives the request.

**Acceptance criteria for this section** (aligned with what the SDK transport actually does):

- `initialize → tools/call` as user A, then re-use the same session ID with user B's PAT → **403** (cross-user replay rejected before SDK dispatch). The existing `session_user_binding` e2e test enforces this.
- Same user A re-uses their own valid `MCP-Session-Id` on a follow-up `tools/call` → **200 OK** (filter does not over-reject the legitimate case).
- Any non-initialize POST without `MCP-Session-Id` → **400/404 from the SDK** (the SDK requires the header; our filter must not consume the body in a way that breaks this) — confirms the filter passes through to the SDK rather than swallowing the request.
- `DELETE` with the correct user's session → 200 + binding removed (subsequent reuse → 401 "expired").

## Verification

`just build-app && just deploy && just e2e` against the live Jira 11 instance referenced by `.credentials/jira.env`. The 54 e2e tests are the safety net. Plus one manual smoke test:

- Open Claude Desktop pointed at the plugin's MCP endpoint, call `get_issue`, confirm the MCP App widget renders the issue card and that subsequent tool calls work.

No `atlas-run` is needed — the user's real Jira 11 IS the test bed. The plugin's current state on that instance is broken (javax classes refused by Tomcat 10.1), so the migration's success criterion is "plugin loads, all 54 e2e tests green, widget renders in Claude Desktop."

## Commit shape

Five atomic commits on `feature/jakarta-jira-11`:

1. **`chore(deps): bump to Jira 11 + Jakarta EE 10 + Java 21 + MCP SDK 2.0.0-M2 + platform BOM 8.1.13`** — pom only. Branch broken to compile, expected.
2. **`feat(transport): replace hand-rolled JSON-RPC with MCP SDK servlet transport`** — delete `McpResource` + `JsonRpcHandler`; add `McpBootstrap`, `McpPluginLifecycle` (programmatic `ServletModuleManager` registration), `McpToolAdapter`, `JiraAuthContextExtractor`, and `SessionBindingFilter`; extract origin/body-limit/rate-limit/security-headers filters; update `atlassian-plugin.xml` (filters yes, transport `<servlet>` no — registered programmatically). Branch compiles with 1–2 tools wired for smoke testing.
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
