# jira-mcp-plugin

Native Jira Data Center plugin that embeds an MCP (Model Context Protocol)
server. AI agents connect via OAuth 2.0 or PATs. 49 tools cover issues,
projects, boards, sprints, comments, worklogs, links, fields, attachments,
service desk, forms, and metrics.

For deeper reference (architecture, transport details, full tool table,
response-trimming rules, admin keys, e2e test catalog, source-tree map),
see [wiki/project/](wiki/project/README.md). It's indexed by Miyo, not
auto-loaded — look it up on demand.

## Build & Deploy

All commands via `just`. Env vars auto-loaded by mise from
`.credentials/jira.env`.

```bash
just build            # build widget + atlas-package (compile + JAR)
just build-app        # build MCP App widget only (React → single HTML)
just deploy           # clean + build + upload JAR to Jira UPM + verify enabled
just test             # unit tests (excludes e2e)
just e2e              # 14 e2e tests against live Jira instance
just deploy-and-test  # build + deploy + e2e in one shot
just dev-app          # widget dev server (Vite hot reload)
just clean            # atlas-clean
```

**Local builds must use `atlas-mvn`** (not plain `mvn`). The Atlassian SDK
wrapper includes the Atlassian Maven repository. Plain `mvn` only works
when Atlassian repos are configured in `~/.m2/settings.xml` (which CI does
via GitHub Actions).

## Key Identifiers

| What | Value |
|------|-------|
| Plugin key | `com.atlassian.mcp.jira-mcp-plugin` |
| Maven coordinates | `com.atlassian.mcp:jira-mcp-plugin` |
| MCP endpoint | `POST /plugins/servlet/mcp` |
| OAuth endpoints | `/plugins/servlet/mcp-oauth/{metadata,register,authorize,callback,token}` |
| Admin REST | `GET/PUT /rest/mcp-admin/1.0/` |
| Admin page | `/plugins/servlet/mcp-admin` |
| Target Jira | Data Center 11.x |

## Hard-Won Lessons

### jakarta, NOT javax
Jira 11.x runs on Tomcat 10.1 + Spring 6.2.15 + Jakarta EE 10. API JARs are
published under `jakarta.servlet`, `jakarta.ws.rs`, `jakarta.inject`,
`jakarta.annotation`. Always use `jakarta.*` imports — never `javax.*`. The
platform BOM at `com.atlassian.platform.dependencies:platform-public-api:8.1.13`
pins all jakarta + Spring + Jackson + Atlassian REST v2 + SAL versions transitively.

### Spring Scanner requires scan-indexes XML
`@ComponentImport` requires `src/main/resources/META-INF/spring/plugin-context.xml`
with `<atlassian-scanner:scan-indexes/>`.

### Plugin key must match Bundle-SymbolicName
`atlassian-plugin.xml` key must be `${atlassian.plugin.key}` =
`com.atlassian.mcp.jira-mcp-plugin`.

### DynamicImport-Package is required
Without `<DynamicImport-Package>*</DynamicImport-Package>` in pom.xml,
runtime class resolution fails.

### Anonymous REST access in Jira 11
Use `@UnrestrictedAccess` from `com.atlassian.annotations.security` (NOT the
old `@AnonymousAllowed`). Combined with a `before-login` servlet filter for
full anonymous access. Still present and required in Jira 11 /
atlassian-annotations.

### REST package scan must be specific
Use `<package>com.atlassian.mcp.plugin.rest</package>` — never the parent
package. (Verify post-merge — Spring 6 / atlassian-spring-scanner 6.0.2 may
have relaxed this; left in place defensively.)

### Version bumps bust JS/CSS cache
Jira CDN caches web resources by plugin version. Bump version in pom.xml to
force browsers to load new JS/CSS.

### Plugin enable timeout
Jira's internal `jira-migration` plugin can cause timeout during enable.
Disable it temporarily, or click "Enable" manually in UPM.

### Write tools must structure Jira payloads correctly
POST/PUT tool bodies often need nested structures like
`{"fields": {"project": {"key": "..."}, "issuetype": {"name": "..."}}}` —
verify against Jira REST API docs before flattening.

### `atlassian.plugins.filter.async.default=true` JVM flag is required
The official MCP Java SDK's Streamable HTTP transport calls `req.startAsync()`
for every non-`initialize` request (it streams the JSON-RPC response back as
a single-event SSE envelope). Atlassian's plugin framework wraps every plugin
filter in a chain whose default `isAsyncSupported` is `false`, so without
this flag the transport throws `IllegalStateException: A filter or servlet
of the current chain does not support asynchronous operations`. Set
`-Datlassian.plugins.filter.async.default=true` on the Jira JVM (via
`setenv.sh`, `JVM_SUPPORT_RECOMMENDED_ARGS`, or container env). Deployment requirement.

### `<servlet>` modules cannot be async-supported in Atlassian plugin framework
`com.atlassian.plugin.servlet.descriptors.BaseServletModuleDescriptor.init()`
hardcodes the filter wrapper's `isAsyncSupported` to `false` for `<servlet>`
modules — there is no XML knob, and `<async-supported>` in
`atlassian-plugin.xml` is silently ignored by the parser. The MCP transport
must be registered as a `<servlet-filter>` that owns its URL pattern and
short-circuits the chain (never calls `chain.doFilter()`); the JVM flag
above is the only effective control for the surrounding wrapper.

## Critical Rules

- **Always use `jakarta.*`** imports, never `javax.*`.
- **Plugin key is `com.atlassian.mcp.jira-mcp-plugin`** everywhere.
- **Use `atlas-mvn`** for local builds, never plain `mvn`.
- **Use `just`** for all workflows — build, deploy, test.
- **Bump version** in pom.xml when changing JS/CSS (cache busting).
- **Run `just e2e`** after any tool changes to verify against live Jira.
