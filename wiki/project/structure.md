# Project Structure

```text
src/main/java/com/atlassian/mcp/plugin/
├── rest/
│   ├── McpBootstrap.java             # Builds McpSyncServer (tools + resources) on plugin start
│   ├── McpTransportFilter.java       # servlet-filter at /plugins/servlet/mcp — bridges to SDK HttpServletStreamableServerTransportProvider, owns URL and never calls chain.doFilter()
│   ├── JiraAuthContextExtractor.java # Surfaces calling Jira user (username, displayName, accountId) into McpTransportContext for tool execution
│   ├── McpToolAdapter.java           # Adapts McpTool → SyncToolSpecification
│   ├── AccessControlFilter.java      # Allowlist gate (allowed users / groups, plugin-enabled flag)
│   ├── SessionBindingFilter.java     # Enforces MCP-Session-Id ↔ Jira user binding (anti-replay)
│   ├── OriginValidationFilter.java   # Origin allowlist (MCP spec MUST)
│   ├── BodySizeLimitFilter.java      # 1 MB cap on /plugins/servlet/mcp POSTs
│   ├── RateLimitFilter.java          # 120/min per user on MCP, IP-based on OAuth endpoints
│   ├── SecurityHeadersFilter.java    # nosniff, no-store, X-Frame-Options DENY
│   ├── BufferedRequestWrapper.java   # Captures POST body for size-limit + downstream re-read
│   ├── CapturingResponseWrapper.java # Captures SDK response for security-header injection
│   ├── OAuthServlet.java             # OAuth proxy servlet
│   ├── OAuthAnonymousFilter.java     # before-login filter for anonymous OAuth access
│   └── RateLimiter.java              # IP-based rate limiter for anonymous + authenticated endpoints
├── ResourceRegistry.java              # MCP Apps ui:// resource registry → SyncResourceSpecification
├── ResourceContextBuilder.java        # Builds dual metadata (Claude _meta.ui + ChatGPT openai/widget*)
├── JiraRestClient.java                # HTTP client → Jira REST API (+ ResponseTrimmer)
├── JiraMarkupConverter.java           # Bidirectional Markdown ↔ Jira wiki markup
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
    ├── ToolRegistry.java              # every tool registered, filtered by capability/config
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

mcp-app/                               # React widget project (MCP Apps)
├── src/issue-card/                    # Issue Card widget source
│   ├── app.tsx                        # Root component (useApp, refreshIssue)
│   ├── i18n.ts                        # Localization (en/ru)
│   ├── types.ts                       # TypeScript types for structuredContent
│   ├── icons/                         # Issue type SVGs + registry
│   │   └── priorities/                # Priority SVGs + registry
│   └── components/                    # UI components
├── vite.config.ts                     # Vite + viteSingleFile bundler
└── dist/                              # Build output (gitignored)

.credentials/                          # gitignored — PATs, OAuth config, deploy workflow
```
