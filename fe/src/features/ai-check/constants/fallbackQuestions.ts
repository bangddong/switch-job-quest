import type { FallbackQuestion } from '../types/aiCheck.types'

export const FALLBACK_QUESTIONS: FallbackQuestion[] = [
  { id: 'q1', category: 'DB', question: '데이터베이스 인덱스의 내부 동작 원리와 잘못 사용했을 때의 문제점을 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q2', category: 'JVM', question: 'Garbage Collection의 종류와 Stop-The-World가 발생하는 이유를 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q3', category: '네트워크', question: 'HTTPS 통신에서 TLS Handshake 과정을 상세하게 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q4', category: '설계', question: 'MSA와 Monolithic 아키텍처의 장단점과 각각 어떤 상황에서 선택해야 하는지 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q5', category: 'DB', question: 'Optimistic Locking과 Pessimistic Locking의 차이와 사용 상황을 설명해주세요.', difficulty: 'HARD' },
  { id: 'q6', category: 'Spring', question: '@Transactional 어노테이션의 내부 동작 원리와 주의해야 할 점들을 설명해주세요.', difficulty: 'HARD' },
  { id: 'q7', category: 'OS', question: 'Thread와 Process의 차이, Thread-safe한 코드를 작성하는 방법을 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q8', category: 'Java', question: 'hashCode()와 equals()의 역할과 올바르게 구현하는 방법을 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q9', category: '설계', question: 'Slow Query를 발견했을 때 원인 분석부터 해결까지 과정을 설명해주세요.', difficulty: 'MEDIUM' },
  { id: 'q10', category: '네트워크', question: '웹 브라우저에 https://www.google.com 입력 후 발생하는 과정을 상세하게 설명해주세요.', difficulty: 'HARD' },
]
