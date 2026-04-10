import type { CSSProperties } from 'react'
import { useAuth } from '@/hooks/useAuth'

const acts = [
  { label: 'ACT I', icon: '⚔️', title: '기초 체력 다지기' },
  { label: 'ACT II', icon: '🛠️', title: '무기 제작' },
  { label: 'ACT III', icon: '🔍', title: '전장 분석' },
  { label: 'ACT IV', icon: '🎯', title: '최종 결전 준비' },
  { label: 'ACT V', icon: '👑', title: '취뽀 달성' },
]

const styles: Record<string, CSSProperties> = {
  root: {
    minHeight: '100vh',
    overflowY: 'auto',
    background: '#060610',
    fontFamily: "'Courier New', monospace",
    color: '#F8FAFC',
  },
  inner: {
    maxWidth: 480,
    margin: '0 auto',
    padding: '72px 24px 80px',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 0,
  },
  title: {
    margin: 0,
    fontSize: 36,
    fontWeight: 'bold',
    color: '#4ECDC4',
    letterSpacing: 2,
  },
  titleSword: {
    marginRight: 8,
  },
  hookBlock: {
    marginTop: 40,
    textAlign: 'center',
    lineHeight: 1.9,
  },
  hookLine1: {
    margin: 0,
    fontSize: 16,
    color: '#F8FAFC',
  },
  hookLine2: {
    margin: 0,
    fontSize: 16,
    color: '#475569',
  },
  coreMessage: {
    marginTop: 28,
    textAlign: 'center',
    lineHeight: 1.9,
  },
  coreLine1: {
    margin: 0,
    fontSize: 16,
    color: '#F8FAFC',
  },
  coreLine2: {
    margin: 0,
    fontSize: 16,
    color: '#F8FAFC',
  },
  divider: {
    width: '100%',
    height: 1,
    background: 'rgba(255,255,255,0.08)',
    margin: '40px 0',
  },
  actList: {
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  actItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: '10px 0',
  },
  actLabel: {
    fontSize: 12,
    color: '#475569',
    minWidth: 64,
    letterSpacing: 1,
  },
  actIcon: {
    fontSize: 18,
    width: 28,
    textAlign: 'center',
  },
  actTitle: {
    fontSize: 15,
    color: '#F8FAFC',
  },
  ctaButton: {
    marginTop: 48,
    width: '100%',
    padding: '16px 0',
    background: 'linear-gradient(135deg, #4ECDC4, #2DD4BF)',
    border: 'none',
    borderRadius: 10,
    color: '#060610',
    fontSize: 15,
    fontWeight: 'bold',
    fontFamily: "'Courier New', monospace",
    letterSpacing: 1,
    cursor: 'pointer',
  },
  subCopy: {
    marginTop: 16,
    fontSize: 12,
    color: '#475569',
    textAlign: 'center',
  },
}

export function LoginPage() {
  const { loginWithGithub } = useAuth()

  return (
    <div style={styles.root}>
      <div style={styles.inner}>
        <h1 style={styles.title}>
          <span style={styles.titleSword}>⚔️</span>DevQuest
        </h1>

        <div style={styles.hookBlock}>
          <p style={styles.hookLine1}>이직을 앞둔 개발자라면,</p>
          <p style={styles.hookLine2}>누구나 느끼는 그 막막함.</p>
        </div>

        <div style={styles.coreMessage}>
          <p style={styles.coreLine1}>퀘스트를 클리어하듯,</p>
          <p style={styles.coreLine2}>이직을 완성하세요.</p>
        </div>

        <div style={styles.divider} />

        <div style={styles.actList}>
          {acts.map(({ label, icon, title }) => (
            <div key={label} style={styles.actItem}>
              <span style={styles.actLabel}>{label}</span>
              <span style={styles.actIcon}>{icon}</span>
              <span style={styles.actTitle}>{title}</span>
            </div>
          ))}
        </div>

        <div style={styles.divider} />

        <button style={styles.ctaButton} onClick={loginWithGithub}>
          GitHub로 모험 시작하기
        </button>

        <p style={styles.subCopy}>AI가 당신의 실력을 평가합니다</p>
      </div>
    </div>
  )
}
