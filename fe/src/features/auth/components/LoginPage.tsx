import { useAuth } from '@/hooks/useAuth'

export function LoginPage() {
  const { loginWithGithub } = useAuth()

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        gap: '24px',
        fontFamily: "'Courier New', monospace",
        color: '#F8FAFC',
      }}
    >
      <h1 style={{ margin: 0, fontSize: 32, color: '#4ECDC4' }}>DevQuest</h1>
      <p style={{ margin: 0, fontSize: 14, color: '#475569' }}>5년차 백엔드 개발자의 이직 준비 RPG</p>
      <button
        onClick={loginWithGithub}
        style={{
          background: 'linear-gradient(135deg, #4ECDC4, #2DD4BF)',
          border: 'none',
          borderRadius: 10,
          color: '#060610',
          fontSize: 14,
          fontWeight: 'bold',
          cursor: 'pointer',
          padding: '12px 28px',
          fontFamily: "'Courier New', monospace",
          letterSpacing: 1,
        }}
      >
        GitHub로 시작하기
      </button>
    </div>
  )
}
