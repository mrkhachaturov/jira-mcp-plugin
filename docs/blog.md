[Skip to content](https://simplescraper.io/blog/how-to-mcp#VPContent)

[![](https://simplescraper.io/blog/logo_blog.svg)](https://simplescraper.io/blog/)

Main Navigation [Home](https://simplescraper.io/blog/) [Try Simplescraper](https://simplescraper.io/)

[twitter](https://twitter.com/simplescraper)

[twitter](https://twitter.com/simplescraper)

On this page

On this page

- [Introduction](https://simplescraper.io/blog/how-to-mcp#introduction "Introduction")
- [Table of contents](https://simplescraper.io/blog/how-to-mcp#table-of-contents "Table of contents")
  - [Why MCP is frustrating today](https://simplescraper.io/blog/how-to-mcp#why-mcp-is-frustrating-today "Why MCP is frustrating today")
- [Part 1: Understanding MCP Fundamentals](https://simplescraper.io/blog/how-to-mcp#part-1-understanding-mcp-fundamentals "Part 1: Understanding MCP Fundamentals")
  - [What MCP Actually Is (In Plain Language)](https://simplescraper.io/blog/how-to-mcp#what-mcp-actually-is-in-plain-language "What MCP Actually Is (In Plain Language)")
  - [How Tools in MCP Connect LLMs to Your Existing API Endpoints](https://simplescraper.io/blog/how-to-mcp#how-tools-in-mcp-connect-llms-to-your-existing-api-endpoints "How Tools in MCP Connect LLMs to Your Existing API Endpoints")
  - [Key Components of an MCP Server](https://simplescraper.io/blog/how-to-mcp#key-components-of-an-mcp-server "Key Components of an MCP Server")
  - [MCP Capabilities Table](https://simplescraper.io/blog/how-to-mcp#mcp-capabilities-table "MCP Capabilities Table")
  - [Local vs. Remote Servers: Understanding the Options](https://simplescraper.io/blog/how-to-mcp#local-vs-remote-servers-understanding-the-options "Local vs. Remote Servers: Understanding the Options")
  - [Transport Protocols: Streamable HTTP vs. HTTP+SSE](https://simplescraper.io/blog/how-to-mcp#transport-protocols-streamable-http-vs-http-sse "Transport Protocols: Streamable HTTP vs. HTTP+SSE")
- [Part 2: Session management in your MCP Server](https://simplescraper.io/blog/how-to-mcp#part-2-session-management-in-your-mcp-server "Part 2: Session management in your MCP Server")
- [Part 3: Securing Access with Authentication and OAuth](https://simplescraper.io/blog/how-to-mcp#part-3-securing-access-with-authentication-and-oauth "Part 3: Securing Access with Authentication and OAuth")
  - [Understanding the Tokens and Codes](https://simplescraper.io/blog/how-to-mcp#understanding-the-tokens-and-codes "Understanding the Tokens and Codes")
  - [Implementing OAuth 2.1 endpoints](https://simplescraper.io/blog/how-to-mcp#implementing-oauth-2-1-endpoints "Implementing OAuth 2.1 endpoints")
  - [User Authentication Options for Your MCP Server](https://simplescraper.io/blog/how-to-mcp#user-authentication-options-for-your-mcp-server "User Authentication Options for Your MCP Server")
  - [Implementing Access Token Verification](https://simplescraper.io/blog/how-to-mcp#implementing-access-token-verification "Implementing Access Token Verification")
- [Part 4: Building the Core MCP Server & Transport Handlers](https://simplescraper.io/blog/how-to-mcp#part-4-building-the-core-mcp-server-transport-handlers "Part 4: Building the Core MCP Server & Transport Handlers")
  - [Core Server Setup with Express](https://simplescraper.io/blog/how-to-mcp#core-server-setup-with-express "Core Server Setup with Express")
  - [Transport Implementation Details](https://simplescraper.io/blog/how-to-mcp#transport-implementation-details "Transport Implementation Details")
  - [Session Management Implementation](https://simplescraper.io/blog/how-to-mcp#session-management-implementation "Session Management Implementation")
  - [Supporting Both Modern and Legacy Clients](https://simplescraper.io/blog/how-to-mcp#supporting-both-modern-and-legacy-clients "Supporting Both Modern and Legacy Clients")
  - [Modern Clients (Streamable HTTP)](https://simplescraper.io/blog/how-to-mcp#modern-clients-streamable-http "Modern Clients (Streamable HTTP)")
  - [Modern Client Implementation](https://simplescraper.io/blog/how-to-mcp#modern-client-implementation "Modern Client Implementation")
  - [Legacy Clients (HTTP+SSE)](https://simplescraper.io/blog/how-to-mcp#legacy-clients-http-sse "Legacy Clients (HTTP+SSE)")
  - [Legacy Client Implementation](https://simplescraper.io/blog/how-to-mcp#legacy-client-implementation "Legacy Client Implementation")
  - [Managing Session State Across Transport Types](https://simplescraper.io/blog/how-to-mcp#managing-session-state-across-transport-types "Managing Session State Across Transport Types")
  - [Error Handling and Graceful Fallbacks](https://simplescraper.io/blog/how-to-mcp#error-handling-and-graceful-fallbacks "Error Handling and Graceful Fallbacks")
- [Part 5: Deployment and Production Considerations](https://simplescraper.io/blog/how-to-mcp#part-5-deployment-and-production-considerations "Part 5: Deployment and Production Considerations")
  - [Cloud Deployment Options](https://simplescraper.io/blog/how-to-mcp#cloud-deployment-options "Cloud Deployment Options")
- [Part 6: Troubleshooting and Lessons Learned](https://simplescraper.io/blog/how-to-mcp#part-6-troubleshooting-and-lessons-learned "Part 6: Troubleshooting and Lessons Learned")
  - [Authentication Challenges and Solutions](https://simplescraper.io/blog/how-to-mcp#authentication-challenges-and-solutions "Authentication Challenges and Solutions")
  - [Debugging Transport-Specific Issues](https://simplescraper.io/blog/how-to-mcp#debugging-transport-specific-issues "Debugging Transport-Specific Issues")
  - [Session Management Gotchas](https://simplescraper.io/blog/how-to-mcp#session-management-gotchas "Session Management Gotchas")
  - [Working Around MCP Spec Limitations](https://simplescraper.io/blog/how-to-mcp#working-around-mcp-spec-limitations "Working Around MCP Spec Limitations")
  - [Testing Your Implementation](https://simplescraper.io/blog/how-to-mcp#testing-your-implementation "Testing Your Implementation")
- [Conclusion](https://simplescraper.io/blog/how-to-mcp#conclusion "Conclusion")
  - [Additional Resources](https://simplescraper.io/blog/how-to-mcp#additional-resources "Additional Resources")

# How to MCP: The Complete Guide to Understanding Model Context Protocol and Building Remote Servers [​](https://simplescraper.io/blog/how-to-mcp\#how-to-mcp-the-complete-guide-to-understanding-model-context-protocol-and-building-remote-servers)

![Blog cover](https://simplescraper.io/blog/thinker.webp)

## Introduction [​](https://simplescraper.io/blog/how-to-mcp\#introduction)

The [Model Context Protocol](https://modelcontextprotocol.io/) (MCP) promises an improved and standardized way of connecting LLM applications like Claude and ChatGPT with external tools and services via their APIs. It's a great effort, but if you've ever tried implementing an MCP server you'll quickly find yourself lost amid changing specifications, a lack of real-world examples, and terse documentation.

"The worst documented technology I have ever encountered," as one Hacker News commenter described it.

After spending a week combing through specs, SDK code, and GitHub issues, to build [our own remote MCP server](https://simplescraper.io/docs/mcp-server), this guide distills everything we've learned about MCP into a practical resource to help you:

1. **Understand MCP fundamentals** and how data flows
2. **Build a spec-compliant remote MCP server** that's ready for clients like Claude
3. **Implement authentication correctly** using OAuth 2.1 from the get-go
4. **Avoid common pitfalls** that can cost you hours of debugging

Whether you're building an MCP server to connect your app to LLMs, or a personal one to add AI to your workflows, you'll find the exact steps required to create a working implementation that balances simplicity with full protocol compliance.

## **Table of contents** [​](https://simplescraper.io/blog/how-to-mcp\#table-of-contents)

- [Introduction](https://simplescraper.io/blog/how-to-mcp#introduction)
- [Table of contents](https://simplescraper.io/blog/how-to-mcp#table-of-contents)
  - [Why MCP is frustrating today](https://simplescraper.io/blog/how-to-mcp#why-mcp-is-frustrating-today)
- [Part 1: Understanding MCP Fundamentals](https://simplescraper.io/blog/how-to-mcp#part-1-understanding-mcp-fundamentals)
  - [What MCP Actually Is (In Plain Language)](https://simplescraper.io/blog/how-to-mcp#what-mcp-actually-is-in-plain-language)
  - [How Tools in MCP Connect LLMs to Your Existing API Endpoints](https://simplescraper.io/blog/how-to-mcp#how-tools-in-mcp-connect-llms-to-your-existing-api-endpoints)
  - [Key Components of an MCP Server](https://simplescraper.io/blog/how-to-mcp#key-components-of-an-mcp-server)
  - [MCP Capabilities Table](https://simplescraper.io/blog/how-to-mcp#mcp-capabilities-table)
  - [Local vs. Remote Servers: Understanding the Options](https://simplescraper.io/blog/how-to-mcp#local-vs-remote-servers-understanding-the-options)
  - [Transport Protocols: Streamable HTTP vs. HTTP+SSE](https://simplescraper.io/blog/how-to-mcp#transport-protocols-streamable-http-vs-http-sse)
- [Part 2: Session management in your MCP Server](https://simplescraper.io/blog/how-to-mcp#part-2-session-management-in-your-mcp-server)
- [Part 3: Securing Access with Authentication and OAuth](https://simplescraper.io/blog/how-to-mcp#part-3-securing-access-with-authentication-and-oauth)
  - [Understanding the Tokens and Codes](https://simplescraper.io/blog/how-to-mcp#understanding-the-tokens-and-codes)
  - [Implementing OAuth 2.1 endpoints](https://simplescraper.io/blog/how-to-mcp#implementing-oauth-2-1-endpoints)
  - [User Authentication Options for Your MCP Server](https://simplescraper.io/blog/how-to-mcp#user-authentication-options-for-your-mcp-server)
  - [Implementing Access Token Verification](https://simplescraper.io/blog/how-to-mcp#implementing-access-token-verification)
- [Part 4: Building the Core MCP Server & Transport Handlers](https://simplescraper.io/blog/how-to-mcp#part-4-building-the-core-mcp-server-transport-handlers)
  - [Core Server Setup with Express](https://simplescraper.io/blog/how-to-mcp#core-server-setup-with-express)
  - [Transport Implementation Details](https://simplescraper.io/blog/how-to-mcp#transport-implementation-details)
  - [Session Management Implementation](https://simplescraper.io/blog/how-to-mcp#session-management-implementation)
  - [Supporting Both Modern and Legacy Clients](https://simplescraper.io/blog/how-to-mcp#supporting-both-modern-and-legacy-clients)
  - [Modern Clients (Streamable HTTP)](https://simplescraper.io/blog/how-to-mcp#modern-clients-streamable-http)
  - [Modern Client Implementation](https://simplescraper.io/blog/how-to-mcp#modern-client-implementation)
  - [Legacy Clients (HTTP+SSE)](https://simplescraper.io/blog/how-to-mcp#legacy-clients-http-sse)
  - [Legacy Client Implementation](https://simplescraper.io/blog/how-to-mcp#legacy-client-implementation)
  - [Managing Session State Across Transport Types](https://simplescraper.io/blog/how-to-mcp#managing-session-state-across-transport-types)
  - [Error Handling and Graceful Fallbacks](https://simplescraper.io/blog/how-to-mcp#error-handling-and-graceful-fallbacks)
- [Part 5: Deployment and Production Considerations](https://simplescraper.io/blog/how-to-mcp#part-5-deployment-and-production-considerations)
  - [Cloud Deployment Options](https://simplescraper.io/blog/how-to-mcp#cloud-deployment-options)
- [Part 6: Troubleshooting and Lessons Learned](https://simplescraper.io/blog/how-to-mcp#part-6-troubleshooting-and-lessons-learned)
  - [Authentication Challenges and Solutions](https://simplescraper.io/blog/how-to-mcp#authentication-challenges-and-solutions)
  - [Debugging Transport-Specific Issues](https://simplescraper.io/blog/how-to-mcp#debugging-transport-specific-issues)
  - [Session Management Gotchas](https://simplescraper.io/blog/how-to-mcp#session-management-gotchas)
  - [Working Around MCP Spec Limitations](https://simplescraper.io/blog/how-to-mcp#working-around-mcp-spec-limitations)
  - [Testing Your Implementation](https://simplescraper.io/blog/how-to-mcp#testing-your-implementation)
- [Conclusion](https://simplescraper.io/blog/how-to-mcp#conclusion)
  - [Additional Resources](https://simplescraper.io/blog/how-to-mcp#additional-resources)

* * *

### Why MCP is frustrating today [​](https://simplescraper.io/blog/how-to-mcp\#why-mcp-is-frustrating-today)

The MCP spec is still a work in progress, meaning finding consistent up-to-date code examples and documentation isn't so easy. Here's a quick rundown of common frustrations:

- **Multiple protocol versions**: MCP supports two transport standards with different requirements:
  - HTTP+SSE (2024-11-05) - The legacy protocol
  - Streamable HTTP (2025-03-26) - The modern protocol
- **Different communication patterns**: Each transport uses distinct methods:
  - HTTP+SSE requires maintaining two separate endpoints (`GET /mcp` for SSE and `POST /messages` for requests)
  - Streamable HTTP uses a single endpoint (`POST /mcp`) but involves complex request/response patterns
- **Local and Remote servers:** "server" can refer to either the local or remote implementation of the protocol which can cause confusion about which setup is being discussed
- **Sparse Documentation**: The official MCP website recommends vibe-coding your way to an MCP server, at the cost of practical examples
- **Unclear errors and evolving specs**: Vague error messages ("Claude was unable to connect") and frequent spec changes add friction to implementation

It’s a new protocol, so much of that can be forgiven, but it’s a big source of confusion. The sections below will provide clarity on all of the above and walk you through building a complete solution.

In the rest of this guide, we'll focus on:

- **Explaining MCP:** What it is, how it works and why it helps
- **Building for compatibility:** Create a server that handles both modern and legacy protocols
- **OAuth implementation:** Set up proper authentication with Firebase and MCP's required endpoints
- **State management:** Handle session state across different transport types
- **Production deployment:** Deploy your server to cloud platforms with proper monitoring
- **Troubleshooting:** Diagnose and fix the most common integration issues

Let's jump in!

## Part 1: Understanding MCP Fundamentals [​](https://simplescraper.io/blog/how-to-mcp\#part-1-understanding-mcp-fundamentals)

### What MCP Actually Is (In Plain Language) [​](https://simplescraper.io/blog/how-to-mcp\#what-mcp-actually-is-in-plain-language)

The Model Context Protocol is a standardized way for LLM applications (such as Claude, ChatGPT, and Cursor) to communicate with external APIs and services. It's a bridge between text-based AI models and your code-based API endpoints.

LLMs can't directly run code - but they can be taught to call functions that do. With MCP, you define each of your API capabilities as a "tool" that allow LLMs to:

1. Discover what tools (your API capabilities) are available
2. Learn how to use those tools (parameters, formats, etc.)
3. Choose the most appropriate tool based on the user's prompt
4. Call those tools to execute your underlying API code
5. Receive structured responses they can understand

Think of MCP as "OpenAPI for LLMs" - a standard interface that makes it easier for AI models to interact with your services.

### How Tools in MCP Connect LLMs to Your Existing API Endpoints [​](https://simplescraper.io/blog/how-to-mcp\#how-tools-in-mcp-connect-llms-to-your-existing-api-endpoints)

How exactly does an LLM application like Claude know which API endpoint to call and what parameters to send when, for example, a user asks "Show me all my Twitter scrape recipes"?

In our case, Simplescraper has a REST API endpoint `/recipes` that accepts parameters like `host` and `sort` and returns a list of recipe. But LLM applications have no idea this endpoint exists or what parameters it accepts, unless we tell them about it.

To solve this, MCP allows us to create a tool definition that:

1. Names the tool in a way that describes its purpose
2. Provides a description of what the tool does
3. Defines what parameters it accepts
4. Connects the tool to the actual API endpoint it represents via a handler function

Now when a user asks the Simplescraper MCP about their Twitter recipes, Claude can look at all available tools, understand their purposes through these descriptions, and select the appropriate one to handle the request.

#### Tool Definition Structure [​](https://simplescraper.io/blog/how-to-mcp\#tool-definition-structure)

A tool definition consists of these key elements:

| Element | Purpose | Example |
| --- | --- | --- |
| **Name** | Unique identifier the LLM uses to select the tool | `"list_recipes"` |
| **Parameter Schema** | Defines what arguments the tool accepts | `{ host: z.string() }` |
| **Description** | Helps the LLM understand when to use this tool | "Returns a list of user's scrape recipes with optional filters and sorting." |
| **Handler Function** | The code that calls your API, with access to authentication info (`authInfo`) | `async (params, { authInfo }) => { const response = await fetch(${API_BASE_URL}/recipes?host=${params.host}, { headers: { 'Authorization': Bearer ${authInfo.token} }}); return await response.json(); }` |

#### The Flow of a Prompt Using Tools [​](https://simplescraper.io/blog/how-to-mcp\#the-flow-of-a-prompt-using-tools)

When a user asks a question, several steps happen behind the scenes to connect their natural language request to your API:

MCP HOST

MCP SERVER LAYER

EXTERNAL API

Request

Call API server

Finds data

Returns data

Formats

/recipe endpoint

called with

API key, headers,

URL parameters

Returns

recipe list JSON

Chooses list\_recipes

tool with params:

{host: 'twitter.com'}

MCP Server formats

API response as

MCP response

User: 'Show me all my

Twitter scrape recipes'

Claude shows

recipe list to user

6tg9gc

First, the LLM application analyzes the user's request to determine intent. Then it selects the appropriate tool from those you've defined. In this example, when it detects a request about Twitter recipes, it chooses the `list_recipes` tool and automatically adds the parameter `host: "twitter.com"`. Your MCP server then executes the actual API call, receives the data, and returns it in a format the LLM application can present to the user.

#### Example Tool Definition [​](https://simplescraper.io/blog/how-to-mcp\#example-tool-definition)

Here's how you would define a tool in your MCP server:

javascript

```
// defining a tool
server.tool(
  "list_recipes",  // Name that LLM application will use
  {
    // parameters schema (what LLM can pass)
    host: z.string().optional().describe("Filter by website host")
  },
  {
    // metadata (helps LLM decide when to use this tool)
    description: "Returns a list of your scrape recipes with filters"
  },
  // actual function that calls your API
  async (params, { authInfo }) => {
    try {
      // get user's API key
      const apiKey = await getApiKeyFromToken(authInfo.token);

      // make the actual API call
      const result = await fetchRecipes(apiKey, params);

      // return formatted response to LLM
      return {
        content: [{ type: "text", text: JSON.stringify(result) }]
      };
    } catch (error) {
      return handleError(error);
    }
  }
);
```

The LLM application never directly executes code or calls your API. It simply identifies the right tool and parameters, then your MCP server handles the actual API communication and returns formatted results.

#### Tool Authorization and User-Specific Access [​](https://simplescraper.io/blog/how-to-mcp\#tool-authorization-and-user-specific-access)

An important feature of MCP tool handlers is that they receive an `authInfo` object containing authentication details about the requesting user.

The `AuthInfo` object includes:

- `token`: The OAuth access token
- `clientId`: Identifier for the OAuth client application (provided by the client - e.g., "claudeai")
- `scopes`: Array of permission scopes granted to the user
- `expiresAt`: Optional expiration timestamp

Which allows you to implement per-user authorization for your tools:

javascript

```
// toolhandler receives both params and authInfo
async (params, { authInfo }) => {
  try {
    // authInfo contains token, clientId, and scopes
    console.log("tool called with authInfo:", authInfo);

    // you can use the token to get user-specific credentials
    let apiKey;
    if (authInfo?.token) {
      // look up the user's API key from their MCP token
      apiKey = await getApiKeyFromToken(authInfo.token);
    }

    // you can also check scopes to implement fine-grained access control
    if (!authInfo?.scopes || !authInfo.scopes.includes("list_recipes")) {
      return {
        content: [\
          {\
            type: "text",\
            text: "Unauthorized: User lacks permission to list recipes",\
          }\
        ]
      };
    }

    // make API call with user's credentials
    const result = await fetchRecipes(apiKey, params);
    return { content: [{ type: "text", text: JSON.stringify(result) }] };
  } catch (error) {
    return handleError(error);
  }
}
```

This enables you to implement powerful authorization patterns:

1. **User-specific API keys**: Map MCP tokens to user-specific API keys in your database
2. **Scope-based access control**: Restrict tool access based on assigned scopes
3. **Resource-level permissions**: Check if a user has access to specific resources
4. **Usage quotas and rate limiting**: Implement per-user limits on tool usage

* * *

### Key Components of an MCP Server [​](https://simplescraper.io/blog/how-to-mcp\#key-components-of-an-mcp-server)

An MCP server consists of several essential components:

1. **Tools**: Functions that AI models can call, each with defined parameters and return values
2. **Transport Layer**: The communication mechanism between the AI model and your server
3. **Session Management**: How your server tracks conversation state across multiple interactions
4. **Authentication**: How you verify and authorize access to your tools

The MCP protocol also defines a structured format for messages, based on JSON-RPC 2.0, which handles things like request/response correlation, error reporting, and tool invocation.

### MCP Capabilities Table [​](https://simplescraper.io/blog/how-to-mcp\#mcp-capabilities-table)

While tool calling is the core feature that most MCP servers implement, the protocol supports additional capabilities that can enhance functionality and user experience based on your goals.

| Capability | Description | Status |
| --- | --- | --- |
| **Tool Calling** | Allow models to invoke your functions with parameters | Core Feature |
| **Streaming** | Send incremental partial results back to clients | Optional |
| **Authentication** | Secure access with OAuth 2.1 with PKCE | Required for Production |
| **Session Management** | Track conversation state across multiple interactions | Core Feature |
| **Sampling** | Enable models to run prompts through your server | Optional |
| **Dynamic Tool Discovery** | Let models discover available tools at runtime | Optional |
| **Error Handling** | Return structured JSON-RPC errors | Core Feature |
| **Event Notifications** | Send server-initiated messages to clients | Optional |

### Local vs. Remote Servers: Understanding the Options [​](https://simplescraper.io/blog/how-to-mcp\#local-vs-remote-servers-understanding-the-options)

MCP servers can be implemented in several ways, from local implementations to fully remote services. The MCP ecosystem is still evolving, with local servers currently being the most common due to their ease of implementation, but remote servers likely represent the future direction as the ecosystem matures. Each approach offers different features and use cases.

#### Local MCP Servers: [​](https://simplescraper.io/blog/how-to-mcp\#local-mcp-servers)

Currently, many MCP implementations involve users downloading server code from GitHub repositories and running it locally.

**How it works:**

- User downloads MCP server code from GitHub
- The AI application launches this server as a subprocess on the user's computer
- Communication happens via STDIO (Standard Input/Output)
- Configured through a local file like `claude_desktop_config.json`

json

```
// claude_desktop_config.json for a local server
{
  "mcpServers": {
    "simplescraper-local": {
      "command": "node",
      "args": [\
        "C:/Users/Me/code/mcp/simplescraper-mcp-server.js",\
        "--stdio"\
      ],
      "env": {
        "SIMPLESCRAPER_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

**Features :**

- **Update Process:** When server code changes, users must manually download and install updates
- **Configuration Requirements:** Users edit configuration files and manage dependencies
- **Updates:** Difficult to roll out updates to your server across a user base.
- **Desktop-Bound:** Primarily limited to desktop applications that can launch local processes.
- **Credential Management:** API keys or other credentials stored in local configuration files or environment variables on the user's machine
- **Direct Resource Access:** Good for tools that need direct access to local files or system resources (e.g., a server that reads from `/Users/username/Documents`).

#### Bridged Remote Servers: An Interim Approach [​](https://simplescraper.io/blog/how-to-mcp\#bridged-remote-servers-an-interim-approach)

As MCP implementations evolve, a transitional pattern has emerged: remote servers with a local bridge connector. This approach exists because most MCP clients currently only support local servers via stdio transport, and don't yet support remote servers with OAuth authentication.

**How it works:**

- Server runs on the internet, with users running a local bridge tool (like [`mcp-remote`](https://github.com/geelen/mcp-remote))
- The bridge forwards requests from the local AI application to the remote server
- Still involves the `claude_desktop_config.json` file, but the command points to the bridge tool.

js

```
// claude_desktop_config.json using mcp-remote to bridge to a remote server
{
  "mcpServers": {
    "simplescraper-remote": {
      "command": "npx",
      "args": [\
        "-y",\
        "mcp-remote",\
        "https://mcp.simplescraper.io/mcp",\
        "--header",\
        "Authorization:${AUTH_HEADER}",\
        "--transport",\
        "http-only"\
      ],
      "env": {
        "AUTH_HEADER": "Bearer your-token-here"
      }
    }
  }
}
```

**Features:**

- **Update Process:** Server logic can be updated centrally, but bridge tool needs local updates
- **Configuration Requirements:** Still requires local configuration files
- **Credential Storage:** Authentication tokens typically stored in local configuration
- **Use Cases:** Good for transitioning from local to remote while supporting existing clients

#### Remote Servers: Scalable, secure (and the focus of this guide) [​](https://simplescraper.io/blog/how-to-mcp\#remote-servers-scalable-secure-and-the-focus-of-this-guide)

The most recent evolution in the MCP specification supports fully remote MCP servers that function like standard web services.

**How it works:**

- MCP server runs as a web service accessible via HTTPS
- AI clients connect directly without local configuration or proxies
- Authentication happens through standard OAuth 2.1 web flows

![image-20250522074207242](https://simplescraper.io/blog/assets/how-to-mcp.DHLvpd8e.png)

_Image showing Claude's Integration UI_

**Features:**

- **Update Process:** Changes immediately available to all users
- **Configuration Requirements:** No local setup or configuration files needed - configuration through LLM application settings UI rather than JSON files
- **Portability:** Works on any device that can access the web
- **Credential Storage:** Credentials managed through OAuth tokens, not local files
- **Discovery**: Central directories of available MCP servers
- **Use Cases:** Well-suited for SaaS products and broader integrations

While local and bridged setups have their place, building a remote MCP server offers the most robust, scalable, and user-friendly approach for integrating services with the broader AI ecosystem - these are the servers AI agents will interact with.

Trusting Remote Servers

You should only connect to official MCP servers hosted by the companies themselves. For example, if you want to access your Stripe data via an LLM application, use `https://mcp.stripe.com/`. Avoid third-party proxies or aggregators unless you trust them 100% as they will have access to your data.

### Transport Protocols: Streamable HTTP vs. HTTP+SSE [​](https://simplescraper.io/blog/how-to-mcp\#transport-protocols-streamable-http-vs-http-sse)

The MCP specification defines multiple transport mechanisms that have evolved over time:

#### Transport Protocols Comparison Table [​](https://simplescraper.io/blog/how-to-mcp\#transport-protocols-comparison-table)

| Feature | Streamable HTTP (Modern) | HTTP+SSE (Legacy) |
| --- | --- | --- |
| **Protocol Version** | 2025-03-26 | 2024-11-05 |
| **SDK Transport Class** | `StreamableHTTPServerTransport` | `SSEServerTransport` |
| **Endpoints** | Single `/mcp` for all operations | Dual endpoints: `GET /mcp` \+ `POST /messages` |
| **Client-to-Server** | POST /mcp | POST /messages?sessionId=xxx |
| **Server-to-Client** | Same POST response (streamed) | GET /mcp (SSE stream) |
| **Session Identification** | Via `Mcp-Session-Id` header | Via query parameters in URL |
| **Session Termination** | DELETE /mcp | Connection close |
| **Implementation Complexity** | Lower | Higher |
| **Connection Management** | Connection terminates naturally | Requires explicit management |
| **Primary Benefit** | Simpler, single connection | Compatible with older clients |

The challenge is that, as of mid-2025, we're in a transition period where different clients support different protocols. A production-ready MCP server should support both transport types to ensure compatibility with the widest range of clients.

## Part 2: Session management in your MCP Server [​](https://simplescraper.io/blog/how-to-mcp\#part-2-session-management-in-your-mcp-server)

#### What Is Session Management and Why Do You Need It? [​](https://simplescraper.io/blog/how-to-mcp\#what-is-session-management-and-why-do-you-need-it)

In MCP, "session management" simply means keeping track of a conversation between your LLM application, like Claude, and your server across multiple requests. Without sessions, the app would need to start from scratch with every request, making tools that require state (like a shopping cart or building a report through several data-gathering steps) impossible.

When the app first connects to your MCP server, it makes an "initialize" request. Your server needs to:

1. Create a unique session ID
2. Set up a transport instance for this conversation
3. Remember this session for future requests

A "transport" is just MCP's term for the communication handler between your server and the LLM app. Think of it as a phone line - each session gets its own dedicated line so messages don't get mixed up. The transport handles the actual sending and receiving of MCP messages for that specific conversation.

#### How Sessions Are Passed Back and Forth [​](https://simplescraper.io/blog/how-to-mcp\#how-sessions-are-passed-back-and-forth)

The MCP specification defines a specific way to handle session IDs:

1. When the app first connects (initialization), your server generates a session ID
2. Your server returns this ID in the `Mcp-Session-Id` HTTP header
3. The app must include this same header in all subsequent requests
4. If the session expires, your server returns a 404, and the app starts over

transports {}MCP ServerLLM Apptransports {}MCP ServerLLM AppSession InitializationSubsequent RequestsPOST /mcp (initialize)Generate session ID xyz123Store transport\[xyz123\]Mcp-Session-Id xyz123POST /mcp + Mcp-Session-Id xyz123Lookup transport\[xyz123\]Return existing transportProcess request with session context2wdl6r

#### Implementing Session Management (The Simple Version) [​](https://simplescraper.io/blog/how-to-mcp\#implementing-session-management-the-simple-version)

The simplest and most effective approach for most implementations is to use an in-memory JavaScript Map or object to store your sessions:

javascript

```
const transports = {};  // JavaScript object stores transport instances by session ID
```

That's it! This object will store all active transport instances, with session IDs as keys.

#### Code Example: Simple Session Management [​](https://simplescraper.io/blog/how-to-mcp\#code-example-simple-session-management)

Here's a practical example from our MCP server implementation:

javascript

```
// store all active transports by session ID
const transports = {};

// handle when a client initializes a connection
app.post('/mcp', async (req, res) => {
  // check if this is an initial connection request
  const isInitRequest = req.body && req.body.method === 'initialize';

  if (isInitRequest) {
    // for new sessions, generate a unique ID
    const sessionId = uuidv4();

    // create a transport for this session
    const transport = new StreamableHTTPServerTransport();
    transport.sessionId = sessionId;

    // store it for future requests
    transports[sessionId] = transport;

    // tell LLM application the session ID
    res.setHeader('Mcp-Session-Id', sessionId);

    // handle the initialize request
    await transport.handleRequest(req, res, req.body);
  }
  else {
    // for existing sessions, get the ID from the header
    const sessionId = req.headers['mcp-session-id'];

    // look up the transport for this session
    const transport = transports[sessionId];

    if (!transport) {
      return res.status(404).json({
        error: 'Session not found'
      });
    }

    // handle the request using the existing transport
    await transport.handleRequest(req, res, req.body);
  }
});

// don't forget cleanup when sessions end
app.delete('/mcp', async (req, res) => {
  const sessionId = req.headers['mcp-session-id'];

  if (transports[sessionId]) {
    // Clean up the session
    delete transports[sessionId];
    res.status(204).end();
  } else {
    res.status(404).json({ error: 'Session not found' });
  }
});
```

That's really all there is to it for most implementations. The MCP SDK handles the complex parts of maintaining conversation state within the transport instance - you just need to make sure you keep track of which transport belongs to which session.

INFO

For production servers, you might want to add a simple timeout mechanism to clean up abandoned sessions, but the basic in-memory approach works well for most use cases.

#### When to Consider More Complex Options [​](https://simplescraper.io/blog/how-to-mcp\#when-to-consider-more-complex-options)

For most implementations, the simple in-memory approach works. You might consider more complex options like Redis or database storage if:

- You're running multiple server instances behind a load balancer
- Your server needs to survive restarts without losing session state
- You have very high traffic volumes (thousands of concurrent sessions)

With session management in place to maintain conversation state, next we'll take a look at authentication, which ensures only authorized users can access your MCP tools.

## Part 3: Securing Access with Authentication and OAuth [​](https://simplescraper.io/blog/how-to-mcp\#part-3-securing-access-with-authentication-and-oauth)

With session management in place to track conversation state, the next step is securing access to your MCP tools. This involves two related but distinct processes:

**User Authentication**: Verifying who your actual human user is (via Firebase, Clerk, etc.)

**AI Client Authorization**: Granting an LLM application permission to access your APIs on behalf of that authenticated user (via OAuth 2.1)

Don't let "OAuth 2.1" intimidate you - it's simply a standardized way for AI clients to request access to your tools, and does not require external service or libraries (although many good ones exist to make things easier). In practice, you'll implement a few simple endpoints in your Express server:

javascript

```
// express endpoints you'll build to be OAuth 2.1 compliant
app.get('/.well-known/oauth-protected-resource', (req, res) => {...})
app.get('/.well-known/oauth-authorization-server', (req, res) => {...})
app.get('/authorize', (req, res) => {...})
app.get('/callback', (req, res) => {...})
app.post('/token', (req, res) => {...})
```

If you have an existing app with users, you may use an auth system like Firebase or Clerk which already handles the hard parts (secure login, password management), meaning you only need to focus on the endpoints that connect authenticated users to AI clients.

This dual security model ensures that:

- Only real users with accounts can access your services
- AI clients can only take actions the user has specifically authorized
- You maintain control over which tools are accessible

### Understanding the Tokens and Codes [​](https://simplescraper.io/blog/how-to-mcp\#understanding-the-tokens-and-codes)

It's helpful to understand the different tokens and codes used in the MCP OAuth flow, as this is a common source of confusion:

| Token/Code Name | What It Is | Comes From | Lifespan | Storage | Purpose |
| --- | --- | --- | --- | --- | --- |
| **Identity Token** | Signed token proving user identity | Firebase/Auth provider (after user login) | ~1 hour | Not stored - used immediately | Verify user is who they claim to be |
| **Authorization Code** | Temporary exchange code | Your MCP server creates it | ~10 minutes | Stored temporarily in database | LLM application exchanges this for access token |
| **Access Token** | Long-lived API access credential | Your MCP server creates it | Days/weeks | Stored permanently in database | LLM application sends this with every API request |
| **Bearer Token** | Same as Access Token | Same as Access Token | Same as Access Token | Same as Access Token | The way to send access token in headers |

#### Understanding the Complete OAuth Flow in MCP [​](https://simplescraper.io/blog/how-to-mcp\#understanding-the-complete-oauth-flow-in-mcp)

When a user first asks an LLM application to use your MCP server, here's what happens:

1. **Initial Tool Request**: The LLM application attempts to access your server
2. **Authentication Challenge**: Your server responds with "401 Unauthorized"
3. **OAuth Discovery**: The LLM application obtains authorization server information from /.well-known endpoints
4. **User Login Redirect**: The LLM application directs the user to your login page
5. **Authentication**: User logs in with your auth provider (Firebase, Auth0, etc.) and receives an identity token
6. **Authorization Code Grant**: Your server creates a temporary authorization code
7. **Token Exchange**: The LLM application exchanges this authorization code for an access token
8. **Authorized Tool Access**: The LLM application can now make authorized requests to your tools with this token

This flow combines standard user authentication with the OAuth 2.1 protocol that governs how AI clients get authorized access. Here's how that looks visualized:

Your MCP ServerLLM applicationUserYour MCP ServerLLM applicationUser"Show me my scrape recipes" (No auth)401 Unauthorized + Auth challengeGet auth server detailsAuthorization endpoints info"Please log in to access your data"Visit login pageShow login formProvide credentials & authenticateCreate authorization codeExchange code for access tokenAccess tokenTool request with Bearer token"Here are your Twitter recipes..."Display recipe resultsgkfw9f

And here are the specific endpoints required to be implemented for this flow :

| Step # | Name | Endpoint | Purpose |
| --- | --- | --- | --- |
| 1 | **Initial Request** | Your remote mcp server (eg `https://mcp.simplescraper.io/mcp`) | Client attempts to use a tool |
| 2 | **Protected Resource Discovery** | `/.well-known/oauth-protected-resource` | Discovers auth server details |
| 3 | **Authorization Server Discovery** | `/.well-known/oauth-authorization-server` | Provides detailed auth server metadata |
| 4 | **Authentication** | `/authorize` | Shows login UI to the user |
| 5 | **Authorization Code** | `/callback` | Receives result after user login |
| 6 | **Token Exchange** | `/token` | Exchanges code for token |
| 7 | **Authenticated Access** | `/mcp` with Authorization header | Uses token for all tool calls |

We'll implement all of these endpoints below, including PKCE (a security feature that prevents certain types of attacks).

### Implementing OAuth 2.1 endpoints [​](https://simplescraper.io/blog/how-to-mcp\#implementing-oauth-2-1-endpoints)

javascript

```
// mcp-server.js
// protected Resource Metadata endpoint
app.get('/.well-known/oauth-protected-resource', (req, res) => {
  const baseUrl = getBaseUrl(req);

  const metadata = {
    authorization_servers: [\
      {\
        issuer: baseUrl,\
        authorization_endpoint: `${baseUrl}/authorize`,\
      }\
    ]
  };

  res.json(metadata);
});

// authorization Server Metadata endpoint
app.get('/.well-known/oauth-authorization-server', (req, res) => {
  const baseUrl = getBaseUrl(req);

  res.json({
    issuer: baseUrl,
    authorization_endpoint: `${baseUrl}/authorize`,
    token_endpoint: `${baseUrl}/token`,
    registration_endpoint: `${baseUrl}/register`,
    token_endpoint_auth_methods_supported: ["none"],
    scopes_supported: ["scrape"],
    response_types_supported: ["code"],
    response_modes_supported: ["query"],
    grant_types_supported: ["authorization_code"],
    code_challenge_methods_supported: ["S256"]
  });
});
```

These endpoints provide information about our authentication flow to MCP clients.

### User Authentication Options for Your MCP Server [​](https://simplescraper.io/blog/how-to-mcp\#user-authentication-options-for-your-mcp-server)

You have several options for implementing user authentication:

**Your Existing Auth System**

- If your application already has authentication, use it!
- You'll just need to add the OAuth endpoints required by MCP
- Avoids introducing another auth system
- Works with any authentication system that can issue tokens

**Firebase Auth** (What we'll use in this guide)

- Easy to set up with minimal code
- Free for most use cases
- Handles login UI and user management
- Integrates with broader Google Cloud services

**Supabase Auth**

- Open source and hosted options
- Directly integrates with PostgreSQL database
- The right choice if you're already using Supabase

**Clerk**

- Developer-friendly with beautiful UI components
- Pre-built React components make integration easy
- Popular option with good user management features

**Auth0**

- More comprehensive with advanced features
- Handles both authentication and authorization rules
- Great compliance features for regulated industries
- You still need all the same MCP OAuth endpoints even with Auth0

#### Creating the User Login Experience [​](https://simplescraper.io/blog/how-to-mcp\#creating-the-user-login-experience)

While auth providers like Firebase have their own login UI, you may want a custom login page to match your application's branding. You'll create an `/authorize` endpoint that serves an HTML page for user login (e.g., Google or email sign-in) and then redirects to the `/callback` endpoint with OAuth parameters.

#### The /authorize Endpoint [​](https://simplescraper.io/blog/how-to-mcp\#the-authorize-endpoint)

javascript

```
// mcp-server.js
app.get('/authorize', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'authorize.html'));
});
```

#### Frontend Login Logic [​](https://simplescraper.io/blog/how-to-mcp\#frontend-login-logic)

Your login page needs to:

1. Authenticate the user with your auth provider (Firebase, Auth0, etc.)
2. Get an identity token proving who they are
3. Redirect to `/callback` with all the OAuth parameters

js

```
// in your authorize.html file
function sendToken() {
  firebase.auth().currentUser.getIdToken()
    .then(idToken => {
      const urlParams = new URLSearchParams(window.location.search);
      const callbackUrl = new URL('/callback', window.location.origin);

      // add the identity token from Firebase
      callbackUrl.searchParams.set('idToken', idToken);

      // forward OAuth parameters from the original request
      ['code_challenge', 'code_challenge_method', 'state', 'redirect_uri', 'client_id']
        .forEach(param => {
          if (urlParams.get(param)) {
            callbackUrl.searchParams.set(param, urlParams.get(param));
          }
        });
      callbackUrl.searchParams.set('response_type', 'code');

      // redirect to callback with all parameters
      window.location.href = callbackUrl.toString();
    })
    .catch(err => {
      console.error('Login failed:', err);
      document.getElementById('statusMessage').innerText = `❌ Login failed: ${err.message}`;
    });
}
```

This handles the user login process and redirects to `/callback` with both the identity token and OAuth parameters required for the next step.

INFO

Other auth providers like Auth0, Clerk, or Supabase would replace `firebase.auth().currentUser.getIdToken()` with their own token retrieval methods, but the overall pattern remains the same.

### Implementing Access Token Verification [​](https://simplescraper.io/blog/how-to-mcp\#implementing-access-token-verification)

With the user login flow in place, we now need to handle the access tokens that the LLM application sends in the request Authorization Bearer header with each request. This verification process ensures that only valid tokens from authenticated users can access our tools.

We can create a helper function that validates these tokens and provides user context to our tool handlers:

javascript

```
// mcp-server.js
// authentication helper function
async function authenticateToken(req, res, rpcId = null) {
  const authHeader = req.headers['authorization'] || '';
  const token = authHeader.replace(/^Bearer\s+/i, '').trim();
  const baseUrl = getBaseUrl(req);

  if (!token) {
    const wwwAuthHeader = `Bearer realm="MCP Server", resource_metadata_uri="${baseUrl}/.well-known/oauth-protected-resource"`;

    return {
      success: false,
      response: res.status(401)
        .header('WWW-Authenticate', wwwAuthHeader)
        .json({
          jsonrpc: '2.0',
          error: { code: -32000, message: 'Missing Bearer token' },
          id: rpcId
        })
    };
  }

  try {
    // verify token in Firestore
    const doc = await db.collection('mcp-tokens').doc(token).get();
    if (!doc.exists) {
      return {
        success: false,
        response: res.status(403).json({
          jsonrpc: '2.0',
          error: { code: -32001, message: 'Invalid or expired token' },
          id: rpcId
        })
      };
    }

    const tokenData = doc.data();

    // create auth object for MCP server
    const authObject = {
      token: token,
      clientId: String(tokenData.client_id),
      scopes: Array.isArray(tokenData.scopes) ? tokenData.scopes.map(String) : []
    };

    return {
      success: true,
      tokenData,
      authObject
    };
  } catch (dbError) {
    return {
      success: false,
      response: res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32002, message: 'Database error during authentication' },
        id: rpcId
      })
    };
  }
}
```

The returned `authObject` contains everything our tool handlers need to know about who's making the request and what they're allowed to do.

## Part 4: Building the Core MCP Server & Transport Handlers [​](https://simplescraper.io/blog/how-to-mcp\#part-4-building-the-core-mcp-server-transport-handlers)

### Core Server Setup with Express [​](https://simplescraper.io/blog/how-to-mcp\#core-server-setup-with-express)

Let's start by setting up the basic structure of our MCP server using Express. This forms the foundation for our implementation:

javascript

```
// mcp-server.js
import express from 'express';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { SSEServerTransport } from '@modelcontextprotocol/sdk/server/sse.js';
import { v4 as uuidv4 } from 'uuid';
import authRoutes from './auth-routes.js';
import { debugLog } from './utils.js';
import { db } from './firebaseConfig.js';

// Create Express app
const app = express();
const port = process.env.PORT || 3000;

// Configure middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Add CORS middleware
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, DELETE');
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization, Mcp-Session-Id');
  res.header('Access-Control-Expose-Headers', 'Mcp-Session-Id, WWW-Authenticate');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  next();
});

// Mount authentication routes
app.use(authRoutes);

// Create MCP server with tools registration
const mcpServer = new McpServer({
  name: "My MCP Server",
  version: "1.0.0",
  instructions: `Instructions for using these tools...`
});

// Register your tools here
registerTools(mcpServer);

// Transport maps
const transports = {};
const pendingTransports = {};

// Start server
app.listen(port, () => {
  console.log(`MCP server running on port ${port}`);
  console.log(`MCP endpoint available at http://localhost:${port}/mcp`);
});
```

Next, we'll implement the transport handling.

### Transport Implementation Details [​](https://simplescraper.io/blog/how-to-mcp\#transport-implementation-details)

This function encapsulates the creation and connection of a transport instance. By mapping each session to a dedicated transport, we maintain state across multiple requests.

javascript

```
// mcp-server.js
// Helper function to create and connect a transport
async function createAndConnectTransport(sessionId, mcpServer, transports) {
  if (pendingTransports[sessionId] || transports[sessionId]) {
    return pendingTransports[sessionId] || transports[sessionId];
  }

  const transport = new StreamableHTTPServerTransport({
    enableJsonResponse: true,
    eventSourceEnabled: true,
    onsessioninitialized: (actualId) => {
      delete pendingTransports[actualId];
    }
  });

  // Manually assign session ID
  transport.sessionId = sessionId;

  // Set cleanup handler
  transport.onclose = () => {
    if (transports[sessionId]) {
      delete transports[sessionId];
    }
  };

  // Track pending transport and store immediately
  pendingTransports[sessionId] = transport;
  transports[sessionId] = transport;

  // Connect to MCP server
  try {
    await mcpServer.connect(transport);
  } catch (error) {
    delete pendingTransports[sessionId];
    delete transports[sessionId];
    throw error;
  }

  return transport;
}
```

### Session Management Implementation [​](https://simplescraper.io/blog/how-to-mcp\#session-management-implementation)

Finally, let's implement the main MCP endpoint that handles session management:

javascript

```
// mcp-server.js
// POST handler for /mcp endpoint
app.post('/mcp', async (req, res) => {
  const body = req.body;
  const rpcId = (body && body.id !== undefined) ? body.id : null;

  // authenticate the token
  const authResult = await authenticateToken(req, res, rpcId);
  if (!authResult.success) {
    return authResult.response;
  }

  // assign auth object to request
  req.auth = authResult.authObject;

  // extract session ID from header
  const clientSessionIdHeader = req.headers['mcp-session-id'];
  const actualClientSessionId = Array.isArray(clientSessionIdHeader)
    ? clientSessionIdHeader[0]
    : clientSessionIdHeader;

  let transport;
  let effectiveSessionId;

  // check if this is an initialize request
  const isInitRequest = body && body.method === 'initialize';

  if (isInitRequest) {
    // for initialize requests, create a new session
    effectiveSessionId = uuidv4();
    transport = await createAndConnectTransport(effectiveSessionId, mcpServer, transports);

    // set session ID in response header
    res.setHeader('Mcp-Session-Id', effectiveSessionId);
  } else if (actualClientSessionId && transports[actualClientSessionId]) {
    // for existing sessions, use the existing transport
    transport = transports[actualClientSessionId];
    effectiveSessionId = actualClientSessionId;
  } else {
    // invalid session ID for non-initialize request
    return res.status(400).json({
      jsonrpc: '2.0',
      error: { code: -32003, message: 'Bad Request: No valid session ID for non-initialize request.' },
      id: rpcId
    });
  }

  // set session ID in request headers for the transport
  req.headers['mcp-session-id'] = effectiveSessionId;

  // always set session ID in response headers
  res.setHeader('Mcp-Session-Id', effectiveSessionId);

  // handle the request using the transport
  try {
    await transport.handleRequest(req, res, body);
  } catch (handleError) {
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32603, message: 'Internal server error during MCP request handling' },
        id: rpcId
      });
    }
  }
});
```

This endpoint is used to create a new session for initial Streamable HTTP request and using existing sessions for subsequent requests.

### Supporting Both Modern and Legacy Clients [​](https://simplescraper.io/blog/how-to-mcp\#supporting-both-modern-and-legacy-clients)

A key challenge in building a production-ready MCP server is supporting both modern clients (using Streamable HTTP) and legacy clients (using HTTP+SSE). Rather than creating separate implementations, we'll use a unified approach:

1. **Single MCP Server Instance**: Register all tools with one central MCP server
2. **Multiple Transport Types**: Support different transport implementations for different clients
3. **Protocol Detection**: Route requests to the appropriate transport handler based on HTTP method and path
4. **Shared Authentication**: Apply the same authentication logic regardless of transport type

This approach provides maximum compatibility while minimizing code duplication and maintenance overhead.

```
                     Client Request
                          │
                          ▼
            ┌─────────────────────────┐
            │      Request Router     │
            └─────────────┬───────────┘
                          │
                ┌─────────┴──────────┐
                │                    │
                ▼                    ▼
┌─────────────────────────┐ ┌─────────────────────────┐
│   Modern Transport      │ │   Legacy Transport      │
│  (StreamableHTTP)       │ │  (HTTP+SSE)             │
└─────────────┬───────────┘ └─────────────┬───────────┘
              │                           │
              ▼                           ▼
┌─────────────────────────────────────────────────────┐
│                    MCP Server                       │
└─────────────────────────────────────────────────────┘
                          │
                          ▼
               ┌─────────────────────┐
               │   Tools Registry    │
               └─────────────────────┘
```

### Modern Clients (Streamable HTTP) [​](https://simplescraper.io/blog/how-to-mcp\#modern-clients-streamable-http)

Modern clients use the Streamable HTTP transport with these key characteristics:

1. **Single `/mcp` Endpoint**: All requests go through one endpoint
2. **Session ID via Header**: The `Mcp-Session-Id` header tracks session state
3. **JSON or SSE Responses**: The same endpoint can return either format based on the client's needs
4. **DELETE for Cleanup**: Clients can explicitly terminate sessions

```
┌────────────────┐
│Client POST /mcp│
└───────┬────────┘
        │
        ▼
┌────────────────┐
│   Validate     │
│  Bearer Token  │
└───────┬────────┘
        │
        ▼
┌────────────────┐
│Extract/Generate│
│   Session ID   │
└───────┬────────┘
        │
        ▼
┌────────────────┐
│ Get/Create     │
│   Transport    │
└───────┬────────┘
        │
        ▼
┌────────────────┐
│transport.handle│
│    Request     │
└───────┬────────┘
        │
        ▼
┌────────────────┐
│   Response     │
│  (streamed)    │
└────────────────┘
```

### Modern Client Implementation [​](https://simplescraper.io/blog/how-to-mcp\#modern-client-implementation)

js

```
// POST handler for /mcp (modern Streamable HTTP clients)
app.post('/mcp', async (req, res) => {
  const body = req.body;
  const rpcId = (body && body.id !== undefined) ? body.id : null;

  // authenticate the token and assign it to req.auth
  const authResult = await authenticateToken(req, res, rpcId);
  if (!authResult.success) {
    return authResult.response;
  }

  req.auth = authResult.authObject;

  // session and transport handling
  const clientSessionIdHeader = req.headers['mcp-session-id'];
  const actualClientSessionId = Array.isArray(clientSessionIdHeader)
    ? clientSessionIdHeader[0]
    : clientSessionIdHeader;

  let transport;
  let effectiveSessionId;

  // check if this is an initialize request
  const isInitRequest = body && body.method === 'initialize';

  if (isInitRequest) {
    // create new session for initialize requests
    effectiveSessionId = uuidv4();
    transport = await createAndConnectTransport(
      effectiveSessionId,
      mcpServer,
      transports,
      'Initialize: '
    );

    // set the session ID in the response header for initialize requests
    res.setHeader('Mcp-Session-Id', effectiveSessionId);

  } else if (actualClientSessionId && pendingTransports[actualClientSessionId]) {
    // use pending transport for remote LLM application sessions
    transport = await pendingTransports[actualClientSessionId];
    effectiveSessionId = actualClientSessionId;

  } else if (actualClientSessionId && transports[actualClientSessionId]) {
    // use existing transport for known sessions
    transport = transports[actualClientSessionId];
    effectiveSessionId = actualClientSessionId;

  } else if (actualClientSessionId) {
    // create new transport for unknown session ID
    effectiveSessionId = actualClientSessionId;
    transport = await createAndConnectTransport(
      effectiveSessionId,
      mcpServer,
      transports,
      'Unknown Session: '
    );

  } else {
    // error: non-initialize request without session ID
    return res.status(400).json({
      jsonrpc: '2.0',
      error: {
        code: -32003,
        message: 'Bad Request: No session ID provided for non-initialize request.'
      },
      id: rpcId
    });
  }

  // ensure session ID is consistent across request/response
  req.headers['mcp-session-id'] = effectiveSessionId;
  res.setHeader('Mcp-Session-Id', effectiveSessionId);

  // handle request using MCP transport
  try {
    // pass the original Express req, res, and parsed body
    await transport.handleRequest(req, res, body);
    return;
  } catch (handleError) {
    console.error(`MCP POST handleRequest error (session ${effectiveSessionId}):`, handleError);
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: '2.0',
        error: {
          code: -32603,
          message: 'Internal server error during MCP request handling'
        },
        id: rpcId
      });
    }
  }
});
```

### Legacy Clients (HTTP+SSE) [​](https://simplescraper.io/blog/how-to-mcp\#legacy-clients-http-sse)

Legacy clients use the HTTP+SSE transport, which requires a different approach with two separate endpoints:

- **Dual Endpoints**: `GET /mcp` for SSE stream and `POST /messages` for requests
- **Session ID in URL**: Query parameters used for session tracking
- **Connection Management**: Long-lived SSE connections must be properly maintained

```
┌────────────────┐      ┌────────────────┐
│ Client GET /mcp│      │Client POST     │
│  (SSE stream)  │      │/messages?id=xxx│
└───────┬────────┘      └───────┬────────┘
        │                       │
        ▼                       ▼
┌────────────────┐      ┌────────────────┐
│   Validate     │      │   Validate     │
│  Bearer Token  │      │  Bearer Token  │
└───────┬────────┘      └───────┬────────┘
        │                       │
        ▼                       ▼
┌────────────────┐      ┌────────────────┐
│Create SSEServer│      │ Extract Session│
│   Transport    │      │ID from URL Param│
└───────┬────────┘      └───────┬────────┘
        │                       │
        ▼                       ▼
┌────────────────┐      ┌────────────────┐
│mcpServer.connect│     │ Lookup Transport│
│  (transport)    │     │ By Session ID  │
└───────┬────────┘      └───────┬────────┘
        │                       │
        │                       ▼
        │              ┌────────────────┐
        │              │transport.handle│
        │              │  PostMessage   │
        │              └───────┬────────┘
        │                      │
        ▼                      ▼
┌────────────────┐     ┌────────────────┐
│ Keep-Alive SSE │     │JSON Response to│
│Connection (resp)│     │  POST Request  │
└────────────────┘     └────────────────┘
```

### Legacy Client Implementation [​](https://simplescraper.io/blog/how-to-mcp\#legacy-client-implementation)

javascript

```
// GET handler for /mcp (legacy SSE stream)
app.get('/mcp', async (req, res) => {
  // authenticate the token
  const authResult = await authenticateToken(req, res, null);
  if (!authResult.success) {
    return authResult.response;
  }

  req.auth = authResult.authObject;

  // create SSE transport
  const transport = new SSEServerTransport('/messages', res);

  // store transport for future messages
  transports[transport.sessionId] = transport;

  // set SSE headers
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no');
  res.setHeader('Mcp-Session-Id', transport.sessionId);

  // connect to MCP server
  try {
    await mcpServer.connect(transport, { auth: req.auth });
  } catch (error) {
    if (!res.headersSent) {
      res.status(500).send('Internal server error during SSE setup.');
    } else {
      res.end();
    }

    // clean up transport
    if (transports[transport.sessionId]) {
      delete transports[transport.sessionId];
    }
  }
});

// POST /messages handler for legacy clients
app.post('/messages', express.json(), async (req, res) => {
  const sessionId = req.query.sessionId;
  const body = req.body;
  const rpcId = (body && body.id !== undefined) ? body.id : null;

  // authenticate the token
  const authResult = await authenticateToken(req, res, rpcId);
  if (!authResult.success) {
    return authResult.response;
  }

  req.auth = authResult.authObject;

  if (!sessionId) {
    return res.status(400).json({
      jsonrpc: '2.0',
      error: { code: -32000, message: 'Missing sessionId in query parameters' },
      id: rpcId
    });
  }

  const transport = transports[sessionId];

  if (!transport || !(transport instanceof SSEServerTransport)) {
    return res.status(404).json({
      jsonrpc: '2.0',
      error: { code: -32001, message: 'Session not found or not an SSE session' },
      id: rpcId
    });
  }

  try {
    await transport.handlePostMessage(req, res, body);
  } catch (error) {
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32603, message: 'Internal server error handling message' },
        id: rpcId
      });
    }
  }
});
```

### Managing Session State Across Transport Types [​](https://simplescraper.io/blog/how-to-mcp\#managing-session-state-across-transport-types)

One challenge with supporting multiple transport types is ensuring consistent session management. Our approach uses a single `transports` map to store all transport instances, regardless of type.

This allows us to:

1. **Track All Sessions**: Maintain a consistent view of active sessions
2. **Reuse Authentication Logic**: Apply the same authentication regardless of transport type
3. **Clean Up Resources**: Properly terminate sessions when they're no longer needed

The key is to make sure that each transport's `sessionId` is unique, even across different transport types.

### Error Handling and Graceful Fallbacks [​](https://simplescraper.io/blog/how-to-mcp\#error-handling-and-graceful-fallbacks)

Proper error handling is crucial for a reliable MCP server. Our implementation includes several error-handling strategies:

1. **JSON-RPC Error Responses**: Return structured error objects that follow the JSON-RPC 2.0 specification
2. **Transport-Specific Error Handling**: Different handling for HTTP responses vs. SSE streams
3. **Authentication Errors**: Specific error codes for missing, invalid, or expired tokens
4. **Session Errors**: Clear error messages for session-related issues
5. **Transport Errors**: Graceful handling of transport-level exceptions

#### Common MCP Error Codes [​](https://simplescraper.io/blog/how-to-mcp\#common-mcp-error-codes)

| Code | Description | Common Cause | How to Handle |
| --- | --- | --- | --- |
| -32000 | Authentication Error | Missing or invalid token | Return WWW-Authenticate header |
| -32001 | Invalid Session | Session ID not found | Client should reinitialize |
| -32002 | Method Not Found | Client called unknown method | Check method name |
| -32003 | Invalid Parameters | Missing or invalid parameters | Validate parameters |
| -32004 | Internal Error | Server-side exception | Log details for debugging |
| -32005 | Parse Error | Invalid JSON | Validate request format |

Implementing these error-handling strategies ensure that clients receive meaningful feedback when issues occur.

## Part 5: Deployment and Production Considerations [​](https://simplescraper.io/blog/how-to-mcp\#part-5-deployment-and-production-considerations)

Once your MCP server is working locally, you'll need to deploy it to make it accessible to LLM applications like Claude. The choice of deployment platform depends on your transport implementation and scaling requirements.

### Cloud Deployment Options [​](https://simplescraper.io/blog/how-to-mcp\#cloud-deployment-options)

#### 1\. Google Cloud Run [​](https://simplescraper.io/blog/how-to-mcp\#_1-google-cloud-run)

- Serverless and scales automatically
- Simple docker deployment with `gcloud run deploy`
- Works well with Firebase Auth

bash

```
gcloud run deploy mcp-server --source . --platform managed --region us-central1 --allow-unauthenticated
```

#### 2\. Vercel [​](https://simplescraper.io/blog/how-to-mcp\#_2-vercel)

- Zero-configuration deployments
- Excellent for Node.js applications
- Built-in edge network for global performance
- Seamless GitHub integration

#### 3\. Railway [​](https://simplescraper.io/blog/how-to-mcp\#_3-railway)

- Automatic deployments from GitHub
- Built-in database options

#### 4\. Digital Ocean App Platform [​](https://simplescraper.io/blog/how-to-mcp\#_4-digital-ocean-app-platform)

- Simple deployment from Git repositories
- Managed SSL certificates
- Good choice for small to medium applications

Azure App Service, AWS Elastiv Beanstalk, and Render are also alternatives. Basically, any platform that supports Node.js applications can host your MCP server with proper configuration.

INFO

⚠️ Legacy HTTP+SSE transport (2024-11-05 spec) requires persistent connections, preventing serverless platforms like Google Cloud Run from scaling to zero when idle. This reduces cost efficiency compared to Streamable HTTP transport (2025-03-26 spec). This limitation applies to most serverless providers.

## Part 6: Troubleshooting and Lessons Learned [​](https://simplescraper.io/blog/how-to-mcp\#part-6-troubleshooting-and-lessons-learned)

### Authentication Challenges and Solutions [​](https://simplescraper.io/blog/how-to-mcp\#authentication-challenges-and-solutions)

Authentication was one of the most challenging aspects of implementing our MCP server. A few key lessons:

1. **OAuth Discovery Flow**: Many clients failed silently without proper discovery endpoints. Always implement `/.well-known/oauth-protected-resource` and `/.well-known/oauth-authorization-server`.

2. **PKCE Support**: The PKCE flow is required by the MCP spec. Our solution validates the `code_verifier` against the stored `code_challenge`:


javascript

```
if (tokenData.code_challenge) {
  if (!code_verifier) {
    return res.status(400).json({ error: 'code_verifier required for PKCE flow' });
  }

  let calculatedChallenge;
  if (tokenData.code_challenge_method === 'S256') {
    const hash = crypto.createHash('sha256').update(code_verifier).digest('base64url');
    calculatedChallenge = hash;
  } else {
    calculatedChallenge = code_verifier;
  }

  if (calculatedChallenge !== tokenData.code_challenge) {
    return res.status(401).json({ error: 'Invalid code_verifier' });
  }
}
```

3. **WWW-Authenticate Header**: When a token is missing, clients expect a proper `WWW-Authenticate` header to start the OAuth flow. Our implementation follows the standard format:

javascript

```
const wwwAuthHeader = `Bearer realm="MCP Server", resource_metadata_uri="${baseUrl}/.well-known/oauth-protected-resource"`;
res.header('WWW-Authenticate', wwwAuthHeader);
```

4. **Client Registration**: Some clients require dynamic client registration. Implement a `/register` endpoint to support these clients:

javascript

```
router.post('/register', express.json(), async (req, res) => {
  const { client_name, redirect_uris = [] } = req.body;
  const client_id = uuidv4();

  await db.collection('mcp-clients').doc(client_id).set({
    client_id,
    client_name,
    redirect_uris,
    createdAt: new Date().toISOString()
  });

  res.status(201).json({
    client_id,
    token_endpoint_auth_method: 'none',
    redirect_uris
  });
});
```

### Debugging Transport-Specific Issues [​](https://simplescraper.io/blog/how-to-mcp\#debugging-transport-specific-issues)

Each transport type comes with its own set of challenges:

**Streamable HTTP Issues:**

1. **Session ID Propagation**: Make sure to set the `Mcp-Session-Id` header in both directions:
   - Extract from `req.headers['mcp-session-id']` for incoming requests (note that headers are lowercase in Node.js/Express).
   - Always set via `res.setHeader('Mcp-Session-Id', effectiveSessionId)` in responses (with capitalized casing as required by the MCP spec)
2. **Content-Type Handling**: Different clients expect different content types:
   - The spec says clients MUST accept both `application/json` and `text/event-stream`
   - In practice, many clients only support one format
   - Configure `StreamableHTTPServerTransport` with `enableJsonResponse: true` and `eventSourceEnabled: true` to support both

**HTTP+SSE Issues:**

1. **SSE Connection Timeouts**: SSE connections can timeout unexpectedly:
   - Set `Content-Type: text/event-stream`, `Cache-Control: no-cache`, and `Connection: keep-alive` headers
   - Consider implementing a heartbeat mechanism to keep connections alive
2. **Session Management Complexity**: The dual-endpoint nature of HTTP+SSE makes session management more complex:
   - Ensure that `POST /messages` can find the transport created by `GET /mcp`
   - Extract sessionId from query parameters: `POST /messages?sessionId=...`
   - Clean up resources properly when SSE connections close
3. **Headers Already Sent**: A common error with SSE is trying to send headers after the stream has started:
   - Check `res.headersSent` before attempting to send status codes or headers
   - Handle errors differently for SSE vs. regular HTTP responses

### Session Management Gotchas [​](https://simplescraper.io/blog/how-to-mcp\#session-management-gotchas)

Several session management issues caused subtle bugs in our implementation:

1. **Race Conditions**: When a client sends multiple requests in quick succession, race conditions can occur:

   - Use a `pendingTransports` map to track transports being created
   - Check both `transports` and `pendingTransports` before creating a new transport
2. **Session Cleanup**: Proper session cleanup is essential to prevent memory leaks:

   - Set an `onclose` handler for each transport
   - Implement explicit cleanup in the DELETE handler
   - Consider adding a session timeout mechanism for abandoned sessions
3. **Manual Session ID Assignment**: The SDK doesn't always set the `sessionId` property automatically:

   - Explicitly assign `transport.sessionId = sessionId` after creation
   - Use this same ID in the `transports` map for consistent lookup

### Working Around MCP Spec Limitations [​](https://simplescraper.io/blog/how-to-mcp\#working-around-mcp-spec-limitations)

The MCP specification has several limitations that required workarounds:

1. **Authentication Context**: The spec doesn't provide a standard way to pass authentication context to tool handlers:
   - We used `req.auth` to pass authentication information through the request object
   - Future versions of the SDK may support a better approach
2. **Limited Error Handling**: The spec doesn't fully define error handling:
   - We implemented a consistent approach using JSON-RPC error codes
   - Always include the original `id` in error responses for proper correlation
3. **Missing But Required Endpoints**: Some clients expect endpoints not clearly required by the spec:
   - Include a `registration_endpoint` in OAuth metadata even if unused
   - This prevents silent failures during client initialization

### Testing Your Implementation [​](https://simplescraper.io/blog/how-to-mcp\#testing-your-implementation)

Once you've built your MCP server, you'll want to test it to ensure it works with different clients. One current (and temporary) hurdle is that for now accessing the Claude's remote MCP settings requires a Claude Max subscription. Fortunately, there are other approaches to test your implementation.

#### Using mcp-remote for Testing [​](https://simplescraper.io/blog/how-to-mcp\#using-mcp-remote-for-testing)

The `mcp-remote` tool provides a convenient way to test your remote MCP server. This approach uses Claude Desktop (or any other LLM application supporting MCP) with a local bridge to your remote server.

Add the following configuration to your `claude_desktop_config.json` file:

json

```
{
  "mcpServers": {
    "test-remote-mcp": {
      "command": "npx",
      "args": [\
        "-y",\
        "mcp-remote",\
        "https://your-server-url.com/mcp",\
        "--header",\
        "Authorization:${AUTH_HEADER}",\
        "--transport",\
        "http-only"\
      ],
      "env": {
        "AUTH_HEADER": "Bearer your-token-here"
      }
    }
  }
}
```

#### Cloudflare's AI playground [​](https://simplescraper.io/blog/how-to-mcp\#cloudflare-s-ai-playground)

Cloudflare provide an [AI playground](https://playground.ai.cloudflare.com/) that allows you to enter the URL of your remote MCP server and initiate the authorization process. It comes with a debug log that is helpful for indentifying any issues that occur during the process.

## Conclusion [​](https://simplescraper.io/blog/how-to-mcp\#conclusion)

Building a reliable MCP server today feels a little rough around the edges, mainly because of sparse documentation and a rapidly evolving specification. However, as this guide shows, once you have the steps in front of you, building a full functionality server is relatively simple.

With your server running, your app can seamlessly talk to LLMs and you can enjoy the benefits (and occasional frustrations) of being an early adopter to a protocol that may turn out to be as foundational to AI as HTTP became for the web.

### Additional Resources [​](https://simplescraper.io/blog/how-to-mcp\#additional-resources)

For those looking to learn more about MCP implementation, here are some helpful resources:

- **[Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-03-26)**: The official specification
- **[MCP SDK Documentation](https://github.com/modelcontextprotocol/sdk)**: Documentation for the official JavaScript SDK
- **[OAuth 2.1 Specification](https://oauth.net/2.1/)**: Details on implementing secure authentication
- **[Firebase Authentication Guide](https://firebase.google.com/docs/auth)**: Documentation for Firebase authentication
- **[Auth0 Documentation](https://auth0.com/docs)**: For Auth0 implementation details
- **[Clerk Documentation](https://clerk.com/docs)**: For Clerk authentication integration
- **[Supabase Auth Documentation](https://supabase.com/docs/guides/auth)**: For Supabase authentication
- [MCP Inspector](https://github.com/modelcontextprotocol/inspector): Developer tool for testing and debugging MCP servers
- [Awesome Remote MCP Servers](https://github.com/jaw9c/awesome-remote-mcp-servers): List of spec compliant remote MCP servers (including [Simplescraper's](https://simplescraper.io/docs/mcp-server))

## More articles:

[![](https://res.cloudinary.com/simplescraper/image/upload/v1618898641/Blog/blog_airbnb_cover_leffmn.png)\\
**How to scrape your Airbnb wishlist (or any website) into Airtable**](https://simplescraper.io/blog/scrape-airbnb-website-to-airtable)

[![](https://res.cloudinary.com/simplescraper/image/upload/v1591310454/Blog/blog_cover_webflow_simplescraper_yufeou.png)\\
**How to scrape data into Webflow to create a job board**](https://simplescraper.io/blog/scrape-nocode-simplescraper-webflow)

## Scrape Data with Simplescraper     Turn websites into structured data in seconds.

[Try it out](https://simplescraper.io/?ref=blog)