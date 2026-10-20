# prod DB 운영 런북 — EC2 Postgres 17 (📌D-016)

> prod DB 는 `1-network` 의 EC2 t4g.micro 한 대다(`Role=prod-db`). Fly(밤·주말)와 EKS(평일 창)가 **같은 DB** 를 쓴다.
> 결정·근거: `infra/aws-eks/README.md` D-016 · 계획서 `docs/superpowers/plans/2026-10-16-prod-eks-weekday-window.md` 「5번」「6번」.
> 🔴 이 디렉터리는 **prod 전용**이다. 학습 클러스터용 `../db-backup.sh`·`../db-restore.sh` 와 섞지 마라(N-17).

## 지금 무엇이 자동으로 도는가

| 무엇 | 언제 | 어디서 | 결과 |
|---|---|---|---|
| 백업 `backup.sh` | 매일 03:30 KST(±10분) | EC2 systemd `devquest-db-backup.timer` | `s3://devquest-eks-backups-seoul/prod-db/dumps/devquest-<UTC>.{dump,counts}` + `health/backup/<UTC>_disk=_bytes=_tables=` |
| 복원 판정 `restore-check.sh` | 매주 일 04:30 KST | EC2 systemd `devquest-db-restore-check.timer` | 최신 덤프 → 스크래치 DB → 행 수·Flyway 이력 **값 비교** → `health/restore/<UTC>_secs=_tables=` |
| 감시 `watch.sh` | 매일 11:00 KST | GitHub Actions `Prod DB Backup Watch`(읽기 전용 역할) | 헬스 키 신선도(백업 26h · 복원 8일)·디스크 80%·테이블 ≥1. **빨강 = 실패 메일** |

보존: 버킷 lifecycle **30일**(현행·이전 버전 모두). 🔴 백업이 멈추면 30일 뒤 **마지막 정상 덤프까지 사라진다** — 감시 메일을 무시하지 마라.

## 접속

SSH 포트는 없다. SSM 으로만 들어간다.

```bash
ID=$(tofu -chdir=infra/aws-eks/1-network output -raw db_instance_id)
aws ssm start-session --region ap-northeast-2 --target "$ID"
sudo -i
runuser -u postgres -- psql -d devquest
```

비밀번호 원천은 SSM `/devquest/prod/db/password`(EC2 가 첫 부팅 때 만든다). 로컬 접속엔 필요 없다(peer 인증).

## 도구 설치·갱신

스크립트를 고쳤으면 **머지한 뒤** 로컬에서:

```bash
bash infra/aws-eks/scripts/prod-db/deploy.sh      # HEAD == origin/main 이어야 한다
```

S3 `prod-db/tooling/<sha>.tgz` 로 올리고 SSM Run Command 로 `install.sh` 를 돌린다. **Postgres 를 재시작하지 않는다.**
🔴 user_data 를 다시 돌리지 마라(`/var/lib/cloud/instance/scripts/part-001`) — Postgres 를 재시작해 Fly·EKS 가 함께 끊긴다.

## 손으로 돌리기

```bash
systemctl start devquest-db-backup.service        && journalctl -u devquest-db-backup -n 5 -o cat
systemctl start devquest-db-restore-check.service && journalctl -u devquest-db-restore-check -n 5 -o cat
bash infra/aws-eks/scripts/prod-db/watch.sh        # 로컬(자격증명 필요) — 헬스 키 판정
```

## 🔴 복구 — DB 가 망가졌을 때 (RTO 실측: 8.7MB 기준 복원 1초)

**고르는 덤프 규칙: `.counts` 짝이 있는 최신.** 백업이 중간에 죽으면 잘린 `.dump` 만 남는다(인스턴스 역할에 Delete 가 없어 지우지 못한다).

1. **쓰기를 멈춘다** — Fly 를 0대로(`fly scale count 0 -a <앱>`), 창이 열려 있으면 EKS core-api replicas 0. (복원 중 쓰기가 섞이면 판정이 무의미하다)
2. EC2 에서 마지막 정상 덤프를 확인한다:
   ```bash
   aws s3 ls s3://devquest-eks-backups-seoul/prod-db/dumps/ | grep counts | sort | tail -3
   ```
3. 현재 DB 를 지우지 말고 **이름을 바꿔 남긴다**(되돌릴 길):
   ```sql
   ALTER DATABASE devquest RENAME TO devquest_broken_<날짜>;
   CREATE DATABASE devquest OWNER devquest;
   ```
4. 복원(소유자 devquest):
   ```bash
   aws s3 cp s3://devquest-eks-backups-seoul/prod-db/dumps/devquest-<UTC>.dump - \
     | runuser -u postgres -- pg_restore --no-owner --no-acl --role=devquest --single-transaction --exit-on-error -d devquest
   ```
5. **판정** — 덤프 시점 행 수와 비교한다(`app.jar validate` 는 빈 DB 도 통과시키므로 판정 도구가 아니다, B6-3):
   ```bash
   aws s3 cp s3://devquest-eks-backups-seoul/prod-db/dumps/devquest-<UTC>.counts /tmp/expected
   # common.sh 는 set -e 를 켠다 — 대화형 셸에 source 하지 말고 서브 bash 로
   bash -c '. /opt/devquest-db/common.sh; local_counts devquest' | diff -u /tmp/expected - && echo 일치
   ```
6. Fly 를 다시 1대로. 확인 뒤 `devquest_broken_*` 를 지운다(10GiB 볼륨 — 오래 두지 마라).

**EC2 자체가 죽었을 때**: 데이터 볼륨은 별도 EBS(`prevent_destroy`)다. 인스턴스를 교체하면 user_data 가 볼륨을 다시 붙이고 **설정도 볼륨에 있어** 그대로 뜬다(5-A 리허설 통과). 볼륨까지 잃었으면 위 복원 절차를 새 볼륨에서.

**계정이 닫혔을 때**: Neon 콜드 대기(📌D-016 Q-2) — 7번 전환 뒤 매일 복원이 켜지면 Fly 의 DB 연결만 Neon 으로 돌린다. 그 전엔 Neon 이 prod 원본이다.

## 패치 (B5-8)

Fly·EKS 가 **한 DB** 를 쓰므로 재시작 = 두 경로 동시 장애. 창 **밖**(밤·주말 = Fly 만 붙어 있을 때), 트래픽이 적은 시간에.

```bash
apt-get update && apt-get -s upgrade | grep -i postgres      # 무엇이 바뀌는지 먼저 본다
systemctl start devquest-db-backup.service                   # 직전 백업
apt-get upgrade -y                                           # 마이너 패치(17.x). 메이저 업그레이드는 별도 계획
systemctl restart postgresql@17-main && systemctl is-active postgresql@17-main
```

커널 패치로 재부팅이 필요하면(`/var/run/reboot-required`) 같은 창에서 `reboot` — 부팅 후 WireGuard·Postgres 가 자동으로 뜬다(systemd enable). 설정 튜닝은 재시작이 아니라 **별도 `conf.d` 파일 + `pg_ctlcluster 17 main reload`**.

## 전환(7번) 뒤

- 🔴 트래픽을 EC2 로 돌리기 **전에** `deploy.sh --cutover-done` — 데이터 볼륨에 표식을 남겨 `neon-import.sh` 를 영구히 막는다(인스턴스를 교체해도).
- Neon 읽기 전용 role(`devquest_ro`)과 SSM `neon-source-url` 은 쓸 일이 없어진다 — 삭제.
- Neon 콜드 대기 매일 복원을 켠다(7번 범위).

## 테스트

```bash
bash infra/aws-eks/scripts/prod-db/tests/prod-db-test.sh     # docker — 진짜 Postgres 17 + 목 aws, $0
```
