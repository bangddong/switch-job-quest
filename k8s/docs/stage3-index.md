# K8s Stage 3 학습 인덱스 — H2 → PostgreSQL StatefulSet

> 내일 학습할 내용의 목차/예습 노트. 실제 실습 기록은 학습 후 `stage3-learning.md`로 작성.

---

## 이번 단계 한 줄 목표

**영구 저장소가 필요한 PostgreSQL을 StatefulSet으로 띄우고**,
DevQuest BE가 H2 대신 클러스터 내 PostgreSQL에 연결하도록 한다.
→ "Pod 재시작해도 데이터 유지" 구조 완성.

---

## 현재 상태 (Stage 2 종료 시점)

```
kind 클러스터
├── Deployment: devquest-be (H2 in-memory 사용)
├── Service: devquest-be-service
├── ConfigMap: devquest-config  ← SPRING_PROFILES_ACTIVE=local
└── Secret: devquest-secrets    ← GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, JWT_SECRET
```

문제의식:
- H2는 Pod 재시작 시 데이터가 날아간다 (in-memory)
- 실제 운영 환경과 동일한 PostgreSQL을 클러스터 내에서 써보고 싶다

---

## 배울 개념

### 1. StatefulSet vs Deployment

| 비교 항목 | Deployment | StatefulSet |
|-----------|-----------|-------------|
| 대상 | **무상태(stateless)** 앱 (BE API 서버) | **상태(stateful)** 앱 (DB, 캐시, 메시지 큐) |
| Pod 이름 | 랜덤 해시 (`devquest-be-7f9b4-xyz`) | **순서가 있는 고정 이름** (`postgres-0`, `postgres-1`) |
| 스토리지 | 공유 or 없음 | Pod마다 **고유한 PVC** 자동 생성 |
| 기동/종료 순서 | 동시 | **순서 보장** (0 → 1 → 2) |
| 네트워크 정체성 | 재시작마다 IP 변경 | **고정 DNS** 유지 (`postgres-0.postgres-svc`) |

**핵심**: DB는 Pod가 죽어도 같은 데이터에 다시 붙어야 한다 → StatefulSet.

### 2. PersistentVolume (PV) / PersistentVolumeClaim (PVC)

```
[K8s 클러스터]
  PV (PersistentVolume)
  ├── 실제 스토리지 리소스 (노드 디스크, NFS, 클라우드 볼륨 등)
  └── 클러스터 관리자가 미리 만들어두거나, StorageClass가 동적으로 생성

  PVC (PersistentVolumeClaim)
  ├── Pod가 "이 크기의 스토리지가 필요해요"라고 요청하는 오브젝트
  └── K8s가 요청에 맞는 PV를 찾아 바인딩

  Pod
  └── PVC를 볼륨으로 마운트 → 컨테이너에서 파일 I/O
```

| 용어 | 한 줄 설명 |
|------|-----------|
| **PV** | 실제 디스크. 관리자가 만들거나 StorageClass가 자동 생성 |
| **PVC** | Pod의 스토리지 주문서 ("5Gi ReadWriteOnce 주세요") |
| **StorageClass** | PV를 동적으로 생성하는 방법 정의 (kind 기본값: `standard`) |
| **ReclaimPolicy** | PVC 삭제 시 PV 처리 방식: `Delete`(같이 삭제) / `Retain`(보존) |
| **AccessMode** | `ReadWriteOnce`(한 노드만 쓰기) / `ReadWriteMany`(여러 노드) |

> kind 클러스터는 `standard` StorageClass를 기본 제공.
> PVC를 생성하면 kind가 노드 디스크에 디렉토리를 만들어 PV를 자동으로 바인딩한다.

### 3. Headless Service (StatefulSet 전용 DNS)

StatefulSet의 각 Pod에 고정 DNS를 부여하려면 **headless service**가 필요하다.

```yaml
# 일반 Service: ClusterIP → 로드밸런싱
# Headless Service: ClusterIP: None → 각 Pod DNS 직접 노출
spec:
  clusterIP: None
```

DNS 패턴: `<pod-name>.<service-name>.<namespace>.svc.cluster.local`
예: `postgres-0.postgres-svc.default.svc.cluster.local`

---

## 실습 흐름 (예상)

```
k8s/
├── be/
│   ├── deployment.yaml   ← 수정: DB 연결 env var 추가
│   └── services.yaml
└── postgres/             ← 신규 생성
    ├── statefulset.yaml  ← PostgreSQL StatefulSet + volumeClaimTemplates
    └── services.yaml     ← Headless Service + ClusterIP Service
```

### Step 1. PostgreSQL StatefulSet 생성

```yaml
# k8s/postgres/statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres-svc      # Headless Service 이름과 일치 필수
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              value: devquest
            - name: POSTGRES_USER
              value: devquest
            - name: POSTGRES_PASSWORD
              value: devquest-local
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:          # StatefulSet 전용 — Pod마다 PVC 자동 생성
    - metadata:
        name: postgres-data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```

### Step 2. PostgreSQL Service 생성

```yaml
# k8s/postgres/services.yaml
# Headless Service — StatefulSet DNS용
apiVersion: v1
kind: Service
metadata:
  name: postgres-svc
spec:
  clusterIP: None              # headless!
  selector:
    app: postgres
  ports:
    - port: 5432
---
# ClusterIP Service — 앱에서 접근용
apiVersion: v1
kind: Service
metadata:
  name: postgres
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
```

### Step 3. ConfigMap에 DB 연결 env var 추가

Spring Boot 릴랙스드 바인딩: `STORAGE_DATASOURCE_CORE_JDBC_URL` → `storage.datasource.core.jdbc-url`

```bash
kubectl delete configmap devquest-config
kubectl create configmap devquest-config \
  --from-literal=SPRING_PROFILES_ACTIVE=local \
  --from-literal=STORAGE_DATASOURCE_CORE_DRIVER_CLASS_NAME=org.postgresql.Driver \
  --from-literal=STORAGE_DATASOURCE_CORE_JDBC_URL=jdbc:postgresql://postgres:5432/devquest \
  --from-literal=STORAGE_DATASOURCE_CORE_USERNAME=devquest \
  --from-literal=SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
  --from-literal=SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop
```

> **왜 Secret이 아니라 ConfigMap인가?**
> 로컬 학습용이라 민감도가 낮다. 실제 운영에서는 `POSTGRES_PASSWORD` + `STORAGE_DATASOURCE_CORE_PASSWORD`를 Secret으로 분리해야 한다.

> **DDL auto는 `create-drop`?**
> Flyway는 prod 전용으로 설정되어 있고, 로컬 K8s 학습용이므로 JPA가 스키마를 자동 생성하게 한다.
> Stage 4 이후 Flyway를 활성화하는 단계를 추가할 예정.

### Step 4. Secret에 DB 비밀번호 추가

```bash
# 기존 Secret에 DB 비밀번호 추가 (재생성)
kubectl delete secret devquest-secrets
kubectl create secret generic devquest-secrets \
  --from-literal=GITHUB_CLIENT_ID=<값> \
  --from-literal=GITHUB_CLIENT_SECRET=<값> \
  --from-literal=JWT_SECRET=<값> \
  --from-literal=STORAGE_DATASOURCE_CORE_PASSWORD=devquest-local
```

### Step 5. 적용 순서

```bash
# 1. PostgreSQL 먼저 기동
kubectl apply -f k8s/postgres/services.yaml
kubectl apply -f k8s/postgres/statefulset.yaml
kubectl get pods -w  # postgres-0 Running 확인

# 2. PVC 생성 확인
kubectl get pvc   # postgres-data-postgres-0 Bound 확인

# 3. BE ConfigMap/Secret 재생성 (Step 3, 4)

# 4. BE Deployment 재기동
kubectl rollout restart deployment/devquest-be
kubectl get pods -w  # devquest-be-xxx Running 확인

# 5. 헬스 체크
kubectl port-forward service/devquest-be-service 8080:8080
curl http://localhost:8080/health
```

---

## 확인하고 싶은 학습 포인트 (실습 전 질문거리)

- [ ] StatefulSet의 `volumeClaimTemplates`와 일반 PVC를 직접 만드는 것의 차이는?
- [ ] `postgres-0`라는 DNS로 접근할 때와 `postgres` Service로 접근할 때 차이는?
- [ ] Pod가 죽었다 살아났을 때 같은 PVC에 다시 붙는지 확인하는 방법은?
- [ ] `create-drop`은 Pod 재시작마다 데이터가 날아가나? PVC가 있는데 왜?
- [ ] kind에서 PV는 노드 디스크 어디에 저장되나?

---

## 주의사항

### PostgreSQL 드라이버가 이미 BE classpath에 있나?

`be/storage/db-core/build.gradle.kts` 확인 필요.
`prod` 프로파일이 PostgreSQL을 쓰고 있으므로, 드라이버는 이미 포함되어 있을 가능성이 높다.
실습 전에 확인한다.

---

## 다음 단계 미리보기

- **Stage 4**: Ingress — port-forward 졸업, 도메인 기반 외부 노출
  - Ingress Controller (nginx) 설치
  - Ingress 리소스로 `/` → FE, `/api/` → BE 라우팅
  - TLS (cert-manager, Let's Encrypt)
