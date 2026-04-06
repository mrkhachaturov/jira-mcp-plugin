# REST and Servlet Modules

## REST Plugin Module

REST modules expose JAX-RS endpoints from your plugin. This is the primary way to serve
HTTP APIs from a Jira DC plugin.

### Declaration

```xml
<rest key="mcp-rest" path="/mcp" version="1.0">
    <description>MCP Server REST API</description>
    <package>com.example.plugins.mcp.rest</package>
</rest>
```

### Attributes

| Attribute | Required | Description |
|---|---|---|
| `key` | Yes | Unique module identifier |
| `path` | Yes | Base URL path (e.g., `/mcp`) |
| `version` | Yes | API version; use `none` to omit from URL |
| `<package>` | No | Package to scan for JAX-RS resources |
| `<dispatcher>` | No | REQUEST, INCLUDE, FORWARD, ERROR |

### URL Pattern

```
http://host:port/context/rest/{path}/{version}/{resource-path}
```

Example with `path="/mcp"`, `version="1.0"`:
```
https://bpm.example.com/rest/mcp/1.0/tools
```

Use `version="none"` to omit version:
```
https://bpm.example.com/rest/mcp/tools
```

### JAX-RS Resource Implementation

```java
package com.example.plugins.mcp.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

@Path("/")
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
public class McpResource {

    @Inject
    public McpResource(/* dependencies */) { }

    @GET
    @Path("tools")
    public Response listTools() {
        return Response.ok(/* tool list */).build();
    }

    @POST
    @Path("tools/call")
    public Response callTool(String jsonBody) {
        return Response.ok(/* result */).build();
    }
}
```

### Accessing Request Context

```java
@GET
@Path("example")
public Response example(@Context HttpServletRequest request,
                        @Context UriInfo uriInfo) {
    // Get current user
    String remoteUser = request.getRemoteUser();
    // Get query params
    String param = uriInfo.getQueryParameters().getFirst("key");
    return Response.ok().build();
}
```

### JSON Serialization

Two options:

**1. JAXB Annotations (built-in)**
```java
import javax.xml.bind.annotation.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ToolDefinition {
    @XmlElement private String name;
    @XmlElement private String description;
    // constructor, getters
}
```

**2. Gson (add dependency)**
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>
</dependency>
```

### Response Format Selection

Clients can request format via:
1. `Accept` header: `application/json`
2. URL extension: `/rest/mcp/1.0/tools.json`

### Path Composition

Paths accumulate across package, class, and method annotations:

```java
@Path("/admin")        // package-level
package com.example;

@Path("/config")       // class-level
public class Config {
    @Path("/tools")    // method-level
    public Response getTools() { }
}
// Result: /rest/mcp/1.0/admin/config/tools
```

---

## Servlet Plugin Module

Servlets provide lower-level HTTP handling. Useful for SSE streams, file uploads,
or custom protocols.

### Declaration

```xml
<servlet key="mcp-servlet" name="MCP Servlet"
         class="com.example.plugins.mcp.McpServlet">
    <description>MCP Streamable HTTP endpoint</description>
    <url-pattern>/mcp/*</url-pattern>
</servlet>
```

### Attributes

| Attribute | Required | Description |
|---|---|---|
| `key` | Yes | Unique identifier |
| `class` | Yes | Must extend `javax.servlet.http.HttpServlet` |
| `<url-pattern>` | Yes | URL matching pattern (repeatable) |
| `<init-param>` | No | Initialization parameters |
| `name` | No | Human-readable name |
| `state` | No | `enabled` (default) or `disabled` |

### URL Access

Servlets are served under `/plugins/servlet/` prefix:
```
https://bpm.example.com/plugins/servlet/mcp/endpoint
```

### Implementation

```java
package com.example.plugins.mcp;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.inject.Inject;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.user.UserManager;
import java.io.IOException;

public class McpServlet extends HttpServlet {

    private final UserManager userManager;

    @Inject
    public McpServlet(@ComponentImport UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");
        resp.getWriter().write("{\"status\": \"ok\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        // Handle POST
    }
}
```

### URL Pattern Wildcards

- `*` matches zero or more characters (including `/`)
- `?` matches zero or one character

Examples:
- `/mcp/*` matches `/mcp/endpoint`, `/mcp/a/b/c`
- `/mcp/?.json` matches `/mcp/a.json`, `/mcp/.json`

### Init Parameters

```xml
<servlet key="my-servlet" class="com.example.MyServlet">
    <url-pattern>/my-path</url-pattern>
    <init-param>
        <param-name>configKey</param-name>
        <param-value>configValue</param-value>
    </init-param>
</servlet>
```

Access via `getServletConfig().getInitParameter("configKey")`.

---

## SSE (Server-Sent Events) from a Servlet

Jira DC runs on Tomcat which supports SSE. From a servlet:

```java
@Override
protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
    resp.setContentType("text/event-stream");
    resp.setCharacterEncoding("UTF-8");
    resp.setHeader("Cache-Control", "no-cache");
    resp.setHeader("Connection", "keep-alive");
    resp.setHeader("X-Accel-Buffering", "no");  // For reverse proxies

    PrintWriter writer = resp.getWriter();

    // Send SSE events
    writer.write("event: message\n");
    writer.write("data: {\"type\": \"ping\"}\n\n");
    writer.flush();

    // Keep connection open for streaming...
}
```

**Considerations:**
- Tomcat supports async servlets (Servlet 3.0+) for long-lived connections
- Enable async in web.xml or via `@WebServlet(asyncSupported = true)`
- For Jira plugins, async servlet support depends on the plugin framework version
- Reverse proxies (Traefik) may need `X-Accel-Buffering: no` header

---

## Servlet Filter Module

```xml
<servlet-filter key="mcp-auth-filter" name="MCP Auth Filter"
                class="com.example.plugins.mcp.McpAuthFilter"
                location="before-login" weight="100">
    <url-pattern>/plugins/servlet/mcp/*</url-pattern>
    <url-pattern>/rest/mcp/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
</servlet-filter>
```

### Filter Locations

| Location | Description |
|---|---|
| `after-encoding` | After character encoding filter |
| `before-login` | Before authentication |
| `before-decoration` | Before page decoration |
| `before-dispatch` | Before request dispatch |
| `after-dispatch` | After request dispatch (cleanup) |

## References

- REST Plugin Module: https://developer.atlassian.com/server/framework/atlassian-sdk/rest-plugin-module/
- Servlet Plugin Module: https://developer.atlassian.com/server/framework/atlassian-sdk/servlet-plugin-module/
- Servlet Filter Module: https://developer.atlassian.com/server/framework/atlassian-sdk/servlet-filter-plugin-module/
