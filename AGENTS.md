# jira-mcp-plugin

Native Jira Data Center plugin that embeds an MCP (Model Context Protocol)
server. AI agents connect via OAuth 2.0 or PATs. Tools cover issues, projects,
boards, sprints, comments, worklogs, links, fields, attachments, service desk,
forms, and metrics.

For deeper reference (architecture, transport details, full tool table,
response-trimming rules, admin keys, e2e test catalog, source-tree map),
see [wiki/project/](wiki/project/README.md). It's indexed by Miyo, not
auto-loaded — look it up on demand.

## Build & Deploy

All commands are mise tasks, defined as scripts under `.mise/tasks/`. Env vars
auto-loaded by mise from `.credentials/jira.env`. Run `mise tasks` to list them.

```bash
mise run build      # widget + atlas-package (compile + JAR)
mise run build:app  # MCP App widget only (React → single HTML)
mise run deploy      # clean + build + upload JAR to Jira UPM + verify enabled
mise run test        # unit tests (excludes e2e)
mise run test:e2e    # e2e tests against the live Jira instance
mise run lint        # every linter, via flint
mise run lint:fix    # apply what the linters can fix
mise run dev:app     # widget dev server (Vite hot reload)
mise run clean       # atlas-clean
```

**Local builds must use `atlas-mvn`** (not plain `mvn`). The Atlassian SDK
wrapper includes the Atlassian Maven repository. Plain `mvn` only works
when Atlassian repos are configured in `~/.m2/settings.xml` (which CI does
via GitHub Actions).

## Key Identifiers

| What              | Value                                                                     |
| ----------------- | ------------------------------------------------------------------------- |
| Plugin key        | `com.atlassian.mcp.jira-mcp-plugin`                                       |
| Maven coordinates | `com.atlassian.mcp:jira-mcp-plugin`                                       |
| MCP endpoint      | `POST /plugins/servlet/mcp`                                               |
| OAuth endpoints   | `/plugins/servlet/mcp-oauth/{metadata,register,authorize,callback,token}` |
| Admin REST        | `GET/PUT /rest/mcp-admin/1.0/`                                            |
| Admin page        | `/plugins/servlet/mcp-admin`                                              |
| Target Jira       | Data Center — exact version in `jira.version` (pom.xml)                   |

## Hard-Won Lessons

### jakarta, NOT javax

Jira 11 runs on Tomcat 10 + Spring 6 + Jakarta EE 10. API JARs are published
under `jakarta.servlet`, `jakarta.ws.rs`, `jakarta.inject`, `jakarta.annotation`.
Always use `jakarta.*` imports — never `javax.*`. Jakarta, Spring, Jackson,
Atlassian REST and SAL versions all arrive transitively from the platform BOM,
which `jira.version` selects; never pin them by hand.

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
package. (Newer Spring Scanner releases may have relaxed this; left in place
defensively.)

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

## Writing a tool

A tool extends `TypedTool<A>` and declares its parameters as a record whose
components carry `@ToolArg`. The advertised JSON Schema is derived from that
record and the bound record is what `run(A args, McpContext context)` receives;
`inputSchema()` and every `execute` overload are final.

- **Declare the type the parameter is.** A list of values is `List<String>`, a
  structured object is `Map<String, Object>`, a repeated structured object is
  `List<SomeRecord>`. `String` is for text and for expressions Jira parses.
- **Never write a wire name as a string literal** — `projectKey` is advertised
  as `project_key`.
- **Take auth and progress from `McpContext`**, never as a parameter.
- **Converting an existing tool starts from what the tool is for**, not from the
  parameters it happens to have. The questions to answer first, and the full
  contract, are in [wiki/project/tool-authoring.md](wiki/project/tool-authoring.md).

## Critical Rules

- **Always use `jakarta.*`** imports, never `javax.*`.
- **Plugin key is `com.atlassian.mcp.jira-mcp-plugin`** everywhere.
- **Use `atlas-mvn`** for local builds, never plain `mvn`.
- **Use `mise run`** for all workflows — build, deploy, test, lint.
- **Bump version** in pom.xml when changing JS/CSS (cache busting).
- **Run `mise run test:e2e`** after any tool changes to verify against live Jira.

## Linting

Run `mise run lint:fix` before committing changes.
If output includes `fixed`, keep those changes.
If output includes `partial` or `review`, address the remaining issues and
run `mise run lint:fix` again.

Example output:
flint: fixed: gofmt — commit before pushing | partial: cargo-clippy
