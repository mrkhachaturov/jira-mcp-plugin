# Changelog

## [1.0.2] - 2026-04-06

### Added

- **OAuth 2.0 proxy** — users authenticate via browser consent instead of PATs
- RFC 9728 protected resource metadata + RFC 8414 authorization server metadata
- OAuth endpoints: register (DCR), authorize, callback, token (with PKCE verification)
- OAuth tab in admin UI with client_id/secret config and user-facing MCP config snippet
- 401 response with `resource_metadata` link when no auth token provided
- PKCE (S256) support — proxy codes bound to client_id, redirect_uri, and code_challenge

## [1.0.1] - 2026-04-06

### Added
- **Group-based access control** — assign Jira groups to control who can use MCP
- **Tabbed admin UI** — General, Access Control, and Tools tabs
- **Group picker** — search and select Jira groups with tag-based UI
- **User picker** — search and select users with autocomplete dropdown and tags

### Fixed
- **Tools not showing in admin UI** — merged ToolInitializer into ToolRegistry to guarantee tool registration during Spring bean initialization
- **User picker not rendering** — replaced broken AJS.MultiSelect with custom search+tags picker using Jira REST API
- **Toggle state not persisting** — was browser cache serving stale JS; fixed by changing web resource key for cache invalidation
- **MCP access check** — now supports both usernames and user keys in allowlist

### Changed
- Web resource key renamed from `admin-resources` to `mcp-admin-ui` (cache busting)
- Removed `jira.webresources:jira-fields` and `jira.webresources:autocomplete` dependencies (not needed)
- Access check logic: user-level overrides group-level; empty lists = allow all

## [1.0.0] - 2026-04-05

### Added
- Initial release: 46 MCP tools ported from mcp-atlassian v0.21.0
- JSON-RPC 2.0 over HTTP POST at `/rest/mcp/1.0/`
- Admin UI at `/plugins/servlet/mcp-admin` with sidebar link
- Per-tool enable/disable with click-to-toggle list
- Read-only mode (hides write tools)
- User allowlist for MCP access
- PAT-based authentication passthrough
