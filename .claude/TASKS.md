# 미완료 작업

> 🛑 **서비스 종료 (2026-11-06).** 남은 사용자 작업은 TASK-25(외부 키·계정 폐기)와 TASK-6(크레딧 — 후속
> 보일러플레이트 트랙이 같은 AWS 계정을 쓴다) 둘뿐이다. 나머지는 「완료된 항목」으로 종결 처리했다.

### TASK-25: 외부 키·계정 폐기 (사용자, 2026-11-06)

서비스는 내려갔지만 **키는 살아 있다.** 사본은 이미 지웠다(Fly secrets·Worker secrets·SSM `/devquest/prod/*`).
원본을 각 콘솔에서 폐기한다. 상세 위치는 `.claude/docs/secrets-map.md`.

- [ ] **Anthropic** — 키 3개(Fly prod · EKS 전용 · 로컬) 삭제
- [ ] **Judge0 (RapidAPI)** — 구독 해지 또는 키 재발급(폐기)
- [ ] **Resend** — API 키 삭제 · 도메인 `dhbang.co.kr` 제거. Cloudflare DNS 의 `resend._domainkey`(TXT)·`send`(MX·TXT)
      는 Resend 전용이라 같이 지워도 된다(블로그 무관)
- [ ] **Grafana Cloud** — devquest 용 API 키 삭제 (스택 자체는 선택)
- [ ] **Sentry** — 프로젝트 삭제 (BE 사용처 0건이었다)
- [ ] **GitHub OAuth App** — devquest 앱 삭제 (client secret 2개가 같이 사라진다)
- [ ] **GitHub PAT** — EventBridge 용(connection 은 삭제됨) · `COMMIT_PAT` 폐기
- [ ] **Vercel** — `switch-job-quest` 프로젝트 삭제(V-4) · 로컬 키체인 `vercel-token` 폐기
- [ ] **Cloudflare 토큰 2개** — FE 배포 토큰 · 계정 토큰 `devquest-eks-window-dns`(종료 작업 때 Workers 권한을 추가했다)
- [ ] **Fly** — org 토큰 `devquest-shutdown`(만료 선택 UI 가 없어 기본 20년으로 발급됐을 것) 폐기
- [ ] **Neon** — 🔴 **마지막에.** 콜드 대기 DB 가 최종 덤프의 두 번째 사본이다. 로컬 덤프를 다른 곳에 복사한 뒤 프로젝트 삭제
- [ ] 위가 끝나면 Claude 에게 알린다 → SSM `/devquest/ops/*` 3개 삭제

### TASK-6: AWS 크레딧 만료일 캘린더 등록 (사용자, 2026-07-23)

Free Plan 크레딧은 **소진 OR 만료 중 먼저 오는 시점에 계정이 자동 폐쇄**된다(과금이 아니라 폐쇄).
실측 만료일을 캘린더에 박아 놓을 것 — 🔴 **2026-10-21 부터 prod DB 가 이 계정(EC2)에 있다 — 폐쇄 = prod DB 소멸**(탈출 경로 = Neon 콜드 대기, `infra/aws-eks/scripts/prod-db/README.md` 「전환 뒤」). EKS 학습 인프라·S3 tfstate·
ECR도 계정과 함께 사라진다(폐쇄 후 90일 content 보관, Paid 업그레이드 시 복구·잔여 크레딧 이월).

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

## 완료된 항목

### TASK-4 · TASK-7 · TASK-11 → **종결 (2026-11-06, 서비스 종료)**

- TASK-4(bootstrap-admin 액세스키 → OIDC 전환 후 폐기): AWS 계정은 후속 트랙이 계속 쓴다 — 그 트랙에서 판단
- TASK-7(EKS 리퍼 재설치): 클러스터가 다시 생길 일이 없다. 이 맥의 launchd 잡은 종료 작업 중 제거
- TASK-11(앱 사용 피드백): 서비스 종료로 무의미

<details><summary>원문</summary>

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


</details>

### TASK-17: EKS 창용 관측 키를 SSM 에 → **완료 (2026-11-03)**

`/devquest/prod/app/GRAFANA_API_KEY`·`GRAFANA_LOKI_URL`·`GRAFANA_LOKI_INSTANCE_ID` 3개 SecureString(`alias/aws/ssm`). 값 미열람 형식 확인:
API 키 `glc_` 136자 · URL `https://logs-prod-….grafana.net/loki/api/v1/push` 50자(첫 입력은 `/loki/api/v1/push` 누락 → 사용자 수정, v2) · User 숫자 7자리.
`SENTRY_DSN` 은 **넣지 않음** — BE main 에 Sentry 사용처 0건. ⚠️ API 키는 길이(136)가 로컬 `application-local.yml` 의 OTLP 토큰과 같아 그 토큰 재사용으로 보인다(🟡) → `logs:write` 권한 미확인, 첫 창에서 `platform="eks"` 로그 유입으로 확인.

### TASK-24: 일일 작업 트리거 비밀 넣기 → **완료 (2026-11-02)**

사용자가 `job-secret-rotate` rotate 실행(run 41422055724 success). 확인: 헤더 없는 POST → 401, 틀린 값 → `{"status":"unauthorized"}`(머지 직후엔 503 disabled). 실제 발송은 다음 09:00 에 확인.

### TASK-23: JWT 키 교체 실행 → **완료 (2026-10-28)**

> `jwt-rotate.yml` check(읽기 권한·JSON 파싱 첫 실측) → rotate(SSM 되읽기 해시 일치 · Fly import · Fly 200 +10s) → check(digest 일치). 로그 64자 영숫자 0건.

### TASK-22: EKS 창 로그인 키 2개를 SSM 에 → **완료 (2026-10-28)**

> `/devquest/prod/app/GITHUB_CLIENT_ID`(String, FE 값과 일치) · `GITHUB_CLIENT_SECRET`(SecureString `alias/aws/ssm`, 40자 hex — 값 미열람). 사전 점검 함수 `ok`.

### TASK-21: 8-C DNS 토큰에 KV 쓰기 권한 추가 → **완료 (2026-10-27)**

`devquest-eks-window-dns` 에 Account · Workers KV Storage · Edit 추가 → SSM 토큰으로 KV 목록 조회 `success: true`(값 미출력).
권한만 바꿔 토큰 값 불변 🔴 실측(SSM 그대로 동작). FE 배포 토큰에도 같은 권한을 추가(바인딩 배포용, 실효는 머지 후 첫 배포).
✅ FE 배포 토큰 KV 권한 실효 확인(#508 배포, 10-27): `deploy-workers` success + 로그 `env.SWITCH … KV Namespace`.
⚠️ 처음엔 FE 배포 토큰에만 넣어서 8-C 토큰은 `10000` 이었다 — **토큰 두 개가 다르다**(V 계획 「토큰을 둘로 나누는 이유」).

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
