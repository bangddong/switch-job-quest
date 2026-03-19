# Pending Tasks (사용자 직접 실행 필요)

> 아래 작업들이 완료되면 해당 항목을 삭제하고 Claude에게 알려주세요.
> 모든 작업 완료 시 이 파일 자체를 삭제해도 됩니다.

---

## 🔧 Tool 설치

### gh CLI (GitHub CLI)
```powershell
winget install --id GitHub.cli -e
gh auth login
```
**설치 후 가능해지는 것:**
- GitHub Issues 생성/관리
- PR 생성 자동화
- Branch Protection Rules 설정

---

### flyctl (Fly.io CLI)
설치 완료 (C:\Users\guseh\flyctl\flyctl.exe) — PATH에 추가됨

```bash
fly auth login        # 브라우저에서 직접 로그인
```
**로그인 후 가능해지는 것:**
- BE 배포 (`fly deploy --remote-only`)
- 환경변수 주입 (`fly secrets set ANTHROPIC_API_KEY=sk-...`)
- `api.quest.dhbang.co.kr` 연결

---

### Vercel CLI
```bash
npm install -g vercel
vercel login          # 브라우저에서 직접 로그인
cd fe && vercel link  # 프로젝트 연결 (.vercel/project.json 생성)
```
**로그인 후 가능해지는 것:**
- FE 배포
- `quest.dhbang.co.kr` 연결

---

## 🔑 GitHub Secrets 등록

PR #1 머지 후 아래 Secrets를 등록해야 CI/CD가 동작합니다.

`github.com/bangddong/switch-job-quest` → Settings → Secrets and variables → Actions

| Secret 이름 | 값 | 획득 방법 |
|------------|---|----------|
| `FLY_API_TOKEN` | Fly.io 토큰 | `fly auth token` (flyctl 로그인 후) |
| `VERCEL_TOKEN` | Vercel 토큰 | Vercel 대시보드 → Settings → Tokens |
| `VERCEL_ORG_ID` | Vercel Org ID | `cd fe && vercel link` 후 `.vercel/project.json` |
| `VERCEL_PROJECT_ID` | Vercel Project ID | 동일 |

---

## 📋 작업 순서 (권장)

1. [ ] PR #1 머지 (`refactor/remove-unused-provider-field`)
2. [ ] gh CLI 설치 & 로그인
3. [ ] GitHub Issues 등록 (Claude가 자동으로 처리)
4. [ ] GitHub Branch Protection Rules 설정 (Claude가 자동으로 처리)
5. [ ] `fly auth login`
6. [ ] `fly secrets set ANTHROPIC_API_KEY=sk-...`
7. [ ] BE 배포 (Claude가 처리)
8. [ ] Vercel CLI 설치 & 로그인 & 프로젝트 연결
9. [ ] FE 배포 (Claude가 처리)
10. [ ] GitHub Secrets 등록
