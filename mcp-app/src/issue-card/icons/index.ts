/**
 * Issue Type Icon Registry
 *
 * To add a new issue type icon:
 * 1. Drop the SVG/PNG file in this directory (mcp-app/src/issue-card/icons/)
 * 2. Add an import line below
 * 3. Add the mapping: Jira issue type name → imported icon
 *
 * The key must match the exact issue type name as returned by Jira.
 * Jira handles localization of type names — use the default (English) name here.
 */

import bug from './bug.svg'
import epic from './epic.svg'
import itHelp from './it_help.png'
import serviceRequest from './serv_req.png'
import srApproval from './sr_approval.png'
import story from './story.svg'
import subTask from './sub-task.svg'
import task from './task.svg'

export const issueTypeIcons: Record<string, string> = {
  'Bug': bug,
  'Epic': epic,
  'IT Help': itHelp,
  'Service Request': serviceRequest,
  'Service Request with Approvals': srApproval,
  'Story': story,
  'Sub-task': subTask,
  'Task': task,
}
