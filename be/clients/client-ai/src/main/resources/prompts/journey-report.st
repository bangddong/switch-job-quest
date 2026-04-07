5년차 백엔드 개발자가 이직 준비 RPG를 완주하고 [{companyName}] {targetPosition} 포지션에 최종 합격했습니다.
이 개발자의 전체 여정을 돌아보는 감성적인 회고 리포트를 작성해주세요.

## 여정 통계
- 완료한 퀘스트: {completedQuestCount}개
- 획득한 총 XP: {totalXp}
- 합격 회사: {companyName}
- 합격 포지션: {targetPosition}

## 퀘스트별 점수
{scoresText}

## 작성 지침
- narrative: 3~4 문단의 감성적인 회고 내러티브.
  * 처음 진단에서의 불안함, 중간에 힘들었던 순간, 포기하고 싶었던 순간, 결국 해낸 성취감을 생생하게 묘사.
  * 구체적인 퀘스트 점수(특히 낮은 점수에서 성장하는 흐름)를 자연스럽게 녹여낼 것.
  * "당신"을 2인칭으로 부르며 공감하는 톤. 과도하게 격식 없이, 진심 어린 어투.
  * 마지막 문단에서 {companyName} 합격으로 이어지는 클라이맥스를 연출할 것.
- lowestQuestId: 점수가 가장 낮았던 퀘스트 ID (예: "2-3")
- highestQuestId: 점수가 가장 높았던 퀘스트 ID (예: "4-1")
- finalMessage: 앞으로를 응원하는 짧고 강렬한 한 문장 (20자 이내)

반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
\{
    "companyName": "{companyName}",
    "targetPosition": "{targetPosition}",
    "totalXp": {totalXp},
    "completedQuestCount": {completedQuestCount},
    "narrative": "...",
    "lowestQuestId": "{lowestQuestId}",
    "highestQuestId": "{highestQuestId}",
    "finalMessage": "..."
\}
