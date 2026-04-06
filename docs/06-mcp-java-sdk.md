# MCP Java SDK

## Overview

The official MCP Java SDK (`io.modelcontextprotocol.sdk`) provides a production-ready
implementation of the Model Context Protocol for Java applications. Maintained in
collaboration with Spring AI.

- GitHub: https://github.com/modelcontextprotocol/java-sdk
- Maven Central: `io.modelcontextprotocol.sdk:mcp`
- Minimum Java: 17
- Conformance: 40/40 tests passed (100%), spec version 2025-06-18

## Maven Dependencies

```xml
<!-- BOM for version management -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Full module (core + Jackson 3 JSON) -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
</dependency>

<!-- OR core only (bring your own JSON mapper) -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp-core</artifactId>
</dependency>
```

## SDK Modules

| Module | Description |
|---|---|
| `mcp-bom` | Dependency version management |
| `mcp-core` | Core: STDIO, JDK HttpClient, Servlet transport |
| `mcp-json-jackson2` | JSON via Jackson 2 |
| `mcp-json-jackson3` | JSON via Jackson 3 |
| `mcp` | Convenience bundle (core + Jackson 3) |
| `mcp-spring-webmvc` | Spring WebMVC transport |
| `mcp-test` | Testing utilities |

## Server Types

| Factory Method | Returns | Session |
|---|---|---|
| `McpServer.sync(provider)` | `McpSyncServer` | Stateful |
| `McpServer.async(provider)` | `McpAsyncServer` | Stateful |
| `McpServer.statelessSync(provider)` | `McpStatelessSyncServer` | Stateless |
| `McpServer.statelessAsync(provider)` | `McpStatelessAsyncServer` | Stateless |

## Server Transports (Built-in, No Spring Required)

### 1. HttpServletStreamableServerTransportProvider (Recommended)

Implements Streamable HTTP protocol. **Is itself a Servlet** (extends `HttpServlet`).

```java
HttpServletStreamableServerTransportProvider transportProvider =
    HttpServletStreamableServerTransportProvider.builder()
        .jsonMapper(jsonMapper)
        .mcpEndpoint("/mcp")
        .build();
```

Register in any Servlet container:

```xml
<!-- web.xml -->
<servlet>
    <servlet-name>mcp</servlet-name>
    <servlet-class>
        io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
    </servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>mcp</servlet-name>
    <url-pattern>/mcp/*</url-pattern>
</servlet-mapping>
```

Or programmatically (Servlet 3.0+):

```java
ServletRegistration.Dynamic reg = servletContext.addServlet("mcp", transportProvider);
reg.addMapping("/mcp/*");
reg.setAsyncSupported(true);  // Important for SSE streaming
```

### 2. HttpServletSseServerTransport (Legacy SSE)

Older SSE transport for backward compatibility.

### 3. StdioServerTransport

For local/subprocess MCP servers (not relevant for web embedding).

## Building an MCP Server

### Sync Server with Tools

```java
McpSyncServer server = McpServer.sync(transportProvider)
    .serverInfo("jira-mcp", "1.0.0")
    .instructions("Jira Data Center MCP Server")
    .capabilities(ServerCapabilities.builder()
        .tools(true)    // Enable tools with list change notifications
        .logging()      // Enable logging
        .build())
    .tool(searchTool, (exchange, request) -> {
        // Handle tool call
        Map<String, Object> args = request.arguments();
        String jql = (String) args.get("jql");
        // ... execute JQL search ...
        return new CallToolResult(
            List.of(new TextContent("Results: ...")),
            false  // isError
        );
    })
    .build();
```

### Tool Definition

```java
Tool searchTool = new Tool(
    "search",                          // name
    "Search Jira issues with JQL",     // description
    new Tool.InputSchema(              // JSON Schema for arguments
        "object",
        Map.of(
            "jql", Map.of(
                "type", "string",
                "description", "JQL query string"
            ),
            "maxResults", Map.of(
                "type", "integer",
                "description", "Max results to return",
                "default", 50
            )
        ),
        List.of("jql")  // required fields
    )
);
```

### Async Server

```java
McpAsyncServer asyncServer = McpServer.async(transportProvider)
    .serverInfo("jira-mcp", "1.0.0")
    .capabilities(ServerCapabilities.builder()
        .tools(true)
        .build())
    .tools(toolSpec1, toolSpec2)
    .build();
```

## MCP Protocol Methods

For a **tools-only** server (our use case), the SDK handles:

| Method | Direction | Handled By |
|---|---|---|
| `initialize` | Client -> Server | SDK (automatic) |
| `notifications/initialized` | Client -> Server | SDK (automatic) |
| `ping` | Bidirectional | SDK (automatic) |
| `tools/list` | Client -> Server | SDK + your tool registrations |
| `tools/call` | Client -> Server | Your tool handlers |

## Streamable HTTP Transport Protocol

### Single Endpoint

Server exposes one HTTP endpoint (e.g., `/mcp`) accepting POST and GET.

### Client -> Server (POST)

- Body: JSON-RPC request/notification/response
- `Accept: application/json, text/event-stream`
- `MCP-Session-Id: <id>` (after initialization)
- `MCP-Protocol-Version: 2025-06-18`

Response: either `application/json` (single response) or `text/event-stream` (streaming).

### Server -> Client (GET)

- Client MAY open persistent SSE stream via GET
- Server uses it for push notifications

### Session Management

- Server assigns `MCP-Session-Id` header during initialization
- Client includes it on all subsequent requests
- Sessions terminated with `404 Not Found`

## Authentication

MCP supports OAuth 2.1 for HTTP transports, but it's **optional**. Options:

1. **Full OAuth 2.1** with PKCE (standard, complex)
2. **Bearer token** (simpler, e.g., PAT passthrough)
3. **None** (rely on host app auth, e.g., Jira session)

For a Jira DC plugin, we can leverage Jira's built-in auth:
- PAT tokens in `Authorization: Bearer <PAT>` header
- Jira session cookies for logged-in users

## Jakarta vs javax.servlet

The MCP Java SDK uses **Jakarta Servlet** (`jakarta.servlet.*`).

### Jira 10+ Uses Jakarta Too!

**Critical finding:** Jira 10.0+ migrated from `javax.*` to `jakarta.*` namespaces.
Since your Jira instance is **10.7.4**, it uses `jakarta.servlet`, `jakarta.ws.rs`,
`jakarta.inject`, etc. — **the same namespace as the MCP Java SDK**.

This means `HttpServletStreamableServerTransportProvider` (which extends
`jakarta.servlet.http.HttpServlet`) may be **directly usable** in a Jira 10+ plugin.

### Approaches (Updated)

1. **Use MCP SDK's Servlet transport directly** (investigate first)
   - Register `HttpServletStreamableServerTransportProvider` as a Jira servlet
   - Requires: MCP SDK's dependencies compatible with Jira's OSGi container
   - Risk: OSGi classloading and dependency conflicts (Jackson, Reactor, etc.)
   - Reward: Full SDK compliance, session management, protocol correctness

2. **Implement MCP protocol on Jira's REST/Servlet modules** (safe fallback)
   - Use Jira's `<rest>` or `<servlet>` modules with `jakarta.servlet`
   - Implement JSON-RPC handling directly (straightforward protocol)
   - Full control over auth integration, no dependency conflicts

3. **Hybrid approach** (recommended)
   - Use MCP SDK's core types (`Tool`, `CallToolResult`, `McpSchema`, etc.)
   - Implement HTTP transport ourselves using Jira's servlet module
   - Best of both worlds: SDK protocol types + Jira's native HTTP handling

### Jira Version Compatibility

| Jira Version | Servlet API | JAX-RS | Inject |
|---|---|---|---|
| 9.x and below | `javax.servlet` | `javax.ws.rs` (1.x) | `javax.inject` |
| 10.x+ | `jakarta.servlet` | `jakarta.ws.rs` (3.x) | `jakarta.inject` |
| 11.x+ | `jakarta.servlet` | `jakarta.ws.rs` (3.x) | `jakarta.inject` |

Plugins targeting Jira 10+ are **NOT backward-compatible** with Jira 9.

## References

- MCP Java SDK: https://github.com/modelcontextprotocol/java-sdk
- MCP Java SDK Docs: https://java.sdk.modelcontextprotocol.io/latest/
- MCP Specification: https://modelcontextprotocol.io/specification/
- MCP Transports: https://modelcontextprotocol.io/specification/draft/basic/transports
- MCP Authorization: https://modelcontextprotocol.io/specification/draft/basic/authorization
