# MCP Java SDK Source Analysis

Source: `.upstream/java-sdk/` (version 2.0.0-SNAPSHOT)

## Project Structure

```
java-sdk/
├── pom.xml                 # Parent POM (Java 17, jakarta.servlet 6.1.0)
├── mcp-bom/                # Bill of Materials
├── mcp/                    # Aggregate module (core + Jackson 3)
├── mcp-core/               # Core: transport, schema, server/client
│   └── src/main/java/io/modelcontextprotocol/
│       ├── server/
│       │   ├── McpServer.java                    # Server builder
│       │   ├── McpSyncServer.java                # Sync server
│       │   ├── McpAsyncServer.java               # Async server
│       │   ├── McpServerFeatures.java            # Tool/Resource specs
│       │   └── transport/
│       │       ├── HttpServletStreamableServerTransportProvider.java
│       │       └── HttpServletSseServerTransportProvider.java
│       ├── spec/
│       │   ├── McpSchema.java                    # All protocol types
│       │   ├── McpServerSession.java             # Session management
│       │   └── McpStreamableServerSession.java   # Streamable session
│       └── json/
│           └── McpJsonMapper.java                # JSON abstraction
├── mcp-json-jackson2/      # Jackson 2.x mapper
├── mcp-json-jackson3/      # Jackson 3.x mapper
├── mcp-test/               # Test utilities
└── conformance-tests/      # Official MCP conformance suite
```

## Key Requirements

| Requirement | Version |
|---|---|
| Java | 17+ |
| Jakarta Servlet | 6.1.0 (provided scope) |
| Reactor Core | Used internally for async |
| Jackson | 2.x or 3.x (via mapper modules) |

## Confirmed: Jakarta Servlet

All transport classes use Jakarta imports:

```java
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

Dependency in `mcp-core/pom.xml`:
```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.1.0</version>
    <scope>provided</scope>
</dependency>
```

## HttpServletStreamableServerTransportProvider

The main class for HTTP-based MCP servers. **It IS a Servlet** (`extends HttpServlet`).

### Builder

```java
HttpServletStreamableServerTransportProvider.builder()
    .jsonMapper(mapper)              // McpJsonMapper (required)
    .mcpEndpoint("/mcp")             // Endpoint path (required)
    .contextExtractor(extractor)     // Optional: extract context from request
    .keepAliveInterval(Duration)     // Optional: SSE keep-alive
    .securityValidator(validator)    // Optional: auth hook
    .build()
```

### HTTP Methods

| Method | Purpose |
|---|---|
| `doPost()` | Handle JSON-RPC requests from client |
| `doGet()` | Establish SSE stream for server-push |
| `doDelete()` | Terminate MCP session |

### Session Management

- Sessions stored in `ConcurrentHashMap<String, McpStreamableServerSession>`
- Session ID assigned during `initialize` handshake
- Clients include `MCP-Session-Id` header on all subsequent requests
- Sessions track pending responses, state (UNINITIALIZED -> INITIALIZING -> INITIALIZED)

### Security Validator Hook

The `.securityValidator()` builder parameter allows custom authentication:

```java
.securityValidator(request -> {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new SecurityException("Missing auth token");
    }
    // Validate token...
})
```

This is where we'd integrate Jira's PAT validation.

## McpSchema — Protocol Types

All protocol types live in `McpSchema.java` as Java records:

### JSON-RPC Messages

```java
sealed interface JSONRPCMessage permits JSONRPCRequest, JSONRPCNotification, JSONRPCResponse

record JSONRPCRequest(String jsonrpc, String method, Object id, Object params)
record JSONRPCNotification(String jsonrpc, String method, Object params)
record JSONRPCResponse(String jsonrpc, Object id, Object result, JSONRPCError error)
```

### Method Constants

```java
METHOD_INITIALIZE = "initialize"
METHOD_TOOLS_LIST = "tools/list"
METHOD_TOOLS_CALL = "tools/call"
METHOD_RESOURCES_LIST = "resources/list"
METHOD_PROMPT_LIST = "prompts/list"
// ... more
```

### Error Codes

```java
PARSE_ERROR = -32700
INVALID_REQUEST = -32600
METHOD_NOT_FOUND = -32601
INVALID_PARAMS = -32602
INTERNAL_ERROR = -32603
RESOURCE_NOT_FOUND = -32002
```

### Tool Record

```java
public record Tool(
    String name,
    String title,
    String description,
    JsonSchema inputSchema,
    Map<String, Object> outputSchema,
    ToolAnnotations annotations,
    Map<String, Object> meta
)
```

Builder:
```java
Tool.builder()
    .name("search")
    .title("Search Issues")
    .description("Search Jira issues with JQL")
    .inputSchema(JsonSchema.builder()
        .type("object")
        .properties(Map.of(
            "jql", Map.of("type", "string", "description", "JQL query")
        ))
        .required(List.of("jql"))
        .build())
    .build()
```

### CallToolResult Record

```java
public record CallToolResult(
    List<Content> content,
    Boolean isError,
    Object structuredContent,
    Map<String, Object> meta
) implements Result
```

Builder:
```java
CallToolResult.builder()
    .content(List.of(new TextContent("Results: ...")))
    .isError(false)
    .build()
```

### Content Types

```java
record TextContent(String text)                           // Plain text
record ImageContent(String data, String mimeType)         // Base64 image
record EmbeddedResource(ResourceContents resource)        // Embedded resource
record AudioContent(String data, String mimeType)         // Audio
```

## Tool Registration Patterns

### Sync Tool (with exchange)

```java
McpServer.sync(transport)
    .serverInfo("jira-mcp", "1.0.0")
    .tool(
        Tool.builder()
            .name("search")
            .description("Search issues with JQL")
            .inputSchema(schema)
            .build(),
        (exchange, request) -> {
            String jql = (String) request.arguments().get("jql");
            // ... execute search ...
            return CallToolResult.builder()
                .content(List.of(new TextContent("Found 5 issues")))
                .isError(false)
                .build();
        }
    )
    .build();
```

### Async Tool Specification

```java
McpServerFeatures.AsyncToolSpecification.builder()
    .tool(toolDef)
    .callHandler((exchange, request) ->
        Mono.fromCallable(() -> {
            // ... do work ...
            return CallToolResult.builder()
                .content(List.of(new TextContent("Done")))
                .build();
        })
    )
    .build()
```

### Exchange Object

The `exchange` parameter provides:
- `getClientCapabilities()` - What the client supports
- `getClientInfo()` - Client name/version
- `loggingNotification()` - Send log messages to client
- `createMessage()` - Request LLM sampling from client

## Embedding in Jira DC Plugin (Approach)

Since Jira 10.7.4 uses Jakarta Servlet and Java 17, we can potentially:

```java
// In a plugin lifecycle component or ServletContextListener
HttpServletStreamableServerTransportProvider transport =
    HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(jsonMapper)
        .mcpEndpoint("/mcp")
        .securityValidator(request -> {
            // Leverage Jira's auth - the request already has
            // PAT or session auth validated by Jira
            UserProfile user = userManager.getRemoteUser(request);
            if (user == null) {
                throw new SecurityException("Authentication required");
            }
            // Check MCP access permission
            if (!mcpConfig.isUserAllowed(user)) {
                throw new SecurityException("MCP access denied");
            }
        })
        .build();

McpSyncServer server = McpServer.sync(transport)
    .serverInfo("jira-mcp", "1.0.0")
    .capabilities(ServerCapabilities.builder()
        .tools(true)
        .build())
    .tool(searchTool, searchHandler)
    .tool(getIssueTool, getIssueHandler)
    // ... register all tools
    .build();
```

### OSGi Considerations

The main risk is **dependency conflicts** in Jira's OSGi container:
- **Jackson**: Jira bundles its own Jackson. The MCP SDK needs Jackson 2.x or 3.x.
  If versions conflict, use the `mcp-core` module + `mcp-json-jackson2` to match
  Jira's bundled Jackson version.
- **Reactor**: The MCP SDK uses Project Reactor internally. This may not be available
  in Jira's OSGi container. May need to bundle it.
- **SLF4J**: Should be fine — Jira provides SLF4J.

If OSGi conflicts arise, fall back to the **hybrid approach**: use `McpSchema` types
for protocol correctness but implement the servlet transport manually.

## Jackson Version Considerations

The SDK provides two JSON mapper modules:
- `mcp-json-jackson2` — for Jackson 2.x (what Jira likely bundles)
- `mcp-json-jackson3` — for Jackson 3.x

Using `mcp-core` + `mcp-json-jackson2` minimizes dependency conflicts with Jira.

## Key Files for Reference

| File | Purpose |
|---|---|
| `mcp-core/.../McpSchema.java` | All protocol types (Tool, CallToolResult, etc.) |
| `mcp-core/.../McpServer.java` | Server builder API |
| `mcp-core/.../McpServerFeatures.java` | Tool/Resource specification records |
| `mcp-core/.../HttpServletStreamableServerTransportProvider.java` | Servlet transport |
| `mcp-core/.../McpServerSession.java` | Session management |
| `mcp-core/pom.xml` | Core dependencies |
| `pom.xml` | Root POM with version properties |
