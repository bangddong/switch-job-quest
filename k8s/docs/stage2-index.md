# K8s Stage 2 학습 인덱스 — ConfigMap + Secret 패턴

> 내일 학습할 내용의 목차/예습 노트. 실제 실습 기록은 학습 후 `stage2-learning.md`로 작성.

---

## 이번 단계 한 줄 목표

**설정(ConfigMap)과 비밀(Secret)을 분리**해서, deployment.yaml에 하드코딩된 `env`를
외부 오브젝트로 빼낸다. → "설정 변경 시 이미지 재빌드 불필요" 구조 만들기.

---

## 현재 상태 (Stage 1 종료 시점)

deployment.yaml에 env가 이렇게 섞여 있다:

```yaml
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "local"           # ← 일반 설정 (비밀 아님) → ConfigMap으로 분리 대상
envFrom:
  - secretRef:
      name: devquest-secrets # ← 비밀값 (이미 Secret) → 잘 되어 있음
```

문제의식:
- `SPRING_PROFILES_ACTIVE` 같은 **일반 설정**이 매니페스트에 직접 박혀 있음
- 설정값이 늘어나면 deployment.yaml이 지저분해지고, 환경(dev/prod)별 분리가 어려움

---

## 배울 개념

### 1. ConfigMap
- **비밀이 아닌 설정**을 key-value로 저장하는 오브젝트
- Secret과 구조 동일하지만 **base64 인코딩 안 함**(평문) — 민감하지 않으니까
- 용도: 프로파일명, 로그 레벨, feature flag, 외부 URL 등

### 2. ConfigMap 주입 방식 (Secret과 동일 패턴)
- `envFrom.configMapRef` — ConfigMap의 모든 key를 env로
- `env.valueFrom.configMapKeyRef` — 특정 key 하나만 골라서
- 볼륨 마운트 — 설정 파일(application.yml 등)을 파일로 주입 (고급)

### 3. ConfigMap vs Secret 선택 기준

| | ConfigMap | Secret |
|--|-----------|--------|
| 저장 형태 | 평문 | base64 (암호화 아님) |
| 용도 | 일반 설정 | 비밀번호·토큰·키 |
| 예시 | 프로파일, 로그레벨 | JWT_SECRET, API 키 |

---

## 실습 흐름 (예상)

```bash
# 1. ConfigMap 생성 (SPRING_PROFILES_ACTIVE를 ConfigMap으로)
kubectl create configmap devquest-config \
  --from-literal=SPRING_PROFILES_ACTIVE=local

# 2. deployment.yaml 수정 — env 하드코딩 제거, configMapRef 추가
#    envFrom:
#      - configMapRef:
#          name: devquest-config
#      - secretRef:
#          name: devquest-secrets

# 3. 적용 & 재기동 확인
kubectl apply -f k8s/be/deployment.yaml
kubectl get pods -w

# 4. 주입 확인 — Pod 안에서 환경변수 보기
kubectl exec deployment/devquest-be -- env | grep SPRING

# 5. 헬스 체크로 정상 동작 재확인
kubectl port-forward service/devquest-be-service 8080:8080
curl http://localhost:8080/health
```

---

## 확인하고 싶은 학습 포인트 (내일 질문거리)

- [ ] ConfigMap을 바꾸면 Pod가 자동으로 새 값을 읽나? (→ 아니다. 재시작 필요. 왜?)
- [ ] `envFrom`에 ConfigMap과 Secret을 둘 다 쓸 때 key 충돌하면 누가 이기나?
- [ ] 파일 마운트 방식은 언제 쓰나? (env 방식과 차이)
- [ ] 환경별(dev/prod) ConfigMap 분리는 어떻게? (→ Stage 후반 Kustomize/Helm 떡밥)

---

## 다음 단계 미리보기

- **Stage 3**: H2 in-memory → PostgreSQL StatefulSet
  - StatefulSet vs Deployment 차이 (영속성·고정 네트워크 ID)
  - PersistentVolume / PersistentVolumeClaim (데이터 영속)
- **Stage 4**: Ingress (port-forward 졸업, 도메인 기반 외부 노출)
