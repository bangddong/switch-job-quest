# AWS EKS 실습 — 정답 경로

> **용도**: "AWS free tier $200 EKS 실습기" 튜토리얼 소스. 처음 하는 사람이 **이 문서만 보고
> 처음부터 따라 하면 되는 상태**를 항상 유지한다.
>
> **유지 규칙** (상세: 루트 `CLAUDE.md` "EKS 작업 일지 규칙"):
> - 실패 시도는 여기 없다 (그건 `eks-migration-log.md`) — **동작 확인된 명령어만** 순서대로
> - 각 명령어에 기대 출력/확인 방법, 단계별 사전 조건(도구 버전·AWS 권한·앞 단계 산출물) 명시
> - 💰 비용 발생 시작 지점 표시 + 각 단계 cleanup 명령어
> - 방식이 바뀌면 과거 단계도 최종 방식으로 소급 갱신
> - 캡처는 사용자가 직접 — 필요 지점에 `<!-- 캡처 필요: ... -->` 자리표시, 아래 체크리스트로 추적
> - 터미널 출력은 이미지 금지, 텍스트 코드블록
>
> **최종 검증**: 이관 완료 후 클러스터 destroy → 이 문서만으로 처음부터 재현 성공해야 완료.

## 캡처 체크리스트

> 위치: `docs/images/eks-tutorial/`. 캡처 넣으면 완료 체크 + 본문 자리표시를 이미지 참조로 교체.
> ⚠️ = 휘발성 (지금 아니면 못 찍음)

| 파일명 | 필요한 화면 | 완료 |
|--------|------------|:----:|
| step-00-credits.png | ⚠️ Billing → Credits: Total remaining **$200.00 / 사용 $0.00** + Active credits 6건 목록 ($100 Free Tier + $20×5 Explore AWS). 크레딧 소비 시작 후엔 이 상태 재현 불가 | ☐ |
| step-00-budgets.png | Budgets 목록에 생성된 budget + 임계값 알림 표시 | ☐ |
| step-00-anomaly.png | Cost Anomaly Detection 모니터 생성 완료 화면 | ☐ |

## 사전 조건 (전체 공통)

<!-- 확정 시 기입: OS/셸, OpenTofu 버전, AWS CLI 버전, kubectl 버전, IAM 권한 -->

- AWS 계정: 신규 계정 — 생성 완료 (2026-07-16)
- 크레딧 (07-16 콘솔 실측): **$200.00 = $100 (AWS Free Tier 기본) + $20×5 (Explore AWS 활동:
  EC2·Bedrock·Lambda·Budgets·RDS)**. 전 건 만료 **2027-07-15** (가입 +1년). 사용 $0.00
- AWS Budgets 알림 + Cost Anomaly Detection 설정 (Step 0 — `tofu apply` 전 필수)

## Step 0 — AWS Budgets 가드레일 설정

<!-- 설정 완료 후 절차 기록 -->
<!-- 캡처 필요: step-00-credits.png — Billing → Credits 페이지, Total remaining $200.00·Active credits 6건이 보이는 상태 -->
<!-- 캡처 필요: step-00-budgets.png — Budgets 목록, 생성된 budget과 임계값 알림이 보이는 상태 -->
<!-- 캡처 필요: step-00-anomaly.png — Cost Anomaly Detection 모니터 생성 완료 상태 -->

## Step 1 — (예정) OpenTofu 설치 + 스캐폴딩

<!-- Stage 0 착수 시 기록 -->
