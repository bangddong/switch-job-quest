package com.devquest.core.domain.model

enum class ApplicationStatus {
    INTERESTED,      // 관심
    APPLIED,         // 지원 완료
    SCREENING_PASS,  // 서류 합격
    SCREENING_FAIL,  // 서류 탈락
    TECH_INTERVIEW,  // 기술면접 단계
    HR_INTERVIEW,    // 인성면접 단계
    OFFERED,         // 최종 합격
    REJECTED,        // 최종 탈락
    WITHDRAWN,       // 자진 철회
}
