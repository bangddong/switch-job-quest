export type FieldType = 'text' | 'textarea' | 'list' | 'tag-search'

export interface FormFieldConfig {
  key: string
  label: string
  type: FieldType
  count?: number
  placeholder: string
  rows?: number
  tips?: string[]       // NEW: writing tips
  example?: string      // NEW: example answer
}

export interface AiFormConfig {
  label: string
  endpoint: string
  fields: FormFieldConfig[]
  description?: string
  transform: (values: Record<string, unknown>) => Record<string, unknown>
}

export type AiFormsMap = Record<string, AiFormConfig>

export interface FallbackQuestion {
  id: string
  category: string
  question: string
  difficulty: string
}
