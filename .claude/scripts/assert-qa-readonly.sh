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
# ══ 🔴 위협 모델 (2026-08-16 확정 — 이걸 먼저 읽어라) ══
#
# 이 가드가 막는 것은 **qa-reviewer가 무심코 리뷰 대상을 고치는 것**이다.
# 적대적으로 작정한 리뷰어는 대상이 아니다. 이유는 하나다:
#
#   🔑 **적대적 리뷰어는 파일을 고칠 필요가 없다 — 보고서에 거짓을 쓰면 된다.**
#      QA 프로세스 전체가 리뷰어의 정직성에 의존한다. 파일시스템을 잠그면서 보고서를
#      믿는 것은 앞뒤가 맞지 않는다. 이 가드로 적대자를 막으려는 시도는 **범주 오류**다.
#
# 그래서 잡아야 하는 것은 정직한 실수의 형태이고, 그건 작고 열거 가능하다:
#   `echo … > file` · `sed -i` · `git checkout` · `git commit` · `rm` · `tee`
# 전부 차단되고, 차단 메시지는 *"우회하라"*가 아니라 **"보고하라"**로 안내한다.
#
# 반대로 **범위 밖**인 것 (원장 L-22, 근거는 위 한 줄):
#   허용 명령의 인자 속 실행 지시(`find -exec`, `xargs`, `awk 'BEGIN{system()}'`),
#   프로세스 치환 `diff <(rm …)`, 명령 내장 파일쓰기(`awk '… > "path"'`).
#   전부 **작정해야 나오는 형태**다.
#
# ⚠️ **이 모델을 근거 없이 넓히지 마라.** 넓히면 파서를 계속 조이게 되고, 조일 때마다
#    새 우회가 열리며(4라운드 실측), 오탐이 늘어 사람이 우회하기 시작한다(L-21에서 실제 발생).
#    적대자를 정말 막아야 한다면 답은 파서가 아니라 **격리**다(읽기 전용 마운트·별도 사용자).
#
# 🔴 **2층 방어는 없다.** 사후 탐지용 `qa-effect-guard.sh`를 짰으나 서브에이전트 생명주기
#    훅이 발화하지 않아 한 번도 돌지 못했고, 08-17에 삭제했다. 여기가 유일한 층이다 —
#    그러니 이 파일 하나로 적대자를 막는다고 착각하지 마라(아래 잔여 위험 참조).
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
# ⚠️ **이것은 `lib/strip-heredoc.sh` 와 같은 로직의 세 번째 사본이다** (08-15 QA F-4).
#    저기는 bash 함수, 여기는 이 파이썬 블록 안이라 소싱이 안 된다 — 언어가 다르다.
#    🔑 **heredoc 태그 처리에 엣지케이스를 추가하면 두 곳을 함께 고쳐야 한다.**
#    사본이 갈라져 3일간 오탐이 산 전례가 있다(assert-no-main-push, 08-12~08-15).
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
    # `read`·`:` 는 키워드 벗기기(F-9) 이후 드러난다 — 둘 다 본질적으로 읽기/무동작이다.
    # 스위트가 `while read -r l; do …` 오탐을 즉시 잡아줘서 추가했다(08-15).
    'read',':','wait','sleep',
    # 읽기·분석
    'cat','head','tail','wc','grep','egrep','fgrep','rg','find','ls','file','stat','diff','comm',
    'sort','uniq','cut','tr','sed','awk','jq','yq','basename','dirname','realpath','xargs','tee',
    'date','env','pwd','which','command','type','column','fold','nl','od','md5','shasum',
    # 쓰기가 필요한 최소한 (대상은 아래에서 따로 검사)
    'mkdir','git','gh',
    # ── 검증 실행 (2026-08-16, 원장 L-25) ──────────────────────────
    # 🔴 **이게 없어서 리뷰어가 굶고 있었다.** `bash`·`python3`·`cd`가 전부 차단돼
    #    **리뷰어가 자기가 리뷰하는 `test-guards.sh`를 실행조차 못 했다.**
    #    매 라운드 보고서에 *"코드 정독으로만 확인했다"* 는 단서가 붙은 이유고,
    #    검증을 못 하니 grep·read 루프로 우회하다 토큰을 태워 **응답이 5회 연속 퇴화**했다
    #    (430k+ 토큰, 응답이 `F-15.` · `(Same.)` · `(unchanged)` 수준).
    #    L-21에 적어둔 *"성가신 가드는 우회당한다"* 의 극단 — **우회조차 못 하면 기능이 죽는다.**
    #
    # ⚠️ **잔여 위험을 정직하게 적는다**: `bash x.sh` · `python3 -c '…'` 는 임의 실행이므로
    #    이 허용목록을 **샌드박스로 착각하면 안 된다.** 확정된 위협 모델(실수 탐지기) 기준으로
    #    허용한 것이다 — 막아야 할 실수(`>` 리다이렉트·`sed -i`·`rm`·`git commit`)는 직접 치면
    #    그대로 잡히고, 스크립트를 경유한 수정은 **실수로 나오는 형태가 아니다**(L-22와 같은 논리).
    'bash','sh','python3','python','cd',
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
    # 🔴 이 한 줄이 없어서 실제 오보가 났다 (08-16). qa-reviewer가 `echo > marker && sed -i …` 를
    #    한 호출로 묶었는데 뒤쪽 sed가 걸려 **호출 전체가 거부**됐다. 그런데 화면상 앞쪽 echo가
    #    성공한 것처럼 보여 "마커 갱신 완료"로 **잘못 보고**했다(실제 마커는 옛 SHA로 남음).
    #    PreToolUse는 부분 실행이 아니라 전부-아니면-전무다 — 그걸 명시한다.
    print("   ⚠️ 이 명령은 **전체가 실행되지 않았습니다** (앞쪽 세그먼트 포함).", file=sys.stderr)
    print("      복합 명령이라면 허용되는 부분만 따로 다시 실행하세요.", file=sys.stderr)
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
        # 🔴 ANSI-C 인용 `$'...'` — 여기서는 `\'` 가 따옴표를 **닫지 않는다**(bash 사양).
        #    초판이 이걸 일반 홑따옴표로 취급해 우회로가 됐다(08-15 QA F-1, 실측):
        #      $'x\'y' ; rm -rf be/   → 상태가 2글자 일찍 닫히고 다시 열려
        #                                뒤쪽 `; rm -rf be/` 가 통째로 마스킹돼 숨었다.
        if stack and stack[-1]=='A':
            if c=='\\' and i+1<n: out.append('XX'); i+=2; continue
            if c=="'": stack.pop(); out.append(c)
            else: out.append('X')
            i+=1; continue
        if stack and stack[-1]=='S':          # 홑따옴표: 전부 리터럴
            if c=="'": stack.pop(); out.append(c)
            else: out.append('X')
            i+=1; continue
        if c=='\\' and i+1<n:
            out.append('\\'); out.append('X'); i+=2; continue
        if s[i:i+2]=="$'" and not (stack and stack[-1]=='D'):
            stack.append('A'); out.append("$'"); i+=2; continue
        if s[i:i+2]=='$"' and not (stack and stack[-1]=='D'):
            stack.append('D'); out.append('$"'); i+=2; continue   # $"..." 는 "..." 와 동일
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
    # 🔴 셸 키워드 뒤의 **실제 명령**까지 벗겨낸다 (08-15 QA F-9).
    #    초판은 `toks[0]` 만 봤고 `if`/`while`/`do` 가 ALLOWED라 그 뒤가 통째로 안 보였다:
    #      `if rm -rf be/core; then true; fi`  → toks[0]='if'(허용) → rm 미검사 → 통과
    #      `for f in x; do rm -rf be/; done`   → toks[0]='do'(허용) → 동일
    #    bash는 if-조건절을 분기 결과와 무관하게 **그대로 실행**한다. 이색적 우회가 아니라
    #    토큰 순회에서 빠진 자리였다.
    LEADING = {'if','then','else','elif','while','until','do','!','time','command','builtin','exec','eval','nohup'}
    while toks and (re.match(r'^[A-Za-z_][A-Za-z0-9_]*=', toks[0])
                    or re.match(r'^[0-9]*>>?', toks[0])
                    or re.match(r'^[0-9]*<', toks[0])
                    or toks[0] in LEADING):
        toks.pop(0)
    if not toks:
        continue
    name = os.path.basename(toks[0].strip('"\''))
    # `$(...)`·따옴표 경계에서 쪼개지며 남은 파편(예: `" 2>/dev/null`)은 명령이 아니다.
    # 리다이렉트 대상 검사는 이 루프와 **독립적으로** 위에서 이미 전체 문자열에 대해 돌았으므로
    # 여기서 파편을 건너뛴다고 쓰기가 새지 않는다.
    if not name or name.startswith('-'):
        continue
    # 🔴 `$VAR` 로 시작하는 명령 이름은 **정적으로 확인할 수 없다** → 막는다(fail-closed).
    #    초판은 건너뛰었고, 그래서 `X=rm; $X -rf be/core` 가 통과했다(08-15 실측).
    #    리뷰어가 변수 간접으로 명령을 만들 이유가 없다 — 필요하면 직접 쓰면 된다.
    if name.startswith('$'):
        fail(f"명령 이름이 변수라 확인할 수 없습니다: {name}", s)
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
