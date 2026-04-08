import { useState } from 'react'
import type { McpApp } from '@modelcontextprotocol/ext-apps'
import type { Issue } from '../types'

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

  async function handleOpen() {
    if (open) {
      setOpen(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const result = await app.callServerTool({
        name: 'get_transitions',
        arguments: { issue_key: issue.key },
      })
      const text = result?.content?.[0]?.text ?? '[]'
      const parsed: Transition[] = JSON.parse(text)
      setTransitions(parsed)
      setOpen(true)
    } catch (e) {
      setError('Failed to load transitions.')
    } finally {
      setLoading(false)
    }
  }

  async function handleTransition(transition: Transition) {
    setApplying(transition.id)
    setError(null)
    try {
      await app.callServerTool({
        name: 'transition_issue',
        arguments: { issue_key: issue.key, transition_id: transition.id },
      })
      setOpen(false)
      setTransitions([])
      onTransitioned()
    } catch (e) {
      setError('Transition failed.')
    } finally {
      setApplying(null)
    }
  }

  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', gap: '6px' }}>
      <button onClick={handleOpen} disabled={loading} style={{ minWidth: '90px' }}>
        {loading ? 'Loading…' : 'Transition'}
      </button>

      {error && (
        <span style={{ fontSize: '11px', color: 'var(--error)' }}>{error}</span>
      )}

      {open && transitions.length > 0 && (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '4px',
          padding: '6px',
          background: 'var(--bg)',
          border: '1px solid var(--border)',
          borderRadius: '6px',
          maxWidth: '280px',
        }}>
          {transitions.map(t => (
            <button
              key={t.id}
              onClick={() => handleTransition(t)}
              disabled={applying !== null}
              style={{ fontSize: '12px', padding: '3px 10px' }}
              title={`→ ${t.to_status}`}
            >
              {applying === t.id ? '…' : t.name}
            </button>
          ))}
        </div>
      )}

      {open && transitions.length === 0 && !loading && (
        <span style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>No transitions available.</span>
      )}
    </div>
  )
}
