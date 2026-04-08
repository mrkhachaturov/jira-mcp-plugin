import { useState, type ReactNode } from 'react'
import type { Issue } from '../types'
import { StatusBadge } from './status-badge'
import { PriorityIcon } from './priority-icon'
import { IssueDetail } from './issue-detail'

interface IssueListProps {
  issues: Issue[]
  baseUrl: string
  totalCount: number
  renderActions?: (issue: Issue) => ReactNode | undefined
}

export function IssueList({ issues, baseUrl, totalCount, renderActions }: IssueListProps) {
  const [expandedKey, setExpandedKey] = useState<string | null>(null)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
      {totalCount > issues.length && (
        <div style={{
          fontSize: '11px',
          color: 'var(--text-secondary)',
          padding: '4px 0 8px 0',
          textAlign: 'right',
        }}>
          Showing {issues.length} of {totalCount}
        </div>
      )}

      {issues.map(issue => {
        const isExpanded = expandedKey === issue.key

        return (
          <div key={issue.key} style={{ borderBottom: '1px solid var(--border)' }}>
            {/* Row */}
            <div
              role="button"
              tabIndex={0}
              onClick={() => setExpandedKey(isExpanded ? null : issue.key)}
              onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') setExpandedKey(isExpanded ? null : issue.key) }}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '8px 4px',
                cursor: 'pointer',
                userSelect: 'none',
              }}
            >
              <PriorityIcon priority={issue.priority} />

              <span style={{
                fontWeight: 600,
                fontSize: '12px',
                color: 'var(--accent)',
                whiteSpace: 'nowrap',
                flexShrink: 0,
              }}>
                {issue.key}
              </span>

              <span style={{
                flex: 1,
                fontSize: '13px',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                color: 'var(--text)',
              }}>
                {issue.summary}
              </span>

              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                <StatusBadge status={issue.status} />
                {issue.assignee && (
                  <span style={{ fontSize: '11px', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                    {issue.assignee.displayName}
                  </span>
                )}
                <span style={{
                  fontSize: '10px',
                  color: 'var(--text-secondary)',
                  transform: isExpanded ? 'rotate(180deg)' : 'none',
                  transition: 'transform 0.15s',
                  display: 'inline-block',
                }}>
                  ▾
                </span>
              </div>
            </div>

            {/* Expanded detail */}
            {isExpanded && (
              <div style={{ padding: '12px 4px 16px 4px' }}>
                <IssueDetail issue={issue} baseUrl={baseUrl}>
                  {renderActions?.(issue)}
                </IssueDetail>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
