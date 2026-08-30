#!/usr/bin/env bash
#MISE description="Build the MCP App widget (React → one inlined HTML file) into plugin resources"
#MISE dir="{{config_root}}"
set -euo pipefail

npm --prefix mcp-app ci
npm --prefix mcp-app run build
mkdir -p src/main/resources/mcp-app
cp mcp-app/dist/index.html src/main/resources/mcp-app/issue-card.html
