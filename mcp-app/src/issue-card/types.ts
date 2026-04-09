export interface Issue {
  key: string
  summary: string
  status: { name: string; category?: string; colorName?: string; categoryName?: string }
  priority: { name: string }
  issue_type: { name: string }
  assignee: { name: string; displayName: string } | null
  reporter: { name: string; displayName: string } | null
  description: string
  comments: Comment[]
  created: string
  updated: string
}

export interface Comment {
  author: string
  body: string
  created: string
}

export interface StructuredContent {
  currentUser: { name: string; displayName?: string }
  issues: Issue[]
  totalCount: number
  baseUrl: string
}
