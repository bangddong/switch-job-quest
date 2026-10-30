-- U-6: 레이트 리밋 카운터를 인스턴스 메모리에서 공유 DB 로 옮긴다.
-- 평일 창 동안 EKS, 그 외 Fly 가 같은 Postgres 를 쓰므로(📌D-016) 전환 때마다 메모리 카운터가 갈라져
-- 1인 AI 한도가 구조적으로 2배가 되던 문제를 없앤다. core-api 와 daily-api 가 같은 테이블을 본다.
--
-- 한 행 = (종류, 키, KST 날짜) 의 오늘 사용 횟수. 날짜가 PK 의 일부라 자정 리셋이 필요 없고
-- 지난 날짜 행은 RateLimitResetScheduler 가 2일 뒤 지운다.
--
-- 컬럼 이름 주의: `key`·`day` 는 H2 예약어라 쓰지 않는다(테스트가 H2 로 스키마를 만든다).
CREATE TABLE rate_limit_counter (
    scope      VARCHAR(64) NOT NULL,           -- 한도 종류(tech-interview, daily-explain, daily-explain-global ...)
    limit_key  VARCHAR(64) NOT NULL,           -- 클라이언트 IP·사용자 ID 의 SHA-256 hex (원문을 저장하지 않는다)
    limit_date DATE        NOT NULL,           -- 앱이 계산한 KST 날짜 (DB CURRENT_DATE 는 서버 TZ 에 의존해 쓰지 않는다)
    used       INT         NOT NULL,           -- 오늘 사용한 횟수
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),   -- 어댑터 쿼리도 INSERT·UPDATE 때 NOW() 로 채운다(앱이 시각을 바인딩하지 않는다)
    PRIMARY KEY (scope, limit_key, limit_date)
);

-- 정리 DELETE (limit_date < ?) 가 PK(scope 선두)로는 인덱스를 못 타므로 날짜 인덱스를 둔다.
CREATE INDEX idx_rate_limit_counter_limit_date ON rate_limit_counter (limit_date);
