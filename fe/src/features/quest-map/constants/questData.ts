import type { Act, QuestType, QuestTypeConfig } from '@/types/quest.types'

export const ACT_UNLOCK_THRESHOLD = 75

export const QUEST_TYPE_CONFIG: Record<QuestType, QuestTypeConfig> = {
  STUDY: { bg: 'rgba(96,165,250,0.08)', border: '#60A5FA', icon: 'book' },
  WRITE: { bg: 'rgba(167,139,250,0.08)', border: '#A78BFA', icon: 'pen' },
  DISCOVER: { bg: 'rgba(251,191,36,0.08)', border: '#FBBF24', icon: 'magnifier' },
  BUILD: { bg: 'rgba(52,211,153,0.08)', border: '#34D399', icon: 'hammer' },
  BOSS: { bg: 'rgba(239,68,68,0.05)', border: '#EF4444', icon: 'skull' },
}

export const ACTS: Act[] = [
  {
    id: 1, title: 'ACT I', subtitle: '캐릭터 생성', color: '#4ECDC4', icon: 'flask',
    quests: [
      { id: '1-1', type: 'STUDY', title: '기술 스택 자가 진단', xp: 150, aiCheck: true, tag: 'AI진단', difficulty: 1,
        desc: 'AI가 보유 기술을 분석해 목표 포지션 대비 강점·갭·학습 우선순위를 진단한다',
        tasks: ['보유 기술 스택 입력 (기술명:레벨 형식)', '목표 포지션 입력', 'AI 진단 결과 수령 → 갭 기술 확인'] },
      { id: '1-2', type: 'WRITE', title: '이직 동기 에세이', xp: 200, aiCheck: true, tag: 'AI검사', difficulty: 1,
        desc: 'AI가 이직 동기의 명확성·논리성·진정성을 4개 기준으로 평가한다',
        tasks: ['현 직장 불만족 사항 3가지', '이직 후 목표 3가지', '5년 후 비전 200자 → AI 제출'] },
      { id: '1-BOSS', type: 'BOSS', title: 'BOSS: 개발자 클래스 판별', xp: 500, aiCheck: true, tag: '보스전', difficulty: 2,
        desc: 'Act I 데이터를 종합해 AI가 개발자 유형과 맞춤 전략을 제시한다',
        tasks: ['1-1, 1-2 완료 데이터 종합', 'AI 클래스 분석 수령', '맞춤 이직 전략 3가지 확인'] },
    ],
  },
  {
    id: 2, title: 'ACT II', subtitle: '스킬 강화', color: '#A78BFA', icon: 'swords',
    quests: [
      { id: '2-1', type: 'STUDY', title: '약점 기술 집중 공략', xp: 400, aiCheck: false, tag: '학습', difficulty: 3,
        desc: 'Act I에서 발견한 약점 기술 2주 심화 학습',
        tasks: ['약점 기술 2가지 학습 계획 수립', '공식 문서 + 책 1권 완독', '실습 코드 GitHub 업로드'] },
      { id: '2-2', type: 'WRITE', title: '기술 블로그 검사', xp: 600, aiCheck: true, tag: 'AI검사', difficulty: 3,
        desc: 'AI가 기술적 정확성·깊이·코드 품질을 채점하고 S~D 등급 부여',
        tasks: ['주제 선정 (JVM GC, DB 인덱스 등)', '1500자+ 작성, 코드 예제 포함', 'AI 제출 → 피드백 반영'] },
      { id: '2-3', type: 'BUILD', title: '시스템 설계 챌린지', xp: 500, aiCheck: true, tag: 'AI검사', difficulty: 4,
        desc: '실전 시스템 설계를 AI가 5개 기준으로 평가한다',
        tasks: ['배달앱/유튜브/채팅 중 선택', '아키텍처 설명 + 고려 사항 작성', 'AI 피드백 수령 후 개선'] },
      { id: '2-4', type: 'BUILD', title: '코딩 테스트 30문제', xp: 450, aiCheck: false, tag: '코딩', difficulty: 3,
        desc: '알고리즘 실력을 증명하는 15일 스프린트',
        tasks: ['Medium 30문제 (하루 2문제)', '못 푼 문제 AI 풀이 분석', '패턴별 오답 노트 작성'] },
      { id: '2-BOSS', type: 'BOSS', title: 'BOSS: 모의 기술 면접', xp: 800, aiCheck: true, tag: '보스전', difficulty: 5,
        desc: 'AI 면접관과 10문제 실전 면접. 평균 70점 이상 통과.',
        tasks: ['카테고리별 10문제 랜덤 출제', 'AI가 4개 기준 실시간 채점', '평균 70점 이상 통과 시 Act III 해금'] },
    ],
  },
  {
    id: 3, title: 'ACT III', subtitle: '세계 탐색', color: '#F59E0B', icon: 'map',
    quests: [
      { id: '3-1', type: 'DISCOVER', title: '관심 회사 10곳 리스트업', xp: 250, aiCheck: false, tag: '탐색', difficulty: 2,
        desc: '원티드·링크드인·잡플래닛으로 타겟 10곳 선정 및 정리',
        tasks: ['관심 회사 10곳 리서치', '기술스택, 팀 규모, 리뷰 정리', '핏 기준으로 5곳 압축'] },
      { id: '3-2', type: 'DISCOVER', title: 'JD 역분석', xp: 350, aiCheck: true, tag: 'AI검사', difficulty: 3,
        desc: 'AI가 JD를 분석해 숨겨진 요구사항과 기술 갭을 파악한다',
        tasks: ['타겟 회사 JD 붙여넣기', 'AI가 필수/우대 기술 + 갭 분석', '지원 전략 및 매칭 점수 수령'] },
      { id: '3-BOSS', type: 'BOSS', title: 'BOSS: 최종 타겟 3곳 확정', xp: 600, aiCheck: false, tag: '보스전', difficulty: 3,
        desc: '모든 데이터를 종합해 지원할 회사 A/B/C를 확정한다',
        tasks: ['핏 분석 + JD 갭 데이터 종합', 'A/B/C 우선순위 확정', '회사별 맞춤 전략 수립'] },
    ],
  },
  {
    id: 4, title: 'ACT IV', subtitle: '장비 제작', color: '#10B981', icon: 'shield',
    quests: [
      { id: '4-1', type: 'BUILD', title: '이력서 STAR 검토', xp: 500, aiCheck: true, tag: 'AI검사', difficulty: 3,
        desc: 'AI가 STAR 기법, 수치화, JD 키워드 매칭을 채점하고 개선 예시를 제공한다',
        tasks: ['이력서 초안 작성', 'AI에 제출 → 상세 피드백', 'AI 개선 예시 반영 후 최종본'] },
      { id: '4-2', type: 'BUILD', title: 'GitHub 프로필 리모델링', xp: 300, aiCheck: false, tag: '빌드', difficulty: 2,
        desc: '30초 안에 감탄하게 만드는 GitHub',
        tasks: ['README 프로필 작성', '프로젝트 README 개선 (구조, 스크린샷 포함)', '최근 커밋 활동 추가'] },
      { id: '4-BOSS', type: 'BOSS', title: 'BOSS: 지원 패키지 완성', xp: 700, aiCheck: true, tag: '보스전', difficulty: 4,
        desc: '이력서, GitHub, 블로그 전체를 AI로 최종 점검한다',
        tasks: ['최종 이력서 AI 재검토', '포트폴리오 링크 정리', '지원 패키지 완성 확인'] },
    ],
  },
  {
    id: 5, title: 'ACT V', subtitle: '최종 보스전', color: '#EF4444', icon: 'crown',
    quests: [
      { id: '5-1', type: 'STUDY', title: '인성 면접 연습', xp: 400, aiCheck: true, tag: 'AI검사', difficulty: 3,
        desc: 'AI가 인성 면접 답변의 구체성·진정성·성장 마인드를 평가한다',
        tasks: ['핵심 인성 질문 10개 선정', 'AI와 1:1 롤플레이', '피드백 반영 후 개선'] },
      { id: '5-2', type: 'BUILD', title: '실전 지원 개시', xp: 500, aiCheck: false, tag: '지원', difficulty: 3,
        desc: '드디어 서류를 넣는다',
        tasks: ['A사 지원 및 면접 예상 질문 시뮬레이션', 'B사, C사 순차 지원', '면접 일정 관리'] },
      { id: '5-BOSS', type: 'BOSS', title: 'FINAL BOSS: 합격!', xp: 2000, aiCheck: false, tag: '클리어', difficulty: 5,
        desc: '게임 클리어. 새로운 챕터의 시작.',
        tasks: ['면접 통과', '오퍼 레터 수령', 'AI 연봉 협상 시뮬레이션'] },
    ],
  },
]
