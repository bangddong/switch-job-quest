#!/usr/bin/env bash
# 배선된 훅이 "실제로 실행 가능한가"를 검사한다.
#
# 왜 이게 필요한가 (2026-08-11 발견):
#   `.claude/scripts/`의 .sh 19개 중 12개가 mode 644였고, 그 상태로 훅에 배선돼 있었다.
#   훅이 bare path로 호출되면 셸이 `permission denied`로 exit 126을 낸다. 그런데 훅 프로토콜에서
#   **차단은 exit 2뿐**이고 그 밖의 비정상 종료는 논블로킹 에러다 → 가드가 막지 못하고 조용히 통과한다.
#   `.claude/logs/`가 존재하지 않는 것이 증거였다: log-event.sh는 훅 5개 지점에 걸려 있고
#   `mkdir -p "$LOG_DIR"`이 무조건 실행되는데, 디렉토리가 없었다 = 단 한 번도 돈 적이 없다.
#
#   🔴 스크립트의 **로직**은 여러 번 검증했지만 스크립트가 **돌기는 하는지**는 아무도 안 봤다.
#      바로 앞 PR(#372)에서 check-wiki-question-candidates.sh의 버그를 고치고 테스트까지 했는데,
#      그 스크립트는 애초에 훅으로 실행되지 않았다.
#
# 검사 항목:
#   ① bare path로 호출되는 스크립트 → 존재 + **실행 권한**
#   ② 인터프리터로 호출되는 스크립트(python3/node/bash ...) → 존재 (mode는 무관)
#   ③ 레포 밖 절대경로 → 이식성 위반 (기기가 바뀌면 죽는다)
#
# 대상: .claude/settings.json 의 hooks + .claude/agents/*.md 의 frontmatter hooks
# 실패 시 exit 1. CI(design-integrity.yml)와 로컬 양쪽에서 돈다.

set -uo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "git 레포가 아닙니다"; exit 0; }
cd "$ROOT" || exit 0

echo "── 훅 배선 검사 ──"

python3 - "$ROOT" <<'PY'
import json, os, re, sys

root = sys.argv[1]
fail = []
checked = 0

INTERPRETERS = {"python3", "python", "node", "bash", "sh", "ruby", "perl"}

def check(origin, command):
    """훅 command 문자열에서 실제 파일 대상을 뽑아 검사한다."""
    global checked
    # `A || B` 형태의 폴백은 각각 검사 (예: python3 X || python X)
    for part in re.split(r'\|\||&&|;', command):
        part = part.strip()
        if not part:
            continue
        toks = part.split()
        if not toks:
            continue
        first = toks[0].strip('"\'')
        base = os.path.basename(first)

        if base in INTERPRETERS:
            if len(toks) < 2:
                continue
            target, needs_exec = toks[1].strip('"\''), False
        else:
            target, needs_exec = first, True

        # $(git rev-parse --show-toplevel) 전개
        target = target.replace('$(git rev-parse --show-toplevel)', root)
        if not target.startswith('.claude') and '.claude' not in target and not target.startswith('/'):
            continue  # 레포 스크립트가 아님 (외부 명령)

        checked += 1
        abspath = target if os.path.isabs(target) else os.path.join(root, target)

        # ③ 레포 밖 절대경로 = 이식성 위반
        if os.path.isabs(target) and not os.path.realpath(abspath).startswith(os.path.realpath(root)):
            fail.append(f"{origin}\n      🔴 레포 밖 절대경로 (다른 기기에서 죽는다): {target}")
            continue

        if not os.path.exists(abspath):
            fail.append(f"{origin}\n      🔴 파일 없음: {target}")
            continue

        # ① bare path 호출인데 실행 권한이 없다 → 훅이 exit 126으로 조용히 죽는다
        if needs_exec and not os.access(abspath, os.X_OK):
            mode = oct(os.stat(abspath).st_mode & 0o777)[2:]
            fail.append(
                f"{origin}\n      🔴 실행 권한 없음 (mode {mode}) — 훅이 exit 126으로 죽고 "
                f"'차단'이 아니라 '통과'가 된다: {target}\n"
                f"         고치기: git update-index --chmod=+x {target}"
            )

# ── settings.json
sp = os.path.join(root, '.claude', 'settings.json')
if os.path.exists(sp):
    with open(sp, encoding='utf-8') as f:
        cfg = json.load(f)
    for event, groups in (cfg.get('hooks') or {}).items():
        for g in groups:
            m = g.get('matcher', '(all)')
            for h in g.get('hooks', []):
                cmd = h.get('command', '')
                if cmd:
                    check(f"settings.json  {event}[{m}]", cmd)

# ── 에이전트 frontmatter (YAML 파서 없이 command: 줄만 뽑는다)
agents_dir = os.path.join(root, '.claude', 'agents')
if os.path.isdir(agents_dir):
    for name in sorted(os.listdir(agents_dir)):
        if not name.endswith('.md'):
            continue
        path = os.path.join(agents_dir, name)
        with open(path, encoding='utf-8') as f:
            text = f.read()
        parts = text.split('\n---\n', 1)
        head = text.split('---', 2)[1] if text.startswith('---') and text.count('---') >= 2 else ''
        for line in head.splitlines():
            mm = re.match(r'\s*-?\s*command:\s*"?(.+?)"?\s*$', line)
            if mm:
                check(f"agents/{name}", mm.group(1))

if fail:
    print(f"\n❌ 훅 배선 검사 실패 — {len(fail)}건 (검사 {checked}건)\n")
    for f_ in fail:
        print(f"   {f_}\n")
    print("   ⚠️ 이 항목들은 '설정돼 있지만 실행되지 않는' 상태다.")
    print("      켜져 있는 것처럼 보이는 가드가 꺼진 것보다 나쁘다.")
    sys.exit(1)

print(f"✅ 훅 배선 정상 — {checked}건 전부 실행 가능")
PY
