import { useEffect } from 'react'
import { setToken } from '@/hooks/useAuth'

export function AuthCallback() {
  useEffect(() => {
    const code = new URLSearchParams(window.location.search).get('code')
    if (!code) return

    fetch('/api/v1/auth/github', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code }),
    })
      .then((res) => res.json())
      .then((json) => {
        if (json.result === 'SUCCESS' && json.data?.token) {
          setToken(json.data.token)
          window.location.href = '/'
        }
      })
      .catch(console.error)
  }, [])

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        fontFamily: "'Courier New', monospace",
        color: '#475569',
        fontSize: 14,
      }}
    >
      로그인 처리 중...
    </div>
  )
}
