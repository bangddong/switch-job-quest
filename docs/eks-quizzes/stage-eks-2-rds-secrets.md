# EKS 학습 퀴즈 — Stage 2 (RDS + Secrets Manager + IRSA + External Secrets Operator)

- 브랜치: `stage/eks-2-rds-secrets` · 날짜: 2026-07-28 · HEAD: `1370ed6` (퀴즈 시점)
- 재료: `docs/eks-migration-log.md` 2026-07-28 엔트리 (실측 로그 — apply 2회·destroy 1회, 총 과금 26분 35초)

---

## Q1. ESO가 다음 에러를 냈다. 무엇이 잘못된 상태인가?

```
AccessDeniedException: User: arn:aws:sts::<account>:assumed-role/devquest-eks-eso/<session>
is not authorized to perform: secretsmanager:GetSecretValue on resource: devquest-eks/app
because no identity-based policy allows the secretsmanager:GetSecretValue action
```

- **사용자 답**: 인증 실패 — 신뢰정책 sub 불일치
- **정오**: ❌ (정답: **인가 실패 — 권한 정책 문제**)
- **근거**: 에러의 주체가 `assumed-role/devquest-eks-eso/<session>`으로 찍혔다는 것은
  STS가 **이미 그 롤로 임시 자격증명을 발급했다**는 뜻 = `AssumeRoleWithWebIdentity` 성공 =
  신뢰정책의 `sub`/`aud`는 맞았다는 증거다. 같은 순간 `SecretStore` 상태도
  `Ready=True reason=Valid msg=store validated`였다(인증 통과).
  **실증**: 권한 정책(`aws_iam_policy.eso_read_secrets`)은 `aws_db_instance.main.master_user_secret[0].secret_arn`에
  의존해 RDS 생성 후에야 붙는다. 정책이 붙자 **매니페스트를 한 글자도 안 바꿨는데** 동기화가 성공했다.
  신뢰정책은 처음부터 옳았다.

  | 에러 | 층 | 원인 |
  |---|---|---|
  | `Not authorized to perform sts:AssumeRoleWithWebIdentity` | 인증 | 신뢰정책 `sub`/`aud` 불일치 |
  | `AccessDeniedException ... no identity-based policy allows` | 인가 | 권한 정책 미부착/ARN 불일치 |

### Q1 재검토. `Not authorized to perform sts:AssumeRoleWithWebIdentity`를 봤다면 가장 먼저 의심할 곳은?

- **사용자 답**: 신뢰정책의 `sub` 문자열
- **정오**: ✅
- **근거**: `irsa-eso.tf`의 신뢰정책 조건 —
  `<oidc_issuer_host>:sub = system:serviceaccount:external-secrets:external-secrets`.
  실제 파드의 네임스페이스/ServiceAccount와 한 글자라도 다르면 assume 자체가 거부된다.
  이번 세션 실측값(성공 케이스): ESO 파드에 주입된
  `AWS_ROLE_ARN` + `AWS_WEB_IDENTITY_TOKEN_FILE=/var/run/secrets/eks.amazonaws.com/serviceaccount/token`.

## Q2. 보안그룹 description에 한글을 넣어 apply가 실패했다. `tofu validate`·`tofu plan`·`tfsec`이 셋 다 통과시킨 이유는?

- **사용자 답**: 셋 다 AWS API를 호출하지 않기 때문
- **정오**: ✅
- **근거**: 세 도구는 HCL 문법·타입·정책 규칙만 **로컬에서** 검사한다. 값이 AWS API의 제약
  (문자셋·길이·형식)을 만족하는지는 실제 호출 전까지 알 수 없다. 실측 에러:
  ```
  InvalidParameterValue: Value (RDS PostgreSQL - EKS 클러스터 파드에서만 접근 허용)
    for parameter GroupDescription is invalid. Character sets beyond ASCII are not supported.
  ```
  같은 세션에서 IAM도 별도 패턴(`[	
 -~¡-ÿ]*`)으로 거부했다.
  **반면 Secrets Manager·ECR lifecycle은 한글을 그대로 받았다** — "AWS는 한글 불가"가 아니라
  **서비스마다 다르다**. 또한 이 종류의 실패는 **OpenTofu가 apply 종료 시점에 몰아서 출력**하므로
  진행 중에는 "Creating..."에서 멈춘 것처럼 보인다 → 중간 진단은 **CloudTrail 이벤트 원문**으로.
  (`lookup-events`의 요약 `ErrorCode`는 `None`으로 나와 오독을 유발한다)

## Q3. RDS를 `3-data` 같은 별도 레이어로 분리하지 않고 `2-cluster`에 넣은 가장 중요한 이유는?

- **사용자 답**: 리퍼가 2-cluster만 destroy하기 때문
- **정오**: ✅
- **근거**: `.claude/scripts/eks-reaper.sh`는 하드코딩된 `2-cluster` 디렉토리에서 `tofu destroy`를
  실행한다. RDS가 그 밖에 있으면 dead man's switch가 발동해도 **RDS는 살아남아 영구 과금**된다.
  "깔끔한 레이어 분리"보다 **안전장치 사정권**이 우선한 사례.
  (같은 세션에 리퍼·하트비트의 생존 판정도 `list-clusters` 단독 → **EKS OR RDS**로 교정했다.
   교정 전 코드는 "EKS 없음 + RDS 생존" 상태에서 마커를 자가 삭제해 감시를 끊었다 — 목 주입으로 증명.)

## Q4. `tofu destroy`가 성공해도 계속 과금될 수 있는 리소스는?

- **사용자 답**: RDS 스냅샷 + 삭제대기 시크릿
- **정오**: ✅
- **근거**: SOP §9. 그래서 코드로 미리 막아뒀다 —
  `skip_final_snapshot = true`(없으면 destroy가 **에러로 실패**해 리퍼까지 마비),
  `backup_retention_period = 0` + `delete_automated_backups = true`,
  `recovery_window_in_days = 0`(기본 30일 복구창은 시크릿당 $0.40/월 + **이름 점유**로 다음 apply를
  `InvalidRequestException`으로 깨뜨린다).
  **이번 teardown 실측 결과 고아 0건** — 특히 RDS가 `manage_master_user_password`로 만든
  `rds!db-73d7eb86-...` 관리형 시크릿도 **인스턴스 삭제와 함께 완전 정리**됐다(복구창 좀비 없음).
  이는 SOP에 🟡 미검증으로 남아 있던 항목이며 이번에 해소했다.

## 요약

- **맞음 3 / 틀림 1** (Q1) → **Q1 재검토 통과**(인증/인가 층 구분 + 최다 실패 지점 식별).
- 이번 마일스톤에서 해소한 미검증 항목 **5/5**:
  ① kubectl 서버측 스키마 검증 ② ESO CRD `v1`(v1beta1은 served조차 아님) ③ IRSA `sub` 일치
  ④ RDS 관리형 시크릿 자동 정리 ⑤ RDS 생성 4분 50초 / 삭제 3분 53초 (추정 ~10분보다 빠름)
- 예상이 빗나가 **문서를 고친 것 3건**: IAM description 한글 가능(→불가) / RDS·EKS 병렬 생성(→보안그룹
  참조로 직렬화) / ESO CRD 버전 구성.

<!-- QUIZ-PASSED -->
