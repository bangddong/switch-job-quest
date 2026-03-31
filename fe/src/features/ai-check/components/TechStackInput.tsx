import { useState } from 'react'

const TECH_STACKS: string[] = [
  // 언어
  'Java', 'Kotlin', 'Python', 'Go', 'TypeScript', 'JavaScript', 'C++', 'C#', 'Swift', 'Rust', 'Scala', 'PHP', 'Ruby',
  // 프레임워크(백)
  'Spring Boot', 'Spring', 'Django', 'FastAPI', 'Express', 'NestJS', 'Gin', 'Fiber', 'Rails', 'Laravel', 'gRPC',
  // 프레임워크(프론트)
  'React', 'Next.js', 'Vue.js', 'Nuxt.js', 'Angular', 'Svelte', 'SvelteKit',
  // DB
  'MySQL', 'PostgreSQL', 'MongoDB', 'Redis', 'Elasticsearch', 'Oracle', 'DynamoDB', 'Cassandra', 'SQLite',
  // 인프라
  'Docker', 'Kubernetes', 'AWS', 'GCP', 'Azure', 'Terraform', 'Ansible', 'Jenkins', 'GitHub Actions', 'ArgoCD', 'Grafana', 'Prometheus',
  // 도구
  'Git', 'Jira', 'Confluence', 'Figma', 'Kafka', 'RabbitMQ', 'GraphQL', 'REST API',
]

const CAREER_OPTIONS = ['6개월', '1년', '2년', '3년', '5년+']

interface TechStackInputProps {
  value: string[]
  onChange: (value: string[]) => void
  placeholder?: string
}

export function TechStackInput({ value, onChange, placeholder }: TechStackInputProps) {
  const [query, setQuery] = useState('')
  const [pendingStack, setPendingStack] = useState<string | null>(null)
  const [showDropdown, setShowDropdown] = useState(false)

  const isMaxReached = value.length >= 10

  const addedNames = value.map((v) => v.split(':')[0])

  const filtered = query.trim().length === 0
    ? []
    : TECH_STACKS.filter((s) =>
        s.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 8)

  const handleQueryChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setQuery(e.target.value)
    setPendingStack(null)
    setShowDropdown(true)
  }

  const handleStackSelect = (stack: string) => {
    if (addedNames.includes(stack)) return
    setPendingStack(stack)
    setShowDropdown(false)
    setQuery('')
  }

  const handleCareerSelect = (career: string) => {
    if (!pendingStack) return
    onChange([...value, `${pendingStack}:${career}`])
    setPendingStack(null)
  }

  const handleRemove = (index: number) => {
    onChange(value.filter((_, i) => i !== index))
  }

  const handleBlur = () => {
    setTimeout(() => {
      setShowDropdown(false)
    }, 150)
  }

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  }

  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: isMaxReached ? 'rgba(10,14,26,0.5)' : '#0A0E1A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 8,
    padding: '9px 13px',
    color: isMaxReached ? '#475569' : '#F1F5F9',
    fontSize: 13,
    outline: 'none',
    boxSizing: 'border-box',
    fontFamily: "'Courier New', monospace",
    lineHeight: 1.6,
    cursor: isMaxReached ? 'not-allowed' : 'text',
  }

  const dropdownStyle: React.CSSProperties = {
    background: '#0F172A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 8,
    overflow: 'hidden',
    marginTop: 2,
  }

  const dropdownItemBase: React.CSSProperties = {
    padding: '8px 13px',
    fontSize: 13,
    fontFamily: "'Courier New', monospace",
    cursor: 'pointer',
    display: 'block',
    width: '100%',
    textAlign: 'left',
    border: 'none',
    background: 'transparent',
  }

  const badgeContainerStyle: React.CSSProperties = {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 6,
  }

  const badgeStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    background: 'rgba(78,205,196,0.1)',
    border: '1px solid rgba(78,205,196,0.3)',
    borderRadius: 6,
    padding: '4px 10px',
    fontSize: 12,
    color: '#4ECDC4',
    fontFamily: "'Courier New', monospace",
  }

  const removeButtonStyle: React.CSSProperties = {
    background: 'none',
    border: 'none',
    color: '#4ECDC4',
    cursor: 'pointer',
    fontSize: 13,
    lineHeight: 1,
    padding: 0,
    display: 'flex',
    alignItems: 'center',
  }

  const subDropdownStyle: React.CSSProperties = {
    background: '#0F172A',
    border: '1px solid rgba(78,205,196,0.2)',
    borderRadius: 8,
    padding: '10px 13px',
    marginTop: 4,
  }

  const subLabelStyle: React.CSSProperties = {
    fontSize: 12,
    color: '#4ECDC4',
    fontFamily: "'Courier New', monospace",
    marginBottom: 8,
    display: 'block',
  }

  const careerOptionsStyle: React.CSSProperties = {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 6,
  }

  const careerOptionButtonStyle: React.CSSProperties = {
    background: 'rgba(78,205,196,0.08)',
    border: '1px solid rgba(78,205,196,0.25)',
    borderRadius: 6,
    padding: '5px 12px',
    fontSize: 12,
    color: '#F1F5F9',
    fontFamily: "'Courier New', monospace",
    cursor: 'pointer',
  }

  const hintStyle: React.CSSProperties = {
    fontSize: 11,
    color: '#334155',
    fontFamily: "'Courier New', monospace",
  }

  return (
    <div style={containerStyle}>
      {value.length > 0 && (
        <div style={badgeContainerStyle}>
          {value.map((tag, i) => {
            const [name, career] = tag.split(':')
            return (
              <span key={i} style={badgeStyle}>
                {name} · {career}
                <button
                  type="button"
                  onClick={() => handleRemove(i)}
                  style={removeButtonStyle}
                  aria-label={`${name} 제거`}
                >
                  ×
                </button>
              </span>
            )
          })}
        </div>
      )}

      <div style={{ position: 'relative' }}>
        <input
          value={query}
          onChange={handleQueryChange}
          onFocus={() => query.trim().length > 0 && setShowDropdown(true)}
          onBlur={handleBlur}
          placeholder={isMaxReached ? '최대 10개 선택됨' : (placeholder ?? '기술 검색...')}
          disabled={isMaxReached}
          style={inputStyle}
        />

        {showDropdown && filtered.length > 0 && !isMaxReached && (
          <div style={{ ...dropdownStyle, position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 10 }}>
            {filtered.map((stack) => {
              const alreadyAdded = addedNames.includes(stack)
              return (
                <button
                  key={stack}
                  type="button"
                  onMouseDown={() => handleStackSelect(stack)}
                  disabled={alreadyAdded}
                  style={{
                    ...dropdownItemBase,
                    color: alreadyAdded ? '#334155' : '#F1F5F9',
                    cursor: alreadyAdded ? 'default' : 'pointer',
                  }}
                >
                  {stack}
                  {alreadyAdded && (
                    <span style={{ marginLeft: 6, color: '#334155', fontSize: 11 }}>추가됨</span>
                  )}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {pendingStack && (
        <div style={subDropdownStyle}>
          <span style={subLabelStyle}>{pendingStack} 경력을 선택하세요</span>
          <div style={careerOptionsStyle}>
            {CAREER_OPTIONS.map((career) => (
              <button
                key={career}
                type="button"
                onClick={() => handleCareerSelect(career)}
                style={careerOptionButtonStyle}
              >
                {career}
              </button>
            ))}
          </div>
        </div>
      )}

      {!isMaxReached && (
        <span style={hintStyle}>{value.length}/10개 선택됨</span>
      )}
    </div>
  )
}
