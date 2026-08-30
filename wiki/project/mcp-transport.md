# MCP Protocol — Streamable HTTP

Single endpoint `/plugins/servlet/mcp` supporting Streamable HTTP transport
(MCP spec 2025-06-18). The wire-level transport is implemented by the
official MCP Java SDK (`io.modelcontextprotocol.sdk:mcp-core` 2.0.0-M2 +
`mcp-json-jackson2`); the plugin only registers tools/resources via
`SyncToolSpecification` / `SyncResourceSpecification` and provides a
`JiraAuthContextExtractor` that surfaces the calling Jira user to tool bodies.

| Method                      | Action                                                      |
| --------------------------- | ----------------------------------------------------------- |
| `initialize`                | Return server info + capabilities + `MCP-Session-Id` header |
| `notifications/initialized` | Return 202                                                  |
| `tools/list`                | Return filtered tool list                                   |
| `tools/call`                | Dispatch to tool, return result                             |
| `ping`                      | Keep-alive                                                  |

## Transport behavior

- **POST** — client sends JSON-RPC. Server returns `application/json` for single responses.
- **POST with `progressToken`** — if tool supports progress (`supportsProgress() = true`),
  server returns `text/event-stream` with progress notifications followed by final result.
- **GET** — SSE stream for server-initiated notifications (requires `MCP-Session-Id`).
- **DELETE** — close session.

## When SSE streaming is used

The server decides per-request. SSE is ONLY used when:

1. The client sends a `progressToken` in `params._meta.progressToken`.
2. AND the tool implements `supportsProgress() = true`.

Otherwise, the response is always plain JSON. SSE is for sending **multiple
JSON-RPC messages** (progress notifications before the final result), not for
wrapping big responses.

## Streaming-capable tools

| Tool                          | What it streams                        |
| ----------------------------- | -------------------------------------- |
| `batch_create_issues`         | Progress per issue created             |
| `batch_create_versions`       | Progress per version created           |
| `batch_get_changelogs`        | Progress per issue's changelog fetched |
| `get_issues_development_info` | Progress per issue's dev info fetched  |

## Session management

- `MCP-Session-Id` returned on `initialize`, required on subsequent requests.
- Sessions stored in static `ConcurrentHashMap` (survives JAX-RS per-request instantiation).
- Session-user binding: each session is tied to the authenticated user; cross-user access returns 403.
- Sessions capped at 200 with 4-hour TTL; expired sessions cleaned lazily.
- DELETE closes session (requires auth + user match), 404 returned for expired/unknown sessions.

## Security

- **Auth on all methods**: POST, GET (SSE), and DELETE all require valid auth + access control.
- **Origin validation** (MUST per spec): `Origin` header checked against Jira base URL +
  `claude.ai`/`claude.com`. Invalid Origin → 403. Localhost always allowed.
- **MCP-Protocol-Version** header validated on non-initialize requests.
- **Rate limiting**: IP-based for anonymous endpoints (`/register` 5/min, `/token` 20/min,
  `/authorize` 10/min), per-user for MCP (120/min). Implemented in `RateLimiter.java`.
- **Request body size limits**: 1 MB for MCP POST, 64 KB for DCR register, 8 KB for token exchange.
- **Session-user binding**: sessions cannot be used by a different user than the one who created them.
- **PKCE S256 mandatory**: `code_challenge` required on `/authorize`, only `S256` method accepted.
- **Redirect URI validation**: `/authorize` validates `redirect_uri` against the client's declared
  URIs — CIMD `redirect_uris` or the DCR-registered set — via `RedirectUriMatcher` (prevents open
  redirect / token theft). Exact match, except that loopback URIs ignore the port per RFC 8252 §7.3
  (a native client on an ephemeral port declares `http://localhost/callback`, sends
  `http://localhost:62127/callback`); scheme, host, path and query still match exactly, and
  `localhost` is not `127.0.0.1`. `/token` re-checks the value stored at `/authorize`, exactly.
- **Security event logging**: all rejections logged with `[MCP-SEC]` prefix and client IP.
- **Security headers**: `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`,
  `X-Frame-Options: DENY`.
- **In-memory map caps**: DCR clients (1000, 24h TTL), pending auths (500, 10min),
  proxy codes (500, 10min).
- **Token exchange hardened**: `HttpClient` with `Redirect.NEVER`, 5s connect timeout,
  10s request timeout.
- **Refresh tokens**: passed through from Jira — no proxy state, Jira's DB manages
  lifecycle and rotation.
