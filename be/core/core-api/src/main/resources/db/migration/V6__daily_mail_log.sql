CREATE TABLE daily_mail_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    mail_type VARCHAR(50) NOT NULL,
    question_content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_daily_mail_log_user_sent ON daily_mail_log(user_id, mail_type, sent_at);
