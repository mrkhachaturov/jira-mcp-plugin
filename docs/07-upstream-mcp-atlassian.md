# Upstream: mcp-atlassian

## Overview

The `mcp-atlassian` project is an open-source Python MCP server for Jira and Confluence.
It serves as the reference implementation for what tools to expose and how to interact
with Jira's REST API.

- Location: `.upstream/mcp-atlassian/`
- Language: Python 3.10+
- Framework: FastMCP v2.13-2.14
- License: Open source

## Jira Tools (49 total)

### Issues & Search
| Tool | Type | Description |
|---|---|---|
| `get_issue` | Read | Get issue details with customizable fields |
| `search` | Read | Search with JQL |
| `search_fields` | Read | Search/list available fields |
| `get_field_options` | Read | Get options for select fields |
| `get_project_issues` | Read | Get issues in a project |
| `create_issue` | Write | Create single issue |
| `batch_create_issues` | Write | Batch create issues |
| `update_issue` | Write | Update issue |
| `delete_issue` | Write | Delete issue |
| `batch_get_changelogs` | Read | Get change history |

### Comments
| Tool | Type | Description |
|---|---|---|
| `add_comment` | Write | Add comment to issue |
| `edit_comment` | Write | Edit existing comment |

### Transitions & Workflows
| Tool | Type | Description |
|---|---|---|
| `get_transitions` | Read | Get available status transitions |
| `transition_issue` | Write | Change issue status |

### Worklogs & Time Tracking
| Tool | Type | Description |
|---|---|---|
| `get_worklog` | Read | Get worklog entries |
| `add_worklog` | Write | Log time on issue |

### Boards & Sprints (Agile)
| Tool | Type | Description |
|---|---|---|
| `get_agile_boards` | Read | List Jira Agile boards |
| `get_board_issues` | Read | Get issues on a board |
| `get_sprints_from_board` | Read | Get sprints from board |
| `get_sprint_issues` | Read | Get sprint issues |

### Issue Links
| Tool | Type | Description |
|---|---|---|
| `get_link_types` | Read | Get link type definitions |
| `create_issue_link` | Write | Link two issues |
| `create_remote_issue_link` | Write | Create remote link |
| `remove_issue_link` | Write | Remove link |

### Epics
| Tool | Type | Description |
|---|---|---|
| `link_to_epic` | Write | Link issue to epic |

### Projects & Versions
| Tool | Type | Description |
|---|---|---|
| `get_all_projects` | Read | List all projects |
| `get_project_versions` | Read | Get versions in project |
| `get_project_components` | Read | Get components in project |
| `create_version` | Write | Create version |
| `batch_create_versions` | Write | Batch create versions |

### Users & Watchers
| Tool | Type | Description |
|---|---|---|
| `get_user_profile` | Read | Retrieve user profile |
| `get_issue_watchers` | Read | Get issue watchers list |
| `add_watcher` | Write | Add user as watcher |
| `remove_watcher` | Write | Remove watcher |

### Attachments & Media
| Tool | Type | Description |
|---|---|---|
| `download_attachments` | Read | Download and encode as base64 |
| `get_issue_images` | Read | Extract and encode images |

### Service Desk (DC only)
| Tool | Type | Description |
|---|---|---|
| `get_service_desk_for_project` | Read | Get service desk config |
| `get_service_desk_queues` | Read | Get service desk queues |
| `get_queue_issues` | Read | Get issues in queue |

### Forms (Proforma)
| Tool | Type | Description |
|---|---|---|
| `get_issue_proforma_forms` | Read | Get Proforma forms |
| `get_proforma_form_details` | Read | Get form details |
| `update_proforma_form_answers` | Write | Update form answers |

### Dates & Metrics
| Tool | Type | Description |
|---|---|---|
| `get_issue_dates` | Read | Get issue date fields |
| `get_issue_sla` | Read | Get SLA metrics |
| `get_issue_development_info` | Read | Get dev info (Cloud) |
| `get_issues_development_info` | Read | Batch get dev info |

## Authentication (Jira DC)

For Data Center, the upstream project supports:

1. **Personal Access Token (PAT)** - Recommended
   ```
   Authorization: Bearer <PAT>
   ```

2. **Username + Password** (Basic Auth)
   ```
   Authorization: Basic <base64(user:pass)>
   ```

3. **OAuth 2.0** via Application Links

4. **Per-request credentials** (HTTP headers for multi-tenant):
   - `X-Atlassian-Jira-Personal-Token` + `X-Atlassian-Jira-Url`

## Jira REST API Endpoints Used

The upstream project uses REST API v2 for Data Center:

| Endpoint | Method | Purpose |
|---|---|---|
| `/rest/api/2/issue/{key}` | GET | Get issue |
| `/rest/api/2/search` | GET/POST | JQL search |
| `/rest/api/2/issue` | POST | Create issue |
| `/rest/api/2/issue/{key}` | PUT | Update issue |
| `/rest/api/2/issue/{key}` | DELETE | Delete issue |
| `/rest/api/2/issue/{key}/comment` | POST | Add comment |
| `/rest/api/2/issue/{key}/worklog` | GET/POST | Worklogs |
| `/rest/api/2/issue/{key}/transitions` | GET/POST | Transitions |
| `/rest/api/2/issue/{key}/watchers` | GET/POST/DELETE | Watchers |
| `/rest/api/2/project` | GET | List projects |
| `/rest/api/2/user` | GET | User info |
| `/rest/agile/1.0/board` | GET | Agile boards |
| `/rest/agile/1.0/sprint` | GET | Sprints |

## Cloud vs Data Center Differences

| Aspect | Cloud | Data Center |
|---|---|---|
| User field | `accountId` | `name` / `key` |
| Assignee | `accountId` | `name` or `key` |
| Pagination | Token-based (`nextPageToken`) | Offset-based (`start`) |
| Search API | `/rest/api/3/search/jql` (POST) | `/rest/api/2/search` (GET/POST) |
| Service Desk queues | Different API | Via `/rest/servicedeskapi/` |

## Transport

The upstream supports three MCP transports:
- **STDIO** (default) - for Claude Desktop, Cursor
- **SSE** - `http://host:port/sse`
- **Streamable HTTP** - `http://host:port/mcp` (recommended)

## Tool Filtering

Built-in filtering mechanisms:
- `--read-only` - Exclude all write tools
- `--enabled-tools tool1,tool2` - Enable only specific tools
- `--toolsets default` - Use predefined toolsets

## Project Structure

```
src/mcp_atlassian/
├── jira/              # Jira client (21 mixins composed into JiraFetcher)
│   ├── client.py      # Base client with auth
│   ├── config.py      # JiraConfig dataclass
│   ├── issues.py      # CRUD operations
│   ├── search.py      # JQL search
│   ├── users.py       # User resolution
│   ├── projects.py    # Project operations
│   ├── boards.py      # Agile boards
│   ├── sprints.py     # Sprint operations
│   └── ...            # 12 more mixins
├── servers/
│   ├── main.py        # Main server with tool filtering
│   ├── jira.py        # Jira MCP tools (49 tools)
│   └── dependencies.py
├── models/            # Pydantic v2 data models
├── preprocessing/     # ADF <-> Markdown conversion
└── utils/             # OAuth, SSL, logging
```

## What We Can Reuse

Since we're building a Java plugin, we can't reuse the Python code directly, but we can
replicate:

1. **Tool definitions** - Same 49 tools with same names and schemas
2. **REST API knowledge** - Same endpoints, same request/response formats
3. **Cloud vs DC handling** - Same field name differences
4. **Tool filtering design** - Same admin-controlled enable/disable pattern
5. **Content conversion** - ADF to Markdown for issue descriptions

## What Changes in Our Plugin

Since our plugin runs **inside** Jira:

1. **No REST API calls needed** - We can use Jira's internal Java APIs directly
2. **No authentication to Jira** - Plugin already has access as the current user
3. **Better performance** - No HTTP overhead, direct service access
4. **More capabilities** - Access to internal APIs not exposed via REST
5. **User context** - Natural access to the authenticated user's permissions

## References

- Upstream repo: https://github.com/sooperset/mcp-atlassian
- MCP Atlassian docs: https://github.com/sooperset/mcp-atlassian/blob/main/README.md
