# 미완료 작업

> **IaC-first 전환(07-16)으로 기존 콘솔 작업 TASK-4/5 폐기.** 크레딧 제외 필터·이상탐지는 이제
> 콘솔이 아니라 `0-bootstrap`의 코드로 처리한다.
>
> - ✅ 예산 — `budget.tf` (`cost_types.include_credit=false`, `ABSOLUTE_VALUE`). 07-18 apply 완료.
>   <!-- verify: infra/aws-eks/0-bootstrap/budget.tf -->
> - ✅ 이상탐지 — `cost-anomaly.tf` (`aws_ce_anomaly_monitor` + `_subscription`). **07-29 코드 추가**,
>   머지 시 CI(`infra-deploy.yml`)가 apply. <!-- verify: infra/aws-eks/0-bootstrap/cost-anomaly.tf -->
>   그전까지 이 줄은 "코드로 처리"라고 적혀 있었지만
>   **실제 `.tf`가 없었다** — 문서가 코드보다 앞서 있던 상태였고 07-29에 해소.

### TASK-4: 0-bootstrap 착수 준비 — AWS 자격증명 (사용자, 2026-07-16, 진행 중)

> ⚠️ **SSO(IAM Identity Center) 경로 폐기** — Organizations로 켜면 **$200 크레딧 즉시 소멸**(일지 `[막힘]`).
> → **IAM 사용자 액세스키**로 확정 (org 안 만듦 = 크레딧 안전).

최초 로컬 `tofu apply`(S3 backend·예산)에 자격증명 필요. Claude는 시크릿 키를 못 다루므로 사용자가 직접:

1. 콘솔 → IAM → Users → **`bootstrap-admin`** 생성, **AdministratorAccess** attach
2. Security credentials → **Create access key** (use case: CLI)
3. **PC 터미널에서 직접** `aws configure` — key/secret 본인 입력, region **`ap-northeast-2`**, output `json`
   - ⚠️ 키를 Claude에 붙여넣지 말 것. `~/.aws/`에만, **git 절대 금지**.
4. "configure 했어" → Claude가 `aws sts get-caller-identity` 확인 → `0-bootstrap` 코드 착수
   - 부트스트랩 후 GitHub OIDC로 전환하고 **이 액세스키 폐기**

### TASK-12: RSS creep 기울기 재측정 (사용자, 2026-09-23) — **선행 조건 4 의 마지막 조각**

> **이것이 선행 조건 5건 중 유일한 미완이다.** $0 · 클러스터 불필요 · prod 를 한 번도 죽이지 않는다.
> 근거: `plans/2026-09-11-prod-eks-migration-prereqs.md` 「B-5」 절 +
> **`docs/jvm-observability-notes.md` §6**(RSS creep·kill 수위·665/576/89 산출) ·
> **§3**(`fly ssh console` 레시피 — JRE 이미지엔 `jcmd` 가 없다).

**왜 Claude 가 못 하나**: `flyctl auth whoami` → `Error: no access token available`.
토큰은 사용자 자격증명이라 대신 받을 수 없다.

**무엇을 알아내려는 것인가**: 현재 RSS creep 속도. 지금 쓰는 `3 MB/h` 는 **2026-07 측정값**이고,
그 값이 K8s 런웨이(`(576 − RSS) ÷ 속도`)를 직접 결정한다. 09-18 실측은 **점 하나**라 기울기가 안 나온다.

#### 🔴 찍는 시각 — 스케줄러 발화 전후로 **갈라서**

`DailyMailScheduler` 가 **매일 09:00 KST** 에 돌며 **메타스페이스 +4.2 MiB** 점프를 만든다
(§1, 지연 로딩). 09-18 에 관측된 **메타스페이스 작동점** 상승(134.6 → 138.8)도 **같은 4.2 MiB** 라
*"Phase 2 로 작동점이 올랐다"* 와 *"일일 점프를 한 번 더 봤다"* 가 안 갈린다 — **이 모호성은
메타스페이스의 것이다.**

🟡 **그런데 RSS 측정에도 걸린다**: 메타스페이스는 힙 밖 네이티브 메모리라 **VmRSS 에 포함**된다.
따라서 09:00 직후에 찍으면 그날의 계단이 creep 으로 오인된다.
⚠️ **§1 의 판정을 RSS 에 그대로 옮기지 말 것** — 같은 문서가 *"메타스페이스와 RSS creep 은
별개 리스크(0.73 vs 3 MB/h)"* 라고 **명시 판정**해뒀다. 겹치는 것은 *시각*이지 *원인*이 아니다.

→ **하루 두 번, 08:30 과 09:30 KST.**

#### 명령 (그대로 복사)

> 🔴 **무엇이 검증됐고 무엇이 안 됐나 (2026-09-23)** — 이 구분을 지우지 마라.
>
> | | 상태 |
> |---|---|
> | **안쪽 payload** (`pgrep -x java` · `grep VmRSS /proc/$P/status` · `/proc/meminfo` · `/proc/uptime`) | ✅ `eclipse-temurin:21-jre-alpine` 에서 직접 실행, **EXIT:0** + 4개 값 출력 |
> | **`-C` 인자 이스케이프** (`\"` → `"`, `\$` → `$` 가 단일 인자로 넘어가는지) | ✅ 로컬 셸에서 인자 경계를 분해해 확인 (QA 독립 재현) |
> | **`fly ssh console` end-to-end** | ⚪ **미검증** — flyctl 인증이 없어 실제로 못 돌렸다 |
>
> → **첫 실행에서 출력 4줄(날짜·meminfo 4행·`pid=`+`VmRSS`·uptime)이 다 나오는지 먼저 확인할 것.**
> 안 나오면 명령 문제이지 앱 문제가 아니다.
>
> 🔑 처음엔 이 블록에 *"✅ 실제 이미지에서 검증함"* 이라고만 적었는데, 실제로 검증한 것은
> **payload 뿐**이었다. `CLAUDE.md` 의 반복 실패 형태 — ***검사가 주장보다 헐겁다*** — 그대로다.

```bash
flyctl auth login          # 최초 1회
```

```bash
# 08:30 KST 와 09:30 KST 에 각각 1회. 이틀 이상 반복하면 기울기가 나온다.
fly ssh console -a devquest-api \
  -C "/bin/sh -c 'date -u +%Y-%m-%dT%H:%M:%SZ; grep -E \"^(MemTotal|MemAvailable|SwapTotal|SwapFree)\" /proc/meminfo; P=\$(pgrep -x java | head -1); echo pid=\$P; grep VmRSS /proc/\$P/status; cut -d\" \" -f1 /proc/uptime'" \
  < /dev/null
```

> 🔴 **`ps -o rss= -C java` 를 쓰지 마라.** 런타임 이미지가 `eclipse-temurin:21-jre-alpine`
> (= **BusyBox ps**)이고 `procps` 가 안 깔려 있다. 2026-09-23 실측:
> ```
> ps -o rss= -C java   →  "ps: unrecognized option" · EXIT 1
> ps -o rss,comm       →  "39m"      ← MB 로 반올림. 3 MB/h 측정에 못 쓴다
> grep VmRSS /proc/<pid>/status  →  "VmRSS:  41060 kB"   ✅ kB 정밀도
> ```
> **09-18 기준값도 `VmRSS` 로 잰 것**이다(§6 블록의 `java VmRSS 386.7 MiB`).
> 같은 방법으로 재야 비교가 성립한다.
>
> ⚠️ `fly ssh console` 은 Windows 에서 끝에 `Error: The handle is invalid` 를 뱉지만
> **출력은 정상**이다 — 무시. `< /dev/null` 이 tty 문제를 완화한다(§3).

#### 기준값 (2026-09-18, 업타임 70.8h = 2.95일)

```
MemTotal      459 MiB
MemAvailable   26.6 MiB
SwapTotal     256 MiB
SwapFree      223 MiB   →  사용 32.6 MiB
java VmRSS    386.7 MiB        ← kill 수위 ~409 MiB 대비 22 MiB 아래
```

#### 판정

| 보는 것 | 뜻 |
|---|---|
| `VmRSS` 증가분 ÷ 경과시간 | **현재 creep 속도**. 구하려는 값이다 |
| `SwapFree` 감소분 | **RAM 초과분**. K8s 는 swap 0 이므로 이 값이 곧 K8s 에서 넘칠 양이다 |
| `/proc/uptime` 리셋 | 재배포·autostop 이 있었다 → **그 구간은 기울기 계산에서 뺀다** |

⚠️ **업타임이 리셋됐으면 측정을 다시 시작한다.** 배포가 끼면 RSS 가 초기화돼 기울기가 과소평가된다.

#### 끝나면

출력을 Claude 에게 주면 → 기울기 산출 → **대응 선택지 ①~④ 결정** → 선행 조건 4 종료
→ **선행 조건 5건 전부 완료** → 이관 계획서 착수(0번 = 월 $140 실지출 결정).

| | 선택지 | 대가 |
|---|---|---|
| ① | `limits` 576 → 768Mi | 노드 메모리 재산정. requests 합 1568Mi 에 allocatable 1365Mi/노드 → **3노드가 이미 꽉 찬다** |
| ② | K8s swap (`NodeSwap`) | ⚪ 이 클러스터는 1.36. 성숙도를 **문서로 확인할 것**(알파/베타 기록이 엇갈렸다) |
| ③ | creep 자체를 잡는다 | 근본적이나 원인이 ⚪ 미확정 |
| ④ | Fly 1GB 인스턴스 (~$5.7/월) | **prod 를 Fly 에 두는 한 가장 싸다** — 이관 동기를 약화시킨다 |

### TASK-6: AWS 크레딧 만료일 캘린더 등록 (사용자, 2026-07-23)

Free Plan 크레딧은 **소진 OR 만료 중 먼저 오는 시점에 계정이 자동 폐쇄**된다(과금이 아니라 폐쇄).
실측 만료일을 캘린더에 박아 놓을 것 — prod는 Fly+Neon이라 무영향이나, EKS 학습 인프라·S3 tfstate·
ECR이 계정과 함께 사라진다(폐쇄 후 90일 content 보관, Paid 업그레이드 시 복구·잔여 크레딧 이월).

> ✅ **만료일 모순 해소 (07-29).** 문서마다 `2027-01-15` / `2027-07-15`로 갈렸던 것을
> 사용자 콘솔 확인으로 **`2027-01-15` 확정**. `2027-07-15`는 07-16 기록 시 "가입 +1년"이라는
> **추론이 콘솔 값을 덮어쓴 오독**이었다. 틀린 쪽 2곳(`infra/aws-eks/README.md`, 일지 2개 항목)은
> 정정 완료 — 일지는 원문을 취소선으로 남기고 정정을 병기했다(기록 왜곡 방지).
>
> 💡 **의미**: 크레딧 수명은 1년이 아니라 **약 6개월**이다. 원래 계획 문서의 "6개월"이 맞았고,
> 07-16의 "1년" 정정이 오히려 오류였다. **남은 기간이 절반이라는 뜻** — 세션 빈도 계획에 반영할 것.

- **만료일**: **2027-01-15** (07-29 콘솔 확인 확정)
- **알림 권장**: **2027-01-01** (만료 2주 전) — 상시 데모 계획이 있으면 이때 Paid 업그레이드 판단
- 별개 감시: 크레딧 잔액이 **$30 안전 예비**에 근접하면(현재 $199.81) 세션 빈도 조절

### TASK-7: EKS 리퍼(dead man's switch) — 새 머신/클론 시 재설치 (2026-07-25)

`.claude/eks-session/` 마커·하트비트·launchd 잡은 **머신 로컬**이라 gitignore된다. 즉 **이 맥에만
설치돼 있고**, 다른 머신에서 clone하면 리퍼가 없어 "끄는 걸 잊음" 자동 방어가 안 걸린다.

- **현재 맥(dhbangui-MacBook-Neo)**: ✅ 설치·로드 완료(07-25). `launchctl list | grep eks-reaper`로 확인.
- **새 머신/재클론 시 1회 실행**:
  ```bash
  bash infra/aws-eks/reaper/install-reaper.sh
  ```
- 상세: `docs/eks-session-sop.md` §안전장치.

## TASK-11: 앱 직접 사용 후 피드백 (2026-09-18, CONTEXT 압축 중 이관)

> 원래 `.claude/CONTEXT.md` 「사용자 확인 필요」에 있었다. CLAUDE.md 규칙상
> **사용자 직접 실행 항목의 목적지는 이 파일**인데 등재돼 있지 않았다.

- [ ] **모바일 실기기 확인** — 데스크톱 시나리오는 prod 테스트 완료
- [ ] **테스트 데이터 정리** — 회사 "테스트-토스" 삭제, 임시 이력서를 실제로 교체
- [ ] **앱 직접 사용 후 불편한 점 / 빠진 기능 파악** → 다음 기능 기획 입력
- [ ] **#257 후속 — 휘발형 학습 실사용 확인**: 후속 질문 UX(단발형·5회/일 제한 적정성), AI 설명 품질.
      만족스러우면 **Phase B(축적형 복습노트)** 착수 판단 — 모르는 개념/오답 저장 → 간격 반복,
      로그인·DB·RPG XP 연동. **지금은 보류이고, 판단은 사용자 몫이다.**

## 완료된 항목

### TASK-10: ACM 인증서 DNS 검증 → **완료 (2026-09-16)**

Cloudflare 에 검증 CNAME 입력(**DNS only**) → **레코드 입력 후 약 3분만에 `ISSUED`**.

```
Status            ISSUED
Domain            eks.quest.dhbang.co.kr
ValidationStatus  SUCCESS (DNS)
발급              2026-09-16 10:00:36 KST   (마감 09-18 13:12 대비 51시간 여유)
만료              2027-04-02 08:59 KST
```

🔴 **`RenewalEligibility: INELIGIBLE` · `InUseBy: []`** — ACM 자동 갱신은 인증서가 AWS 리소스에
연결돼 있을 때만 동작한다. 아직 ALB 에 안 붙어서 갱신 대상이 아니다. 다음 유료 세션에서 Ingress 에
붙으면 `ELIGIBLE` 로 바뀐다. 만료(2027-04-02)가 크레딧 만료(2027-01-15)보다 뒤라 이 트랙에선 무해하다.

절차 전문은 git 이력에 있다(이 커밋의 부모).

### TASK-8: timezone 배포 후 실측 → **불필요해져 종결 (2026-07-28)**
로컬에 colima+docker를 설치해 **배포 전에 이미지로 직접 실측**해버렸으므로 prod 확인이 필요 없다.
결과: `TZ=Asia/Seoul`만으로 이미 `ZoneId.systemDefault()=Asia/Seoul`(temurin alpine에 tzdata 포함)
→ prod는 #210부터 쭉 KST였고 **L-9은 오진**이었음이 확정. 상세는 원장 L-9/L-10, PR #337.

### TASK-3: BE 서버 다운 — PR #231 배포 실패 후 헬스체크 미통과 (2026-07-01, 해결됨)
`https://api.quest.dhbang.co.kr/health` 503 → 완전 타임아웃. Grafana Loki 스냅샷 로그로 원인 확정:
Flyway `V8` 마이그레이션 버전이 `core-api`(V8__company_pipeline.sql)와 `db-core`
(V8__create_tech_question_bank.sql, PR #231 신규) 양쪽에 중복 생성되어 앱 부팅 자체가 실패.
- 수정: PR #233 — `V10__create_tech_question_bank.sql`로 rename, 재배포 성공, `/health` 200 복구 확인
- 재발 방지: `be-ci.yml`에 마이그레이션 버전 중복 검사 CI 린트 추가 (PR 단계 자동 차단)

### TASK-1: BE AI Evaluator 캐시 메트릭 관측가능성 추가 (PR #123)
`CacheMetricsAdvisor` 추가 — 매 AI 평가 호출 후 cache_read_input_tokens / cache_creation_input_tokens INFO 로그 출력.

### TASK-2: Claude Code 세션 프롬프트 구조 최적화 (PR #124)
CONTEXT.md 고정 내용(비자명적 결정, 참조 문서) 상단 배치, 동적 내용(현재 상태, 최근 완료) 하단으로 분리.

### [Observability] Sentry → 포기, Logtail 연동 완료
- **Sentry**: Spring Boot 4.x 미지원으로 포기 (PR #52에서 의존성 제거)
- **Logtail (Better Stack)**: 연동 완료 (fly.io log drain 등록)

## TASK-9: prod(Neon)의 PostgreSQL 메이저 버전 확인

**왜**: EKS 학습 RDS의 `db_engine_version`을 기본 `17.10`으로 뒀는데, prod(Neon)의 메이저를
모르는 상태다. 메이저가 다르면 Flyway 마이그레이션 12개를 EKS에서 돌려봐도
"prod에서도 된다"는 근거가 약해진다(학습 가치는 유지되나 회귀 검증 가치가 떨어짐).

**하는 법** (Neon 콘솔 또는 psql):
```sql
SELECT version();
```
또는 Neon 대시보드 → 프로젝트 → Settings에서 Postgres 버전 확인.

**결과 반영**: `infra/aws-eks/2-cluster/variables.tf`의 `db_engine_version` 기본값을
prod와 같은 메이저로 맞춘다(예: prod가 16이면 `"16.x"`).
지원 버전 확인: `aws rds describe-db-engine-versions --region ap-northeast-2 --engine postgres`

**07-28 실측 보강 (#339 세션)**: RDS **PostgreSQL 17.10**에 대해 Flyway **12개 마이그레이션이
전부 정상 적용**됐다(`Successfully validated 12 migrations` / `Current version of schema "public": 12`).
즉 17.x에서 스키마가 깨지지 않는 것은 확인됐다. 남은 건 "prod와 **같은** 메이저인가"뿐이고,
다르더라도 **학습 진행에는 지장 없다**(회귀 검증의 강도만 낮아진다). 우선순위 낮음.
