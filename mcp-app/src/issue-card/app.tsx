import { useState, useCallback } from 'react'
import { useApp, useHostStyles } from '@modelcontextprotocol/ext-apps/react'
import type { StructuredContent } from './types'
import { Loading } from './components/loading'
import { Empty } from './components/empty'
import { IssueList } from './components/issue-list'
import { IssueDetail } from './components/issue-detail'
import { ActionBar } from './components/action-bar'
import './styles/global.css'

export function App() {
  const [data, setData] = useState<StructuredContent | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const { app, isConnected } = useApp({
    appInfo: { name: 'jira-issue-card', version: '1.0.0' },
    capabilities: {},
    onAppCreated: (app) => {
      app.ontoolinput = () => {
        setLoading(true)
        setData(null)
        setError(null)
      }
      app.ontoolresult = (params) => {
        setLoading(false)
        if (params.isError) {
          setError('Failed to load issue data.')
          return
        }
        if (params.structuredContent) {
          setData(params.structuredContent as StructuredContent)
        }
      }
    },
  })

  useHostStyles(app, app?.getHostContext())

  const refreshIssue = useCallback(async (issueKey: string) => {
    if (!app) return null
    const result = await app.callServerTool({
      name: 'get_issue',
      arguments: { issue_key: issueKey },
    })
    if (result?.structuredContent) {
      const sc = result.structuredContent as StructuredContent
      setData(prev => {
        if (!prev) return sc
        return {
          ...prev,
          issues: prev.issues.map(i =>
            i.key === issueKey && sc.issues[0] ? sc.issues[0] : i
          ),
        }
      })
      return sc.issues[0] ?? null
    }
    return null
  }, [app])

  if (loading) return <Loading />
  if (error) return <div style={{ color: 'var(--error)', padding: '12px' }}>{error}</div>
  if (!data || data.issues.length === 0) return <Empty />

  if (data.issues.length === 1) {
    return (
      <IssueDetail issue={data.issues[0]} baseUrl={data.baseUrl}>
        {app && (
          <ActionBar
            app={app}
            issue={data.issues[0]}
            currentUser={data.currentUser}
            onRefresh={() => refreshIssue(data.issues[0].key)}
          />
        )}
      </IssueDetail>
    )
  }

  return (
    <IssueList
      issues={data.issues}
      baseUrl={data.baseUrl}
      totalCount={data.totalCount}
      renderActions={issue => app ? (
        <ActionBar
          app={app}
          issue={issue}
          currentUser={data.currentUser}
          onRefresh={() => refreshIssue(issue.key)}
        />
      ) : undefined}
    />
  )
}
