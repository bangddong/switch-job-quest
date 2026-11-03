package com.devquest.core.api.osiv

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles

/**
 * B7-21 ⓐ 회귀 (H2, 기본 `test` 태스크). 설정은 앱 기본값 그대로다 - 프로퍼티 오버라이드 없음.
 * 기대: 외부 경계 대기 중 active=0, OSIV 인터셉터 빈 0개, 요청은 그대로 200.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingExternalWaitConnectionTest : AbstractCodingExternalWaitConnectionTest(config = "app-default(H2)")
