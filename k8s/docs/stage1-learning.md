# K8s Stage 1 학습 기록 — 첫 Pod 띄우기

> 목표: kind 로컬 클러스터에 DevQuest 백엔드를 Deployment + Service로 띄우고,
> Service → Pod 경로로 HTTP 응답을 받아낸다.
> 이 문서만 보고 처음부터 다시 재현할 수 있도록 작성한다.

---

## 0. 전체 그림 먼저

```
[내 노트북]
└── Docker
    └── kind 클러스터 (Docker 컨테이너 1개 = K8s 노드)
        └── control-plane (devquest-control-plane)
            ├── etcd            ← 모든 오브젝트 상태 저장 (Secret 포함)
            ├── kube-apiserver  ← kubectl 명령이 도달하는 입구
            ├── scheduler       ← Pod를 어느 노드에 올릴지 결정
            ├── controller-mgr  ← "선언한 상태"와 "현재 상태"를 계속 맞춤
            └── kubelet         ← 실제로 컨테이너를 띄우는 노드 에이전트
                └── Pod (devquest-be-xxxx)
                    └── Container (devquest-be:local 이미지)
                        └── Spring Boot 앱 (port 8080)
```

핵심 철학 = **선언형(Declarative)**.
"이런 상태였으면 좋겠다"를 YAML로 선언하면, K8s가 그 상태를 **계속 유지**한다.
명령형(`docker run`처럼 "이걸 실행해")이 아니라, "이 상태를 원해"라고 말하는 방식.

---

## 1. 클러스터 생존 확인

```bash
kubectl get nodes
```
```
NAME                     STATUS   ROLES           AGE   VERSION
devquest-control-plane   Ready    control-plane   24h   v1.36.1
```

### 용어

| 용어 | 의미 |
|------|------|
| **노드(Node)** | 컨테이너가 실제로 돌아가는 머신. 실무에선 EC2 같은 서버 1대. kind에선 **Docker 컨테이너 1개**가 노드 역할 |
| **control-plane** | 클러스터의 두뇌. 스케줄링·상태 관리·API 처리 담당. 실무 클러스터는 control-plane과 worker 노드를 분리하지만, kind 단일 노드는 control-plane이 워크로드도 같이 실행 |
| **Ready** | 노드가 Pod를 받을 준비 완료 상태 |
| **kubectl** | K8s에 명령을 보내는 CLI. 내부적으로 `~/.kube/config`의 접속 정보를 읽어 kube-apiserver에 HTTP 요청을 보냄 |

> kind는 노드를 Docker 컨테이너로 흉내낸다. `docker ps` 하면 `devquest-control-plane` 컨테이너가 보인다.
> 이 컨테이너 **안에서** 또 컨테이너(우리 앱 Pod)가 돈다 — "컨테이너 속 컨테이너" 구조.

---

## 2. Secret 생성 — 민감 정보 주입

```bash
kubectl create secret generic devquest-secrets \
  --from-literal=GITHUB_CLIENT_ID=dummy-client-id \
  --from-literal=GITHUB_CLIENT_SECRET=dummy-client-secret \
  --from-literal=JWT_SECRET=local-k8s-jwt-secret-must-be-at-least-256-bits-long-xxxxx
```

### 왜 필요한가
DevQuest 앱(`application.yml`)은 기동 시 `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET`
3개를 **기본값 없이** 요구한다. 이게 없으면 Spring이 `PlaceholderResolutionException`으로 부팅 실패.
→ 이 3개를 컨테이너 환경변수로 주입해야 한다.

### 용어

| 용어 | 의미 |
|------|------|
| **Secret** | 민감 데이터(키·비밀번호)를 저장하는 K8s 오브젝트. 값은 **base64 인코딩**으로 저장됨 (암호화 아님 — 주의) |
| **generic** | Secret 타입 중 하나. 임의의 key-value 저장용. (다른 타입: `docker-registry`, `tls`) |
| **--from-literal** | 명령줄에서 직접 key=value를 넣는 방식. 파일에서 읽으려면 `--from-file` |
| **etcd** | Secret이 실제로 저장되는 곳. 클러스터의 모든 상태를 담는 분산 key-value 저장소 |

### Secret은 어디에 저장되나?
- YAML 파일로 만들어지지 않음. `.kube/config`에도 없음.
- **클러스터 내부 etcd에만 존재.**

| 상황 | 유지 여부 |
|------|----------|
| 클러스터 살아있는 동안 | ✅ |
| 노트북 재부팅 후 kind 재기동 | ✅ (Docker 볼륨에 etcd 데이터 잔존) |
| `kind delete cluster` | ❌ 사라짐 |

> ⚠️ base64는 **암호화가 아니라 인코딩**이다. `kubectl get secret devquest-secrets -o yaml`로 보면
> 누구나 디코딩 가능. 실무에서 진짜 비밀을 git/etcd에 안전하게 넣으려면 **Sealed Secrets / External Secrets**(Stage 5)가 필요하다.
> 지금은 dummy 값이라 문제 없음.

---

## 3. Deployment 적용 — Pod 생성

```bash
kubectl apply -f k8s/be/deployment.yaml
```
```
deployment.apps/devquest-be configured
```

### deployment.yaml 한 줄씩 해부

```yaml
apiVersion: apps/v1        # 이 오브젝트가 속한 API 그룹/버전
kind: Deployment           # 오브젝트 종류
metadata:
  name: devquest-be        # Deployment 이름 (클러스터 내 식별자)
  namespace: default       # 논리적 격리 공간
spec:                      # "원하는 상태(desired state)" 선언 시작
  replicas: 1              # Pod를 항상 1개 유지하라
  selector:
    matchLabels:
      app: devquest-be     # 이 라벨을 가진 Pod를 "내 것"으로 관리
  template:                # 아래는 Pod를 찍어낼 틀(template)
    metadata:
      labels:
        app: devquest-be   # 생성될 Pod에 붙일 라벨 (selector와 반드시 일치!)
    spec:
      containers:
        - name: devquest-be
          image: devquest-be:local   # 사용할 Docker 이미지
          imagePullPolicy: Never     # 레지스트리에서 받지 말고 로컬 이미지 사용
          ports:
            - containerPort: 8080     # 컨테이너가 리스닝하는 포트
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "local"          # local 프로파일 (H2, OTLP/Loki off)
          envFrom:
            - secretRef:
                name: devquest-secrets # Secret의 모든 key를 env로 주입
```

### 용어 심화

| 용어 | 깊은 설명 |
|------|----------|
| **Deployment** | Pod를 직접 만들지 않는다. **ReplicaSet**을 만들고, ReplicaSet이 Pod를 만든다. Deployment의 진짜 역할은 "버전 관리" — 이미지가 바뀌면 새 ReplicaSet을 만들어 **롤링 업데이트**하고, 문제 생기면 이전 ReplicaSet으로 **롤백**한다 |
| **Pod** | K8s의 최소 배포 단위. 컨테이너 1개 이상을 감싸는 껍데기. 같은 Pod 안 컨테이너는 네트워크(localhost)·스토리지를 공유. 보통 컨테이너 1개 = Pod 1개 |
| **ReplicaSet** | "Pod를 N개 유지"를 담당하는 컨트롤러. Pod가 죽으면 즉시 새로 만든다. 보통 직접 안 만들고 Deployment가 관리 |
| **replicas** | 유지할 Pod 개수. `3`이면 Pod 3개. 하나 죽으면 자동으로 다시 3개로 복구 |
| **selector / labels** | K8s의 핵심 연결 메커니즘. Deployment·Service는 IP가 아니라 **라벨**로 Pod를 찾는다. `selector.matchLabels`와 `template.labels`가 **반드시 일치**해야 Deployment가 자기 Pod를 인식 |
| **template** | Pod를 찍어내는 틀. replicas만큼 이 틀로 Pod를 복제 |
| **image / imagePullPolicy** | `Never` = 로컬 이미지만 사용. kind에선 이미지를 `kind load docker-image`로 클러스터에 미리 넣어야 함. 실무에선 `IfNotPresent`/`Always`로 레지스트리(ECR 등)에서 pull |
| **containerPort** | 컨테이너가 여는 포트. 문서화 목적이 강하고, 실제 노출은 Service가 담당 |
| **env / envFrom** | `env` = key 하나씩 직접 지정. `envFrom.secretRef` = Secret의 **모든 key를 한 번에** 환경변수로 주입 |
| **namespace** | 클러스터를 논리적으로 나누는 칸막이. `default`는 기본 칸. 실무에선 `prod`, `dev`, 팀별로 분리해 권한·리소스 격리 |

### `apply` vs `create`
- `kubectl create` — 새로 만들기. 이미 있으면 에러.
- `kubectl apply` — "이 상태가 되도록 맞춰라". 없으면 생성, 있으면 **변경분만 반영**(`configured`). 선언형 철학에 맞는 명령. **항상 apply를 쓰는 습관**을 들이면 좋다.

> 출력 `configured`는 어제 만든 Deployment에서 바뀐 부분(프로파일 prod→local, envFrom 추가)만 반영했다는 뜻.
> 처음이면 `created`로 나온다.

---

## 4. Service 적용 — 트래픽 라우팅

```bash
kubectl apply -f k8s/be/services.yaml
```
```
service/devquest-be-service unchanged
```

### services.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: devquest-be-service
  namespace: default
spec:
  selector:
    app: devquest-be       # 이 라벨을 가진 Pod로 트래픽 전달
  ports:
    - protocol: TCP
      port: 8080           # Service가 노출하는 포트
      targetPort: 8080     # Pod(컨테이너)의 실제 포트
  type: ClusterIP          # 클러스터 내부 전용
```

### 왜 Service가 필요한가
Pod는 **언제든 죽고 다시 태어난다**. 죽으면 IP가 바뀐다.
"방금 그 Pod IP로 요청해" 같은 방식은 Pod 재시작 한 번이면 깨진다.
→ Service가 **고정된 가상 IP + DNS 이름**을 제공하고, 뒤에서 살아있는 Pod로 알아서 라우팅한다.

```
요청 → Service(devquest-be-service, 고정 IP)
        ↓ selector: app=devquest-be 로 살아있는 Pod 찾기
       Pod (IP가 바뀌어도 라벨만 맞으면 OK)
```

### 용어 심화

| 용어 | 깊은 설명 |
|------|----------|
| **Service** | Pod 집합에 **안정적인 단일 진입점**(고정 IP + DNS)을 부여하는 오브젝트. 부하 분산(load balancing)도 자동 수행 — Pod가 여러 개면 라운드로빈 분배 |
| **selector** | Service가 어떤 Pod로 트래픽을 보낼지 라벨로 결정. Deployment의 `template.labels`와 같은 값(`app: devquest-be`)을 써야 연결됨 |
| **port** | Service가 외부(클러스터 내부 다른 Pod)에 노출하는 포트 |
| **targetPort** | 실제 Pod 컨테이너의 포트. `port`로 들어온 트래픽을 `targetPort`로 전달 |
| **type: ClusterIP** | 기본 타입. **클러스터 내부에서만** 접근 가능. DNS 이름 `devquest-be-service`로 다른 Pod가 호출 가능. 외부 노출 안 됨 |
| **DNS** | K8s 내부 DNS가 Service 이름을 자동 등록. 같은 namespace면 `http://devquest-be-service:8080`, 다른 namespace면 `http://devquest-be-service.default.svc.cluster.local:8080` |

### Service 타입 비교 (앞으로 배울 것)

| 타입 | 노출 범위 | 용도 |
|------|----------|------|
| **ClusterIP** (현재) | 클러스터 내부만 | 내부 서비스 간 통신 |
| **NodePort** | 노드 IP:포트로 외부 노출 | 간단한 외부 접근 |
| **LoadBalancer** | 클라우드 LB로 외부 노출 | 실무 외부 노출 (EKS+ELB) |
| **Ingress** (Stage 4) | 도메인·경로 기반 라우팅 | HTTP 라우팅 + TLS |

> `unchanged` = 어제 만든 그대로라 변경 없음.

---

## 5. Pod 상태 확인

```bash
kubectl get pods
```
```
NAME                           READY   STATUS    RESTARTS   AGE
devquest-be-8544ff7758-4sbzv   1/1     Running   0          18s
```

### 읽는 법

| 컬럼 | 의미 |
|------|------|
| **NAME** | `devquest-be`(Deployment) + `8544ff7758`(ReplicaSet 해시) + `4sbzv`(Pod 랜덤 ID). 이름만 봐도 계층 구조가 보인다 |
| **READY** | `1/1` = 컨테이너 1개 중 1개 준비됨. `0/1`이면 기동 중이거나 실패 |
| **STATUS** | `Running` = 정상. 다른 값: `Pending`(스케줄 대기), `ContainerCreating`(이미지 받는 중), `CrashLoopBackOff`(계속 죽음), `Error` |
| **RESTARTS** | 재시작 횟수. 숫자가 계속 오르면 앱이 반복 크래시 중 |
| **AGE** | 생성 후 경과 시간 |

```bash
kubectl get pods -w   # -w(watch): 상태 변화를 실시간 스트리밍
```

> `-w`는 Ctrl+C로 종료. 상태가 바뀔 때마다 새 줄이 추가된다.

---

## 6. 로그로 내부 동작 검증

```bash
kubectl logs deployment/devquest-be --tail=30
```

핵심 라인:
```
Database JDBC URL [jdbc:h2:mem:devquest]         ← H2 in-memory 연결됨
Initialized JPA EntityManagerFactory             ← JPA 초기화 완료
Tomcat started on port 8080 (http)               ← 웹서버 기동
Started DevQuestApplicationKt in 7.941 seconds   ← 앱 부팅 완료
Exposing 2 endpoints beneath base path '/actuator'
```

### 용어

| 용어 | 의미 |
|------|------|
| **kubectl logs** | Pod(컨테이너)의 stdout/stderr를 가져옴. 우리 앱은 STDOUT으로 로그를 쏘므로 그대로 보임 |
| **deployment/devquest-be** | Pod 이름 대신 Deployment를 지정 가능. Deployment가 관리하는 Pod의 로그를 자동으로 찾아줌 (Pod 이름이 매번 바뀌므로 편리) |
| **--tail=30** | 마지막 30줄만. 전체는 생략, 실시간은 `-f`(follow) |

> 로그가 "앱이 살아있다"는 **직접 증거**. STATUS가 Running이어도 앱 내부가 깨졌을 수 있으니 로그 확인이 중요.

---

## 7. End-to-End 검증 — Service → Pod 경로

터미널 1 (터널 유지):
```bash
kubectl port-forward service/devquest-be-service 8080:8080
```

터미널 2 (요청):
```bash
curl http://localhost:8080/health
```
```json
{"data":"DevQuest API is running","error":null,"result":"SUCCESS"}
```

### 이 한 줄이 검증한 전체 경로
```
curl localhost:8080
   ↓ port-forward (로컬 ↔ 클러스터 터널)
Service (devquest-be-service, ClusterIP)
   ↓ selector: app=devquest-be
Pod (devquest-be-8544ff7758-4sbzv)
   ↓
Spring Boot → HealthController(@GetMapping("/health"))
   ↓
"DevQuest API is running"
```

### 용어

| 용어 | 의미 |
|------|------|
| **port-forward** | 로컬 포트 ↔ 클러스터 내부(Service/Pod)를 잇는 **임시 터널**. ClusterIP는 외부 접근 불가라, 개발자가 로컬에서 확인할 때 쓴다. 명령을 끄면 터널도 사라짐. **디버깅·검증 전용**, 실무 트래픽 경로 아님 |
| **Not Found vs Connection refused** | `Not Found`(404) = 연결은 됐고 경로만 없음 → **앱은 살아있다**. `Connection refused` = 연결 자체 실패 → 앱이 죽었거나 포트 안 열림. 디버깅 시 이 둘을 구분하는 게 핵심 |

> 처음에 `/actuator/health`, `/api/v1/quests`로 404가 났던 이유:
> actuator 노출 경로가 제한적이고, 실제 헬스 엔드포인트는 앱 자체 `HealthController`의 `/health`였기 때문.
> 404가 떴다는 것 자체가 "Service→Pod 연결은 정상"이라는 신호였다.

---

## 명령어 치트시트

```bash
# 상태 확인
kubectl get nodes                          # 노드 목록
kubectl get pods                           # Pod 목록
kubectl get pods -w                        # 실시간 감시
kubectl get svc                            # Service 목록
kubectl get deployment                     # Deployment 목록
kubectl describe pod <이름>                 # Pod 상세(이벤트·에러 원인)

# 적용/삭제
kubectl apply -f <파일>                     # 선언 상태로 맞추기 (생성/수정)
kubectl delete -f <파일>                    # 오브젝트 삭제

# 로그/디버깅
kubectl logs deployment/devquest-be        # 로그
kubectl logs -f <pod>                       # 실시간 로그
kubectl exec -it <pod> -- sh                # 컨테이너 안으로 진입
kubectl port-forward service/<svc> 8080:8080 # 로컬 터널

# Secret
kubectl get secret                         # Secret 목록
kubectl get secret devquest-secrets -o yaml # 내용(base64) 확인
```

---

## 직접 해볼 실험 (자기복구 체감)

```bash
# 1. Pod를 강제로 죽여본다
kubectl delete pod <pod-이름>

# 2. 즉시 새 Pod가 자동 생성되는지 확인
kubectl get pods -w
```
→ Deployment(ReplicaSet)가 `replicas: 1`을 유지하려고 **새 Pod를 자동으로 띄운다.**
이름의 끝부분(랜덤 ID)이 바뀌어도, Service는 라벨로 새 Pod를 찾아 계속 라우팅한다.
**이게 K8s "자가 치유(self-healing)"의 핵심.**

---

## Stage 1에서 익힌 핵심 개념 요약

1. **선언형** — YAML로 원하는 상태를 선언하면 K8s가 유지
2. **Pod** — 최소 배포 단위, 언제든 죽고 다시 태어남(IP 가변)
3. **Deployment → ReplicaSet → Pod** — 계층 구조, replicas 유지 + 버전 관리
4. **Service** — 고정 진입점, 라벨로 Pod를 찾아 라우팅(IP 무관)
5. **라벨(label)** — K8s의 연결 접착제, IP가 아닌 라벨로 모든 게 연결됨
6. **Secret** — 민감값 주입(base64, 암호화 아님)
7. **자가 치유** — Pod가 죽어도 자동 복구

---

## 다음 단계

- **Stage 2**: ConfigMap + Secret 패턴 정리 (설정과 비밀 분리)
- **Stage 3**: H2 in-memory → PostgreSQL StatefulSet (영속 스토리지)
- **Stage 4**: Ingress (port-forward 없이 도메인 기반 외부 노출)
