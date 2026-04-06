# 🔌 Atlassian MCP Plugin for Jira Data Center

> **Native Jira plugin that brings AI directly into your Jira instance** — no sidecars, no proxies, no external services.

[![Build](https://github.com/mrkhachaturov/atlassian-mcp-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/mrkhachaturov/atlassian-mcp-plugin/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An [MCP](https://modelcontextprotocol.io/) (Model Context Protocol) server embedded directly inside Jira Data Center. AI agents like **Claude Code**, **Cursor**, and other MCP-compatible tools connect to your Jira and work with issues, projects, boards, sprints, and more — all running inside the Jira JVM with zero external dependencies.

## ✨ Highlights

- 🛠️ **46 MCP tools** — issues, projects, boards, sprints, comments, worklogs, links, fields, SLA, dev info
- 🔐 **OAuth 2.0** — click "Authenticate" in your AI tool, consent in browser, done. No PATs to manage
- 👥 **Group & user access control** — define who can use MCP via Jira groups or individual users
- 🎛️ **Per-tool management** — enable/disable individual tools, read-only mode
- 📦 **Single JAR** — install via UPM, no Docker, no sidecar, no external services
- 🔑 **PAT support** — Personal Access Tokens still work alongside OAuth

## 🚀 Quick Start

### 1. Install

Download the latest JAR from [**Releases**](../../releases) and upload via **Jira Admin → Manage Apps → Upload App**.

### 2. Configure OAuth *(recommended)*

```
Jira Admin → Application Links → Create Link → External Application (Incoming)
```

| Field | Value |
|-------|-------|
| Name | `MCP Server` |
| Redirect URL | `https://your-jira.example.com/plugins/servlet/mcp-oauth/callback` |
| Permission | `Write` |

Then go to **MCP Configuration → OAuth** tab, paste the Client ID and Secret.

### 3. Connect your AI tool

```json
{
  "mcpServers": {
    "jira": {
      "type": "http",
      "url": "https://your-jira.example.com/rest/mcp/1.0/"
    }
  }
}
```

First connection → "Needs Auth" → click **Authenticate** → Jira consent page → **Allow** → done! 🎉

## 🛠️ Available Tools

<details>
<summary><strong>46 tools across 14 categories</strong> (click to expand)</summary>

| Category | Tools | Count |
|----------|-------|:-----:|
| **Issues** | `search`, `get_issue`, `create_issue`, `update_issue`, `delete_issue`, `batch_create_issues`, `batch_get_changelogs` | 7 |
| **Comments** | `add_comment`, `edit_comment` | 2 |
| **Transitions** | `get_transitions`, `transition_issue` | 2 |
| **Worklogs** | `get_worklog`, `add_worklog` | 2 |
| **Boards & Sprints** | `get_agile_boards`, `get_board_issues`, `get_sprints_from_board`, `get_sprint_issues` | 4 |
| **Links** | `get_link_types`, `create_issue_link`, `create_remote_issue_link`, `remove_issue_link` | 4 |
| **Epics** | `link_to_epic` | 1 |
| **Projects** | `get_all_projects`, `get_project_issues`, `get_project_versions`, `get_project_components`, `create_version`, `batch_create_versions` | 6 |
| **Users** | `get_user_profile`, `get_issue_watchers`, `add_watcher`, `remove_watcher` | 4 |
| **Attachments** | `download_attachments`, `get_issue_images` | 2 |
| **Fields** | `search_fields`, `get_field_options` | 2 |
| **Service Desk** | `get_service_desk_for_project`, `get_service_desk_queues`, `get_queue_issues` | 3 |
| **Forms** | `get_issue_proforma_forms`, `get_proforma_form_details`, `update_proforma_form_answers` | 3 |
| **Metrics** | `get_issue_dates`, `get_issue_sla`, `get_issue_development_info`, `get_issues_development_info` | 4 |

</details>

## 🔐 Authentication

### OAuth 2.0 *(recommended)*

Best UX — users just click "Authenticate" and consent in the browser. The plugin acts as an OAuth 2.0 proxy between MCP clients and Jira's built-in OAuth provider.

```
MCP Client → Plugin OAuth Proxy → Jira OAuth 2.0 → Consent → Token → Done
```

### Personal Access Tokens

Create a PAT in Jira (**Profile → Personal Access Tokens**) and configure your MCP client with a Bearer token header.

## ⚙️ Admin Configuration

Access via **Jira Admin → MCP Server → MCP Configuration**.

| Tab | What |
|-----|------|
| **General** | Enable/disable MCP, read-only mode, base URL override |
| **Access Control** | Allowed groups + individual users (empty = everyone) |
| **Tools** | Click-to-toggle tool list with search filter |
| **OAuth** | Client ID/Secret, status, callback URL, user config snippet |

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                   Jira DC JVM                    │
│                                                  │
│  ┌──────────────┐  ┌────────────────────────┐   │
│  │ MCP Endpoint  │  │   OAuth Proxy Servlet   │   │
│  │ /rest/mcp/1.0 │  │ /plugins/servlet/       │   │
│  │               │  │  mcp-oauth/*            │   │
│  │ JSON-RPC 2.0  │  │ authorize, callback,    │   │
│  │ POST handler  │  │ token, register,        │   │
│  │               │  │ metadata                │   │
│  └───────┬───────┘  └────────────────────────┘   │
│          │                                        │
│  ┌───────▼───────┐                               │
│  │ Tool Registry  │  46 tools                    │
│  │ ToolRegistry   │  Each tool calls             │
│  │                │  Jira REST API internally    │
│  └───────┬───────┘                               │
│          │                                        │
│  ┌───────▼───────┐                               │
│  │ JiraRestClient │  HTTP calls to               │
│  │                │  localhost Jira API           │
│  └────────────────┘                               │
│                                                   │
└───────────────────────────────────────────────────┘
```

## 📋 Requirements

- **Jira Data Center** 10.x (tested on 10.7.4)
- **Java** 17

## 🔨 Building from Source

```bash
# Java 17 required (via mise, SDKMAN, or system)
mvn package -DskipTests

# Output
target/atlassian-mcp-plugin-<version>.jar   # Upload to Jira UPM
target/atlassian-mcp-plugin-<version>.obr
```

## 🙏 Credits

Tool definitions are ported from [**mcp-atlassian**](https://github.com/sooperset/mcp-atlassian) by [@sooperset](https://github.com/sooperset) — an excellent Python-based MCP server for Atlassian products. This plugin re-implements those tools as a native Jira plugin, eliminating the need for an external sidecar service.

## 📄 License

[MIT](LICENSE)
