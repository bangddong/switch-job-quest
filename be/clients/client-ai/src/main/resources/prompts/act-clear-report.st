5년차 백엔드 개발자가 이직 준비 RPG의 ACT {actId} "{actTitle}" 를 클리어했습니다.
이 ACT에서의 퀘스트 점수를 바탕으로 종합 리포트를 작성해주세요.

## 퀘스트 점수
{scoresText}
(평균: {avgScore}점)

## 작성 지침
- developerClass: 이 개발자의 현재 클래스/직업군 (예: "시스템 아키텍트 지망생", "풀스택 전환형 백엔드", "기술 전문가형")
- achievements: 이번 ACT에서 보여준 강점 2~3가지 (구체적으로)
- nextActHint: 다음 ACT를 위한 핵심 조언 1문장
- encouragement: 이직 준비 중인 개발자에게 진심 어린 응원 메시지 1문장

반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
\{
    "actId": {actId},
    "actTitle": "{actTitle}",
    "overallScore": {avgScore},
    "grade": "B",
    "developerClass": "시스템 아키텍트 지망생",
    "achievements": ["강점1", "강점2", "강점3"],
    "nextActHint": "다음 단계 조언",
    "encouragement": "응원 메시지"
\}
