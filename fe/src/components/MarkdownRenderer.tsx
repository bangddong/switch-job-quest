import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface MarkdownRendererProps {
  content: string
  fontSize?: number
}

export function MarkdownRenderer({ content, fontSize = 13 }: MarkdownRendererProps) {
  return (
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        p: ({ children }) => (
          <p style={{ color: '#F1F5F9', fontSize, fontFamily: "'Courier New', monospace", lineHeight: 1.7, margin: '0 0 8px' }}>
            {children}
          </p>
        ),
        strong: ({ children }) => (
          <strong style={{ color: '#F8FAFC', fontWeight: 700 }}>{children}</strong>
        ),
        em: ({ children }) => (
          <em style={{ color: '#CBD5E1', fontStyle: 'italic' }}>{children}</em>
        ),
        // pre가 블록 코드를 감싸므로 code는 인라인 스타일만 담당.
        // pre 안의 code는 pre 스타일이 적용되고, 나머지는 인라인으로 렌더링됨.
        code: ({ children, ...props }: React.ComponentPropsWithoutRef<'code'>) => (
          <code style={{ background: 'rgba(255,255,255,0.08)', borderRadius: 4, padding: '1px 5px', fontSize: fontSize - 1, fontFamily: "'Courier New', monospace", color: '#4ECDC4' }} {...props}>
            {children}
          </code>
        ),
        pre: ({ children }) => (
          <pre style={{ background: '#0A0E1A', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, padding: '12px 14px', overflowX: 'auto', margin: '8px 0', maxWidth: '100%', boxSizing: 'border-box' }}>
            <code style={{ fontSize: 12, fontFamily: "'Courier New', monospace", color: '#A5F3FC', whiteSpace: 'pre', display: 'block', background: 'transparent', padding: 0, borderRadius: 0 }}>
              {(children as React.ReactElement<{ children?: React.ReactNode }>)?.props?.children ?? children}
            </code>
          </pre>
        ),
        ul: ({ children }) => (
          <ul style={{ color: '#F1F5F9', fontSize, fontFamily: "'Courier New', monospace", lineHeight: 1.7, margin: '4px 0 8px', paddingLeft: 20 }}>
            {children}
          </ul>
        ),
        ol: ({ children }) => (
          <ol style={{ color: '#F1F5F9', fontSize, fontFamily: "'Courier New', monospace", lineHeight: 1.7, margin: '4px 0 8px', paddingLeft: 20 }}>
            {children}
          </ol>
        ),
        li: ({ children }) => (
          <li style={{ marginBottom: 4, color: '#F1F5F9' }}>{children}</li>
        ),
        h1: ({ children }) => (
          <h1 style={{ color: '#4ECDC4', fontSize: fontSize + 4, fontFamily: "'Courier New', monospace", margin: '16px 0 8px' }}>{children}</h1>
        ),
        h2: ({ children }) => (
          <h2 style={{ color: '#4ECDC4', fontSize: fontSize + 2, fontFamily: "'Courier New', monospace", margin: '14px 0 6px' }}>{children}</h2>
        ),
        h3: ({ children }) => (
          <h3 style={{ color: '#60A5FA', fontSize: fontSize + 1, fontFamily: "'Courier New', monospace", margin: '12px 0 6px' }}>{children}</h3>
        ),
        blockquote: ({ children }) => (
          <blockquote style={{ borderLeft: '3px solid rgba(78,205,196,0.5)', paddingLeft: 12, margin: '8px 0', color: '#94A3B8' }}>
            {children}
          </blockquote>
        ),
        hr: () => (
          <hr style={{ border: 'none', borderTop: '1px solid rgba(255,255,255,0.08)', margin: '12px 0' }} />
        ),
      }}
    >
      {content}
    </ReactMarkdown>
  )
}
