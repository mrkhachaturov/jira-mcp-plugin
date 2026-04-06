# Atlassian Forge Platform

## IMPORTANT: Forge is Cloud-Only

Forge does **NOT** support Jira Data Center or Server. It is exclusively for Atlassian
Cloud products. This document is included for reference and context only.

For Jira DC plugin development, use the Atlassian Plugin SDK (P2 framework).
See [01-atlassian-sdk.md](01-atlassian-sdk.md).

## What is Forge?

Forge is Atlassian's cloud app development platform where apps are hosted on
Atlassian-managed infrastructure. Apps are built in JavaScript (Node.js) and run
in a sandboxed serverless environment.

## Forge vs Connect vs P2

| Aspect | Forge | Connect | P2 (Server/DC) |
|--------|-------|---------|-----------------|
| **Target** | Cloud only | Cloud only | Server/DC only |
| **Language** | JavaScript (Node.js) | Any (webhook-based) | Java |
| **Hosting** | Atlassian-managed | Developer-managed | Runs inside Jira JVM |
| **Runtime** | Sandboxed serverless | External server | OSGi bundle |
| **Install** | Marketplace | Marketplace | UPM upload or Marketplace |
| **Security** | Atlassian-controlled | Developer responsible | Developer responsible |
| **Database** | Forge Storage API | External | Active Objects or direct |

## Forge CLI

### Installation

```bash
npm install -g @forge/cli
forge --version
```

### Authentication

```bash
forge login
# Requires Atlassian API scoped token
```

### Core Commands

| Command | Purpose |
|---------|---------|
| `forge create` | Create new Forge app (interactive) |
| `forge deploy` | Deploy to Atlassian infrastructure |
| `forge deploy -e staging` | Deploy to staging |
| `forge deploy -e production` | Deploy to production |
| `forge install` | Install on an Atlassian site |
| `forge install --upgrade` | Upgrade existing installation |
| `forge tunnel` | Local dev tunnel (hot-reload) |
| `forge lint` | Lint app code |

### Development Workflow

```bash
forge login
forge create          # Interactive template selection
cd my-app
forge deploy          # Deploy to development
forge install         # Install on your site
forge tunnel          # Local dev with hot-reload
```

## App Structure

```
my-app/
├── manifest.yml      # App configuration
├── package.json
├── src/
│   ├── index.jsx     # Frontend (Custom UI)
│   └── resolvers/
│       └── index.js  # Backend functions
└── static/
```

### manifest.yml

```yaml
modules:
  jira:issuePanel:
    - key: my-panel
      title: My Panel
      resource: main
      resolver:
        function: resolver

  function:
    - key: resolver
      handler: index.handler

app:
  id: "ari:cloud:ecosystem::app/<uuid>"
  runtime:
    name: nodejs24.x
    memoryMB: 512

permissions:
  scopes:
    - read:jira-work
    - write:jira-work
    - storage:app

resources:
  - key: main
    path: src/frontend/index.jsx
```

## Jira Modules Available in Forge

| Module | Key | Purpose |
|--------|-----|---------|
| Issue Panel | `jira:issuePanel` | Panel in issue detail view |
| Issue Context | `jira:issueContext` | Context section in issue view |
| Issue Glance | `jira:issueGlance` | Glance panel on issues |
| Issue Action | `jira:issueAction` | Action in issue menu |
| Project Page | `jira:projectPage` | Custom page in a project |
| Global Page | `jira:globalPage` | Global page across Jira |
| Admin Page | `jira:adminPage` | Page in Jira admin |
| Dashboard Gadget | `jira:dashboardGadget` | Dashboard widget |
| Custom Field | `jira:customField` | Custom issue fields |
| Workflow Condition | `jira:workflowCondition` | Workflow transition condition |
| Workflow Validator | `jira:workflowValidator` | Workflow transition validation |
| Workflow Post Function | `jira:workflowPostFunction` | Post-transition action |

## Your Developer Console

You have access to the Atlassian Developer Console at https://developer.atlassian.com
with an existing Forge app ("Notion-AI-Connector"). You can create new Forge apps or
OAuth 2.0 integrations.

## Connect-to-Forge Migration

Forge supports incremental adoption from Connect apps. You can integrate Forge modules
into existing Connect architecture without a complete rewrite. Only available for
Confluence and Jira Connect apps already on Marketplace.

## Why Forge Doesn't Apply to This Project

This project targets Jira Data Center (self-hosted). Forge:
- Requires Atlassian Cloud infrastructure
- Cannot run on self-hosted instances
- Uses JavaScript, not Java
- Has no mechanism for DC deployment

The correct approach for Jira DC is the P2 plugin framework via the Atlassian SDK.

## References

- Forge Documentation: https://developer.atlassian.com/platform/forge/
- Forge CLI Reference: https://developer.atlassian.com/platform/forge/cli-reference/
- Forge Getting Started: https://developer.atlassian.com/platform/forge/getting-started/
