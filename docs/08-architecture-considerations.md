# Architecture Considerations

## The Core Idea

Embed an MCP server directly inside a Jira Data Center plugin so that:
- Users don't need to self-host the MCP server
- Admins control who has access and which tools are available
- Users connect their AI agents using their Jira PAT
- The MCP endpoint is available at the same URL as Jira itself

## Key Technical Challenges

### 1. Jakarta Servlet Compatibility (Resolved)

**Good news:** Jira 10+ migrated to `jakarta.*` namespaces — the same as the MCP Java
SDK. The servlet API mismatch concern only applies to Jira 9 and below.

**Options for MCP transport:**

- **Option A:** Use MCP SDK's `HttpServletStreamableServerTransportProvider` directly
  as a Jira servlet. Risk: OSGi classloading/dependency conflicts.
- **Option B:** Implement MCP Streamable HTTP protocol manually on a Jira servlet.
  The protocol is straightforward JSON-RPC over HTTP:
  - POST `/mcp` — Client sends JSON-RPC request, server responds
  - GET `/mcp` — Optional SSE stream for server push
  - Session management via `MCP-Session-Id` header
- **Option C (recommended):** Hybrid — use MCP SDK core types (`Tool`, `CallToolResult`,
  JSON-RPC schema classes) but implement the servlet transport ourselves to avoid
  OSGi dependency conflicts.

**Decision:** Start with Option A (direct SDK usage). Fall back to Option C if OSGi
classloading issues arise.

### 2. Internal Java APIs vs REST API

**Advantage of running inside Jira:** We can use Jira's internal Java APIs instead of
making REST API calls to ourselves.

| Upstream (External) | Plugin (Internal) |
|---|---|
| `GET /rest/api/2/issue/{key}` | `IssueManager.getIssueObject(key)` |
| `POST /rest/api/2/search` | `SearchService.search(jql)` |
| `POST /rest/api/2/issue` | `IssueService.create(user, input)` |
| HTTP Basic Auth / PAT | `JiraAuthenticationContext` |
| JSON parsing/formatting | Direct Java object access |

This means:
- Zero HTTP overhead
- Direct access to the user's permission context
- Can use services not exposed via REST
- Better error handling with typed exceptions

### 3. Authentication Flow

```
MCP Client (Claude, etc.)
    |
    | Authorization: Bearer <Jira PAT>
    |
    v
Jira DC (Traefik reverse proxy)
    |
    v
MCP Servlet/REST endpoint (our plugin)
    |
    | Jira validates PAT automatically
    | Plugin gets authenticated user context
    |
    v
Jira Internal APIs (as the authenticated user)
```

The plugin inherits Jira's authentication. When a request comes in with a PAT in the
`Authorization` header, Jira authenticates it before our code runs. We just call
`userManager.getRemoteUser(request)` to get the current user.

### 4. Admin Configuration

Admin page at `/plugins/servlet/mcp/admin` with:
- **Global enable/disable** - Turn MCP server on/off
- **User allowlist** - Which users can access the MCP endpoint
- **Tool selection** - Which MCP tools are available (per-user or global)
- **Read-only mode** - Disable all write operations

Storage: SAL PluginSettings for simple key-value config, or Active Objects for
more complex per-user tool permissions.

### 5. Plugin Architecture

```
com.example.mcp/
├── McpServlet.java           # Main MCP endpoint (Streamable HTTP)
│   ├── doPost()              # Handle JSON-RPC requests
│   └── doGet()               # SSE stream (optional)
├── McpJsonRpcHandler.java    # JSON-RPC dispatch
│   ├── initialize()          # MCP lifecycle
│   ├── toolsList()           # List available tools
│   └── toolsCall()           # Execute a tool
├── tools/                    # Tool implementations
│   ├── SearchTool.java       # JQL search
│   ├── GetIssueTool.java     # Get issue details
│   ├── CreateIssueTool.java  # Create issue
│   ├── ...                   # 46+ more tools
│   └── ToolRegistry.java     # Tool registration and filtering
├── admin/
│   ├── AdminServlet.java     # Admin config page
│   └── ConfigResource.java   # Admin REST API
└── config/
    └── McpPluginConfig.java  # Configuration management
```

### 6. Tool Implementation Pattern

Each tool maps to Jira's internal Java API:

```java
public class SearchTool implements McpTool {
    private final SearchService searchService;

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() { return "Search Jira issues with JQL"; }

    @Override
    public JsonNode getInputSchema() {
        return schemaBuilder()
            .property("jql", "string", "JQL query string", true)
            .property("maxResults", "integer", "Max results", false)
            .build();
    }

    @Override
    public CallToolResult execute(Map<String, Object> args, User user) {
        String jql = (String) args.get("jql");
        int max = (int) args.getOrDefault("maxResults", 50);

        SearchResults results = searchService.search(user, jql, max);

        return new CallToolResult(
            formatResults(results),
            false
        );
    }
}
```

### 7. MVP Scope

For the first version, focus on:

**Phase 1 - Core Infrastructure:**
- Plugin skeleton (Maven, AMPS, atlassian-plugin.xml)
- MCP Streamable HTTP servlet (JSON-RPC handling)
- Basic auth (PAT validation via Jira)
- `initialize`, `tools/list`, `tools/call` protocol support

**Phase 2 - Essential Tools (read-only):**
- `search` - JQL search
- `get_issue` - Get issue details
- `get_all_projects` - List projects
- `get_transitions` - Get available transitions
- `get_agile_boards` - List boards
- `get_sprints_from_board` - Get sprints

**Phase 3 - Write Tools:**
- `create_issue` - Create issue
- `update_issue` - Update issue
- `add_comment` - Add comment
- `transition_issue` - Change status

**Phase 4 - Admin UI:**
- Admin configuration page
- User allowlist management
- Per-tool enable/disable
- Read-only mode toggle

### 8. Developer Toolchain

System packages are managed via **nix-darwin** (not Homebrew). Per-project tools are
declared in **`.mise.toml`** at the repo root. Tasks run via **`justfile`**.

```toml
# .mise.toml (example for this project)
[tools]
just = "latest"
java = "temurin-17"    # JDK 17 for Jira 10+ / MCP SDK
maven = "3.9"          # Maven for Atlassian Plugin SDK builds
```

The Atlassian Plugin SDK itself (`atlas-*` commands) is a system-level tool — install
via nix. Maven and JDK are per-project via mise.

### 9. Deployment

1. Build: `atlas-package` produces an OBR/JAR
2. Upload to Jira via UPM (Manage Apps > Upload)
3. Configure via admin page
4. Users connect AI agents to: `https://bpm.example.com/plugins/servlet/mcp`

### 9. MCP Client Configuration

Users configure their MCP client (Claude, Cursor, etc.) with:

```json
{
  "mcpServers": {
    "jira": {
      "url": "https://bpm.example.com/plugins/servlet/mcp",
      "transport": "streamable-http",
      "headers": {
        "Authorization": "Bearer <JIRA_PAT>"
      }
    }
  }
}
```

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| OSGi classloading issues | Careful dependency management, test in AMPS |
| Jira API changes between versions | Target 10.7.x specifically, use stable APIs |
| Long-running SSE connections | Implement timeouts, connection limits |
| Plugin memory impact | Stateless tool execution, no caching |
| Security (tool access control) | Admin-controlled allowlist, respect Jira permissions |

## References

- MCP Streamable HTTP spec: https://modelcontextprotocol.io/specification/draft/basic/transports
- Jira Java API: https://docs.atlassian.com/software/jira/docs/api/
- Jira REST API v2: https://docs.atlassian.com/jira-software/REST/latest/
