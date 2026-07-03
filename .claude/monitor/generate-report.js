#!/usr/bin/env node
'use strict'

/**
 * 일별 활동 리포트 생성기
 * 사용: node generate-report.js [YYYY-MM-DD]
 *   - 날짜 지정: 해당 날짜만 생성
 *   - 인자 없음: 오늘 + 리포트가 없거나 마지막 이벤트보다 오래된 날짜 전부 백필
 *     (세션 비정상 종료로 Stop 훅이 안 돈 날의 구멍을 다음 세션에서 메꿈)
 * Stop hook 또는 수동 실행
 */

const fs = require('fs')
const path = require('path')

const LOG_FILE   = path.join(__dirname, '../logs/activity.jsonl')
const REPORT_DIR = path.join(__dirname, '../logs/daily')

if (!fs.existsSync(LOG_FILE)) {
  console.log('No activity log found.')
  process.exit(0)
}

// ── 이벤트 파싱 ──────────────────────────────────────────────
const allEvents = fs.readFileSync(LOG_FILE, 'utf8')
  .split('\n').filter(Boolean)
  .map(l => { try { return JSON.parse(l) } catch { return null } })
  .filter(Boolean)

// ── 대상 날짜 결정 ───────────────────────────────────────────
const argDate = process.argv[2]
const today = new Date().toISOString().slice(0, 10)
let targetDates

if (argDate) {
  targetDates = [argDate]
} else {
  // jsonl에 있는 모든 날짜 중: 오늘 + (리포트 없음 또는 마지막 이벤트보다 오래됨)
  const lastEventByDate = {}
  allEvents.forEach(e => {
    const d = e.ts?.slice(0, 10)
    if (!d) return
    if (!lastEventByDate[d] || e.ts > lastEventByDate[d]) lastEventByDate[d] = e.ts
  })
  targetDates = Object.keys(lastEventByDate).sort().filter(d => {
    if (d === today) return true
    const reportPath = path.join(REPORT_DIR, `${d}.md`)
    if (!fs.existsSync(reportPath)) return true
    return fs.statSync(reportPath).mtime < new Date(lastEventByDate[d])
  })
}

// ── 헬퍼 ─────────────────────────────────────────────────────
function fmtDur(ms) {
  if (!ms) return '?'
  if (ms < 60000) return `${(ms / 1000).toFixed(0)}s`
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
}

function fmtTime(ts) {
  return ts ? ts.slice(11, 19) : '-'
}

// 스킬 파일 경로 → 스킬 이름 (universal/tdd, project/be-module-create 등)
function skillNameFrom(str) {
  const m = str.replace(/\\\\|\\/g, '/').match(/\.claude\/skills\/([\w\-\/]+)\.md/)
  return m ? m[1] : null
}

// ── 날짜별 리포트 생성 ───────────────────────────────────────
function generateFor(targetDate) {
  const events = allEvents.filter(e => e.ts?.startsWith(targetDate))
  if (events.length === 0) {
    console.log(`No events for ${targetDate}`)
    return
  }

  // 세션 분석
  const sessions = []
  const startMap = {}
  events.forEach(e => {
    if (e.event === 'SubagentStart') {
      startMap[e.agent] = e.ts
    } else if (e.event === 'SubagentStop' && startMap[e.agent]) {
      sessions.push({
        agent: e.agent,
        start: startMap[e.agent],
        end:   e.ts,
        durationMs: new Date(e.ts) - new Date(startMap[e.agent]),
      })
      delete startMap[e.agent]
    }
  })
  Object.entries(startMap).forEach(([agent, start]) => {
    sessions.push({ agent, start, end: null, durationMs: null })
  })

  // 도구 호출 집계
  const toolCalls  = events.filter(e => e.event === 'PostToolUse')
  const byAgent    = {}
  const allTools   = {}
  const filesWritten = new Set()
  const filesRead    = new Set()
  const skillUsage   = {}   // skillName -> count (Read 또는 Bash cat 주입)

  toolCalls.forEach(e => {
    if (!byAgent[e.agent]) byAgent[e.agent] = { total: 0, tools: {} }
    byAgent[e.agent].total++
    byAgent[e.agent].tools[e.tool] = (byAgent[e.agent].tools[e.tool] || 0) + 1
    allTools[e.tool] = (allTools[e.tool] || 0) + 1

    if (e.file) {
      const short = e.file.replace(/.*switch-job-quest[\\\/]/, '')
      if (e.tool === 'Write' || e.tool === 'Edit') filesWritten.add(short)
      if (e.tool === 'Read') filesRead.add(short)
    }

    // 스킬 사용 추적: Read(file) 또는 Bash(cmd)에서 .claude/skills/*.md 등장
    const src = (e.tool === 'Read' && e.file) ? e.file
              : (e.tool === 'Bash' && e.cmd)  ? e.cmd
              : null
    if (src) {
      const name = skillNameFrom(src)
      if (name) skillUsage[name] = (skillUsage[name] || 0) + 1
    }
  })

  // 마크다운 생성
  const lines = []
  lines.push(`# ${targetDate} — Claude Code Activity Report`)
  lines.push('')
  lines.push(`> 생성: ${new Date().toISOString().slice(0, 19).replace('T', ' ')} KST`)
  lines.push('')

  const totalToolCalls = toolCalls.length
  const uniqueAgents   = [...new Set(toolCalls.map(e => e.agent))]
  lines.push('## Summary')
  lines.push('')
  lines.push(`| | |`)
  lines.push(`|---|---|`)
  lines.push(`| Total tool calls | **${totalToolCalls}** |`)
  lines.push(`| Agent sessions | **${sessions.length}** |`)
  lines.push(`| Active agents | **${uniqueAgents.join(', ') || '-'}** |`)
  lines.push(`| Files modified | **${filesWritten.size}** |`)
  lines.push(`| Files read | **${filesRead.size}** |`)
  lines.push(`| Skills touched | **${Object.keys(skillUsage).length}** |`)
  lines.push('')

  if (sessions.length > 0) {
    lines.push('## Agent Sessions')
    lines.push('')
    lines.push('| Agent | Start | End | Duration | Tool Calls |')
    lines.push('|-------|-------|-----|----------|------------|')
    sessions.forEach(s => {
      const calls = byAgent[s.agent]?.total || 0
      lines.push(`| \`${s.agent}\` | ${fmtTime(s.start)} | ${fmtTime(s.end)} | ${fmtDur(s.durationMs)} | ${calls} |`)
    })
    lines.push('')
  }

  // 스킬 사용 (방치 스킬 탐지용 — skill-guide.md 참조)
  if (Object.keys(skillUsage).length > 0) {
    lines.push('## Skill Usage')
    lines.push('')
    lines.push('| Skill | Touches |')
    lines.push('|-------|---------|')
    Object.entries(skillUsage)
      .sort((a, b) => b[1] - a[1])
      .forEach(([s, c]) => lines.push(`| \`${s}\` | ${c} |`))
    lines.push('')
  }

  if (Object.keys(byAgent).length > 0) {
    lines.push('## Tool Usage by Agent')
    lines.push('')
    Object.entries(byAgent)
      .sort((a, b) => b[1].total - a[1].total)
      .forEach(([agent, data]) => {
        const toolStr = Object.entries(data.tools)
          .sort((a, b) => b[1] - a[1])
          .map(([t, c]) => `${t}×${c}`)
          .join(', ')
        lines.push(`**${agent}** (${data.total} calls): ${toolStr}`)
      })
    lines.push('')
  }

  if (filesWritten.size > 0) {
    lines.push('## Files Modified')
    lines.push('')
    ;[...filesWritten].sort().forEach(f => lines.push(`- \`${f}\``))
    lines.push('')
  }

  if (filesRead.size > 0) {
    const topRead = [...filesRead].sort().slice(0, 15)
    lines.push(`## Files Read (top ${topRead.length})`)
    lines.push('')
    topRead.forEach(f => lines.push(`- \`${f}\``))
    if (filesRead.size > 15) lines.push(`- ... and ${filesRead.size - 15} more`)
    lines.push('')
  }

  if (Object.keys(allTools).length > 0) {
    lines.push('## Tool Stats')
    lines.push('')
    lines.push('| Tool | Count |')
    lines.push('|------|-------|')
    Object.entries(allTools)
      .sort((a, b) => b[1] - a[1])
      .forEach(([t, c]) => lines.push(`| ${t} | ${c} |`))
    lines.push('')
  }

  fs.mkdirSync(REPORT_DIR, { recursive: true })
  const outPath = path.join(REPORT_DIR, `${targetDate}.md`)
  fs.writeFileSync(outPath, lines.join('\n'))

  console.log(`Report saved: ${outPath}`)
  console.log(`  ${sessions.length} sessions | ${totalToolCalls} tool calls | ${filesWritten.size} files modified`)
}

targetDates.forEach(generateFor)
