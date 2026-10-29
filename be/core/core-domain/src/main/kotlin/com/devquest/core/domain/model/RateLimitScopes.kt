package com.devquest.core.domain.model

/**
 * 레이트 리밋 카운터의 scope 이름 (`rate_limit_counter.scope`, varchar(64)).
 *
 * core-enum 이 아니라 core-domain 에 둔다 — daily-api 는 core-enum 을 의존하지 않는데 두 앱이 **같은 값**을
 * 써야 같은 행을 본다. 값은 저장된 데이터의 일부라 바꾸면 그날 카운터가 갈라진다(리네임 금지).
 *
 * 개인 한도와 전체 상한은 scope 로 분리한다(전체 상한 = `*-global` scope + 고정 키 [GLOBAL_KEY]).
 * 한 scope 에 섞으면 클라이언트가 보낸 IP 문자열이 [GLOBAL_KEY] 와 같을 때 전체 상한 행을 건드릴 수 있다.
 */
object RateLimitScopes {
    const val TECH_INTERVIEW = "tech-interview"
    const val DAILY_EXPLAIN = "daily-explain"
    const val DAILY_EVALUATE = "daily-evaluate"
    const val DAILY_EXPLAIN_GLOBAL = "daily-explain-global"
    const val DAILY_EVALUATE_GLOBAL = "daily-evaluate-global"

    /** 전체 상한 행의 고정 키(원문). 저장 시에는 다른 키와 똑같이 해시된다. */
    const val GLOBAL_KEY = "global"
}
