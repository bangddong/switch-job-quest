# prod 창 — EKS 켜고 끄기 (📌D-016 순서표 8)

> 결정·순서·남은 위험은 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` 「8번」이 소유한다.
> 이 문서는 **어떻게 쓰나**만 적는다.

## 지금 할 수 있는 것 (8-B + 8-C C-2)

켜면 EKS 가 prod DB(EC2)·prod 키로 뜨고 ALB 와 `eks.quest.dhbang.co.kr`(DNS only) 이 생긴다. **사용자 트래픽은 입력 `traffic` 이 정한다.**

| 하고 싶은 것 | 방법 (원격에서 된다 — 로컬 셸 불필요) |
|---|---|
| 켜기 — 트래픽 Fly 유지 | Actions → **EKS Window** → `action=on`, `hours=6`, `traffic=fly`(기본) · `gh workflow run eks-window.yml -f action=on -f hours=6` |
| 🔴 켜기 — prod `/api` 를 EKS 로 | 같은 화면에서 `traffic=eks` · `… -f traffic=eks`. 임대 만료 시각까지 EKS, 지나면 Worker 가 스스로 Fly |
| 끄기 | `action=off` · 또는 **18:00 KST** 자동. 언제나 **트래픽 Fly 복귀 → 배수 240s → ALB·클러스터 삭제** 순 |
| 지금 어디로 가나 | `curl -sI https://quest.dhbang.co.kr/api/v1/daily-question \| grep -i x-devquest-backend` → `fly` / `eks` |
| 지금 켜져 있나 | `aws eks list-clusters --region ap-northeast-2` · 임대: `aws ssm get-parameter --name /devquest/ops/eks-window/lease --region ap-northeast-2 --query Parameter.Value --output text` (끝에 ` traffic=eks` 면 트래픽 창) |

### 트래픽 스위치 (방식 ⓑ — 계획서 8-C)

- 갈림길은 **Worker** 다. Worker 가 요청마다 Cloudflare KV `devquest-backend-switch` 의 `api-target` 을 읽는다.
  `{"target":"eks","until":<ISO>}` 이고 until 이 미래면 EKS(`eks.quest`), 그 밖은 전부 Fly.
- 그 값을 쓰는 것은 **이 워크플로뿐**이다(`switch-on.sh`·`switch-off.sh`, 토큰 SSM `/devquest/ops/cloudflare-token`).
- `until` = 임대 만료 — **off 가 실패해도 그 시각에 트래픽은 Fly 로 돌아온다.** 클러스터는 다음 watchdog 이 끈다.
- 수동 긴급 복귀(대시보드): Cloudflare → Workers KV → `devquest-backend-switch` → `api-target` 삭제 또는 `{"target":"fly"}`. 전파 ≤ 60s.

| 실패 | 동작 |
|---|---|
| on — eks.quest·스위치 확인 실패 | 같은 실행의 off 가 KV 를 fly 로 되돌리고 끈다 |
| off — Cloudflare 불통 + 임대 유효 + traffic=eks 창 | **destroy 보류**(빨강). 임대 만료 뒤 Worker 는 Fly, watchdog 이 끈다 — 상한 = 임대 + 1h |
| off — Cloudflare 불통 + 그 밖(traffic=fly 창·임대 만료·없음) | 경고 후 진행. `eks.quest` 가 죽은 ALB 를 가리키며 남을 수 있다(무해 — 다음 on 이 덮어씀) |

⚠️ Cloudflare 토큰 만료 **2027-01-24**. ⚠️ 러너의 확인은 러너가 닿는 엣지 기준이다(한국 엣지는 수십 초 늦을 수 있다).

## 무엇이 끄나 — 세 겹

| | 언제 | 무엇을 |
|---|---|---|
| 18:00 KST off | 매일(주말 포함) — **AWS EventBridge 가 정시에 dispatch**(`action=off`) | 무조건 off. 꺼져 있으면 no-op |
| 수동 off | 언제든 | 무조건 off |
| **매시 watchdog** | 매시 17분 — **EventBridge dispatch**(`action=watchdog`) · 매시 47분 — GitHub cron(보조, 지연·누락 잦음) | 클러스터가 있는데 **임대가 없거나 만료**면 off · 클러스터가 없는데 **LB 가 남았으면 빨강**(고아 ALB) |

> 🔴 **시각을 지키는 것은 AWS 다(B4-21, 2026-10-23).** GitHub cron 은 10-22 실측에서 18:00 off 가 **5시간 24분 늦었고**
> 매시 watchdog 은 **10슬롯 중 1개만** 생겼다. 그래서 `0-bootstrap/eks-window-dispatch.tf` 의 예약 규칙 2개가
> 정시에 이 워크플로를 `workflow_dispatch` 로 부른다(인증 = GitHub fine-grained PAT, 콘솔에서 넣는다).
> GitHub 의 18:00 cron 은 **지웠다** — 늦게 오는 force off 는 18:00 이후 정당하게 켠 창을 끈다. watchdog cron 만 보조로 남겼다.
> dispatch 가 실패하면(PAT 만료 401 · 워크플로 Disable · 입력 오류 422) **CloudWatch 알람이 메일**을 보낸다(SNS 구독 확인 필요).

- **임대**는 on 이 apply **전에** 쓴다(`expires = 지금 + hours`). off 는 착수하자마자 만료로 바꾸고, AWS 로 꺼짐을 확인한 뒤 지운다.
  그래서 off 가 중간에 죽어도 다음 시간 watchdog 이 다시 돈다.
- on 이 실패하면(임대를 쓴 뒤) **같은 실행의 off 잡**이 바로 끈다 — 8-B 는 트래픽이 없으니 망가진 창은 즉시 끄는 게 안전하다.
- 빨강 = GitHub 실패 메일 = 유일한 알림. ⚠️ 네이버 메일은 **스팸함**으로 갔다(6-D 실측) — 수신 허용 등록 확인.

## ⚠️ 로컬에서 2-cluster 를 apply 할 일이 생기면

watchdog 은 **누가 켰는지 묻지 않는다** — 임대 없는 클러스터는 한 시간 안에 꺼진다(Q-1: 이 클러스터는 prod 전용, 학습 세션은 끝났다).
로컬에서 꼭 켜야 하면 **임대를 직접 쓴다**(권장). Actions 화면에서 EKS Window 를 Disable 하는 방법도 있지만,
그러면 EventBridge dispatch 가 매시 실패해 **알람 메일이 반복된다** — Disable 할 거면 규칙도 함께 끄고, 끝나면 둘 다 켠다:

```bash
for n in off watchdog; do aws events disable-rule --region ap-northeast-2 --name devquest-eks-window-$n; done   # 끝나면 enable-rule
```
> ⚠️ 규칙을 끈 채 두면 **다음 0-bootstrap apply 가 다시 켠다**(state = ENABLED). 의도된 동작이다 — 꺼진 안전장치가 잊히지 않게.

임대 직접 쓰기:

```bash
aws ssm put-parameter --region ap-northeast-2 --name /devquest/ops/eks-window/lease --type String --overwrite \
  --value "expires=$(( $(date +%s) + 3*3600 )) run=local started=$(date +%s)"
```

끝나면 지운다(`aws ssm delete-parameter …`). 18:00 off 는 임대와 무관하게 끈다.
> 반대 방향 — **로컬 도구(리퍼·리마인더)는 아직 임대를 읽지 않는다.** CI 클러스터는 createdAt 소유 판정으로 foreign 이 된다(4번).
> 남은 틈(QA 4번 F-2)은 계획서 8-B 「미룬 것」.

## 막혔을 때

| 증상 | 볼 곳 |
|---|---|
| on 이 예산 게이트에서 거부 | Step Summary 의 💰 줄. 남은 크레딧 − 예비 $30 − 상시 $0.5/일 × 남은 일수가 이번 창 비용보다 작다 |
| on 이 이미지 태그에서 실패 | `pick-image-tag.sh` 로그 — Fly 커밋 이미지가 ECR 에 없다. main 에서 ECR Push 를 돌린 뒤 다시 |
| `core-api-runtime 이 비었다` | SSM `/devquest/prod/app/` 키 이름이 허용 목록과 다르다(`k8s/gitops/apps-external/externalsecret-runtime.yaml`). 실례: `JUDGE0` 의 숫자 0 대신 알파벳 O |
| off 가 `LB 가 여전히 남아 있다` | destroy 를 일부러 안 했다(LBC SG 참조로 DependencyViolation + ALB 고아). `aws elbv2 describe-load-balancers` 로 확인 → 다음 watchdog 이 재시도 |
| off-verify 빨강 | Step Summary 의 🔴 줄. 임대가 남아 있으니 watchdog 이 매시 다시 돈다 |

## 파일

| 파일 | 하는 일 |
|---|---|
| `.github/workflows/eks-window.yml` | on·off 잡, 트리거·concurrency(취소 함정 설계는 파일 머리말) |
| `common.sh` | 클러스터 존재 · 임대 읽기/쓰기/만료/삭제 · LB 수 |
| `budget-gate.sh` | 예산 게이트 (`aws freetier` — 무료) |
| `pick-image-tag.sh` | 이미지 태그 = 마지막 성공 be-cd 커밋(8-A) |
| `on.sh` | apply 뒤 — 관문 · LBC · ArgoCD · Application · 판정 · 스모크(ALB 직접) |
| `cf.sh` | Cloudflare API — 토큰·계정·존·KV 조회(add-mask) · KV 읽기/쓰기 · `eks.quest` upsert/삭제 · DoH |
| `switch-on.sh` | on 마지막 — `eks.quest` → ALB · 실제 이름 200 · traffic=eks 면 임대 표식 → KV eks → 공개 URL 헤더 확인 |
| `switch-off.sh` | off 맨 앞 — KV fly → (직전 eks 면) 배수 240s → `eks.quest` 삭제 · Cloudflare 불통 폴백 |
| `decide-off.sh` | 끌지 판정(force/watchdog 표) |
| `off-pre.sh` | destroy 전 — LB 기록(LCU) · Application → Ingress → LB 0 |
| `off-verify.sh` | destroy 후 — AWS 로 꺼짐 판정 · LBC SG 정리 · 임대 삭제 |
| `tests/window-test.sh` | 목 회귀(67건, $0 — 목 aws·kubectl·curl). `tests/pick-image-tag-test.sh`(9건) |

🔴 이 폴더의 스크립트는 `tofu apply`/`destroy` 를 부르지 않는다 — 워크플로 단계에만 둔다(Infra CI guard 가 워크플로만 읽는다).
