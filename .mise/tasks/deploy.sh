#!/usr/bin/env bash
#MISE description="Upload the built JAR to Jira's plugin manager and confirm it enabled"
#MISE depends=["clean", "build"]
#MISE dir="{{config_root}}"
set -euo pipefail

: "${JIRA_URL:?JIRA_URL is not set — see .credentials/jira.env}"
: "${JIRA_PAT_RKADMIN:?JIRA_PAT_RKADMIN is not set — see .credentials/jira.env}"

jar=$(ls target/jira-mcp-plugin-*.jar)

# UPM hands out a one-shot token in a response header and rejects uploads without it.
token=$(curl -fsSI \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "X-Atlassian-Token: no-check" \
  "$JIRA_URL/rest/plugins/1.0/" | awk 'tolower($1) == "upm-token:" { print $2 }' | tr -d '\r')

if [ -z "$token" ]; then
  echo "no upm-token in the response — the token is probably expired or lacks admin rights" >&2
  exit 1
fi

curl -fsS \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "X-Atlassian-Token: no-check" \
  -F "plugin=@$jar" \
  "$JIRA_URL/rest/plugins/1.0/?token=$token" >/dev/null

echo "uploaded $(basename "$jar"), waiting for Jira to enable it"
sleep 20

curl -fsS \
  -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
  -H "X-Atlassian-Token: no-check" \
  "$JIRA_URL/rest/plugins/1.0/com.atlassian.mcp.jira-mcp-plugin-key" |
  python3 -c 'import sys, json; d = json.load(sys.stdin); print("enabled:", d.get("enabled"), "version:", d.get("version"))'
