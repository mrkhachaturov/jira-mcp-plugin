interface StatusBadgeProps {
  status: { name: string; category?: string }
}

function categoryColor(category?: string): string {
  switch (category) {
    case 'new': return '#4b7bec'
    case 'indeterminate': return '#0052cc'
    case 'done': return '#22c55e'
    default: return '#6b7280'
  }
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const bg = categoryColor(status.category)
  return (
    <span style={{
      display: 'inline-block',
      background: bg,
      color: '#fff',
      borderRadius: '20px',
      padding: '2px 8px',
      fontSize: '11px',
      fontWeight: 600,
      letterSpacing: '0.02em',
      whiteSpace: 'nowrap',
    }}>
      {status.name}
    </span>
  )
}
