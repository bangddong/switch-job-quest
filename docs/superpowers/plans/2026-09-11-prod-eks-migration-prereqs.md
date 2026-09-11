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
| **1** | **백업·복구 리허설** — `pg_dump` → EBS 파괴 → 복구 | B-8, B-9 | ~$0.1 |
| 2 | 시크릿 환경 분리 — JWT를 `0-bootstrap`으로(L-14 패턴), prod/학습 경계 | B-2, B-15 | $0 |
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
