import { useState } from 'react'
import type { AiEvaluationResult, BossPackageResult } from '@/types/api.types'
import type { AiFormConfig } from '../types/aiCheck.types'
import { submitAiCheck, submitBossPackage } from '../api/aiCheckApi'

interface UseAiCheckFormParams {
  questId: string
  cfg: AiFormConfig
  onResult: (result: AiEvaluationResult | BossPackageResult) => void
  onSubmit?: (values: Record<string, unknown>) => void
  initialValues?: Record<string, unknown>
}

export function useAiCheckForm({ questId, cfg, onResult, onSubmit, initialValues }: UseAiCheckFormParams) {
  const [values, setValues] = useState<Record<string, unknown>>(initialValues ?? {})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeHint, setActiveHint] = useState<string | null>(null)

  const set = (key: string, value: unknown) =>
    setValues((prev) => ({ ...prev, [key]: value }))

  const setListItem = (key: string, index: number, value: string) =>
    setValues((prev) => {
      const arr = [...((prev[key] as string[] | undefined) ?? [])]
      arr[index] = value
      return { ...prev, [key]: arr }
    })

  const handleActiveHintChange = (key: string | null) => setActiveHint(key)

  const validate = (): string | null => {
    for (const field of cfg.fields) {
      const val = values[field.key]
      if (field.type === 'text' || field.type === 'textarea') {
        if (!val || !(val as string).trim()) {
          return `'${field.label}' 항목을 입력해주세요`
        }
      } else if (field.type === 'list') {
        const arr = (val as string[] | undefined) ?? []
        const hasNonEmptyItem = arr.some((s) => (typeof s === 'string' ? s : '').trim().length > 0)
        if (!hasNonEmptyItem) {
          return `'${field.label}' 항목을 최소 1개 이상 입력해주세요`
        }
      } else if (field.type === 'tag-search') {
        const arr = (val as string[] | undefined) ?? []
        if (arr.length === 0) {
          return `'${field.label}' 항목을 최소 1개 이상 선택해주세요`
        }
      }
    }
    return null
  }

  const handleSubmit = async () => {
    const validationError = validate()
    if (validationError) {
      setError(validationError)
      return
    }
    setLoading(true)
    setError('')
    try {
      onSubmit?.(values)
      const body = cfg.transform(values)
      const result =
        questId === '4-BOSS'
          ? await submitBossPackage(body)
          : await submitAiCheck(cfg.endpoint, body)
      onResult(result)
    } catch (e) {
      setError('서버 연결 오류: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setLoading(false)
    }
  }

  return {
    values,
    loading,
    error,
    activeHint,
    set,
    setListItem,
    setActiveHint: handleActiveHintChange,
    handleSubmit,
  }
}
