# JVM · 관측 현장 노트 (prod = Fly)

> **출처**: `.claude/CONTEXT.md` 「알아둬야 할 비자명적 결정」에서 이관 (2026-09-18, 원장 L-10).
> 여기 있는 것은 **재조사하면 비싼 실측 상수와 조회 레시피**다. 조사 서사가 아니라 **재사용 대상**이다.
>
> ℹ️ 이 문서에는 결정 메타(📌 `D-`)가 없다 — 있었다면 `check-design-integrity.sh` 의 `DOCS`
> 고정 목록 밖이라 감시 사각지대가 됐을 것이다.

## 1. 메타스페이스 — 조사 종결, 누수 없음 (2026-07-15) 🔴

Grafana 7일 range 실측으로 확정. **재조사 불필요.**

```
이 앱의 메타스페이스 작동점 = 134.6 MiB
  160m 하에서 uptime 94.3h 까지 평탄 (95포인트 연속 실측)
  #263 의 128m 은 작동점보다 6.6 MiB 낮았다 → 죽는 게 필연. 누수와 무관
```

- **128m 창의 실제 모양**: 재시작 후 122.7 → 10.9h 에 126.3 MiB(상한의 **98.7%**) → **9시간 고정**
  (상한이 눌러 Full GC 로 버팀) → **uptime ~20h 에 전 지표 소실**(좀비화).
  ⚠️ *"34시간 후 OOME"* 는 **틀린 기록**이다 — 34h 는 사람이 알아채고 #265 를 배포한 시각.
- **누수 아님의 근거 3종** 🔴: ①클래스 수 평탄~순감소(11분 +12개, 부하 중 −51개)
  ②정지 30초 **Δ=0 바이트** ③동일 부하 2R 이 1R 의 51%(감속).
  → 시간이 아니라 **처음 밟는 코드 경로**가 키운다.
- **계단의 정체**: ~~**매일** 00:00 UTC(=09:00 KST) `DailyMailScheduler` 가 돌 때 **+4.2 MiB** 점프 후 평탄.~~
  전형적 **지연 로딩**.
  🔴 **2026-09-24 실측으로 *"매일"* 을 기각한다 — 1회성이다(JVM 기동 후 첫 실행).**
  ```
  누적이면 예상   138.8 + 4.2 × 5.81일 = 163.2 MiB
  실측(09-24)                           140.11 MiB
  → 누적이었다면 상한 160 을 넘겨 이미 OOME 로 죽어 있어야 한다
  ```
  ⚠️ **이 귀류법은 §2 에 의존한다 (QA F-3)**: *"Serial 은 Full GC 때만 클래스를 언로드하고,
  메타스페이스가 상한을 쳐야 비로소 Full GC 가 돈다"* — 그래서 **중간에 언로드로 카운터가
  초기화됐을 가능성이 배제된다.** 이 전제가 없으면 논증이 성립하지 않으므로 명시해둔다.
  같은 줄이 적어둔 *"전형적 지연 로딩"* 이 맞다. ***"매일" 이라는 단어 하나가 1회성 현상을
  반복 현상으로 바꿔놨고***, 그 위에 `TASK-12` 의 *"08:30/09:30 갈라 찍기"* 절차가 통째로 세워졌다
  (전제가 사라져 폐기). 상세: `plans/2026-09-11-prod-eks-migration-prereqs.md` 「항목 4 — 종료」④.
- **통합 가설 기각** 🔴 — 메타스페이스 성장은 0.73 MiB/h 이고 평상시 0 이라 **RSS creep 3MB/h 를 설명 못 한다.**
  두 리스크는 별개다.
- ~~**잔여 여유 25.4 MiB(16%)**~~ → 🔴 **재측정: 여유 21.2 MiB(13%) (2026-09-18).**
  **작동점이 134.6 → 138.8 MiB 로 ~4.2 MiB 올랐다.** 더 낮은 업타임(70.8h)에서 더 높은 값이므로
  **평탄 구간의 높이 자체가 상승**한 것이다 — Phase 2 로 모듈이 늘어난 결과로 보인다(🟡).
  이 절이 지시한 *"신규 대형 의존성 추가 시 작동점 재확인"* 이 실제로 필요했던 사례다.
  🔴 **대안 가설을 배제하지 못했다 (QA 부수 발견).** 상승분 **4.2 MiB** 는 이 절이 이미 기록한
  *"매일 00:00 UTC `DailyMailScheduler` 발동 시 **+4.2 MiB** 점프"* 와 **크기가 정확히 같다.**
  즉 *"Phase 2 로 작동점이 올랐다"* 와 *"이미 알려진 일일 점프를 한 번 더 관측했다"* 를
  **구분할 근거가 없다.** → 재측정 시 **스케줄러 발동 전후로 각각 찍어** 이 둘을 갈라라.
  ⚠️ ~~**160m 상한까지 13% 밖에 안 남았다.** 다음 모듈 추가 전에 다시 재라.~~
  🔴 **재측정 (2026-09-24) — 12.4% 로 더 줄었고, 같은 JVM 안에서 계속 오른다.**
  ```
  used      140.11 MiB / max 160.00  =  87.6%     여유 19.89 MiB
  committed 141.00 MiB                            여유 19.00 MiB
  동일 JVM(부팅 09-15 06:50Z) 안에서  138.80 → 140.11  =  +1.31 MiB / 139.5h  =  +0.225 MiB/일
  → 상한 도달까지 used 기준 88일 · committed 기준 84일
  ```
  🔴 **이건 컨테이너 `limits` 로 안 고쳐진다** — `-XX:MaxMetaspaceSize=160m` 은 별개이고, 닿으면
  **2026-07-14 와 같은 경로**(`Pause Full (Metadata GC Threshold)` 무한 반복 → `OOME: Metaspace`)다.
  **조건은 *"`be/**` 배포 없이 ~85일"*** — 재배포가 JVM 을 리셋한다. 관측된 최대 배포 공백은 15일이라
  **🟢 판정.** ⚠️ ~~이 레포는 #361(08-06) 이후 `feat` 커밋이 0건이다~~ → **거짓 (QA F-2)**:
  `git log --since=2026-08-06 -- be/` 에 `feat(be)` **3건**(#395·#397·#403). 출처인 `CLAUDE.md`
  하네스 동결 규칙의 그 문구가 **stale** 하다(08-16/17 작성 후 `be/` 에 feat 이 계속 들어왔다).
  🔑 ***레포 문서를 인용할 때도 날짜를 본다 — `CLAUDE.md` 라고 해서 현재형인 것은 아니다.***
  소유: `.claude/CONTEXT.md` 제품 백로그.

> 🔑 **최대 교훈**: 이 사고의 답은 **배포 당시 이미 Grafana 그래프에 찍혀 있었다**(07-08 시점 135~137 MiB).
> ***상한을 자르기 전에 그래프의 작동점을 먼저 본다.*** 5분이면 막을 수 있었다.

## 2. GC 는 SerialGC 다 — G1 아님 (2026-07-15) 🔴

- prod 실측 라벨: `gc="Copy"`(Serial Young) + `gc="MarkSweepCompact"`(Serial Old).
  512MB + shared-cpu-1x 라 JVM 인체공학이 자동 선택(2코어 미만 & 1792MB 미만 → SerialGC).
- **`G1PeriodicGCInterval` 검토 항목은 폐기** — 전제부터 틀렸다. G1 옵션은 이 앱에서 전부 무효.
- #263 의 2초짜리 `Pause Full` 은 이상 현상이 아니라 **Serial Old 단일스레드 컴팩션의 정상 비용.**
- 🔴 **Serial 은 Full GC 때만 클래스를 언로드한다.** 힙이 45M/179M 라 Full GC 가 거의 안 돌아
  언로드가 사실상 정지(11.8h 에 379개). **메타스페이스가 상한을 쳐야 비로소 Full GC 가 돈다.**

> 결론과 파생 판단(반환에 인색하다)은 `docs/superpowers/plans/2026-09-05-memory-rightsizing.md`
> (F-7)에도 있다. 위 **라벨 실측값**과 **언로드 조건**은 여기에만 있다.

## 3. prod JVM 지표 조회법 — jcmd 없는 JRE 이미지 우회 (2026-07-15)

프로덕션 이미지는 **JRE 전용**이라 `jcmd`/`jmap`/`jstat` 이 없다
(`java jfr jrunscript jwebserver keytool rmiregistry` 만). 어태치 기반 진단 불가.
**대신 액추에이터를 머신 내부에서 친다:**

```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
fly ssh console -a devquest-api \
  -C "/bin/sh -c 'wget -qO- localhost:8080/actuator/prometheus | grep Metaspace'" < /dev/null
```

**근거 — `SecurityConfig.kt` 현재 상태 (2026-09-18 기준):**

```kotlin
permitAll:  /api/v1/auth/**  /health  /actuator/health  /actuator/health/readiness
/actuator/**:  hasIpAddress('127.0.0.1') or hasIpAddress('::1')
anyRequest:    authenticated
```

> 🔴 **종전 서술 2곳이 틀려 있었다** (2026-09-18 발견·정정):
> ① ~~`or hasIpAddress('fdaa::/16')`~~ → **#424 에서 삭제**됐다(Fly 6PN 절, 소비처 0건 + prod 403 실측).
> ② ~~*"`/health`·`/actuator/health` 만 공개"*~~ → **#374 로 `/actuator/health/readiness` 도 공개**다.
>    kubelet 이 파드 IP(127.0.0.1 아님)로 readiness 를 찌르는데 IP 제한에 걸리면 **파드가 영영 Ready 가 안 된다.**
>
> ⚠️ 즉 위 `fly ssh console` 레시피는 **여전히 유효**하다(머신 내부 = 127.0.0.1). 외부에선 403.

- `fly ssh console` 은 Windows 에서 끝에 `Error: The handle is invalid` 를 뱉지만 **출력은 정상** — 무시.
  `< /dev/null` 을 붙이면 tty 문제가 완화된다.
- ⚠️ **`[metrics]` 섹션이 `be/fly.toml` 에 없다** → Fly 는 앱 메트릭을 스크레이프하지 않는다.
  JVM 지표는 **오직 Grafana Cloud(OTLP push)** 에만 있다.

## 4. Grafana Cloud 스택 접근법 (2026-07-15) — 좌표는 로컬에만

> ⚠️ **스택 slug/URL 을 여기 적지 않는다.** 이 레포는 **공개**라 테넌트 식별자를 남기면
> 표적 피싱의 과녁이 된다. **그래서 이 절에는 대체 출처가 원리적으로 존재할 수 없다.**

**찾는 법**: Chrome 에 grafana.com 세션이 살아 있으면 `fetch('/api/instances')` → `slug`·`url`·`status`.
instance id 는 `application-prod.yml` 의 `grafana.otlp.instance-id` 와 일치하는지로 검증한다.

- 🔴 **무료 플랜은 UI 가 자동 슬립한다** — `/api/instances` 가 `status: "paused"`, `pausedAt: null`.
  **수동 pause 가 아니다.** 스택 URL 로 접속하면 `Grafana is loading...` 후 ~1분 내 기동.
  **Prometheus 수집은 계속된다.**
- 조회는 브라우저 세션의 datasource proxy fetch (스크린샷 불필요):

```js
fetch('/api/datasources/proxy/uid/grafanacloud-prom/api/v1/query_range?query='
  + encodeURIComponent('jvm_memory_used_bytes{area="nonheap",id="Metaspace",application="devquest-api"}')
  + '&start=<epoch>&end=<epoch>&step=1800', {credentials:'include'}).then(r=>r.json())
```

- 🔴 **단위 함정**: Grafana 는 **MiB(2²⁰)** 로 표기, actuator raw 는 바이트.
  `138,162,760 B = 131.8 MiB`. MB(10⁶)와 섞어 비교하면 **없는 문제를 만든다**(07-15 에 실제로 오경보).

## 5. flyctl 이 `config.yml` 토큰을 자동 로드하지 못함 (2026-07-15)

**매 세션 30분 낭비 방지용 항목이다.**

- 증상: `fly auth whoami` → `no access token available` (**로컬 판정, 네트워크 요청 없음**).
  토큰은 `~/.fly/config.yml` 에 멀쩡히 있고(665자) fly.io 콘솔 토큰은 `Expires: Never`. **만료 아님.**
- 원인 **불명 ⚪** (flyctl v0.4.54 / Windows).
  🔴 **HOME 경로 이론은 반증됐다** — Go 는 Windows 에서 `HOME` 을 무시하고 `USERPROFILE` 을 본다
  → `HOME=...` 실험은 **무효다. 재현 시 그 실험을 반복하지 마라.**
- **해결 — env 주입** (이 프로젝트의 확립된 패턴, 과거 12회 사용. 리터럴 붙여넣기 0회):

```bash
export FLY_API_TOKEN=$(cat ~/.fly/config.yml | grep access_token | awk '{print $2}')
```

  ⚠️ 값을 절대 출력하지 말 것 — 프리픽스만 찍어도 자격증명 실체화로 차단된다.

## 6. OOM 진짜 원인 — 순수 누적형 RSS 포화, kill 수위 `anon-rss` ~409MB (2026-07-07, #245)

#239 JVM 튜닝 후에도 재발. **커널 OOM 로그 7일 전수(8건)** 로 인과 확정:

- **모든 kill 에서 java `anon-rss` 가 406~410MB 로 동일** — 시각은 제각각(새벽·오전·오후·저녁).
  RSS 가 시간당 ~3MB 씩 차올라 ~409MB 에 닿으면 **시각 무관 사망.**
  ⚠️ **이벤트(메일 잡 등)는 무관** — 8건 중 09시 메일 직후는 2건뿐. 상관관계 과대 해석 주의.
- **creep 정체**: JVM 이 커밋해둔 페이지(총 314MB)를 서서히 실제 터치 + native ~95MB
  → RSS 천장 = 커밋총량 + native ≈ **409MB = kill 수위.** JVM 지표(used/committed)로는 **안 보인다.**
- 부팅 직후 가용 마진 **~44MB뿐**(총 459MB). 사망 시점 HTTP/AI 요청은 **항상 0건**.
- 대응 1차: `fly.toml` `swap_size_mb = 256`(#245 배포). creep 이 스왑도 채우면(+3.5일) 재발 가능
  → **업타임 4~5일째 `fly_instance_memory_swap_free` 관찰.**
- 최후 수단: 1GB 스케일업 (~$5.7/월).

### 🔴 2026-09-18 재측정 — creep 은 **여전히 진행 중**이고 swap 이 막고 있다

`fly ssh console` 로 머신 내부 직접 조회(업타임 **70.8h = 2.95일**):

```
MemTotal      459 MiB          ← Fly 머신 전체
MemAvailable   26.6 MiB        ← 문서의 "부팅 직후 ~44MB" 보다 더 빡빡해졌다
SwapTotal     256 MiB
SwapFree      223 MiB   →  사용 32.6 MiB (13%)
java VmRSS    386.7 MiB        ← 기록된 kill 수위 ~409 MiB 대비 22 MiB 아래
```

- **kill 이 안 난 이유가 확인됐다**: RAM 이 바닥나자(가용 26.6 MiB) **32.6 MiB 가 swap 으로 나갔다.**
  즉 `swap_size_mb = 256`(#245)이 **설계대로 작동 중**이고, 그게 유일한 방어선이다.
- 🔑 **그래서 이 수치가 prod→K8s 이관의 선행 조건을 직접 판정한다** — K8s 노드엔 swap 이 없다.
  ```
  Fly  실효 = 앱 상한 409 + swap 256 = 665 MiB
  K8s  실효 = limits  576 + swap 0   = 576 MiB      ← 89 MiB(13%) 적다
  ```
  ***핵심은 "swap 이 없다" 가 아니라 "Fly 의 앱 상한(409)이 K8s limits(576)보다 작아서
  swap 을 뺀 총량이 역전된다" 는 것이다.***

  > 🔴 **재판정 (2026-09-24) — 방향은 맞다. 틀린 것은 긴급도다.** 표본 3개 실측(동일 JVM):
  > ```
  > 총 익명 발자국(RSS + swap)   09-18 419.70  →  09-24 428.47 MiB   (+0.063 MiB/h)
  > Fly  용량 665 (409 + swap 256)   여유 236.53   런웨이 157일(장창) / 64일(단창)
  > K8s  용량 576 (swap 0)           여유 147.53   런웨이  98일(장창) / 40일(단창)
  > → K8s 가 더 좁다는 위 결론은 옳다. 틀린 것은 "런웨이 2.6일"(3 MB/h 가정).
  > ```
  > ⚠️ **나는 이 재판정을 한 번 틀리게 썼다 (QA HIGH)** — *"Fly RAM 천장 409 엔 안 들어가고 K8s 576 엔
  > 들어가니 방향이 뒤집힌다"*. Fly 에만 RAM-only 천장을 대고 K8s 엔 총량을 댄 **비일관 비교**였다.
  > ***swap 은 `#245` 에서 의도적으로 넣은 방어선이고 지금 설계대로 작동 중이다.***
  >
  > 🔑 **살아남는 관찰 — 전환 함정**: K8s 엔 swap 이 없으므로 지금 swap 에 있는 **53.55 MiB 가
  > 전부 resident** 가 된다. **Fly 의 `RSS`(374.92)만 보고 `limits` 를 잡으면 53.6 MiB 과소산정**이다.

- ~~**남은 미지수 = 현재 creep 속도.** 3MB/h 는 2026-07 측정값이다.~~
  🔴 **해소 (2026-09-24) — `3 MB/h` 는 기각됐다. 실측 `0.063 MiB/h` 로 54배 과대였다.**
  ```
  3 MB/h 면 09-23 RSS = 386.7 + 3×129 = 774 MiB
  그런데 이 머신은 RAM 459 + swap 256 = 715 MiB   → 물리적으로 불가능
  ```
  ⚠️ **기울기가 안정적이지 않다**: 긴 창(139.5h) 0.063 vs 짧은 창(10.5h) 0.155 MiB/h.
  런웨이는 구간으로 읽는다 — **40~98일**(옛 값 2.6일과 자릿수가 다르다).
  🔑 **RSS 는 단조 감소하는데 swap 사용은 단조 증가한다** — 커널이 식은 페이지를 밀어낸다.
  **RSS 만 보면 *"줄고 있다"* 는 반대 결론이 난다.** 반드시 둘의 합으로 본다.
  → **같은 명령을 며칠 간격으로 두 번 더 찍으면 기울기가 나온다. 클러스터 불필요, $0.**
  swap 사용량 증가분이 곧 RAM 초과분이라 K8s(swap 0) 런웨이로 직접 환산된다.
  ⚠️ *"클러스터를 2~3일 켜서 OOMKill 을 본다"* 보다 낫다 — 관측 대상이 **prod 실트래픽**이고,
  **$0** 이며, **한 번도 죽이지 않고** 답이 나온다.

### 조사 방법 메모 (재조사 시 그대로 재사용)

| 무엇 | 어떻게 |
|---|---|
| PromQL 수치 직접 획득 | Grafana/fly-metrics.net 페이지에서 `fetch('/api/datasources/proxy/uid/<uid>/api/v1/query_range?...')`. **fly 쪽 uid `prometheus_on_fly`, 앱 스택 uid `grafanacloud-prom`** |
| 머신 RSS (JVM 지표에 안 보이는 creep) | `fly_instance_memory_mem_available{app="devquest-api"}` |
| 재시작 전수 | `process_uptime_seconds` 리셋 감지 ⚠️ 배포·autostop 과 섞이므로 **OOM 확정은 커널 로그로** |
| 커널 OOM 로그 | fly-metrics.net `application_logs_vlog` datasource · LogsQL `"fly.app.name":"devquest-api" "Out of memory: Killed process" _time:7d` → `anon-rss` 수치 |

## 7. Metaspace OOME 사고에서 남은 **진단 규약** (2026-07-14, #263→#265)

> 사고 서사 자체는 `.claude/CONTEXT.archive.md`(#263·#265·#239 행)와
> `plans/2026-09-05-memory-rightsizing.md:201-233` 에 있다. 여기는 **재사용할 규약만** 남긴다.

- **힙 실측: 사용 42MB / 커밋 117MB / 상한 179MB** (prod GC 로그 직접 확인 🔴).
  힙은 남아돈다 — 메모리 튜닝 시 **힙을 늘리는 방향은 근거가 없다.** 더 줄일 여지가 있는 쪽이다.
- 🔴 **진단 규약 — GC 트리거 괄호를 먼저 읽어라.**
  `(Allocation Failure)` 면 힙, `(Metadata GC *)` 면 메타스페이스.
  **화살표 좌우가 안 줄면(`42M->42M`) 힙 문제가 아니다.**
- 🔴 **`-Xlog:gc` 제거 금지.** 이게 없었으면 진단 자체가 불가능했다(#263 이 우연히 같이 넣었다).
  단 **변경과 관측을 동시에 넣으면** *"원래 있던 현상"* 과 *"새로 생긴 현상"* 을 구분 못 하는 confound 가 생긴다.
- **프로세스 규약**: #263 은 힙·메타스페이스·코드캐시 **3개를 실측 없이 동시에** 잘랐다
  (커밋 메시지에 *"근사치, 배포 후 실측 검증 필요"* 라 스스로 적고도 배포했다).
  → ***리소스 상한은 live set 실측 후에 자른다. 한 번에 하나씩.***

## 8. AI 메트릭 대시보드 "0으로 보임" — 버그 아님 (2026-07-01)

- 저트래픽 앱에서 `increase()` 기반 패널은 **원래 0으로 보인다**(신규 시리즈 콜드스타트 + 증가량 없음).
  raw 카운터를 Explore 에서 `increase()` 없이 직접 쿼리하면 실제 값이 나온다.
  **재발해도 코드 재조사 불필요.**
- AI 카운터(`gen_ai_*`, `ai_*`)는 **첫 AI 호출 때 lazy 등록** — 재시작 후 호출이 없으면 **시리즈 자체가 소멸**한다
  (JVM 메트릭은 기동 시 즉시 재등록되어 남는다).
  🔑 **시리즈 부재 = "그 구간 AI 호출 0건" 의 증거로 쓸 수 있다.**
- ⚠️ **OTLP resourceAttributes 에 instance 라벨이 없다** → 멀티 머신이 되면 시리즈 충돌.
  1대라 현재는 무해하지만 **스케일아웃 시 필수 수정**이다.

## 9. Grafana 대시보드 = 신형 v2 스키마 + table 패널의 `format: "table"` (2026-07-02)

- 라이브 대시보드는 신형 v2 스키마(`kind: Panel`, `elements` 맵, `RowsLayout`).
  IaC 소스는 `grafana/ai-metrics-dashboard.json`(신형으로 동기화됨).
- 🔴 **table 패널: `instant: true` 만으론 부족하고 `format: "table"` 이 필수다.**
  빠지면 기본 `time_series` 라 라벨 컬럼이 안 나오고 merge/organize transform 이 깨진다.
- ⚠️ **대시보드 편집 시 자동 새로고침 OFF 필수** — 미저장 편집 삭제 + 렌더러 OOM/CDP 프리즈.
  파일의 `timeSettings.autoRefresh` 는 `""`(off) 유지.

> 🔑 JSON 파일은 *"무엇"* 만 담는다. 위 두 줄(**왜 그래야 하는가**·**편집 규칙**)은 파일에 없어서
> 여기 남긴다 — 대시보드를 다시 만질 때 같은 함정을 다시 밟는다.
