export const PASS_THRESHOLD = 70

export function getGrade(score: number): string {
  if (score >= 90) return 'S'
  if (score >= 80) return 'A'
  if (score >= 70) return 'B'
  if (score >= 60) return 'C'
  return 'D'
}

export const GRADE_COLORS: Record<string, string> = {
  S: '#FFD700',
  A: '#4ECDC4',
  B: '#45B7D1',
  C: '#FFA07A',
  D: '#FF6B6B',
}
