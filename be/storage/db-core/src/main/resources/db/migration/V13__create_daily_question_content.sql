CREATE TABLE daily_question_content (
    id BIGSERIAL PRIMARY KEY,
    question_date DATE NOT NULL,
    mail_type VARCHAR(50) NOT NULL,
    question TEXT NOT NULL,
    source VARCHAR(20) NOT NULL,
    category VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_daily_question_content_date_type UNIQUE (question_date, mail_type)
);

-- QA F-5 백필: 새 테이블이 빈 상태로 시작하면, 컷오버 직전까지 메일로 나간 질문이 오늘 다시
-- 선택될 수 있다(DailyQuestionContentService.RECENT_QUESTION_WINDOW_DAYS=20일 창이 비어 있으므로).
-- daily_mail_log(메일 발송 이력)에서 (날짜, mail_type)별 과거 출제 이력을 복원한다.
--
-- 주의 사항:
--   - 같은 날 여러 유저에게 발송된 로그는 (question_date, mail_type)당 여러 행이므로,
--     UNIQUE(question_date, mail_type) 제약을 지키기 위해 날짜별 1건으로 접는다
--     (그 날 가장 먼저 발송된 질문 = MIN(sent_at) 기준으로 채택 — 그날 실제로 발송을 트리거한 질문).
--   - source는 원본 이력(BANK/AI 여부)을 daily_mail_log가 담고 있지 않으므로 알 수 없다.
--     'LEGACY_MAIL_LOG'로 표시해 컷오버 이후 자연 생성분(BANK/AI)과 구분한다.
--   - ON CONFLICT DO NOTHING으로 재실행 안전(idempotent) — 이 마이그레이션이 이미 적용된 뒤
--     다시 실행되거나, 이 사이 자연 생성된 오늘자 행과 충돌해도 에러 없이 스킵한다.
INSERT INTO daily_question_content (question_date, mail_type, question, source, category, created_at, updated_at)
SELECT
    backfill.question_date,
    backfill.mail_type,
    backfill.question,
    'LEGACY_MAIL_LOG' AS source,
    NULL AS category,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT ON (DATE(sent_at), mail_type)
        DATE(sent_at) AS question_date,
        mail_type,
        question_content AS question,
        sent_at
    FROM daily_mail_log
    ORDER BY DATE(sent_at), mail_type, sent_at ASC
) AS backfill
ON CONFLICT (question_date, mail_type) DO NOTHING;
