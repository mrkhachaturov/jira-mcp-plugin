import type { ReactNode } from 'react'
import type { Issue } from '../types'
import { StatusBadge } from './status-badge'
import { PriorityIcon } from './priority-icon'

interface IssueDetailProps {
  issue: Issue
  baseUrl: string
  children?: ReactNode
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
  } catch {
    return iso
  }
}

export function IssueDetail({ issue, baseUrl, children }: IssueDetailProps) {
  const issueUrl = `${baseUrl.replace(/\/$/, '')}/browse/${issue.key}`

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
        <PriorityIcon priority={issue.priority} />
        <span style={{ color: 'var(--text-secondary)', fontSize: '12px' }}>
          {issue.issue_type.name}
        </span>
        <a
          href={issueUrl}
          target="_blank"
          rel="noopener noreferrer"
          style={{ fontWeight: 600, fontSize: '13px', color: 'var(--accent)', textDecoration: 'none' }}
        >
          {issue.key}
        </a>
        <div style={{ marginLeft: 'auto' }}>
          <StatusBadge status={issue.status} />
        </div>
      </div>

      {/* Summary */}
      <h3 style={{ fontSize: '15px', fontWeight: 600, lineHeight: 1.4, color: 'var(--text)' }}>
        {issue.summary}
      </h3>

      {/* Metadata */}
      <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', fontSize: '12px', color: 'var(--text-secondary)' }}>
        <span>
          <strong>Assignee:</strong>{' '}
          {issue.assignee ? issue.assignee.displayName : 'Unassigned'}
        </span>
        <span>
          <strong>Reporter:</strong>{' '}
          {issue.reporter ? issue.reporter.displayName : '—'}
        </span>
        <span>
          <strong>Updated:</strong> {formatDate(issue.updated)}
        </span>
      </div>

      {/* Description */}
      {issue.description && (
        <div style={{
          fontSize: '13px',
          color: 'var(--text)',
          maxHeight: '120px',
          overflowY: 'auto',
          borderLeft: '3px solid var(--border)',
          paddingLeft: '10px',
          lineHeight: 1.5,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}>
          {issue.description}
        </div>
      )}

      {/* Comments */}
      {issue.comments.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div style={{ fontSize: '12px', fontWeight: 600, color: 'var(--text-secondary)' }}>
            Comments ({issue.comments.length})
          </div>
          {issue.comments.slice(0, 5).map((comment, i) => (
            <div key={i} style={{
              background: 'var(--border)',
              borderRadius: '6px',
              padding: '8px',
              fontSize: '12px',
            }}>
              <div style={{ fontWeight: 600, marginBottom: '3px', color: 'var(--text)' }}>
                {comment.author}
                <span style={{ fontWeight: 400, color: 'var(--text-secondary)', marginLeft: '8px' }}>
                  {formatDate(comment.created)}
                </span>
              </div>
              <div style={{ color: 'var(--text)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                {comment.body}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Action slot */}
      {children && (
        <div style={{ borderTop: '1px solid var(--border)', paddingTop: '10px', marginTop: '2px' }}>
          {children}
        </div>
      )}
    </div>
  )
}
