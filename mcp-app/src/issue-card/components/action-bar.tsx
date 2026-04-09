import { useState } from 'react'
import type { McpApp } from '@modelcontextprotocol/ext-apps'
import type { Issue } from '../types'
import { TransitionPicker } from './transition-picker'
import { CommentForm } from './comment-form'
import { t } from '../i18n'

interface ActionBarProps {
  app: McpApp
  issue: Issue
  currentUser: { name: string; displayName?: string }
  onRefresh: () => void
}

export function ActionBar({ app, issue, currentUser, onRefresh }: ActionBarProps) {
  const [assigning, setAssigning] = useState(false)
  const [assignError, setAssignError] = useState<string | null>(null)

  const isAssignedToMe = issue.assignee?.name === currentUser.name

  async function handleAssignToMe() {
    setAssigning(true)
    setAssignError(null)
    try {
      await app.callServerTool({
        name: 'update_issue',
        arguments: {
          issue_key: issue.key,
          fields: JSON.stringify({ assignee: currentUser.name }),
        },
      })
      onRefresh()
    } catch (e) {
      setAssignError(t('assignFailed'))
    } finally {
      setAssigning(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'flex-start' }}>
      <TransitionPicker app={app} issue={issue} onTransitioned={onRefresh} />

      <CommentForm app={app} issue={issue} onCommented={onRefresh} />

      {!isAssignedToMe && (
        <div style={{ display: 'inline-flex', flexDirection: 'column', gap: '4px' }}>
          <button onClick={handleAssignToMe} disabled={assigning}>
            {assigning ? t('assigning') : t('assignToMe')}
          </button>
          {assignError && (
            <span style={{ fontSize: '11px', color: 'var(--error)' }}>{assignError}</span>
          )}
        </div>
      )}
    </div>
  )
}
