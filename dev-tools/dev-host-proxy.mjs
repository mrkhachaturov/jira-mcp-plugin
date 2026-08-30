#!/usr/bin/env node
// Dev-only auth proxy for the basic-host harness.
//
// basic-host speaks MCP Streamable HTTP to the server URL configured via the
// SERVERS env var, but has no hook for injecting headers. Our Jira plugin
// requires `Authorization: Bearer <PAT>` on every request, so this proxy
// bridges the two: listen on PROXY_PORT, forward to ${JIRA_URL}/plugins/
// servlet/mcp with the bearer header injected, and pipe responses (including
// SSE streams) back verbatim.
//
// Env vars are loaded by mise from .credentials/jira.env at the repo root —
// nothing secret in this file, safe to commit.
//
// Run via `just dev-host` — never enable on a public network.

import http from 'node:http';
import https from 'node:https';
import { URL } from 'node:url';

const PROXY_PORT = parseInt(process.env.PROXY_PORT || '3001', 10);
const TARGET_URL = process.env.JIRA_URL;
const PAT = process.env.JIRA_PAT_RKADMIN;

if (!TARGET_URL) {
  console.error('[dev-host-proxy] JIRA_URL is not set (expected via mise) — abort');
  process.exit(1);
}
if (!PAT) {
  console.error('[dev-host-proxy] JIRA_PAT_RKADMIN is not set (expected via mise) — abort');
  process.exit(1);
}

const target = new URL(TARGET_URL);
const isHttps = target.protocol === 'https:';
const client = isHttps ? https : http;

const ALLOWED_ORIGIN_RE = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$/;

const server = http.createServer((req, res) => {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGIN_RE.test(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    res.setHeader(
      'Access-Control-Allow-Headers',
      'Content-Type, Authorization, MCP-Session-Id, MCP-Protocol-Version, Accept, Last-Event-ID'
    );
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
    res.setHeader(
      'Access-Control-Expose-Headers',
      'MCP-Session-Id, MCP-Protocol-Version'
    );
  }
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  // basic-host posts to "/mcp" / "/mcp/" — our plugin lives at /plugins/servlet/mcp.
  const incomingPath = (req.url || '/').replace(/^\/mcp\/?(\?|$)/, '/plugins/servlet/mcp$1');

  const headers = { ...req.headers };
  headers['authorization'] = `Bearer ${PAT}`;
  headers['host'] = target.host;
  delete headers['connection'];
  delete headers['content-length'];

  const upstream = client.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port || (isHttps ? 443 : 80),
      method: req.method,
      path: incomingPath,
      headers,
    },
    (upstreamRes) => {
      Object.entries(upstreamRes.headers).forEach(([k, v]) => {
        if (
          k.toLowerCase().startsWith('access-control-') &&
          res.hasHeader(k)
        ) return;
        if (v !== undefined) res.setHeader(k, v);
      });
      res.writeHead(upstreamRes.statusCode || 502);
      upstreamRes.pipe(res);
    }
  );

  upstream.on('error', (err) => {
    console.error('[dev-host-proxy] upstream error:', err.message);
    if (!res.headersSent) res.writeHead(502, { 'Content-Type': 'text/plain' });
    res.end(`Upstream error: ${err.message}`);
  });

  req.pipe(upstream);
});

server.listen(PROXY_PORT, '127.0.0.1', () => {
  console.log(
    `[dev-host-proxy] listening on http://127.0.0.1:${PROXY_PORT}/mcp` +
      ` \u2192 ${TARGET_URL}/plugins/servlet/mcp (auth injected from $JIRA_PAT_RKADMIN)`
  );
});

const shutdown = () => {
  console.log('[dev-host-proxy] shutting down');
  server.close(() => process.exit(0));
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
