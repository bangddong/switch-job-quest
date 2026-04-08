#!/usr/bin/env node
'use strict'

/**
 * 일별 활동 리포트 생성기
 * 사용: node generate-report.js [YYYY-MM-DD]
 * Stop hook 또는 수동 실행
 */

const fs = require('fs')
const path = require('path')

const LOG_FILE   = path.join(__dirname, '../logs/activity.jsonl')
const REPORT_DIR = path.join(__dirname, '../logs/daily')

const targetDate = process.argv[2] || new Date().toISOString().slice(0, 10)

if (!fs.existsSync(LOG_FILE)) {
  console.log('No activity log found.')
  process.exit(0)
}

// ── 이벤트 파싱 ──────────────────────────────────────────────
const allEvents = fs.readFileSync(LOG_FILE, 'utf8')
  .split('\n').filter(Boolean)
  .map(l => { try { return JSON.parse(l) } catch { return null } })
  .filter(Boolean)

const events = allEvents.filter(e => e.ts?.startsWith(targetDate))

if (events.length === 0) {
  console.log(`No events for ${targetDate}`)
  process.exit(0)
}

// ── 세션 분석 ────────────────────────────────────────────────
const sessions = []   // { agent, start, end }
const startMap  = {}  // agent -> start ts

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

// 종료 이벤트 없이 끝난 세션
Object.entries(startMap).forEach(([agent, start]) => {
  sessions.push({ agent, start, end: null, durationMs: null })
})

// ── 도구 호출 집계 ───────────────────────────────────────────
const toolCalls  = events.filter(e => e.event === 'PostToolUse')
const byAgent    = {}   // agent -> { total, tools: {toolName: count} }
const allTools   = {}   // toolName -> count
const filesWritten = new Set()
const filesRead    = new Set()

toolCalls.forEach(e => {
  if (!byAgent[e.agent]) byAgent[e.agent] = { total: 0, tools: {} }
  byAgent[e.agent].total++
  byAgent[e.agent].tools[e.tool] = (byAgent[e.agent].tools[e.tool] || 0) + 1
  allTools[e.tool] = (allTools[e.tool] || 0) + 1

  if (e.file) {
    const short = e.file.replace(/.*switch-job-quest\//, '')
    if (e.tool === 'Write' || e.tool === 'Edit') filesWritten.add(short)
    if (e.tool === 'Read') filesRead.add(short)
  }
})

// ── 헬퍼 ─────────────────────────────────────────────────────
function fmtDur(ms) {
  if (!ms) return '?'
  if (ms < 60000) return `${(ms / 1000).toFixed(0)}s`
  return `${Math.floor(ms / 60000)}m ${Math.floor((ms % 60000) / 1000)}s`
}

function fmtTime(ts) {
  return ts ? ts.slice(11, 19) : '-'
}

// ── 마크다운 생성 ────────────────────────────────────────────
const lines = []

lines.push(`# ${targetDate} — Claude Code Activity Report`)
lines.push('')
lines.push(`> 생성: ${new Date().toISOString().slice(0, 19).replace('T', ' ')} KST`)
lines.push('')

// 요약
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
lines.push('')

// 에이전트 세션 타임라인
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

// 에이전트별 도구 사용
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

// 수정된 파일
if (filesWritten.size > 0) {
  lines.push('## Files Modified')
  lines.push('')
  ;[...filesWritten].sort().forEach(f => lines.push(`- \`${f}\``))
  lines.push('')
}

// 조회된 파일 (상위 15개)
if (filesRead.size > 0) {
  const topRead = [...filesRead].sort().slice(0, 15)
  lines.push(`## Files Read (top ${topRead.length})`)
  lines.push('')
  topRead.forEach(f => lines.push(`- \`${f}\``))
  if (filesRead.size > 15) lines.push(`- ... and ${filesRead.size - 15} more`)
  lines.push('')
}

// 전체 도구 사용 통계
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

// 저장
fs.mkdirSync(REPORT_DIR, { recursive: true })
const outPath = path.join(REPORT_DIR, `${targetDate}.md`)
fs.writeFileSync(outPath, lines.join('\n'))

console.log(`Report saved: ${outPath}`)
console.log(`  ${sessions.length} sessions | ${totalToolCalls} tool calls | ${filesWritten.size} files modified`)
