CREATE TABLE applied_company (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    position VARCHAR(255) NOT NULL DEFAULT '',
    jd_url VARCHAR(2048),
    status VARCHAR(50) NOT NULL DEFAULT 'INTERESTED',
    notes TEXT,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE company_activity (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES applied_company(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    ai_score INTEGER NOT NULL DEFAULT 0,
    ai_result_json TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_applied_company_user_id ON applied_company(user_id);
CREATE INDEX idx_company_activity_company_id ON company_activity(company_id);
