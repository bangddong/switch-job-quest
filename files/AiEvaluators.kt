package com.devquest.ai.evaluator

import com.devquest.model.request.*
import com.devquest.model.response.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger("AiEvaluator")

// ─────────────────────────────────────────────────────────────
// 1. 이직 동기 에세이 평가 (Act I - Quest 1-2)
// ─────────────────────────────────────────────────────────────
@Component
class CareerEssayEvaluator(
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper
) {
    fun evaluate(request: CareerEssayCheckRequest): EssayCheckResult {
        val prompt = """
            다음 5년차 백엔드 개발자의 이직 동기를 평가해주세요.
            
            ## 현재 직장 불만족 사항
            ${request.dissatisfactions.mapIndexed { i, s -> "${i+1}. $s" }.joinToString("\n")}
            
            ## 이직 후 목표
            ${request.goals.mapIndexed { i, g -> "${i+1}. $g" }.joinToString("\n")}
            
            ## 5년 후 비전
            ${request.fiveYearVision}
            
            ## 평가 기준 (총 100점)
            - 명확성 (30점): 불만족 사유와 목표가 구체적인가
            - 논리성 (30점): 현재 상황 → 이직 → 미래 비전의 흐름이 자연스러운가
            - 동기 진정성 (20점): 성장 욕구가 진정성 있게 느껴지는가
            - 성장 방향 (20점): 5년 후 비전이 현실적이고 구체적인가
            
            반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "score": 75,
                "passed": true,
                "grade": "B",
                "clarityScore": 22,
                "logicScore": 23,
                "motivationScore": 15,
                "growthScore": 15,
                "feedback": "전반적으로 이직 동기가 명확하나...",
                "developerType": "아키텍트 지향형",
                "suggestedFocus": ["기술 중심 스타트업", "플랫폼 팀이 있는 기업"]
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(EssayCheckResult::class.java)
            ?: throw RuntimeException("AI 평가 응답 파싱 실패")
    }
}

// ─────────────────────────────────────────────────────────────
// 2. 기술 블로그 정확성 검사 (Act II - Quest 2-2)
// ─────────────────────────────────────────────────────────────
@Component
class TechBlogEvaluator(
    private val chatClient: ChatClient,
    private val objectMapper: ObjectMapper
) {
    fun evaluate(request: TechBlogCheckRequest): AiEvaluationResult {
        val prompt = """
            다음 기술 블로그 포스트를 평가해주세요.
            
            ## 주제: ${request.techTopic}
            ## 제목: ${request.title}
            
            ## 본문
            ${request.content}
            
            ## 평가 기준 (총 100점)
            - 기술적 정확성 (40점): 내용이 기술적으로 올바른가? 오류나 오해가 없는가?
            - 깊이와 인사이트 (25점): 표면적 설명 이상의 깊이가 있는가?
            - 코드 예제 품질 (20점): 코드 예제가 실용적이고 정확한가?
            - 가독성과 구조 (15점): 글의 흐름과 설명이 명확한가?
            
            기술적 오류가 있으면 반드시 명시하고 올바른 내용으로 수정해주세요.
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "score": 82,
                "passed": true,
                "grade": "A",
                "summary": "JVM GC 튜닝에 대한 실용적인 글입니다.",
                "strengths": ["실제 경험 기반의 구체적인 사례", "G1GC 파라미터 설명이 정확"],
                "improvements": ["ZGC와의 비교 분석 추가 필요", "메모리 프로파일링 도구 언급 필요"],
                "detailedFeedback": "전반적으로 기술적으로 정확하나...",
                "xpMultiplier": 1.2,
                "retryAllowed": true
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(AiEvaluationResult::class.java)
            ?: throw RuntimeException("AI 블로그 평가 실패")
    }
}

// ─────────────────────────────────────────────────────────────
// 3. 시스템 설계 평가 (Act II - Quest 2-3)
// ─────────────────────────────────────────────────────────────
@Component
class SystemDesignEvaluator(
    private val chatClient: ChatClient
) {
    fun evaluate(request: SystemDesignCheckRequest): AiEvaluationResult {
        val prompt = """
            다음 시스템 설계 답변을 평가해주세요.
            
            ## 문제: ${request.problemStatement}
            
            ## 후보자 설계 내용
            ${request.architectureDescription}
            
            ## 후보자가 고려한 사항
            ${request.considerations.mapIndexed { i, c -> "${i+1}. $c" }.joinToString("\n")}
            
            ## 평가 기준 (총 100점)
            - 요구사항 분석 (20점): 기능/비기능 요구사항을 제대로 파악했는가?
            - 확장성 설계 (25점): 트래픽 증가에 대응하는 구조인가?
            - 데이터 모델링 (20점): DB 설계가 적절한가? (정규화, 인덱스, 샤딩)
            - 가용성/장애 대응 (20점): SPOF 제거, 장애 복구 전략
            - 실현 가능성 (15점): 현실적으로 구현 가능한 설계인가?
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "score": 78,
                "passed": true,
                "grade": "B",
                "summary": "배달앱 주문 시스템 설계가 전반적으로 타당합니다.",
                "strengths": ["메시지 큐를 활용한 비동기 처리가 적절", "DB 샤딩 전략이 명확"],
                "improvements": ["Redis 캐시 전략 상세화 필요", "결제 서비스 장애 시 fallback 전략 미흡"],
                "detailedFeedback": "주문 서비스와 배달 서비스의 분리는 좋은 결정입니다...",
                "xpMultiplier": 1.0,
                "retryAllowed": true
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(AiEvaluationResult::class.java)
            ?: throw RuntimeException("시스템 설계 평가 실패")
    }
}

// ─────────────────────────────────────────────────────────────
// 4. 기술 면접 답변 평가 (Act II - Boss Quest)
// ─────────────────────────────────────────────────────────────
@Component
class MockInterviewEvaluator(
    @Qualifier("interviewChatClient") private val interviewChatClient: ChatClient
) {
    fun evaluate(request: MockInterviewRequest): InterviewEvaluationResult {
        val prompt = """
            다음 기술 면접 답변을 채점해주세요.
            
            ## 카테고리: ${request.category}
            ## 질문: ${request.question}
            
            ## 후보자 답변
            ${request.answer}
            
            ## 채점 기준
            - 기술적 정확성: /40점 (틀린 내용은 엄격하게 감점)
            - 깊이와 응용력: /30점 (원리를 이해하고 응용할 수 있는가)
            - 실무 경험 연결: /20점 (실제 업무 경험과 연결된 답변인가)
            - 커뮤니케이션: /10점 (명확하고 논리적으로 설명했는가)
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "questionId": "${request.questionId}",
                "question": "${request.question}",
                "userAnswer": "${request.answer.take(100)}...",
                "score": 72,
                "passed": true,
                "technicalAccuracy": 30,
                "depthAndApplication": 22,
                "practicalExperience": 12,
                "communicationClarity": 8,
                "correctAnswer": "정확한 모범 답안...",
                "keyPointsMissed": ["STW(Stop-The-World) 개념 설명 미흡", "G1GC 작동 원리 언급 없음"],
                "improvements": "GC의 각 단계별 동작을 순서대로 설명하고, 실제 튜닝 경험을 연결하면 더 좋습니다."
            }
        """.trimIndent()

        return interviewChatClient.prompt()
            .user(prompt)
            .call()
            .entity(InterviewEvaluationResult::class.java)
            ?: throw RuntimeException("면접 평가 실패")
    }

    /**
     * 면접 질문 랜덤 출제 (interview_questions.txt 기반)
     * 카테고리별로 균등 출제
     */
    fun generateQuestions(categories: List<String>, count: Int): List<Map<String, String>> {
        val prompt = """
            다음 카테고리에서 백엔드 5년차 개발자 면접 질문 ${count}개를 선별해주세요.
            카테고리: ${categories.joinToString(", ")}
            
            실제 면접에서 자주 나오는 실전적인 질문 위주로 선별하고,
            카테고리별로 균등하게 배분해주세요.
            
            반드시 다음 JSON 배열 형식으로만 응답하세요:
            [
                {"id": "q1", "category": "DB", "question": "인덱스의 내부 동작 원리를 설명해주세요.", "difficulty": "MEDIUM"},
                {"id": "q2", "category": "JVM", "question": "GC의 종류와 차이점을 설명해주세요.", "difficulty": "MEDIUM"}
            ]
        """.trimIndent()

        val response = interviewChatClient.prompt()
            .user(prompt)
            .call()
            .content() ?: "[]"

        return try {
            val objectMapper = ObjectMapper()
            objectMapper.readValue<List<Map<String, String>>>(response)
        } catch (e: Exception) {
            log.warn("질문 생성 파싱 실패, 기본 질문 반환: ${e.message}")
            emptyList()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 5. JD 분석 및 핏 매칭 (Act III - Quest 3-2)
// ─────────────────────────────────────────────────────────────
@Component
class JdAnalysisEvaluator(
    private val chatClient: ChatClient
) {
    fun analyze(request: JdAnalysisRequest): JdAnalysisResult {
        val prompt = """
            다음 채용공고(JD)를 분석하고, 후보자와의 기술 갭을 평가해주세요.
            
            ## 회사: ${request.companyName}
            
            ## 채용공고 내용
            ${request.jobDescription}
            
            ## 후보자 보유 기술
            ${request.userSkills.joinToString(", ")}
            
            ## 후보자 경력 요약
            ${request.userExperiences.joinToString("\n")}
            
            JD에 명시되지 않았더라도 이 포지션에서 일반적으로 필요한 숨겨진 요구사항도 파악해주세요.
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "companyName": "${request.companyName}",
                "requiredSkills": [
                    {"skill": "Spring Boot", "required": true, "userLevel": "상", "importance": "HIGH"},
                    {"skill": "Kubernetes", "required": false, "userLevel": "없음", "importance": "MEDIUM"}
                ],
                "hiddenRequirements": ["대용량 트래픽 처리 경험", "MSA 마이그레이션 경험"],
                "overallMatchScore": 78,
                "keyDifferentiators": ["Kafka 경험이 특히 중요", "코드 리뷰 문화 중시"],
                "applicationStrategy": "Spring Boot 경험을 강조하고, Kubernetes는 학습 의지를 보여주세요..."
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(JdAnalysisResult::class.java)
            ?: throw RuntimeException("JD 분석 실패")
    }
}

// ─────────────────────────────────────────────────────────────
// 6. 이력서 STAR 검토 (Act IV - Quest 4-1)
// ─────────────────────────────────────────────────────────────
@Component
class ResumeCheckEvaluator(
    private val chatClient: ChatClient
) {
    fun evaluate(request: ResumeCheckRequest): ResumeCheckResult {
        val prompt = """
            다음 이력서를 평가하고 개선 방안을 제시해주세요.
            
            ## 지원 회사: ${request.targetCompany}
            
            ## 채용공고 핵심 요구사항
            ${request.targetJd.take(500)}
            
            ## 이력서 내용
            ${request.resumeContent}
            
            ## 평가 기준
            1. STAR 기법 활용도 (Situation-Task-Action-Result): /40점
            2. 수치화 정도 (%, X배, N건 등 구체적 숫자): /30점  
            3. JD 키워드 매칭도: /30점
            
            이력서 bullet point 중 개선이 필요한 것을 찾아 STAR 기법으로 다시 써주세요.
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "overallScore": 72,
                "starMethodScore": 28,
                "quantificationScore": 22,
                "keywordMatchScore": 22,
                "improvements": [
                    {
                        "section": "경력사항",
                        "original": "API 성능 개선 작업을 했습니다.",
                        "issue": "수치가 없고 STAR 구조가 없습니다.",
                        "suggestion": "결과(Result)를 수치로 추가하고 어떤 방법(Action)을 썼는지 명시하세요."
                    }
                ],
                "rewrittenExamples": [
                    {
                        "original": "API 성능 개선 작업을 했습니다.",
                        "improved": "레거시 쿼리 N+1 문제 해결로 주요 API 응답시간 450ms → 120ms(73%) 단축 (Spring Boot, JPA QueryDSL)",
                        "explanation": "구체적인 수치와 기술 스택을 추가하여 임팩트를 명확히 표현"
                    }
                ]
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(ResumeCheckResult::class.java)
            ?: throw RuntimeException("이력서 평가 실패")
    }
}

// ─────────────────────────────────────────────────────────────
// 7. 회사 핏 종합 분석 (Act I Boss + Act III Boss)
// ─────────────────────────────────────────────────────────────
@Component
class CompanyFitEvaluator(
    private val chatClient: ChatClient
) {
    fun analyze(request: CompanyFitRequest): List<CompanyFitResult> {
        val preferencesText = request.preferences.entries
            .joinToString("\n") { (k, v) -> "- $k: $v" }

        val companiesText = request.companies.mapIndexed { i, c ->
            """
            ${i+1}. ${c.name}
               - 문화: ${c.culture}
               - 기술스택: ${c.techStack.joinToString(", ")}
               - 규모: ${c.size}
               - 설명: ${c.description}
            """.trimIndent()
        }.joinToString("\n\n")

        val prompt = """
            개발자의 선호도를 기반으로 각 회사와의 핏 점수를 분석해주세요.
            
            ## 개발자 선호 사항
            $preferencesText
            
            ## 분석 대상 회사들
            $companiesText
            
            ## 평가 기준 (각 회사별 100점)
            - 문화 적합도: /25점 (개발 문화, 팀 분위기)
            - 기술 적합도: /25점 (기술 스택, 도전적 과제)
            - 성장 방향 적합도: /25점 (커리어 성장 가능성)
            - 라이프스타일 적합도: /25점 (워라밸, 원격근무, 복지)
            
            반드시 다음 JSON 배열 형식으로만 응답하세요:
            [
                {
                    "companyName": "회사명",
                    "fitScore": 85,
                    "fitGrade": "A",
                    "cultureFit": 22,
                    "techFit": 23,
                    "growthFit": 20,
                    "lifestyleFit": 20,
                    "pros": ["기술 중심 문화", "MSA 전환 중으로 좋은 경험 가능"],
                    "cons": ["초기 스타트업이라 불안정성 있음"],
                    "recommendation": "기술 성장을 원한다면 최우선 추천"
                }
            ]
        """.trimIndent()

        val response = chatClient.prompt()
            .user(prompt)
            .call()
            .content() ?: "[]"

        return try {
            val objectMapper = ObjectMapper()
            objectMapper.readValue<List<CompanyFitResult>>(response)
        } catch (e: Exception) {
            log.error("회사 핏 분석 파싱 실패: ${e.message}")
            throw RuntimeException("회사 핏 분석 응답 파싱 실패", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 8. 인성 면접 평가 (Act V - Quest 5-1)
// ─────────────────────────────────────────────────────────────
@Component
class PersonalityInterviewEvaluator(
    private val chatClient: ChatClient
) {
    fun evaluate(request: PersonalityInterviewRequest): AiEvaluationResult {
        val prompt = """
            다음 인성 면접 답변을 평가해주세요.
            
            ## 질문: ${request.question}
            
            ## 후보자 답변
            ${request.answer}
            
            ## 평가 기준 (총 100점)
            - 구체성 (30점): 추상적이 아닌 실제 경험과 사례를 들었는가?
            - 진정성 (25점): 꾸밈없이 솔직한 답변인가?
            - 성장 마인드셋 (25점): 어려운 상황에서 배움을 얻는 태도인가?
            - 커뮤니케이션 (20점): 명확하고 간결하게 전달했는가?
            
            반드시 다음 JSON 형식으로만 응답하세요:
            {
                "score": 78,
                "passed": true,
                "grade": "B",
                "summary": "갈등 해결 경험을 구체적으로 설명했으나...",
                "strengths": ["실제 팀 프로젝트 경험 기반", "해결 과정이 논리적"],
                "improvements": ["결과(Result)가 명확하지 않음", "자신의 역할 강조 필요"],
                "detailedFeedback": "STAR 기법을 활용해 Situation을 먼저 설명하고...",
                "xpMultiplier": 1.0,
                "retryAllowed": true
            }
        """.trimIndent()

        return chatClient.prompt()
            .user(prompt)
            .call()
            .entity(AiEvaluationResult::class.java)
            ?: throw RuntimeException("인성 면접 평가 실패")
    }
}
