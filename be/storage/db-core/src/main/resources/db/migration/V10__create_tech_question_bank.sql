CREATE TABLE tech_question_bank (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(100) NOT NULL,
    question TEXT NOT NULL,
    reference_url VARCHAR(500),
    source VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tech_question_bank_category ON tech_question_bank(category);

INSERT INTO tech_question_bank (category, question, reference_url, source) VALUES
('java-spring', 'Spring Bean의 기본 스코프인 싱글톤(singleton)이 스레드 안전(thread-safe)하지 않을 수 있는 이유와, 상태를 가진 Bean을 안전하게 다루는 방법을 설명해주세요.', NULL, 'manual-seed'),
('concurrency', 'ConcurrentHashMap이 HashTable이나 Collections.synchronizedMap()에 비해 동시성 처리에서 갖는 장점을 세그먼트/버킷 단위 락 관점에서 설명해주세요.', NULL, 'manual-seed'),
('database', 'JPA에서 N+1 문제가 발생하는 원인과, fetch join·엔티티 그래프(EntityGraph)·batch size 설정을 이용한 해결 방법의 차이를 설명해주세요.', NULL, 'manual-seed'),
('java-spring', '@Transactional의 전파(propagation) 속성 중 REQUIRES_NEW와 REQUIRED의 차이를 예시와 함께 설명해주세요.', NULL, 'manual-seed'),
('system-design', '대용량 트래픽 환경에서 캐시 무효화(cache invalidation) 전략을 설계할 때 고려해야 할 요소(TTL, write-through, write-behind 등)를 설명해주세요.', NULL, 'manual-seed');
