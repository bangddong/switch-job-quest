# EKS 실습 세션 SOP (시작 → 작업 → 종료)

> 과금이 시작되는 실습의 **단일 출처**. 흩어진 규칙(튜토리얼 Step 8·CLAUDE.md·비용모델·teardown 순서)을
> 한 체크리스트로 모았다. **"끄는 걸 잊는 것"이 유일한 진짜 돈 새는 지점**이라 그걸 기계로도 막는다(아래 §안전장치).

## 🟢 시작 (여기까진 $0)

1. **통시간 확보 확인** — destroy까지 붙어있을 수 있나? 중간 이탈 = 과금 누수.

   🔴 **`tofu destroy` 는 사람이 승인해야 실행된다 (2026-10-11 결정).** 로컬 설정이 `tofu apply`·
   `tofu destroy` 를 매번 묻게 돼 있고, 이 규칙은 **유지한다** — 삭제 명령을 묻지 않고 돌리는 쪽을
   기각했다. 그 대가로 **세션 종료가 사람의 손에 걸린다.** 그래서 `apply` 승인을 받을 때 아래를
   **함께 알리고 확답을 받는다.** 확답이 없으면 apply 하지 않는다.

   | 알릴 것 | |
   |---|---|
   | 승인 창이 뜨는 횟수 | apply 2번 · **destroy 2번** (10분 도구 제한 때문에 각각 둘로 나눠 친다) |
   | destroy 승인이 필요한 예상 시각 | apply 시각 + 계획서의 과금 구간 길이. **시각으로** 말한다 |
   | 그 사이의 간격 | 첫 destroy 승인 뒤 **4~8분 뒤에 한 번 더** 뜬다 — 첫 승인 후 자리를 뜨면 안 된다 |
   | 못 지키면 드는 돈 | 분당 요금 × 대기 분. 리퍼는 **최장 2시간 30분** 뒤에야 움직이고, 맥이 자면 그마저 안 돈다 |

   **실제 사고 (2026-10-08, Stage 5a)**: 측정은 33분에 끝났는데 destroy 승인을 113분 + 37분 기다렸다.
   예상 $0.23 → **약 $0.59**, 그중 79% 가 유휴. 이 표의 어느 것도 apply 전에 알리지 않았다.
   | 구성 | 왕복 소요(벽시계) |
   |---|---|
   | Stage 0~1 (EKS만) | 30~40분 (실측: Task 8 ~50분, Stage 1 ~20분) |
   | **Stage 2~ (EKS + RDS)** | **40~50분** (실측 07-28: apply~검증~destroy 전체 ~45분) |

   > 07-28 실측 세부 — 추정보다 빨랐다:
   > | 단계 | 실측 |
   > |---|---|
   > | EKS 컨트롤플레인 생성 | ~6분 |
   > | 노드그룹 | 2분 48초 |
   > | 애드온(vpc-cni·kube-proxy) | 55초 / coredns 24초 |
   > | **RDS db.t4g.micro 생성** | **4분 50초** (추정 ~10분의 절반) |
   > | ESO helm install --wait | ~40초 |
   > | 앱 기동(Flyway 12개 포함) | 26초 |
   >
   > 🔴 **정정 (2026-08-07 실측) — RDS는 EKS와 *병렬로* 생성된다.**
   > 07-28에 이 자리에 *"직렬화되므로 RDS 시간이 EKS 시간에 더해진다"* 고 적었는데 **틀렸다.**
   > ```
   > aws_db_instance.main[0]: Creating...      aws_eks_cluster.main: Creating...   ← 동시 시작
   > aws_db_instance.main[0]: Creation complete after 4m56s
   > aws_eks_cluster.main:    Creation complete after 6m1s
   > aws_vpc_security_group_ingress_rule.rds_from_cluster[0]: Creation complete after 0s
   > ```
   > **RDS 4m56s가 EKS 6m1s 안에 통째로 들어갔다.** 의존 그래프상 `aws_db_instance`는
   > `aws_security_group.rds[0]`·`aws_db_subnet_group`에만 의존하고, 클러스터 SG를 참조하는 것은
   > **인그레스 규칙 하나**(`rds.tf:75`)다. 규칙이 별도 리소스라 사슬이 DB까지 이어지지 않는다.
   > 이 구조는 **Stage 2 최초 커밋(#339)부터 동일**했으므로 원래 서술이 옳았고 "정정"이 오정정이었다.
   >
   > ⚠️ **교훈**: 벽시계가 길어진 것을 보고 **원인을 추론해 문서를 고쳤다.** 의존 그래프를 안 봤다.
   > 근거를 확인하지 않은 정정은 원래 서술보다 나쁘다 — 틀린 데다 "검증됨" 딱지가 붙는다.
   >
   > **실측 왕복(08-07, Stage 3b 검증 세션)**: apply 8m27s · destroy 6m21s ·
   > 재구축 apply 9m40s. 전체 세션 97분(디버깅 포함).
2. **사전 점검** (전부 무료):
   ```bash
   command -v tofu kubectl aws            # 도구 존재
   aws sts get-caller-identity            # 자격증명
   aws eks describe-cluster-versions --region ap-northeast-2 \
     --query 'clusterVersions[?status==`STANDARD_SUPPORT`].[clusterVersion,endOfStandardSupportDate]' --output table
   ```
   크레딧 잔여도 확인(만료 2027-01-15, 안전예비 $30 규칙).
2b. 🔴 **ECR 이미지가 관측 기본값 수정을 포함하는지 확인** (무료, 30초).
   `secrets.tf`가 더 이상 `GRAFANA_*` 자리표시를 주입하지 않으므로, **그 수정 이전에 빌드된
   이미지는 부팅 자체가 실패**한다 — `PlaceholderResolutionException: ... 'GRAFANA_API_KEY'`.
   "왜 CrashLoopBackOff지"를 클러스터 안에서 디버깅하는 건 과금 구간에서 가장 비싼 실수다.
   ```bash
   git fetch origin   # 이미지 태그 커밋이 로컬에 있어야 한다
   F=be/support/monitoring/src/main/kotlin/com/devquest/monitoring/OtlpMetricsConfig.kt
   SHA=$(aws ecr describe-images --repository-name devquest/core-api --region ap-northeast-2 \
     --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags' --output json \
     | ruby -rjson -e 'puts JSON.parse(STDIN.read).find{|t| t =~ /\A[0-9a-f]{40}\z/}')

   if [ -z "$SHA" ]; then
     echo "🔴 판정 불가 — ECR 태그를 못 읽었다(자격증명·ruby·태그 형식 확인). 수동 확인 후 진행할 것"
   elif git show "$SHA:$F" 2>/dev/null | grep -q 'GRAFANA_API_KEY:}'; then
     echo "✅ OK — $SHA"
   else
     echo "🔴 재빌드 필요 — GitHub Actions > ECR Push > workflow_dispatch (service: core-api)"
   fi
   ```
   > 날짜·푸시시각 비교가 아니라 **그 커밋의 소스를 직접 읽는다.** 태그가 곧 커밋이므로
   > `git show <sha>:<path>`로 "이 이미지 안의 코드가 실제로 어떤가"를 확인할 수 있다.
   >
   > 🔴 **`[ -z "$SHA" ]` 가드를 지우지 마라.** 이 검사는 처음 작성했을 때 정확히
   > **"통과했다고 믿게 만드는 검사"** 였다(QA F-1에서 재현). `SHA`가 비면
   > `git show "$SHA:$F"`가 `git show ":$F"`로 해석되는데, 이건 **로컬 인덱스의 파일**을
   > 읽는 유효한 문법이다 — ECR과 아무 상관 없이 네 워킹트리를 보고 `exit 0`으로 ✅를 낸다.
   > 즉 aws 호출이 실패한 상황에서 가장 위험한 방향으로 조용히 통과한다.
   > **"판정 불가"와 "재빌드 필요"를 구분해 둔 것도 의도적이다** — 둘 다 멈추라는 뜻이지만
   > 해야 할 행동이 다르다(자격증명 고치기 vs 이미지 굽기).
   >
   > ⚠️ **`aws`·`ruby`의 stderr를 `2>/dev/null`로 죽이지 마라.** 한 번 그렇게 고쳤다가
   > QA F-3에 걸렸다. `set -e` 아래에서는 `SHA=$(... | ruby ...)` 대입문 자체가 파이프라인
   > 실패로 스크립트를 즉시 끝내는데, stderr까지 막으면 **"판정 불가"조차 못 찍고 무출력으로
   > 죽는다.** 에러 원문이 지저분해 보여도 그게 유일하게 남는 신호다.
   >
   > 📌 이 블록은 **사람이 터미널에 붙여넣는 체크리스트**다(그래서 `set -e`가 없다).
   > 스크립트로 옮기려면 SHA 대입 실패를 명시적으로 처리할 것 — 그대로 복사하지 말 것.
   >
   > ✅ **2026-10-19 부터 아래 상황은 생기지 않는다** (📌D-016 순서표 2): PR 은 빌드만 하고
   > 푸시하지 않으며, ECR 엔 main 커밋 이미지만 들어간다(IMMUTABLE · `latest` 없음).
   > 아래는 그 이전 이미지가 남아 있을 때의 해석과, 이 정책이 왜 생겼는지의 기록이다.
   >
   > 🔎 **`🔴 재빌드 필요`가 자주 뜨던 이유 — 오탐이 아니라 정책이었다.**
   > `ecr-push.yml`은 `be/**`가 바뀐 **PR에서도** 빌드했는데, `pull_request` 컨텍스트의
   > `github.sha`는 **머지 커밋**이라 레포 히스토리에 없다. 실측(PR #355):
   > ```
   > ECR 최신 태그  6f6c0932…   부모 = 9692256(main) + 8dd4a0e(브랜치)  → 머지 커밋
   > git cat-file   fatal: could not get object info                  → 로컬에 없음
   > ```
   > 이 이미지는 수정을 담고 있는데도 검사는 🔴를 낸다. **그대로 둔다.**
   > 이 트랙의 산출물은 *"처음 하는 사람이 그대로 따라 할 수 있는 문서"* 이고, 그러려면
   > **지금 도는 이미지가 어느 커밋인지 특정 가능해야** 한다. 조회조차 안 되는 태그로
   > 유료 세션을 돌리면 나중에 "그때 뭐가 돌았더라"에 답할 수 없다.
   > → 🔴가 뜨면 **main에서 `ECR Push`를 `workflow_dispatch`로 한 번 굽는다.** 태그가
   >   main 커밋이 되어 ✅로 바뀌고, 그 자체가 "배포된 이미지 = main의 상태"를 보장한다.
   >
   > ⚠️ `git fetch origin '+refs/pull/*/merge:...'`로 머지 커밋까지 끌어와 판정을 통과시키는
   > 우회는 **일부러 택하지 않았다.** 판정은 통과하지만 추적성은 그대로 없고,
   > 머지된 PR의 merge ref는 GitHub이 정리할 수 있어 조용한 실패 경로가 하나 더 생긴다.
3. **일지 시작 기록** — `docs/eks-migration-log.md`에 세션 시작 시각 append.
4. `tofu init && tofu plan` — 리소스 하나씩 해설 + 비용 영향 + **사용자 승인 게이트**.

## 🔴 과금 구간

> ## 🔴🔴 이 구간에서 **사용자에게 질문하지 않는다** (2026-09-06, $0.39 손실로 확정)
>
> 범위·방향 결정은 **`apply` 전에 전부 끝낸다.** 세션 중에 새 선택지가 보이면
> **먼저 destroy 하고 묻는다.**
>
> | | |
> |---|---|
> | 재apply + destroy 왕복 | 9분 + 10분 = **19분 ≈ $0.059** |
> | **대기 손익분기** | **19분** |
> | 사람이 19분 안에 답할 확률 | 낮다. 대기 중인 사람이 아니다 |
>
> **실제 사고 (09-06)**: 측정을 21분에 끝내고 *"2대 검증까지 갈까요?"* 를 물었다.
> 답은 **127분 뒤**에 왔고 그동안 3노드가 계속 돌았다. 예상 $0.10 → **실제 $0.4622**,
> 그중 **85%가 아무것도 안 한 시간**. 리퍼가 13:36 에 자동 destroy 해서 멈췄다
> (= 안전장치는 성공, **절차가 실패**).
>
> 🔑 09-04 원칙 *"과금 구간에서 무엇을 **하지 않을지**가 비용을 정한다"* 의 두 번째 면이다.
> 그때는 **진단**을 과금 전으로 뺐고, 이번엔 **의사결정**을 빼야 한다는 것이 드러났다.
>
> ⚠️ **불가피하게 물어야 하면 질문과 동시에 destroy 를 건다.** 질문을 남겨두고 클러스터를
> 살려두는 조합이 가장 비싸다.

5. `tofu apply` ← **과금 시작.** (`eks-session-marker.sh` 훅이 세션 마커 생성 → 리퍼 감시 개시)

   🔴 **`tofu -chdir=… apply` / `tofu -chdir=… destroy` 형태로 치지 마라 (2026-10-14, Stage 5d 실측).**
   반드시 `cd infra/aws-eks/2-cluster && tofu apply …` 처럼 **`tofu` 바로 뒤에 하위 명령**이 오게 친다.
   | 무엇이 걸리는가 | 패턴 | `-chdir` 를 끼우면 |
   |---|---|---|
   | 사람 승인 게이트 | settings `ask`: `Bash(tofu apply:*)` · `Bash(tofu destroy:*)` — 접두 일치 | **안 걸린다** → 승인 창 없이 auto mode 분류기가 판정 |
   | 세션 마커(dead man's switch) | `eks-session-marker.sh`: `tofu[[:space:]]+apply` | **안 걸린다** → 마커 없이 과금 |

   실제로 일어난 일: apply 는 분류기가 통과시켜 **승인 창 없이** 돌았고 마커도 안 생겼다. 이어서 kubectl 과
   destroy 를 분류기가 거부(`[Protected-Scope IaC Apply]`)해서 **켠 쪽은 끄지 못하는** 상태가 됐다.
   `cd … && tofu destroy` 로 다시 치자 정상 경로(ask)로 돌았다. 22분 · 약 $0.07 소모, 검증 0건.
   ⚠️ 가드는 고치지 않는다(하네스 동결) — **명령 형태를 지키는 것**으로 막는다. `tofu plan` 은 어느 쪽이든 무방.

   🔴 **`cd … && tofu apply` 로 쳐도 마커가 안 생겼다 (2026-10-15 실측 — 현상은 확정, 원인은 🟡 추정).**
   마커 훅은 settings 에 **상대경로**(`.claude/scripts/eks-session-marker.sh`)로 등록돼 있고, 앞선 호출의 `cd` 로
   셸 작업 폴더가 `2-cluster` 에 남아 있었다. 정규식은 매칭되므로 *"훅 파일을 못 찾아 아예 안 돌았다"* 고 본다
   (훅 stderr 로 대조하지 않았다). destroy 뒤 마커 청소도 안 돌았다.
   ⚠️ **범위 미확인**: 이 원인이 맞다면 같은 방식으로 등록된 **다른 가드 훅도 `cd` 뒤 꺼질 수 있다** — 원장 L-70.
   → **아래 `cat` 확인이 유일한 방어선이다 — 파일 존재(`ls`)가 아니라 `applied_at_h` 가 방금 시각인지 본다.**
   destroy 뒤 청소 훅도 같은 이유로 안 돌 수 있어 **옛 마커가 남으면 다음 세션의 `ls` 확인이 거짓 통과한다**
   (5d 퀴즈 Q3-2). 없거나 옛 시각이면 손으로 만들고, destroy 뒤엔 손으로 지운다.

   🔴 **`apply` 도 `destroy` 도 배경 프로세스로 보내지 마라 (2026-09-16 추가).**
   | | |
   |---|---|
   | `apply` 를 배경으로 돌리면 | **마커가 안 생겼다** — dead man's switch 가 꺼진 채 과금이 시작된다(원장 L-58). 정규식은 매칭되는데 마커가 없었다 |
   | `destroy` 를 배경으로 돌리면 | 프로세스가 중단되면 **아무도 모른다.** 09-16 에 15.8시간이 샜다(§8b) |

   → **둘 다 전경에서 끝까지 붙어 있는다.** 실측 왕복은 apply ~9분 · destroy 4~8분이다.
   도구 타임아웃에 걸렸다면 *"곧 끝난다"* 가 아니라 **경로가 막혔다는 신호**로 읽고,
   배경에 맡기지 말고 다시 전경에서 붙어 **AWS 에 직접 물어** 상태를 확정한다.

   ⚠️ **마커가 생겼는지 육안 확인한다** — 이게 유일한 확인 지점이다:
   ```bash
   cat .claude/eks-session/active     # repo_root / cluster_dir / applied_at 이 보여야 한다
   ```
   없으면 손으로 만든다(형식은 `eks-session-marker.sh` 참조). **마커 없이 세션을 진행하지 마라.**
6. `aws eks update-kubeconfig --name devquest-eks --region ap-northeast-2` → `kubectl get nodes` 검증.
6b. 🔴 **in-cluster 모드라면 — postgres 파드가 Ready된 직후 비밀번호를 동기화한다.**
   > **Stage 5e 부터는 사람이 치지 않는다** — `postgres-static.yaml` 의 **postStart 훅**이 컨테이너가 뜰 때마다
   > 같은 `ALTER USER` 를 한다(손 경로 · ArgoCD 경로 공통). *"항상 한다"* 는 아래 성질은 그대로이고, 하는 주체가
   > 사람에서 kubelet 으로 바뀌었다. 실패하면 이벤트 `FailedPostStartHook` + 컨테이너 재시작으로 **시끄럽게** 드러난다.
   > ⚠️ **아래 파드 IP 검증은 계속 한다** — 동기화를 자동화한 것이지, 확인을 자동화한 것이 아니다.
   > 아래 수동 명령은 postStart 가 없던 이미지·매니페스트로 되돌아갔을 때의 예비 경로로 남긴다.
   ```bash
   PW=$(kubectl get secret core-api-db -o jsonpath='{.data.DB_PASSWORD}' | base64 -d)
   kubectl exec postgres-0 -- psql -U devquest -d devquest \
     -c "ALTER USER devquest PASSWORD '$PW';"     # → ALTER ROLE
   ```
   **왜 필요한가**: postgres 이미지는 `POSTGRES_PASSWORD`를 **`initdb` 시점에만** 쓴다.
   영속 볼륨에 이미 데이터 디렉토리가 있으면 그 변수를 **읽지도 않고** 옛 해시를 유지한다.
   선언된 상태(Secrets Manager)와 실제 상태(볼륨)가 갈라지면 앱이 이렇게 죽는다:
   ```
   FATAL:  password authentication failed for user "devquest"
   ```
   🔴 **관측을 근거로 생략하지 마라 (2026-09-16 추가 — 실제로 생략했다).**
   *"`core-api` 가 `1/1 Running` 이니 DB 인증이 통했다"* 는 추론은 **맞았지만**, 그게 맞는 것은
   **#374(readiness 가 DB 를 실제로 검증) 이후뿐**이다. 그 전에는 DB 가 깨진 채로도 `1/1 Running`
   이었다(원장 L-15 가 지적한 내용). 즉 **판단이 맞아도 맞는 이유가 시점 의존적이다.**
   🔑 그리고 아래 *"왜 매번 하는가"* 의 논지는 **"필요하니 해라" 가 아니라 "조건부로 만들면
   잊힌다"** 다 — 관측으로 조건을 만드는 것 자체가 이 단계의 설계를 훼손한다.
   ***멱등하고 2초인 절차를 건너뛰어 아끼는 것은 2초고, 잃는 것은 "항상 한다"는 성질이다.***

   **왜 매번 하는가 (한 번이 아니라)**: 같은 값으로 다시 걸면 무의미하므로 **멱등**이고,
   2초면 끝난다. "한 번만 하면 되는 수동 절차"는 반드시 잊힌다 — 그리고 잊힌 걸
   **과금 중에** 알게 된다(08-07에 7분을 태웠다). 표준 절차로 두면 이 실패 종류가 사라진다.

   > 08-08 이후 비밀번호는 `0-bootstrap`이 소유해 **세션을 넘어 유지된다**(원장 L-14).
   > 그래도 이 단계는 남긴다 — **08-07 이전에 구워진 볼륨은 어느 state에도 없는 옛 비밀번호를
   > 들고 있고**, 앞으로 비밀번호를 회전시킬 때도 같은 갈라짐이 생긴다.
   > 컨테이너 안 로컬 소켓은 `trust` 인증이라 **옛 비밀번호 없이도** 이 명령이 통한다.
   >
   > 🔴 **동기화됐는지 확인할 때 `-h 127.0.0.1`을 쓰지 마라 — 항상 거짓 합격한다.**
   > `pg_hba.conf` 실측(08-12):
   > ```
   > local  all all                trust
   > host   all all 127.0.0.1/32   trust          ← 소켓뿐 아니라 루프백 TCP도 trust다
   > host   all all ::1/128        trust
   > host   all all all            scram-sha-256  ← 비밀번호를 실제로 검사하는 유일한 줄
   > ```
   > *"소켓은 trust"* 만 알고 **"그러므로 TCP는 검사된다"** 로 추론하면 틀린다(08-12에 실제로 밟았다).
   > 검증은 **파드 IP나 다른 파드에서** 붙어야 `scram-sha-256` 줄을 탄다:
   > ```bash
   > PODIP=$(kubectl get pod postgres-0 -o jsonpath='{.status.podIP}')
   > kubectl get secret core-api-db -o jsonpath='{.data.DB_PASSWORD}' | base64 -d | \
   >   kubectl exec -i postgres-0 -- sh -c "read -r PW; PGPASSWORD=\"\$PW\" \
   >     psql -h $PODIP -U devquest -d devquest -tAc 'select 1'"
   > ```
   > → `1`이면 동기화 성공. `FATAL: password authentication failed`면 아직 갈라져 있다.

7. 실습 목표 수행. **일지 실시간 기록**(에러 원문·해결·비용·결정).
   - 매 턴 Stop 훅이 하트비트를 갱신 → "사람이 활동 중"이라 리퍼가 안 죽인다.

## 🟢 종료 (과금 OFF)

8. **teardown 순서 엄수** (Stage 1+ ALB/PVC 생기면 필수):
   ```bash
   # ⓪ 🔴 ArgoCD Application 이 있으면 **그것부터** 지운다 (Stage 5e~)
   #    selfHeal 이 켜져 있어서 아래 ①을 먼저 치면 ArgoCD 가 **1~2초 만에 되살린다**
   #    (5c 실측: 손으로 지운 Deployment 가 그 시간에 부활). 5e 부터 ExternalSecret 도 ArgoCD 소유다.
   #    Application 에는 finalizer 가 없으므로, 지우면 **리소스는 그대로 남고 관리만 끊긴다** —
   #    그 뒤로는 기존 절차가 그대로 통한다. postgres 5종은 `Delete=false` 라 어차피 안 지워진다.
   kubectl -n argocd delete application devquest-apps --ignore-not-found
   kubectl -n argocd get application        # → "No resources found" 여야 한다

   # ① ESO가 관리하는 Secret부터 (Stage 2~)
   #    ⚠️ `kubectl delete secret core-api-db`만 하면 ESO가 **즉시 재생성**한다.
   #       소유자인 ExternalSecret을 먼저 지워야 한다(creationPolicy: Owner).
   #    ✅ 07-28 실측으로 확인: 삭제 후 8초 만에 부활, UID가 바뀌어 있었다
   #       (94fe931e-… → db35e78b-… = 같은 이름의 다른 객체).
   #       반대로 ExternalSecret을 지우면 K8s Secret도 함께 사라진다(소유자 GC).
   kubectl delete externalsecret --all -A
   kubectl delete secretstore --all -A

   # ② K8s가 만든 ALB·EBS 회수 (state 밖 고아 방지)
   # 🔴 **삭제 명령을 친 것과 ALB 가 사라진 것은 다르다 (Stage 4 에서 추가).**
   #   ALB Ingress 에는 finalizer 가 붙는다 — LBC 컨트롤러가 실제 ALB 를 지운 뒤에야
   #   오브젝트가 사라진다. 컨트롤러 파드가 먼저 죽었거나 evict 됐으면 이 명령은
   #   **영구 hang** 하고, 사람은 Ctrl-C 하고 다음 단계로 넘어간다 → **ALB 고아.**
   #   → 타임아웃을 걸고, **완료를 별도로 확인**한다.
   kubectl delete ingress --all -A --timeout=180s

   # 완료 확인 ①: 오브젝트가 비었나
   kubectl get ingress -A          # → "No resources found" 여야 한다

   # 완료 확인 ②: 🔴 **AWS 에 직접 묻는다.** K8s 가 "지웠다"고 해도 실물이 남을 수 있다.
   aws elbv2 describe-load-balancers --region ap-northeast-2 \
     --query 'length(LoadBalancers)' --output text     # → 0 이어야 한다
   #   0 이 아니면 **여기서 멈춘다.** destroy 로 넘어가면 두 가지가 겹친다:
   #     ① ALB 가 고아로 남아 월 ~$16.43 + 퍼블릭 IP $7.30
   #     ② LBC 가 만든 SG 를 참조하는 규칙 때문에 `DependencyViolation` 으로 destroy 실패
   #   수동 삭제: aws elbv2 delete-load-balancer --region ap-northeast-2 --load-balancer-arn <ARN>
   #
   # 🔴 PVC는 **Stage에 따라 다르다.** 감각으로 옮기면 사고가 난다.
   #   Stage 3a (동적 PVC): `kubectl delete pvc --all -A` **필수.**
   #       안 하면 CSI가 만든 EBS가 tofu state 밖에 고아로 남아 계속 과금된다.
   #   Stage 3b (static PV): **하지 않는 것을 권장.**
   #       볼륨은 terraform(0-bootstrap) 소유라 destroy 대상이 아니고, reclaimPolicy가
   #       Retain이며 IAM이 CSI의 삭제를 거부한다 — 즉 지워도 볼륨은 안 사라진다.
   #       대신 PV가 `Released` + claimRef 잔존 상태가 되어, 다음 세션에 재적용할 때
   #       실패 6종 ④(PV가 새 PVC를 안 받음)를 밟는다.
   #       → **PVC를 남긴 채 클러스터를 destroy**하면 클러스터와 함께 깨끗이 사라진다.
   kubectl get pvc -A     # 무엇이 있는지 확인하고 Stage에 맞게 판단
   # 콘솔/CLI로 ALB 사라졌나 확인

   # ③ 인프라  🔴 **여기서 승인 창이 뜬다** (§시작 1). 과금 구간에서 질문은 금지지만 이 승인은
   #    피할 수 없다 — 그래서 apply 전에 시각을 약속받아 둔다. 나눠 치면 창도 그만큼 뜬다.
   cd infra/aws-eks/2-cluster && tofu destroy
   ```

8b. 🔴🔴 **destroy 완료 게이트 — "쳤다"와 "끝났다"는 다르다 (2026-09-16, $2.8 손실로 추가).**

   **`tofu destroy` 를 실행한 것으로 세션을 끝내지 마라.** 이 SOP 는 §8 ②에서 ALB 에 대해
   정확히 그 구분을 해뒀다(*"삭제 명령을 친 것과 ALB 가 사라진 것은 다르다"*) — 그런데
   **destroy 자체에는 안 해뒀다.** 09-16 에 그 구멍으로 **15.8시간**이 샜다.

   ```bash
   R=ap-northeast-2
   aws ec2 describe-instances --region $R \
     --filters Name=instance-state-name,Values=running,pending \
     --query "length(Reservations[].Instances[?!(Tags[?Key=='Role' && Value=='prod-db'])][])" --output text    # → 0
   aws eks list-clusters --region $R --query 'length(clusters)' --output text   # → 0
   ```
   > 📌 **prod DB(`Role=prod-db`, 1-network — 📌D-016 순서표 5)는 세지 않는다** (2026-10-20). 상시 인스턴스라
   > 예전 쿼리(필터 없음)로는 이 게이트가 **영원히 0 이 안 된다** → 매번 실패하는 검사는 곧 눈으로 넘기게 되고,
   > 최악은 *"0 을 만들려고"* prod DB 를 종료하는 것이다(Blindspot B5-13). 같은 이유로 EBS 고아 검사가
   > 영속 볼륨을 `Persistent=true` 로 거르는 것과 같은 모양이다.
   > 확인: `aws ec2 describe-instances --region $R --filters Name=tag:Role,Values=prod-db Name=instance-state-name,Values=running --query 'length(Reservations[])' --output text` → `db_enabled` 이면 1.

   🔴 **이 두 숫자가 0 인 것을 눈으로 본 뒤에 세션을 닫는다.** `tofu destroy` 의 출력이 아니라
   **AWS 의 답**으로 판정한다. state 는 destroy 가 성공했다고 믿을 수 있지만 AWS 는 못 속인다.

   > **09-16 사고 경위**: `tofu destroy` 가 로컬 도구의 600초 타임아웃에 걸려 **배경 프로세스로
   > 전환**됐고, 그 프로세스가 밤새 중단됐다. 노드는 **다음 날 09:12 에야** 종료됐다.
   > ```
   > i-0c54bea8  2026-09-16T07:07:59Z → 2026-09-17T00:12:55Z   17.08h
   > i-09dffc2e  2026-09-16T08:10:50Z → 2026-09-17T00:16:57Z   16.10h
   > i-0062dcc5  2026-09-16T08:10:50Z → 2026-09-17T00:12:55Z   16.03h
   > 컨트롤플레인 17.34h → 예상 $0.13 대비 실제 $2.77~3.14 (21~24배)
   > ```
   > 🔑 **리퍼는 이 경우를 못 막는다** — launchd 는 머신이 자면 안 돌고(§안전장치 한계),
   > 게다가 하트비트 2h stale 을 기다린다. **장시간 destroy 를 배경에 두는 것은
   > dead man's switch 를 우회하는 행위다.**
   >
   > ⚠️ **destroy 를 절대 배경으로 보내지 마라.** 실측 왕복은 4~8분이고 타임아웃이 걸리면
   > 그건 *"곧 끝난다"* 가 아니라 **경로가 막혔다는 신호**다. 타임아웃이 났으면 배경에 맡기지 말고
   > **다시 전경에서 붙어** 위 두 숫자가 0 이 되는 것을 확인한다.

   > ESO 자체(Helm 릴리스)는 클러스터와 함께 사라지므로 별도 삭제 불필요.
9. **고아 전수 검증** = 0:
   ```bash
   R=ap-northeast-2
   tofu state list                                   # 비어야 함
   aws eks list-clusters --region $R                 # 비어야 함
   # 🔴 합격 기준 = **0건**. (Stage 4 이후로도 마찬가지 — ALB 는 세션 리소스다.
   #   영속 EBS 처럼 "남아 있는 게 정상"인 로드밸런서는 이 레포에 없다.)
   aws elbv2 describe-load-balancers --region $R --query 'length(LoadBalancers)' --output text
   aws ec2 describe-nat-gateways --region $R --filter Name=state,Values=available

   # ── EBS: "available" 전부가 고아는 아니다 (영속 볼륨 도입 이후) ──
   # 🔴 영속 EBS는 destroy 후 **영원히 available 상태**로 남는 것이 정상이다.
   #    옛 쿼리(Volumes[].VolumeId, 필터 없음)를 그대로 쓰면 매 세션 "고아 발견"이 뜬다.
   #    합격 기준이 "전부 0건"이므로 **매번 실패하는 검사**가 되고, 매번 실패하는 검사는
   #    곧 눈으로 넘기게 된다 → 그때부터 진짜 고아도 안 보인다.
   #    → 고아 검사와 영속 인벤토리를 **성격이 다른 두 검사**로 가른다.
   aws ec2 describe-volumes --region $R --filters Name=status,Values=available \
     --query "Volumes[?!(Tags[?Key=='Persistent' && Value=='true'])].[VolumeId,Size,CreateTime]" \
     --output table

   # ── RDS 계열 (Stage 2에서 추가) ──
   # ⚠️ 인스턴스가 사라져도 **스냅샷은 남아 계속 과금**된다. 가장 놓치기 쉬운 고아다.
   aws rds describe-db-instances --region $R --query 'DBInstances[].DBInstanceIdentifier'
   aws rds describe-db-snapshots --region $R --snapshot-type manual   --query 'DBSnapshots[].DBSnapshotIdentifier'
   aws rds describe-db-snapshots --region $R --snapshot-type automated --query 'DBSnapshots[].DBSnapshotIdentifier'
   aws rds describe-db-subnet-groups --region $R --query 'DBSubnetGroups[].DBSubnetGroupName'

   # ── Secrets Manager ──
   # 🔴 --include-planned-deletion 없이 조회하면 "삭제 대기" 상태 시크릿이 안 보인다.
   #    복구창(기본 30일) 동안 시크릿당 $0.40/월이 계속 과금되고, 이름이 점유돼
   #    다음 세션 apply가 InvalidRequestException으로 실패한다.
   #    (그래서 코드에서 recovery_window_in_days = 0 으로 둔다 — secrets.tf ⑨)
   aws secretsmanager list-secrets --region $R --include-planned-deletion \
     --query 'SecretList[].{Name:Name,DeletedDate:DeletedDate}'

   # ── CloudWatch 로그 그룹 (Stage 5d에서 추가) ──
   # 🔴 클러스터 쪽이 **스스로 만든** 그룹은 tofu state 밖이라 destroy 가 못 지우고, 보존이 기본 무기한이다.
   #    후보: EKS 컨트롤플레인 로깅(`/aws/eks/…`) · Container Insights · aws-for-fluent-bit 차트 기본 이름.
   #    5d 수집기는 IRSA 에 CreateLogGroup 이 없어 만들 수 없게 해 뒀다 — 이 검사는 그 장치가 맞는지 재는 것이다.
   #    합격 기준 = **0건**. 학습용 그룹 2개(`/devquest/eks-learning/…`)는 영속이라 여기 아니라 9b 에서 본다.
   #    Stage 5f: `/aws/eks/devquest-eks/cluster` 는 0-bootstrap 이 **미리 만든** 영속 그룹이라 쿼리에서 뺀다(9b 에서 본다).
   #    그 밖의 `/aws/eks/…` 가 나오면 클러스터 이름이 어긋난 채 로깅이 켜져 EKS 가 스스로 만든 고아다.
   aws logs describe-log-groups --region $R \
     --query "logGroups[?(starts_with(logGroupName, '/aws/eks/') && logGroupName != '/aws/eks/devquest-eks/cluster') || starts_with(logGroupName, '/aws/containerinsights/')].logGroupName"
   ```

   > ✅ **실측 완료(07-28)**: RDS가 `manage_master_user_password`로 만든 마스터 시크릿(`rds!db-...`)은
   > **인스턴스 삭제와 함께 완전히 정리된다.** teardown 후 `--include-planned-deletion`으로 조회해도
   > 빈 결과 — 복구창 좀비도, 이름 점유도 남지 않는다. (우리가 만든 시크릿 2개는
   > `recovery_window_in_days = 0` 덕분에 즉시 소멸)
   >
   > 📌 **07-28 teardown 실측: 위 항목 전부 0건 = 고아 없음.** destroy 4분 34초(26 destroyed).
   > 단계별: 노드그룹 2분 16초 · **RDS 3분 53초** · 컨트롤플레인 2분 9초.
9b. **영속 인벤토리 대조** — 합격 기준이 위와 **정반대**다. 0건이 아니라 **원장과 일치**:
   ```bash
   aws ec2 describe-volumes --region $R --filters Name=tag:Persistent,Values=true \
     --query 'Volumes[].[VolumeId,Size,AvailabilityZone,State]' --output table
   # Stage 5d — 학습용 로그 그룹: 정확히 2개(containers · events), 둘 다 보존 30
   aws logs describe-log-groups --region $R --log-group-name-prefix /devquest/eks-learning/ \
     --query 'logGroups[].[logGroupName,retentionInDays,storedBytes]' --output table
   ```
   > 📖 기대값은 **`infra/aws-eks/PERSISTENT-RESOURCES.md`** 의 표다. 개수·크기·AZ가 어긋나면
   > ①모르는 사이 볼륨이 늘었거나 ②원장 갱신을 빠뜨린 것 — **둘 다 즉시 확인 대상**이다.
   >
   > ⚠️ **9와 9b는 반대 방향의 검사다.** 9는 "없어야 한다", 9b는 "정확히 이만큼 있어야 한다".
   > 하나로 합치면 어느 쪽 기준으로 읽어야 할지 모호해져 둘 다 무력해진다.

10. 일지에 destroy 시각·총 과금 시간·비용 결산 append.
11. **구축 후 이해도 퀴즈** — 학습 마일스톤(`stage/eks-*` 브랜치)이면 `quiz.md`로 진행,
    `docs/eks-quizzes/<브랜치>.md` + `<!-- QUIZ-PASSED -->`. (`assert-eks-quiz.sh`가 PR 차단으로 강제)
12. PR → 머지 → CONTEXT 정리.

---

## 🔒 안전장치 — "끄는 걸 잊음"을 기계로 막기 (dead man's switch)

사람 규율만으로는 "apply하고 destroy 잊고 세션 종료"를 못 막는다. 그래서 로컬 리퍼를 둔다.

| 조각 | 파일 | 역할 |
|------|------|------|
| 마커 | `eks-session-marker.sh` (PreToolUse) | `tofu apply` 감지 → `.claude/eks-session/active` 생성 |
| 하트비트+리마인더 | `eks-heartbeat-reminder.sh` (Stop) | 매 턴 하트비트 touch("사람 활동 중") + **과금 리소스**(EKS·RDS) 살아있으면 경고 + 전부 없으면 마커 자가청소 |
| 리퍼 | `eks-reaper.sh` (launchd 30분) | 마커 있고 **하트비트 2h stale**(=사람 사라짐)이면 → 실제 리소스 확인 후 `tofu destroy` |

> 🔴 **생존 판정은 EKS·RDS의 OR이다 (Stage 2에서 교정).** 클러스터만 보면 "EKS는 지워졌는데
> RDS는 남은" 부분 실패 상태에서 **마커를 자가 삭제해 감시를 끝내버린다** → RDS 영구 과금.
> 두 스크립트가 같은 기준을 쓰도록 유지할 것. 목 주입 테스트로 회귀 검증됨.

> 🔴 **리퍼는 "이 세션이 만든 클러스터" 만 destroy 한다 (📌D-016 순서표 4번, 2026-10-20).**
> D-016 이후 같은 이름·같은 state 의 클러스터를 **CI 가 평일 창 동안 prod 로** 켠다. 그런데 마커는
> 1-network apply(N-4b)·따옴표 안 오탐에도 생긴다 — 종전엔 *"클러스터가 없으면 마커가 자가청소된다"* 가
> 그 오탐을 무해하게 만들었는데, 창 동안엔 클러스터가 있으니 그 근거가 무너진다. 그대로면
> **오탐 마커 + 2h 공백 → 리퍼가 Fly 복귀 없이 prod 를 destroy** 한다.
>
> | 판정 (`.claude/scripts/lib/eks-cluster-owner.sh`) | 조건 | 리퍼 | 리마인더·session-status |
> |---|---|---|---|
> | **mine** | 클러스터 `createdAt` ∈ [마커 `applied_at` −30s, +1h] | 종전대로 Ingress 정리 → destroy | 종전대로 destroy 안내 |
> | **foreign** | 그 밖(먼저 생겼거나 한참 뒤) = CI 창·다른 기기 | **destroy 안 함**, 마커 해제 | *"끄지 마라 — off 워크플로 몫"* |
> | **unknown** | `applied_at` 없음·`describe-cluster` 실패·시각 해석 실패 | **destroy 안 함**, 마커 유지·재시도(rc 1) | 판정 불가 경고 + 확인 명령 |
>
> - 실패 방향: foreign/unknown 오판의 대가는 학습 클러스터 과금, mine 오판의 대가는 **prod 장애**다 → 판정 못 하면 안 지운다.
>   ⚠️ **8번 전에는 CI 창이 없다** — 그 구간에 foreign 이 나오면 로컬 잔존물·오판일 수 있으니 `createdAt` vs `applied_at_h` 를
>   확인하고, 내 것이면 §8 로 손으로 destroy. *"8번 off cron 이 2차 백스톱"* 도 8번 **이후**의 이야기다.
> - 🔴 **남은 mine 오판 경로 (원장 등재 → 8번)**: 마커가 생긴 뒤 Stop 훅이 한 번도 못 돌고(턴 도중 종료) **+1h 안에 CI 가
>   클러스터를 켜면** mine 이 된다. 8번 전엔 CI on 이 없어 성립하지 않는다. 8번이 on 잡의 임대로 닫는다.
> - 클러스터가 여럿이면 **하나라도 foreign 이면 foreign** — 무관한 클러스터가 있으면 내 리퍼도 멈춘다(의도된 fail-safe).
> - `applied_at` 은 마커 훅이 **세션의 첫 apply 시각으로 보존**한다(하트비트가 TTL 안이고 **지금 클러스터가 떠 있을 때만** —
>   클러스터가 없으면 이번 apply 가 새로 만들 것이라 새 시각. 한 턴 안 destroy→재apply 대비, QA F-1). 두 번째 apply 가
>   덮어쓰면 자기 클러스터가 foreign 이 되어 dead man's switch 가 조용히 꺼진다.
> - 마커 정규식은 **조이지 않았다** — L-22(*조일 때마다 새 우회*). 마커가 아니라 destroy 대상을 판정한다.
> - **CI 가 켠 클러스터의 dead man's switch 는 로컬 리퍼가 아니다** — Fly 복귀를 포함한 off 경로를 반복 실행하는
>   워크플로로 **순서표 8번에서** 만든다(로컬 리퍼는 맥이 깨어 있을 때만 돌고, 원격 세션의 마커는 보지도 못한다).
> - 회귀: `bash .claude/scripts/tests/reaper-alb-test.sh` ⑧~⑯ (무과금 목. CI 미편입 — 수동 실행).

**동작 요지**: 네가 Claude로 작업 중이면 매 턴 하트비트가 갱신돼 리퍼가 안 건드린다.
네가 **끝 신호 없이 사라지면** 하트비트가 2시간 뒤 stale → 리퍼가 자동 `tofu destroy` → 과금 종료.
tofu state를 그대로 쓰므로(로컬 실행) 다음에 상태가 깨끗하다.

- **한계**: macOS가 **자면(닫으면)** launchd가 안 돈다 → 깨어날 때 밀린 실행을 따라잡는다.
  그동안(노트북 잠든 시간)은 과금 지속. 최악(주말 방치) ~$6.
  - 🔴 **이 경우의 backstop은 예산이 아니라 이상탐지다.** 예산은 **누적** $10 단위라
    주말 급증 $6은 누적이 다음 계단을 넘지 않는 한 안 울린다(2026-07-31 누적 $0.481 기준
    $6.48 → $10 미도달). 하루 단위 급증을 잡는 것은 **Cost Anomaly Detection(DAILY, $5)** 이다.
    ~~"$35 예산 알람이 backstop"~~ 이라는 종전 서술은 임계 체계와 어긋났다(코드에 $35는 없었다).
- **설치** (새 머신/클론 시 1회): `bash infra/aws-eks/reaper/install-reaper.sh`
  > 📌 **조각이 두 디렉토리에 나뉘어 있다** (2026-09-16 에 헷갈려 오정정을 냈다):
  > · 설치기·plist 템플릿 → `infra/aws-eks/reaper/`   · **리퍼 본체 → `.claude/scripts/eks-reaper.sh`**
  > 드라이런은 본체를 직접 부른다: `EKS_REAPER_DRYRUN=1 bash .claude/scripts/eks-reaper.sh`
- **설정**: `EKS_REAPER_TTL`(기본 7200초=2h), `EKS_REAPER_DRYRUN=1`(테스트), `EKS_REGION`.
- **리퍼가 뭔가 했나 확인**: `.claude/eks-session/reaper.log` (`DEAD MAN'S SWITCH 발동` 있으면 자동 destroy된 것).
- **제거**: `launchctl bootout gui/$(id -u)/com.devquest.eks-reaper ; rm ~/Library/LaunchAgents/com.devquest.eks-reaper.plist`

> ⚠️ `.claude/eks-session/`은 gitignore(머신 로컬·휘발성). 리퍼 스크립트/plist 템플릿만 커밋된다.
