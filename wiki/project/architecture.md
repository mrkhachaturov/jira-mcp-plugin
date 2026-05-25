# Architecture

| Layer | What |
|-------|------|
| MCP endpoint | Servlet at `/plugins/servlet/mcp` — SDK transport (`io.modelcontextprotocol.sdk:mcp-core` 2.0.0-M2), registered programmatically and bridged through an async-supported `servlet-filter` |
| OAuth proxy | Servlet at `/plugins/servlet/mcp-oauth/` — bridges MCP client OAuth with Jira OAuth 2.0. Supports `authorization_code` + `refresh_token` grants. Tokens passed through from Jira (stateless — Jira's DB manages lifecycle) |
| Tools | 49 classes in `tools/` — each calls Jira REST API internally via `JiraRestClient` |
| Response trimmer | `ResponseTrimmer` — strips verbose fields (`avatarUrls`, `iconUrl`, `groups`, `applicationRoles`) to reduce payload size for AI agents |
| Admin | Servlet at `/plugins/servlet/mcp-admin` + REST at `/rest/mcp-admin/1.0/` |
| Config | `McpPluginConfig` backed by Jira `PluginSettings` (key-value) |
| Auth | OAuth 2.0 (via Application Link) or PAT — Jira validates tokens, plugin checks access control |
| MCP Apps | Interactive UI widget rendered in Claude Desktop, ChatGPT, VS Code Copilot. React app bundled as single HTML, served via `resources/read`. 5 tools linked via `_meta.ui.resourceUri` |
| Resources | `ResourceRegistry` — serves `ui://jira/issue-card@{hash}` HTML from classpath. Dual metadata for Claude (`_meta.ui`) and ChatGPT (`openai/widget*`) |
