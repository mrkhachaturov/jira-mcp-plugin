# atlassian-mcp-plugin

Native Jira Data Center plugin that embeds an MCP (Model Context Protocol) server. AI agents connect via OAuth 2.0 or PATs. 46 tools for issues, projects, boards, sprints, comments, worklogs, links, fields, and more.

## Architecture

| Layer | What |
|-------|------|
| MCP endpoint | JAX-RS at `/rest/mcp/1.0/` — stateless JSON-RPC 2.0 over POST |
| OAuth proxy | Servlet at `/plugins/servlet/mcp-oauth/` — bridges MCP client OAuth with Jira OAuth 2.0 |
| Tools | 46 classes in `tools/` — each calls Jira REST API internally via `JiraRestClient` |
| Admin | Servlet at `/plugins/servlet/mcp-admin` + REST at `/rest/mcp-admin/1.0/` |
| Config | `McpPluginConfig` backed by Jira `PluginSettings` (key-value) |
| Auth | OAuth 2.0 (via Application Link) or PAT — Jira validates tokens, plugin checks access control |

## Key Identifiers

| What | Value |
|------|-------|
| Plugin key | `com.atlassian.mcp.atlassian-mcp-plugin` |
| Maven coordinates | `com.atlassian.mcp:atlassian-mcp-plugin` |
| MCP endpoint | `POST /rest/mcp/1.0/` |
| OAuth endpoints | `/plugins/servlet/mcp-oauth/{metadata,register,authorize,callback,token}` |
| Admin REST | `GET/PUT /rest/mcp-admin/1.0/` |
| Admin page | `/plugins/servlet/mcp-admin` |
| Target Jira | Data Center 10.x |

## Build

```bash
# Java 17 required
mvn package -DskipTests

# Output: target/atlassian-mcp-plugin-<version>.jar
# Upload via Jira Admin > Manage Apps > Upload App
```

## MCP Protocol

Stateless JSON-RPC 2.0 over HTTP POST. No SSE, no sessions.

| Method | Action |
|--------|--------|
| `initialize` | Return server info + capabilities |
| `notifications/initialized` | Return 202 |
| `tools/list` | Return filtered tool list |
| `tools/call` | Dispatch to tool, return result |
| `ping` | Keep-alive |

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

## Project Structure

```
src/main/java/com/atlassian/mcp/plugin/
├── rest/
│   ├── McpResource.java              # JAX-RS MCP endpoint (POST/GET/DELETE)
│   ├── OAuthServlet.java             # OAuth proxy servlet (all OAuth endpoints)
│   └── OAuthAnonymousFilter.java     # before-login filter for anonymous OAuth access
├── JsonRpcHandler.java                # JSON-RPC dispatch
├── JiraRestClient.java                # HTTP client calling Jira REST API
├── McpToolException.java              # Checked exception for tool failures
├── config/
│   ├── McpPluginConfig.java           # PluginSettings-backed configuration
│   └── OAuthStateStore.java           # In-memory OAuth state (pending auths, proxy codes, PKCE)
├── admin/
│   ├── AdminServlet.java              # Admin page (Velocity)
│   └── ConfigResource.java           # Admin REST API (GET/PUT config)
└── tools/
    ├── McpTool.java                   # Tool interface
    ├── ToolRegistry.java              # Tool catalog + registration + filtering
    ├── issues/                        # 7 tools
    ├── comments/                      # 2 tools
    ├── transitions/                   # 2 tools
    ├── worklogs/                      # 2 tools
    ├── boards/                        # 4 tools (require Jira Software)
    ├── links/                         # 4 tools
    ├── epics/                         # 1 tool
    ├── projects/                      # 6 tools
    ├── users/                         # 4 tools
    ├── attachments/                   # 2 tools
    ├── fields/                        # 2 tools
    ├── servicedesk/                   # 3 tools (require JSM)
    ├── forms/                         # 3 tools (require Proforma)
    └── metrics/                       # 4 tools
```

## Hard-Won Lessons

### javax, NOT jakarta
Jira 10.x API JARs use `javax.servlet`, `javax.ws.rs`, `javax.inject`. Always use `javax.*` imports.

### Spring Scanner requires scan-indexes XML
`@ComponentImport` requires `src/main/resources/META-INF/spring/plugin-context.xml` with `<atlassian-scanner:scan-indexes/>`.

### Plugin key must match Bundle-SymbolicName
`atlassian-plugin.xml` key must be `${atlassian.plugin.key}` = `com.atlassian.mcp.atlassian-mcp-plugin`.

### DynamicImport-Package is required
Without `<DynamicImport-Package>*</DynamicImport-Package>` in pom.xml, runtime class resolution fails.

### Anonymous REST access in Jira 10
Use `@UnrestrictedAccess` from `com.atlassian.annotations.security` (NOT the old `@AnonymousAllowed` from `com.atlassian.plugins.rest.common.security` which isn't OSGi-exported). Combined with a `before-login` servlet filter for full anonymous access.

### REST package scan must be specific
Use `<package>com.atlassian.mcp.plugin.rest</package>` — never the parent package.

### Version bumps bust JS/CSS cache
Jira CDN caches web resources by plugin version. Bump version in pom.xml to force browsers to load new JS/CSS.

### Plugin enable timeout
Jira's internal `jira-migration` plugin can cause timeout during enable. Disable it temporarily, or click "Enable" manually in UPM.

## Critical Rules

- **Always use `javax.*`** imports, never `jakarta.*`
- **Plugin key is `com.atlassian.mcp.atlassian-mcp-plugin`** everywhere
- **Rebuild after changes:** `mvn package -DskipTests`
- **Bump version** when changing JS/CSS (cache busting)
