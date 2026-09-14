# prod → EKS 이관: 선행 조건 (2026-09-11)

> 📌 **D-013** · 상태 `🚧진행중` · 영향 `infra/aws-eks/README.md`, `.claude/CONTEXT.md`(D-001),
> `.claude/scripts/eks-reaper.sh`, `docs/eks-session-sop.md`, `infra/aws-eks/2-cluster/secrets.tf`,
> `k8s/base/`, `.github/workflows/`, `fe/vercel.json`
> 재판정 `선행 조건 7건 중 미충족이 남아 있는 동안 이관을 착수하지 않는다`

## 이 문서가 있는 이유

사용자가 *"실 서비스까지 EKS로 돌리려면 얼마나 걸릴까"* 라고 물었다. 나는 처음
**"기술 2~3주, 월 $140"** 이라고 답했다. **둘 다 틀렸다.**

Blindspot Pass(2026-09-11, Explore)가 **22건**을 찾았고 그중 **6건이 "이 계획은 성립하지
않는다"** 수준이었다. 실제 규모는 **2~3개월**이고, 착수 순서를 그대로 따르면 **전환 당일에
로그인 불가 + TLS 실패 + 메일 중복 발송**을 동시에 만난다.

🔑 이 문서의 값은 "계획"이 아니라 **"왜 지금 못 하는지"** 다. 그 목록을 잃으면 다음에
같은 조사를 다시 해야 하고, 그때는 기억으로 하게 된다.

---

## 🔴 계획이 성립하지 않는 6건

### B-1. 리퍼(dead man's switch)가 prod를 자동 파괴한다

```
.claude/settings.json  PreToolUse → eks-session-marker.sh 가 tofu apply 를 감지해 마커 자동 생성
.claude/scripts/eks-reaper.sh:17  TTL=7200 (2h)
reaper/com.devquest.eks-reaper.plist:21  StartInterval 1800 (30분마다)
→ 하트비트 2h stale 이면 tofu destroy -auto-approve
```

**노트북을 2.5시간 덮으면 prod가 사라진다.** 마커는 사람이 만드는 게 아니라 훅이 자동 생성한다.

🔴 **리퍼를 끄는 것이 답이 아니다** — 그건 *"$140/월이 사람 없이 계속 나가는 상태"* 를 만든다.
안전장치를 제거하는 게 아니라 **상시 운영 전제로 재설계**해야 한다.

### B-2. 로그인이 안 된다 (시크릿이 학습용으로 고정돼 있다)

```
secrets.tf:111-120  GITHUB_CLIENT_ID/SECRET = 자리표시 고정
                    주석: "prod 의 실제 값을 쓰지 않는다 — 의도적"
secrets.tf:121      random_password.jwt_secret 이 **2-cluster state**
application.yml:50  JWT_EXPIRATION_MS:2592000000 (30일)
```

**클러스터를 한 번 부술 때마다 전 사용자 강제 로그아웃**이다. 원장 **L-14**(postgres 비밀번호가
2-cluster에 있어 재구축 시 접속 실패)와 **정확히 같은 형태**이고, L-14는 `0-bootstrap`으로
레이어를 옮겨 해결했는데 **JWT는 안 옮겼다.**

> 🔑 D-004·L-14가 확립한 규칙 — *"볼륨과 수명이 같아야 하는 것은 볼륨과 같은 레이어에 둔다"* —
> 의 적용 대상이 하나 더 있었다. **토큰은 사용자 세션과 수명이 같다.**

🔴 **재판정 (2026-09-14, 항목 2 Blindspot Pass)** — 위 두 문단은 **두 군데가 틀렸다.**

1. *"클러스터를 한 번 부술 때마다 전 사용자 강제 로그아웃"* → **지금은 안 일어난다.**
   실사용자 토큰은 Fly 의 `JWT_SECRET` 으로 서명되고 Fly secrets 는 write-only 라 읽어올 수
   없다(B-9 와 같은 근거). **전환 당일 로그아웃은 어느 레이어에 두든 확정**이고, 이 항목이
   막는 것은 *전환 **이후** teardown 마다*뿐이다. → 전환 시점 항목에 사전 공지 절차 추가할 것.
2. *"규칙의 적용 대상이 하나 더"* → 맞지만 **그대로 복사하면 안 된다.** 앞선 세 번과 달리
   JWT 는 **두 환경**에 걸치므로, 키 하나를 영속 레이어에 두면 수명은 맞추고 환경을 섞어
   **학습 클러스터가 prod 유효 토큰을 발급**할 수 있게 된다.

상세와 채택한 설계는 이 문서 하단 **「항목 2 — 시크릿 환경 분리 (착수 설계)」** 참조.

### B-3. 도메인·TLS 전제가 틀렸다

| 내 가정 | 실제 |
|---|---|
| `api.` 서브도메인이 없다 | `fe/vercel.json:5` → `https://api.quest.dhbang.co.kr/api/$1` **실재** |
| DNS = Route53 | **Cloudflare** (`CONTEXT.archive.md:189`). `infra/` 에 `aws_route53_*`·`aws_acm_certificate` **0건** |
| "DNS만 바꾸면 된다" | Ingress가 **HTTP 80만** 연다(`ingress.yaml:60`). rewrite 목적지가 `https://` → **플립 순간 TLS 실패** |

선행 필수: ACM 발급 + **Cloudflare 수동 DNS 검증**(IaC 불가) + Ingress annotation 3개
(`certificate-arn`·HTTPS listen-ports·`ssl-redirect`). **이 경로는 코드도 검증도 0.**

게다가 `ingress.yaml:57-59`가 스스로 *"⚠️ 이 Ingress로는 **로그인·토큰을 태우지 마라. 평문이다**"*
라고 금지해뒀다.

### B-4. Stage 0~4 "실검증"은 AI·메일·채점이 한 번도 안 돈 검증이다

```
ai-api.yaml:65-66                  DEVQUEST_AI_STUB_TECH_INTERVIEW_ENABLED: "true"
TechInterviewStubEvaluator.kt:15   "🔴 다른 17개 AI 포트는 스텁되지 않는다
                                    — 여전히 실제 ANTHROPIC_API_KEY 가 필요하다"
grep ANTHROPIC|RESEND|JUDGE0|MAIL_ENABLED k8s/ infra/   →  0건
client-ai-anthropic.yml:4          ${ANTHROPIC_API_KEY:}  ← 빈 기본값
```

**빈 기본값이라 부팅은 성공하고 AI 호출 시점에 런타임 실패한다** — Phase 2 계획서가
*"SOP가 가장 비싼 실수로 지목한 형태"* 라고 적어둔 바로 그것이다.

🔑 즉 **"Stage 0~4 전부 실검증 완료"는 참이지만, 검증된 것이 prod 트래픽의 핵심 경로가 아니다.**
스텁이 17개 중 1개만 대체한다.

### B-5. Fly에는 swap이 있고 K8s 노드에는 없다

```
fly.toml:4-6   "OOM 완화: RSS 가 512MB 한계 근접 시 스왑으로 흡수
                근본 원인: 부팅 직후 가용 마진 ~44MB + 시간당 ~3MB RSS 증가"
               swap_size_mb = 256
K8s 노드 swap 설정 → 전수 grep 0건
core-api.yaml:97  limits: 576Mi   ← 하드 OOMKill 선
```

산술: 마진 168Mi(576−408) ÷ 72MB/일(3MB×24h) = **약 2.3일**.

그리고 `infra/aws-eks/README.md:24`가 prod 이관의 **선행 조건으로 "메타스페이스 누수 검증"을
걸어뒀고**, `be/Dockerfile:43`은 *"원인 미검증 — 별도 조사 필요"* 다. **선행 조건 미충족.**

> ⚠️ 확도: 2.3일은 **산술이다**(🟡). 시간당 3MB가 선형인지, Metaspace 상한에서 안정화되는지는
> 미확인. 그러나 **swap이 그 증가를 흡수해왔다는 사실은 `fly.toml` 주석의 실측**이다.

### B-6. 블루-그린이 구조적으로 불가능하다

```
DailyMailScheduler.kt:25  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
DailyMailScheduler.kt:41  중복 방지 = dailyMailLogPort.existsTodayLog
                          → **같은 DB 안에서만** 작동한다
```

| 시도 | 결과 |
|---|---|
| Fly(Neon) + EKS(in-cluster) 동시 | 두 스케줄러가 각자 발송 → **사용자가 메일 하루 2통** |
| 둘 다 Neon 을 보게 | *"학습 클러스터를 prod DB 에 연결 금지"* 위반. `migrate-on-startup: true` 라 **EKS 가 뜨는 순간 Neon 에 Flyway** |

`RateLimitResetScheduler`도 같은 구조다.

---

## 🔴 시한 정정 — 내 계산이 틀렸다

```
내가 말한 것   "크레딧 $167 → 1.2개월 시험 후 계속할지 결정"
실제           크레딧 만료 = 2027-01-15 **고정** = 2026-09-11 기준 약 4.1개월
               폐쇄 트리거 2개: 크레딧 소진 **또는** 기간 만료
               → 돈이 아니라 **계정**이 대가
```

더 나쁜 것: **안전 예비 $30 규칙의 근거 문장**이 `CONTEXT.md:745` — *"prod는 Fly+Neon이라
계정 폐쇄돼도 무영향"* 이다. **이 계획은 그 전제를 깨면서 그 전제 위에 세워진 예산 상한을
그대로 쓴다.**

---

## 🟡 그 외 미충족 (요약)

| # | 내용 | 근거 |
|:-:|---|---|
| B-7 | in-cluster TLS가 자체서명 — 코드가 *"MITM을 막지 못한다. 실제 운영이라면 cert-manager"* 라고 명시. 인증서도 2-cluster 소유라 세션마다 재생성 | `postgres-tls.tf:28-60` |
| B-8 | **백업 0건.** `pg_dump`·`pg_restore`·스냅샷 스케줄 레포 전체 0. D-001 기각 사유 ④가 정확히 *"자동 백업·PITR을 전부 자작"* | 전수 grep · `CONTEXT.md:650` |
| B-9 | Neon 크리덴셜을 **읽을 수 없다** — Fly secrets가 write-only. `pg_dump` 선행 조건 미충족 | `eks-migration-log.md:365` |
| | ⚠️ **09-12 재배치**: 항목 1의 대상을 in-cluster 로 정했으므로 **항목 1은 B-9 를 닫지 않는다.** Fly secrets 는 그대로 write-only 다. B-9 는 **전환 시점 항목**(B-6·B-11·B-16 과 같은 묶음)으로 옮긴다 — 실데이터 이전을 실제로 할 때 사용자가 Neon 콘솔에서 재발급해야 한다 | |
| B-10 | 3서비스로 가면 **기동 순서 강제 필요**(core-api가 Flyway를 먼저 끝내야 daily-api가 뜬다). `k8s/base/`에 initContainer·Job·순서 제약 0건 | `daily-api/application-prod.yml:30-41` |
| B-11 | 노드 2대가 **단일 AZ** 핀 = 가용성 아님. `replicas: 1` + `Recreate` = 배포마다 ~30초 다운타임. PDB 없음. 원장 **L-43·L-47** 미해결 | `nodes.tf:28` · `core-api.yaml:20,30,37` |
| B-12 | **EKS CD가 0.** `ecr-push.yml`이 `workflow_dispatch`+`pull_request`만, 배포는 손작업 `sed \| kubectl apply`. 원장 **L-44**(PR 빌드 태그가 레포에 없는 커밋을 가리킴) | `ecr-push.yml:12-37` |
| B-13 | Fly를 살려두면 **롤백 타깃이 계속 움직인다**(main push마다 재배포 + 동결된 Neon). 끄면 롤백 가치가 준다. 원장 **L-36** | `be-cd.yml:5-7,40` |
| B-14 | `prod-smoke-daily.yml` **3중 고장**: Vercel을 때려 Fly/EKS 구분 불가 · 실패 안내가 `fly status` 하드코딩 · 05:23 KST라 클러스터 상시 가동 전제 | `prod-smoke-daily.yml:20,45,54` |
| B-15 | Grafana: `secrets.tf`가 *"값의 부재를 스위치로"* 확정한 것을 뒤집어야 함. `application-prod.yml:19` `instance-id: "1680166"` 이 **진짜 값 하드코딩**이라 학습/prod 메트릭이 섞인다. `SecurityConfig.kt:41`의 `fdaa::/16`(Fly 사설망)은 **EKS에서 아무것도 열지 않는다** | `secrets.tf:144-166` |
| B-16 | **Fly `suspend`의 "$0"이 미검증** — `min_machines_running=1`이면 최소 1대는 계속 running일 수 있다. `CONTEXT.md:520`은 이걸 **"(비용)" 항목으로 분류**해뒀다. `grep suspend *.md` → 0건 | ⚪ 실측 필요 |
| B-17 | $140에서 빠진 것: **ALB LCU**(prod는 정의상 실트래픽 상시 — `ingress.yaml:9-11`이 최악 고정비의 4배 경고) · NAT +$32 · KMS $1+ · CloudWatch. 그리고 **이상탐지 DAILY $5 임계가 $4.6/일 상주로 무력화** | `budget.tf` · `cost-anomaly.tf:55,71` |
| B-18 | 상시 전환은 **학습 전제 위에 세운 통제 전체를 무근거로 만든다**: SOP 전체 · `assert-eks-quiz.sh` · tfsec 예외 4건(전부 *"세션마다 폐기되는 학습 자산"* 근거) · 원장 L-49 | 다수 |

---

## 🔴 뒤집어야 하는 기존 결정 2건 — 아직 유효 상태다

| 결정 | 현재 상태 | 내용 |
|---|---|---|
| `infra/aws-eks/README.md:9,11-22` | **갱신 없음** | *"프로덕션은 Fly.io($0) 그대로"* · *"이건 프로덕션 이관이 아니다 — 명시적으로 기각"* |
| **D-001** (`CONTEXT.md:628`) | `🔄부분무효` (무효화된 건 RDS 편입 1건뿐) | `:648` *"**역설: in-cluster가 최고가**"* · `:671` *"**in-cluster는 그때도 답이 아니다**(상시 $125+)"* |

기각 근거는 *"월 $35도 비싸다"* 였다($200÷$35=5.7개월 → *"문제를 없앤 게 아니라 이연"*).
**새 계산 $140은 그 4배**이므로 **원래 기각 근거를 강화한다.**

> 🔴 `design-change-procedure.md` §4에 따라, 이관을 실제로 결정하면 **위 두 블록에 되돌아가
> 재판정을 표시**해야 한다. 이 문서만 쓰고 원본을 안 고치면 *"일지에 적어뒀으니 됐어"* 형태가 된다.

---

## ✅ 순서 — 선행 조건을 세션형으로 (사용자 결정, 2026-09-11)

**이관을 목표로 두되 선행 조건부터 하나씩.** 각 항목이 **독립적으로 운영 경험이자 이력서 가치**이고,
중간에 그만둬도 손실이 없다. 다 끝나면 이관은 *"DNS를 바꾼다"* 수준으로 줄어든다.

| 순 | 할 것 | 닫는 항목 | 비용 |
|:-:|---|---|---|
| **1** | **백업·복구 리허설** — `pg_dump` → EBS 파괴 → 복구 (**in-cluster 대상**) | B-8 | ~$0.1 |
| 2a | 시크릿 **환경 축** 도입 — JWT 키를 환경별로 `0-bootstrap`에, 시크릿 이름·IAM으로 경계 강제 **✅2026-09-14** | B-2 | $0 |
| 2b | Grafana `instance-id` 환경변수화 + `fdaa::/16` 절 삭제 — **`fly secrets set` 선행 필수** | B-15 | $0 |
| 3 | HTTPS 경로 — ACM + Cloudflare 검증 + `ssl-redirect` | B-3 | ~$0.1 |
| 4 | 메타스페이스 누수 검증 (README 선행 조건) | B-5 | $0~0.1 |
| 5 | 실 AI·메일·채점 경로 검증 (스텁 해제) | B-4 | ~$0.2 |
| 6 | EKS CD 파이프라인 | B-12, B-13 | $0~0.1 |
| 7 | 상시 운영 전제로 안전장치 개정 (리퍼·SOP·이상탐지 임계) | B-1, B-14, B-18 | $0 |

> 🔑 **1번을 먼저 하는 이유**: 백업이 없으면 DB 이전을 **시작할 수 없다**. 그리고 관리형이
> 조용히 해주던 일을 직접 해보는 것이 이 트랙에서 가장 값진 운영 경험이다
> (Stage 3b가 *"관리형이 공짜로 주던 것에 TLS가 있었다"* 를 가르친 것과 같은 종류).
>
> ⚠️ **B-6(스케줄러 중복)·B-11(단일 AZ·다운타임)·B-16(Fly 비용 실측)은 이 목록에 없다.**
> 전환 **시점**에 풀어야 하는 것이고, 선행 조건을 다 채운 뒤 이관 계획서를 쓸 때 다룬다.
> 지금 설계하면 그때 조건이 바뀌어 다시 쓴다.

## 기각한 대안 (재론 방지)

| 대안 | 기각 사유 |
|---|---|
| 바로 상시 전환 후 보완 | 전환 당일 **로그인 불가 + TLS 실패 + 메일 2통** 동시 발생. 사용자가 먼저 발견한다 |
| DB를 Neon 유지 | 전환·롤백이 훨씬 싸지만 **StatefulSet 운영 경험**(사용자 목적)을 못 얻는다. 사용자가 in-cluster를 선택 |
| RDS로 | 월 +$18에 이미 Neon이 같은 일을 $0에 한다 |
| 상시 스테이징 별도 운영 | 비용이 동일한 월 $140인데 **효용은 더 낮다**(사용자 트래픽이 없어 진짜 장애를 못 겪는다) |
| EKS를 여기서 닫기 | 사용자가 *"꼭 해보고 싶다"* 고 명시. 구축 경험은 확보됐으나 **운영 경험**이 목적 |

---

# 항목 1 — 백업·복구 리허설 (착수 설계, 2026-09-12)

> 상태 `✅완료 (2026-09-13)` · 닫는 항목 `B-8` · **실비용 `$0.0848`** (32m10s, 고아 0) · 대상 `in-cluster postgres`
>
> 결과: 볼륨을 실제 파괴한 뒤 S3 덤프만으로 복구 성공. 판정은 센티넬로만 했고,
> `DROP SCHEMA` 실험으로 **Flyway 단독 재생성(26행)** 과 **센티넬 부재** 를 함께 실측해
> *"26행이 있다"* 가 판정 기준이 될 수 없음을 확정했다. 상세는 `docs/eks-migration-log.md` 09-13.

## 사용자 결정

| 결정 | 선택 | 근거 |
|---|---|---|
| 백업 대상 | **in-cluster postgres** (prod Neon 아님) | B-9 미해결 + 학습 클러스터를 prod DB 에 연결 금지(상시 보안 제약) |
| 백업 목적지 | **0-bootstrap 에 S3 버킷 신설** | 백업은 자기가 백업하는 대상보다 오래 살아야 한다. EBS 가 0-bootstrap 이므로 백업도 최소 그 층 — **D-004·L-14 와 같은 규칙의 세 번째 적용** |
| 자동화 범위 | **수동 스크립트만** | CronJob·IRSA 는 상시 운영(항목 7) 전까지 실효가 없다. "세션 켜져 있을 때만 도는 CronJob"은 백업이 아니다 |
| 파괴 방식 | **볼륨을 실제로 파괴** | PVC/PV 만 지우면 `Retain` 탓에 데이터가 남는다 |

## 🔴 Blindspot Pass 가 뒤집은 것 3건 (2026-09-12, 착수 전)

### ① 볼륨을 파괴해도 검사의 판정력이 0 이다 — **가장 중요**

착수 직전에 나는 *"볼륨을 실제로 파괴해야 명제가 반증 가능해진다"* 고 단언했다. **틀렸다.**

```
PERSISTENT-RESOURCES.md:92   "스냅샷 백업은 **의도적으로 만들지 않는다**
                              … 데이터가 Flyway 마이그레이션 12개로 전부 재생성 가능"
application-prod.yml:31-35   migrate-on-startup: true
V11__seed_tech_question_bank_202607.sql  → 26행 시드
postgres-static.yaml:225     실측 주석 "68Mi 는 **26행짜리 거의 빈 DB** 의 값"
```

볼륨을 파괴·재생성한 뒤 **덤프를 복구하지 않아도** core-api 가 뜨면서 스키마와 26행이 전부
돌아온다. 두 가설이 같은 결과를 예측한다:

```
가설 A  복구가 동작했다        → 예측: 26행 존재
가설 B  Flyway 가 재시드했다   → 예측: 26행 존재     ← 같다
```

🔑 **이것은 #416 의 "응답 본문으로 라우팅을 판정할 수 없었다" 와 같은 병이고, 그 교훈을
바로 다음 작업에서 재발시켰다.** 원인은 판정력을 **검사 절차**에서 찾고 **저장소의 성질**에서
찾지 않은 것이다 — 백업 대상이 *마이그레이션으로 재생성 가능한 데이터*뿐이면 **어떤 파괴
방식을 써도** 두 가설이 갈리지 않는다. 파괴의 강도는 이 문제를 풀지 못한다.

**조치 — 센티넬 행.** 마이그레이션이 절대 만들 수 없는 행을 백업 **직전에** 심고, 그 행으로만
판정한다. 그리고 **양방향**으로 본다:

| 시점 | 확인 | 무엇을 증명하나 |
|---|---|---|
| 백업 직전 | 센티넬 **존재** | 덤프에 들어갈 것이 있다 |
| 볼륨 재생성 후·복구 **전** | 센티넬 **부재** | 볼륨이 진짜로 비었다 (파괴가 실제로 일어났다) |
| 복구 후 | 센티넬 **존재** | 데이터가 **덤프에서** 왔다 (Flyway 는 이 행을 만들 수 없다) |

가운데 줄이 없으면 앞뒤만으로는 *"애초에 안 지워졌다"* 와 구별되지 않는다.

### ② `tofu destroy -target` 은 실행조차 안 된다 — 그리고 빼면 안 된다

`ebs-postgres.tf:67-69` 에 `lifecycle { prevent_destroy = true }`. plan 단계 에러라 apply 로
넘어가지도 않는다. 그런데 이건 단순 장애물이 아니라 **0-bootstrap 전체의 오타 방지 latch** 다.
지금 bare `tofu destroy` 를 막고 있는 것이:

```
① ebs-postgres.tf:68          ← 이번에 제거하고 싶어지는 것
② postgres-password.tf:54     random_password.postgres_master 의 prevent_destroy
③ backend-state.tf:14-21      버저닝 켜진 tfstate 버킷에 force_destroy 미설정 → BucketNotEmpty
```

게다가 제거 PR 이 머지되면 `infra-deploy.yml:10-12,36` 이 `paths: infra/aws-eks/**` 로
**0-bootstrap 을 자동 apply** 한다(CI 는 tfvars 를 안 주입하므로 `postgres_persistent_volume_enabled`
는 default `true`). 로컬 destroy 와 타이밍이 겹치면 **CI 가 볼륨을 되살린다.**

**조치 — 경로를 바꾼다.** latch 를 건드리지 않는다:

```
tofu state rm aws_ebs_volume.postgres_data     # AWS 에 아무 일도 일어나지 않는다
aws ec2 delete-volume --volume-id <id>          # 실제 파괴 (내 로컬 크리덴셜로)
tofu apply                                      # state 에 없으므로 새로 만든다
```

부수 효과가 아니라 **더 정확한 시뮬레이션**이다 — 진짜 재해는 terraform 이 모르는 채로
볼륨이 사라지는 것이다. `prevent_destroy` 도 CI 경쟁도 발생하지 않는다.

> ⚠️ `ebs-postgres.tf:75-96` 의 **IAM 자물쇠는 CSI 드라이버에만** 걸린다(세 태그 부재로
> `ec2:DeleteVolume` 조건 불충족). 내 로컬 크리덴셜에는 해당하지 않으므로 위 삭제는 통과한다.
> 즉 *"의도적으로 지울 수 있고, 컨트롤러는 못 지운다"* — 설계대로다.

### ③ 조용한 부분 복구 — `psql` 은 실패하면서 `exit 0` 을 낸다

의심했던 시나리오(Flyway 체크섬 불일치로 기동 거부)는 **일어나지 않는다** — `FlywayConfig.kt`
가 `migrate()` 앞에서 `repair()` 를 호출해 체크섬을 재정렬한다. 대신 실제로 터지는 것:

- core-api 가 먼저 뜨면 Flyway 가 V1~V13 을 다 올린다 → 이후 복구가 `relation already exists`
  / duplicate key 로 **부분 실패**
- `psql` 은 기본 `ON_ERROR_STOP` 이 **꺼져 있어** 에러를 뱉으며 끝까지 돌고 **exit 0**
- `application-prod.yml:9-10` `ddl-auto: validate` 는 **테이블 구조만** 본다 → 행이 반쯤
  없어도 앱은 정상 기동

**조치**: 복구 전 `core-api`·`daily-api` 를 `replicas=0` 으로 내린다 + `psql -v ON_ERROR_STOP=1`.

## 🟡 절차에 반영할 것 (Blindspot 나머지)

| # | 내용 | 반영 |
|:-:|---|---|
| B1-4 | **daily-api 도 같은 `core-api-db` Secret 을 본다**(`daily-api.yaml:64,70-71`). 그리고 마이그레이션이 **두 모듈에 쪼개져 있어**(core-api V1~V6·V8·V9 / db-core V7·V10~V13) 복구한 `flyway_schema_history` 를 들고 daily-api 에서 migrate 를 켜면 `repair()` 가 core-api 버전을 `DELETED` 로 마킹 → **core-api 영구 부팅 불가**(07-01 V8 사고 재현) | 복구 중 두 앱 모두 `replicas=0`. **"마이그레이션을 다시 돌려보자"는 유혹이 정확히 이 버튼** |
| B1-5 | attach 된 볼륨은 `VolumeInUse` 로 삭제 실패. `aws_ebs_volume` 삭제가 detach 를 대신 해주지 않는다 | 순서 고정: `sts replicas=0` → detach 완료 → PVC 삭제(`pvc-protection`) → PV 삭제(`pv-protection`) → 그제서야 삭제 |
| B1-6 | `volumeHandle`(`postgres-static.yaml:86`)·PVC `volumeName`(`:126`) 은 **둘 다 불변**이라 patch 로 새 ID 를 꽂을 수 없다 → PV/PVC 는 **지우고 새로 만드는 것 외에 선택지가 없다**. 따라서 `:78` 의 `claimRef:null` 패치는 이 경로에서 **필요 없고**, 오히려 그 길로 가면 **stale volumeHandle 로 없는 볼륨을 attach** 하려 든다 | 재사용 시도 금지, 항상 재생성 |
| B1-7 | 볼륨이 없는 구간에 `tofu output -raw` 가 **null** → SOP 의 `sed \| kubectl apply` 파이프라인에 `pipefail` 이 없어 **빈 `volumeHandle:` 이 그대로 apply 된다** | 치환 전 `[ -z ]` 가드 (SOP §2b 가 같은 함정을 이미 한 번 잡았다) |
| B1-8 | 리허설 중 **원장·SOP·배너가 전부 "고장난 것처럼" 보인다** — SOP §9b 합격 기준은 0 건이 아니라 *원장과 일치*, `eks-session-marker.sh:90` 은 볼륨 0 개일 때 *"스토리지는 전부 destroy 로 회수됩니다"* 라는 **적극적으로 틀린 안심 문구**를 낸다, `session-status.sh:185` 도 "영속 EBS 0개" | 재생성 완료를 **세션 종료 전 명시적 게이트**로 둔다 + 일지에 그 구간 기록 |
| B1-10 | 새 `aws_s3_bucket` 은 `infra-ci.yml:26-32` tfsec 에서 **PR 이 막힌다** — public-access-block 계열 HIGH 4종 + logging + CMK | `backend-state.tf:9,22` 관례대로 **근거를 적은** `#tfsec:ignore:` + `public_access_block` 동반 |
| B1-11 | 원장 규칙과 3중 충돌: **(a)** `PERSISTENT-RESOURCES.md:92` 의 *"스냅샷 백업은 의도적으로 만들지 않는다"* 를 **역전**하는 것이므로 `design-change-procedure.md` §4 대로 **원본 결정 블록에 재판정 표시** 필요 **(b)** 버저닝을 켜면 `expiration` 만으로는 증가 상한이 아니다 → `noncurrent_version_expiration` + `abort_incomplete_multipart_upload` 필수 **(c)** §확인 명령이 EBS·ECR 만 조회 → S3 줄을 안 넣으면 **신설 버킷이 원장 대조에서 영원히 안 보인다** | 셋 다 이 PR 에서 처리 |
| B1-13 | 노트북 `pg_dump` 는 상위 서버를 덤프하지 않는다(`server version 17.x; pg_dump version 15.x — aborting`) → **파드 안에서** 실행. 단 **`-it` 금지** — TTY 가 붙으면 스트림이 텍스트 변환돼 `-Fc` 아카이브가 깨진다(`-i` 만). alpine 에 `aws` CLI 가 없으므로 업로드는 노트북 쪽 | 스크립트에 고정 |
| B1-14 | TLS 는 접속 경로와 무관 — 파드 안 `pg_dump` 는 유닉스 소켓(`local all all trust`)이라 **비밀번호도 TLS 도 불필요**. `sslmode=require` 는 앱 JDBC 경로에만 해당 | 🔑 함의: **kubectl 접근권 = DB 무인증 superuser 접근권**이고, 이 절차가 그 경로를 표준으로 승격시킨다 |
| B1-15 | 비밀번호는 유지된다 — `random_password.postgres_master` 는 0-bootstrap state 항목이고 볼륨과 무관. 새 볼륨은 **그 값으로 initdb** 된다(L-14 가 잡은 사고는 반대 방향) | ⚠️ **`pg_dumpall` 금지** — 롤의 SCRAM 해시가 덤프에 들어가 S3 로 나간다. `pg_dump` 단일 DB 로 한정 |
| B1-16 | 리퍼는 이 작업을 안 건드린다(`eks-session-marker.sh:48` 이 2-cluster 하드코딩). 다만 **0-bootstrap 에서 `tofu apply` 를 칠 때마다 마커가 덮어써져 `applied_at` 이 리셋**되어 "~N분 과금 중"이 과소보고 | 🔴 **더 큰 발견: 영속 리소스를 destroy 하는 행위에는 어떤 가드도 없다.** 마커·리퍼·guard 가 전부 `apply` 만 본다. 이 리허설이 그 구멍을 **처음 쓰는 사례** → 원장 등재 |
| B1-17 | 재생성을 `aws ec2 create-volume` 로 하면 `Persistent=true` 태그가 없어 **고아로 잡히는 동시에 state 밖이라 리퍼도 못 지운다**(무한 경고) | 반드시 `tofu apply` 로 복원 |
| B1-19 | PV capacity 가 `postgres-static.yaml:70`·`:123` 두 곳에 `10Gi` 하드코딩 | 재생성 기회에 `postgres_volume_size_gb` 를 만지지 않는다 |
| B1-20 | 버킷명에 **계정 ID 금지**(`providers.tf:13`, `outputs.tf:10-14` sensitive). 레포 관례는 리전 suffix(`devquest-eks-tfstate-seoul`) | `devquest-eks-backups-seoul` |

## 절차 (최종)

```
[$0]  ① S3 백업 버킷 신설 (0-bootstrap) + 원장 등재 + 재판정 표시
      ② db-backup.sh / db-restore.sh + 목 테스트
      ③ 튜토리얼 절차 문서

[유료] ④ apply → 센티넬 심기 → 백업 → S3 업로드
      ⑤ 앱 replicas=0 → PVC/PV 삭제 → state rm → delete-volume
      ⑥ tofu apply 로 볼륨 재생성 → PV/PVC 재작성 → postgres 기동
      ⑦ 🔴 복구 **전** 센티넬 부재 확인  ← 판정력의 원천
      ⑧ psql -v ON_ERROR_STOP=1 로 복구 → 센티넬 존재 확인 → 앱 기동
      ⑨ 볼륨 재생성 완료를 원장과 대조(게이트) → teardown
```

---

# 항목 2 — 시크릿 환경 분리 (착수 설계)

| | |
|---|---|
| 상태 | **2a ✅ 코드 완료 (2026-09-14)** · 2b 미착수 |
| 비용 | **$0** — 클러스터 불필요. 생성되는 것은 `random_password` 2개(= tfstate 항목, AWS 리소스 아님) |
| 닫는 항목 | **B-2** (2a). ~~B-15~~ → **2b 로 분리**, 아래 참조 |

## 🔴 Blindspot Pass 가 뒤집은 것 (2026-09-14, 착수 전)

### ① 이 계획을 글자 그대로 실행하면 **경계가 없어진다** — 가장 중요

계획 원문은 *"JWT를 `0-bootstrap`으로(L-14 패턴)"* 였다. 그대로 하면 키가 **하나**다.

D-004·L-14 규칙(*수명이 같아야 하는 것은 같은 레이어에*)의 앞선 세 번은 전부
**학습 전용 단일 환경**이었다 — ①postgres 비밀번호 ②영속 EBS ③백업 S3. 그래서 규칙을
그대로 적용해도 아무것도 섞이지 않았다.

**JWT 키는 처음으로 두 환경에 걸친다.** 수명만 맞추고 환경을 섞으면:

```
학습 클러스터가 영속 키로 서명  →  prod 도 같은 0-bootstrap 출력을 읽음
→ 학습 클러스터(또는 kubectl 보유자)가 prod 유효 토큰을 발급할 수 있다
```

`2-cluster/secrets.tf` 가 **스스로 금지해둔 상태**다 — *"학습 클러스터에 prod 크리덴셜을
넣으면 그 클러스터가 실서비스 사용자에게 유효한 토큰을 발급할 수 있게 된다."*
OAuth 로는 지키고 JWT 로는 뚫리는 경계는 경계가 아니다.

확도: 메커니즘 🔴(코드 확인 — `0-bootstrap/variables.tf` 에 환경 축 부재, ESO `key` 하드코딩)
· 영향 크기 🟡(전환 후 토폴로지 미결정. 단 **연습용 재구축**만으로도 발화한다)

→ **채택: 환경 축 도입** (사용자 결정). 키 2개 + 시크릿 이름 2개 + ESO key 파라미터화.

### ② B-2 의 피해 서술이 두 시점을 섞어놨다

B-2 본문은 *"클러스터를 한 번 부술 때마다 전 사용자 강제 로그아웃"* 이라고 썼다.
**지금은 그런 일이 일어나지 않는다** — 실사용자 토큰은 **Fly 의** `JWT_SECRET` 으로
서명되고(`be/fly.toml` `[env]` 에 없음 = `fly secrets`), Fly secrets 는 **write-only**
(B-9 와 같은 근거). 즉 현재 prod 키를 읽어서 AWS 로 옮길 수 없다.

🔑 **전환 당일 전 사용자 로그아웃은 어느 레이어에 두든 확정이다.**
이 항목이 사는 것은 *"전환 **이후** teardown 마다 로그아웃"* 뿐.

→ 전환 시점 항목(B-6·B-11·B-16 묶음)에 **"JWT 재발급 = 전원 로그아웃, 사전 공지"** 추가할 것.
   `JWT_EXPIRATION_MS` 30일이라 재로그인 파도가 30일간 이어진다.

### ③ OAuth 경계는 이미 닫혀 있다 — 할 일 0

4중으로 확인됐다: 자리표시가 변수 default 리터럴 · 2-cluster 는 CI apply 매트릭스에 없음
(+ `infra-ci.yml` 이 기계적 차단) · CI 주입 `TF_VAR_*` 는 예산 이메일 하나 · `*.tfvars` 가
세 레이어 전부 gitignore. → 계획서의 "prod/학습 경계" 문구가 **이미 성립된 것을 다시 하려는
것처럼** 읽혔다. 실제 미비 지점은 **JWT 한 곳**이었다.

## 🟡 확인된 사실 (설계를 단순하게 만든 것)

| | 내용 |
|---|---|
| **값만 옮긴다, 시크릿은 안 옮긴다** | postgres 도 그랬다: `random_password`(0-bootstrap) → `outputs.tf` → `2-cluster/remote-state.tf` → 소비. `aws_secretsmanager_secret` 리소스는 2-cluster 소유로 남는다 → **ARN·IAM·$0.40/월 전부 변화 없음** |
| **`tofu state mv` 불필요** | 2-cluster state 항목 **0개** 실측(클러스터 미가동). `random_password.jwt_secret` 은 state 에 존재하지 않으므로 코드에서 지우면 끝 |
| **CI 영향** | `infra-deploy.yml` 매트릭스가 `[0-bootstrap, 1-network]` → 머지 시 자동 apply. 실측 plan **`2 to add, 0 to change, 0 to destroy`**, 비용 $0 |
| **치환 관례가 이미 있다** | `externalsecret-db.yaml` 의 `RDS_MASTER_SECRET_PLACEHOLDER` + `sed \| kubectl apply -f -`. 새 도구(kustomize·envsubst) 도입 없이 재사용 |
| **출력도 이미 있다** | `2-cluster/outputs.tf` 의 `app_secret_name` — sed 소스를 새로 만들 필요 없음 |
| **`random` provider 제거** | 2-cluster 의 유일한 사용처가 JWT 였다. 안 지우면 `versions.tf` 주석이 거짓으로 남는다 |

## ⚠️ 적용 순서 함정 (U-7)

`2-cluster` 는 새 출력 `jwt_secrets` 를 참조하는데, 그 출력은 **0-bootstrap 이 apply 된 뒤에만**
존재한다. 머지 전에 2-cluster 를 로컬 apply 하면 `output not found` 로 실패한다.
`remote-state.tf` 가 이미 경고하는 것과 같은 함정이고, 순서는 그대로다 — **머지 → CI 가
0-bootstrap apply → 그 다음 2-cluster 로컬 apply.**

## 경계를 무엇이 강제하는가

문서가 아니라 **IAM** 이다.

```
시크릿 이름   <cluster_name>/<environment>/app        (2-cluster/secrets.tf)
ESO 정책      resources = [... aws_secretsmanager_secret.app.arn ...]   (irsa-eso.tf)
→ environment=learning 으로 선 클러스터의 ESO 역할에는
  prod 시크릿 ARN 에 대한 권한이 **아예 없다**. 매니페스트를 손으로 고쳐도 못 읽는다.
```

### ⚠️ 경계는 **절반만** 세워졌다 (QA F-2, 2026-09-14)

위 문장은 **두 배포가 공존할 수 있을 때에만 참이다. 지금은 아니다.**

```
2-cluster/backend.tf      key          = "2-cluster/terraform.tfstate"  ← 환경 없음
2-cluster/variables.tf    cluster_name = "devquest-eks" (default)       ← 환경 없음
2-cluster/irsa-eso.tf     name         = "${var.cluster_name}-eso"      ← 두 환경 동일
```

셋 다 단일값이라 learning·prod 2-cluster 를 **동시에 세울 수 없다**(state·클러스터명·IAM
역할명 충돌). 지금 `environment` 가 하는 일은 **한 배포의 라벨을 가르는 것**이다.

| 세워진 것 (2a) | 남은 것 (prod 전환 선행 조건) |
|---|---|
| 키가 환경별로 분리 (`0-bootstrap`) | `backend.tf` state key 를 환경별로 (`2-cluster/<env>/terraform.tfstate`) |
| 시크릿 이름·IAM 스코프가 올바른 모양 | `cluster_name` 을 환경별로 (서브넷 태그가 여기 묶여 있어 1-network 도 영향) |
| 소비 측이 자기 환경만 인덱싱 | IAM 역할명 충돌 해소 (`${cluster_name}-eso`) |

🔑 **이걸 등재하는 이유**: 2a 의 주석·커밋 메시지가 처음엔 조건절 없이 *"IAM 이 경계를 지킨다"*
라고 단정했다. **참인 문장을 그 유효 범위 밖에 쓴 것**이고, #417 퀴즈 Q4 가 잡은 것과 같은 병이다
(*"조건절을 떨어뜨리면 경고가 거짓말이 된다"*). 정정해서 코드 주석에 반영했다.

## 항목 2b — B-15 (분리, 미착수)

**분리 사유**: `fly secrets set` 이 **머지보다 먼저** 실행돼야 하는데(`be-cd.yml` 이 main
push 마다 자동 배포), 2a 에 섞으면 인프라 전용 PR 이 prod 배포를 트리거한다.

| 대상 | 변경 | 위험 |
|---|---|---|
| `application-prod.yml:19` `instance-id: "1680166"` | `${GRAFANA_OTLP_INSTANCE_ID:}` | 🔴 **Fly prod 도 같은 `prod` 프로파일**(`fly.toml:12`). 값이 비면 `GrafanaOtlpCredentialsCondition` 이 false → 빈 미등록 + WARN 1줄. `enabled: true` 는 남아 **설정은 켜졌다는데 레지스트리가 없는** 상태. `/health` 스모크로 **안 잡힌다** |
| `SecurityConfig.kt:43` `hasIpAddress('fdaa::/16')` | **절 삭제** (`127.0.0.1`·`::1` 만) | 🟡 EKS CIDR 로 "교체"하면 소스가 ALB 라 `/actuator/**` 가 **인터넷에 열린다**(Ingress 는 HTTP 80 평문). 삭제만이 확대가 아님을 증명할 수 있다. Fly 는 `[metrics]` 블록이 없어 안 깨진다 |
| `OtlpMetricsConfigTest` | 보강 필요 | 🔴 3개 테스트가 `instance-id=1680166` 을 **프로퍼티로 직접 주입** → yml 을 비워도 전부 GREEN. **안전망이 변경 지점을 안 덮는다** |

🔴 **2b 착수 전 필수 (사용자 실행)**: `fly secrets set GRAFANA_OTLP_INSTANCE_ID=<값>`
→ 확인 후 PR 생성. 순서를 뒤집으면 prod 메트릭이 조용히 꺼진다.

⚪ **별건**: `instance-id "1680166"` 은 이미 **퍼블릭 레포 히스토리에 평문**이다. 환경변수로
빼도 히스토리에는 남는다 → "제거"가 아니라 *"이게 정말 비밀인가"* 를 먼저 판정해야 한다.
instance ID 는 회전 불가한 계정 식별자일 가능성이 높고, 그렇다면 진짜 비밀은
`GRAFANA_API_KEY` 쪽이며 그건 이미 레포에 없다. 판정 후 CONTEXT 의 해당 항목을 갱신할 것.

## 절차 (2a — 전부 $0)

```
① 0-bootstrap/jwt-secret.tf 신설 — for_each 로 환경별 키 + prevent_destroy
② 0-bootstrap variables(jwt_environments) / outputs(jwt_secrets, sensitive)
③ 2-cluster variables(environment) — 검증 정규식은 시크릿 이름 세그먼트 규칙
④ 2-cluster secrets.tf — 이름에 환경, JWT_SECRET 을 remote state 로, random_password 삭제
⑤ 2-cluster versions.tf — random provider 제거
⑥ k8s/eso/externalsecret-app.yaml — APP_SECRET_NAME_PLACEHOLDER
⑦ 튜토리얼 2-3 절 + 원장(state 안의 값) + 이 문서
⑧ tofu validate ×2 + 0-bootstrap plan 으로 "2 to add / 0 to destroy" 실측
```

> 🔴 **실검증은 다음 유료 세션에서** — 2-cluster 는 apply 없이 검증할 수 없다.
> 그때 확인할 것: `app_secret_name` = `devquest-eks/learning/app` · ESO 동기화 성공 ·
> 파드가 `JWT_SECRET` 을 받는가 · **teardown 후 재apply 에서 키가 그대로인가**(이 항목의 목적).
