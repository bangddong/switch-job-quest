package com.devquest.daily

import com.devquest.daily.support.RateLimitResetScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

/**
 * 전역 일일 상한의 자정 리셋이 **실제로 돌도록** 배선돼 있는지 본다.
 * `@Scheduled` 는 `@EnableScheduling` 이 없으면 조용히 무시되므로(에러 0, 리셋 0) 컨텍스트에서 직접 확인한다.
 */
@SpringBootTest
class DailyApiRateLimitWiringTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `자정 리셋 스케줄러 빈이 있고 스케줄링이 활성화돼 있다`() {
        assertThat(context.getBeansOfType(RateLimitResetScheduler::class.java)).hasSize(1)
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java)).isNotEmpty()
    }
}
