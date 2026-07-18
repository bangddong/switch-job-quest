# 작업 컨텍스트

> 새 대화 시작 시 이 파일을 먼저 읽으세요.
> 전체 작업 이력은 `.claude/CONTEXT.archive.md` 참조.

## 현재 상태

| 항목 | 내용 |
|------|------|
| 브랜치 | (없음 — main) |
| 열린 PR | 없음 |

## 최근 완료 (최근 3건)

| PR/커밋 | 내용 | 날짜 |
|---------|------|------|
| #285 | **EKS 1-network 레이어 완성.** VPC(10.0.0.0/16, DNS ON) + IGW + 퍼블릭 서브넷 ×2(ap-northeast-2a/2c, /20, 공인IP 자동) + 라우트(0.0.0.0/0→IGW). **NAT 회피**(퍼블릭 서브넷+노드 공인IP, $32/mo 절약) — tfsec `no-public-ip`·`vpc-flow-logs`는 근거 ignore. EKS discovery 태그(`role/elb=1`, `cluster/devquest-eks=shared`). **CI 도그푸딩**: infra-deploy matrix에 `1-network` 추가 → PR plan / merge apply(OIDC)로 VPC 생성, CLI 실측 검증. 비용 $0. 머지 완료 | 2026-07-18 |
| #283 | **EKS 0-bootstrap 레이어 완성 (IaC-first, 콘솔 클릭 0).** remote backend(S3+DynamoDB 락) + state S3 이관 / 예산 `aws_budgets_budget`(크레딧 제외, $10/$50/$150 알림) / 보안 CI 2층 `infra-ci.yml`(gitleaks+tfsec) / GitHub OIDC + IAM 역할(admin + 신뢰정책 `repo:...:main`·`pull_request` 강잠금) / `infra-deploy.yml` plan-on-PR·apply-on-merge. **양방향 OIDC 실증**(장기키 0개). 비용 $0. 머지 완료 | 2026-07-18 |
| #271 | EKS 작업 일지 체계 도입 — `docs/eks-migration-log.md`(블로그 원고 소스, 누적 비용 테이블) + CLAUDE.md "EKS 작업 일지 규칙"(즉시 append·태그 체계·에이전트 전파, EKS 종료 시 제거). 머지 완료 | 2026-07-16 |

## 다음 작업

### 코드 작업
- [ ] **파이프라인 후속 (사용자 확인 대기)**: ① 모바일 실기기 확인 (데스크톱 시나리오는 Claude가
      prod 테스트 완료) ② 테스트 데이터 정리 — 회사 "테스트-토스" 삭제, 임시 이력서를 실제로 교체
- [ ] **Phase 4 후보 (실사용 후 판단)**: 면접 회고 메모(activity NOTE 타입), 같은 회사 카드
      그룹핑 뷰, JD 등록/수정 모달(현재 AddCompanyModal에서만 입력 가능), Phase 3c(JD URL 파싱)
- [ ] tech-debt(LOW): CompanyCard 삭제/상태변경 진행 중 busy 플래그(연타 중복 요청 가드, #259 QA LOW),
      FE 테스트 러너 미도입(vitest 등 — 인프라 도입 여부 별도 결정 필요, #259에서 확인),
      extractPdfText LOW 3건(#261 QA — destroy 실패 시 원 예외 덮어쓰기, `\r` 정규식 엣지,
      pdfjs 로딩/파싱 실패 메시지 미구분)
- [ ] **#261 후속**: 배포 후 실제 PDF 이력서로 추출 품질 확인(줄바꿈·표 레이아웃 깨짐 정도).
      BE 파싱(PDFBox) 구현은 로컬 `backup/be-pdf-parse` 브랜치 보존 — 스케일업 결정 시 재활용
- [ ] Phase 3a MEDIUM 보류: UserResumeAdapter upsert read-then-write 경합 — 다중 기기 동시
      사용 필요해지면 DB ON CONFLICT 전환
- [ ] **OOM 후속 관찰** (#245 swap 배포 후) — 07-10 3.3일차 실측: kill 0건(마지막 kill 07-07 00:01
      = 스왑 배포 직전, anon-rss 409MB), 스왑 소비 22~32MB/일 선형(배포 재시작 시 리셋),
      mem_available 12~47MB 바닥권 지속 → 무배포 8~10일 시 스왑 소진·재발 가능성 🟡.
      → **JVM 다이어트(#263)로 근본 대응 착수·배포 완료(07-13)**. 이후 검증은 위 "post-deploy 관측" 항목으로 이관.
- [x] ~~메타스페이스 누수 조사~~ → **2026-07-15 종결. 누수 없음.** 아래 "비자명적 결정" 참조.
      잔여 관찰(선택): 신규 기능으로 클래스가 늘면 작동점 134.6 MiB가 올라간다. 160 MiB 여유는
      25.4 MiB(16%)뿐이므로 **대형 의존성 추가 시 Grafana로 작동점 재확인**할 것.
- [ ] tech-debt(LOW, BE): `DailyExplainRateLimitInterceptor`·`TechInterviewRateLimitInterceptor`가
      **요청마다 `ObjectMapper()` 신규 생성** (07-15 조사 중 발견). 메타스페이스와 무관하나 힙·CPU 낭비 →
      싱글턴 주입으로 교체
- [ ] 에이전트 Disambiguation Gate / Closing Summary 미비점 보완 (Gate 횟수 상한, 트리거 기준 명시 — 실사용 경험 더 쌓은 뒤 결정)
- [ ] **#255 후속**: 다음 기능 작업에서 Blindspot Pass 실효성 확인 (Deviations→QA 집중검토 흐름은
      #259에서 1차 동작 확인. template 동기화는 07-10 완료 — orchestrator·clarify·quiz + 훅 스크립트 3종)
- [ ] 질문 뱅크 category 파라미터 — 현재 DailyMailScheduler가 항상 null로 호출해 카테고리 분기가
      죽은 경로 (QA MEDIUM, 의도적 보류). 향후 카테고리별 배분 쓸 계획 생기면 활성화
- [ ] 질문 뱅크 규모 확대 시(수백 건↑) `findAllBy...` 전체 로드 방식 재검토 — `ORDER BY RANDOM() LIMIT 1`
      native query 전환 고려 (단, `@DataJpaTest` 등 native query 검증 인프라 먼저 필요)

### 사용자 확인 필요
- [ ] 앱 직접 사용 후 불편한 점 / 빠진 기능 파악 → 다음 기능 기획
- [ ] **#257 후속 — 휘발형 학습 실사용 확인**: 후속 질문 UX(단발형·5회/일 제한 적정성), AI 설명 품질.
      만족스러우면 **Phase B(축적형 복습노트)** 착수 판단 — 모르는 개념/오답 저장 → 나중에 복습(로그인·DB·간격 반복, RPG XP 연동). 지금은 보류.

### 백로그
- [ ] **Spring 시작 시간 최적화** — 현재 cold start 시 2~3분 소요, 사용자 503 경험
  - 원인: 512MB shared CPU + Neon DB cold start + Flyway 실행 겹침
  - 방향: `spring.main.lazy-initialization=true` / `min_machines_running=1`(비용) / Neon PgBouncer

### AWS EKS 학습 놀이터 — 새 시작점 (07-13 확정, 07-16 kind 트랙 폐기로 단독 트랙化)
- **계획 문서: `infra/aws-eks/README.md`** — 착수 전 반드시 읽을 것 (비용 분석·기각안 포함)
- **작업 일지: `docs/eks-migration-log.md` 실시간 유지 의무** — 규칙은 루트 `CLAUDE.md`
  "EKS 작업 일지 규칙" 참조. 블로그 원고 소스. 서브에이전트 위임 시 규칙 전파 필수
- **정답 경로 튜토리얼: `docs/eks-tutorial-steps.md`** — 성공 확인된 절차만. 최상단 캡처 체크리스트로
  이미지 추적.
- **🎯 방향 확정 (07-16): IaC-first — "인프라 전부를 코드로, 콘솔 클릭 0".** 레이어별 state 분리
  `0-bootstrap`(remote backend·OIDC·IAM·예산·이상탐지) / `1-network` / `2-cluster`(destroy 대상) /
  `gitops`(ArgoCD). CI: plan-on-PR+tfsec, apply-on-merge(OIDC). 상세: `infra/aws-eks/README.md`.
  - **✅ 0-bootstrap 완료 (#283, 07-18):** backend(S3+DynamoDB)·state 이관·예산(콘솔판 삭제로 코드 일원화)·
    보안 CI(gitleaks+tfsec)·GitHub OIDC+IAM(admin+신뢰정책 강잠금)·plan/apply 파이프라인. PR→plan /
    merge→apply 양방향 OIDC 실증. 비용 $0. GitHub Secret `AWS_ROLE_ARN`·`BUDGET_EMAIL` 등록됨.
    로컬 자격증명 = `bootstrap-admin` 액세스키(`aws configure`, region ap-northeast-2).
  - **✅ 1-network 완료 (#285, 07-18):** VPC 10.0.0.0/16 + IGW + 퍼블릭 서브넷 ×2(2a/2c, 공인IP) +
    라우트. NAT 회피(퍼블릭+공인IP). EKS discovery 태그. CI 도그푸딩(matrix에 `1-network` 추가,
    merge→apply로 VPC 생성 실측). 비용 $0. 실측 VPC `vpc-0e8401b42ba207328`.
  - **➡️ 다음: 2-cluster (EKS 컨트롤플레인 + 노드그룹).** ⚠️⚠️ **여기서 비용 발생 시작** —
    EKS 컨트롤플레인 **$0.10/hr(방치 시 월 ~$73)** + 노드(t4g.small Spot) + EBS. **세션 종료 시
    `tofu destroy` 필수**(destroy-after-use). backend·예산·VPC는 $0라 유지. 착수 전 크레딧 잔여 재확인.
    - infra-deploy matrix에 `2-cluster` 추가 시 편입. 단 2-cluster는 destroy 왕복이 잦아 CI apply보다
      **로컬 apply/destroy가 나을 수 있음** — 착수 시 판단. `kubernetes`/`helm` 프로바이더 주의(README 81줄).
  - CI 관리 레이어 현재: `infra-deploy.yml` matrix `[0-bootstrap, 1-network]`.
  - IaC-first라 **캡처 필요량 급감** — 단계가 코드+CLI 텍스트. 잔여는 서사/증빙 소수.
- **🖼️ remote 세션 스크린샷 넣는 법 (헷갈리지 말 것)**: 채팅 인라인 이미지·파일은 실행 디스크에
  **안 닿고** 클립보드도 격리됨. → 사용자가 **캡처를 GitHub 댓글창에 Ctrl+V(자동 업로드) → 생성된
  `user-attachments` URL을 채팅에 전달** → 에이전트가 받아서 `docs/images/eks-tutorial/`에 저장.
  익명 접근은 404라 **`gh` 토큰 필요**: `curl -sSL -H "Authorization: token $(gh auth token)" -o <경로> <url>`.
  (PS5.1 `Invoke-WebRequest`는 실패 — curl 쓸 것.)
- 한 줄: **EKS를 OpenTofu로 세웠다 부수는 K8s 학습 놀이터.** destroy-after-use + $200 크레딧.
  **prod는 Fly($0) 그대로** (prod 이전은 검토 후 명시적 기각 — Fargate 상시 월 $35 = 크레딧 5.7개월 → 절벽)
- 착수 순서(IaC-first): 0-bootstrap(backend·OIDC·예산 코드) → CI(plan/apply) → 1-network →
  2-cluster(+즉시 destroy 왕복으로 teardown 체득) → gitops → 재현 검증. 상세 README 참조.
- ⚠️ NAT Gateway 금지(+$32/mo). Ingress·PVC를 tofu destroy보다 먼저 삭제. **tfstate git 커밋 금지**(public repo)
- **kind 로컬 트랙(Stage 1~3, `k8s/`)은 07-16 폐기·삭제** (#269) — README의 "kind 트랙 합류" 언급은
  무시, EKS 단독 트랙으로 진행. 복구 필요 시 git 히스토리(#225 시점) 참조

## 알아둬야 할 비자명적 결정

### 메타스페이스 조사 종결 — 누수 없음, 128m이 작동점보다 낮았을 뿐 (2026-07-15) 🔴
Grafana 7일 range 실측으로 확정. **재조사 불필요.**
- **이 앱의 메타스페이스 작동점 = 134.6 MiB.** 160m 하에서 **uptime 94.3h까지 평탄**(95포인트 연속 실측).
  #263의 `128m`은 **이 작동점보다 6.6 MiB 낮았다** → 죽는 게 필연이었다. 누수와 무관.
- **128m 창의 실제 모양**: 재시작 후 122.7 → 10.9h에 126.3 MiB(=상한의 **98.7%**) 도달 → **9시간 고정**
  (상한이 눌러서 Full GC로 버틴 것) → **uptime 19.9h에 전 지표 소실**(좀비화).
  **"34시간 후 OOME"는 틀린 기록** — 34h는 사람이 알아채고 #265를 배포한 시각. 실제 붕괴는 **~20h**.
- **누수 아님의 근거 3종** 🔴: ① 클래스 수 평탄~순감소(11분 +12개, 부하 중 -51개)
  ② **정지 30초 Δ=0 바이트** ③ 동일 부하 2R이 1R의 51%(감속). 시간이 아니라 **처음 밟는 코드 경로**가 키운다.
- **계단의 정체**: 매일 **00:00 UTC(=09:00 KST) DailyMailScheduler**가 돌 때 +4.2 MiB 점프 후 평탄.
  07-10·07-15 동일 패턴. 전형적 **지연 로딩**.
- **통합 가설(metaspace = RSS creep 3MB/h의 정체) 기각** 🔴 — 메타스페이스 성장은 0.73 MiB/h이고
  평상시 0이다. 3MB/h RSS creep을 설명 못 함. **QA 반론이 옳았다. 두 리스크는 별개.**
- **잔여 여유 25.4 MiB(16%)** — 신규 대형 의존성 추가 시 작동점 재확인할 것.
- **최대 교훈**: 이 사고의 답은 **배포 당시 이미 Grafana 그래프에 찍혀 있었다**(07-08 시점 135~137 MiB).
  **상한을 자르기 전에 그래프의 작동점을 먼저 본다.** 5분이면 막을 수 있었다.

### GC는 SerialGC다 — G1 아님 (2026-07-15) 🔴
- prod 실측: `gc="Copy"`(Serial Young) + `gc="MarkSweepCompact"`(Serial Old).
  512MB + shared-cpu-1x라 JVM 인체공학이 자동 선택(2코어 미만 & 1792MB 미만 → SerialGC).
- **`G1PeriodicGCInterval` 검토 항목은 폐기** — 전제부터 틀렸다. G1 옵션은 이 앱에서 전부 무효.
- #263의 2초짜리 `Pause Full`은 이상 현상이 아니라 **Serial Old 단일스레드 컴팩션의 정상 비용**.
- Serial은 **Full GC 때만 클래스를 언로드**한다. 힙이 45M/179M라 Full GC가 거의 안 돌아
  언로드가 사실상 정지 상태(11.8h에 379개). 메타스페이스가 상한을 쳐야 비로소 Full GC가 돈다.

### prod JVM 지표 조회법 — jcmd 없는 JRE 이미지 우회 (2026-07-15)
프로덕션 이미지는 **JRE 전용**이라 `jcmd`/`jmap`/`jstat`이 없다(`java jfr jrunscript jwebserver keytool rmiregistry`만).
어태치 기반 진단 불가. **대신 액추에이터를 머신 내부에서 친다** — `SecurityConfig`가 IP 화이트리스트라 무인증 통과:
```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
fly ssh console -a devquest-api -C "/bin/sh -c 'wget -qO- localhost:8080/actuator/prometheus | grep Metaspace'" < /dev/null
```
- 근거: `SecurityConfig.kt` → `/actuator/**` 는 `hasIpAddress('127.0.0.1') or ('::1') or ('fdaa::/16')`.
  외부에선 403, 내부에선 200. `/health`·`/actuator/health`만 공개.
- `fly ssh console`은 Windows에서 끝에 `Error: The handle is invalid`를 뱉지만 **출력은 정상** — 무시.
  `< /dev/null` 붙이면 tty 문제 완화.
- **주의**: `[metrics]` 섹션이 `be/fly.toml`에 없다 → **Fly는 앱 메트릭을 스크레이프하지 않는다.**
  JVM 지표는 오직 Grafana Cloud(OTLP push)에만 있다.

### Grafana Cloud 스택 접근법 (2026-07-15) — 좌표는 로컬에만 (이 레포는 PUBLIC)
> ⚠️ **스택 slug/URL은 여기 적지 않는다.** 이 레포는 공개라 테넌트 식별자를 남기면 표적 피싱의 과녁이 된다.
> **찾는 법**: Chrome에 grafana.com 세션이 살아 있다 → `fetch('/api/instances')` 하면
> `slug`·`url`·`status`가 나온다. instance id는 `application-prod.yml`의 `grafana.otlp.instance-id`와 일치하는지로 검증.
- **무료 플랜은 UI가 자동 슬립한다** — `/api/instances`가 `status: "paused"`, `pausedAt: null`로 보인다.
  **수동 pause가 아니다.** 스택 URL로 접속하면 `Grafana is loading...` 후 ~1분 내 기동. Prometheus 수집은 계속됨.
- 조회는 브라우저 세션으로 datasource proxy fetch (스크린샷 불필요):
```js
fetch('/api/datasources/proxy/uid/grafanacloud-prom/api/v1/query_range?query='
  + encodeURIComponent('jvm_memory_used_bytes{area="nonheap",id="Metaspace",application="devquest-api"}')
  + '&start=<epoch>&end=<epoch>&step=1800', {credentials:'include'}).then(r=>r.json())
```
- 스택 목록이 필요하면 grafana.com 로그인 세션에서 `fetch('/api/instances')`.
- **단위 함정**: Grafana는 **MiB(2²⁰)** 로 표기, actuator raw는 바이트. `138,162,760 B = 131.8 MiB`.
  MB(10⁶)와 섞어 비교하면 없는 문제를 만든다 (07-15에 실제로 오경보 냄).

### flyctl이 config.yml 토큰을 자동 로드하지 못함 (2026-07-15) — 매 세션 30분 낭비 방지
- 증상: `fly auth whoami` → `no access token available` (**로컬 판정, 네트워크 요청 없음**).
  토큰은 `~/.fly/config.yml`에 멀쩡히 있고(665자) fly.io 콘솔의 토큰은 **`Expires: Never`**. 만료 아님.
- 원인 불명 ⚪ (flyctl v0.4.54 / Windows). HOME 경로 이론은 반증됨 — Go는 Windows에서 `HOME`을 무시하고
  `USERPROFILE`을 본다 → `HOME=...` 실험은 **무효**다. 재현 시 그 실험 반복하지 말 것.
- **해결: env 주입** (이 프로젝트의 확립된 패턴, 과거 12회 사용. 리터럴 붙여넣기 0회):
```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
```
  값을 절대 출력하지 말 것 — 프리픽스만 찍어도 자격증명 실체화로 차단된다.

### Metaspace OOME 사고 + 힙 실측치 확보 (2026-07-14, #263→#265)
- **힙 실측: 사용 42MB / 커밋 117MB / 상한 179MB.** prod GC 로그로 직접 확인 🔴.
  힙은 남아돈다 — 향후 메모리 튜닝 시 힙을 되돌리거나 늘리는 방향은 근거 없음. **더 줄일 여지가 있는 쪽.**
- `MaxMetaspaceSize=128m`(#263)이 `OutOfMemoryError: Metaspace` 유발 → 프로덕션 다운.
  증상: `Pause Full (Metadata GC Threshold)` / `(Metadata GC Clear Soft References)` 2초짜리가
  **42M->42M로 아무것도 회수 못 하며 무한 교대 반복** = 메타스페이스 고갈 데스 스파이럴. → #265로 160m 복구.
  ⚠️ **"34시간 후"는 오기** — 07-15 Grafana 실측 결과 실제 붕괴는 **~20h**, 34h는 발견·수정 시각.
  ⚠️ 원인도 정정: "누수 의심"이 아니라 **128m < 작동점 134.6 MiB**. 위 07-15 항목이 최신·확정본.
- **진단 교훈**: "Major GC가 계속 돈다" ≠ "힙 부족". **GC 트리거 괄호를 먼저 읽어라** —
  `(Allocation Failure)`면 힙, `(Metadata GC *)`면 메타스페이스. 화살표 좌우가 안 줄면(42M->42M) 힙 문제 아님.
- **관측 교훈**: `-Xlog:gc`가 없었으면 이 진단 불가능했다 (#263이 우연히 같이 넣음). **제거 금지.**
  단 변경과 관측을 동시에 넣으면 "원래 있던 현상"과 "새로 생긴 현상"을 구분 못 하는 confound가 생긴다.
- **프로세스 교훈**: #263은 힙·메타스페이스·코드캐시 **3개를 실측 없이 동시에** 잘랐다
  (커밋 메시지에 "근사치, 배포 후 실측 검증 필요"라 스스로 명시하고도 배포).
  → 리소스 상한은 **live set 실측 후에** 자른다. 한 번에 하나씩.

### OOM 진짜 원인 확정 — 순수 누적형 RSS 포화, kill 수위 anon-rss ~409MB (2026-07-07, #245)
#239 JVM 튜닝 후에도 재발. 커널 OOM 로그 7일 전수(8건)로 인과 최종 확정:
- **모든 kill에서 java anon-rss가 406~410MB로 동일** — 시각은 제각각(새벽·오전·오후·저녁).
  RSS가 시간당 ~3MB씩 차올라 ~409MB 도달하면 시각 무관 사망. **이벤트(메일 잡 등)는 무관** —
  8건 중 09시 메일 직후는 2건뿐, 상관관계 과대 해석 주의 (uptime 리셋 목록은 배포와 섞임)
- creep 정체: JVM이 커밋해둔 페이지(총 314MB)를 서서히 실제 터치 + native ~95MB
  → RSS 천장 = 커밋총량+native ≈ 409MB = kill 수위. JVM 지표(used/committed)로는 안 보임
- 부팅 직후 가용 마진 ~44MB뿐 (총 459MB). 사망 시점 HTTP/AI 요청 항상 0건 — 서비스 영향은
  회당 3~5분 503 창 + "AI 평가 도중 kill" 잠재 시나리오뿐
- 대응 1차: fly.toml `swap_size_mb = 256` (#245 머지·배포). creep이 스왑도 채우면(+3.5일) 재발
  가능 → **업타임 4~5일째 `fly_instance_memory_swap_free` 관찰**
- 대응 2차: JVM 메모리 다이어트 (다음 작업 참조). 최후 수단: 1GB 스케일업 (~$5.7/월)
- 조사 방법 메모 (재조사 시 그대로 재사용):
  - Grafana 스택/fly-metrics.net 페이지에서 `fetch('/api/datasources/proxy/uid/<uid>/api/v1/query_range?...')`
    — 스크린샷 없이 PromQL 수치 직접 획득. fly 쪽 uid `prometheus_on_fly`, 앱 스택 uid `grafanacloud-prom`
  - 머신 RSS: `fly_instance_memory_mem_available{app="devquest-api"}` (JVM 지표에 안 보이는 creep 관측)
  - 재시작 전수: `process_uptime_seconds` 리셋 감지 (단, 배포·autostop 섞임 — OOM 확정은 커널 로그로)
  - 커널 OOM 로그: fly-metrics.net `application_logs_vlog` datasource,
    LogsQL `"fly.app.name":"devquest-api" "Out of memory: Killed process" _time:7d` → anon-rss 수치 확인

### AI 메트릭 대시보드 "0으로 보임" — 버그 아님, `increase()` 콜드스타트 특성 (2026-07-01)
- 저트래픽 앱에서 `increase()` 기반 패널은 원래 0으로 보임 (신규 시리즈 콜드스타트 + 증가량 없음).
  raw 카운터를 Explore에서 `increase()` 없이 직접 쿼리하면 실제 값 확인 가능. **재발해도 코드 재조사 불필요**
- AI 카운터(`gen_ai_*`, `ai_*`)는 **첫 AI 호출 때 lazy 등록** — 재시작 후 호출 없으면 시리즈 자체가 소멸
  (JVM 메트릭은 기동 시 즉시 재등록되어 남음). 시리즈 부재 = "그 구간 AI 호출 0건"의 증거로 쓸 수 있음
- OTLP resourceAttributes에 instance 라벨 없음 — 멀티 머신 되면 시리즈 충돌 (1대라 무해, 스케일아웃 시 필수 수정)

### Grafana 대시보드 = 신형 v2 스키마 + table 패널의 `format: "table"` 필수 (2026-07-02)
- 라이브 대시보드는 신형 v2 스키마(`kind: Panel`, `elements` 맵, `RowsLayout`). IaC 소스는
  레포 `grafana/ai-metrics-dashboard.json` (신형으로 동기화됨)
- table 패널: `instant: true`만으론 부족, **`format: "table"`** 필수 — 빠지면 기본 `time_series`라
  라벨 컬럼이 안 나오고 merge/organize transform 깨짐
- 대시보드 편집 시 자동 새로고침 OFF 필수 (미저장 편집 삭제 + 렌더러 OOM/CDP 프리즈) —
  파일의 `timeSettings.autoRefresh`는 `""`(off) 유지

### Spring Boot 4.x Flyway 수동 구성
SB 4.x에서 Flyway auto-configuration 제거됨. `db-core`에 `FlywayConfig.kt`로 수동 구성:
- `@Profile("prod")` / `flyway.repair()` → `migrate()` 순차 호출 (flyway 11.x에서 repairOnMigrate 제거)
- `EntityManagerFactoryDependsOnPostProcessor("flyway")`로 JPA 초기화 순서 보장
- 패키지: `org.springframework.boot.jpa.autoconfigure` (SB 4.x 변경)
- ⚠️ 마이그레이션 작성 전 `core-api`·`db-core` **두 디렉토리 전부** 버전 확인 (V8 충돌 prod 다운 사고,
  상세는 archive — be-ci.yml 린트가 자동 차단)

### mneme wiki ↔ 앱 데이터 관계 (런타임 연동 아님)
`E:/development/wiki/`(mneme LLM wiki)는 로컬 개발머신 전용 — 앱 반영은 **빌드타임 시드**만
(사람 큐레이션 → Flyway 마이그레이션/정적 리소스). 런타임에 앱이 mneme 호출하는 구조 금지.
유사 패턴: `client-ai/support/ConferenceReferenceLoader` + `conference-references.json`

### Controller 테스트 패턴
`standaloneSetup` + `@AuthenticationPrincipal` 조합 시 반드시:
```kotlin
.setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
// + @BeforeEach: SecurityContextHolder.getContext().authentication 설정
// + @AfterEach: SecurityContextHolder.clearContext()
```

### 동일 파일 수정 스프린트 — 직렬 순서 필수
두 스프린트가 같은 파일을 수정하면 병렬 브랜치 금지. 앞 PR 머지 후 다음 브랜치 생성.
(BE↔FE 다른 파일이면 병렬 OK)

### Observability 최종 상태
- Sentry: SB 4.x 미지원으로 포기 (PR #52)
- Grafana Cloud Loki: loki4j 1.6.0, `GRAFANA_LOKI_URL` 빈값이면 자동 비활성화
  - `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_INSTANCE_ID` fly secret 주입, `GRAFANA_API_KEY`는 메트릭과 공유
  - Grafana Cloud용 `maxBytes=65536`, `requestTimeoutMs=15000`

### 에이전트 Remote Control 운영 방식
- 대화형 세션에서는 named agent(`.claude/agents/*.md`) 스폰 불가 — 내장 타입만 지원
- 오케스트레이터 + remote control: `claude --agent orchestrator --remote-control` (직접 터미널 실행)
- `claude remote-control` 서버 모드는 `--agent` 플래그 미지원

## 참조 문서

| 주제 | 문서 |
|------|------|
| Copilot 리뷰 처리 | `.claude/docs/copilot-review.md` |
| 멀티 에이전트 운영 | `.claude/docs/agent-workflow.md` |
| 배포 / 환경변수 | `.claude/docs/deployment.md` |
| 커밋 / PR / 브랜치 | `.claude/docs/git-strategy.md` |
| 전체 작업 이력 | `.claude/CONTEXT.archive.md` |
