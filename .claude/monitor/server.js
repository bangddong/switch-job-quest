#!/usr/bin/env node
'use strict'

const http = require('http')
const fs = require('fs')
const path = require('path')

const LOG_FILE = path.join(__dirname, '../logs/activity.jsonl')
const PORT = Number(process.env.MONITOR_PORT) || 4242
const MAX_EVENTS = 1000

const AGENT_COLORS = {
  'main':                '#64748B',
  'be-feature-builder':  '#3B82F6',
  'fe-feature-builder':  '#4ECDC4',
  'qa-reviewer':         '#F59E0B',
  'logic-reviewer':      '#A78BFA',
  'convention-reviewer': '#10B981',
  'test-writer':         '#F97316',
  'orchestrator':        '#EC4899',
}

function readEvents() {
  if (!fs.existsSync(LOG_FILE)) return []
  const lines = fs.readFileSync(LOG_FILE, 'utf8').split('\n').filter(Boolean)
  return lines.slice(-MAX_EVENTS).map(l => { try { return JSON.parse(l) } catch { return null } }).filter(Boolean)
}

const HTML = `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Claude Code Monitor</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0 }
  body { background: #060610; color: #F8FAFC; font-family: 'Courier New', monospace; font-size: 13px; min-height: 100vh }
  #app { max-width: 1400px; margin: 0 auto; padding: 16px }

  /* Header */
  .header { display: flex; align-items: center; gap: 12px; padding: 12px 0 20px; border-bottom: 1px solid rgba(255,255,255,0.08); margin-bottom: 20px }
  .header h1 { font-size: 18px; color: #F8FAFC; letter-spacing: 2px }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: #10B981; animation: pulse 2s infinite }
  @keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:.4 } }
  .header-right { margin-left: auto; display: flex; gap: 8px; align-items: center }
  .btn { padding: 4px 12px; border: 1px solid rgba(255,255,255,0.2); background: transparent; color: #94A3B8; cursor: pointer; font-family: monospace; font-size: 12px; border-radius: 4px }
  .btn:hover { border-color: rgba(255,255,255,0.4); color: #F8FAFC }
  .refresh-info { color: #475569; font-size: 11px }

  /* Grid */
  .grid { display: grid; grid-template-columns: 280px 1fr; gap: 16px }
  .left-col { display: flex; flex-direction: column; gap: 16px }

  /* Panel */
  .panel { background: #0F172A; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; overflow: hidden }
  .panel-title { padding: 10px 14px; font-size: 11px; color: #475569; letter-spacing: 1px; text-transform: uppercase; border-bottom: 1px solid rgba(255,255,255,0.06) }

  /* Agent cards */
  .agent-card { padding: 10px 14px; border-bottom: 1px solid rgba(255,255,255,0.05); display: flex; align-items: center; gap: 10px }
  .agent-card:last-child { border-bottom: none }
  .agent-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0 }
  .agent-dot.active { animation: pulse 1.5s infinite }
  .agent-name { flex: 1; font-size: 12px }
  .agent-status { font-size: 11px; color: #475569 }
  .agent-tool-count { font-size: 11px; padding: 2px 6px; border-radius: 10px; background: rgba(255,255,255,0.06) }

  /* Stats */
  .stat-row { padding: 8px 14px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.05) }
  .stat-row:last-child { border-bottom: none }
  .stat-label { color: #64748B; font-size: 11px }
  .stat-value { font-size: 14px; font-weight: bold }

  /* Activity feed */
  .feed { height: calc(100vh - 160px); overflow-y: auto; padding: 8px 0 }
  .feed::-webkit-scrollbar { width: 4px }
  .feed::-webkit-scrollbar-track { background: transparent }
  .feed::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 2px }

  .event { padding: 6px 14px; border-bottom: 1px solid rgba(255,255,255,0.04); display: grid; grid-template-columns: 72px 140px 80px 1fr; gap: 10px; align-items: center }
  .event:hover { background: rgba(255,255,255,0.02) }
  .event-time { color: #334155; font-size: 11px }
  .event-agent { font-size: 11px; font-weight: bold }
  .event-tool { font-size: 11px; color: #64748B }
  .event-file { font-size: 11px; color: #475569; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }

  .badge { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px }
  .badge-start  { background: rgba(16,185,129,0.15); color: #10B981 }
  .badge-stop   { background: rgba(100,116,139,0.15); color: #64748B }
  .badge-tool   { background: rgba(59,130,246,0.12); color: #60A5FA }
  .badge-main   { background: rgba(236,72,153,0.12); color: #F472B6 }

  .empty { padding: 40px; text-align: center; color: #334155 }

  /* Bar chart */
  .bar-section { padding: 10px 14px }
  .bar-row { margin-bottom: 8px }
  .bar-label { display: flex; justify-content: space-between; margin-bottom: 3px; font-size: 11px }
  .bar-label-name { color: #94A3B8 }
  .bar-label-count { color: #64748B }
  .bar-track { height: 4px; background: rgba(255,255,255,0.06); border-radius: 2px; overflow: hidden }
  .bar-fill { height: 100%; border-radius: 2px; transition: width .3s }
</style>
</head>
<body>
<div id="app">
  <div class="header">
    <div class="dot" id="statusDot"></div>
    <h1>CLAUDE CODE MONITOR</h1>
    <div class="header-right">
      <span class="refresh-info" id="refreshInfo">--</span>
      <button class="btn" onclick="clearLog()">CLEAR</button>
    </div>
  </div>

  <div class="grid">
    <div class="left-col">
      <div class="panel">
        <div class="panel-title">Agents</div>
        <div id="agentList"></div>
      </div>
      <div class="panel">
        <div class="panel-title">Stats</div>
        <div id="statsPanel"></div>
      </div>
      <div class="panel">
        <div class="panel-title">Tool Usage</div>
        <div class="bar-section" id="toolChart"></div>
      </div>
    </div>
    <div class="panel">
      <div class="panel-title">Activity Feed <span id="eventCount" style="color:#334155;margin-left:8px"></span></div>
      <div class="feed" id="feed"></div>
    </div>
  </div>
</div>

<script>
const AGENT_COLORS = ${JSON.stringify(AGENT_COLORS)}

function agentColor(name) {
  return AGENT_COLORS[name] || '#94A3B8'
}

function fmtTime(ts) {
  const d = new Date(ts)
  return d.toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function fmtDuration(ms) {
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms/1000).toFixed(1) + 's'
  return Math.floor(ms/60000) + 'm ' + Math.floor((ms%60000)/1000) + 's'
}

function shortPath(p) {
  if (!p) return ''
  return p.replace(/.*\\/switch-job-quest\\//, '').replace(/.*switch-job-quest\//, '')
}

let lastEvents = []

async function fetchAndRender() {
  try {
    const res = await fetch('/api/events')
    const events = await res.json()

    document.getElementById('refreshInfo').textContent = new Date().toLocaleTimeString('ko-KR', {hour12:false})

    // Agent session tracking
    const sessions = {}  // agent -> {start, active}
    const toolCounts = {}  // agent -> count
    const toolTypeCounts = {}  // toolName -> count
    let totalCalls = 0

    events.forEach(e => {
      if (e.event === 'SubagentStart') {
        sessions[e.agent] = { start: e.ts, active: true }
      } else if (e.event === 'SubagentStop') {
        if (sessions[e.agent]) sessions[e.agent].active = false
      } else if (e.event === 'PostToolUse' && e.tool) {
        toolCounts[e.agent] = (toolCounts[e.agent] || 0) + 1
        toolTypeCounts[e.tool] = (toolTypeCounts[e.tool] || 0) + 1
        totalCalls++
      }
    })

    // Agent list
    const allAgents = Object.keys(AGENT_COLORS)
    const agentListEl = document.getElementById('agentList')
    agentListEl.innerHTML = allAgents.map(name => {
      const session = sessions[name]
      const active = session?.active
      const count = toolCounts[name] || 0
      const color = agentColor(name)
      const duration = session ? fmtDuration(new Date() - new Date(session.start)) : ''
      return \`<div class="agent-card">
        <div class="agent-dot \${active ? 'active' : ''}" style="background:\${active ? color : '#1E293B'}"></div>
        <div>
          <div class="agent-name" style="color:\${active ? color : '#475569'}">\${name}</div>
          \${active ? \`<div style="font-size:10px;color:#334155">\${duration}</div>\` : ''}
        </div>
        <span class="agent-tool-count" style="\${count > 0 ? 'color:'+color : ''}">\${count > 0 ? count + ' calls' : '-'}</span>
      </div>\`
    }).join('')

    // Stats
    const today = new Date().toDateString()
    const todayEvents = events.filter(e => new Date(e.ts).toDateString() === today)
    const activeCount = Object.values(sessions).filter(s => s.active).length
    document.getElementById('statsPanel').innerHTML = \`
      <div class="stat-row"><span class="stat-label">Total tool calls</span><span class="stat-value">\${totalCalls}</span></div>
      <div class="stat-row"><span class="stat-label">Today events</span><span class="stat-value">\${todayEvents.length}</span></div>
      <div class="stat-row"><span class="stat-label">Active agents</span><span class="stat-value" style="color:\${activeCount>0?'#10B981':'#475569'}">\${activeCount}</span></div>
      <div class="stat-row"><span class="stat-label">Log entries</span><span class="stat-value">\${events.length}</span></div>
    \`

    // Tool type chart
    const topTools = Object.entries(toolTypeCounts).sort((a,b)=>b[1]-a[1]).slice(0,8)
    const maxCount = topTools[0]?.[1] || 1
    document.getElementById('toolChart').innerHTML = topTools.length
      ? topTools.map(([tool, cnt]) => \`
          <div class="bar-row">
            <div class="bar-label">
              <span class="bar-label-name">\${tool}</span>
              <span class="bar-label-count">\${cnt}</span>
            </div>
            <div class="bar-track"><div class="bar-fill" style="width:\${(cnt/maxCount*100).toFixed(0)}%;background:#3B82F6"></div></div>
          </div>\`).join('')
      : '<div style="padding:10px;color:#334155;font-size:11px">No data yet</div>'

    // Feed (newest first)
    const feedEvents = [...events].reverse().slice(0, 200)
    document.getElementById('eventCount').textContent = events.length + ' events'
    const feedEl = document.getElementById('feed')
    if (feedEvents.length === 0) {
      feedEl.innerHTML = '<div class="empty">Waiting for activity...<br><span style="font-size:11px;margin-top:8px;display:block">Claude Code를 사용하면 여기에 실시간으로 표시됩니다</span></div>'
    } else {
      feedEl.innerHTML = feedEvents.map(e => {
        const color = agentColor(e.agent)
        let badgeClass = 'badge-tool'
        let label = e.event
        if (e.event === 'SubagentStart') { badgeClass = 'badge-start'; label = 'START' }
        else if (e.event === 'SubagentStop') { badgeClass = 'badge-stop'; label = 'STOP' }
        else if (e.event === 'PostToolUse') { badgeClass = 'badge-tool'; label = e.tool || 'tool' }
        else if (e.event === 'PreToolUse' && e.agent === 'main') { badgeClass = 'badge-main'; label = e.tool || 'tool' }

        const detail = e.file ? shortPath(e.file) : (e.cmd ? e.cmd.slice(0,80) : '')

        return \`<div class="event">
          <span class="event-time">\${fmtTime(e.ts)}</span>
          <span class="event-agent" style="color:\${color}">\${e.agent}</span>
          <span class="badge \${badgeClass}">\${label}</span>
          <span class="event-file" title="\${detail}">\${detail}</span>
        </div>\`
      }).join('')
    }

  } catch(err) {
    document.getElementById('statusDot').style.background = '#EF4444'
    document.getElementById('statusDot').style.animation = 'none'
  }
}

async function clearLog() {
  if (!confirm('로그를 초기화할까요?')) return
  await fetch('/api/events', { method: 'DELETE' })
  fetchAndRender()
}

fetchAndRender()
setInterval(fetchAndRender, 3000)
</script>
</body>
</html>`

http.createServer((req, res) => {
  const url = new URL(req.url, `http://localhost`)

  if (req.method === 'DELETE' && url.pathname === '/api/events') {
    if (fs.existsSync(LOG_FILE)) fs.writeFileSync(LOG_FILE, '')
    res.writeHead(204)
    res.end()
    return
  }

  if (url.pathname === '/api/events') {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' })
    res.end(JSON.stringify(readEvents()))
    return
  }

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
  res.end(HTML)
}).listen(PORT, '127.0.0.1', () => {
  console.log(`\nClaude Code Monitor → http://localhost:${PORT}\n`)
})
