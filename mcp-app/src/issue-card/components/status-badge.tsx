interface StatusBadgeProps {
  status: { name: string; category?: string; colorName?: string }
}

/**
 * Maps Jira's statusCategory.colorName to CSS colors.
 * colorName comes directly from Jira's API — not hardcoded by us.
 */
function colorFromJira(colorName?: string): { bg: string; color: string; border: string } {
  switch (colorName) {
    case 'default':
    case 'blue-gray':
    case 'bluegray':
    case 'medium-gray':
      return { bg: '#dfe1e6', color: '#42526e', border: '#c1c7d0' }
    case 'inprogress':
    case 'yellow':
      return { bg: '#deebff', color: '#0747a6', border: '#b3d4ff' }
    case 'green':
      return { bg: '#e3fcef', color: '#006644', border: '#abf5d1' }
    case 'warm-red':
      return { bg: '#ffebe6', color: '#bf2600', border: '#ffbdad' }
    default:
      return { bg: '#dfe1e6', color: '#42526e', border: '#c1c7d0' }
  }
}

export function StatusBadge({ status }: StatusBadgeProps) {
  const style = colorFromJira(status.colorName)
  return (
    <span style={{
      display: 'inline-block',
      background: style.bg,
      color: style.color,
      border: `1px solid ${style.border}`,
      borderRadius: '3px',
      padding: '1px 6px',
      fontSize: '10px',
      fontWeight: 700,
      letterSpacing: '0.04em',
      textTransform: 'uppercase',
      whiteSpace: 'nowrap',
    }}>
      {status.name}
    </span>
  )
}
