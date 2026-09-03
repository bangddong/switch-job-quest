# ── ⑥ 관리형 애드온 (노드 Ready 필수 3종) ─────────────────────
resource "aws_eks_addon" "vpc_cni" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "vpc-cni"

  # ── NetworkPolicy 강제를 켠다 (2026-08-31, Phase 2 Stage C 블로커 C-5) ──
  #
  # 왜 필요한가: `ai-api` 의 `/internal/ai/**` 24개 엔드포인트는 **인증이 없다**
  #   (Spring Security 의존 자체가 없음 — `be/core/ai-api/build.gradle.kts` 의존 4개뿐).
  #   설계가 처음부터 *"내부 전용이니 네트워크로 막는다"* 로 갔기 때문이고, 그 통제가 이것이다.
  #   이게 꺼져 있으면 방어가 **네트워크·앱 양층 모두 0** 이다.
  #
  # 🔴 **이 플래그 없이 NetworkPolicy 매니페스트만 적용하면 조용히 무시된다.**
  #   `NetworkPolicy` 는 K8s **코어 API**(`networking.k8s.io/v1`)라 CRD 가 아니다 → 강제하는 CNI 가
  #   없어도 API 서버가 정상 수용하고 `... created` 를 출력한다. **에러도 경고도 없다.**
  #   즉 *"정책을 걸었다고 믿는데 실제론 안 걸린 상태"* 가 만들어진다. 순서를 반드시 지킬 것:
  #   이 애드온 설정 → 클러스터 apply → **플래그 실제 반영 확인** → 매니페스트 apply → 차단/허용 쌍 검증.
  #
  # 📏 스키마 실측 (`aws eks describe-addon-configuration --addon-name vpc-cni
  #                  --addon-version v1.22.3-eksbuild.1`, 2026-08-31 · 클러스터 미가동 · 무료):
  #     $.definitions.VpcCni.properties.enableNetworkPolicy = { "type": "string", "format": "boolean" }
  #   🔴 **문자열이다. 불리언이 아니다.** `= true` 로 쓰면 apply 가 InvalidParameterException 으로 죽는다.
  #   ⚠️ 같은 스키마 안에서 타입이 갈린다 — `nodeAgent.enabled` 는 `"type": "boolean"`(진짜 불리언),
  #      `enableNetworkPolicy`·`nodeAgent.enablePolicyEventLogs` 는 문자열이다. 일관되게 쓰려다 틀리기 쉽다.
  #      ebs-csi 에서 이미 쓴 예방책(스키마 실측)을 vpc-cni 에 처음 적용한 것이다.
  #
  # 📏 파드 상한 영향 **없음** (실측 근거): `aws-node` 는 이미 컨테이너 2개짜리다
  #   (`docs/eks-migration-log.md:329` — `aws-node 2/2 Running`, `:968-975` 전수 목록에 별도 정책 파드 0건).
  #   이 플래그는 파드/DaemonSet 을 새로 만들지 않고 **이미 붙어 있는 사이드카의 동작을 켠다.**
  #   따라서 Stage 3a/3b 의 파드·메모리 실측치는 이 컨테이너가 뜬 상태의 값이며, 켠다고 requests 가 바뀌지 않는다.
  #   (증가분은 eBPF 맵·conntrack 등 커널 메모리라 파드 cgroup 요청량에 안 잡힌다 — 측정 불가.)
  #
  # ⚠️ `depends_on` 을 붙이지 말 것. CNI 는 노드 조인보다 **먼저** 있어야 한다(coredns·ebs_csi 와 다른 이유).
  #    노드에이전트도 같은 DaemonSet 이라 정책을 켜도 이 구조는 그대로다.
  configuration_values = jsonencode({
    enableNetworkPolicy = "true"
  })
}

resource "aws_eks_addon" "kube_proxy" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "kube-proxy"
}

resource "aws_eks_addon" "coredns" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "coredns"

  # coredns 파드가 스케줄되려면 노드가 있어야 함
  depends_on = [aws_eks_node_group.main]

  # 🆘 **켰다 (Stage 3b).** 파드 상한 11에 실제로 부딪혔으므로 목적을 달성했다.
  #
  # Stage 3a 실측: 시스템 4 + ESO 3 + coredns 2 + core-api 1 + postgres 1 = **11/11, 여유 0.**
  # `kubectl rollout restart`가 실패했다 —
  #   0/1 nodes are available: 1 Insufficient memory, 1 Too many pods
  # 롤링 업데이트는 **새 파드를 먼저 띄우고** 구 파드를 내리므로 빈 슬롯 1칸이 필요한데 없었다.
  # (그때는 `scale 0→1`로 우회했다. 삭제 후 생성이라 슬롯이 안 필요하다.)
  #
  # 3b는 StatefulSet을 지웠다 다시 만드는 작업이라 이 벽에 정면으로 부딪힌다.
  #
  # 왜 coredns를 줄이는 것이 공짜인가: **노드가 1대라 replica 2개는 HA가 아니다.**
  # 둘 다 같은 노드에 뜨므로 노드가 죽으면 함께 죽는다. 가용성은 0도 2도 아니고 그냥 같다.
  # 즉 두 번째 replica는 슬롯만 먹고 아무것도 보장하지 않았다.
  #
  # ⚠️ 노드를 2대 이상으로 늘리면 이 판단이 뒤집힌다 — 그때는 2로 되돌릴 것.
  #    (파드 상한도 노드당 11이라 함께 늘어난다.)
  configuration_values = jsonencode({ replicaCount = 1 })
}

# ── ⑫ EBS CSI 드라이버 (Stage 3a) ─────────────────────────────
#
# 이게 없으면 PVC를 만들어도 **영원히 Pending**이다. K8s 자체는 EBS가 뭔지 모른다 —
# "스토리지를 만들어 달라"는 요청을 클라우드 API 호출로 번역해 주는 게 CSI 드라이버다.
# (CSI = Container Storage Interface. K8s가 스토리지 벤더별 코드를 코어에서 걷어내고
#  표준 인터페이스로 뺀 결과물. 그래서 EBS·EFS·GCP PD가 전부 같은 방식으로 붙는다.)
#
# 파드 2개가 뜬다:
#   ebs-csi-controller (Deployment) — AWS API를 호출해 볼륨을 만들고 지운다. IRSA 필요.
#   ebs-csi-node       (DaemonSet)  — 노드에 볼륨을 attach/mount 한다. 노드마다 1개.
resource "aws_eks_addon" "ebs_csi" {
  cluster_name = aws_eks_cluster.main.name
  addon_name   = "aws-ebs-csi-driver"

  # 🔴 이 한 줄이 IRSA를 애드온에 연결한다. 빼면 컨트롤러가 AWS 호출에서
  #    AccessDenied를 내고 PVC가 Pending에서 안 벗어난다(= "인가" 실패 모드, Stage 2 학습).
  service_account_role_arn = aws_iam_role.ebs_csi.arn

  # 스키마 실측(aws eks describe-addon-configuration, 07-30):
  #   controller.replicaCount     default 2, minimum 1
  #   defaultStorageClass.enabled default false
  #
  # replicaCount=1로 낮추는 이유는 **비용이 아니라 파드 상한**이다.
  # ~~t4g.small의 max-pods는 11(= ENI 3 × (IP 4 - 1) + 2, 실측). 현재 8을 쓰고 있어
  # 기본값 2로 두면 12가 되어 파드 하나가 Pending에 갇힌다.~~
  #
  # ~~🔄 정정 (2026-08-31, D-009 로 노드가 t4g.medium 이 됐다) — t4g.medium = 파드 상한 17, Stage C 13/17, 여유 4.~~
  #
  # 🔴 **재정정 (2026-08-31 유료 세션, D-010)** — 위 t4g.medium 전제는 **무효다.**
  #   그 타입은 이 계정에서 **launch 자체가 안 된다**(신 Free Tier 플랜이 인스턴스 타입을 제한).
  #   노드는 **t4g.small 로 되돌아갔다** → 파드 상한은 다시 **11** 이다(실측 재확인: capacity.pods = 11).
  #
  #   🔑 **그런데 판단의 근거가 파드에서 메모리로 바뀌었다.** 같은 세션의 Allocatable 실측:
  #     allocatable 1365Mi − 시스템 파드 requests 406Mi = **가용 959Mi** vs 3서비스 필요 1792Mi.
  #     → **파드 슬롯은 제약이 아니었다** (11칸 중 5칸 사용, 6칸 여유. 필요한 건 4칸).
  #       *"replicaCount=1 로 낮추는 이유는 파드 상한"* 이라는 위 서술은 **이제 정확하지 않다** —
  #       t4g.small 에서도 파드 칸은 남는다. 지금 유지하는 진짜 이유는 아래 ①② 다.
  #
  #   되돌리지 않는 이유: ①노드 1대에서 coredns 2개는 어차피 HA 가 아니다(같이 죽는다)
  #   ②coredns 한 개당 **~100Mi**(🟡 [미확인] — upstream 기본 requests 값을 인용한 것이고
  #     **이 클러스터에서 실측하지 않았다.** 08-31 세션에 시스템 파드 합계 406Mi 만 쟀고
  #     파드별로 쪼개지 않았다). 사실이라면 959Mi 예산의 10% 라 파드 칸이 아니라 메모리가 아깝다.
  #     🔑 다음 세션에 `kubectl get pods -A -o json` 으로 파드별 requests 를 쪼갤 때 함께 확정할 것
  #     — 원장 L-43 이 요구하는 DaemonSet ÷ Deployment 분리 측정과 **같은 한 번의 조회**로 끝난다.
  #   🔑 **노드를 늘리면 이 판단이 뒤집힌다** — 그때는 coredns 를 2로 되돌릴 것(기존 규율 유지).
  #      ~~⚠️ 단 그 100Mi 가 노드 B 예산에서 나가므로, D-010 의 2대 배치 여유가 155Mi → **55Mi** 로 준다.~~
  #      🔴 **재정정 (2026-09-03, D-011)** — 재개 경로가 **2대 → 3대**로 바뀌어 이 경고는 무효다.
  #      3대에서는 비관 가정(노드당 여유 959Mi)에서도 앱 하나 512Mi 를 얹고 447Mi 가 남으므로
  #      두 번째 coredns replica(~100Mi)를 여유롭게 흡수한다. **여유 55Mi 라는 숫자는 2대 전제였다.**
  #      variables.tf 의 재개 경로 주석과 **함께** 볼 것.
  # 노드 1대짜리 학습 클러스터에서 컨트롤러 2개는 어차피 HA가 아니다(같이 죽는다).
  #
  # defaultStorageClass를 안 켜는 이유: StorageClass를 직접 쓰는 게 이번 Stage의 목표다.
  # 자동 생성된 것을 쓰면 reclaimPolicy·volumeBindingMode를 왜 그렇게 두는지 안 배운다.
  configuration_values = jsonencode({
    controller = {
      replicaCount = 1
    }
  })

  # 애드온 파드가 스케줄되려면 노드가 있어야 한다(coredns와 같은 이유).
  depends_on = [aws_eks_node_group.main]
}
