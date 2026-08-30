#!/usr/bin/env bash
#MISE description="Local MCP Apps harness: vendored basic-host plus the auth-injecting proxy"
#MISE dir="{{config_root}}"
set -euo pipefail

harness=dev-tools/basic-host
if [ ! -d "$harness/node_modules" ]; then
  echo "[dev-host] installing basic-host dependencies (one-time, ~150 MB)"
  npm --prefix "$harness" install
fi

exec hivemind Procfile.dev-host
