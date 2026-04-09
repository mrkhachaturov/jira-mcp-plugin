import { useState } from 'react'
import type { McpApp } from '@modelcontextprotocol/ext-apps'
import type { Issue } from '../types'
import { t } from '../i18n'

interface CommentFormProps {
  app: McpApp
  issue: Issue
  onCommented: () => void
}

export function CommentForm({ app, issue, onCommented }: CommentFormProps) {
  const [open, setOpen] = useState(false)
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSend() {
    if (!body.trim()) return
    setSending(true)
    setError(null)
    try {
      await app.callServerTool({
        name: 'add_comment',
        arguments: { issue_key: issue.key, comment: body.trim() },
      })
      setBody('')
      setOpen(false)
      onCommented()
    } catch (e) {
      setError(t('commentFailed'))
    } finally {
      setSending(false)
    }
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} style={{ minWidth: '90px' }}>
        {t('comment')}
      </button>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <textarea
        value={body}
        onChange={e => setBody(e.target.value)}
        placeholder={t('commentPlaceholder')}
        rows={3}
        disabled={sending}
        style={{
          width: '100%',
          resize: 'vertical',
          fontSize: '13px',
          padding: '6px',
          border: '1px solid var(--border)',
          borderRadius: '4px',
          background: 'var(--bg)',
          color: 'var(--text)',
          fontFamily: 'inherit',
        }}
      />
      {error && (
        <span style={{ fontSize: '11px', color: 'var(--error)' }}>{error}</span>
      )}
      <div style={{ display: 'flex', gap: '6px' }}>
        <button
          className="primary"
          onClick={handleSend}
          disabled={sending || !body.trim()}
        >
          {sending ? t('commentSending') : t('commentSend')}
        </button>
        <button onClick={() => { setOpen(false); setBody('') }} disabled={sending}>
          {t('commentCancel')}
        </button>
      </div>
    </div>
  )
}
