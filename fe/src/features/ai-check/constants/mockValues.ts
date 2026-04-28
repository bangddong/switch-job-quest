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

export const MOCK_INTERVIEW_SAMPLE_ANSWER =
  'HashMap의 내부 구조는 배열 기반의 버킷 구조입니다. 키를 해시 함수로 변환한 값을 인덱스로 사용해 버킷에 저장합니다. Java 8부터는 충돌이 많을 경우(동일 버킷 8개 이상) 연결 리스트에서 레드-블랙 트리로 전환해 탐색 성능을 O(n)에서 O(log n)으로 개선합니다. 로드 팩터 기본값은 0.75로, 저장 용량의 75%가 채워지면 배열을 2배로 리해시합니다. 리해시 시 모든 요소를 재배치하므로 O(n) 비용이 발생합니다. 실무에서는 초기 용량을 예상 크기의 1.5배로 설정해 불필요한 리해시를 방지합니다.'
