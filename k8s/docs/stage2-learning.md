# K8s Stage 2 학습 기록 — ConfigMap + Secret 패턴

> 목표: deployment.yaml에 하드코딩된 `SPRING_PROFILES_ACTIVE`를 ConfigMap으로 분리.
> "설정 변경 시 이미지 재빌드 불필요" 구조 완성.

---

## 0. 전체 그림

Stage 1 종료 시점의 문제:

```yaml
# 기존 deployment.yaml — 일반 설정이 매니페스트에 박혀있음
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "local"           # ← 이게 문제
envFrom:
  - secretRef:
      name: devquest-secrets
```

설정값이 늘어날수록 deployment.yaml이 지저분해지고,
환경(dev/prod)별로 다른 값을 쓰려면 매니페스트 자체를 복사해야 함.

**해결**: 설정은 ConfigMap, 비밀은 Secret — 역할에 맞게 분리.

```
Pod
├── envFrom: configMapRef(devquest-config)  ← 일반 설정 (평문)
└── envFrom: secretRef(devquest-secrets)    ← 민감 정보 (base64)
```

---

## 1. ConfigMap 생성

```bash
kubectl create configmap devquest-config \
  --from-literal=SPRING_PROFILES_ACTIVE=local
```

확인:
```bash
kubectl get configmap devquest-config -o yaml
```
```yaml
apiVersion: v1
data:
  SPRING_PROFILES_ACTIVE: local    # ← 평문 그대로 저장 (base64 아님)
kind: ConfigMap
metadata:
  name: devquest-config
  namespace: default
```

### 용어 심화

| 용어 | 의미 |
|------|------|
| **ConfigMap** | 비밀이 아닌 설정을 key-value로 저장하는 K8s 오브젝트. Secret과 구조 동일하지만 **평문 저장** |
| **Secret과 차이** | Secret = base64 인코딩, etcd에서 암호화 옵션 있음. ConfigMap = 평문, 누구나 `kubectl get configmap -o yaml`로 읽을 수 있음 |
| **용도** | 프로파일명, 로그 레벨, feature flag, 외부 서비스 URL 등 민감하지 않은 설정값 |
| **--from-literal** | 명령줄에서 직접 key=value 입력. 파일로 만들려면 `--from-file`, YAML로 선언적으로 만들려면 `kubectl apply -f configmap.yaml` |

> ConfigMap은 **"이 파일이 git에 올라가도 괜찮은가?"** 기준으로 선택.
> JWT_SECRET, DB 비밀번호 → Secret. 프로파일명, 포트 → ConfigMap.

---

## 2. deployment.yaml 수정

`env` 하드코딩 제거 → `envFrom`에 configMapRef 추가:

```yaml
# 변경 전
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "local"
envFrom:
  - secretRef:
      name: devquest-secrets

# 변경 후
envFrom:
  - configMapRef:
      name: devquest-config   # SPRING_PROFILES_ACTIVE 등 일반 설정
  - secretRef:
      name: devquest-secrets  # GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, JWT_SECRET
```

### 용어 심화

| 용어 | 의미 |
|------|------|
| **envFrom** | 오브젝트(ConfigMap/Secret)의 **모든 key를 한 번에** 환경변수로 주입. 키가 많아도 한 줄로 처리 |
| **configMapRef** | envFrom에서 ConfigMap을 참조. `name`은 ConfigMap 이름과 일치해야 함 |
| **secretRef** | envFrom에서 Secret을 참조. ConfigMap과 동일한 패턴 |
| **env vs envFrom** | `env` = key 하나씩 직접 지정 (유연하지만 장황). `envFrom` = 오브젝트 전체 주입 (간결하지만 key 전부 들어옴) |
| **선언 순서** | `envFrom`에 ConfigMap, Secret 순서로 선언. **key 충돌 시 나중에 선언된 쪽이 이긴다** (Secret이 ConfigMap을 덮어씀) |

---

## 3. 적용 및 Pod 재기동 확인

```bash
kubectl apply -f k8s/be/deployment.yaml
kubectl get pods -w
```
```
NAME                           READY   STATUS        RESTARTS   AGE
devquest-be-8544ff7758-4sbzv   1/1     Terminating   0          Xm
devquest-be-7d9f6c5b9-abcde    0/1     ContainerCreating   0    2s
devquest-be-7d9f6c5b9-abcde    1/1     Running       0          8s
```

deployment.yaml이 바뀌었으므로 **새 ReplicaSet**이 생성되고 기존 Pod는 종료됨.
Pod 이름 중간 해시(`8544ff7758` → `7d9f6c5b9`)가 바뀐 것이 그 증거.

### 왜 Pod가 재시작되나?

`envFrom`에 추가된 configMapRef는 **Pod 스펙 변경**이다.
K8s는 Pod를 in-place로 수정하지 않고 — 기존 Pod를 종료하고 새 Pod를 생성한다.
Deployment가 이 과정을 **롤링 업데이트**로 처리: 새 Pod Running 확인 후 기존 Pod 제거.

---

## 4. 환경변수 주입 확인

```bash
kubectl exec deployment/devquest-be -- env | grep -E "SPRING|GITHUB|JWT"
```
```
SPRING_PROFILES_ACTIVE=local     ← ConfigMap에서 주입
GITHUB_CLIENT_ID=dummy-client-id ← Secret에서 주입
GITHUB_CLIENT_SECRET=dummy-client-secret
JWT_SECRET=local-k8s-jwt-secret-...
```

ConfigMap과 Secret 양쪽 모두 Pod 안에 환경변수로 들어온 것 확인.

### 용어 심화

| 용어 | 의미 |
|------|------|
| **kubectl exec** | 실행 중인 컨테이너 안에서 명령어를 실행. `docker exec`와 같은 개념 |
| **`-- env`** | `--` 뒤는 컨테이너 안에서 실행할 명령어. `env`는 현재 환경변수 전체 출력 |
| **`deployment/devquest-be`** | Pod 이름 대신 Deployment로 지정 가능. 관리 중인 Pod를 자동으로 찾아줌 |

---

## 5. End-to-End 헬스 체크

```bash
# 터미널 1
kubectl port-forward service/devquest-be-service 8080:8080

# 터미널 2
curl http://localhost:8080/health
```
```json
{"data":"DevQuest API is running","error":null,"result":"SUCCESS"}
```

ConfigMap 분리 후에도 앱 정상 동작 확인.

---

## 학습 포인트 정리

### Q. ConfigMap 값을 바꾸면 Pod가 자동으로 새 값을 읽나?

**No.** 환경변수 방식(`envFrom`)은 **Pod 기동 시 1회 주입**된다.
ConfigMap을 `kubectl edit configmap devquest-config`로 바꿔도 이미 떠있는 Pod는 모름.
새 값을 적용하려면 **Pod를 재시작**해야 한다.

```bash
kubectl rollout restart deployment/devquest-be
```

> 볼륨 마운트 방식(`volumeMounts`)은 다르다 — 파일로 마운트된 ConfigMap은 변경 시 **자동으로 파일이 갱신**됨 (단, 앱이 파일 변경을 감지해야 반영됨).

### Q. envFrom에서 ConfigMap과 Secret key가 충돌하면?

**나중에 선언된 쪽이 이긴다.**

```yaml
envFrom:
  - configMapRef:
      name: devquest-config   # SPRING_PROFILES_ACTIVE=local
  - secretRef:
      name: devquest-secrets  # 만약 SPRING_PROFILES_ACTIVE도 있다면 → Secret 값으로 덮어씀
```

### Q. 파일 마운트 방식은 언제?

`application.yml`처럼 **구조가 있는 설정 파일 전체**를 주입할 때.
환경변수 방식은 단순 key=value만 가능하지만, 볼륨 마운트는 파일 형태로 통째로 주입 가능.

```yaml
volumes:
  - name: config
    configMap:
      name: devquest-config
containers:
  - volumeMounts:
      - name: config
        mountPath: /app/config/  # 이 경로에 ConfigMap key들이 파일로 생성됨
```

### Q. 환경별(dev/prod) ConfigMap 분리는?

지금은 `local` 프로파일 하나지만, 나중에 `devquest-config-dev`, `devquest-config-prod`처럼
**환경별 ConfigMap**을 따로 만들고 deployment.yaml에서 참조 이름만 바꾸는 방식.
이걸 체계적으로 관리하는 도구가 **Kustomize / Helm** (Stage 후반).

---

## 명령어 치트시트

```bash
# ConfigMap 생성
kubectl create configmap <이름> --from-literal=KEY=VALUE

# ConfigMap 목록/내용
kubectl get configmap
kubectl get configmap <이름> -o yaml

# ConfigMap 수정
kubectl edit configmap <이름>

# 수정 후 Pod 재시작 (환경변수 방식은 재시작 필요)
kubectl rollout restart deployment/<이름>

# 환경변수 주입 확인
kubectl exec deployment/<이름> -- env | grep <KEY>
```

---

## Stage 2 핵심 요약

1. **ConfigMap** — 평문 설정 저장. Secret과 구조 동일, 민감하지 않은 값에 사용
2. **envFrom 병용** — ConfigMap + Secret을 함께 쓸 수 있음. 충돌 시 나중 선언이 우선
3. **환경변수 방식은 기동 시 1회 주입** — ConfigMap 변경 후 Pod 재시작 필요
4. **볼륨 마운트** — 파일 형태 설정, 변경 시 자동 갱신 (앱 감지 필요)
5. **환경별 분리** — Kustomize/Helm으로 ConfigMap 이름만 교체하는 구조로 발전

---

## 다음 단계

- **Stage 3**: H2 in-memory → PostgreSQL StatefulSet
  - StatefulSet vs Deployment (영속성, 고정 네트워크 ID)
  - PersistentVolume / PersistentVolumeClaim
