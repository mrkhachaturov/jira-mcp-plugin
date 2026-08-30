#!/usr/bin/env bash
#MISE description="Widget dev server with hot reload"
#MISE dir="{{config_root}}"
set -euo pipefail

exec npm --prefix mcp-app run dev
