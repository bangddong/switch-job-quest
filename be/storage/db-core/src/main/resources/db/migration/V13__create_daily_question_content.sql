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
