import type { FormFieldConfig } from '../types/aiCheck.types'
import { TechStackInput } from './TechStackInput'
import { inputStyle } from '../../../constants/styles'

interface FormFieldProps {
  field: FormFieldConfig
  value: unknown
  activeHint: string | null
  onActiveHintChange: (key: string | null) => void
  onChange: (value: unknown) => void
  onListItemChange: (index: number, value: string) => void
}

export function FormField({ field: f, value, activeHint, onActiveHintChange, onChange, onListItemChange }: FormFieldProps) {
  const isHintActive = activeHint === f.key

  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
        <label style={{ fontSize: 12, color: '#64748B' }}>{f.label}</label>
        {(f.tips || f.example) && (
          <button
            type="button"
            onClick={() => onActiveHintChange(isHintActive ? null : f.key)}
            style={{
              background: isHintActive ? 'rgba(78,205,196,0.15)' : 'rgba(255,255,255,0.05)',
              border: `1px solid ${isHintActive ? 'rgba(78,205,196,0.4)' : 'rgba(255,255,255,0.1)'}`,
              borderRadius: '50%',
              width: 18,
              height: 18,
              fontSize: 10,
              color: isHintActive ? '#4ECDC4' : '#475569',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontFamily: "'Courier New', monospace",
              padding: 0,
              flexShrink: 0,
            }}
          >
            ?
          </button>
        )}
      </div>
      {isHintActive && (f.tips || f.example) && (
        <div style={{
          background: 'rgba(78,205,196,0.04)',
          border: '1px solid rgba(78,205,196,0.2)',
          borderRadius: 8,
          padding: '10px 12px',
          marginBottom: 8,
          animation: 'slideIn 0.2s ease',
        }}>
          {f.tips && f.tips.length > 0 && (
            <div style={{ marginBottom: f.example ? 8 : 0 }}>
              <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 2, marginBottom: 6 }}>💡 작성 팁</div>
              {f.tips.map((tip, ti) => (
                <div key={ti} style={{ fontSize: 12, color: '#64748B', marginBottom: 3, display: 'flex', gap: 6 }}>
                  <span style={{ color: '#4ECDC4', flexShrink: 0 }}>·</span>
                  <span>{tip}</span>
                </div>
              ))}
            </div>
          )}
          {f.example && (
            <div>
              <div style={{ fontSize: 10, color: '#A78BFA', letterSpacing: 2, marginBottom: 6 }}>✏️ 예시 답변</div>
              <div style={{ fontSize: 12, color: '#475569', lineHeight: 1.6, fontStyle: 'italic' }}>{f.example}</div>
            </div>
          )}
        </div>
      )}
      {f.type === 'text' && (
        <input
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={f.placeholder}
          style={inputStyle}
        />
      )}
      {f.type === 'textarea' && (
        <textarea
          value={(value as string) ?? ''}
          onChange={(e) => onChange(e.target.value)}
          placeholder={f.placeholder}
          rows={f.rows ?? 5}
          style={{ ...inputStyle, resize: 'vertical' }}
        />
      )}
      {f.type === 'list' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {Array.from({ length: f.count ?? 0 }).map((_, i) => (
            <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ fontSize: 12, color: '#334155', minWidth: 16 }}>{i + 1}.</span>
              <input
                value={((value as string[] | undefined) ?? [])[i] ?? ''}
                onChange={(e) => onListItemChange(i, e.target.value)}
                placeholder={f.placeholder}
                style={{ ...inputStyle, flex: 1 }}
              />
            </div>
          ))}
        </div>
      )}
      {f.type === 'tag-search' && (
        <TechStackInput
          value={(value as string[] | undefined) ?? []}
          onChange={(val: string[]) => onChange(val)}
          placeholder={f.placeholder}
        />
      )}
    </div>
  )
}
