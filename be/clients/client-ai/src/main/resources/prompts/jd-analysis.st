다음 채용공고(JD)를 분석하고, 후보자와의 기술 갭을 평가해주세요.

## 회사: {companyName}
## 채용공고 내용
{jobDescription}
## 후보자 보유 기술
{userSkills}
## 후보자 경력 요약
{userExperiences}

반드시 다음 JSON 형식으로만 응답하세요:
\{
    "companyName": "{companyName}",
    "requiredSkills": [\{"skill": "Spring Boot", "required": true, "userLevel": "상", "importance": "HIGH"\}],
    "hiddenRequirements": ["..."], "overallMatchScore": 78,
    "keyDifferentiators": ["..."], "applicationStrategy": "..."
\}
