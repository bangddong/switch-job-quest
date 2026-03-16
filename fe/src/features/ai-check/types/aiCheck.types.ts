export type FieldType = 'text' | 'textarea' | 'list'

export interface FormFieldConfig {
  key: string
  label: string
  type: FieldType
  count?: number
  placeholder: string
  rows?: number
}

export interface AiFormConfig {
  label: string
  endpoint: string
  fields: FormFieldConfig[]
  transform: (values: Record<string, unknown>) => Record<string, unknown>
}

export type AiFormsMap = Record<string, AiFormConfig>

export interface FallbackQuestion {
  id: string
  category: string
  question: string
  difficulty: string
}
