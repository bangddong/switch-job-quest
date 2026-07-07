import { fetchResume, saveResume } from '@/lib/apiClient'
import type { Resume } from '@/types/api.types'

export async function loadResume(): Promise<Resume | null> {
  return fetchResume()
}

export async function updateResume(content: string): Promise<Resume> {
  return saveResume(content)
}
