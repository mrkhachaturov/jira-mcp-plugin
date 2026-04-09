import { t } from '../i18n'

export function Empty() {
  return (
    <div style={{ padding: '24px', textAlign: 'center', color: 'var(--text-secondary)' }}>
      {t('noIssues')}
    </div>
  )
}
