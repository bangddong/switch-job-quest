-- 코딩 문제 저장
CREATE TABLE coding_problem (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    difficulty  VARCHAR(20)  NOT NULL,
    language    VARCHAR(20)  NOT NULL,
    solution_code TEXT       NOT NULL,
    test_cases  TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 사용자 코딩 레벨
CREATE TABLE user_coding_level (
    user_id    VARCHAR(255) PRIMARY KEY,
    level      INTEGER      NOT NULL DEFAULT 1,
    solve_count INTEGER     NOT NULL DEFAULT 0,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 사용자 제출 기록
CREATE TABLE coding_submission (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    problem_id  BIGINT       NOT NULL REFERENCES coding_problem(id),
    language    VARCHAR(20)  NOT NULL,
    user_code   TEXT         NOT NULL,
    passed      BOOLEAN      NOT NULL DEFAULT FALSE,
    judge_result TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coding_submission_user ON coding_submission(user_id);
