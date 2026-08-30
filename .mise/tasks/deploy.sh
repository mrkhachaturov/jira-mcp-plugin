#!/usr/bin/env bash
#MISE description="Upload the built JAR to Jira's plugin manager and confirm it enabled"
#MISE depends=["clean", "build"]
#MISE dir="{{config_root}}"
set -euo pipefail

: "${JIRA_URL:?JIRA_URL is not set — see .credentials/jira.env}"
: "${JIRA_PAT_RKADMIN:?JIRA_PAT_RKADMIN is not set — see .credentials/jira.env}"

key=com.atlassian.mcp.jira-mcp-plugin-key
auth=(-H "Authorization: Bearer $JIRA_PAT_RKADMIN" -H "X-Atlassian-Token: no-check")

jar=$(ls target/jira-mcp-plugin-*.jar)
built=$(basename "$jar" .jar | sed 's/^jira-mcp-plugin-//')

# UPM hands out a one-shot token in a response header and rejects uploads without it.
token=$(curl -fsSI "${auth[@]}" "$JIRA_URL/rest/plugins/1.0/" |
  awk 'tolower($1) == "upm-token:" { print $2 }' | tr -d '\r')

if [ -z "$token" ]; then
  echo "no upm-token in the response — the token is probably expired or lacks admin rights" >&2
  exit 1
fi

curl -fsS "${auth[@]}" -F "plugin=@$jar" "$JIRA_URL/rest/plugins/1.0/?token=$token" >/dev/null
echo "uploaded $(basename "$jar"), waiting for Jira to install it"

for _ in $(seq 1 20); do
  sleep 4
  state=$(curl -fsS "${auth[@]}" "$JIRA_URL/rest/plugins/1.0/$key" || true)
  enabled=$(printf '%s' "$state" | sed -n 's/.*"enabled":\([a-z]*\).*/\1/p')
  version=$(printf '%s' "$state" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')
  [ "$enabled" = "true" ] && [ "$version" = "$built" ] && break
done

echo "enabled: ${enabled:-none} version: ${version:-none}"

if [ "$enabled" != "true" ] || [ "$version" != "$built" ]; then
  echo "deploy did not take effect — built $built, Jira reports ${version:-nothing}" >&2
  exit 1
fi
