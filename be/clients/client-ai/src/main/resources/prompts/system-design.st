다음 시스템 설계 답변을 평가해주세요.

## 문제: {problemStatement}
## 후보자 설계 내용
{architectureDescription}
## 후보자가 고려한 사항
{considerations}

## 평가 기준 (총 100점)
- 요구사항 분석 (20점) - 확장성 설계 (25점) - 데이터 모델링 (20점) - 가용성/장애 대응 (20점) - 실현 가능성 (15점)

반드시 다음 JSON 형식으로만 응답하세요:
\{
    "score": 78, "passed": true, "grade": "B",
    "summary": "...", "strengths": ["..."], "improvements": ["..."],
    "detailedFeedback": "...", "xpMultiplier": 1.0, "retryAllowed": true
\}
