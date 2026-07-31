# ── 영속 Postgres 데이터 볼륨 (EKS Stage 3b) ──────────────────────────────
#
# Stage 3a는 StatefulSet의 `volumeClaimTemplates`가 PVC를 만들고, StorageClass가 CSI를 시켜
# EBS를 **동적으로** 만들었다. 세션이 끝나면 `kubectl delete pvc`로 함께 사라졌다.
#
# 3b는 방향을 뒤집는다: **terraform이 볼륨을 소유하고, K8s는 이미 있는 볼륨에 static PV로
# 붙기만 한다.** 그래야 클러스터를 통째로 부숴도 데이터가 살아남는다.
#
#   3a: PVC → StorageClass → CSI → EBS 생성   (아래에서 위로 자동)
#   3b: EBS(여기) → PV(volumeHandle 수기) → PVC → StatefulSet이 참조   (위아래를 맞춰 끼움)
#
# 학습 목표는 "부수고 다시 지어도 데이터가 그대로 붙는다"를 **매 세션 반복**해서 겪는 것이다.
# destroy-after-use 규율 덕에 우리는 어차피 매번 부수므로, 이 연습이 공짜로 따라온다
# (보통 학습자는 클러스터를 부술 이유가 없어 이 경험 자체를 못 한다).

resource "aws_ebs_volume" "postgres_data" {
  count = var.postgres_persistent_volume_enabled ? 1 : 0

  # 🔴 AZ가 이 리소스의 정체성이다. EBS는 특정 AZ의 스토리지에 물리적으로 존재하며
  #    다른 AZ의 인스턴스에는 **붙지 않는다**(실패 6종 ① AZ 불일치 → 파드 영구 Pending).
  #    2-cluster의 노드그룹이 이 값을 remote state로 읽어 같은 AZ에 고정된다.
  availability_zone = var.persistent_az

  size = var.postgres_volume_size_gb
  type = "gp3"

  # 추가 비용 없이 켜진다(기본 aws/ebs 키). 끄면 tfsec에서도 걸린다.
  encrypted = true

  # ℹ️ **여기서 포맷하지 않는다.** 갓 만든 EBS는 파일시스템이 없는 맨 블록 디바이스다.
  #    EBS CSI 드라이버가 첫 attach 때(NodeStageVolume) 파일시스템이 없으면 mkfs를 돌린다.
  #    → PV의 `csi.fsType`을 ext4로 고정해 둔다. **타입이 어긋나면 마운트가 실패**하고,
  #      최악의 경우 빈 볼륨으로 오인해 다시 포맷된다(실패 6종 ⑥ = 복구 불가).
  #    또 ext4 포맷은 마운트 지점에 `lost+found`를 만든다 → postgres 엔트리포인트가
  #    "PGDATA가 비어 있지 않다"고 판단해 initdb를 건너뛰고 죽는다.
  #    그래서 매니페스트에서 PGDATA를 하위 디렉토리(`.../data/pgdata`)로 내려둔다(3a에서 실측).

  tags = {
    Name    = "devquest-eks-postgres-data"
    Purpose = "EKS Stage 3b — static PV 재바인딩 실습용 영속 데이터"

    # 🔑 고아 검사의 축. SOP §9는 이 태그가 **정확히 "true"가 아닌** available 볼륨만
    #    고아로 센다. 영속 볼륨은 destroy 후 영원히 available 상태로 남는 것이 정상이라,
    #    이 태그가 없으면 매 세션 "고아 발견"이 뜨고 → 매번 실패하는 검사는 곧 무시되고
    #    → 그때부터 진짜 고아도 안 보인다.
    #    ⚠️ 값은 소문자 "true"여야 한다. "True"·"false"는 고아로 잡힌다(의도된 엄격함).
    Persistent = "true"

    # 크레딧 만료일. 이 시점에 모든 비용이 실지출로 전환되므로 원장 전체를 재판단한다.
    ReviewBy = "2027-01-15"
  }

  # ── 🔒 삭제 방지 2중 잠금 ────────────────────────────────────────────
  #
  # ① terraform 레벨 — 실수로 destroy를 걸어도 거부한다.
  #    이게 안전한 이유는 **0-bootstrap이 리퍼의 사정권 밖**이기 때문이다.
  #    2-cluster에 걸었다면 리퍼의 `tofu destroy -auto-approve`가 통째로 실패해
  #    dead man's switch가 벽돌이 됐을 것이다.
  lifecycle {
    prevent_destroy = true
  }

  # ② IAM 레벨 (아래 참조) — 이쪽이 훨씬 강하다.
}

# ── 🔒 IAM 자물쇠 — "태그를 안 붙이는 것"이 곧 삭제 방지다 ────────────────
#
# `AmazonEBSCSIDriverPolicy`(2-cluster의 EBS CSI IRSA에 붙는 관리형 정책) 실물 v15를
# 조회해 확인한 것(2026-07-31):
#
#   ec2:AttachVolume / DetachVolume  → Resource volume/*, instance/*  **Condition 없음**
#   ec2:DeleteVolume                 → Condition:  ebs.csi.aws.com/cluster
#                                              또는 CSIVolumeName
#                                              또는 kubernetes.io/created-for/pvc/name
#   ec2:CreateTags                   → Condition: ec2:CreateAction ∈ {CreateVolume, ...}
#
# 읽는 법:
#   • Attach에 조건이 없다  → terraform이 만든(= CSI 태그가 없는) 볼륨도 **정상적으로 붙는다.**
#   • Delete에 조건이 있다  → 저 세 태그가 **하나도 없으면 CSI는 이 볼륨을 지울 권한이 없다.**
#   • CreateTags가 생성 시점으로 제한 → CSI가 **기존 볼륨에 태그를 붙여 권한을 얻을 수도 없다.**
#
# 즉 위 tags 블록에 저 세 키를 **쓰지 않는 것 자체가 잠금장치**다.
# `reclaimPolicy: Retain`이나 `prevent_destroy`는 사람이 한 줄 고치면 뚫리지만,
# 이건 AWS IAM이 거부한다.
#
# 🔴 **저 세 태그를 절대 추가하지 마라.** 특히 "CSI가 관리하게 하려고" 붙이고 싶어지는데,
#    그 순간 6개월 영속 볼륨을 컨트롤러가 지울 수 있게 된다.
