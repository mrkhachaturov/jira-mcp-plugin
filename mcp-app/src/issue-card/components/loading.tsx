import { t } from '../i18n'

export function Loading() {
  return (
    <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-secondary)' }}>
      {t('loading')}
    </div>
  )
}
