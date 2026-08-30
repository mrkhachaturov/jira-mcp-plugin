#!/usr/bin/env bash
#MISE description="Refresh wiki/mcp-docs/ from the upstream MCP documentation repo"
#MISE dir="{{config_root}}"
set -euo pipefail

exec bash scripts/sync-mcp-docs.sh
