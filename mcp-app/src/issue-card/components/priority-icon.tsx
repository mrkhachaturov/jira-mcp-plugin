interface PriorityIconProps {
  priority: { name: string }
}

interface PriorityMeta {
  color: string
  symbol: string
}

function priorityMeta(name: string): PriorityMeta {
  switch (name) {
    case 'Highest': return { color: '#ef4444', symbol: '⬆⬆' }
    case 'High':    return { color: '#f97316', symbol: '⬆' }
    case 'Medium':  return { color: '#eab308', symbol: '⬛' }
    case 'Low':     return { color: '#22c55e', symbol: '⬇' }
    case 'Lowest':  return { color: '#6b7280', symbol: '⬇⬇' }
    default:        return { color: '#6b7280', symbol: '⬛' }
  }
}

export function PriorityIcon({ priority }: PriorityIconProps) {
  const { color, symbol } = priorityMeta(priority.name)
  return (
    <span
      title={priority.name}
      style={{
        color,
        fontSize: '12px',
        lineHeight: 1,
        userSelect: 'none',
        flexShrink: 0,
      }}
    >
      {symbol}
    </span>
  )
}
