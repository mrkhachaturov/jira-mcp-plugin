[Skip to main content](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#main-content)

[![Claude Help Center](https://downloads.intercomcdn.com/i/o/lupk8zyo/787776/ade321b9d8ff06050cb06ac0049d/d7ef4b66df4ff3851b5de741185c97ab.png)](https://support.claude.com/en/)

[API Docs](https://docs.claude.com/en/docs/intro) [Release Notes](https://support.claude.com/en/articles/12138966-release-notes) [How to Get Support](https://support.claude.com/en/articles/9015913-how-to-get-support)

EnglishFrançaisDeutschBahasa IndonesiaItaliano日本語한국어PortuguêsPусский简体中文Español繁體中文

English

[API Docs](https://docs.claude.com/en/docs/intro) [Release Notes](https://support.claude.com/en/articles/12138966-release-notes) [How to Get Support](https://support.claude.com/en/articles/9015913-how-to-get-support)

EnglishFrançaisDeutschBahasa IndonesiaItaliano日本語한국어PortuguêsPусский简体中文Español繁體中文

English

Search for articles...

1. [All Collections](https://support.claude.com/en/)

2. [Connectors](https://support.claude.com/en/collections/15399129-connectors)

3. Build custom connectors via remote MCP servers

# Build custom connectors via remote MCP servers

Updated this week

Table of contents

[Building remote MCP servers](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_18842a1b95)[MCP support](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_ed638d686b)[Testing remote MCP servers](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_b0e7505b75)

Custom connectors using remote MCP are available on Claude, Cowork, and Claude Desktop for users on Free, Pro, Max, Team, and Enterprise plans. Free users are limited to one custom connector. This feature is currently in beta.

## Building remote MCP servers

To get started with remote servers, start with the following resources:

- The **[auth spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)**, especially details on **[the auth flow for third-party services](https://modelcontextprotocol.io/specification/2025-03-26/basic/authorization#2-10-third-party-authorization-flow)**.

- The remote server examples in the **[TypeScript](https://github.com/modelcontextprotocol/typescript-sdk/tree/main/src/examples/server)** and **[Python](https://github.com/modelcontextprotocol/python-sdk/tree/main/examples/servers)** SDKs.

- The client and server auth implementations in the **[TypeScript](https://github.com/modelcontextprotocol/typescript-sdk/tree/main/src/server/auth)** and **[Python](https://github.com/modelcontextprotocol/python-sdk/tree/main/src/mcp)** SDKs.

- The official MCP **[roadmap](https://modelcontextprotocol.io/development/roadmap)** and **[draft spec’s changelog](https://modelcontextprotocol.io/specification/draft/changelog)** for details on how the protocol will evolve.


Other resources (like **[this](https://simplescraper.io/blog/how-to-mcp)**) may also be helpful to learn about considerations when building, deploying, and troubleshooting remote servers.

In addition, some **[solutions like Cloudflare](https://developers.cloudflare.com/agents/guides/remote-mcp-server/)** provide remote MCP server hosting with built-in autoscaling, OAuth token management, and deployment.

## MCP support

### Platforms

- Remote MCP servers are supported on Claude and Claude Desktop for Pro, Max, Team, and Enterprise plans.





  - To configure remote MCP servers for use in Claude Desktop, add them via **[Customize > Connectors](https://claude.ai/customize/connectors)**. Claude Desktop will not connect to remote servers that are configured directly via `claude_desktop_config.json`.


- As of July, Claude for iOS and Android also support remote MCP servers!





  - Users can use tools, prompts, and resources from remote servers that they’ve already added via claude.ai. Users cannot add new servers directly from Claude Mobile.


### Network reachability

All remote MCP connections originate from Anthropic's cloud infrastructure, regardless of which Claude client the user is running. Your server must accept inbound HTTPS connections from Anthropic's IP ranges—see **[Anthropic IP addresses](https://docs.anthropic.com/en/api/ip-addresses)** for the current list.

This applies even when users are running Cowork or Claude Desktop locally. Remote connectors added through **[Customize > Connectors](https://claude.ai/customize/connectors)** are brokered server-side, so the request to your MCP server comes from Anthropic, not from the user's machine.

If your server runs inside a private network, configure your firewall or ingress to allow inbound traffic from Anthropic's published IP ranges. Servers that are only reachable via VPN or behind a firewall that blocks Anthropic's egress IPs will fail to connect.

### Transport and auth

- Claude supports both SSE- and Streamable HTTP-based remote servers, although support for SSE may be deprecated in the coming months.

- Claude supports both authless and OAuth-based remote servers.


**Auth support**

- Claude supports the **[3/26 auth spec](https://modelcontextprotocol.io/specification/2025-03-26/basic/authorization#1-introduction)** and (as of July) the **[6/18 auth spec](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)**.

- Claude supports **[Dynamic Client Registration](https://modelcontextprotocol.io/specification/2025-03-26/basic/authorization#2-4-dynamic-client-registration)** (DCR).





  - OAuth servers can signal to Claude that a DCR client has been deleted and that Claude should re-register the client by returning an HTTP 401 with an error of invalid\_client from the token endpoint, as described in [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749#section-5.2).

  - As of July, users are also able to specify a custom client ID and client secret when configuring a server that doesn’t support DCR.


- Claude’s OAuth callback URL is **[https://claude.ai/api/mcp/auth\_callback](https://claude.ai/api/mcp/auth_callback)** and its OAuth client name is Claude.





  - This callback URL may change to **[https://claude.com/api/mcp/auth\_callback](https://claude.com/api/mcp/auth_callback)** in the future – if you choose to allowlist MCP client callback URLs, please allowlist this callback URL as well to ensure that your server continues to work with Claude.


- Claude supports token expiry and refresh – servers should support this functionality in order to provide the best experience for users.


See **[here](https://docs.anthropic.com/en/api/ip-addresses#ipv4-2)** for the IP addresses used by Claude for inbound and outbound connections to MCP servers. Server developers wishing to disallow non-Claude MCP Clients can whitelist these IP addresses, Claude’s OAuth callback URL, and/or Claude’s OAuth client name.

### Protocol Features

- Claude supports tools, prompts, and resources.





  - Claude supports text- and image-based tool results.

  - Claude supports text- and binary- based resources.


- Claude does not yet support resource subscriptions, sampling, and other more advanced or draft capabilities.


## Testing remote MCP servers

The best way to test and validate a server is to try **[adding it to Claude](https://support.anthropic.com/en/articles/11175166-getting-started-with-custom-connectors-using-remote-mcp#h_3d1a65aded)**.

Alternatively, use the **[inspector tool](https://github.com/modelcontextprotocol/inspector)**. This will allow you to validate:

- that your server successfully initiates and completes the auth flow.

- that your server correctly implements various parts of the auth flow.

- which tools, prompts, resources, and other MCP features your server exposes.


See the **[MCP documentation](https://modelcontextprotocol.io/docs/tools/inspector)** for more details on using inspector and for other tips on how to debug and troubleshoot your server.

In addition, other solutions like **[Cloudflare’s AI Playground](https://playground.ai.cloudflare.com/)** allow you to test remote MCP server functionality.

* * *

Related Articles

[Get started with custom connectors using remote MCP](https://support.claude.com/en/articles/11175166-get-started-with-custom-connectors-using-remote-mcp) [Use connectors to extend Claude's capabilities](https://support.claude.com/en/articles/11176164-use-connectors-to-extend-claude-s-capabilities) [Anthropic Connectors Directory FAQ](https://support.claude.com/en/articles/11596036-anthropic-connectors-directory-faq) [Remote MCP Server Submission Guide](https://support.claude.com/en/articles/12922490-remote-mcp-server-submission-guide) [Building Desktop Extensions with MCPB](https://support.claude.com/en/articles/12922929-building-desktop-extensions-with-mcpb)

Did this answer your question?

😞😐😃

Table of contents

[Building remote MCP servers](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_18842a1b95)[MCP support](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_ed638d686b)[Testing remote MCP servers](https://support.claude.com/en/articles/11503834-build-custom-connectors-via-remote-mcp-servers#h_b0e7505b75)

[![Claude Help Center](https://downloads.intercomcdn.com/i/o/487548/17213f6a445c8e6e874b1f4b/fad85208982e639d11b9108df895a293.png)](https://support.claude.com/en/)

- [Product](https://www.anthropic.com/product)
- [Research](https://www.anthropic.com/research)
- [Company](https://www.anthropic.com/company)
- [News](https://www.anthropic.com/news)
- [Careers](https://www.anthropic.com/careers)

- [Terms of Service - Consumer](https://www.anthropic.com/terms)
- [Terms of Service - Commercial](https://www.anthropic.com/legal/commercial-terms)
- [Privacy Policy](https://www.anthropic.com/privacy)
- [Usage Policy](https://www.anthropic.com/aup)
- [Responsible Disclosure Policy](https://www.anthropic.com/responsible-disclosure-policy)
- [Compliance](https://trust.anthropic.com/)