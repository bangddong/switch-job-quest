export const QUEST_NEXT: Record<string, { questId: string; message: string }> = {
  '1-1': { questId: '1-2', message: '진단 결과를 바탕으로 이직 동기를 정리해봐요' },
  '1-2': { questId: '1-BOSS', message: '1-1, 1-2 결과를 종합해 개발자 클래스를 판별해요' },
  '2-2': { questId: '2-3', message: '블로그에서 보인 기술 깊이를 시스템 설계에도 발휘해봐요' },
  '2-3': { questId: '2-BOSS', message: '설계 경험을 AI 모의 면접에서 어필해봐요' },
  '3-2': { questId: '3-BOSS', message: 'JD 분석을 바탕으로 최종 지원 타겟을 확정해요' },
  '4-1': { questId: '4-BOSS', message: '이력서와 GitHub를 함께 최종 점검해요' },
}
