# prod → EKS 이관: 선행 조건 (2026-09-11)

> 📌 **D-013** · 상태 `🚧진행중` · 영향 `infra/aws-eks/README.md`, `.claude/CONTEXT.md`(D-001),
> `.claude/scripts/eks-reaper.sh`, `docs/eks-session-sop.md`, `infra/aws-eks/2-cluster/secrets.tf`,
> `k8s/base/`, `.github/workflows/`, `fe/vercel.json`,
> **`docs/eks-cost-model.md`** (2026-09-22 추가 — 이 계획이 그 문서의 *"prod 는 Fly+Neon 이라
> 계정 폐쇄돼도 무영향이다"* 를 거짓으로 만든다. 그 문장이 안전 예비 $30 규칙의 근거다),
> **`infra/aws-eks/PERSISTENT-RESOURCES.md`** (2026-09-22 추가 — *"선행 조건 7에서 재검토"* 를
> 두 번 써둔 ACM 갱신 함정의 출처)
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

#### ✅ 2026-09-18 실측 — B-5 는 성립한다. 단 **근거가 위 서술과 다르다**

`fly ssh console` 머신 내부 조회(업타임 **70.8h = 2.95일**, prod 실트래픽):

```
MemTotal 459 MiB · MemAvailable 26.6 MiB · SwapTotal 256 · SwapFree 223 (사용 32.6) · java RSS 386.7 MiB
```

| | 실효 용량 (앱 레벨) |
|---|---|
| **Fly** | **앱 상한 409** + swap 256 = **665 MiB** |
| **K8s** | limits 576 + swap 0 = **576 MiB** |
| | → **K8s 가 89 MiB(13%) 적다** |

> 🔴 **처음엔 `459 + 256 = 715` 로 적어 격차를 139 MiB(24%)로 과대평가했다 (QA F-1).**
> **층위가 달랐다** — `459` 는 **Fly VM 총 메모리**(비앱 오버헤드 ~50 MiB 포함)인데
> `576Mi` 는 **컨테이너 전용 한계**다. 이 문서가 이미 확립한 **앱 레벨 상한은 `anon-rss ~409`**
> (kill 수위, 커널 OOM 로그 8건 전수)이므로 그쪽에 맞춰야 사과 대 사과가 된다.
> 방향(K8s 가 작다)은 같지만 **격차가 139 → 89 MiB 로 1.6배 부풀어 있었고**, 그 수치가
> 아래 옵션①의 상향폭 판단에 직접 들어간다.
> ⚠️ 백분율을 나란히 쓰지 않는다 — 옛 `24%` 는 576 을, 새 `13%` 는 665 를 분모로 써서
> **비교 가능한 쌍이 아니다**(QA 부수 지적). 절대값(139 → 89 MiB)으로만 비교한다.

🔴 **핵심은 *"swap 이 없다"* 가 아니라 *"Fly 의 RAM(459)이 K8s limits(576)보다 작아서
swap 을 뺀 총량이 역전된다"* 는 것이다.** 위 산술의 *"마진 168Mi(576−408)"* 는 **RSS 가 408 에서
안정화된다고 가정**했는데, 실측은 **2.95일에 386.7 이고 아직 creep 중**이다(가용 RAM 26.6 MiB,
swap 32.6 MiB 사용 = RAM 초과분이 이미 나가고 있다).

현재 RSS 기준 K8s 런웨이 = (576 − 386.7) ÷ 3MB/h = **63시간 ≈ 2.6일** → 원 산술과 같은 자릿수.

> ⚠️ **확도 🟡 — 이것도 산술이다.** `3MB/h` 는 **2026-07 측정값**이고 현재 속도는 미지수다.
> 위 원 산술에 붙인 경고와 **같은 수준으로 읽어야 한다**(QA F-6: 이 마커를 빠뜨렸었다).

> 🔑 **`swap_size_mb = 256`(#245)이 유일한 방어선이고 설계대로 작동 중이다.**
> 이관하면 그 방어선이 사라진다.
>
> 🔴 **재판정 (2026-09-24) — B-5 는 `🔄부분무효` 다. 비교 축이 틀렸다.**
> *"K8s 에 swap 이 없다"* 는 **사실**이고, *"그래서 Fly 보다 위험하다"* 가 **틀렸다.**
> 위 표는 **용량**(Fly 665 vs K8s 576)을 비교하는데, 정작 중요한 것은 ***지금 발자국 428.5 MiB 가
> 각 RAM 천장에 들어가는가*** 다 — **Fly 409 ❌**(그래서 53.6 MiB 가 swap 에 나가 있다) /
> **K8s 576 ✅ 여유 147.5**. ***Fly 는 swap 이 있어서 여유로운 게 아니라 RAM 천장이 발자국보다
> 낮아서 swap 이 필수인 것이다.*** 상세·표본: 「항목 4 — 종료」(2026-09-24).

**남은 미지수 = 현재 creep 속도**(3MB/h 는 2026-07 값).
→ **같은 명령을 며칠 간격으로 두 번 더 찍으면 기울기가 나온다. 클러스터 불필요, $0.**
⚠️ *"클러스터를 2~3일 켜서 OOMKill 을 본다"*(≈$7~10)보다 낫다 — prod 실트래픽을 보고, $0 이고,
**한 번도 죽이지 않는다.**

**대응 선택지 (미결정)**
| | 내용 | 대가 |
|---|---|---|
| ① | `limits` 576 → 768Mi | 노드 메모리 재산정 필요. **현 requests 합계 1568Mi** (core-api 480 + **ai-api 320** + daily-api 512 + postgres 256, 전부 replicas=1) + LBC 96 — ⚠️ 처음엔 ai-api 320Mi 를 빠뜨려 1248 로 적었다(QA F-2). **1568 은 #414 가 산출한 그 값**이고, 거기에 노드당 allocatable 1365Mi 를 나누면 3노드가 이미 꽉 찬다 |
| ② | K8s swap 활성화 (`NodeSwap`) | ⚪ **성숙도를 확인해야 한다** — 내가 *"알파"* 로 적었는데 QA 가 *"1.28 에서 Beta 승격"* 을 지적했다(F-5). 이 클러스터는 **1.36** 이다. 둘 다 오프라인에서 확정할 수 없으므로 **착수 시 문서로 확인할 것.** 어느 쪽이든 AL2023 노드에서 실제로 켜지는지는 별도 검증이 필요하다 |
| ③ | creep 자체를 잡는다 | 근본적이지만 원인이 `⚪ 미확정`(커밋된 페이지를 서서히 터치 + native ~95MB) |
| ④ | 1GB 인스턴스 | Fly ~$5.7/월. **prod 를 Fly 에 두는 한 이게 가장 싸다** — 이관 자체의 동기를 약화시킨다 |


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

더 나쁜 것: **안전 예비 $30 규칙의 근거 문장**이 `docs/eks-cost-model.md` 「Free Plan」 — *"prod는 Fly+Neon이라
계정 폐쇄돼도 무영향"* 이다. **이 계획은 그 전제를 깨면서 그 전제 위에 세워진 예산 상한을
그대로 쓴다.**

---

## 🟡 그 외 미충족 (요약)

| # | 내용 | 근거 |
|:-:|---|---|
| B-7 | in-cluster TLS가 자체서명 — 코드가 *"MITM을 막지 못한다. 실제 운영이라면 cert-manager"* 라고 명시. 인증서도 2-cluster 소유라 세션마다 재생성 | `postgres-tls.tf:28-60` |
| B-8 | **백업 0건.** `pg_dump`·`pg_restore`·스냅샷 스케줄 레포 전체 0. D-001 기각 사유 ④가 정확히 *"자동 백업·PITR을 전부 자작"* | 전수 grep · `infra/aws-eks/README.md` 「결정 기록」 D-001 기각 사유 ④ |
| B-9 | Neon 크리덴셜을 **읽을 수 없다** — Fly secrets가 write-only. `pg_dump` 선행 조건 미충족 | `eks-migration-log.md:365` |
| | ⚠️ **09-12 재배치**: 항목 1의 대상을 in-cluster 로 정했으므로 **항목 1은 B-9 를 닫지 않는다.** Fly secrets 는 그대로 write-only 다. B-9 는 **전환 시점 항목**(B-6·B-11·B-16 과 같은 묶음)으로 옮긴다 — 실데이터 이전을 실제로 할 때 사용자가 Neon 콘솔에서 재발급해야 한다 | |
| B-10 | 3서비스로 가면 **기동 순서 강제 필요**(core-api가 Flyway를 먼저 끝내야 daily-api가 뜬다). `k8s/base/`에 initContainer·Job·순서 제약 0건 | `daily-api/application-prod.yml:30-41` |
| B-11 | 노드 2대가 **단일 AZ** 핀 = 가용성 아님. `replicas: 1` + `Recreate` = 배포마다 ~30초 다운타임. PDB 없음. 원장 **L-43·L-47** 미해결 | `nodes.tf:28` · `core-api.yaml:20,30,37` |
| B-12 | **EKS CD가 0.** `ecr-push.yml`이 `workflow_dispatch`+`pull_request`만, 배포는 손작업 `sed \| kubectl apply`. ~~원장 **L-44**(PR 빌드 태그가 레포에 없는 커밋을 가리킴)~~ → 🔴 **인용 철회 (2026-09-21)**: L-44 는 **`⚪ obsolete`(09-04)** 다 — ①SOP §2b 가 이미 문서화한 **중복 등재**였고 ②`latest` 불일치는 재빌드로 소멸했다. **남는 근거는 트리거 부재 하나뿐**이고, 그건 여전히 유효하다. 상세: 「항목 6 — 착수 설계」① | `ecr-push.yml:12-37` |
| B-13 | Fly를 살려두면 **롤백 타깃이 계속 움직인다**(main push마다 재배포 + 동결된 Neon). 끄면 롤백 가치가 준다. 원장 **L-36** | `be-cd.yml:5-7,40` |
| B-14 | `prod-smoke-daily.yml` **3중 고장**: Vercel을 때려 Fly/EKS 구분 불가 · 실패 안내가 `fly status` 하드코딩 · 05:23 KST라 클러스터 상시 가동 전제 | `prod-smoke-daily.yml:20,45,54` |
| B-15 | 🔴 **재판정 (2026-09-14) — 대부분 해소.** ~~`secrets.tf`가 *"값의 부재를 스위치로"* 확정한 것을 뒤집어야 함. `application-prod.yml:19` `instance-id: "1680166"` 이 **진짜 값 하드코딩**이라 학습/prod 메트릭이 섞인다.~~ → **섞이지 않는다.** 학습 클러스터에 `GRAFANA_API_KEY` 가 없어 `GrafanaOtlpCredentialsCondition` 이 false 이고, 이는 **#355(08-03)에서 자리표시 3종을 삭제하며 이미 끝난 사고**다. 이 행은 그것을 09-11 에 **현재형으로 다시 적은 것**이다. `instance-id` 도 Basic auth 의 username 이라 시크릿이 아니다(상세: 하단 「항목 2b — 재판정」). `SecurityConfig.kt` 의 `hasIpAddress('fdaa::/16')` 절(Fly 사설망)은 **EKS에서 아무것도 열지 않는다** | ~~`secrets.tf:144-166`~~ → 남은 실행 항목은 `be/core/core-api/.../SecurityConfig.kt` 의 `hasIpAddress('fdaa::/16')` 절뿐이고, **항목 3 에서 처리한다** |
| B-16 | **Fly `suspend`의 "$0"이 미검증** — `min_machines_running=1`이면 최소 1대는 계속 running일 수 있다. ~~`CONTEXT.md:520` 은 이걸 "(비용)" 항목으로 분류해뒀다~~ → **그 줄은 #427 로 소멸했다** (A/B/C 판정에서 이관 대상이 아니었다). **분류 근거가 없어졌으므로 이 항목은 실측으로만 판정한다.** `grep suspend *.md` → 0건 | ⚪ 실측 필요 |
| B-17 | $140에서 빠진 것: **ALB LCU**(prod는 정의상 실트래픽 상시 — `ingress.yaml:9-11`이 최악 고정비의 4배 경고) · NAT +$32 · KMS $1+ · CloudWatch. 그리고 **이상탐지 DAILY $5 임계가 $4.6/일 상주로 무력화** | `budget.tf` · `cost-anomaly.tf:55,71` |
| B-18 | 상시 전환은 **학습 전제 위에 세운 통제 전체를 무근거로 만든다**: SOP 전체 · `assert-eks-quiz.sh` · tfsec 예외 4건(전부 *"세션마다 폐기되는 학습 자산"* 근거) · 원장 L-49 | 다수 |

---

## 🔴 뒤집어야 하는 기존 결정 ~~2건~~ **3건** — 아직 유효 상태다

| 결정 | 현재 상태 | 내용 |
|---|---|---|
| `infra/aws-eks/README.md:9,11-22` | **갱신 없음** | *"프로덕션은 Fly.io($0) 그대로"* · *"이건 프로덕션 이관이 아니다 — 명시적으로 기각"* |
| **D-001** (`infra/aws-eks/README.md:362`) | `🔄부분무효` (무효화된 건 RDS 편입 1건뿐) | `README.md` D-001 블록 *"**역설: in-cluster가 최고가**"* · `README.md` D-001 블록 *"**in-cluster는 그때도 답이 아니다**(상시 $125+)"* |
| **D-002** — 🔴 **누락돼 있었다 (2026-09-22 추가, 항목 7 Blindspot U-B)** | `✅유효` | `infra/aws-eks/README.md` 「상시 운영은 기각 — 자기 선례와 충돌」. **이 계획이 정면으로 뒤집는 바로 그 결정인데 이 표에 없었다.** 6b 행은 *"상주는 D-002 를 건드린다"* 라고 **이미 적어놓고** 표에는 안 넣었다. D-002 의 「영향」 목록(`eks-session-sop.md`·`eks-reaper.sh`·`2-cluster`·`README.md`)이 **항목 7 의 작업 대상과 동일**하다. 🔴 그리고 D-002 는 *"$200 크레딧 수명 **5~7주**"* 를 **이미 계산해뒀다** — 아래 0번 항목이 독립으로 낸 *"≈1.4개월"* 과 같은 값이다 |

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
| ~~2b~~ | 🔴 **재판정으로 해소 (2026-09-14)** — `instance-id` 는 Basic auth 의 username 이라 시크릿이 아니고, 메트릭 혼입은 **#355(08-03)에서 이미 끝난 사고**였다. 남은 `fdaa::/16` 절 삭제는 **항목 3 으로 이관** | B-15 ✅ | $0 |
| 3 | 🔴 **쪼갰다 (2026-09-15, Blindspot U-3)** — ~~한 유료 세션(~$0.1)~~. **$0 선행분**: ⓐ ACM 발급 **✅** ⓑ Cloudflare 수동 검증(사용자, TASK-10) ⓒ `SecurityConfig` 의 `fdaa::/16` 절 삭제. ~~**유료분**: Ingress annotation 3개 + 실 HTTPS + 2a 실검증.~~ ✅ **완료 (2026-09-16 유료 세션, ~$0.1)** — 2026-09-22 추가. 이 행이 09-15 쪼갠 시점 그대로 방치돼 **읽는 사람을 미완으로 오도했다**(실제로 오도됐다). ⚠️ ~~*"같은 `/actuator/**` 노출 표면"* 이라 묶는다~~ → **근거가 성립하지 않는다**: EKS 쪽 그 표면은 코드가 아니라 `ingress.yaml` 라우팅이 소유한다(*"📌 노출하지 않는 것: `/actuator/**`"*). 게다가 `be/**` 와 `infra/aws-eks/**` 는 **서로 다른 자동 파이프라인**(`be-cd`→Fly prod, `infra-deploy`→`tofu apply`)을 발사하므로 한 PR 에 담으면 안 된다. 상세: 「항목 3 — 착수 설계」 | B-3, B-15 | **$0 + ~$0.1** |
| 4 | 🔴 **재정의됨 (2026-09-18)** — ~~메타스페이스 누수 검증~~ → **RSS creep vs swap 부재**. 메타스페이스 누수는 **이미 없음이 확정**(2026-07-15 근거 3종)이고 *"두 리스크는 별개"* 로 명시 판정돼 있다(0.73 vs 3 MB/h). README:24 의 옛 프레이밍을 이 계획서가 현재형으로 옮긴 것 — **2b 와 같은 형태의 오류**. **$0 로 실측 완료**: ~~Fly 실효 715 MiB vs K8s 576 MiB → 139 MiB 적다.~~ 🔴 **두 군데가 틀렸다 (2026-09-23 정정)**. ① **숫자**: QA F-1 이 이미 기각한 값이다 — `459` 는 **VM 총 메모리**고 `576Mi` 는 **컨테이너 전용 한계**라 **층위가 다르다**. 앱 레벨로 맞추면 **Fly 665(앱 상한 409 + swap 256) vs K8s 576 → 89 MiB 적다**. ***정정은 본문(B-5)과 `jvm-observability-notes.md` 에만 반영되고 이 행과 `infra/aws-eks/README.md` 는 기각된 값을 5일간 들고 있었다.*** ⚠️ 백분율을 나란히 쓰지 않는다 — 옛 `24%` 는 576 을, 새 `13%` 는 665 를 분모로 쓴다. ② **포인터**: 「항목 4 — 재정의」라는 **절은 존재한 적이 없다.** 상세는 위 **「B-5」** 절과 `docs/jvm-observability-notes.md` §1 이다. 🔑 **미완은 이 항목 하나뿐인데 그 행이 기각된 숫자와 깨진 포인터를 동시에 들고 있었다.** ✅ **완료 (2026-09-24) — ⑤ 현행 유지(`limits` 576Mi 그대로)**. 표본 3개(동일 JVM, 재시작 없음)로 🔴 **`3 MB/h` 를 기각**했다(실측 **0.063 MiB/h**, 54배 과대 — 3 MB/h 면 RSS 가 774 MiB 여야 하는데 머신 총량이 715 다). 🔴 **B-5 의 비교 축도 틀렸다**: *용량*(665 vs 576)이 아니라 **지금 발자국 428.5 가 각 RAM 천장에 들어가는가**가 문제이고, 그 기준으로는 **Fly(409) ❌ / K8s(576) ✅ 여유 147.5** 로 **방향이 뒤집힌다** → B-5 `🔄부분무효`. 런웨이 2.6일 → **40~98일**. 🔴 **`§1` 의 *"매일 +4.2 MiB 점프"* 도 실측 기각** — 누적이면 상한 160 을 넘겨 죽어 있어야 한다. *"전형적 지연 로딩"*(기동 후 1회)이 맞다. 🔴 **덤으로 선행 조건과 무관한 prod 리스크 1건**: 메타스페이스 **140.11/160 = 87.6%**, **+0.225 MiB/일 → 상한까지 88일**. `limits` 로는 안 고쳐진다(`MaxMetaspaceSize` 는 별개) — 2026-07-14 OOME 재발 경로. 조건은 *"`be/**` 배포 없이 ~85일"* → **CONTEXT 제품 백로그 소유**. 상세: 「항목 4 — 종료」 | B-5 | **$0** (실측 완료) |
| ~~5~~ | 🔴 **선행 조건에서 제외 (2026-09-19) — 이관 *후* 작업이다.** ~~실 AI·메일·채점 경로 검증(스텁 해제)~~. **📌 D-008(`✅유효`)과 정면 충돌**: *"`ANTHROPIC_API_KEY` 를 학습 클러스터에 넣지 않는다"*. 클러스터는 하나이고 선행 조건은 이관 **전**에 태워야 하므로 그 클러스터는 학습용이다 → 원문 실행 = **D-008 반전**. 우회로(*별도 예산 키*)는 D-008 의 「기각한 대안」에 **이미 닫혀 있다**. 🔑 **D-008 의 근거는 이관 전에는 해소 불가, 이관 후에는 적용되지 않는다** — 따라서 선행 조건의 자격이 없다. ④ 5개 질문 답: 메일 ✅안 켠다(`MAIL_ENABLED:false`) · egress ✅제약없음 · ESO→env ✅2a 가 증명 · 남은 후보는 ③타임아웃 차이뿐인데 재려면 과금이다. 남는 것은 **매니페스트 배선**(ExternalSecret 1 + `envFrom` 1줄 + 스텁 플래그 제거) — 이관 계획서에서 다룬다(B-6·B-11·B-16 과 같은 취급). 상세: 「항목 5 — 착수 설계」⑥ | B-4 → **이관 계획서로 이관** | **$0** |
| **6a** | 🔴 **쪼갰다 (2026-09-21, Blindspot Pass)** — 토폴로지 **무관** $0 분. ~~①L-44 수정~~ → 🔴 **철회**: L-44 는 `obsolete`(09-04)이고 SOP 가 이미 다른 처방(*main 에서 `workflow_dispatch` 재빌드*)을 골라뒀다 — **틀린 게 아니라 불필요**. ~~②치환 체인 일원화(문서 **3곳**, **스크립트 1개**)~~ → ✅ **완료 (2026-09-22), 단 전제가 둘 다 틀렸다**: **3곳 아니라 2곳**(`eks-session-sop.md` 의 `PLACEHOLDER` **0건**)이고, *"어긋날 수 있다"* 가 아니라 **`k8s/README.md:80` 에서 이미 어긋나 있었다**(#418 이 플레이스홀더를 넣으며 문서를 안 고침). **스크립트는 만들지 않았다** — §3 의 kustomize 기각 근거(*"절차가 눈에 보이는 편"*)가 그대로 적용된다. 대신 기존 `verify` 마커 사용(신설 0줄). ③~~CI 역할 access entry~~ → 🔴 **하지 않는다**: 신뢰정책이 `pull_request` 를 포함해 **PR 트리거로 클러스터 admin 이 열린다**. 지금 entry 가 1개인 것이 사실상의 방어선 | B-12 (일부) | **$0** |
| ~~6b~~ | 🔴 **이관 후로 이동 (2026-09-21)** — ~~ArgoCD gitops 레이어~~. **하드 블로커 2개**: Ⓐ `design-integrity` 의 **verify 앵커 2개가 렌더 매니페스트 커밋을 기계적으로 실패**시킨다 (퍼블릭 레포 제약이 CI 로 구현돼 있다 — 치환값 6종 중 5종이 계정 ID·ARN). Ⓑ **ArgoCD selfHeal + SOP §8 + 리퍼 = 고아 ALB 확정 경로**($16.43/월 + IP $7.30, SG `DependencyViolation` 으로 destroy 실패까지). 🔴 **순서도 뒤집힌다** — 상주형 CD 는 항목 7(B-18)의 결론을 선취하고 📌 **D-002(`✅유효`)** 를 건드린다. 🔑 Ⓑ·순서·D-002 는 **전부 destroy-after-use 한 뿌리**라 이관 후 저절로 풀린다(항목 5 와 같은 모양). **Ⓐ만 이관 후에도 남으므로 해법을 먼저 정할 것.** ✅ **용량은 $0 로 쟀다**: `helm template` → 파드 **7** (슬롯 18칸 여유라 들어감) · LoadBalancer·PVC·Ingress **0개**(추가 과금 없음) · 🔴 **`memory:` 선언 0건 = requests 0Mi** → ESO 와 같은 *"스케줄러 예산 0"* 함정. 상세: 「항목 6 — 착수 설계」 | B-12(잔여) · **B-13 → 이관 계획서** | **이관 후** |
| ~~7~~ | 🔴 **선행 조건에서 제외 (2026-09-22) — 이관 계획서로 옮긴다.** ~~상시 운영 전제로 안전장치 개정(리퍼·SOP·이상탐지 임계)~~. **닫는 항목 3개가 전부 *"이관 전에 답이 안 나온다"***: **B-14 는 오늘 위반 0건**이고 그 3분의 1(*05:23 KST 라 상시 가동 전제*)은 **애초에 고장이 아니다** — 항목 7 이 그 전제를 참으로 만든다(자기참조). **B-1·B-18 은 이관 후 토폴로지에 의존**하는데 그 결정은 레포에 **없고**, 현재 IaC 로는 2클러스터가 **불가능**하다(`2-cluster/backend.tf` 의 state key 가 리터럴이라 보간 불가). 🔴 **B-18 의 숫자도 틀렸다**: *"tfsec 예외 4건"* → 실측 디렉티브 **25건**이고, 반대로 **코드가 스스로 *"prod 이관 시 뒤집힌다"* 로 표시해둔 것이 12건** — 과소보고 3배. 인용한 원장 **L-49 도 틀렸다**(*"규칙이 아직 없다"* 라 뒤집힐 게 없다) — 항목 7 을 명시 호명한 것은 **L-50** 이다. 🔴 **하네스 동결 규칙상 적법한 착수 경로가 없다**(해제 조건 ①②  둘 다 불성립). 🔑 **계획에 없는 필수 작업 13건**(ACM 갱신·예산 20단계·`guard-local-layers` 하드코딩·마커 자가치유 소멸·하트비트의 실제 정의 등)을 ⑥ 표에 수확해뒀다. 상세: 「항목 7 — 착수 설계」 | B-1 · B-14 · B-18 → **전부 이관 계획서로** | **이관 후** |
| **0** | 🔴 **신설 (2026-09-22) — 이관 계획서의 0번 항목.** **월 $140 실지출을 시작할 것인가.** 크레딧 잔여 ≈$194 / 상시 $140월 → **약 1.4개월 뒤 소진 = 계정 폐쇄**, 플랜 만료는 **2027-01-15**(3.8개월). ⚠️ *성립 불가*가 아니다 — 잔여 크레딧은 Paid 로 **이월**된다. 정확한 서술은 ***"이관은 무료 크레딧으로 굴리는 게 아니라 월 $140 실지출을 시작하는 것"*** 이다. 🔴 그리고 이관은 `docs/eks-cost-model.md` 의 *"prod 는 Fly+Neon 이라 계정 폐쇄돼도 무영향이다"* 를 **직접 거짓으로 만든다** — 안전 예비 $30 규칙의 근거 문장이다. 이 계획서 「시한 정정」 절이 *"이 계획은 그 전제를 깨면서 그 전제 위에 세워진 예산 상한을 그대로 쓴다"* 라고 **이미 인정**해뒀는데 항목으로 세우지 않았다. 상세: 「항목 7 — 착수 설계」⑦ | (신규) | **결정** |

> 🔑 **1번을 먼저 하는 이유**: 백업이 없으면 DB 이전을 **시작할 수 없다**. 그리고 관리형이
> 조용히 해주던 일을 직접 해보는 것이 이 트랙에서 가장 값진 운영 경험이다
> (Stage 3b가 *"관리형이 공짜로 주던 것에 TLS가 있었다"* 를 가르친 것과 같은 종류).
>
> ⚠️ **B-6(스케줄러 중복)·B-11(단일 AZ·다운타임)·B-16(Fly 비용 실측)은 이 목록에 없다.**
> 전환 **시점**에 풀어야 하는 것이고, 선행 조건을 다 채운 뒤 이관 계획서를 쓸 때 다룬다.
> 지금 설계하면 그때 조건이 바뀌어 다시 쓴다.
>
> 🔴 **2026-09-19 — B-4 도 여기로 합류했다** (위 5번 행). 처음부터 이 셋과 같은 성질이었는데
> 선행 조건에 들어가 있었다. 판별 기준은 **"이관 전에 답이 나오는가"** 이고,
> B-4 는 D-008 때문에 **이관 전에는 원리적으로 답이 안 나온다**.
> ~~***선행 조건은 7건에서 6건이 됐다 — 남는 것은 `1 · 2(=2a) · 3 · 4 · 6 · 7` 이다.***~~
>
> 🔴 **2026-09-22 — 항목 7 도 여기로 합류했다** (위 `~~7~~` 행). 같은 판별 기준이다.
> ***선행 조건은 5건이 됐다 — 남는 것은 `1 · 2(=2a) · 3 · 4 · 6a` 이고,
> ~~그중 미완은 `4` 의 잔여(기울기 재측정 + 대응 선택지 결정) **하나뿐**이다.~~***
>
> 🔴 **2026-09-24 — 항목 4 가 닫혔다. 선행 조건 5건이 전부 완료다.**
> ***남은 것은 선행 조건이 아니라 이관 계획서(0번 = 월 $140 실지출 결정)와 블로그 원고다.***
>
> 🔴 **위 문장을 한 번 틀리게 썼다 (같은 커밋 안에서 정정).** 처음에 *"`3` 의 유료분과 `4` 의
> 잔여 둘"* 이라고 적었는데, 항목 3 유료분은 **2026-09-16 유료 세션에서 완료**됐다
> (`docs/eks-migration-log.md` 의 진행표 행 *"Ingress annotation 3개 + 실 HTTPS 확인 +
> 2a 실검증 | ✅ | ~$0.1"*, 그리고 *"✅ 실제 HTTPS 성립 — `--resolve` 로 DNS 없이, `-k` 없이 통과"*).
> 🔑 **근거는 순서표 행이 아니라 일지에 있었다** — 순서표 3 행은 09-15 에 쪼갠 시점의 서술이고
> 완료 표시로 갱신되지 않았다. 순서표를 출처로 읽은 것이 오류의 원인이다.
> 🔑 **네 번 연속(4·5·6·6a)에 이어 다섯 번째로 항목 정의가 틀렸다.** 착수 전 $0 전제 검증이
> 다섯 번 다 값을 냈다 — 이제 이것은 요령이 아니라 **이 계획서의 기본 절차**로 본다.
>
> 🔴 **2026-09-21 — B-13 도 합류했다** (위 6b 행). *"Fly 를 살려두면 롤백 타깃이 계속 움직인다"* 인데,
> **Fly 를 끄는 것이 곧 이관**이다. 같은 기준(*"이관 전에 답이 나오는가"*)에 걸린다.
> ⚠️ **다만 항목 6 은 통째로 빠지지 않는다** — `6a`(토폴로지 무관 $0 분)가 남는다.
> ***6 은 제외가 아니라 축소다.*** 그래서 위 "6건" 은 그대로다.
> (`2b` 는 2026-09-14 재판정으로 이미 해소돼 **항목 2 는 2a 하나로 센다**. 거기에 `5` 가 이번에 빠졌다.)
>
> ⚠️ **첫 서술은 *"6건이 아니라 5건"* 이었고 틀렸다** — 괄호 안에 **6개를 나열해놓고 5건이라 썼다**.
> QA 가 F-1(MEDIUM)로 잡았다. ***같은 문장 안에서 나열과 개수가 어긋났는데 내가 못 봤다 —
> 세어보지 않고 숫자를 썼기 때문이다.*** 이 세션의 반복 주제(`무스텁 포트 15 vs 16`)와 같은 형태다.

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

## 항목 2b — B-15 🔴 **재판정 (2026-09-14): 항목이 해소된다**

위 ⚪별건이 요구한 판정(*"이게 정말 비밀인가"*)을 실제로 했다. **답은 "아니다"** 이고,
그 결과 2b 는 할 일이 하나만 남아 **항목 3 으로 이관**된다.

### 판정 ① `instance-id` 는 시크릿이 아니다 — Basic auth 의 **username**

```kotlin
// be/support/monitoring/.../OtlpMetricsConfig.kt
Base64.getEncoder().encodeToString("$instanceId:$apiKey".toByteArray())
"Authorization" to "Basic $encoded"
```

`instanceId` 가 아이디, **`GRAFANA_API_KEY` 가 비밀번호**다. 아이디만으로는 아무것도 못 한다.

근거 4중 (전부 코드로 확인):

| | 확인 | 명령 |
|---|---|---|
| 값이 레포에 있나 | **실제 키 0건.** 리터럴 대입은 2건인데 둘 다 비시크릿 — `OtlpMetricsConfigTest.kt` 의 `"GRAFANA_API_KEY="`(빈 값, blank 케이스 테스트)와 `secrets.tf` 의 **주석** `# GRAFANA_API_KEY = "learning-placeholder-not-a-real-api-key"`(삭제된 자리표시 기록) | `git grep -n "GRAFANA_API_KEY *= *[\"']" -- ':!*.md'` **기대: 2건** (문서를 빼지 않으면 이 표 자신이 잡혀 3건이 된다) |
| 학습 클러스터가 받나 | GRAFANA 언급이 전부 *"왜 안 넣는가"* 주석 | `grep -n GRAFANA infra/aws-eks/2-cluster/secrets.tf` |
| K8s 매니페스트 | 주입 **0건** (README 설명문만) | `grep -rn GRAFANA k8s/` |
| 조합 방식 | `username:password` 의 앞자리 | 위 코드 |

⚠️ **잔여 위험은 인정한다**: 키가 언젠가 유출되면 ID 를 이미 알고 있어 악용이 즉시 가능하다.
⚪ **미확인 (코드로 검증 불가)**: ID 가 스택 URL 에서 얻어지는지, 회전이 가능한지는 **Grafana Cloud 제품
동작**이라 이 레포에서 확인할 수 없다. 처음엔 *"회전 불가한 계정 식별자"* 라고 단정했으나 근거가 없다
(QA F-2 가 강등을 요구했고 맞다). 확인하려면 Grafana Cloud 콘솔에서 봐야 한다.
🔑 **다만 이 세부가 어느 쪽이든 핵심 판정은 안 흔들린다** — username 은 password 없이 쓸 수 없고,
이 레포는 히스토리 재작성을 금지하므로 값 자체는 **어차피 제거할 수 없다.** 진짜 통제점은
`GRAFANA_API_KEY` 이고 그건 레포 밖에 있다. 🔴 회전이 **가능**한 것으로 밝혀지면 그때 한 번 돌리면 된다 —
그건 이 판정을 뒤집는 게 아니라 값싼 추가 조치다.

### 판정 ② *"학습/prod 메트릭이 섞인다"* 는 **이미 해소된 사고의 현재형 재기술**

```
GrafanaOtlpCredentialsCondition:  apiKey.isNullOrBlank() → return false
학습 클러스터의 apiKey:            없음 (#355, 2026-08-03 에 자리표시 3종 삭제)
→ 학습 클러스터는 OtlpMeterRegistry 를 아예 만들지 않는다
```

혼입은 **08-03 에 끝난 사고**다. B-15 는 09-11 에 그것을 **살아있는 문제로** 다시 적었다.
🔑 이번엔 조건절이 빠진 게 아니라 **해소 사실이 빠졌다** — 원장 **L-53** 의 변종이다.

⚠️ **그리고 같은 실수를 내가 한 번 더 했다.** `.claude/CONTEXT.md` 의 해당 항목은 **처음부터
정확했다** (*"시크릿은 아니고 스택 식별자다. 키가 없으면 push 자체가 안 돌아 당장의 위험은
무해화됐다(#355)"*). 그런데 그것을 읽고 사용자에게 *"퍼블릭 레포 평문 커밋, 미해결로 등재됨"*
이라고 요약했다 — **하위 불릿의 한정을 떨어뜨렸다.** L-53 이 한 세션에 세 번 났고,
세 번 다 *읽은 것을 옮기는 단계*에서 났다.

### 남는 일 하나 → **항목 3 으로 이관**

| 대상 | 변경 | 왜 항목 3 인가 |
|---|---|---|
| `SecurityConfig.kt` 의 `hasIpAddress('fdaa::/16')` 절 | **삭제** (`127.0.0.1`·`::1` 만 남긴다) | `/actuator/**` 노출 범위 이야기이고, 항목 3(HTTPS·ALB·`ssl-redirect`)이 **정확히 같은 표면**을 다룬다. 따로 하면 같은 파일을 두 번 연다 |

🔴 **EKS CIDR 로 "교체"하지 마라.** `hasIpAddress` 는 원격 주소를 보는데 Ingress 경유 소스는
**ALB** 다 → ALB 서브넷을 허용하면 `/actuator/**` 가 **인터넷 전체에 열린다**(Ingress 는 HTTP 80 평문).
파드 CIDR 을 허용하면 반대로 아무것도 안 열린다. **절 삭제만이 "확대가 아님"을 증명할 수 있는 변경.**
Fly 는 `[metrics]` 블록이 없어(파일 전체 확인) 인바운드 스크레이프 경로가 없으므로 안 깨진다.

### 없어진 일

| 원래 | 판정 후 |
|---|---|
| `instance-id` 환경변수화 | ❌ **불필요** — 시크릿이 아니고 혼입도 해소됨. 바꾸면 위험만 생긴다(아래) |
| 🔴 `fly secrets set` 선행 (사용자 실행) | ❌ **불필요.** `fly` 를 쓸 수 있는 환경인지와 무관해졌다 |
| `OtlpMetricsConfigTest` 보강 | ❌ **불필요** — yml 을 안 건드리므로 그 회귀 자체가 없다 |

🔑 **환경변수화를 안 하는 쪽이 더 안전하다**: `application-prod.yml` 을 `${GRAFANA_OTLP_INSTANCE_ID:}`
로 바꾸면 Fly prod 도 **같은 `prod` 프로파일**을 읽으므로(`fly.toml`), 값 주입을 놓치는 순간
`GrafanaOtlpCredentialsCondition` 이 false 가 되어 **`enabled: true` 는 남은 채 레지스트리만 없는**
상태가 된다. 앱은 정상 기동하므로 `/health` 스모크로 **안 잡힌다.**
얻는 것 없이 조용한 실패 경로만 하나 늘린다.

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


---

## 항목 3 — 착수 설계 (2026-09-15)

### 분할

| | 내용 | 파이프라인 | 비용 | 상태 |
|---|---|---|---|---|
| ⓐ | ACM 퍼블릭 인증서 (`0-bootstrap/acm.tf`) | `infra-deploy` → `tofu apply` | $0 | ✅ |
| ⓑ | Cloudflare 수동 CNAME 검증 | — (사람) | $0 | `.claude/TASKS.md` TASK-10 |
| ⓒ | `SecurityConfig` 의 `hasIpAddress('fdaa::/16')` 절 삭제 | `be-cd` → **Fly prod 배포** | $0 | ✅ #424 (prod 반영 확인) |
| ⓓ | Ingress annotation 3개 + 실 HTTPS + 2a 실검증 | 로컬 apply | ~~$0.1~~ → **실제 $2.77~3.14** | ✅ 완료 (2026-09-16). 목표 6/6, **비용 21~24배 초과** — 일지 참조 |

> 🔴 **ⓐ 와 ⓒ 를 한 PR 에 담지 않는다** — 자동 파이프라인 둘이 같은 머지에서 발사되고
> 롤백 경로가 서로 다르다. ⓒ 는 머지 즉시 **prod 에 배포**된다.

### 확정된 설계 (근거 전문은 `infra/aws-eks/0-bootstrap/acm.tf` 헤더)

| 결정 | 선택 | 한 줄 근거 |
|---|---|---|
| 레이어 | `0-bootstrap` | 검증이 수동이라 수명이 클러스터가 아니라 **도메인**에 묶인다 (D-004 5번째 적용 — **레이어만**) |
| `prevent_destroy` | **안 붙임** | SAN 변경이 replacement 강제 → 전환 때 매 머지 CI 실패. 재발급 $0 |
| `for_each` 환경 축 | **안 씀** | 2a 의 전제(`random_password` = $0 state 항목)가 실제 리소스인 ACM 에 전이되지 않는다 |
| `aws_acm_certificate_validation` | **안 씀** | `infra-deploy.yml` auto-apply 를 블로킹해 CI 정지 |
| 호스트명 | `eks.quest.dhbang.co.kr` | prod `api.` 는 Fly 가 서빙 중 |

### 🔴 ⓓ(유료 세션)로 이월된 작업 — Blindspot 이 찾았지만 이번에 안 한 것

> 이 절이 이 항목들의 **유일한 목적지**다. 원장(`review-ledger.md`)은 **QA 지적 전용**이라
> Blindspot 산출물을 받지 않고, 일지(`eks-migration-log.md`)는 시간순 기록이라 **검색되지 않는다.**
> 실제로 아래 **U-8 은 이번 세션에 한 번 유실됐다**(일지에 U-1~U-17 중 U-8 만 빠져 있었다).

| ID | 할 일 | 왜 지금 못 하나 |
|---|---|---|
| **U-8** | ✅ **해소 (2026-09-16, `stage/eks-11-https-ingress`).** 4단계 경로 완성: ①`0-bootstrap` output `acm_certificate_arn`(sensitive) ②**`2-cluster` 중계 output** 신설(sensitive) ③`ingress.yaml` 의 `CERT_ARN_PLACEHOLDER` ④튜토리얼 §4-4 주입 절차 | — |
| **U-13** | ✅ **해소.** `ingress.yaml` ④ 절의 **첫 문장만** 갱신(*"평문이니 로그인·토큰 태우지 마라"* → TLS 붙음). **둘째 문장은 보존** — 학습 클러스터 OAuth 자리표시는 `2-cluster/secrets.tf` 가 강제하는 **살아 있는 제약**이고 TLS 가 그 이유를 해소하지 않는다 | — |
| **U-14** | 🔴 **계획이 두 군데 틀렸다.** ⓐ *"문서 3곳"* → **2곳**(`docs/eks-tutorial-steps.md:2045`, `docs/eks-quizzes/stage-eks-9-alb.md:15`). 제3 후보인 튜토리얼 1896 은 ASCII 흐름도 안 **개념 표기**라 대상 아님. ⓑ 🔴 **퀴즈 파일은 고치면 안 된다** — `stage-eks-9-alb.md` 는 *"브랜치 `stage/eks-9-alb` · HEAD `0f3fcef` · 재료: 이 세션 실측"* 으로 고정된 **트랜스크립트**이고 `:15-19` 는 당시 실제 출력이다. sed 형태로 "갱신"하면 **그때 치지 않은 명령이 실측 기록에 남는다** → **튜토리얼 1곳만 갱신**(✅ 완료) | — |
| **U-15** | ✅ **해소 — 유료 세션 불필요.** #422 CI 실측: `Infra CI / tfsec` **pass**, 변경 파일에 `acm.tf` 포함, `tfsec:ignore` **0건**. **tfsec 은 ACM 리소스에 아무것도 요구하지 않는다** | — |
| **U-17** | ✅ **해소 — 그리고 서술이 절반 틀렸다.** `09-16: k8s-default-devquest-3675af8c03-1059226667` vs `09-11: …-3675af8c03-775497815…` → **앞부분(`k8s-default-<ns>-<ingress>-<해시>`)은 안정적**이고 뒷자리만 바뀐다. 전체 이름이 바뀌므로 **DNS 레코드 고정 불가**라는 결론은 유지되지만, *"세션마다 바뀐다"* 는 부정확하다 | — |
| **U-11** | EKS 에서 `CORS_ALLOWED_ORIGINS` 기본값 `http://localhost:5173` 이 그대로 산다. `--resolve` 검증도 브라우저가 아니라 curl 이라 **여전히 안 잡힌다** | 항목 3 범위 밖 — **prod 전환 계획에서** |

### 🔴 Blindspot Pass 가 ⓓ 착수 전에 뒤집은 것 (2026-09-16)

#### ① DNS 레코드가 없다 — 원안대로면 과금 구간에서 사람을 기다렸다

`eks.quest.dhbang.co.kr` → ALB 의 A/CNAME 이 없고 **만들 코드도 없다**(Route53 리소스 0건,
존은 Cloudflare 수동). ALB DNS 이름은 세션마다 바뀐다. 원안은 apply 직후 사람의 Cloudflare
작업을 요구했고, 그건 **SOP 과금 구간 최상단 규칙과 정면 충돌**이다(09-06, $0.39 손실).

**→ `--resolve` 우회가 성립한다.** 두 조건이 맞아 있다: ⓐ`ingress.yaml` 의 `rules:` 에
**`host:` 가 0건**이라 ALB 규칙에 Host 조건이 없다 ⓑ인증서는 리스너 고정 부착이라 SNI 무관.
`--resolve` 는 DNS 만 건너뛰고 **TLS 검증은 정상 수행**된다. 절차는 튜토리얼 §4-4.
⚠️ 나중에 `host:` 를 추가하면 이 우회가 죽는다.

#### ② `ssl-redirect` 가 09-11 의 노출-차단 증거를 무효화한다

`README.md:143` 의 *"`/actuator/health/readiness` **404** = 인터넷 노출 차단"* 과 튜토리얼
응답코드 표는 **HTTP 로 찍은 값**이다. `ssl-redirect` 가 붙으면 전부 301 이 되어 증거가 사라진다.
→ ①의 `--resolve` 는 **선택적 우회가 아니라 기존 증거를 재현하는 필수 경로**다.
🔴 **`curl -L` 금지는 틀린 경고였다 (09-16 실측).** LBC 의 `ssl-redirect` 는 **Host 헤더를 보존**해
`Location: https://eks.quest.dhbang.co.kr:443/…` 를 낸다 — `-L` 로 따라가도 403 정상.
🔑 이 경고는 Blindspot 의 **⚪ 추측**이었는데 문서로 옮기며 **🔴 로 격상**됐다.
***확도 표시를 함께 옮기지 않으면 추측이 한 번의 복사로 사실이 된다.***

#### ③ 가드를 `-z` 로 짜려던 것이 틀렸다

원안의 근거(*"SOP §2b·B1-7 이 이미 두 번 잡았다"*)는 **둘 다 오인용**이었다. 레포가 실제로
밟은 실패는 빈 값이 아니라 **`Warning: No outputs found` 가 종료코드 0 으로 변수에 담긴 것**
(ESO, 08-12 실측). `-z` 는 통과시킨다. → **`case … arn:aws:acm:*:certificate/*` 형태 검사.**
튜토리얼 자신이 *"비었나(`-z`)보다 그 모양이 맞나가 강하다"* 라고 적어놨고, Stage 4 의
`$ROLE`·`$VPC` 검사가 같은 형태다. ⚠️ 가드가 있는 곳은 **레포 전체에서 1곳**, 무가드 9곳 —
*"관례"* 라고 믿고 복사하면 무가드 쪽을 복사한다.

#### ④ 항목 2a 검증 4종이 전부 동어반복이었다 — 재설계

| 원안 | 판정 |
|---|---|
| `app_secret_name == devquest-eks/learning/app` | `environment` 기본값이 `learning` 이고 2-cluster 에 tfvars **0건** → **기본값의 동어반복** |
| ESO 동기화 / 파드가 `JWT_SECRET` 받는가 | 값이 맞는지를 안 본다 |
| teardown 후 재apply 에서 키 유지 | 키는 `0-bootstrap` 의 `random_password` 라 **2-cluster state 에 없다.** destroy+재apply 13분(≈$0.045)의 반증력 **0** |
| (경계 검증) prod 시크릿 읽어 `AccessDenied` | **prod 시크릿이 존재하지 않는다** → `ResourceNotFound` 가 나고 *"권한 없음"* 과 *"그런 게 없음"* 이 **같은 결과**. 항목 1의 센티넬 문제 재발 |

**재설계 — 두 가설이 다른 결과를 예측하게:**

```
① [$0·완료 2026-09-16]  0-bootstrap 키 지문을 먼저 기록 (값은 어디에도 안 남긴다)
      learning  sha256:99559e943576  (len=64)
      prod      sha256:e0a6dca3c137  (len=64)
② [유료]  파드 안 JWT_SECRET 의 sha256 이 ①의 learning 과 일치하는가
      → 0-bootstrap → Secrets Manager → ESO → K8s Secret → 파드 env 4단계를 한 번에 판정
      → prod 지문과 일치하면 경계 붕괴 = 즉시 중단
③ [$0·10초]  aws iam simulate-principal-policy (ESO 역할)
      learning ARN  → allowed
      prod 형태 ARN → implicitDeny        ← 다른 결과를 예측한다 = 경계 실재 증명
      근거: irsa-eso.tf:93-101 의 resources 가 와일드카드 없는 정확한 ARN 목록
④ teardown 왕복 — 삭제
```

#### ⑤ 기각된 가설 4건 (착수 전에 확인해 작업을 줄였다)

| 가설 | 실제 |
|---|---|
| 헬스체크가 HTTPS 에 영향받는다 | **아니다.** 타겟그룹 헬스체크는 리스너와 독립, `backend-protocol` 기본 HTTP 유지 |
| LBC IAM 에 ACM 권한 추가 필요 | **이미 있다**(`iam-policy-alb-controller.json`). 게다가 파일 sha256 이 `irsa-alb.tf:66` 에 박혀 있어 *"혹시 몰라 추가"* 하면 그 주석이 거짓이 된다 |
| `verify:` 마커가 placeholder 로 깨진다 | **안 깨진다.** 마커는 `target-type: ip`·`healthcheck-path` 를 지킨다(둘 다 보존 확인) |
| teardown·리퍼가 HTTPS 를 다르게 다룬다 | **아니다.** 단 09-11 의 *"delete ingress 16초"* 는 리스너 1개 값이라 기준선이 틀어진다 |

#### ⑥ 세션 중/후 챙길 것

| | |
|---|---|
| **리스너 인덱스** | 튜토리얼이 `Listeners[0]` 하드코딩이었다 → 443 포트 필터로 수정(✅). HTTP 쪽을 잡으면 rules 가 전부 redirect 라 우선순위 표가 안 나온다 |
| **ARN 유출 경로** | `sensitive` 는 `Outputs:` 블록만 가린다. **2-cluster 는 로컬 apply 라 CI 의 `::add-mask::`·`sed -u` 가 없다.** 일지(`eks-migration-log.md`)는 **커밋되는 퍼블릭 문서** → 터미널 출력을 붙여넣을 때 사람이 `<account>` 로 가려야 한다 |
| **ACM `InUseBy`** | 세션 중 ALB ARN 이 들어가는 **새 상태**가 생긴다. SOP §9(0건)도 §9b(원장 일치)도 이 필드를 안 본다. `RenewalEligibility` 가 `INELIGIBLE` → `ELIGIBLE` 로 뒤집히는지 **세션 중/후 두 번** 찍는다($0) |
| **`infra-deploy` 머지 타이밍** | `paths: infra/aws-eks/**` 라 2-cluster output 추가만으로도 트리거되어 0-bootstrap 을 auto-apply 한다. **ALB 가 cert 를 쥔 동안 머지하면** ACM replacement 시도 → `ResourceInUseException`. **머지를 과금 창과 겹치지 않는다** |
| **퀴즈** | 브랜치가 `stage/eks-11-https-ingress` 라 훅이 PR 생성을 차단한다. `docs/eks-quizzes/stage-eks-11-https-ingress.md` + 통과 마커 필요 — 계획서에 없던 항목 |

### 범위 밖 — 별건

| ID | 내용 |
|---|---|
| **U-4** | `0-bootstrap/outputs.tf` 의 `ecr_repository_urls` 가 `sensitive` 없이 `<account>.dkr.ecr...` 를 내보내고, `infra-deploy.yml` 이 `-no-color` apply 로 Outputs 를 **공개 Actions 로그에 찍는다.** 같은 파일 `account_id` 는 `sensitive = true`. **지금 새고 있다** — 별도 판단 대기 |

---

## 항목 5 — 착수 설계 ($0 사전 조사, 2026-09-19)

> **아직 착수하지 않았다.** 항목 4 에서 *"$0 확인을 먼저 하니 항목 정의가 틀려 있었다"* 를 겪었으므로
> 같은 절차를 먼저 밟았다. 이번엔 **B-4 의 서술이 정확했다**(아래 ①).

### ① B-4 재확인 — 정확하다

| B-4 주장 | 실측 (2026-09-19) |
|---|---|
| `grep ANTHROPIC\|RESEND\|JUDGE0\|MAIL_ENABLED k8s/ infra/` → **0건** | ✅ 맞다. 매칭 2건은 `ai-api.yaml` 의 **주석**이고 실제 주입은 없다 |
| 무스텁 AI 포트 수 | 🔴 **16개** (내 첫 집계 15 는 틀렸다 — QA F-1). `AiEvaluatorPort` 를 확장하는 Port **17개** 중 `TechInterviewPort` 하나만 스텁된다. 구현체 디렉토리 `client-ai/.../evaluator/` 도 **17개 파일**로 일치 |
| `${ANTHROPIC_API_KEY:}` 빈 기본값 → 부팅은 성공, 호출 시 런타임 실패 | ✅ |

### ①-b 🔴 세는 방법이 틀렸다 — 이름 기반 grep 의 사각지대

처음엔 **파일명에 `Evaluator` 가 있는 것**을 셌다(`find -name '*Evaluator*.kt'` → 17개,
`BaseAiEvaluator` 빼고 16, 스텁 1 빼고 **15**). QA 가 **16** 이라고 정정했고 맞았다.

```
누락된 것:  JourneyReportGenerator.kt
            ← 클래스명이 "Generator" 라 이름 기반 grep 에 안 걸린다
            BaseAiEvaluator 를 상속하고 JourneyReportPort 를 구현하며
            실제 bossChatClient(Anthropic)를 호출하는 진짜 AI 평가자다
```

**올바른 세는 법 — 이름이 아니라 타입으로 센다:**
```bash
grep -rl ": AiEvaluatorPort\|, AiEvaluatorPort" be/core/core-domain/src/main/kotlin | wc -l   # → 17
# TechInterviewPort 1개만 스텁 → 무스텁 16
```

> 🔑 ***이름 기반 검색은 네이밍 규약을 벗어난 것을 구조적으로 못 본다.*** 그리고 규약을 벗어난
> 항목이야말로 **누락되기 쉬운 것**이다 — 여기서도 정확히 그게 빠졌다.
> 이 세션의 반복 주제(*"이 관측이 무엇을 배제하는가"*)와 같은 형태다: 이름 grep 은
> *"이름이 다른 구현체가 없다"* 를 **전혀 배제하지 못한다.**

⚠️ **별건 — 소스 주석 2곳이 같은 오프바이원을 갖고 있다** (2026-09-19 전수 확인):

```
be/core/ai-api/.../config/AiStubConfig.kt:28           "나머지 17개 AiEvaluatorPort + Judge0Port"   → 16
be/core/ai-api/.../stub/TechInterviewStubEvaluator.kt:15  "다른 17개 AI 포트는 스텁되지 않는다"        → 16
```

🔴 **다음 `be/` 변경에 묶어 고친다 — 이것만으로 PR 을 만들지 않는다.**
`be-cd` 가 `paths: ['be/**']` 로 main push 에 걸려 **Fly prod 를 재배포**한다(워크플로 실측).
***주석 한 줄 때문에 prod 를 재배포하지 않는다.***

> ⚠️ 위 B-4 절이 인용한 *"다른 17개 AI 포트"* 는 **소스를 그대로 옮긴 것**이라 인용으로서는 정확하다.
> 반면 같은 절의 *"스텁이 17개 중 1개만 대체한다"* 는 **맞다** — 전체 17, 스텁 1, 무스텁 16.
> ***세 숫자가 한 화면에 있고 둘은 맞고 하나는 틀리다. 고칠 때 맞는 쪽을 같이 바꾸지 말 것.***

### ② 🟢 메일은 기본적으로 안전하다 — 확인됨

```
application.yml:92   devquest.mail.enabled: ${MAIL_ENABLED:false}
k8s/ 에 MAIL_ENABLED  0건   → 학습 클러스터는 기본값 false
MailService.kt:20,31  "메일 발송 skip (MAIL_ENABLED=false)"
```

🔑 **G-2 의 마스터 스위치가 실제로 작동한다.** 학습 클러스터에서 스케줄러가 돌아도 **메일은 안 나간다.**
→ *"학습 클러스터가 실사용자에게 메일을 보낸다"* 는 시나리오는 **현재 코드에서 발생 불가**다.
⚠️ 단 이 항목을 검증하려면 **일부러 켜야** 하므로, 그 순간 이 보호가 사라진다(아래 ④).

### ③ 🔴 진짜 위험은 AI 키다 — 그리고 비용 추정이 모호하다

`~$0.2` 라는 추정이 **무엇의 비용인지 계획서에 없다.** 둘은 완전히 다르다:

| | |
|---|---|
| **클러스터 비용** | 세션 $0.1624/h × 1~2h ≈ $0.2 — 이게 계획서가 뜻한 것으로 보인다 |
| **AI 호출 비용** | **별도.** 16개 evaluator 를 실제로 태우면 Anthropic 과금이 발생한다. 추정치가 **없다** |

그리고 보안 제약과 충돌한다 — *"prod 자격증명을 학습 클러스터에 넣지 말 것"*. `ANTHROPIC_API_KEY` 는
prod OAuth 와 성격이 다르지만(사용자 세션을 위조하지 못한다) **과금 주체가 같다.**

### ④ 착수 전에 정해야 할 것 (전부 미결정)

| # | 질문 | 왜 먼저 정해야 하나 |
|:-:|---|---|
| 1 | **16개를 다 태우나, 대표 경로만 태우나** | 다 태우면 AI 비용이 추정 불가 구간으로 간다. *"prod 트래픽의 핵심 경로"* 가 무엇인지부터 정의해야 한다 |
| 2 | **키를 어떻게 넣나** | ESO→Secrets Manager 경로는 이미 있다(2a). **넣는 순간 학습 클러스터가 과금 주체가 된다** |
| 3 | 🔴 **메일을 켜나** | 켜면 ②의 보호가 사라진다. **실사용자 주소로 나갈 수 있다** — 켠다면 수신자를 내 주소로 강제하는 장치가 선행돼야 한다 |
| 4 | **Judge0 채점을 태우나** | 외부 API. 무료 티어 한도·레이트리밋 미확인 |
| 5 | **이게 정말 prod 전환의 선행 조건인가** | B-4 의 논지는 *"검증된 것이 핵심 경로가 아니다"* 다. 그런데 **같은 코드가 Fly prod 에서 매일 돌고 있다** — K8s 에서 다시 태워 새로 알 수 있는 것이 *"환경변수 주입이 되는가"* 뿐이라면, 그건 **$0.2 짜리 질문이 아니라 매니페스트 리뷰로 끝난다** |

> 🔑 **5번이 가장 중요하다.** 항목 4 에서 *"선행 조건을 파니 이관 동기로 되돌아왔다"* 를 겪었다.
> 이 항목도 **"K8s 에서만 새로 드러나는 것이 무엇인가"** 를 먼저 답해야 한다.
> 후보: ①ESO→env 주입이 실제로 되는가(2a 에서 `JWT_SECRET` 으로 **이미 증명**했다)
> ②네트워크 egress 가 Anthropic 에 닿는가(`networkpolicy-ai-api.yaml` 이 default-deny 다 — **이건 진짜 새 질문**)
> ③타임아웃·재시도가 클러스터 네트워크에서 다르게 동작하는가

### ⑤ 그래서 다음 세션의 첫 작업은 "착수"가 아니라 **범위 결정**이다

②의 egress 질문은 **$0 로 답할 수 있다** — 그래서 **지금 답했다.**

#### ✅ egress 질문 종결 (2026-09-19, $0)

```
NetworkPolicy 전수 = 1개 (networkpolicy-ai-api.yaml)
  policyTypes:
    - Ingress          ← Egress 가 없다
```

🔴 **K8s NetworkPolicy 는 `policyTypes` 에 나열된 방향만 제한한다.** Egress 가 선언되지 않았으므로
**모든 아웃바운드가 허용**된다. 즉 ai-api 파드는 Anthropic·Judge0·Resend 에 **제약 없이 닿는다.**

> ⚠️ **내가 착수 설계 ⑤에서 *"default-deny 라 이건 진짜 새 질문"* 이라고 쓴 것은 틀렸다.**
> 그 파일의 default-deny 는 **Ingress 방향**이고(C-5 가 검증한 것도 그것이다 — *"ALB ENI 를 거부한다"*),
> egress 와는 무관하다. **방향을 안 보고 "default-deny" 라는 말만 가져왔다.**

**함의: 남는 "K8s 에서만 새로 드러나는 것" 후보가 사실상 없다.**

| 후보 | 상태 |
|---|---|
| ①ESO→env 주입 | **이미 증명됨** — 2a 에서 `JWT_SECRET` 지문 일치로 4단계 체인 확인(#426) |
| ②egress 도달성 | **✅ 위에서 종결** — 제한 없음 |
| ③타임아웃·재시도 차이 | 🟡 이론상 남지만, **그걸 재려면 실패를 유발해야** 하고 그건 AI 과금을 태우는 일이다 |

→ 🔑 **항목 5 는 "과금 세션" 이 아니라 "매니페스트에 키를 배선하는 코드 작업 + 리뷰" 로 축소될 가능성이 크다.**
착수 시 ④의 5개 질문에 먼저 답하고, **그래도 실클러스터가 필요한 이유가 남는지** 확인할 것.
남지 않으면 항목 자체를 **$0 으로 재분류**한다(항목 4 와 같은 결말).

### ⑥ 🔴 결론 — **$0 으로 재분류한다. 항목 5 는 기존 결정과 정면으로 부딪힌다** (2026-09-19)

⑤가 남긴 첫 작업(*"④의 5개 질문에 먼저 답하라"*)을 실행한 결과, **질문 목록에 없던 것**이 나왔다.

#### 충돌

| | |
|---|---|
| 항목 5 의 정의 | *"실 AI·메일·채점 경로 검증 **(스텁 해제)**"* |
| 📌 **D-008** (`plans/…phase02.md:140`) | *"`ANTHROPIC_API_KEY` 를 **학습 클러스터에 넣지 않는다**"* · 상태 **`✅유효`** |

**클러스터는 하나다.** 항목 5 는 *선행* 조건이므로 이관 **전**에 태워야 하고, 그 시점의 클러스터는
학습 클러스터다. → **항목 5 를 원문대로 실행하는 것 = D-008 반전**이며
`design-change-procedure.md` 4단계 대상이다.

> 🔑 기억이 아니라 **결정 블록을 직접 열어 확인**했다(CLAUDE.md 빨간 깃발 *"전에 이렇게 하기로 했었지"*).
> 상태 칸이 `✅유효`다.

#### 우회로가 이미 기각돼 있다

*"prod 키 말고 별도 예산 키를 세션 동안만 주입"* 은 D-008 의 **「기각한 대안」에 그대로 있다** —
원칙의 취지는 지키지만 *"사용자가 키 발급·한도 설정을 직접 해야 하고, 실습마다 주입/삭제 절차가 늘어
**끄는 걸 잊는 것**의 표면을 넓힌다"*. **새 우회로를 찾아낸 것이 아니라, 이미 닫힌 문이었다.**

#### ④ 5개 질문 최종 답

| # | 질문 | 답 |
|:-:|---|---|
| 1 | 16개 다 태우나 | **모호해진다** — 5번에 종속 |
| 2 | 키를 어떻게 넣나 | 🔴 **넣지 않는다.** D-008 + `secrets.tf` ⑨ 원칙. `ai-api.yaml:57` 이 코드로 못박음 |
| 3 | 메일 켜나 | ✅ **켜지 않는다.** `MAIL_ENABLED:false` 기본 · 우회 경로 0 (②) |
| 4 | Judge0 태우나 | **아니다.** `Judge0Port` 는 스텁 대상이 아니라 2번과 같은 문제를 그대로 갖는다 |
| 5 | 정말 선행 조건인가 | 🔴 **아니다 — 이관 *후* 작업이다** (아래) |

#### 5번 — **이관 후 작업**이다

D-008 의 근거는 *"**학습** 클러스터에 prod 크리덴셜을 넣지 않는다"* 다. 이 근거는

- **이관 전**에는 해소될 수 없고 (클러스터가 학습용이므로),
- **이관 후**에는 애초에 적용되지 않는다 (그 클러스터가 prod 다).

***즉 이 검증은 "선행 조건"의 자격을 잃는다. 이관 직후 첫 작업으로 옮긴다.***
항목 4 와 같은 결말이되 이유가 다르다 — 4는 *항목 이름이 틀렸고*, 5는 *항목 순서가 틀렸다*.

#### 남는 것 = 매니페스트 배선 (코드 작업, $0)

```
k8s/base/ai-api.yaml    envFrom 없음 (의도적 — ai-api 는 DB 도 JWT 도 안 쓴다)
                        env 2개: SPRING_PROFILES_ACTIVE, DEVQUEST_AI_STUB_..._ENABLED
k8s/eso/                ExternalSecret 4종 — AI 키를 담는 것 0개
```
→ 이관 시 필요한 것은 **ExternalSecret 1개 + `envFrom` 1줄 + 스텁 플래그 제거**.
이관 계획서를 쓸 때 다룬다(B-6·B-11·B-16 과 같은 취급).

#### 🔴 이관 후로 넘길 때 반드시 함께 옮길 것

| | |
|---|---|
| **AI 호출 비용 추정치가 없다** | ③에서 지적한 그대로 미해결. 16개 evaluator 실호출 비용은 **한 번도 추정된 적이 없다**. 이관 직후 작업의 **첫 단계는 추정**이다 |
| **런타임 실패라 진단이 늦다** | `${ANTHROPIC_API_KEY:}` 빈 기본값 → 부팅 성공, **호출 시점에 실패**. CrashLoop 가 아니다 (D-008 이 *"가장 비싼 실수 형태"* 로 지목) |
| **스텁 플래그를 지우는 것을 잊으면** | `[STUB]` 응답이 **prod 에서** 나간다. 다만 기본값이 off 라 *"잊으면 안 돈다"* 가 아니라 *"명시적으로 켠 것을 지워야 한다"* — 매니페스트에서 `DEVQUEST_AI_STUB_TECH_INTERVIEW_ENABLED` **삭제**가 이관 체크리스트 항목이다 |
| **③타임아웃·재시도 차이** | 🟡 유일하게 살아남은 "K8s 에서만 드러나는 것" 후보. 재려면 실패를 유발해야 하고 = 과금. **이관 후 실트래픽으로 자연 관측**하는 것이 맞다 |

#### ⚠️ `AiStubConfig.kt:30` 주석 오프바이원 — **이 PR 에 담지 않는다**

①-b 의 별건(*"나머지 **17개** `AiEvaluatorPort`"* → **16**)은 한 단어 수정이지만 `be/**` 다.
**`be-cd` 가 `paths: ['be/**']` 로 main push 에 걸려 Fly prod 를 재배포한다**(워크플로 실측).
***주석 한 줄을 고치려고 prod 를 재배포하지 않는다.*** 다음 `be/` 변경에 묶는다.

---

## 항목 6 — 착수 설계 ($0 사전 조사 + Blindspot Pass, 2026-09-21)

> **결론 먼저: 항목 6 을 쪼갠다.** `6a` = 토폴로지 무관 $0 분 / `6b` = **ArgoCD gitops 레이어 → 이관 후**.
> 항목 3 이 $0분/유료분으로 쪼개진 것과 같은 처리이고, `6b` 가 밀리는 이유는 항목 5 와 같다.

### ⓪ 절차 오류 기록 — **토폴로지를 Blindspot Pass 전에 물었다**

오케스트레이터 절차는 Blindspot Pass 가 **3.5단계, 설계 확정 앞**이다. 그런데 이번엔
*"ArgoCD 냐 Actions 냐"* 를 **먼저 사용자에게 물어 결정을 받고**, 그 뒤에 Blindspot 을 돌렸다.
**Blindspot 결과가 그 결정을 뒤집어서 같은 질문을 두 번 하게 됐다.**

🔑 ***설계 질문은 Blindspot 뒤에 해야 한다 — 앞에 하면 사용자가 정보 없이 고르게 된다.***
이 항목은 절차 자체의 교훈이라 여기 남긴다.

### ① 🔴 ArgoCD 가 **B-12 를 닫지 못한다** — 단, 그 근거 절반은 **내가 틀렸다**

```
B-12 의 인용 증거 = ecr-push.yml:12-37 (트리거가 workflow_dispatch + pull_request 뿐)
                  + 원장 L-44        ← 🔴 이 인용이 낡았다 (아래)
```

**트리거 쪽은 유효하다** — ArgoCD 는 배포 "방식"만 바꾸므로 `main push` 트리거 부재는 그대로 남는다.

#### 🔴 정정 — L-44 는 `deferred` 가 아니라 **`⚪ obsolete`(2026-09-04)** 다

내가 이 조사 내내 *"L-44 가 항목 6 의 토대를 무너뜨린다"* 고 보고했는데 **틀렸다.**

```
원장에서 grep → 행 본문에 "🔵 deferred — 하네스 동결 규칙" 문자열이 보인다
실제로는      → 그 행은 `## 처리 완료 (closed / wontfix / obsolete)` 절에 있고
                끝에 "⚪ obsolete — 2026-09-04" 가 덧붙어 있다
```

🔑 ***행을 읽고 절을 안 읽었다.*** `deferred` 는 그 행의 **과거 상태**이고 지금 상태는 뒤쪽에 있다.
**원장은 상태를 덧붙이는 append 형식이라, 행 안에 상태 단어가 여러 개 산다** — grep 한 줄로는
최신 상태를 알 수 없다. 이 세션에서 같은 형태의 오류가 네 번째다(`15 vs 16` · `459 vs 576` · `5 vs 6` · 이번).

**obsolete 사유 2개** (원장 원문):
1. **중복 등재였다** — `docs/eks-session-sop.md:92-104` 가 PR #355 실측(`6f6c0932…` 부모 = main + 브랜치)까지
   붙여 이미 문서화했다. *"발견을 등재하기 전에 이미 있는지 검색하지 않았다"*.
2. `latest` 서비스 간 불일치는 **09-04 재빌드로 세 서비스 전부 `14cb335`** 가 되며 소멸했다.

#### 🔴 그리고 SOP 가 이미 **다른 처방**을 골라뒀다

`eks-session-sop.md:96-104`:

> *"🔴가 뜨면 **main 에서 `ECR Push` 를 `workflow_dispatch` 로 한 번 굽는다.** 태그가 main 커밋이 되어
> ✅로 바뀌고, 그 자체가 **'배포된 이미지 = main 의 상태'** 를 보장한다."*
>
> *"⚠️ `git fetch origin '+refs/pull/*/merge:...'` 로 머지 커밋까지 끌어와 판정을 통과시키는 우회는
> **일부러 택하지 않았다.** 판정은 통과하지만 **추적성은 그대로 없고**, 머지된 PR 의 merge ref 는
> GitHub 이 정리할 수 있어 조용한 실패 경로가 하나 더 생긴다."*

⚖️ **내가 제안했던 `github.event.pull_request.head.sha` 는 위에서 기각된 그 우회가 *아니다*** —
merge ref 를 끌어오는 것과 브랜치 head 를 태그로 쓰는 것은 다르고, 후자는 **실재하는 커밋**이라
추적성이 진짜로 생긴다. 그러나 **SOP 의 처방이 더 강한 성질을 산다**: 브랜치 head 는 추적은 되지만
*main 의 상태가 아니다*. SOP 는 *"배포된 이미지 = main 의 상태"* 를 원한다.

→ ***그래서 이 수정은 틀린 게 아니라 **불필요**하다.*** 6a 에서 뺀다.

#### 🔴 가장 아픈 부분 — **Blindspot 에 내 오류를 전제로 주입했다**

Blindspot 프롬프트에 `## 이미 확인한 것 (다시 조사하지 말 것)` 절을 두고 거기에
*"원장 L-44(PR 빌드 sha 가 레포에 없는 커밋)"* 를 넣었다. 에이전트는 **지시대로 재조사하지 않았고**,
그 전제 위에 U-1·U-2 를 세웠다.

> 🔑 ***조사 에이전트에 전제를 주입하면 그 전제만은 검증되지 않는다.*** 위임의 가치는 독립 검증인데,
> *"다시 조사하지 말 것"* 이 정확히 그것을 껐다. **앞으로 그 절에는 "내가 코드로 직접 확인한 것"만
> 넣고, 남의 기록(원장·문서)을 요약한 것은 넣지 않는다** — 그건 검증 대상이지 전제가 아니다.

#### 그래서 B-12 자체도 손봐야 한다

`## 🔴 계획이 성립하지 않는 6건` 의 B-12 행이 **obsolete 된 L-44 를 살아 있는 근거로 인용**하고 있다.
이번에 정정했다.

### ② 🔴🔴 하드 블로커 A — `design-integrity` CI 가 렌더 매니페스트 커밋을 **기계적으로 실패**시킨다

```
check-design-integrity.sh 의 verify 앵커 (내용 단언형):
  infra/aws-eks/README.md:153      verify: k8s/base/ingress.yaml ~ io/certificate-arn:[[:space:]]*CERT_ARN_PLACEHOLDER
  docs/eks-tutorial-steps.md:952   verify: k8s/eso/externalsecret-app.yaml ~ ^        key: APP_SECRET_NAME_PLACEHOLDER

design-integrity.yml:12-13   경로 필터 없음 = 모든 PR 에서 돈다
```

> 🔴 **위 두 줄에서 `<!-- -->` 를 일부러 뗐다 (2026-09-22 정정).** #431 에서 이 두 마커를
> **코드펜스 안에 그대로 인용**했는데, `check-design-integrity.sh` 는 **원문 grep** 이라
> ***인용이 곧 live 단언이 됐다.*** 실제로 이 계획서가 `:1015` 에서 단언을 하고 있었다
> (09-22 에 반증 주입으로 발견 — 일부러 플레이스홀더를 지우자 **계획서 줄이 에러에 같이 찍혔다**).
>
> 지금 당장 해롭진 않았다(참인 단언의 중복). 그러나 **실제 앵커를 정당하게 바꿀 때
> "설명문 안의 인용"까지 찾아 고쳐야 한다** — 문서가 자기도 모르게 가드가 된 것이다.
> 🔑 ***마커를 인용하면 마커가 된다.*** 설명 목적이면 `<!-- -->` 를 떼고 쓴다.

**퍼블릭 레포 제약이 문서 규칙이 아니라 CI 로 구현돼 있다.** 치환값 6종 중 **5종이 "커밋 금지" 사유로 존재**한다:

| PLACEHOLDER | 실제 값 | 금지 근거 |
|---|---|---|
| `IMAGE_` ×3 | `<account>.dkr.ecr…` | `k8s/README.md:124,141` |
| `CERT_ARN_` | `arn:aws:acm:<region>:<account>:certificate/…` | `ingress.yaml:70-73` — ***"선택이 아니라 강제다"*** |
| `EBS_VOLUME_ID_` · `PERSISTENT_AZ_` | `vol-…` | `k8s/README.md:124` |
| `RDS_MASTER_SECRET_` ×2 · `APP_SECRET_NAME_` | 시크릿 이름/ARN | `externalsecret-db.yaml:15-20`, `externalsecret-app.yaml:49` |

🔴 **결정적 증거**: `ecr-push.yml:123,127` 은 이미지 URI 에서 계정 ID 를 **치환해 지우고**
*"`<account>` 자리의 실제 값은 로컬에서 얻으세요 — 퍼블릭 레포라 여기 찍지 않습니다"* 를 출력한다.
***같은 워크플로가 로그에 찍는 것조차 거부하는 값을, 이 계획은 git 에 커밋하려 했다.***

**탈출구도 이미 닫혀 있다** — kustomize 는 `k8s/README.md:145` 가 기각(*"생 yaml + 치환으로 일관"*).
남는 것은 ArgoCD CMP 로 **클러스터 안에서 sed** = *"git 이 단일 진실"* 의 포기다.
또는 **프라이빗 렌더 레포** = *"이 레포만 보고 재현"*(`README:255`, 블로그 원고 전제)의 포기.

### ③ 🔴🔴 하드 블로커 B — selfHeal + SOP §8 + 리퍼 = **고아 ALB 확정 경로**

```
SOP §8 ②   kubectl delete ingress --all -A --timeout=180s
              ↓ ArgoCD selfHeal 이 Ingress 를 되살린다
              ↓ LBC 가 ALB 를 새로 만든다
           tofu destroy
              ↓
           고아 ALB $16.43/월 + 퍼블릭 IP $7.30/월  +  SG DependencyViolation 으로 destroy 실패
```

- SOP 는 *"AWS 에 직접 물어 0 이 아니면 **여기서 멈춘다**"* 로 사람이 막게 돼 있으나 **0 이 될 수가 없다.**
- 리퍼 `cleanup_k8s_loadbalancers()` 는 `delete ingress` 를 **한 번만** 치고 실패해도 destroy 를 진행한다.
- `warn_orphan_albs()` 는 스스로 **감지 전용·삭제 불가**라고 적어뒀다(*"리퍼가 할 수 있는 일은 tofu destroy 하나뿐"*).
- 🔑 **리퍼가 도는 상황 = 사람이 없는 상황**이다. 아무도 안 지운다.

> SOP 가 ESO 에 대해 적어둔 *"`delete secret` 만 하면 8초 만에 부활한다"* 의 **한 층 위**다.
> 착수하려면 **teardown 1단계에 `argocd` Application/네임스페이스 삭제**를 넣고 **리퍼에도 같은 단계**를 넣어야 한다. 지금 코드엔 없다.

### ④ 🔴 순서가 뒤집혀 있다 — 6 이 7 에 의존한다

```
계획서 순서:  6 CD 파이프라인  →  7 상시 운영 전제로 안전장치 개정 (B-1·B-14·B-18)
B-18       =  "상시 전환은 학습 전제 위에 세운 통제 전체를 무근거로 만든다"
```

**ArgoCD 상주형 CD 는 항목 7 의 결론을 선취한다.** 그리고 상주는 📌 **D-002(`✅유효`)** 를 건드리는데,
그 블록이 *"이 결정이 뒤집히면 destroy-after-use 규율 전체가 무너진다 — 리퍼·SOP·`guard-local-layers` 가
전부 이 결정의 파생물"* + *"뒤집으려면 숫자를 **다시 실측**하고 `design-change-procedure.md` 전 단계를 밟을 것"*
이라고 못박고 있다.

🔑 ***블로커 ③·순서·D-002 가 전부 destroy-after-use 한 뿌리에서 나온다.*** 이관 후엔 클러스터가
상주하므로 **셋 다 저절로 풀린다** — 항목 5 와 정확히 같은 모양이다.

### ⑤ ✅ 용량 미지수를 $0 로 닫았다 — **helm 렌더 실측 (2026-09-21)**

Blindspot 이 *"ArgoCD 의 메모리·파드 수가 레포에 0건"* 이라고 정확히 지적했다. 클러스터 없이 쟀다:

```bash
helm repo add argo https://argoproj.github.io/argo-helm && helm repo update argo
helm template argocd argo/argo-cd --namespace argocd > /tmp/argocd-rendered.yaml
```

| 항목 | 실측 | 판정 |
|---|---|---|
| 차트 / 앱 버전 | `argo-cd 10.9.2` / `v3.5.3` | — |
| 워크로드 | Deployment **6** + StatefulSet **1** (`replicas: 1` ×7) | **파드 7개** |
| `Service type` | `ClusterIP` ×2 — **LoadBalancer 0개** | ✅ **ALB/NLB 추가 과금 없음** |
| `kind: Ingress` | **0** | ✅ |
| `kind: PersistentVolumeClaim` / `volumeClaimTemplates` | **0** | ✅ `cost-model.md:108` 서술과 일치 |
| CRD | 3 | — |
| **컨테이너 `resources`** | 🔴 **`memory:` 0건 · `cpu:` 0건** — 차트가 **컨테이너 requests/limits 를 설정하지 않는다** | **아래** |

> ⚠️ **문구를 한정한 이유 (QA F-4)**: 처음엔 *"`resources` 를 **전혀** 설정하지 않는다"* 로 썼는데,
> 렌더 결과에 `resources:` **문자열 자체는 51건** 있다 — 대부분 CRD 스키마의 `subresources` 와 RBAC 규칙의
> `resources:` 다. **컨테이너의 requests/limits 와 다른 것**이다. 무한정 문구를 그대로 뒀으면
> 나중에 `grep -c 'resources:'` 로 독립 검증하는 사람이 **51 vs 0 을 모순으로 읽는다.**
> 🔑 ***주장의 범위를 검사의 범위에 맞춘다*** — 이 레포의 반복 실패(*"검사가 주장보다 헐겁다"*)의 역방향 교정이다.
>
> **재현**(클러스터 불요):
> ```bash
> helm repo add argo https://argoproj.github.io/argo-helm && helm repo update argo
> helm template argocd argo/argo-cd --namespace argocd > /tmp/a.yaml
> grep -c 'memory:' /tmp/a.yaml          # → 0
> # 반증 주입 — grep 이 실제로 탐지하는지 확인 (이 단계를 건너뛰지 마라)
> helm template argocd argo/argo-cd --set controller.resources.requests.memory=256Mi | grep -c 'memory:'   # → 1
> ```

#### 🔴 `requests` 가 0 인 것은 좋은 소식이 아니다 — **ESO 와 같은 함정**

`migration-log.md:2397` 이 이미 적어뒀다 — *"ESO 3파드가 `requests: {}` 라 **스케줄러 예산 0**, 슬롯만 3칸"*.
ArgoCD 7파드가 **정확히 같은 성질**이다.

```
슬롯   3대 × 11 = 33칸,  현재 15칸 사용 → 18칸 여유.  ArgoCD 7칸 → ✅ 들어간다
메모리 requests 0Mi → 스케줄러가 막지 않는다
       그러나 실사용 RAM 은 그대로 먹는다 → 노드당 가용 959Mi / 가장 큰 연속 블록 447Mi 를 잠식
```

🔑 ***스케줄러가 통과시키는 것과 노드가 버티는 것은 다르다.*** 이 레포의 반복 실패 형태
(*"검사가 주장보다 헐겁다"*)가 여기서도 그대로다 — **`requests` 는 "얼마나 쓰는가"의 대리 지표일 뿐이고,
ArgoCD 는 그 대리 지표를 0 으로 신고한다.**

⚠️ **실사용량은 $0 로 못 잰다** — 띄워봐야 안다. 숫자를 만들지 않는다.
`6b` 착수 시 **첫 단계는 `resources` 를 명시적으로 박는 것**이다(차트 기본값을 그대로 쓰지 말 것).

> ⚠️ 참고 — `t4g.medium` 으로 도망갈 수 없다: **이 계정에서 launch 되지 않는다**(D-010, 신 Free Tier).
> `addons.tf:97` 의 *"medium = 파드 상한 17"* 은 **취소선으로 무효 처리**돼 있다. 인용하면 D-009 잔재다.
> 그리고 3대로 올리면 `coredns replicaCount=1` 규율이 풀려 ~70~100Mi(🟡 미실측)가 더 나간다(`variables.tf:125-131`).

### ⑥ gitops 레이어의 현재 상태 — **기각된 적은 없지만 "선택"이고, 디렉토리는 없다**

| | |
|---|---|
| 설계 존재 | `README:111`(레이어 표) · `:113`(두 평면 분리) · `:176`(디렉토리) · `:252`(착수 순서 5번) |
| 🔴 **디렉토리 부재** | `ls infra/aws-eks/` → `0-bootstrap 1-network 2-cluster PERSISTENT-RESOURCES.md README.md reaper scripts`. **`gitops/` 는 없다.** `README:176` 은 계획을 **현재형으로 적어둔 것** |
| 강등 상태 | Stage 5 = **`(선택)`** (`README:140,154`). `CONTEXT.md` 도 이미 경고해뒀다 |
| 🔴 **조건 위반** | `infra-deploy.yml:35` — *"**gitops 등 상주형($0 유지) 레이어만** 생기면 추가"*. ArgoCD 를 설치하는 gitops 레이어는 **$0 상주가 아니다**(세션 클러스터 의존) |
| 🔴 **guard 사각지대** | `infra-ci.yml:55` 의 로컬전용 목록이 `for local_layer in 2-cluster` **하드코딩**이다. `gitops` 를 매트릭스에 넣어도 **guard 는 통과시킨다** |
| 🔴 **레포 관례 충돌** | `irsa-alb.tf:9-11` — *"이 레이어의 provider 는 aws·tls·random 뿐 … 서드파티는 CLI helm"*. `README:113` 의 *"terraform kubernetes provider 로 워크로드 안 넣음"* 과 합치면 **ArgoCD 를 tofu 로 설치하는 레이어는 두 규칙 모두와 부딪힌다.** 남는 경로는 세션마다 손으로 `helm install` |

🔑 마지막 항목의 귀결: **세션마다 손으로 설치하면 reconcile 루프가 세션 길이(실측 20~100분)만 존재한다.**
남는 이득은 *"선언적 매니페스트 적용"* 인데 그건 `kubectl apply -f` 가 이미 한다.
그리고 그 설치·동기화 디버깅이 **전부 과금 구간**에서 일어난다 — SOP 가 *"이 구간에서 질문하지 않는다"* 로 봉인한 구간이다.

### ⑦ 그 외 Blindspot 발견 (6b 착수 시 반드시 다시 볼 것)

| # | 불일치 | 근거 | 확도 |
|:-:|---|---|:-:|
| U-3 | **`ecr-push` 에 main push 트리거가 없는 것은 사유가 명시된 판단이다** — *"EKS 는 destroy-after-use 라 클러스터가 없는 동안 자동 푸시는 낭비"*. CD 자동화는 이걸 뒤집어야 하는데 계획서에 언급이 없었다 | `ecr-push.yml:13` | 🔴 |
| U-4 | **매니페스트 커밋 잡은 권한·가드를 모두 건드린다.** `ecr-push.yml:52` 는 `contents: read`. 커밋하려면 `write` + main push 가 필요한데 레포는 main 직접 push 를 훅으로 차단해왔다. 봇 커밋은 PR 리뷰 게이트 **밖으로 나간다**. 게다가 main push 는 `infra-deploy`·`design-integrity` 를 재발사한다 | `ecr-push.yml:52`, `assert-no-main-push.sh`, `infra-deploy.yml:10-13` | 🟡 |
| U-5 | 🔴 **CI 역할에 EKS access entry 가 없다.** `access.tf` 의 유일한 entry 는 `data.aws_caller_identity.current.arn`(로컬 apply 수행자)이고 `cluster.tf:23 authentication_mode = "API"` 다 → IAM `AdministratorAccess` 는 **kubectl 권한을 주지 않는다**. ⚠️ **그런데 entry 를 추가하면 신뢰정책이 `pull_request` 를 포함하므로 PR 트리거로 클러스터 admin 이 열린다.** 지금 entry 가 1개로 좁혀져 있는 것이 **사실상의 방어선**이다 — 넓히기 전에 신뢰정책부터 좁혀야 한다 | `2-cluster/access.tf`, `2-cluster/cluster.tf:23`, **`0-bootstrap/`**`iam-github-oidc.tf:37-39,50` ⚠️ 앞 둘과 **레이어가 다르다** | 🔴 / 🟡 |
| U-6 | **tfsec 예외 4건 + `cluster.tf:6-9`(퍼블릭 엔드포인트 `0.0.0.0/0`)의 근거가 *"세션마다 폐기되는 학습 자산"*이다.** ArgoCD 상주 = **상시 노출되는 CD 컨트롤 플레인**. B-18 이 가리키는 무근거화가 항목 6 단계에서 발생한다 | `cluster.tf:6-9`, B-18 | 🟡 |
| U-8 | **비용 산정 `$0~0.1` 이 이 트랙의 실적과 안 맞는다.** 항목 3 도 $0.13 예상 → **$2.77~3.14 실측(21~24배)**. ArgoCD 는 설치·동기화·teardown 확인이 **전부 과금 구간**이고 ③의 고아 ALB 리스크가 붙는다 | `README:153`, #426 | 🟡 |
| U-10 | **`prod-smoke-daily.yml` 3중 고장(B-14)이 CD 의 자연스러운 후속인데 방치돼 있다** — Vercel 을 때려 Fly/EKS 구분 불가 · 실패 안내가 `fly status` 하드코딩 · 05:23 KST 라 상시 가동 전제 | B-14, `prod-smoke-daily.yml:20,45,54` | 🟡 |
| U-11 | **`406Mi` 시스템 requests 에 ESO 3파드가 빠져 있고**, DaemonSet ÷ Deployment 분리는 **여전히 미측정**(원장 L-43, *"$0 경로가 없다"*). 6b 메모리 예산을 짤 때 이 둘을 빼먹으면 `variables.tf` 가 *"정정할 때마다 다른 항목을 빠뜨렸다"* 고 적어둔 패턴의 4번째 재현이 된다 | `migration-log.md:2366,2397`, `variables.tf:110` | 🟡 |

### ⑧ 그래서 — 쪼갠 결과

#### `6a` — 토폴로지 무관 $0 분 (지금 할 수 있는 것)

| | 내용 | 파일 계층 | 상태 |
|:-:|---|---|---|
| ~~①~~ | ~~L-44 수정~~ | — | 🔴 **철회.** L-44 는 `obsolete`(09-04)이고 SOP 가 이미 다른 처방(*main 에서 `workflow_dispatch` 재빌드*)을 골라뒀다. **틀린 게 아니라 불필요하다.** 상세: ① |
| ② | ~~치환 체인 일원화 (문서 **3곳**, 스크립트 1개로)~~ → ✅ **완료 (2026-09-22), 단 내용이 달랐다.** 🔴 **"3곳" 이 아니라 2곳**(`eks-session-sop.md` 의 `PLACEHOLDER` 언급 **0건**). 🔴 **"어긋날 수 있다" 가 아니라 이미 어긋나 있었다** — `k8s/README.md:80` 의 지시 1건이 실제로 틀려 있었다. **스크립트는 만들지 않았다**(§3 의 kustomize 기각 근거가 그대로 적용). 상세: 아래 「`6a ②` 실행 결과」 | `k8s/README.md` | ✅ |
| ③ | ~~CI 역할 access entry~~ | `2-cluster/access.tf` | 🔴 **하지 않는다** — U-5 대로 지금 넓히면 신뢰정책의 `pull_request` 때문에 **PR 트리거로 클러스터 admin 이 열린다**. 신뢰정책을 먼저 좁히는 설계가 선행이고 그건 6b 와 같이 간다 |
| ④ | **B-12 행의 L-44 인용 정정** | 이 계획서 | ✅ 이번에 처리 |

#### `6a ②` 실행 결과 (2026-09-22) — **스크립트가 아니라 드리프트 1건이었다**

착수 전 전제 검증에서 항목 정의가 **네 번째로** 바뀌었다.

| 내가 적어둔 전제 | 실측 |
|---|---|
| *"6종 PLACEHOLDER 가 문서 **3곳**에 산재"* | 🔴 **2곳.** `docs/eks-session-sop.md` 의 `PLACEHOLDER` 언급은 **0건**이다 |
| *"어긋날 수 있다"* | 🔴 **이미 어긋나 있었다** — 가정이 아니라 실현된 버그 |

**실제 치환 지점 9곳 / 7파일**을 전수 확인한 결과 **틀린 지시는 정확히 1건**이다:

```
k8s/README.md:80   kubectl apply -f k8s/eso/externalsecret-app.yaml      ← sed 없음
매니페스트 :57      key: APP_SECRET_NAME_PLACEHOLDER                      ← 치환 필요
```

**드리프트 경위 (`git log -S` 실측)**: README 줄은 **#339**(Stage 2), 플레이스홀더 도입은 **#418**(2a). ***#418 이 매니페스트를 고치면서 이 문서를 같이 안 고쳤다.*** `externalsecret-app.yaml` 자신은 올바른 명령을 주석으로 갖고 있었으므로 **같은 지식이 두 곳에 살면서 한쪽만 갱신된** 형태다.

🔑 **스크립트를 만들지 않았다** — `k8s/README.md` §3 이 kustomize 를 기각하며 *"도구를 하나 더 들이는 것보다 절차가 눈에 보이는 편"* 을 이미 골랐고, **`sed` 래퍼 스크립트에도 같은 근거가 적용된다**. 대신 **이미 있는 기계장치**(`verify` 마커)를 썼다 — 신설 0줄.

⚠️ **마커는 역방향만 막는다**(플레이스홀더가 *사라지면* 깨진다). **정방향**(새 플레이스홀더가 생겼는데 문서가 안 따라오는 것 = 이번에 실제로 난 사고)은 **이 마커 문법으로 표현되지 않는다.** 문서에 명시만 해뒀다.

> 📌 **재판정 트리거 (QA 제안, 2026-09-22)**: *"문서가 `sed` 치환 지시를 빠뜨려 플레이스홀더가
> 그대로 apply 되는 것"* 이 **세 번째**로 나오면 **스크립트 도입 여부를 여기서 다시 판정한다.**
>
> 🔴 **현재 1건이다 — 이번 `k8s/README.md:80` 하나뿐.**
>
> ⚠️ **처음엔 "2건" 이라 쓰면서 *"①항목 3 의 `sed` 치환 누락"* 을 들었는데, 그런 사건은 없었다.**
> 실측(QA F-1 이 잡고 내가 재확인): `CERT_ARN_PLACEHOLDER` 를 건드린 커밋은 **`f978ef8`(#426) 하나**이고
> **도입 커밋에 `sed` 가 함께 들어갔다.** 빠뜨렸다 고친 이력이 없다.
> 항목 3 부근의 `sed` 관련 사건 2건은 **둘 다 다른 실패 모드**다 —
> ①**마스킹 파이프의 `sed` 가 full-buffering 으로 회귀**(`sed -u` 로 해소) — **#423 QA F-1**(`230c332`).
> ②**`sed` 는 치환 대상이 없어도·빈 값으로 치환해도 `exit 0`** 이라 `pipefail` 로 못 잡는다 → `tofu output` 의
>   빈 값을 대비해 **미리 넣은** `case` 형태 검사(`eks-tutorial-steps.md:2054-2061`) — **#426 QA F-5**(`f978ef8`).
> 둘 다 *"문서가 지시를 빠뜨렸다"* 가 아니다.
>
> ⚠️ **이 인용도 처음엔 뒤바뀌어 있었다 (QA F-2)** — *마스킹* 에 `#426 F-5` 를 붙였는데 마스킹은 **#423** 이다.
> ***두 항목이 다르다고 정확히 구분해놓고, 번호는 반대로 붙였다.***
>
> 🔑 ***이 세션 세 번째 숫자 오류인데, 앞의 둘과 질이 다르다.*** `15 vs 16`·`5 vs 6` 은 **실재하는 것을
> 잘못 센 것**이지만, 이번은 **개수를 맞추려고 사례를 만들어낸 것**이다. `"2건"` 이라는 말이 먼저 있었고
> 거기 채울 항목을 찾았다. ***재발 카운트는 셀 때마다 사례를 적고, 사례마다 커밋 해시를 붙인다.***
>
> 🔑 **트리거를 박아두는 이유**: 지금의 기각 근거(*"절차가 눈에 보이는 편"*)는 **1~2건에서는 옳지만
> 반복 횟수에 따라 뒤집힐 수 있는 종류**다. 트리거가 없으면 다음 세션이 같은 논쟁을 처음부터 한다.
> ⚠️ 세 번째가 와도 **자동 착수가 아니다** — 하네스 동결 규칙은 그대로이고, 판정만 다시 하는 것이다.

> ⚠️ **6a 가 ①을 잃어 실행 항목이 ② 하나로 줄었다.** 그래도 $0 이고 클러스터가 필요 없으며,
> **문서 2곳이 어긋난 상태는 실재했다** — 가정이 아니라 `k8s/README.md:80` 에서 **실현돼 있었다**.
>
> 🔴 **이 줄은 정정을 두 번 빠뜨렸다 (QA F-1 `not-fixed`).** 원문은 *"문서 **3곳**이 어긋날 수 있는 상태는
> 실재한다(**항목 3 에서 `sed` 치환을 빠뜨려 겪은 형태**)"* 였다 — **한 줄에 오류 2개**이고, 둘 다
> **같은 문서의 20줄 위에서 이미 정정한 것**이었다(3곳→2곳, 항목 3 사례는 허구).
> 🔴 **결국 세 번째까지 나왔다 (QA F-3, `:244` 순서 표 6a② 셀).** 그리고 **왜 내 전수 grep 이 놓쳤는지가 핵심이다**:
> `grep -n "3곳" … | cut -c1-170` 으로 돌렸고, **`:244` 는 출력에 나왔는데 170자에서 잘려 ② 셀이 안 보였다.**
> ***출력을 잘라놓고 "봤다"고 판단한 것이다.*** 이 레포의 반복 실패(*"검사가 주장보다 헐겁다"*)와 같은 가족이다 —
> 여기서 대리 지표는 **"grep 이 결과를 냈다"** 였고, 진짜 성질은 **"그 결과를 끝까지 읽었다"** 였다.
>
> 📌 **규약**: 긴 표 행을 훑을 때 `cut`·`head` 로 자르지 않는다. 폭이 문제면 **셀 단위로 쪼개서**(예: `tr '.' '\n'`)
> 보거나 스크립트로 판정한다. 잘린 출력은 **음성 증거로 쓸 수 없다.**
>
> 🔑 ***국소 수정이 반쪽이었던 것이 이 파일에서 세 번째다*** (#428 F-3 *"#427 유실을 고쳤다고 보고했는데
> 하나만 고친 것"* 과 같은 형태). **정정할 때는 같은 주장이 사는 다른 자리를 `grep` 으로 전수 확인한다.**

#### `6b` — ArgoCD gitops 레이어 → **이관 후**

블로커 ②(퍼블릭 레포 ↔ git 단일 진실)만 이관 후에도 **남는다.** 나머지(③고아 ALB·④순서·D-002)는
destroy-after-use 가 사라지면 함께 사라진다. → **이관 계획서에서 다루되, ②의 해법
(프라이빗 렌더 레포 / CMP / 렌더 안 하는 구조)을 반드시 먼저 정할 것.**

---

## 항목 7 — 착수 설계 ($0 사전 조사 + Blindspot Pass, 2026-09-22)

> **결론 먼저: 항목 7 은 선행 조건이 아니다. 통째로 이관 계획서로 옮긴다** (사용자 결정 2026-09-22).
> 그리고 이 조사가 **순서표에 없던 결정 하나**를 드러냈다 — **월 $140 실지출**. 그것을
> **이관 계획서의 0번 항목**으로 세운다(사용자 결정).
>
> 항목 5·6b 와 **같은 모양**이다. 판별 기준은 동일하다: ***"이관 전에 답이 나오는가"***.

### ① 판정 — 닫는 항목 3개 전부 "이관 전에 답이 안 나온다"

| 닫는 항목 | 오늘 위반 | 무엇에 의존하나 |
|---|:-:|---|
| **B-14** `prod-smoke-daily.yml` 3중 고장 | **0건** | — (아래 ②) |
| **B-18** 학습 전제 무근거화 | — | **학습 클러스터 존속 여부** |
| **B-1** 리퍼가 prod 를 자동 파괴 | — | 리퍼를 **고칠지 지울지** = 이관 후 토폴로지 |

### ② B-14 — 세어보니 오늘 위반 **0건**이고, 3분의 1은 **고장이 아니다**

| # | 서술 | 판정 |
|:-:|---|---|
| ① Vercel 을 때려 Fly/EKS 구분 불가 | `prod-smoke-daily.yml` 이 치는 URL 이 `quest.dhbang.co.kr`(Vercel) → rewrite → Fly. **오늘은 대상이 하나뿐이라 모호성 자체가 없다** | 이관 후에만 틀림 |
| ② 실패 안내가 `fly status` 하드코딩 | 오늘 prod = Fly 이므로 **정확한 안내다** | 이관 후에만 틀림 |
| ③ `cron '23 20 * * *'` = 05:23 KST 라 **상시 가동 전제** | 🔴 **애초에 고장이 아니다.** 항목 7 이 바로 그 전제를 **참으로 만든다** — 자기참조다. ③ 이 실제로 무는 창은 *이관 후 & 상시 운영 전* 뿐인데 **그 구간은 정의상 존재하지 않는다**(항목 7 이 이관의 선행이므로) | 고장 아님 |

🔑 **목록에 없는 것이 오늘 유일하게 실재하는 약점이다**: 스모크가 Vercel 을 경유하므로
**Vercel rewrite 장애가 BE 회귀로 오보**된다. *"3중 고장"* 이 센 3개는 전부 미래의 것이고,
현재형 결함 1개는 목록 밖에 있었다.

### ③ 🔴 항목 7 이 의존하는 토폴로지 결정이 **존재하지 않고, 현재 코드로는 불가능하다**

```
2-cluster/backend.tf     key = "2-cluster/terraform.tfstate"   ← 리터럴. backend 블록은 보간 불가
1-network/network.tf     "kubernetes.io/cluster/${var.cluster_name}" = "shared"
                         ↑ 1-network 도 state 하나라 클러스터 하나만 태그한다
```

찾아본 곳 전부 음성: `infra/aws-eks/README.md` 「결정 기록」 전체 · `docs/eks-cost-model.md`
(`상시 운영` 0건) · 이 계획서 전문.

> 🔴 **내 중간 판단을 철회한다.** 조사 중 나는 *"리퍼의 EKS 이름 필터 누락은 토폴로지와 무관하게
> 오늘 고칠 수 있다"* 고 사용자에게 보고했다. **틀렸다.** 클러스터가 1개면 리퍼를 **지워야** 하고
> (필터는 무의미), 2개면 위 IaC 가 먼저다. **어느 쪽에서도 필터는 해법이 아니다.**
> 형태: *"고칠 수 있는 조각을 찾고 싶어서 찾아낸 것"* — 항목 5 에서 *별도 예산 키*를 우회로로
> 떠올렸다가 D-008 「기각한 대안」에 이미 닫혀 있던 것과 같다.

🟡 다만 **리퍼의 식별 필터 비대칭은 사실이다**(아래 ⑥ U-N). 이관 계획서에서 다룬다.

### ④ 🔴 B-18 의 숫자는 **어떤 셈법으로도 나오지 않는다**

```
B-18 서술   "tfsec 예외 4건(전부 '세션마다 폐기되는 학습 자산' 근거)"
실측        grep -rn "^[[:space:]]*#[[:space:]]*tfsec:ignore:" --include="*.tf" infra/
            → 디렉티브 25건
```

| 셈법 | 개수 |
|---|:-:|
| *"세션마다 폐기되는 학습 자산"* **정확히 그 문구** | **1** (`2-cluster/postgres-tls.tf`) |
| 형제 문장 *"세션마다 만들고 부수는 학습용"* 포함 (= `AVD-AWS-0098` 전부) | **3** |
| **수명**(폐기·재생성) 근거 전수 | **10** |
| 넓게 *"학습장"* 근거 | **25** (전부) |

개수 4 와 일치하는 집합은 `cluster.tf` 4건과 `rds.tf` 4건뿐인데, U-6 이 *"tfsec 예외 4건 **+**
`cluster.tf:6-9`"* 라고 써서 전자를 별개로 셌다. 남는 `rds.tf` 의 근거 헤더는 *"학습용
destroy-after-use"* 이고 *"세션마다 폐기되는 학습 자산"* 문구는 `postgres-tls.tf` 에만 있다
→ **B-18 은 서로 다른 두 집합을 한 문장에 겹쳐 썼다.** (항목 6a② 의 *"3곳 아니라 2곳"* 과 같은 형태)

🔴 **그리고 방향이 반대다 — 과대가 아니라 과소보고였다.**

```
코드가 스스로 "prod 이관 시 뒤집힌다"고 표시해둔 것
  "prod 이관" 정확 문구, tfsec 디렉티브에 붙은 것        2건  (0-bootstrap/s3-backups.tf)
  "prod면 / prod였다면 / prod 전환 시" 조건절 포함      12건
B-18 이 적은 것                                          4건   ← 3배 과소
```

`s3-backups.tf` 는 아예 *"🔴 **prod 이관 시 이 판단은 뒤집힌다.** 그때 덤프에는 사용자
데이터가 들어간다"* 라고 써뒀다. **재판정 목록은 새로 만드는 게 아니라 코드에서 수확하는 것이다.**

### ⑤ 🔴 B-18 이 인용한 원장 항목이 틀렸다 — L-49 가 아니라 **L-50**

| | |
|---|---|
| **L-49** (B-18 이 인용) | *"사전 승인된 파라미터를 과금 중에 자율 변경하는 것에 대한 **SOP 조항이 없다**"* — **없는 규칙은 뒤집힐 게 없다.** 게다가 L-51 이 이미 L-49 를 반증했다 |
| **L-50** (빠져 있음) | 🔴 **항목 7 을 명시 호명한 유일한 원장 항목이다**: *"ⓑ **상시 운영 전환(D-013 항목 7)에 착수할 때는 트리거가 아니라 필수** — 그 시점엔 destroy 대상이 학습 데이터가 아니라 사용자 데이터다"*, 그리고 *"항목 7 착수 시 **`cluster_dir` 하드코딩부터 본다**"* |

### ⑥ Blindspot 이 찾은, **계획에 없는데 반드시 건드리게 될 것**

> 📌 목적지 규칙(`CLAUDE.md` 「무엇을 어디에 적나」): *Blindspot 이 찾았지만 이번에 안 하는 것*은
> **계획서의 해당 항목 절**에 적는다. 원장은 QA 지적 전용이고, 일지는 시간순이라 검색되지 않는다.
> 아래가 그 목적지다 — **이관 계획서를 쓸 때 여기서 가져간다.**

| ID | 내용 | 확도 |
|---|---|:-:|
| **U-T** | 🔴 **ACM 갱신 함정.** `PERSISTENT-RESOURCES.md` 가 *"선행 조건 7에서 재검토"* 라고 **두 번** 써뒀는데 순서표 7 행에 ACM 언급 0건. 양방향 실측까지 끝나 있다(Ingress apply → `ELIGIBLE` / teardown → `INELIGIBLE` 로 **되돌아온다**). 만료 **2027-04-02**. 선택지 ①상시 ALB ②캘린더 감시 ③재발급 절차화 | 🔴 |
| **U-R** | 🔴 **예산 20단계가 첫 달에 14개 동시 발화한다.** `0-bootstrap/budget.tf` = $200 ÷ $10 누적(ANNUALLY). 상시 $140/월이면 1개월에 14단계 돌파 → 같은 파일이 스스로 적어둔 *"오탐이 잦은 알림은 곧 무시된다 — 그러면 진짜 신호도 함께 죽는다"* 에 그대로 걸린다. 순서표 7 은 *"이상탐지 임계"* 만 적고 **예산 계단 재설계가 없다** | 🔴 |
| **U-Q** | 🔴 **`guard-local-layers` 가 항목 7 PR 을 기계적으로 막는다.** `.github/workflows/infra-ci.yml` 의 `for local_layer in 2-cluster` 하드코딩. 상시 운영이면 `2-cluster` 를 `infra-deploy.yml` 매트릭스에 넣어야 하는데 이 잡이 정확히 그 PR 을 fail 시킨다 | 🔴 |
| **U-M** | 🔴 **마커 오탐 자가치유가 죽고, 그러면 `eks-session-marker.sh` 의 안전성 논증이 반전된다.** 그 파일은 따옴표 안 문자열도 마커를 만드는 오탐을 **의도적으로 수용**하며 근거를 *"과금 리소스가 하나도 없으면 마커가 자가 삭제된다 → 5분 안에 저절로 사라진다"* 로 적었다. **상시 운영에서는 과금 리소스가 항상 있으므로 그 경로가 영구히 죽는다** → 잡담 커밋 메시지가 만든 유령 마커가 잔존하고 2시간 뒤 prod 를 destroy 한다 | 🔴 |
| **U-L** | 🔴 **하트비트의 실제 정의는 "노트북"이 아니라 "Claude Code 턴"이다.** `eks-heartbeat-reminder.sh` 의 `touch heartbeat` 는 **Stop 훅**에서만 돈다. 상시 운영에서는 prod 에 대해 아무도 턴을 돌리지 않으므로 **하트비트가 구조적으로 영구 stale** 이다. B-1 의 *"노트북을 2.5시간 덮으면"* 은 조건을 잘못 짚었다 — 조건은 **Claude 세션의 부재**이고 상시 운영은 그 부재가 **기본 상태**다 | 🔴 |
| **U-N** | 🟡 **리퍼의 식별 필터 비대칭이 1곳이 아니라 4곳.** RDS 만 `starts_with(…,'devquest')` 로 거르고 주석에 원칙까지 적어뒀는데, `aws eks list-clusters`·`warn_orphan_albs`·그리고 **실패 경로의 잔존 확인 로그는 붙여뒀던 `devquest` 필터를 다시 떨어뜨렸다** | 🔴 |
| **U-O** | 🟡 **탐지 범위(계정 전역) ≠ 조치 범위(디렉토리 1개).** 생존 판정은 계정 전역인데 파괴 대상은 `cluster_dir` 고정. `warn_orphan_volumes` 주석이 *"감지를 생존 판정에 넣으면 → 영원히 끝나지 않는 destroy 루프"* 라고 못 박은 그 구조를 2클러스터 토폴로지가 재생산한다 | 🟡 |
| **U-P** | 🟡 **$0 이 아니다 — 사용자 머신 상태 변경을 포함한다.** `eks-reaper.sh` 수정은 즉시 반영되지만 TTL·StartInterval 변경은 **plist 템플릿 수정 + `install-reaper.sh` 재실행**이 필요하고, 설치본은 `~/Library/LaunchAgents/` 에 있다. 게다가 plist 에 `EKS_REAPER_TTL` 이 **없어서** 7200 고정이다 | 🟡 |
| **U-I** | 🟡 **`assert-eks-quiz.sh` 는 B-18 의 오분류다.** 판정 축이 **브랜치 이름**(`^stage/eks-`)이지 클러스터 수명이 아니고, prod 브랜치(`feat/`·`fix/`)는 **오늘도 이미 면제**다. 상시 운영이 무근거화하는 게 아니라 애초에 적용 범위 밖이다. 실제 질문은 *"prod 변경도 퀴즈 대상인가"* 라는 **범위 확대**이고 항목 7 의 방향(완화)과 반대다 | 🔴 |
| **U-J** | 🟡 *"SOP 전체"* 는 과대 — `eks-session-sop.md` 374줄 중 destroy-after-use 종속은 **3개 절**(§과금 구간·§종료·§안전장치). §시작은 대부분 잔존 | 🟡 |
| **U-U** | 🟡 **「실패 6종」 표는 건드리면 23곳이 깨지고, 안 건드리면 전제가 거짓이 된다.** 표의 존재 근거가 *"어차피 매번 부수므로 추가 작업 없이 매 세션 연습된다"* 인데 `PERSISTENT-RESOURCES.md` 는 이미 *"prod 이관 후에는 이 절의 전제 자체가 사라진다"* 라고 적어뒀다 | 🟡 |
| **U-V** | 🟡 `random_password.jwt_secret["prod"]` 가 **이미 tfstate 에 존재**하고 보호는 `prevent_destroy` 뿐이다. 잃으면 전 사용자 강제 로그아웃(30일 만료라 재로그인 파도가 30일간). L-50 의 ⓑ 트리거가 정확히 이것을 가리킨다 | 🟡 |
| **U-W** | 🟡 리퍼 회귀 스위트가 거의 없다(`test-guards.sh` 에 reaper 케이스 0건). `CLAUDE.md` 「가드 규약 3줄」② 가 *"가드를 고칠 때 회귀 스위트를 함께 고친다"* 를 요구하는데, 스위트 신설은 동결 규칙 해제 조건 ②의 *"스크립트·훅 신설 금지"* 와 충돌한다(아래 ⑧) | 🟡 |

### ⑦ 🔴 **순서표에 없던 결정 — 월 $140 실지출. 이관 계획서의 0번 항목으로 세운다**

```
docs/eks-cost-model.md  "Account closes when credits are depleted OR when the plan duration ends"
                        🔑 "prod 는 Fly+Neon 이라 계정 폐쇄돼도 무영향이다"
                           ↑ 이관이 이 문장을 직접 거짓으로 만든다

크레딧 $199.81 − 소진 ≈$6 = 잔여 ≈$194
상시 $140/월              → 약 1.4개월 뒤 소진
플랜 만료 2027-01-15      → 2026-09-22 기준 3.8개월
```

🔴 **정정 — 이 결정은 "순서표에 없던" 것이지 "레포에 없던" 것이 아니다.**
위 문단을 쓴 직후 `infra/aws-eks/README.md` 의 **📌 D-002(`✅유효`) 「상시 운영은 기각 — 자기
선례와 충돌」** 을 열어보니 **같은 계산이 이미 있었다**: *"$200 크레딧 수명 **5~7주**"*.
내가 독립으로 낸 *"≈1.4개월"* 과 같은 값이다. 게다가 D-002 는 이렇게 경고해뒀다 —
***"재채택 유혹이 실재하는 결정이다 — '잠깐만 켜두면 편한데'가 곧 월 $122~174다.
크레딧 잔액이 남아 보일 때 특히 흔들린다."***

🔑 그러므로 **0번 항목은 "새 결정"이 아니라 `✅유효` 인 D-002 의 반전**이고,
`design-change-procedure.md` **전 단계**를 밟아야 한다(D-002 가 명시적으로 요구한다:
*"뒤집으려면 아래 표의 숫자를 **다시 실측**하고 전 단계를 밟을 것"*).
⚠️ 이것이 `CLAUDE.md` 빨간 깃발의 ***"전에 이렇게 하기로 했었지"*** 와 **반대 방향** 실패다 —
기억이 뒤집힌 결정을 유효한 것으로 재생하는 게 아니라, **유효한 기각 결정을 못 보고
처음부터 다시 계산했다.** 다행히 같은 값이 나왔지만, 달랐다면 어느 쪽이 맞는지 몰랐을 것이다.

⚠️ **"성립 불가"가 아니다.** `eks-cost-model.md` 에 *"잔여 크레딧 이월 — Paid 업그레이드해도
남은 크레딧이 future bill 에 적용된다"* 가 있다. 정확한 서술은 계정이 죽는다가 아니라

> ***이관은 무료 크레딧으로 굴리는 것이 아니라 월 $140 실지출을 시작하는 것이고,
> 그 결정이 어디에도 없다.***

이 계획서 「시한 정정」 절이 *"이 계획은 그 전제를 깨면서 그 전제 위에 세워진 예산 상한을
그대로 쓴다"* 라고 **이미 인정**해뒀는데 순서표에 항목으로 세우지 않았다. → **이관 계획서 0번.**

### ⑧ 🔴 하네스 동결 규칙상 **적법한 착수 경로가 없다** — 이것도 이관 계획서로 넘긴다

| 해제 조건 | 항목 7 |
|---|---|
| ① 제품 작업이 막힘 | **불성립** — 막히는 것은 제품 작업이 아니라 **prod 가용성**이다 |
| ② 사용자가 먼저 꺼냄 | 범위가 🔴 *"문서 변경으로 한정. 스크립트·훅 신설 금지"*. 항목 7 은 `eks-reaper.sh` **로직 변경이 불가피**하다 |

유일한 근거는 원장 **L-50** 한 줄(*"트리거가 아니라 필수"*)인데 `CLAUDE.md` 해제 조건 목록에
**반영 0건**이다. `CLAUDE.md` 가 스스로 적어둔 함정 — ***"이건 일지에 적어뒀으니 됐어" → 일지는
기록이지 반영이 아니다*** — 에 정확히 걸려 있다.

🔴 **그런데 지금 `CLAUDE.md` 를 고치지 않는다.** 고치는 것 자체가 하네스 작업이고,
**내가 먼저 꺼낸 것**이라 해제 조건 ②의 *"사용자가 먼저"* 를 만족하지 않는다
(*"물으면 대개 승인이 오므로, 유도를 허용하면 이 조건은 그 자리에서 무력해진다"*).
→ **이관 계획서 착수 시점에 L-50 ⓑ 가 발화하면 그때 함께 처리한다.**

🟡 **U-AA — 레포에 없는 지식**: 이 레포의 가드 실패 표본은 **전부 조이는 쪽**이다(L-22 4라운드).
항목 7 은 최초로 **가드를 푸는** 대규모 변경인데, `CLAUDE.md` 에는 *"꺼진 가드를 켜는 것은
무해하지 않다"* 만 있고 **반대 방향 문장이 없다.**

### ⑨ 인용 정정 (Blindspot 이 잡은 것)

```
계획서 B-1     reaper/com.devquest.eks-reaper.plist   → infra/aws-eks/reaper/... (줄번호는 맞음)
내 조사 노트   guard-local-layers.sh                  → 그런 파일은 없다. infra-ci.yml 의 잡 이름
내 조사 노트   2-cluster/cost-anomaly.tf              → 0-bootstrap/cost-anomaly.tf (budget.tf 도 동일)
내 보고        eks-reaper.sh:180 cleanup 호출          → :171
```

🔑 마지막 것은 **L-53 이 채택한 관례를 안 지켜서 났다** — *"코드를 인용할 때 줄번호를 쓰지 않고
선언문을 쓴다."* 위 4건 중 줄번호로 인용한 것만 틀렸고, 파일명·선언문으로 인용한 것은 맞았다.

### ⑩ QA 가 잡은 것 — **새로운 형태의 세기 오류: 숫자 번짐**

**F-1 (MEDIUM)**: 순서표 7 행에 *"계획에 없는 필수 작업 **12건**"* 이라고 썼는데 ⑥ 표는 **13행**이다.

```
awk '/^\| \*\*U-[TRQMLNOPIJUVW]\*\* \|/{c++} END{print c}'   →  13
```

🔴 **원인이 앞선 세 번과 다르다.** #429(`15 vs 16`)·#430(`5 vs 6`)은 **실재하는 것을 잘못 센 것**이고
#432 는 **없는 사례를 지어낸 것**이었다. 이번은 ***바로 앞 문장의 숫자가 번진 것*** 이다 —
같은 행 안에 *"코드가 스스로 뒤집힘으로 표시해둔 것이 **12건**"* 이 먼저 있었고, 다른 집합을 가리키는
그 12 가 뒤 문장으로 새어 들어왔다. QA 의 표현이 정확하다: ***"두 '12' 가 서로 다른 집합을
가리키는데 인접해 있어 착시를 유발한다."***
(⚠️ 이 인용의 출처는 **1라운드 QA 보고서 본문**이고 `findings.md` 에는 없다 — 보고서 본문은
파일로 저장되지 않으므로 **나중에 검증할 수 없는 인용**이다. 2라운드 QA 가 이것을 짚었다.
📌 사라지는 매체를 인용할 때는 그 사실을 함께 적는다.)

📌 **관례 추가**: 한 문장·한 표 행 안에 **서로 다른 집합의 개수를 두 개 이상 쓸 때는 각각을
세는 명령을 함께 적는다.** 개수를 나란히 두는 것 자체가 위험하다.

**F-2 (LOW)**: 09-19 의 *"7건에서 6건"* 목록이 `6`·`7` 을 그대로 담은 채 남아 있었다
(#431 의 6a/6b 분할과 이번 7 제외가 반영 안 됨). **취소선 처리.** 아래 09-22 주석이 정정하고
있었지만, ***정정문 위에 갱신 안 된 원문을 평문으로 두는 것*** 이 정확히 직전 PR 의 L-44 사고
(*행을 읽고 절을 안 읽었다*)가 일어나는 조건이다.

---

## 항목 4 — 종료 (재측정 + 판정, 2026-09-24)

> **결론: ⑤ 현행 유지(`limits` 576Mi 그대로). 선행 조건 4 를 닫는다.**
> 그리고 **B-5 의 비교 축이 틀렸다** — 아래 ③. 덤으로 **선행 조건과 무관한 prod 리스크 1건**을 찾았다(⑤).
>
> 🔑 이 절은 09-18 순서표가 *"상세: 「항목 4 — 재정의」"* 로 가리켰지만 **존재한 적 없던** 절의 자리다(#434).

### ① 실측 — 같은 JVM 인스턴스 3개 표본

부팅 `2026-09-15 06:50Z` (= `be/` 마지막 변경 #424 배포). **세 표본 사이 재시작 없음** — 업타임이 연속이다.

| 시각 (UTC) | uptime | VmRSS | swap 사용 | **총 발자국** | MemAvailable | Metaspace used |
|---|---:|---:|---:|---:|---:|---:|
| 2026-09-18 05:38 | 70.8h | 386.70 | 33.00 | **419.70** | 26.60 | 138.80 |
| 2026-09-23 14:37 | 199.8h | 376.80 | 50.05 | **426.85** | 33.24 | — |
| 2026-09-24 01:07 | 210.3h | 374.92 | 53.55 | **428.47** | 35.38 | 140.11 |

(단위 MiB. Linux `/proc` 의 `kB` 는 KiB 다.)

🔑 **RSS 는 단조 감소하는데 swap 사용은 단조 증가한다.** 커널이 식은 페이지를 밀어내는 것이므로
**진짜 증가량은 둘의 합(총 발자국)** 으로 봐야 한다. RSS 만 보면 *"줄고 있다"* 는 반대 결론이 난다.
⚠️ **`MemAvailable` 도 늘고 있다**(26.6 → 35.4) — *"시간이 갈수록 빡빡해진다"* 는 서술과 반대다.

### ② 🔴 `3 MB/h` 는 기각된다 — 54배 과대

```
3 MB/h 가정이면 09-23 RSS = 386.7 + 3×129 = 774 MiB
그런데 이 머신은 RAM 459 + swap 256 = 715 MiB    → 물리적으로 불가능
```

`3 MB/h` 는 **2026-07 값**(#245 이전 워크로드)이고 현재 코드에 해당하지 않는다.

| 창 | Δt | 기울기 |
|---|---:|---|
| 표본1→2 | 129.0h | +0.0554 MiB/h (+1.33 MiB/일) |
| 표본2→3 | 10.5h | +0.1545 MiB/h (+3.71 MiB/일) |
| **표본1→3** | **139.5h** | **+0.0629 MiB/h (+1.51 MiB/일)** |

⚠️ **기울기가 안정적이지 않다.** 짧은 창(10.5h)이 2.8배 가파르다 — 노이즈일 수도, 가속일 수도 있다.
**판정에는 가장 긴 창(1→3)을 쓰되, 런웨이는 구간으로 읽는다: 40~98일.** 어느 쪽이든 옛 값 **2.6일** 과는 자릿수가 다르다.

### ③ 🔴 B-5 재판정 — **비교 축이 틀렸다** (방향이 뒤집힌다)

B-5 와 `jvm-observability-notes.md` §6 은 **용량**을 비교했다:
*"Fly 665(앱 상한 409 + swap 256) vs K8s 576 → K8s 가 89 MiB 적다."*

**틀린 축이다.** 중요한 것은 용량이 아니라 ***지금 발자국이 각 RAM 천장에 들어가는가*** 다.

| | RAM 천장 | 발자국 428.5 MiB |
|---|---:|---|
| **Fly** | **409** (kill 수위 `anon-rss`, 커널 OOM 로그 8건 전수) | ❌ **안 들어간다** → 그래서 53.6 MiB 가 swap 에 나가 있다 |
| **K8s** | **576** (`k8s/base/core-api.yaml` `limits.memory`) | ✅ 들어간다 — **여유 147.5 MiB** |

***Fly 는 swap 이 있어서 여유로운 게 아니라, RAM 천장(409)이 발자국(428.5)보다 낮아서 swap 이
필수인 것이다.*** K8s 576Mi 는 swap 없이 그 발자국을 통째로 담는다.

```
런웨이   옛 계산  (576 − 386.7) ÷ 3 MB/h        =   63h =   2.6일
         새 계산  (576 − 428.5) ÷ 0.0629 MiB/h = 2345h =  98일   (짧은 창 기준이면 40일)
```

> ⚠️ **여유 147.5 를 그대로 믿지 마라 (🟡 미검증)**: cgroup v2 의 `memory.max` 는 anon 만이 아니라
> **page cache 도 센다**. 실제 여유는 이보다 작다. 다만 **부호가 바뀔 크기는 아니다.**

> 🔑 **B-5 를 `🔄부분무효` 로 본다** — *"K8s 에 swap 이 없다"* 는 사실이고 *"그래서 Fly 보다 위험하다"* 가
> 틀렸다. **사실은 맞고 결론이 틀린** 형태이며, 이 계획서가 2b·4 에서 두 번 겪은 것과 같다.

### ④ 🔴 `§1` 의 *"매일 +4.2 MiB 점프"* 를 실측으로 기각

```
누적이면 예상   138.8 + 4.2 × 5.81일 = 163.2 MiB
실측                                    140.11 MiB
→ 누적이었다면 상한 160 을 넘겨 이미 OOME 로 죽어 있어야 한다
```

같은 줄이 적어둔 *"전형적 **지연 로딩**"* 이 맞다 — **JVM 기동 후 첫 실행 1회**다.
*"매일"* 이라는 단어가 1회성 현상을 반복 현상으로 바꿔놨다.

🔑 **그래서 `TASK-12` 의 *"08:30/09:30 으로 갈라 찍기"* 절차는 전제가 사라졌다.**
***나는 그 절차를 통째로 이 혼동 위에 세웠다.*** (배포 직후 첫날에만 유효하다.)

### ⑤ 🔴 신규 — 선행 조건과 무관한 **prod 리스크**: 메타스페이스 87.6%

```
used 140.11 / max 160.00 = 87.6%    여유 19.89 MiB
같은 JVM 안에서 +1.31 MiB / 139.5h = +0.225 MiB/일
→ 상한 도달까지 used 기준 88일 · committed(141.00) 기준 84일
```

🔴 **`limits` 를 올려도 안 고쳐진다.** `-XX:MaxMetaspaceSize=160m` 은 컨테이너 한계와 **별개**이고,
거기 닿으면 **2026-07-14 와 같은 경로**다 — `Pause Full (Metadata GC Threshold)` 무한 반복 →
`OutOfMemoryError: Metaspace` → prod 다운. 그때는 128m → 160m 으로 올려 살렸다.

**언제 현실화되나 — 재배포가 JVM 을 리셋하므로 조건은 *"`be/**` 배포 없이 ~85일"*** 이다.

```
현재 머신 부팅   2026-09-15 06:50Z (#424)     업타임 9일 → 남은 여유 ~79일
최근 be/ 배포 간격   09-15 ←15일← 08-31 ←3← 08-28 ←1← 08-27 ←4← 08-23
```

관측된 최대 공백이 **15일**이라 당장은 🟢. **다만 근거가 약하다** — 이 레포는 **#361(08-06) 이후
`feat` 커밋이 0건**이고(`CLAUDE.md` 하네스 동결 규칙이 스스로 적어둔 사실), ***"제품 작업이 멈춘
상태"가 바로 이 리스크의 조건***이다. 소유는 `.claude/CONTEXT.md` 제품 백로그.

> ⚠️ **EKS 이관은 이것을 악화시키지 않는다** — 파드 수명도 배포 주기에 묶이므로 Fly 와 같다.
> 이 항목이 여기 적힌 것은 **항목 4 를 재려다 나왔기 때문**이지 이관 조건이라서가 아니다.

### ⑥ 정정 이력 — 이 절을 쓰며 낸 오류

```
내가 보고한 것   메타스페이스 +0.24 MiB/일 · 상한까지 83일
실제            +0.225 MiB/일 · 88일
원인            Δt 에 129h(=meminfo 두 표본 간격)를 썼다. 메타스페이스는 그 다음 날 쟀으므로
                실제 간격은 139.5h 다. ***두 측정의 시각이 다른데 같은 Δt 를 재사용했다.***
교정            세 항목을 한 명령으로 묶고 `date`·`/proc/uptime` 을 함께 찍어 표본을 못박았다
```

📌 **관례: 서로 다른 시각에 잰 값으로 기울기를 낼 때는 각 표본에 타임스탬프를 붙인다.**
한 번의 조회로 묶을 수 있으면 묶는다 — 간격을 나중에 추정하면 반드시 틀린다.
