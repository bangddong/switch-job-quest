CREATE TABLE quest_progress
(
    id                  BIGSERIAL PRIMARY KEY,
    user_id             VARCHAR(255) NOT NULL,
    quest_id            VARCHAR(255) NOT NULL,
    act_id              INTEGER      NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'NOT_STARTED',
    ai_score            INTEGER      NOT NULL DEFAULT 0,
    earned_xp           INTEGER      NOT NULL DEFAULT 0,
    ai_evaluation_json  TEXT,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_quest_progress_user_quest ON quest_progress (user_id, quest_id);
CREATE INDEX idx_quest_progress_user_id ON quest_progress (user_id);

CREATE TABLE quest_history
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(255) NOT NULL,
    quest_id   VARCHAR(255) NOT NULL,
    act_id     INTEGER      NOT NULL,
    score      INTEGER      NOT NULL DEFAULT 0,
    grade      VARCHAR(10)  NOT NULL DEFAULT 'D',
    passed     BOOLEAN      NOT NULL DEFAULT FALSE,
    earned_xp  INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quest_history_user_id ON quest_history (user_id);
CREATE INDEX idx_quest_history_user_quest ON quest_history (user_id, quest_id);
