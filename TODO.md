# TODO

Improvements identified through testing and ChatGPT review (2026-04-09).

## Response Consistency

- [ ] **Unify response shapes across all tools** — some tools return full JSON objects, others return simplified arrays. Normalize to a consistent envelope pattern so LLM agents don't need to handle multiple formats.
- [ ] **Add `issue_id` alongside `key`** in issue responses for tools that may benefit from numeric ID access.

## Discovery & Create Meta

- [ ] **Add `get_create_meta` tool** — return available issue types per project with required/optional fields. Critical for LLM agents to create issues correctly without guessing.
- [ ] **Add `get_edit_meta` tool** — return editable fields for a specific issue. Useful for update operations.

## Write Operations

- [ ] **Add `validate_only` mode to `create_issue` and `update_issue`** — `batch_create_issues` already has it. Lets LLM agents dry-run before committing changes.
- [ ] **Transition enrichment** — `get_transitions` should include `hasRequiredFields` boolean per transition (expand `?expand=transitions.fields`, check for `required: true`). Widget can then hide transitions that need extra screen fields.

## MCP Apps Widget

- [ ] **Board/kanban view widget** — second widget showing sprint board columns with drag-and-drop.
- [ ] **Project dashboard widget** — overview with project stats, recent activity.
- [ ] **Queue support in widget** — add `get_queue_issues` to UI-linked tools (needs JSM response normalization and public/internal comment handling).

## Response Normalization

- [ ] **Components, labels, fix_versions always present** — even if empty arrays, include them in structuredContent for consistency.
- [ ] **Priority with id** — include `priority.id` alongside `priority.name` in structuredContent.
