package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.model.TechQuestionBank
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class DailyQuestionContentServiceTest {

    @Mock lateinit var dailyQuestionContentPort: DailyQuestionContentPort
    @Mock lateinit var techQuestionBankPort: TechQuestionBankPort
    @Mock lateinit var techInterviewPort: TechInterviewPort

    private lateinit var service: DailyQuestionContentService

    private fun newService() = DailyQuestionContentService(
        dailyQuestionContentPort = dailyQuestionContentPort,
        techQuestionBankPort = techQuestionBankPort,
        techInterviewPort = techInterviewPort,
        techStack = "Java,Spring Boot,JPA",
    )

    // --- QA F-4: 메서드 레벨뿐 아니라 클래스 레벨(+ 메타 어노테이션을 통한 상속) @Transactional도 잡는다.
    // raw Method#isAnnotationPresent는 enclosing class의 어노테이션을 따라가지 않으므로,
    // AnnotatedElementUtils.findMergedAnnotation을 메서드/클래스 양쪽에 적용해 재도입을 구조적으로 감지한다.
    private fun assertNotTransactional(methodName: String) {
        val method = DailyQuestionContentService::class.java.getMethod(methodName)
        val reason = "$methodName()는 AI 폴백 호출(최대 150초, prod 타임아웃 없음) 동안 " +
            "HikariCP 커넥션(pool=10)을 점유하지 않기 위해 메서드에도 클래스에도 @Transactional이 붙으면 안 된다 (QA F-1/F-4)"

        assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java))
            .`as`("[method-level] $reason")
            .isNull()
        assertThat(AnnotatedElementUtils.findMergedAnnotation(DailyQuestionContentService::class.java, Transactional::class.java))
            .`as`("[class-level] $reason")
            .isNull()
    }

    @Test
    fun `ensureTodayQuestion - 유저가 0명이어도 오늘의 질문이 없으면 뱅크에서 생성해 저장한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        val result = service.ensureTodayQuestion()

        assertThat(result.question).isEqualTo("뱅크 질문")
        assertThat(result.source).isEqualTo("BANK")
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 뱅크가 소진되면 AI로 질문을 생성한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull())).thenReturn(null)
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("AI 생성 질문")
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        val result = service.ensureTodayQuestion()

        assertThat(result.question).isEqualTo("AI 생성 질문")
        assertThat(result.source).isEqualTo("AI")
        verify(dailyQuestionContentPort).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 오늘 질문이 이미 있으면 재생성하지 않고 그대로 반환한다`() {
        service = newService()
        val existing = DailyQuestionContent(
            id = 1L,
            questionDate = LocalDate.now(),
            mailType = "TECH_INTERVIEW",
            question = "기존 질문",
            source = "BANK",
        )
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(existing)

        val result = service.ensureTodayQuestion()

        assertThat(result).isEqualTo(existing)
        verify(techQuestionBankPort, never()).findUnused(any(), anyOrNull())
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort, never()).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 두 번 호출해도 뱅크·AI 조회는 최초 1회만 일어난다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(null)
            .thenReturn(DailyQuestionContent(question = "뱅크 질문", source = "BANK", mailType = "TECH_INTERVIEW"))
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        service.ensureTodayQuestion()
        service.ensureTodayQuestion()

        verify(techQuestionBankPort, org.mockito.kotlin.times(1)).findUnused(any(), anyOrNull())
        verify(dailyQuestionContentPort, org.mockito.kotlin.times(1)).save(any())
    }

    @Test
    fun `ensureTodayQuestion - 최근 질문 제외 목록을 20일 전 날짜를 기준으로 조회한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        service.ensureTodayQuestion()

        val mailTypeCaptor = argumentCaptor<String>()
        val sinceCaptor = argumentCaptor<LocalDate>()
        verify(dailyQuestionContentPort).findQuestionsSince(mailTypeCaptor.capture(), sinceCaptor.capture())
        assertThat(mailTypeCaptor.firstValue).isEqualTo("TECH_INTERVIEW")
        assertThat(sinceCaptor.firstValue).isEqualTo(LocalDate.now().minusDays(20))
    }

    // --- QA F-1 / F-4 회귀 가드 ---
    // 실제 트랜잭션 전파(커넥션이 AI 호출 동안 붙잡혀 있는지)는 Mockito 단위 테스트로는 검증할
    // 수 없다 — Spring AOP 프록시가 없는 순수 객체 생성이기 때문이다(통합 테스트가 필요한 영역).
    // 차선책으로 "@Transactional이 재도입되지 않았는지"를 구조적으로 가드한다.
    // F-4: 메서드 레벨만 보는 raw isAnnotationPresent는 클래스 레벨 재도입(be/CLAUDE.md 컨벤션을 따라
    // 다음 사람이 붙이기 가장 쉬운 형태)을 못 잡는다 — assertNotTransactional이 메서드+클래스를 모두 본다.
    // 한계: 이 테스트는 어노테이션의 "부재"만 확인할 뿐, 실제로 커넥션이 짧게 쓰이고 반환되는지는
    // 증명하지 못한다.
    @Test
    fun `ensureTodayQuestion 회귀가드 - 메서드·클래스 어디에도 @Transactional이 붙어있지 않다 (구조적 검증, F-1·F-4)`() {
        assertNotTransactional("ensureTodayQuestion")
    }

    // --- QA F-3 회귀 가드 ---
    @Test
    fun `ensureTodayQuestion - save 시 UNIQUE 위반이 나면 기존 값을 재조회해 반환한다 (F-3)`() {
        service = newService()
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val winner = DailyQuestionContent(
            id = 1L,
            questionDate = today,
            mailType = "TECH_INTERVIEW",
            question = "동시 생성 승자 질문",
            source = "BANK",
        )
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(null)
            .thenReturn(winner)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any()))
            .thenThrow(DataIntegrityViolationException("uq_daily_question_content_date_type 위반"))

        val result = service.ensureTodayQuestion()

        assertThat(result).isEqualTo(winner)
        verify(dailyQuestionContentPort, times(2)).findToday(eq("TECH_INTERVIEW"), any())
    }

    @Test
    fun `ensureTodayQuestion - UNIQUE 위반 후 재조회도 없으면 원래 예외를 던진다 (F-3)`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any()))
            .thenThrow(DataIntegrityViolationException("uq_daily_question_content_date_type 위반"))

        assertThatThrownBy { service.ensureTodayQuestion() }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    // --- Phase 2 Stage A: ensureTodayQuestionFromBank (읽기 경로 전용, AI 폴백 없음) ---

    @Test
    fun `ensureTodayQuestionFromBank - 오늘 질문이 없고 뱅크에 후보가 있으면 뱅크에서 생성해 저장한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any())).thenAnswer { it.arguments[0] }

        val result = service.ensureTodayQuestionFromBank()

        assertThat(result).isNotNull
        assertThat(result?.question).isEqualTo("뱅크 질문")
        assertThat(result?.source).isEqualTo("BANK")
        verify(dailyQuestionContentPort).save(any())
    }

    @Test
    fun `ensureTodayQuestionFromBank - 뱅크가 소진되면 AI를 호출하지 않고 null을 반환한다`() {
        service = newService()
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(null)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull())).thenReturn(null)

        val result = service.ensureTodayQuestionFromBank()

        assertThat(result).isNull()
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort, never()).save(any())
    }

    @Test
    fun `ensureTodayQuestionFromBank - 오늘 질문이 이미 있으면 재생성하지 않고 그대로 반환한다`() {
        service = newService()
        val existing = DailyQuestionContent(
            id = 1L,
            questionDate = LocalDate.now(),
            mailType = "TECH_INTERVIEW",
            question = "기존 질문",
            source = "BANK",
        )
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any())).thenReturn(existing)

        val result = service.ensureTodayQuestionFromBank()

        assertThat(result).isEqualTo(existing)
        verify(techQuestionBankPort, never()).findUnused(any(), anyOrNull())
        verify(techInterviewPort, never()).generateDailyQuestion(any(), any())
        verify(dailyQuestionContentPort, never()).save(any())
    }

    @Test
    fun `ensureTodayQuestionFromBank - save 시 UNIQUE 위반이 나면 기존 값을 재조회해 반환한다 (F-3)`() {
        service = newService()
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        val winner = DailyQuestionContent(
            id = 1L,
            questionDate = today,
            mailType = "TECH_INTERVIEW",
            question = "동시 생성 승자 질문",
            source = "BANK",
        )
        whenever(dailyQuestionContentPort.findToday(eq("TECH_INTERVIEW"), any()))
            .thenReturn(null)
            .thenReturn(winner)
        whenever(dailyQuestionContentPort.findQuestionsSince(any(), any())).thenReturn(emptyList())
        whenever(techQuestionBankPort.findUnused(any(), anyOrNull()))
            .thenReturn(TechQuestionBank(category = "java-spring", question = "뱅크 질문"))
        whenever(dailyQuestionContentPort.save(any()))
            .thenThrow(DataIntegrityViolationException("uq_daily_question_content_date_type 위반"))

        val result = service.ensureTodayQuestionFromBank()

        assertThat(result).isEqualTo(winner)
        verify(dailyQuestionContentPort, times(2)).findToday(eq("TECH_INTERVIEW"), any())
    }

    // --- 회귀 가드: ensureTodayQuestionFromBank에도 @Transactional이 없어야 한다 (F-4: 메서드+클래스 레벨) ---
    @Test
    fun `ensureTodayQuestionFromBank 회귀가드 - 메서드·클래스 어디에도 @Transactional이 붙어있지 않다 (구조적 검증, F-4)`() {
        assertNotTransactional("ensureTodayQuestionFromBank")
    }
}
