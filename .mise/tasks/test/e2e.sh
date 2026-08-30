#!/usr/bin/env bash
#MISE description="End-to-end tests against the live Jira instance"
#MISE dir="{{config_root}}"
set -euo pipefail

: "${JIRA_URL:?JIRA_URL is not set — see .credentials/jira.env}"
exec atlas-mvn test -Dtest=McpEndpointE2ETest -DfailIfNoTests=false
