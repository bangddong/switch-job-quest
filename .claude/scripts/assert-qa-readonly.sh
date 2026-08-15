#!/usr/bin/env bash
# qa-reviewer 전용 PreToolUse(Bash) 가드 — "읽기는 자유, 쓰기는 qa-cache/만".
#
# 왜 필요한가 (2026-08-11 발견 → 08-12 조치):
#   qa-reviewer의 도구 목록에서 Write·Edit를 빼 "코드 수정 금지"를 기계로 만들었다. 그런데
#   Bash가 남아 있어서 `echo ... > file` 한 줄이면 그대로 뚫린다. 리뷰어가 자기가 리뷰하는
#   코드를 고칠 수 있으면 리뷰가 아니다(자기승인).
#   design-reviewer는 permissionMode: plan 이라 완전히 막히는데, qa-reviewer는 그럴 수 없다 —
#   `git diff`로 리뷰하고 `.claude/qa-cache/`에 마커·findings를 써야 하기 때문이다(에이전트 정의 105행).
#   → 도구를 빼는 대신 **명령 단위로** 가른다.
#
# 설계 선택 — 허용목록(allowlist)을 쓴다:
#   금지목록은 반드시 샌다(kubectl·tofu·npm·python -c … 끝이 없다). 반면 "리뷰어가 필요한 명령"은
#   작고 열거 가능하다. 모르는 명령은 막고, 필요하면 orchestrator가 **의도적으로** 넓히게 한다.
#   ⚠️ 과하게 성가신 가드는 우회당한다(08-12 assert-no-admin.sh에서 겪음). 그래서 차단 메시지는
#      "우회하라"가 아니라 **"보고하라"**로 안내한다 — 에스컬레이션이 올바른 탈출구다.
#
# 통과/차단 요약:
#   통과: git diff/log/show/status …, gh pr view/diff, grep·rg·find·cat·jq·awk·sed -n …,
#         `> .claude/qa-cache/…` 로의 리다이렉트, mkdir -p
#   차단: 그 외 경로로의 쓰기, sed -i, rm/mv/cp/chmod, git add/commit/push/checkout …,
#         gh pr merge/create/edit …, 목록에 없는 명령 전부

set -uo pipefail

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null || echo "")
[ -n "$COMMAND" ] || exit 0

python3 - "$COMMAND" <<'PY'
import re, sys, os

cmd = sys.argv[1]

# ── heredoc 본문은 데이터다 ──────────────────────────────────────
# findings 본문에는 리뷰 대상 코드가 인용된다 — `rm -rf`나 `>` 가 그대로 들어갈 수 있다.
# 그걸 명령으로 읽으면 정상 리뷰가 막힌다(08-12 assert-no-admin.sh와 같은 오탐).
lines, out, tag = cmd.split('\n'), [], None
for ln in lines:
    if tag is not None:
        if ln.strip() == tag:
            tag = None
        continue
    out.append(ln)
    m = re.search(r'<<-?\s*[\'"]?([A-Za-z_][A-Za-z0-9_]*)[\'"]?', ln)
    if m:
        tag = m.group(1)
cmd = '\n'.join(out)

QA_DIR = '.claude/qa-cache/'

ALLOWED = {
    # 셸 키워드·구조
    'if','then','else','elif','fi','for','while','do','done','case','esac','[',']','[[',']]',
    'test','true','false','local','export','set','shift','return','echo','printf',
    # 읽기·분석
    'cat','head','tail','wc','grep','egrep','fgrep','rg','find','ls','file','stat','diff','comm',
    'sort','uniq','cut','tr','sed','awk','jq','yq','basename','dirname','realpath','xargs','tee',
    'date','env','pwd','which','command','type','column','fold','nl','od','md5','shasum',
    # 쓰기가 필요한 최소한 (대상은 아래에서 따로 검사)
    'mkdir','git','gh',
}

GIT_READONLY = {
    'diff','log','show','status','ls-files','rev-parse','rev-list','branch','blame','cat-file',
    'merge-base','describe','shortlog','name-rev','symbolic-ref','config','check-ignore','grep','remote',
}
GH_READONLY = {'pr','api','repo','run','issue','workflow','browse','search','label','release'}
# gh pr <sub> 중 상태를 바꾸는 것
GH_PR_MUTATORS = {'merge','create','close','reopen','edit','review','comment','ready','checkout','lock','unlock'}

def fail(reason, seg):
    print(f"⛔ qa-reviewer는 읽기 전용입니다 — {reason}", file=sys.stderr)
    print("", file=sys.stderr)
    print(f"   차단된 조각: {seg.strip()[:160]}", file=sys.stderr)
    print("", file=sys.stderr)
    print("   쓰기는 .claude/qa-cache/ 안에서만 허용됩니다 (마커·findings).", file=sys.stderr)
    print("   코드를 고쳐야 한다면 직접 고치지 말고 **보고서에 지적으로 남기세요**.", file=sys.stderr)
    print("   리뷰어가 리뷰 대상을 고치면 그건 리뷰가 아니라 자기승인입니다.", file=sys.stderr)
    print("   명령이 정말 필요하면 orchestrator에 사유와 함께 보고하세요 — 우회하지 마세요.", file=sys.stderr)
    sys.exit(2)

def target_ok(path):
    p = path.strip().strip('"\'')
    if p in ('/dev/null', '/dev/stderr', '/dev/stdout'):
        return True
    # 상대(.claude/qa-cache/x)·절대(/Users/…/.claude/qa-cache/x) 양쪽을 같은 기준으로 본다
    return QA_DIR in p or p.startswith('qa-cache/')

# ── 리다이렉트 대상 검사 (heredoc 제거 후이므로 남은 > 는 진짜 리다이렉트) ──
# `2>&1`, `>&2` 같은 fd 복제는 파일 쓰기가 아니다.
# ── 따옴표 안은 데이터다 (2026-08-15, 원장 L-21) ────────────────
# 초판은 원문에서 그대로 `;` `|` `(` `)` 를 잘랐다. 그래서 **구분자·패턴으로 그 문자를 쓰는
# 정상 읽기 명령**까지 막았다 — `awk -F'|'`, `grep -nE 'L-(20|21)'`, `grep '>' file`.
# 🔴 **실제로 우회가 일어났다**: 이 레포의 qa-reviewer가 차단당하자 `printf '\174'` 로
#    파이프 문자를 만들어 통과시켰다. 이 스크립트 자신의 주석이 경고한 바로 그 일이다.
#
# 해법: 따옴표 안 문자를 'X'로 **마스킹한 사본**을 만들고, 연산자 탐색·분리는 마스크에서,
#       값 추출은 원문에서 한다(길이가 같으므로 위치가 그대로 대응된다).
# ⚠️ 마스킹은 **차단 범위를 넓히지 않는다** — 따옴표 밖의 연산자는 그대로 잡힌다.
#    적대적 케이스(`"rm" -rf`, `; rm -rf be/`, `$(rm -rf be)`)는 테스트로 고정돼 있다.
# 🔴 **겹따옴표 안을 통째로 마스킹하면 안 된다.** 셸에서 `$(...)` 와 백틱은
#    겹따옴표 안에서도 **살아있다**. 초판이 그걸 놓쳐 `mkdir -p "$(rm -rf be/core)"` 가
#    뚫렸다(08-15, 기존 적대적 스위트가 잡음). 홑따옴표만 완전 리터럴이다.
def mask_quotes(s):
    out=[]; i=0; n=len(s); stack=[]   # S=홑따옴표 D=겹따옴표 C=$( ) B=백틱
    while i < n:
        c=s[i]
        if stack and stack[-1]=='S':          # 홑따옴표: 전부 리터럴
            if c=="'": stack.pop(); out.append(c)
            else: out.append('X')
            i+=1; continue
        if c=='\\' and i+1<n:
            out.append('\\'); out.append('X'); i+=2; continue
        if c=="'" and not (stack and stack[-1]=='D'):
            stack.append('S'); out.append(c); i+=1; continue
        if c=='"':
            if stack and stack[-1]=='D': stack.pop()
            else: stack.append('D')
            out.append(c); i+=1; continue
        if s[i:i+2]=='$(':                     # 겹따옴표 안에서도 활성
            stack.append('C'); out.append('$('); i+=2; continue
        if c==')' and stack and stack[-1]=='C':
            stack.pop(); out.append(c); i+=1; continue
        if c=='`':                             # 겹따옴표 안에서도 활성
            if stack and stack[-1]=='B': stack.pop()
            else: stack.append('B')
            out.append(c); i+=1; continue
        out.append('X' if (stack and stack[-1]=='D') else c)
        i+=1
    return ''.join(out)

masked = mask_quotes(cmd)
assert len(masked)==len(cmd)   # 위치 대응이 깨지면 조용히 틀리는 것보다 죽는 편이 낫다

for m in re.finditer(r'(?<![0-9<>])>>?\s*(?!&)([^\s;|&()]+)', masked):
    target = cmd[m.start(1):m.end(1)]     # 값은 원문에서
    if not target_ok(target):
        fail(f"허용되지 않은 경로로 씁니다: {target}", cmd[m.start():m.end()])

# ── 세그먼트별 명령 이름 검사 ────────────────────────────────────
def segment_spans(m):
    """마스크에서 연산자 위치를 찾아 원문 구간으로 돌려준다."""
    spans=[]; start=0; i=0; n=len(m)
    while i < n:
        if m[i:i+2] in ('||','&&','$('):
            spans.append((start,i)); i+=2; start=i; continue
        if m[i] in ';|\n`)':
            spans.append((start,i)); i+=1; start=i; continue
        i+=1
    spans.append((start,n))
    return spans

segments = [cmd[a:b] for a,b in segment_spans(masked)]
for seg in segments:
    s = seg.strip()
    if not s:
        continue
    toks = s.split()
    # 앞쪽 VAR=... 대입과 리다이렉트 토큰은 건너뛴다
    while toks and (re.match(r'^[A-Za-z_][A-Za-z0-9_]*=', toks[0])
                    or re.match(r'^[0-9]*>>?', toks[0])
                    or re.match(r'^[0-9]*<', toks[0])):
        toks.pop(0)
    if not toks:
        continue
    name = os.path.basename(toks[0].strip('"\''))
    # `$(...)`·따옴표 경계에서 쪼개지며 남은 파편(예: `" 2>/dev/null`)은 명령이 아니다.
    # 리다이렉트 대상 검사는 이 루프와 **독립적으로** 위에서 이미 전체 문자열에 대해 돌았으므로
    # 여기서 파편을 건너뛴다고 쓰기가 새지 않는다.
    if not name or name.startswith('-') or name.startswith('$'):
        continue
    if name not in ALLOWED:
        fail(f"허용목록에 없는 명령입니다: {name}", s)

    if name == 'sed':
        for t in toks[1:]:
            if t == '--in-place' or (t.startswith('-i') and not t.startswith('--')):
                fail("sed -i 는 파일을 제자리에서 고칩니다", s)

    if name == 'tee':
        for t in toks[1:]:
            if not t.startswith('-') and not target_ok(t):
                fail(f"tee 대상이 허용 경로 밖입니다: {t}", s)

    if name == 'git':
        subs = [t for t in toks[1:] if not t.startswith('-')]
        if subs and subs[0] not in GIT_READONLY:
            fail(f"git {subs[0]} 은 저장소 상태를 바꿉니다", s)

    if name == 'gh':
        subs = [t for t in toks[1:] if not t.startswith('-')]
        if subs and subs[0] not in GH_READONLY:
            fail(f"gh {subs[0]} 은 원격 상태를 바꿉니다", s)
        if len(subs) >= 2 and subs[0] == 'pr' and subs[1] in GH_PR_MUTATORS:
            fail(f"gh pr {subs[1]} 은 PR 상태를 바꿉니다", s)
        if subs and subs[0] == 'api' and re.search(r'(-X|--method)\s*(PUT|POST|PATCH|DELETE)', s):
            fail("gh api 쓰기 요청입니다", s)
PY
