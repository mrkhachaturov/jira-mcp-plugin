import { priorityIcons } from '../icons/priorities'

interface PriorityIconProps {
  priority: { name: string }
  size?: number
}

export function PriorityIcon({ priority, size = 16 }: PriorityIconProps) {
  const src = priorityIcons[priority.name]

  if (!src) {
    return (
      <span
        title={priority.name}
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: size,
          height: size,
          borderRadius: '2px',
          background: 'var(--text-secondary)',
          color: 'white',
          fontSize: size * 0.6,
          fontWeight: 700,
          flexShrink: 0,
        }}
      >
        {priority.name.charAt(0)}
      </span>
    )
  }

  return (
    <img
      src={src}
      alt={priority.name}
      title={priority.name}
      width={size}
      height={size}
      style={{ flexShrink: 0 }}
    />
  )
}
