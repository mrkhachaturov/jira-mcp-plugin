# Atlassian MCP Plugin — Task Runner

set dotenv-load := false

# List all available recipes
default:
    @just --list

# Build the plugin JAR/OBR
build:
    atlas-package

# Run Jira locally with the plugin installed
run:
    atlas-run

# Run Jira with remote debugging enabled
debug:
    atlas-debug

# Run unit tests
test:
    atlas-unit-test

# Clean build artifacts
clean:
    atlas-clean
