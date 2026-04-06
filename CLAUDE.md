# atlassian-mcp-plugin — Claude Context

Native Jira Data Center plugin that embeds an MCP (Model Context Protocol) server directly inside Jira. AI agents connect to `https://bpm.astrateam.net/rest/mcp/1.0/` using Jira PATs. 46 tools ported from the open-source mcp-atlassian v0.21.0 project.

## Status

**Working.** Plugin installs, MCP endpoint responds, tools execute against Jira REST API. Admin config page renders but save has a JS web resource loading issue being fixed.

## Architecture

| Layer | What |
|-------|------|
| MCP endpoint | JAX-RS at `/rest/mcp/1.0/` — stateless JSON-RPC over POST |
| Tools | 46 classes, each calls Jira's own REST API internally via `JiraRestClient` |
| Admin | Servlet at `/plugins/servlet/mcp-admin` + REST at `/rest/mcp-admin/1.0/` |
| Config | `McpPluginConfig` backed by Jira `PluginSettings` (key-value) |
| Auth | Jira PAT passthrough — Jira validates before our code runs |

## Key Identifiers

| What | Value |
|------|-------|
| Plugin key | `com.atlassian.mcp.atlassian-mcp-plugin` (= `${groupId}.${artifactId}`) |
| Maven coordinates | `com.atlassian.mcp:atlassian-mcp-plugin:1.0.0-SNAPSHOT` |
| MCP endpoint | `POST /rest/mcp/1.0/` |
| Admin REST | `GET/PUT /rest/mcp-admin/1.0/` |
| Admin page | `/plugins/servlet/mcp-admin` |
| Target Jira | 10.7.4 (running at `bpm.astrateam.net`) |

## Build

```bash
# Prerequisites: mise installs java 17 + maven 3.9. Atlassian SDK installed via nix.
mise install

# Build
atlas-mvn package -DskipTests

# Output
target/atlassian-mcp-plugin-1.0.0-SNAPSHOT.jar   # Upload to Jira UPM
target/atlassian-mcp-plugin-1.0.0-SNAPSHOT.obr

# Tests
atlas-mvn test

# JAVA_HOME: atlas-* commands need it. mise sets it via hook-env when activated.
# In scripts/CI, prefix with: JAVA_HOME=$(mise where java)
```

## Deploy to Production Jira

1. Build: `atlas-mvn package -DskipTests`
2. Upload JAR via: Jira Admin > Manage Apps > Upload App
3. Enable MCP: `curl -X PUT https://bpm.astrateam.net/rest/mcp-admin/1.0/ -H "Authorization: Bearer <PAT>" -H "Content-Type: application/json" -d '{"enabled":true}'`
4. Test: `curl -X POST https://bpm.astrateam.net/rest/mcp/1.0/ -H "Authorization: Bearer <PAT>" -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'`

## Jira Instance Access

| What | Value |
|------|-------|
| URL | `https://bpm.astrateam.net` |
| Admin user | `rkadmin` |
| Container | `jira_app` on swarm03 (`10.1.130.23`) |
| SSH | `ssh rkadmin@10.1.130.23` (use `sudo docker exec ...`) |
| Logs | `sudo docker exec jira_app.1.<id> tail -200 /var/atlassian/application-data/jira/log/atlassian-jira.log` |
| Find container ID | `sudo docker ps --filter name=jira --format '{{.Names}} {{.ID}}' \| grep -v mcp` |
| UPM upload flag | `-Dupm.plugin.upload.enabled=true` (already set) |
| Signature validation | Disabled (already set) |

## Hard-Won Lessons (DO NOT REPEAT)

### javax, NOT jakarta
Jira 10.7.4 API JARs use `javax.servlet`, `javax.ws.rs`, `javax.inject` in their method signatures. Despite research suggesting Jira 10+ migrated to jakarta, **compilation proves otherwise**. Always use `javax.*` imports.

### Spring Scanner requires scan-indexes XML
`@ComponentImport` annotations do NOTHING without `src/main/resources/META-INF/spring/plugin-context.xml`:
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
    xmlns:atlassian-scanner="http://www.atlassian.com/schema/atlassian-scanner/2" ...>
    <atlassian-scanner:scan-indexes/>
</beans>
```
Without this file, Spring beans can't inject OSGi services and every servlet/resource fails with `NoSuchBeanDefinitionException`.

### Plugin key must match Bundle-SymbolicName
`atlassian-plugin.xml` must use `key="${atlassian.plugin.key}"` (resolves to `com.atlassian.mcp.atlassian-mcp-plugin`). If key doesn't match OSGi `Bundle-SymbolicName`, UPM rejects the plugin.

### Web resource requires full plugin key
In Velocity templates: `$webResourceManager.requireResource("com.atlassian.mcp.atlassian-mcp-plugin:admin-resources")` — NOT the short key.

### DynamicImport-Package is required
Without `<DynamicImport-Package>*</DynamicImport-Package>` in pom.xml instructions, Jira condition classes and other runtime-loaded classes can't be resolved by OSGi.

### No web-item with Jira condition classes
Using `<web-item>` with `<condition class="...UserIsAdminCondition"/>` causes Spring bean resolution errors — the condition gets instantiated in the plugin's Spring context where `PermissionManager` isn't available. Use `<param name="configure.url">` in `<plugin-info>` for admin links instead (SAML SSO / ScriptRunner pattern).

### REST package scan must be specific
`<rest><package>com.atlassian.mcp.plugin</package></rest>` scans ALL sub-packages, causing JAX-RS to try instantiating non-resource classes. Always use a dedicated package: `<package>com.atlassian.mcp.plugin.rest</package>`.

### XSRF for browser REST calls
Browser AJAX to Jira REST endpoints requires `X-Atlassian-Token: no-check` header, otherwise Jira blocks the request silently.

## Project Structure

```
src/main/java/com/atlassian/mcp/plugin/
├── rest/McpResource.java              # JAX-RS MCP endpoint (POST/GET/DELETE)
├── JsonRpcHandler.java                # JSON-RPC dispatch (initialize, tools/list, tools/call)
├── JiraRestClient.java                # HTTP client calling Jira REST API internally
├── McpToolException.java              # Checked exception for tool failures
├── config/McpPluginConfig.java        # PluginSettings-backed configuration
├── admin/
│   ├── AdminServlet.java              # Admin page (Velocity)
│   └── ConfigResource.java            # Admin REST API (GET/PUT config)
└── tools/
    ├── McpTool.java                   # Tool interface
    ├── ToolRegistry.java              # Tool catalog + capability/config filtering
    ├── ToolInitializer.java           # Registers all 46 tools at startup
    ├── issues/                        # 7 tools: search, get_issue, create, update, delete, batch
    ├── comments/                      # 2 tools: add_comment, edit_comment
    ├── transitions/                   # 2 tools: get_transitions, transition_issue
    ├── worklogs/                      # 2 tools: get_worklog, add_worklog
    ├── boards/                        # 4 tools: boards, board issues, sprints, sprint issues
    ├── links/                         # 4 tools: link types, create/remove links
    ├── epics/                         # 1 tool: link_to_epic
    ├── projects/                      # 6 tools: projects, issues, versions, components
    ├── users/                         # 4 tools: profile, watchers, add/remove watcher
    ├── attachments/                   # 2 tools: download, images
    ├── fields/                        # 2 tools: search_fields, get_field_options
    ├── servicedesk/                   # 3 tools: service desk, queues, queue issues (require JSM)
    ├── forms/                         # 3 tools: proforma forms (require Proforma plugin)
    └── metrics/                       # 4 tools: dates, SLA, dev info

src/main/resources/
├── atlassian-plugin.xml               # Plugin descriptor
├── mcp-plugin.properties              # i18n strings
├── META-INF/spring/plugin-context.xml # Spring Scanner scan-indexes (CRITICAL)
├── templates/admin.vm                 # Admin page Velocity template
├── css/admin.css
└── js/admin.js
```

## Upstream References (.upstream/ — gitignored)

| Directory | What | When to consult |
|-----------|------|-----------------|
| `mcp-atlassian/` | Python MCP server for Jira/Confluence (v0.21.0) | Tool definitions, parameter schemas, REST endpoints, response formatting. **Primary reference for all 46 tools.** |
| `java-sdk/` | Official MCP Java SDK (v2.0.0-SNAPSHOT) | Protocol types, JSON-RPC message shapes, error codes. Reference only — no runtime dependency. |
| `myplugin/` | AMPS-generated sample plugin | Basic plugin structure. Outdated — use BigPicture/ScriptRunner patterns instead. |
| `bigpicture/` | BigPicture plugin (extracted) | Real-world plugin patterns: REST modules, servlet filters, web resources. **Check here first for "how does a real plugin do X?"** |
| `scriptrunner/` | ScriptRunner plugin (extracted) | Another reference for plugin patterns. |
| `samlsso/` | SAML SSO plugin (extracted) | Admin page pattern via `configure.url` (no web-item). |
| `atlassian-spring-scanner/` | Spring Scanner source + README | **Authoritative** reference for `@ComponentImport`, `@Named`, scan-indexes, troubleshooting. Read README.md before touching DI. |

### Upstream version tracking

`upstream-versions.json` records which upstream versions our tools are ported from. When upstream updates, diff their tool definitions against ours.

## Docs (docs/)

| File | Content |
|------|---------|
| `01-atlassian-sdk.md` | SDK setup, AMPS versions, Maven repos, atlas-* commands |
| `02-jira-plugin-anatomy.md` | Plugin descriptor, module types, Spring annotations, SAL, Active Objects |
| `03-rest-and-servlet-modules.md` | REST endpoints (JAX-RS), servlets, SSE limitations, filters |
| `04-admin-configuration.md` | Full admin UI example: servlet + Velocity + REST + PluginSettings |
| `05-forge-platform.md` | Forge (Cloud-only — NOT for DC, reference only) |
| `06-mcp-java-sdk.md` | MCP SDK, transports, tool registration, Jakarta compatibility |
| `07-upstream-mcp-atlassian.md` | All 46 tools listed, REST API endpoints, Cloud vs DC differences |
| `08-architecture-considerations.md` | Design decisions, auth flow, MVP phases, toolchain |
| `09-java-sdk-source-analysis.md` | SDK source: McpSchema types, session management, OSGi risks |
| `rkstack/specs/2026-04-06-jira-mcp-plugin-design.md` | Full design spec (reviewed by Codex) |
| `rkstack/plans/2026-04-06-jira-mcp-plugin.md` | Implementation plan |

**When to use docs:** Consult before making architectural changes. The spec and plan were Codex-reviewed. The research docs (01-09) are background knowledge — useful but may contain outdated assumptions (e.g., the jakarta assumption was wrong).

## MCP Protocol (what we implement)

Stateless JSON-RPC 2.0 over HTTP POST. No SSE, no sessions.

| Method | Action |
|--------|--------|
| `initialize` | Return server info + capabilities |
| `notifications/initialized` | Return 202 |
| `tools/list` | Return filtered tool list |
| `tools/call` | Dispatch to tool, return result |
| `ping` | Keep-alive |

Error shape: protocol errors return JSON-RPC `error` object. Tool execution failures return `CallToolResult` with `isError: true` (still a JSON-RPC success).

## Admin Config (PluginSettings keys)

| Key | Default | Purpose |
|-----|---------|---------|
| `com.atlassian.mcp.plugin.enabled` | false | Global MCP on/off |
| `com.atlassian.mcp.plugin.allowedUsers` | "" | Comma-separated user keys (empty = all) |
| `com.atlassian.mcp.plugin.disabledTools` | "" | Comma-separated tool names |
| `com.atlassian.mcp.plugin.readOnlyMode` | false | Hide write tools |
| `com.atlassian.mcp.plugin.jiraBaseUrl` | "" | Override internal base URL |

## Known Issues / TODO

- **Admin page save:** JS web resource was not loading due to wrong plugin key in Velocity template (fixed). XSRF header added. Needs re-test after latest deploy.
- **Admin UI UX:** Tool enable/disable is a textarea — should be a visual list with toggles.
- **OAuth:** Upstream mcp-atlassian supports OAuth 2.0 via Application Links for Jira DC. Tested and working with the upstream sidecar. Plan to add OAuth flow to this plugin so users don't need PATs.
- **Capability detection:** Board/sprint tools registered even if Jira Software not installed (will fail at call time — not hidden from tools/list yet).
- **No sidebar admin link:** Removed due to OSGi condition class issues. Access admin via Manage Apps > Configure.

## Toolchain

- **System packages:** nix-darwin (RK manages). Includes Atlassian Plugin SDK 9.9.1.
- **Per-project tools:** `.mise.toml` — Java temurin-17, Maven 3.9, just.
- **Task runner:** `justfile` — but currently `atlas-mvn` commands are used directly.
- **Never:** suggest `brew install` or global installs. Use mise or nix.

## Critical Rules

- **Always use `javax.*`** imports, never `jakarta.*`
- **Always rebuild after changes:** `atlas-mvn package -DskipTests`
- **Plugin key is `com.atlassian.mcp.atlassian-mcp-plugin`** everywhere — templates, web resources, references
- **Check .upstream/ plugins** (BigPicture, SAML SSO, ScriptRunner) before guessing how Jira plugins work
- **Read `.upstream/atlassian-spring-scanner/README.md`** before touching dependency injection
- **Test on production Jira** by uploading JAR via UPM — `atlas-run` starts a separate local Jira
- **Logs:** SSH to swarm03, `sudo docker exec` into `jira_app` container, read `atlassian-jira.log`
