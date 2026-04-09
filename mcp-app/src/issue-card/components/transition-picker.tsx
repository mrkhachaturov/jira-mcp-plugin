import { useState } from 'react'
import type { McpApp } from '@modelcontextprotocol/ext-apps'
import type { Issue } from '../types'
import { StatusBadge } from './status-badge'
import { t } from '../i18n'

interface Transition {
  id: string
  name: string
  to_status: string
}

interface TransitionPickerProps {
  app: McpApp
  issue: Issue
  onTransitioned: () => void
}

export function TransitionPicker({ app, issue, onTransitioned }: TransitionPickerProps) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [transitions, setTransitions] = useState<Transition[]>([])
  const [applying, setApplying] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleToggle() {
    if (open) { setOpen(false); return }
    setLoading(true)
    setError(null)
    try {
      const result = await app.callServerTool({
        name: 'get_transitions',
        arguments: { issue_key: issue.key },
      })
      const text = result?.content?.[0]?.text ?? '[]'
      setTransitions(JSON.parse(text))
      setOpen(true)
    } catch {
      setError(t('transitionLoadFailed'))
    } finally {
      setLoading(false)
    }
  }

  async function handleTransition(tr: Transition) {
    setApplying(tr.id)
    setError(null)
    try {
      await app.callServerTool({
        name: 'transition_issue',
        arguments: { issue_key: issue.key, transition_id: tr.id },
      })
      setOpen(false)
      setTransitions([])
      onTransitioned()
    } catch {
      setError(t('transitionFailed'))
    } finally {
      setApplying(null)
    }
  }

  return (
    <div style={{ position: 'relative', display: 'inline-block' }}>
      {/* Status badge as clickable dropdown trigger (like Jira's "To Do ▾") */}
      <div
        role="button"
        tabIndex={0}
        onClick={handleToggle}
        onKeyDown={e => { if (e.key === 'Enter') handleToggle() }}
        style={{ cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '4px' }}
      >
        <StatusBadge status={issue.status} />
        <span style={{ fontSize: '10px', color: 'var(--text-secondary)' }}>
          {loading ? '…' : '▾'}
        </span>
      </div>

      {error && (
        <div style={{ fontSize: '11px', color: 'var(--error)', marginTop: '4px' }}>{error}</div>
      )}

      {/* Dropdown */}
      {open && transitions.length > 0 && (
        <div style={{
          position: 'absolute',
          top: '100%',
          left: 0,
          marginTop: '4px',
          background: 'var(--bg)',
          border: '1px solid var(--border)',
          borderRadius: '6px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
          zIndex: 10,
          minWidth: '160px',
          overflow: 'hidden',
        }}>
          {transitions.map(tr => (
            <div
              key={tr.id}
              role="button"
              tabIndex={0}
              onClick={() => handleTransition(tr)}
              onKeyDown={e => { if (e.key === 'Enter') handleTransition(tr) }}
              style={{
                padding: '8px 12px',
                fontSize: '13px',
                cursor: applying ? 'wait' : 'pointer',
                color: 'var(--text)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                borderBottom: '1px solid var(--border)',
              }}
              onMouseEnter={e => (e.currentTarget.style.background = 'var(--border)')}
              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
            >
              <span>{applying === tr.id ? '…' : tr.name}</span>
              {tr.to_status && (
                <span style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>
                  → {tr.to_status}
                </span>
              )}
            </div>
          ))}
        </div>
      )}

      {open && transitions.length === 0 && !loading && (
        <div style={{
          position: 'absolute', top: '100%', left: 0, marginTop: '4px',
          fontSize: '11px', color: 'var(--text-secondary)',
          background: 'var(--bg)', border: '1px solid var(--border)',
          borderRadius: '6px', padding: '8px 12px',
        }}>
          {t('noTransitions')}
        </div>
      )}
    </div>
  )
}
