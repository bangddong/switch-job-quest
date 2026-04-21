import type { AiFormsMap } from '../types/aiCheck.types'

export const AI_FORMS: AiFormsMap = {
  '1-1': {
    label: '기술 스택 AI 진단',
    endpoint: 'skill-assessment',
    fields: [
      {
        key: 'skills',
        label: '보유 기술 스택 (최대 10개)',
        type: 'tag-search',
        placeholder: '기술 검색...',
        tips: [
          '기술명만 입력하세요 (예: Kotlin, Spring Boot, Redis)',
          '현재 실무에서 사용 중인 기술 위주로 입력하세요',
          '5~8개 입력이 가장 정확한 진단을 받을 수 있어요',
        ],
        example: 'Kotlin, Spring Boot, JPA, MySQL, Redis, Docker',
      },
      {
        key: 'targetRole',
        label: '목표 포지션',
        type: 'text',
        placeholder: '예: 시니어 백엔드 개발자',
        tips: [
          '구체적일수록 더 정확한 갭 분석이 가능해요',
          '시니어/주니어 레벨도 함께 명시해주세요',
        ],
        example: '시니어 백엔드 개발자 (Java/Kotlin)',
      },
    ],
    transform: (v) => v,
  },
  '1-2': {
    label: '이직 동기 에세이',
    endpoint: 'career-essay',
    fields: [
      {
        key: 'dissatisfactions',
        label: '현 직장 불만족 사항 3가지',
        type: 'list',
        count: 3,
        placeholder: '예: 기술 부채가 많아 성장이 멈춘 느낌입니다',
        tips: [
          '감정적 표현보다 구체적 상황으로 작성하세요',
          '기술적 성장 제한, 문화적 미스매치, 커리어 방향성 등을 활용하세요',
        ],
        example: '레거시 코드 비중이 높아 새로운 기술을 적용할 기회가 없습니다',
      },
      {
        key: 'goals',
        label: '이직 후 목표 3가지',
        type: 'list',
        count: 3,
        placeholder: '예: MSA 아키텍처를 직접 설계해보고 싶습니다',
        tips: [
          '달성 가능하고 구체적인 목표를 작성하세요',
          '기술적 목표와 커리어 목표를 함께 포함하면 좋아요',
        ],
        example: 'MSA 아키텍처를 직접 설계하고 운영해보고 싶습니다',
      },
      {
        key: 'fiveYearVision',
        label: '5년 후 나의 모습 (200자)',
        type: 'textarea',
        placeholder: '5년 뒤 어떤 개발자가 되고 싶은지 구체적으로 작성해주세요...',
        tips: [
          '5년 후의 역할, 전문성, 영향력을 구체적으로 묘사하세요',
          '회사 규모나 포지션보다 어떤 문제를 해결하는 사람이 되고 싶은지 서술하세요',
          '200자 이상 작성해야 높은 점수를 받을 수 있어요',
        ],
        example:
          '5년 후에는 대용량 트래픽을 처리하는 결제 시스템의 리드 엔지니어로서, 주니어 개발자들의 멘토 역할을 하며 팀의 기술적 방향성을 이끌어가고 싶습니다.',
      },
    ],
    transform: (v) => v,
  },
  '2-2': {
    label: '기술 블로그 검사',
    endpoint: 'tech-blog',
    fields: [
      {
        key: 'techTopic',
        label: '기술 주제',
        type: 'text',
        placeholder: '예: JVM G1GC 튜닝',
        tips: [
          'Java/Kotlin 내부 동작, DB 최적화, 분산 시스템 등 깊이 있는 주제를 선택하세요',
          '실무에서 겪은 문제와 해결 과정이 가장 좋은 점수를 받아요',
        ],
        example: 'JVM G1GC 튜닝으로 Full GC 제거하기',
      },
      { key: 'title', label: '포스트 제목', type: 'text', placeholder: '예: 실무에서 G1GC를 50% 개선한 방법' },
      {
        key: 'content',
        label: '본문 (1500자 이상)',
        type: 'textarea',
        placeholder: '블로그 본문을 붙여넣어 주세요...',
        rows: 8,
        tips: [
          '1500자 이상 작성해야 통과 가능성이 높아요',
          '코드 예제를 반드시 포함하세요',
          '문제 상황 → 원인 분석 → 해결 방법 → 결과 구조로 작성하면 좋아요',
          '성능 개선 수치 (예: 응답시간 50% 개선)가 있으면 높은 점수를 받아요',
        ],
      },
    ],
    transform: (v) => ({ ...v, questId: '2-2' }),
  },
  '2-3': {
    label: '시스템 설계 평가',
    endpoint: 'system-design',
    fields: [
      {
        key: 'problemStatement',
        label: '설계 문제',
        type: 'text',
        placeholder: '예: 하루 100만 주문을 처리하는 배달앱 주문 시스템 설계',
        tips: ['규모를 명시하세요 (예: 일 100만 건, MAU 500만)'],
        example: '하루 100만 주문을 처리하는 배달앱 주문 시스템 설계',
      },
      {
        key: 'architectureDescription',
        label: '아키텍처 설명',
        type: 'textarea',
        placeholder: '설계한 시스템을 상세하게 설명해주세요...',
        rows: 6,
        tips: [
          '핵심 컴포넌트 (DB, 캐시, 메시지큐 등)와 역할을 설명하세요',
          '데이터 흐름을 순서대로 설명하면 명확해요',
          'MSA vs 모놀리식 선택 근거도 포함하면 좋아요',
        ],
      },
      {
        key: 'considerations',
        label: '고려한 사항 4가지',
        type: 'list',
        count: 4,
        placeholder: '예: Auto Scaling으로 트래픽 급증에 대응',
        tips: ['성능, 확장성, 가용성, 일관성 4가지 관점을 각각 하나씩 작성하면 좋아요'],
        example: 'Redis 캐시로 DB 부하를 80% 감소',
      },
    ],
    transform: (v) => ({ ...v, questId: '2-3' }),
  },
  '3-2': {
    label: 'JD 역분석',
    endpoint: 'jd-analysis',
    fields: [
      { key: 'companyName', label: '회사명', type: 'text', placeholder: '예: 토스' },
      {
        key: 'jobDescription',
        label: '채용공고 원문',
        type: 'textarea',
        placeholder: '채용공고를 붙여넣어 주세요...',
        rows: 6,
        tips: [
          '채용공고 전문을 붙여넣을수록 더 정확한 분석이 가능해요',
          '우대사항까지 포함해주세요',
        ],
      },
      {
        key: 'userSkills',
        label: '내 보유 기술 (5가지)',
        type: 'list',
        count: 5,
        placeholder: '예: Spring Boot',
        tips: [
          '실무 경험이 있는 기술 위주로 입력하세요',
          '버전이나 사용 기간을 괄호로 추가하면 더 좋아요',
        ],
        example: 'Spring Boot (3년), JPA, MySQL, Redis',
      },
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
      {
        key: 'resumeContent',
        label: '이력서 내용',
        type: 'textarea',
        placeholder: '이력서 전문을 붙여넣어 주세요...',
        rows: 8,
        tips: [
          'STAR 형식 (상황-과제-행동-결과)으로 작성된 경험이 높은 점수를 받아요',
          '수치로 성과를 표현하세요 (예: API 응답속도 40% 개선)',
          'JD의 요구사항과 매칭되는 경험을 앞에 배치하세요',
        ],
      },
    ],
    transform: (v) => v,
  },
  '4-BOSS': {
    label: '지원 패키지 최종 점검',
    endpoint: 'boss-package',
    fields: [
      { key: 'resumeContent', label: '최종 이력서', type: 'textarea', placeholder: '이력서 전문을 붙여넣어 주세요...', rows: 8 },
      { key: 'githubUrl', label: 'GitHub 프로필 URL', type: 'text', placeholder: 'https://github.com/username' },
      { key: 'blogUrl', label: '기술 블로그 URL (선택)', type: 'text', placeholder: 'https://your-blog.com' },
      { key: 'targetPosition', label: '목표 포지션', type: 'text', placeholder: '예: 시니어 백엔드 개발자' },
    ],
    transform: (v) => v,
  },
  '1-BOSS': {
    label: '개발자 클래스 판별',
    endpoint: 'developer-class',
    fields: [],
    description: '1-1, 1-2 결과를 종합하여 AI가 개발자 클래스를 판별합니다. 별도 입력 없이 바로 분석을 시작하세요.',
    transform: () => ({}),
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
