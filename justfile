# Atlassian MCP Plugin — Task Runner
# Env vars loaded by mise from .credentials/jira.env (see .mise.toml)

set dotenv-load := false

# List all available recipes
default:
    @just --list

# Build the MCP App widget (React → single HTML)
[group('build')]
build-app:
    cd mcp-app && npm ci && npm run build
    mkdir -p src/main/resources/mcp-app
    cp mcp-app/dist/issue-card.html src/main/resources/mcp-app/

# Build the plugin JAR/OBR
[group('build')]
build: build-app
    atlas-package -DskipTests

# Clean build artifacts
[group('build')]
clean:
    atlas-clean

# Run unit tests (excludes e2e)
[group('test')]
test:
    atlas-mvn test -Dtest="!*E2E*"

# Run e2e tests against live Jira
[group('test')]
e2e:
    atlas-mvn test -Dtest="McpEndpointE2ETest" -DfailIfNoTests=false

# Deploy plugin to Jira instance
[group('deploy')]
deploy: clean build
    #!/usr/bin/env bash
    set -euo pipefail
    JAR=$(ls target/jira-mcp-plugin-*.jar)
    UPM_TOKEN=$(curl -sI \
      -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
      -H "X-Atlassian-Token: no-check" \
      "$JIRA_URL/rest/plugins/1.0/" | grep -i upm-token | awk '{print $2}' | tr -d '\r')
    curl -s -w '\n%{http_code}' \
      -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
      -H "X-Atlassian-Token: no-check" \
      -F "plugin=@$JAR" \
      "$JIRA_URL/rest/plugins/1.0/?token=$UPM_TOKEN"
    echo "Waiting for plugin to enable..."
    sleep 20
    curl -s "$JIRA_URL/rest/plugins/1.0/com.atlassian.mcp.jira-mcp-plugin-key" \
      -H "Authorization: Bearer $JIRA_PAT_RKADMIN" \
      -H "X-Atlassian-Token: no-check" \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print('enabled:', d.get('enabled'), 'version:', d.get('version'))"

# Build + deploy + e2e in one shot
[group('deploy')]
deploy-and-test: deploy e2e

# Run Jira locally with the plugin installed
[group('dev')]
run:
    atlas-run

# Run Jira with remote debugging enabled
[group('dev')]
debug:
    atlas-debug

# Regenerate Java tool classes from upstream Python definitions
[group('dev')]
codegen:
    python3 .codegen/translate.py

# Widget dev server (hot reload)
[group('dev')]
dev-app:
    cd mcp-app && npm run dev
