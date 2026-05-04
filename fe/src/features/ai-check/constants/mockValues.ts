import { AI_FORMS } from './formConfig'

export const MOCK_FORM_VALUES = {
  '1-1': {
    skills: ['Kotlin', 'Spring Boot', 'JPA', 'MySQL', 'Redis', 'Docker'],
    targetRole: '시니어 백엔드 개발자 (Kotlin/Spring Boot)',
  },
  '1-2': {
    dissatisfactions: [
      '레거시 코드 비중이 높아 새로운 기술을 적용할 기회가 없습니다',
      '기술적 의사결정 과정에 참여하기 어렵고 수직적인 구조입니다',
      '팀 전체가 성장보다 유지에 집중하여 도전적인 프로젝트가 없습니다',
    ],
    goals: [
      'MSA 아키텍처를 직접 설계하고 운영하며 기술 역량을 확장하고 싶습니다',
      '코드 리뷰와 테스트 주도 개발이 정착된 환경에서 일하고 싶습니다',
      '3년 내 테크 리드로 성장하여 팀 기술 방향성을 이끌어가고 싶습니다',
    ],
    fiveYearVision:
      '5년 후에는 대용량 트래픽을 처리하는 결제 시스템의 리드 엔지니어로서 10명 이상의 팀에서 기술적 의사결정을 담당하고 싶습니다. MSA 전환을 주도하고 주니어 개발자를 멘토링하며 팀 전체의 기술 수준을 끌어올리는 역할을 하고 싶습니다. 또한 오픈소스 프로젝트에 기여하고 기술 블로그를 운영하여 개발 커뮤니티에 지식을 환원하는 개발자가 되고자 합니다.',
  },
  '2-2': {
    techTopic: 'JVM G1GC 튜닝으로 Full GC 제거하기',
    title: '실무에서 G1GC 튜닝으로 평균 응답속도 40% 개선한 방법',
    content: `## 배경\n\n결제 서버에서 매일 새벽 2시마다 Full GC가 발생해 평균 응답시간이 1.2초에서 8초로 급등하는 문제가 있었습니다. 서비스 특성상 새벽에도 결제 요청이 끊이지 않아 고객 이탈이 심각했습니다.\n\n## 원인 분석\n\nJVM 힙 덤프와 GC 로그를 분석한 결과, Old Gen이 80% 이상 찬 상태에서 Concurrent Mark Sweep GC가 실패하며 Full GC로 fallback되는 패턴을 확인했습니다. 원인은 두 가지였습니다. 첫째, 결제 이력 캐싱 로직에서 대용량 Map 객체를 장시간 보유하고 있었고, 둘째, G1GC가 아닌 CMS GC를 사용 중이었습니다.\n\n## 해결 방법\n\n### GC 변경\n\nJava 11부터 기본 GC인 G1GC로 전환했습니다.\n\n\`\`\`\n-XX:+UseG1GC\n-XX:MaxGCPauseMillis=200\n-XX:G1HeapRegionSize=16m\n-XX:InitiatingHeapOccupancyPercent=45\n\`\`\`\n\n### 캐싱 구조 개선\n\nCaffeine Cache를 도입하고 TTL 5분, 최대 엔트리 10000개로 제한했습니다.\n\n\`\`\`kotlin\n@Bean\nfun paymentCache(): Cache<String, PaymentDetail> {\n    return Caffeine.newBuilder()\n        .maximumSize(10_000)\n        .expireAfterWrite(5, TimeUnit.MINUTES)\n        .recordStats()\n        .build()\n}\n\`\`\`\n\n### 결과\n\n- Full GC 발생 횟수: 일 12회 → 0회\n- 평균 응답시간: 1.2s → 0.72s (40% 개선)\n- GC 일시정지 시간: 평균 2.1s → 180ms\n- Micrometer + Prometheus로 GC 지표 실시간 모니터링 추가\n\n## 결론\n\nG1GC 전환만으로도 상당한 개선이 가능하지만, 근본 원인인 메모리 누수 패턴을 함께 개선해야 효과가 지속됩니다. JVM GC 튜닝은 반드시 실제 워크로드 측정 결과를 기반으로 진행해야 합니다.`,
  },
  '2-3': {
    problemStatement: '하루 100만 주문을 처리하는 배달앱 주문 시스템 설계',
    architectureDescription:
      '클라이언트 요청은 API Gateway를 통해 주문 서비스로 라우팅됩니다. 주문 서비스는 Redis로 재고를 확인한 후 주문 레코드를 MySQL에 저장하고, Kafka에 주문 이벤트를 발행합니다. 결제 서비스는 Kafka 이벤트를 소비해 외부 PG사와 통신합니다. 결제 완료 이벤트가 발행되면 배달 서비스와 알림 서비스가 각각 처리합니다. 서비스 간 통신은 Kafka 비동기 방식을 기본으로 하고, 재고 확인처럼 즉각 응답이 필요한 경우만 gRPC 동기 호출을 사용합니다.',
    considerations: [
      'Redis 캐시 레이어로 재고 조회 DB 부하를 80% 감소시킵니다',
      'Kafka를 활용한 비동기 주문 처리로 서비스 간 결합도를 낮추고 확장성을 확보합니다',
      'Circuit Breaker(Resilience4j)로 외부 PG사 장애 시 서비스 가용성을 유지합니다',
      'DB Replication(1 Primary + 2 Replica)으로 읽기/쓰기를 분리해 가용성을 확보합니다',
    ],
  },
  '3-2': {
    companyName: '토스',
    jobDescription: `[토스 백엔드 개발자 채용]\n\n포지션: Server Developer\n\n[주요 업무]\n- 토스 결제/송금 핵심 서버 개발\n- 대용량 트래픽 처리를 위한 시스템 설계 및 구현\n- MSA 기반 서비스 개발 및 운영\n\n[자격 요건]\n- Java 또는 Kotlin 기반 Spring Boot 개발 경험 3년 이상\n- RDBMS(MySQL/PostgreSQL) 및 NoSQL(Redis) 활용 경험\n- 분산 시스템 이해 및 대용량 트래픽 처리 경험\n- RESTful API 설계 및 구현 능력\n\n[우대 사항]\n- Kafka 등 메시지 큐 시스템 활용 경험\n- Kubernetes 기반 컨테이너 운영 경험\n- 금융/결제 도메인 경험\n- 오픈소스 기여 경험`,
    userSkills: [
      'Kotlin (3년)',
      'Spring Boot (4년)',
      'JPA/Hibernate',
      'MySQL + Redis',
      'Docker/Kubernetes',
    ],
    userExperiences: [
      '결제 API 개발 및 TPS 3배 성능 개선 (2년 경험)',
      '레거시 모놀리식 서비스 MSA 전환 프로젝트 주도',
      '일 500만 건 처리 배치 시스템 설계 및 구축',
    ],
  },
  '4-1': {
    targetCompany: '카카오페이',
    targetJd:
      'Kotlin/Spring Boot 기반 백엔드 개발, 결제/금융 도메인 경험 우대, 대용량 트래픽 처리, MSA 설계 경험, Redis/Kafka 활용 경험',
    resumeContent: `[경력]\n\n(주)테크스타트업 | 백엔드 개발자 | 2020.03 ~ 현재 (5년)\n\n■ 결제 시스템 성능 개선 (2023.06 ~ 2023.12)\n- 상황: 결제 API 평균 응답시간 1.8초, 피크 타임 TPS 한계로 서비스 장애 발생\n- 과제: 결제 API 응답시간 1초 이하, TPS 3배 향상 목표\n- 행동: Redis 캐시 레이어 도입, DB 쿼리 최적화(N+1 제거), DB Replication 적용\n- 결과: 평균 응답시간 1.8s → 0.4s (78% 개선), TPS 150 → 450 달성, 장애 0건\n\n■ 레거시 MSA 전환 (2022.01 ~ 2022.12)\n- 상황: 모놀리식 서비스 배포 주기 2주, 장애 시 전체 서비스 영향\n- 과제: 핵심 도메인(주문/결제/배달) MSA 분리\n- 행동: DDD 도입, 서비스 경계 정의, Kafka 기반 이벤트 드리븐 아키텍처 설계\n- 결과: 배포 주기 2주 → 1일, 장애 격리로 가용성 99.9% 달성\n\n[기술 스택]\nKotlin, Spring Boot 3.x, JPA, MySQL, Redis, Kafka, Docker, Kubernetes`,
  },
  '4-BOSS': {
    resumeContent: `[경력]\n\n(주)테크스타트업 | 백엔드 개발자 | 2020.03 ~ 현재 (5년)\n\n■ 결제 시스템 성능 개선\n- Redis 캐시 도입으로 API 응답시간 78% 개선 (1.8s → 0.4s)\n- TPS 150 → 450 달성\n\n■ MSA 전환 리드\n- 모놀리식 → MSA 전환으로 배포 주기 2주 → 1일\n- Kafka 이벤트 드리븐 아키텍처 설계\n\n[기술 스택]\nKotlin, Spring Boot, JPA, MySQL, Redis, Kafka, Docker, k8s`,
    githubUrl: 'https://github.com/bangddong',
    blogUrl: 'https://devlog.tistory.com',
    targetPosition: '시니어 백엔드 개발자',
  },
  '5-1': {
    question: '납득이 가지 않는 업무를 맡게 되었을 때 어떻게 하시겠습니까?',
    answer:
      '먼저 업무의 배경과 목적을 파악하기 위해 담당자나 팀장에게 구체적으로 질문합니다. 제가 이해하지 못한 맥락이 있을 수 있기 때문입니다. 충분한 설명을 들은 후에도 납득이 되지 않는다면, 제 우려 사항을 데이터와 근거를 들어 명확하게 전달합니다. 예를 들어 과거 레거시 시스템을 단기간에 교체하라는 지시를 받았을 때, 기술 부채 현황과 위험도를 문서로 정리해 2단계 마이그레이션 계획을 역제안하여 수용된 경험이 있습니다. 최종 결정이 제 의견과 다르더라도, 일단 팀의 방향에 맞춰 최선을 다해 진행합니다. 개인 판단보다 팀의 협업과 신뢰가 장기적으로 더 중요하다고 생각합니다.',
  },
} satisfies Partial<Record<keyof typeof AI_FORMS, Record<string, unknown>>>

export const MOCK_INTERVIEW_SAMPLE_ANSWERS: Record<string, string> = {
  q1: '데이터베이스 인덱스는 내부적으로 B-Tree 구조로 구현되어 있습니다. 루트 노드부터 리프 노드까지 내려가며 O(log n)으로 데이터를 탐색합니다. 잘못 사용하면 카디널리티가 낮은 컬럼(예: boolean)에 인덱스를 걸어 Full Scan보다 느려지거나, 복합 인덱스에서 선두 컬럼을 빠뜨려 인덱스가 사용되지 않는 문제가 발생합니다. 실무에서 EXPLAIN으로 쿼리 실행 계획을 확인하고, 쓰기 빈번한 테이블에는 인덱스 수를 최소화해 INSERT/UPDATE 부하를 줄입니다.',
  q2: 'JVM GC는 크게 Serial, Parallel, CMS, G1GC, ZGC로 구분됩니다. 모든 GC는 살아있는 객체 그래프를 탐색하는 Mark 단계에서 힙 일관성을 보장하기 위해 애플리케이션 스레드를 멈추는 Stop-The-World가 발생합니다. 특히 Full GC는 Old 영역까지 수집하므로 STW가 길어집니다. 실무에서는 G1GC를 기본으로 사용하고, -Xms/-Xmx를 동일하게 설정해 힙 리사이즈 STW를 제거하며, GC 로그를 수집해 튜닝 근거로 활용합니다.',
  q3: 'TLS Handshake는 클라이언트가 ClientHello를 보내며 지원 암호화 스펙을 알리고, 서버가 ServerHello로 선택된 스펙과 인증서를 전달하는 것으로 시작됩니다. 클라이언트는 인증서를 CA 체인으로 검증 후 Pre-Master Secret을 서버 공개키로 암호화해 전송합니다. 양측이 이를 이용해 동일한 세션 키를 도출하고, 이후 대칭 암호화로 통신합니다. TLS 1.3에서는 RTT를 1회로 줄여 초기 연결 지연이 개선됐습니다.',
  q4: 'MSA는 서비스별 독립 배포와 기술 스택 선택이 가능하지만 분산 트랜잭션, 네트워크 레이턴시, 운영 복잡도가 증가합니다. Monolithic은 초기 개발 속도가 빠르고 트랜잭션 관리가 단순하지만 배포 단위가 커 장애 영향 범위가 넓습니다. 저는 팀 규모 10명 미만이거나 도메인 경계가 불명확한 초기 단계에서는 Monolithic을 선택하고, 서비스가 안정화되고 특정 도메인의 확장성 요구가 뚜렷해지면 점진적으로 분리하는 방식을 선호합니다.',
  q5: 'Optimistic Lock은 버전 컬럼으로 충돌을 감지하고 커밋 시점에 롤백합니다. 충돌이 드문 읽기 중심 환경에 적합합니다. Pessimistic Lock은 SELECT FOR UPDATE로 트랜잭션 시작 시 락을 획득해 충돌 자체를 방지합니다. 충돌 빈도가 높거나 데이터 정합성이 절대적으로 중요한 재고 차감, 포인트 사용 등에 사용합니다. 실무에서 Optimistic Lock은 ObjectOptimisticLockingFailureException 재시도 로직을 함께 구현해야 합니다.',
  q6: '@Transactional은 AOP 프록시를 통해 동작합니다. 스프링이 빈을 감싸는 프록시 객체를 생성하고, 메서드 호출 시 트랜잭션을 시작·커밋·롤백합니다. 주의사항으로 self-invocation 문제가 있는데, 같은 클래스 내 메서드 호출은 프록시를 거치지 않아 트랜잭션이 적용되지 않습니다. 또한 기본 rollbackFor는 RuntimeException이므로 Checked Exception은 명시적으로 지정해야 합니다. readOnly=true 설정 시 Hibernate의 dirty checking을 생략해 성능을 높일 수 있습니다.',
  q7: 'Process는 독립된 메모리 공간을 가지며 OS 자원을 독립적으로 사용합니다. Thread는 같은 프로세스 내에서 힙과 메서드 영역을 공유합니다. Thread-safe 코드를 위해서는 공유 상태를 최소화하고, 불가피하면 synchronized, ReentrantLock, 또는 Atomic 클래스를 사용합니다. 실무에서는 Java의 ConcurrentHashMap, AtomicInteger 같은 스레드 안전 컬렉션을 우선 사용하고, 직접 동기화는 범위를 최소화해 데드락 위험을 줄입니다.',
  q8: 'hashCode()는 객체를 해시 기반 컬렉션(HashMap, HashSet)에서 빠르게 탐색하기 위한 버킷 인덱스를 결정합니다. equals()는 동등성을 비교합니다. 두 메서드는 반드시 함께 재정의해야 하며, equals()가 true면 hashCode()도 같아야 합니다. 반대는 성립하지 않아도 됩니다. hashCode 구현 시 Objects.hash()를 사용하거나 핵심 필드를 31 곱산으로 조합합니다. 롬복의 @EqualsAndHashCode 사용 시 JPA 엔티티에서 연관관계 필드 포함 여부를 주의해야 합니다.',
  q9: 'Slow Query 발견 시 먼저 EXPLAIN ANALYZE로 실행 계획을 확인해 Full Table Scan, 잘못된 인덱스 선택 여부를 파악합니다. 원인이 인덱스 누락이면 추가하고, 인덱스가 있어도 사용 안 되는 경우 함수 적용이나 묵시적 형변환 여부를 확인합니다. N+1 문제라면 Fetch Join이나 Batch Size 조정으로 해결합니다. 튜닝 후 쿼리 실행 시간을 재측정하고, 임계값을 초과하는 쿼리는 slow_query_log와 APM(Pinpoint, Datadog)으로 모니터링합니다.',
  q10: '브라우저는 DNS 조회로 도메인을 IP로 변환하고(캐시 없으면 재귀 쿼리), TCP 3-way Handshake로 연결을 수립합니다. HTTPS이므로 TLS Handshake를 거쳐 암호화 세션을 생성합니다. 이후 HTTP GET 요청을 전송하면 서버가 HTML을 응답합니다. 브라우저는 HTML을 파싱해 DOM을 구성하고 CSS로 CSSOM을 만들어 렌더 트리를 생성합니다. JS 실행, 레이아웃, 페인트 단계를 거쳐 화면에 표시됩니다. CDN 캐시 HIT 시 원본 서버 요청 없이 엣지에서 응답합니다.',
}
