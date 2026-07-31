# EKS 학습 퀴즈 — Stage 3b (terraform 소유 영속 EBS + static PV)

- 브랜치: `stage/eks-3b-postgres-static-pv` · 날짜: 2026-07-31 · HEAD: `ababf26`
- 형식: 1차 서술형 5문 → 전부 오답 → 교육 → 2차 객관식 재확인 → 최종 개념 확인

> **1차 0/5.** 다섯 문제 전부 "문서를 읽어선 안 나오고 코드를 쓰다가 밟아야 아는" 것들이었고,
> 실제로 이번 세션에 구현하며 발견한 것들이다(둘은 Blindspot Pass가 찾았다).
> 공통점: **다섯 다 "겉보기와 실제가 다른" 경우**다.

---

## 1차 (서술형)

### Q1. 영속 EBS를 `2-cluster`가 아니라 `0-bootstrap`에 둔 이유는? `prevent_destroy`로는 왜 안 되나
- 사용자 답: 모름
- 정오: ❌
- 근거: `.claude/scripts/eks-reaper.sh` — 리퍼는 마커에 박힌 `cluster_dir`(=2-cluster)에서
  `tofu destroy -auto-approve` 하나만 돈다. `prevent_destroy`는 **plan 단계에서 destroy 전체를 거부**하므로
  EKS·노드·NAT까지 아무것도 안 지워진다. → **데이터를 지키려다 지갑 안전장치를 끈다.**
  리퍼는 실패를 로그에 남기지만, 그 로그를 읽으려면 사람이 있어야 하는데
  **리퍼가 발동했다는 건 사람이 없다는 뜻**이다.
- 실증: `local_file` 3개 중 1개에만 `prevent_destroy`를 걸고 `tofu destroy` →
  `Plan: 3 to destroy` 후 에러, **3개 다 살아남음**, state도 3개 그대로($0 재현).

### Q2. 삭제 방지 장치 ①(코드로 못 뚫는 것)은 무엇이며 `tags` 블록에서 어떤 모습인가
- 사용자 답: 가 (`Persistent = "true"` 태그가 CSI에게 지우지 말라고 알린다)
- 정오: ❌ (정답: 나 — CSI 삭제용 태그 3종을 **하나도 안 붙인 것**)
- 근거: `AmazonEBSCSIDriverPolicy` v15 실물 조회.
  `AttachVolume`/`DetachVolume`은 **Condition 없음**, `DeleteVolume`만
  `ebs.csi.aws.com/cluster`·`CSIVolumeName`·`kubernetes.io/created-for/pvc/name` 태그 조건.
  **IAM은 기본이 거부**이므로 태그는 삭제를 *끄는* 스위치가 아니라 *켜는* 스위치다.
  `Persistent`는 우리가 발명한 태그로 **어떤 IAM 정책도 참조하지 않는다** — 우리 쉘 스크립트의 고아 필터용.
  `ec2:CreateTags`가 생성 시점으로 제한돼 CSI가 스스로 권한을 얻는 경로도 닫혀 있다.
- 위험: 보호가 "코드에 **없는** 것"이라 리뷰에서 안 보인다 → `assert-no-csi-delete-tags.sh`로 CI 차단.

### Q3. AZ 불일치가 3a에서 한 번도 안 터진 이유
- 사용자 답: 가 (2a에 용량이 많아 항상 2a에 떴다 = 운)
- 정오: ❌ (정답: 나 — `volumeBindingMode: WaitForFirstConsumer`)
- 근거: 3a 실측에서 **PVC가 처음에 Pending이었다가 11초 뒤 Bound**됐다.
  `Immediate`였다면 PVC 생성 즉시 볼륨을 만들어 기다릴 이유가 없다.
  즉 **볼륨이 노드를 따라간 것**이고, 노드가 2c에 떴어도 볼륨이 2c에 생겼을 것이다.
  3b는 순서가 뒤집혀(볼륨이 먼저 존재) 이 방어막이 사라진다.
- 교훈: **"잘 돌아갔다"는 "구조적으로 옳다"의 증거가 아니다.** 무엇이 실패를 막고 있었는지
  이름을 댈 수 없으면 아직 운일 수 있다.

### Q4. StorageClass에 `reclaimPolicy: Retain`을 적고 PV엔 안 적으면?
- 사용자 답: 나 (에러가 나고 apply 실패)
- 정오: ❌ (정답: 다 — 조용히 무시된다. static PV엔 기여 0)
- 근거: SC의 `reclaimPolicy`는 **그 클래스가 동적으로 만든 PV에만 상속**된다.
  손으로 쓴 PV는 자기 `spec.persistentVolumeReclaimPolicy`가 지배한다.
  **에러도 경고도 없고** `kubectl get sc`엔 `Retain`이 보인다.
  게다가 PV 기본값이 `Retain`이라 **동작은 맞는데 이유가 틀린** 상태가 된다.
- 관련: #326에서 걷어낸 "죽은 설정"과 같은 병 — 소비처 없는 설정은 없는 것보다 나쁘다.

### Q5. 3b에서 3a 습관대로 `kubectl delete pvc --all -A` 후 destroy하면?
- 사용자 답: 모름
- 정오: ❌ (정답: 나 — EBS는 살지만 PV가 `Released`+`claimRef` 잔존)
- 근거: 실패 6종 ④(최다 정체 지점). K8s는 이름이 아니라 **UID로 동일성을 판단**하므로
  같은 이름의 PVC를 다시 만들어도 안 붙는다. `kubectl patch pv postgres-data -p '{"spec":{"claimRef":null}}'`로 해소.
  보기 다(거부당한다)가 매력적인 이유: 파드가 사용 중이면 `pvc-protection` finalizer로 `Terminating`에 멈춘다(절반의 진실).
- 결론: 3b 권장은 **PVC를 남긴 채 클러스터를 destroy** — 3a에서 필수였던 습관을 옮기면 안 된다.

---

## 2차 재확인 (객관식)

### R1. 삭제를 막는 **가장 강한** 장치는?
- 사용자 답: 다 (`prevent_destroy`) · 추론: *"가는 우리만 쓰는 태그니까 아니고, 다는 apply 자체를 실패시킨다"*
- 정오: ❌ **부분정답** — 가를 소거한 근거가 정확했고, 다의 동작도 정확히 기억했다. 순위 기준만 달랐다.
- 근거: 판정 기준은 **집행자가 누구인가**.
  `prevent_destroy` = terraform이 집행 → 우리 HCL 3줄 삭제로 뚫림(에러 메시지가 `-exclude` 우회법까지 알려준다).
  태그 미부착 = **AWS IAM이 집행** → 사고로는 안 뚫린다(태그 추가 경로는 CI 가드가 차단).
  ⚠️ "절대 못 뚫는다"는 과장 — `irsa-ebs-csi.tf`에서 더 넓은 정책을 붙이면 우회 가능하나 명백히 의도적 행위다.

### R2. 노드 서브넷을 2a로 고정하지 않았다면?
- 사용자 답: 나 — *"서브넷 여러 개 지정 후 랜덤으로 뜬다면 EBS는 2a에 있기 때문에 확률적으로 못 붙을 수 있음"*
- 정오: ✅ **정답. 근거까지 정확** ("확률적으로"를 짚은 것이 핵심)
- 보강: EKS의 "서브넷 2개 이상" 요구는 **컨트롤플레인에만** 적용된다(`cluster.tf`). 노드그룹은 1개도 된다.
  같은 이름의 필드가 두 군데 있고 제약이 다르다.

### R3. PVC를 안 지운 채 클러스터를 destroy → 다음 세션에 재적용하면?
- 사용자 답: 나 (claimRef가 남아 Pending)
- 정오: ❌ (정답: 가 — 깨끗이 새로 붙고 데이터도 그대로)
- 근거: **claimRef는 PV 객체 안에 살고, PV는 etcd에 산다.** 클러스터를 destroy하면 etcd째 사라지므로
  남을 claimRef가 없다. Q5(PVC만 삭제, 클러스터 유지)와 **정반대 경우**다.
  데이터가 사는 이유: CSI는 파일시스템이 **없을 때만** mkfs, postgres는 PGDATA가 **비어 있을 때만** initdb.

---

## 최종 개념 확인 — K8s 객체(etcd) ↔ AWS 리소스의 수명 분리

### F1. destroy 직후 AWS와 K8s에 각각 무엇이 남나?
- 사용자 답: 나 (K8s엔 PV가 남는다 — PV는 클러스터 밖 객체)
- 정오: ❌ (정답: 가 — AWS엔 EBS만, K8s엔 아무것도)
- 🔑 **오개념의 정체**: `cluster-scoped`를 "클러스터 **밖**"으로 읽은 것.
  실제 뜻은 "**네임스페이스에 안 묶인다**"이며 PV는 여전히 etcd 객체다.
  판별법: **`kubectl get`으로 볼 수 있는 것은 전부 etcd 안에 있다.**

### F2. destroy 직후 `kubectl get pv`를 실행하면?
- 사용자 답: 나 (에러 — 접속할 API 서버가 없다)
- 정오: ✅ **정답**
- 근거: 이 세션에서 실제로 관측했다 —
  `dial tcp: lookup 4404....gr7.ap-northeast-2.eks.amazonaws.com: no such host`
  (3a에서 destroy한 클러스터의 엔드포인트가 DNS에서 사라진 상태)
  → "PV가 0개"가 아니라 **물어볼 서버가 없다.** PV가 etcd에 산다는 증명.

---

## 요약

| 라운드 | 결과 |
|---|---|
| 1차 서술형 | **0 / 5** |
| 2차 객관식 | **1 / 3** (R1 부분정답) |
| 최종 개념 확인 | **1 / 2** — 교정 후 핵심 개념 확보 |

**확실히 잡힌 것**
- AZ 결합과 그 **확률적** 실패 (R2를 근거까지 정확히 설명)
- `Persistent` 태그가 "우리 것"이라 AWS에 힘이 없다는 구분 (R1 소거 근거)
- `prevent_destroy`가 destroy **전체**를 실패시킨다는 동작
- **PV는 etcd에 산다** — `kubectl get`이 곧 API 서버 질의라는 판별법 (F2)

**재검토 파일** (틀린 문제 대응)
- `infra/aws-eks/0-bootstrap/ebs-postgres.tf` 하단 — IAM 자물쇠의 방향(허용 스위치)과 집행자 개념 (Q2·R1)
- `k8s/base/postgres-static.yaml` PV 절 — reclaimPolicy 소속, claimRef 수명, etcd↔AWS 경계 (Q4·Q5·R3·F1)

**메타 교훈**: 다섯 문제 전부 *겉보기와 실제가 다른* 사례였다.
`prevent_destroy`는 지켜주는 것 같은데 안전장치를 껐고 · `Persistent` 태그는 막는 것 같은데 힘이 없고 ·
3a는 성공했는데 이유가 달랐고 · StorageClass 설정은 보이는데 작동 안 하고 ·
`delete pvc`는 청소인 것 같은데 지뢰를 남긴다.

<!-- QUIZ-PASSED -->
