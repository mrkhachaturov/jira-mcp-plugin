const messages: Record<string, Record<string, string>> = {
  en: {
    loading: 'Loading…',
    noIssues: 'No issues found.',
    failedToLoad: 'Failed to load issue data.',
    showing: 'Showing',
    of: 'of',
    assignee: 'Assignee',
    reporter: 'Reporter',
    updated: 'Updated',
    unassigned: 'Unassigned',
    comments: 'Comments',
    transition: 'Transition',
    transitionLoading: 'Loading…',
    transitionFailed: 'Transition failed.',
    transitionLoadFailed: 'Failed to load transitions.',
    noTransitions: 'No transitions available.',
    comment: 'Comment',
    commentPlaceholder: 'Add a comment…',
    commentSend: 'Send',
    commentSending: 'Sending…',
    commentCancel: 'Cancel',
    commentFailed: 'Failed to add comment.',
    assignToMe: 'Assign to me',
    assigning: 'Assigning…',
    assignFailed: 'Failed to assign.',
    collapse: '▾',
    liveOn: 'Live',
    liveOff: 'Live',
    liveTooltipOn: 'Auto-refresh every 30s — click to pause',
    liveTooltipOff: 'Click to auto-refresh every 30s',
  },
  ru: {
    loading: 'Загрузка…',
    noIssues: 'Задачи не найдены.',
    failedToLoad: 'Не удалось загрузить данные.',
    showing: 'Показано',
    of: 'из',
    assignee: 'Исполнитель',
    reporter: 'Автор',
    updated: 'Обновлено',
    unassigned: 'Не назначен',
    comments: 'Комментарии',
    transition: 'Перевести',
    transitionLoading: 'Загрузка…',
    transitionFailed: 'Не удалось перевести.',
    transitionLoadFailed: 'Не удалось загрузить переходы.',
    noTransitions: 'Нет доступных переходов.',
    comment: 'Комментарий',
    commentPlaceholder: 'Добавить комментарий…',
    commentSend: 'Отправить',
    commentSending: 'Отправка…',
    commentCancel: 'Отмена',
    commentFailed: 'Не удалось добавить комментарий.',
    assignToMe: 'Назначить на меня',
    assigning: 'Назначение…',
    assignFailed: 'Не удалось назначить.',
    collapse: '▾',
    liveOn: 'Live',
    liveOff: 'Live',
    liveTooltipOn: 'Авто-обновление каждые 30с — нажмите для паузы',
    liveTooltipOff: 'Нажмите для авто-обновления каждые 30с',
  },
}

function getLocale(): string {
  const lang = document.documentElement.lang || navigator.language || 'en'
  return lang.split('-')[0]
}

export function t(key: string): string {
  const locale = getLocale()
  return messages[locale]?.[key] ?? messages.en[key] ?? key
}
