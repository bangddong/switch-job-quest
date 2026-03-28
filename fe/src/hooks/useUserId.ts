import { useMemo } from 'react'

const STORAGE_KEY = 'devquest-uid'

function generateUserId(): string {
  return 'user-' + Math.random().toString(36).slice(2, 10)
}

export function useUserId(): string {
  return useMemo(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) return stored

      const id = generateUserId()
      localStorage.setItem(STORAGE_KEY, id)
      return id
    } catch {
      return 'user-demo'
    }
  }, [])
}
