#!/usr/bin/env bash
#MISE description="Run Jira locally with remote debugging enabled"
#MISE dir="{{config_root}}"
set -euo pipefail

exec atlas-debug
