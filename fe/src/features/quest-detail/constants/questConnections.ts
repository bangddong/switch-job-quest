export const QUEST_NEXT: Record<string, { questId: string; message: string }> = {
  // ACT I
  '1-1': { questId: '1-2', message: '진단 결과를 바탕으로 이직 동기를 정리해봐요' },
  '1-2': { questId: '1-BOSS', message: '1-1, 1-2 결과를 종합해 개발자 클래스를 판별해요' },
  '1-BOSS': { questId: '2-1', message: '클래스 판별 완료! 이제 약점을 집중 강화할 시간이에요' },
  // ACT II
  '2-1': { questId: '2-2', message: '공략한 기술을 블로그 글로 정리해 깊이를 증명해봐요' },
  '2-2': { questId: '2-3', message: '블로그에서 보인 기술 깊이를 시스템 설계에도 발휘해봐요' },
  '2-3': { questId: '2-BOSS', message: '설계 경험을 AI 모의 면접에서 어필해봐요' },
  '2-4': { questId: '2-BOSS', message: '코딩 테스트까지 끝냈어요! 이제 실전 면접으로 마무리해요' },
  '2-BOSS': { questId: '3-1', message: '기술력 증명 완료! 이제 나에게 맞는 회사를 찾아봐요' },
  // ACT III
  '3-1': { questId: '3-2', message: '리스트업한 회사의 JD를 역분석해 요구사항을 파악해봐요' },
  '3-2': { questId: '3-BOSS', message: 'JD 분석을 바탕으로 최종 지원 타겟을 확정해요' },
  '3-BOSS': { questId: '4-1', message: '타겟 확정! 이제 합격을 위한 장비를 제작할 시간이에요' },
  // ACT IV
  '4-1': { questId: '4-2', message: '이력서 검토 완료! GitHub 프로필도 함께 정비해봐요' },
  '4-2': { questId: '4-BOSS', message: '이력서와 GitHub를 함께 최종 점검해요' },
  '4-BOSS': { questId: '5-1', message: '지원 패키지 완성! 마지막 관문 인성 면접을 준비해봐요' },
  // ACT V
  '5-1': { questId: '5-2', message: '인성 면접 준비 완료! 이제 실전 지원을 시작할 시간이에요' },
  '5-2': { questId: '5-BOSS', message: '지원서 제출 완료! 최후의 보스와 맞설 준비가 됐나요?' },
}
