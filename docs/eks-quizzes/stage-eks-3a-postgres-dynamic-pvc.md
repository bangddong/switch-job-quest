# EKS 학습 퀴즈 — Stage 3a (in-cluster Postgres + EBS CSI + 동적 PVC)

- 브랜치: `stage/eks-3a-postgres-dynamic-pvc` · 날짜: 2026-07-30 · HEAD: `47f94c2`
- 재료: `docs/eks-migration-log.md` 07-30 엔트리(실측 로그) + 이 브랜치 diff

> **기록 방침**: 이 파일은 통과 증명서가 아니라 **학습 이력**이다.
> 1차에서 못 푼 것을 지우지 않는다 — 무엇을 몰랐는지가 다음 Stage의 입력이다.

## 1차 (6문항) — 사용자 답: **"다 모르겠다"** (0/6 무응답)

6문항을 한 번에 제시한 것이 과했다. 개념 설명 → 재확인 순서로 전환했다.

| # | 질문 요지 | 결과 |
|:-:|---|:-:|
| Q1 | `Immediate`가 "어떤 날만" 실패하는 이유 | 무응답 |
| Q2 | StatefulSet 삭제 후에도 EBS가 남는 이유 ①② | 무응답 |
| Q3 | EBS CSI 정책이 ARN 대신 태그로 경계를 긋는 이유 | 무응답 |
| Q4 | `sslmode=require` 상수가 Stage 2에선 무해했던 이유 | 무응답 |
| Q5 | `rollout restart`는 실패하고 `scale 0→1`은 되는 이유 | 무응답 |
| Q6 | 같은 설정이 3a에선 정답, 3b에선 사고인 것의 함의 | 무응답 |

## 설명한 내용 (근거 = 오늘 실측)

- **Q1** EBS는 AZ 종속. 노드그룹이 2a·2c에 걸쳐 있어 노드 AZ가 매번 다름 →
  `Immediate`는 파드 배치 전에 AZ를 확정해 **운에 맡기는 구조**가 된다.
  근거: 오늘 노드 2a / 볼륨 2a 일치는 우연이 아니라 `WaitForFirstConsumer` 때문
  (`k8s/base/postgres.yaml` StorageClass).
- **Q2** ① `volumeClaimTemplates`가 만든 PVC는 StatefulSet 생명주기에 안 묶인다(데이터 보호, 의도된 설계).
  ② 볼륨을 만든 주체가 tofu가 아니라 **클러스터 안의 EBS CSI 컨트롤러**다.
  근거(실측): `tofu state list` 34개 중 EBS 볼륨 **0개** / StatefulSet 삭제 직후 `vol-0c327… in-use` →
  `kubectl delete pvc` 7초 후 소멸.
- **Q3** 동적 프로비저닝은 **볼륨 ID가 생성 시점에 정해진다** → 정책 작성 시점에 ARN을 알 수 없다.
  그래서 리소스가 아니라 **출처(태그)**로 경계를 긋는다.
  근거(실측): 생성된 볼륨 태그 `ebs.csi.aws.com/cluster = true`.
- **Q4** RDS가 TLS를 켠 채로 오므로 상수 `sslmode=require`가 그냥 통했다 — **결함이 가려져 있었다.**
  교훈: 관리형에서 사라지는 건 카탈로그에 적힌 기능만이 아니라 **아무도 언급 안 하는 기본값**이고,
  그건 문서 비교가 아니라 **실제로 붙여봐야** 드러난다.
  근거: `application-prod.yml:34` / 실패 원문 `PSQLException: The server does not support SSL.`
- **Q5** RollingUpdate는 무중단이 목적이라 **새 파드를 먼저 띄운다** → 순간 12개 → 상한 11 초과.
  `scale 0→1`은 먼저 지우고 나중에 만들어 겹침이 없다(대신 다운타임).
  근거(실측): `0/1 nodes are available: 1 Insufficient memory, 1 Too many pods.`
- **Q6** 설정 자체에 옳고 그름이 없고 **목적이 정한다.** 3a에서 몸에 익은 감각이 3b에서
  그대로 오답이 되는 구조 — 결정 메타(D-001~004)를 붙이는 이유와 같은 계열.

## 2차 재확인 (3문항, 객관식) — **3/3 정답**

| # | 질문 | 사용자 답 | 정오 | 근거 |
|:-:|---|:-:|:-:|---|
| R1 | destroy 전 `kubectl delete pvc`를 빼먹으면? | (나) 볼륨이 남아 계속 과금 | ✅ | `tofu state`에 EBS 0건 / 실측 회수 로그 |
| R2 | `WaitForFirstConsumer`가 막는 문제는? | (나) 볼륨-노드 AZ 불일치 | ✅ | 볼륨 AZ 2a == 노드 AZ 2a |
| R3 | `The server does not support SSL`이 말해주는 것은? | (나) RDS가 조용히 해주던 일이 있었다 | ✅ | `application-prod.yml:34` 상수 `sslmode=require` |

## 요약

- 1차 0/6 (무응답) → 설명 → **2차 3/3 정답.**
- **확인된 이해**: ①고아 과금 메커니즘(state 밖 리소스) ②EBS의 AZ 종속 ③관리형이 감춰주던 기본값.
- **재검토 완료**: 6문항 전부 근거와 함께 설명했고, 핵심 3개를 재질문으로 확인.
- **다음 Stage(3b) 이월 주의**: Q6이 3b의 주제 그 자체다 — `reclaimPolicy: Delete`와
  `volumeClaimTemplates`가 **3a에서는 정답, 3b에서는 함정**(실패 6종 ②⑤).
  3b 퀴즈에서 이 지점을 다시 물을 것.
- **미출제(3b로 이월)**: `volumeHandle`·`claimRef` 잔존·PV 재바인딩 — 3a에서 안 다룬 개념.

<!-- QUIZ-PASSED -->
