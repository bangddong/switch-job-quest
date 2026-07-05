-- 질문 뱅크 wiki 시드 2026-07 — E:/development/wiki tech/·ai-llm/ concept 페이지 큐레이션
-- 기존 V10 manual-seed와 주제 중복 없음 (N+1은 기존 커버로 제외, 전파·캐시는 다른 각도)

INSERT INTO tech_question_bank (category, question, reference_url, source) VALUES
-- database: 트랜잭션 격리수준
('database', '트랜잭션 격리수준 4단계와 각 단계에서 발생 가능한 이상 현상(Dirty Read, Non-Repeatable Read, Phantom Read)을 설명하고, MySQL InnoDB의 REPEATABLE READ가 표준과 달리 팬텀 리드를 대부분 방지할 수 있는 이유를 갭 락(gap lock) 관점에서 설명해주세요.', 'https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html', 'wiki:tech/pages/database/transaction-isolation-levels.md'),
('database', 'READ COMMITTED 격리수준에서 재고 차감 같은 read-modify-write 로직에 lost update가 발생하는 시나리오를 설명하고, 비관적 락·낙관적 락·원자적 UPDATE 세 가지 해결 방법의 트레이드오프를 비교해주세요.', 'https://www.postgresql.org/docs/current/transaction-iso.html', 'wiki:tech/pages/database/transaction-isolation-levels.md'),
-- database: B+Tree 인덱스
('database', 'RDB 인덱스가 해시 테이블이나 이진 탐색 트리가 아닌 B+Tree로 구현된 이유를 디스크 I/O와 범위 검색 관점에서 설명해주세요.', 'https://use-the-index-luke.com/sql/anatomy', 'wiki:tech/pages/database/btree-index.md'),
('database', '인덱스가 걸려 있어도 인덱스를 타지 못하는 쿼리 패턴을 3가지 이상 들고, 각각을 어떻게 재작성해야 하는지 설명해주세요. (예: 컬럼 가공, 앞쪽 와일드카드 LIKE, 복합 인덱스 순서 위반, 암묵적 형변환)', 'https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html', 'wiki:tech/pages/database/btree-index.md'),
-- database: MVCC
('database', 'MVCC가 "읽기는 쓰기를 막지 않는다"를 달성하는 원리를 설명하고, InnoDB(undo log)와 PostgreSQL(튜플 버전 + VACUUM)의 구현 차이, 그리고 두 방식 모두에서 장기 트랜잭션이 위험한 이유를 설명해주세요.', 'https://www.postgresql.org/docs/current/mvcc-intro.html', 'wiki:tech/pages/database/mvcc.md'),
-- java-spring: 트랜잭션 전파 — 프록시/self-invocation 각도 (전파 옵션 자체는 V10 기존 질문이 커버)
('java-spring', '@Transactional이 붙은 메서드를 같은 클래스 내부에서 this로 호출하면 트랜잭션이 적용되지 않는 이유를 AOP 프록시 동작 원리로 설명하고, 해결 방법을 제시해주세요.', 'https://docs.spring.io/spring-framework/reference/core/aop/proxying.html', 'wiki:tech/pages/java-spring/spring-transaction-propagation.md'),
('java-spring', 'REQUIRED 전파로 합류한 내부 메서드에서 던져진 예외를 외부에서 catch했는데도 커밋 시 UnexpectedRollbackException이 발생하는 이유(rollback-only 마킹)를 설명해주세요.', 'https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html', 'wiki:tech/pages/java-spring/spring-transaction-propagation.md'),
-- java-spring: 영속성 컨텍스트
('java-spring', 'JPA에서 repository.save()를 호출하지 않았는데 트랜잭션 커밋 시 UPDATE 쿼리가 실행되는 이유를 영속성 컨텍스트의 dirty checking과 flush 타이밍으로 설명해주세요.', 'https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#pc', 'wiki:tech/pages/java-spring/jpa-persistence-context.md'),
('java-spring', 'OSIV(Open Session In View)를 켰을 때와 껐을 때의 트레이드오프를 DB 커넥션 점유 관점에서 설명하고, API 서버에서 어떤 설정을 권장하는지 근거와 함께 설명해주세요.', 'https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html', 'wiki:tech/pages/java-spring/jpa-persistence-context.md'),
-- java-spring: GC
('java-spring', 'G1 GC가 리전(region) 단위 설계로 pause 목표를 달성하는 원리를 설명하고, ZGC가 컬러드 포인터와 load barrier로 1ms 미만 pause를 달성하는 방식과 그 대가(처리량·메모리)를 비교해주세요.', 'https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html', 'wiki:tech/pages/jvm/garbage-collection.md'),
('java-spring', '컨테이너 환경에서 JVM 힙 OOM(OutOfMemoryError)과 컨테이너 OOM 킬(exit 137)이 어떻게 다른지 설명하고, MaxRAMPercentage 설정 시 비힙 메모리(Metaspace, 스레드 스택 등) 예산을 고려해야 하는 이유를 설명해주세요.', 'https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html', 'wiki:tech/pages/jvm/garbage-collection.md'),
-- concurrency: 가상 스레드
('concurrency', 'JDK 21 가상 스레드가 블로킹 I/O 시점에 캐리어 스레드에서 언마운트되는 원리를 설명하고, synchronized 블록에서 발생하는 pinning 문제와 해결 방법을 설명해주세요.', 'https://openjdk.org/jeps/444', 'wiki:tech/pages/jvm/virtual-threads.md'),
('concurrency', '가상 스레드를 도입해도 DB 커넥션 풀이 여전히 동시성 병목이 되는 이유와, 가상 스레드를 스레드 풀에 넣지 말아야 하는 이유를 설명해주세요.', 'https://spring.io/blog/2022/10/11/embracing-virtual-threads', 'wiki:tech/pages/jvm/virtual-threads.md'),
-- concurrency: JMM
('concurrency', 'volatile 변수에 count++를 수행해도 동시성 버그가 발생하는 이유를 가시성과 원자성을 구분해 설명하고, AtomicInteger의 CAS 방식과 LongAdder를 언제 선택하는지 설명해주세요.', 'https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4', 'wiki:tech/pages/jvm/java-memory-model.md'),
('concurrency', 'Java 메모리 모델의 happens-before 관계가 왜 필요한지 CPU 캐시·명령어 재정렬 관점에서 설명하고, volatile 쓰기가 그 이전의 모든 쓰기를 함께 publish하는 원리를 설명해주세요.', 'https://jenkov.com/tutorials/java-concurrency/java-memory-model.html', 'wiki:tech/pages/jvm/java-memory-model.md'),
-- system-design: 분산 락
('system-design', '스케일아웃 환경에서 JVM 락(synchronized)이 무력한 이유를 설명하고, Redis 분산 락에서 TTL과 작업 시간의 불일치로 발생하는 문제와 Redisson 워치독이 이를 해결하는 방식을 설명해주세요.', 'https://redis.io/docs/latest/develop/use/patterns/distributed-locks/', 'wiki:tech/pages/infra/distributed-lock.md'),
('system-design', '분산 락의 범위가 트랜잭션 범위보다 커야 하는 이유를 설명해주세요. (락 해제가 커밋보다 먼저일 때 발생하는 정합성 문제)', 'https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html', 'wiki:tech/pages/infra/distributed-lock.md'),
-- system-design: 캐시 장애 패턴 (무효화 전략 자체는 V10 기존 질문이 커버)
('system-design', '캐시 스탬피드(thundering herd), 캐시 관통(penetration), 캐시 눈사태(avalanche)가 각각 어떤 상황에서 발생하는지 설명하고, 각각의 대응 방법(락 기반 재적재, null 캐싱·블룸 필터, TTL 지터)을 설명해주세요.', 'https://aws.amazon.com/caching/best-practices/', 'wiki:tech/pages/infra/cache-strategies.md'),
-- system-design: 메시지 큐
('system-design', 'Kafka와 RabbitMQ의 구조적 차이(분산 로그 vs 브로커 큐)를 설명하고, 어떤 기준으로 선택할지 메시지 재생(replay)·라우팅·처리량 관점에서 비교해주세요.', 'https://kafka.apache.org/documentation/#design', 'wiki:tech/pages/infra/message-queue.md'),
('system-design', 'at-least-once 전달에서 발생하는 메시지 중복을 멱등 소비자로 처리하는 방법과, DB 저장과 이벤트 발행의 원자성을 보장하는 Transactional Outbox 패턴을 설명해주세요.', 'https://microservices.io/patterns/data/transactional-outbox.html', 'wiki:tech/pages/infra/message-queue.md'),
-- ai-llm: LLM Wiki 패턴
('ai-llm', 'RAG가 쿼리마다 원본 문서에서 지식을 재유도하는 것과 달리, LLM이 지식을 위키 형태로 점진적으로 컴파일·유지하는 패턴(Karpathy LLM Wiki)의 장점과 트레이드오프를 설명해주세요.', NULL, 'wiki:ai-llm/pages/patterns/llm-wiki-pattern.md');
