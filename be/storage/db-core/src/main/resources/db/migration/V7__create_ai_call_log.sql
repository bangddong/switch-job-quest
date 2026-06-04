CREATE TABLE ai_call_log (
    id BIGSERIAL PRIMARY KEY,
    evaluator_name VARCHAR(100) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    cache_read_tokens INT NOT NULL DEFAULT 0,
    cache_creation_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_call_log_evaluator ON ai_call_log(evaluator_name);
CREATE INDEX idx_ai_call_log_created_at ON ai_call_log(created_at);
