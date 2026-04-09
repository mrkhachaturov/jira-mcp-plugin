import { issueTypeIcons } from '../icons'

interface Props {
  name: string
  size?: number
}

export function IssueTypeIcon({ name, size = 16 }: Props) {
  const src = issueTypeIcons[name]

  if (!src) {
    return (
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: '3px',
        background: 'var(--text-secondary)',
        color: 'white',
        fontSize: size * 0.6,
        fontWeight: 700,
        flexShrink: 0,
      }}>
        {name.charAt(0).toUpperCase()}
      </span>
    )
  }

  return (
    <img
      src={src}
      alt={name}
      width={size}
      height={size}
      style={{ flexShrink: 0 }}
    />
  )
}
