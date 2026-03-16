import type { AiFormsMap } from '../types/aiCheck.types'

export const AI_FORMS: AiFormsMap = {
  '1-2': {
    label: '이직 동기 에세이',
    endpoint: 'career-essay',
    fields: [
      { key: 'dissatisfactions', label: '현 직장 불만족 사항 3가지', type: 'list', count: 3, placeholder: '예: 기술 부채가 많아 성장이 멈춘 느낌입니다' },
      { key: 'goals', label: '이직 후 목표 3가지', type: 'list', count: 3, placeholder: '예: MSA 아키텍처를 직접 설계해보고 싶습니다' },
      { key: 'fiveYearVision', label: '5년 후 나의 모습 (200자)', type: 'textarea', placeholder: '5년 뒤 어떤 개발자가 되고 싶은지 구체적으로 작성해주세요...' },
    ],
    transform: (v) => v,
  },
  '2-2': {
    label: '기술 블로그 검사',
    endpoint: 'tech-blog',
    fields: [
      { key: 'techTopic', label: '기술 주제', type: 'text', placeholder: '예: JVM G1GC 튜닝' },
      { key: 'title', label: '포스트 제목', type: 'text', placeholder: '예: 실무에서 G1GC를 50% 개선한 방법' },
      { key: 'content', label: '본문 (1500자 이상)', type: 'textarea', placeholder: '블로그 본문을 붙여넣어 주세요...', rows: 8 },
    ],
    transform: (v) => ({ ...v, questId: '2-2' }),
  },
  '2-3': {
    label: '시스템 설계 평가',
    endpoint: 'system-design',
    fields: [
      { key: 'problemStatement', label: '설계 문제', type: 'text', placeholder: '예: 하루 100만 주문을 처리하는 배달앱 주문 시스템 설계' },
      { key: 'architectureDescription', label: '아키텍처 설명', type: 'textarea', placeholder: '설계한 시스템을 상세하게 설명해주세요...', rows: 6 },
      { key: 'considerations', label: '고려한 사항 4가지', type: 'list', count: 4, placeholder: '예: Auto Scaling으로 트래픽 급증에 대응' },
    ],
    transform: (v) => ({ ...v, questId: '2-3' }),
  },
  '3-2': {
    label: 'JD 역분석',
    endpoint: 'jd-analysis',
    fields: [
      { key: 'companyName', label: '회사명', type: 'text', placeholder: '예: 토스' },
      { key: 'jobDescription', label: '채용공고 원문', type: 'textarea', placeholder: '채용공고를 붙여넣어 주세요...', rows: 6 },
      { key: 'userSkills', label: '내 보유 기술 (5가지)', type: 'list', count: 5, placeholder: '예: Spring Boot' },
      { key: 'userExperiences', label: '경력 요약 (3가지)', type: 'list', count: 3, placeholder: '예: 결제 API 개발 및 성능 개선 (2년)' },
    ],
    transform: (v) => v,
  },
  '4-1': {
    label: '이력서 STAR 검토',
    endpoint: 'resume',
    fields: [
      { key: 'targetCompany', label: '지원 회사명', type: 'text', placeholder: '예: 카카오페이' },
      { key: 'targetJd', label: 'JD 핵심 요구사항', type: 'textarea', placeholder: '채용공고 주요 내용...', rows: 4 },
      { key: 'resumeContent', label: '이력서 내용', type: 'textarea', placeholder: '이력서 전문을 붙여넣어 주세요...', rows: 8 },
    ],
    transform: (v) => v,
  },
  '5-1': {
    label: '인성 면접 연습',
    endpoint: 'personality-interview',
    fields: [
      { key: 'question', label: '면접 질문', type: 'text', placeholder: '예: 납득이 가지 않는 업무를 맡게 되었을 때 어떻게 하시겠습니까?' },
      { key: 'answer', label: '내 답변', type: 'textarea', placeholder: '실제 면접처럼 구체적으로 답변해주세요...', rows: 6 },
    ],
    transform: (v) => v,
  },
}
