import { useState, useEffect } from "react";

const API_BASE = "http://localhost:8080/api/v1";
const USER_ID = (() => {
  try {
    const stored = sessionStorage.getItem("devquest-uid");
    if (stored) return stored;
    const id = "user-" + Math.random().toString(36).slice(2, 10);
    sessionStorage.setItem("devquest-uid", id);
    return id;
  } catch { return "user-demo"; }
})();

async function callAiCheck(endpoint, body) {
  const res = await fetch(`${API_BASE}/ai-check/${endpoint}`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...body, userId: USER_ID })
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

const AI_FORMS = {
  "1-2": {
    label: "이직 동기 에세이", endpoint: "career-essay",
    fields: [
      { key: "dissatisfactions", label: "현 직장 불만족 사항 3가지", type: "list", count: 3, placeholder: "예: 기술 부채가 많아 성장이 멈춘 느낌입니다" },
      { key: "goals", label: "이직 후 목표 3가지", type: "list", count: 3, placeholder: "예: MSA 아키텍처를 직접 설계해보고 싶습니다" },
      { key: "fiveYearVision", label: "5년 후 나의 모습 (200자)", type: "textarea", placeholder: "5년 뒤 어떤 개발자가 되고 싶은지 구체적으로 작성해주세요..." }
    ],
    transform: v => v
  },
  "2-2": {
    label: "기술 블로그 검사", endpoint: "tech-blog",
    fields: [
      { key: "techTopic", label: "기술 주제", type: "text", placeholder: "예: JVM G1GC 튜닝" },
      { key: "title", label: "포스트 제목", type: "text", placeholder: "예: 실무에서 G1GC를 50% 개선한 방법" },
      { key: "content", label: "본문 (1500자 이상)", type: "textarea", placeholder: "블로그 본문을 붙여넣어 주세요...", rows: 8 }
    ],
    transform: v => ({ ...v, questId: "2-2" })
  },
  "2-3": {
    label: "시스템 설계 평가", endpoint: "system-design",
    fields: [
      { key: "problemStatement", label: "설계 문제", type: "text", placeholder: "예: 하루 100만 주문을 처리하는 배달앱 주문 시스템 설계" },
      { key: "architectureDescription", label: "아키텍처 설명", type: "textarea", placeholder: "설계한 시스템을 상세하게 설명해주세요...", rows: 6 },
      { key: "considerations", label: "고려한 사항 4가지", type: "list", count: 4, placeholder: "예: Auto Scaling으로 트래픽 급증에 대응" }
    ],
    transform: v => ({ ...v, questId: "2-3" })
  },
  "3-2": {
    label: "JD 역분석", endpoint: "jd-analysis",
    fields: [
      { key: "companyName", label: "회사명", type: "text", placeholder: "예: 토스" },
      { key: "jobDescription", label: "채용공고 원문", type: "textarea", placeholder: "채용공고를 붙여넣어 주세요...", rows: 6 },
      { key: "userSkills", label: "내 보유 기술 (5가지)", type: "list", count: 5, placeholder: "예: Spring Boot" },
      { key: "userExperiences", label: "경력 요약 (3가지)", type: "list", count: 3, placeholder: "예: 결제 API 개발 및 성능 개선 (2년)" }
    ],
    transform: v => v
  },
  "4-1": {
    label: "이력서 STAR 검토", endpoint: "resume",
    fields: [
      { key: "targetCompany", label: "지원 회사명", type: "text", placeholder: "예: 카카오페이" },
      { key: "targetJd", label: "JD 핵심 요구사항", type: "textarea", placeholder: "채용공고 주요 내용...", rows: 4 },
      { key: "resumeContent", label: "이력서 내용", type: "textarea", placeholder: "이력서 전문을 붙여넣어 주세요...", rows: 8 }
    ],
    transform: v => v
  },
  "5-1": {
    label: "인성 면접 연습", endpoint: "personality-interview",
    fields: [
      { key: "question", label: "면접 질문", type: "text", placeholder: "예: 납득이 가지 않는 업무를 맡게 되었을 때 어떻게 하시겠습니까?" },
      { key: "answer", label: "내 답변", type: "textarea", placeholder: "실제 면접처럼 구체적으로 답변해주세요...", rows: 6 }
    ],
    transform: v => v
  }
};

const FALLBACK_QUESTIONS = [
  { id: "q1", category: "DB", question: "데이터베이스 인덱스의 내부 동작 원리와 잘못 사용했을 때의 문제점을 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q2", category: "JVM", question: "Garbage Collection의 종류와 Stop-The-World가 발생하는 이유를 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q3", category: "네트워크", question: "HTTPS 통신에서 TLS Handshake 과정을 상세하게 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q4", category: "설계", question: "MSA와 Monolithic 아키텍처의 장단점과 각각 어떤 상황에서 선택해야 하는지 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q5", category: "DB", question: "Optimistic Locking과 Pessimistic Locking의 차이와 사용 상황을 설명해주세요.", difficulty: "HARD" },
  { id: "q6", category: "Spring", question: "@Transactional 어노테이션의 내부 동작 원리와 주의해야 할 점들을 설명해주세요.", difficulty: "HARD" },
  { id: "q7", category: "OS", question: "Thread와 Process의 차이, Thread-safe한 코드를 작성하는 방법을 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q8", category: "Java", question: "hashCode()와 equals()의 역할과 올바르게 구현하는 방법을 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q9", category: "설계", question: "Slow Query를 발견했을 때 원인 분석부터 해결까지 과정을 설명해주세요.", difficulty: "MEDIUM" },
  { id: "q10", category: "네트워크", question: "웹 브라우저에 https://www.google.com 입력 후 발생하는 과정을 상세하게 설명해주세요.", difficulty: "HARD" },
];

const GRADE_COLORS = { S: "#F59E0B", A: "#10B981", B: "#4ECDC4", C: "#A78BFA", D: "#EF4444" };

function ScoreRing({ score, size = 80 }) {
  const color = score >= 90 ? "#F59E0B" : score >= 80 ? "#10B981" : score >= 70 ? "#4ECDC4" : score >= 60 ? "#A78BFA" : "#EF4444";
  const r = size / 2 - 9;
  const circ = 2 * Math.PI * r;
  const fill = (score / 100) * circ;
  return (
    <svg width={size} height={size} style={{ flexShrink: 0 }}>
      <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth={8} />
      <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={8}
        strokeDasharray={`${fill} ${circ}`} strokeLinecap="round"
        transform={`rotate(-90 ${size/2} ${size/2})`} style={{ transition: "stroke-dasharray 1.2s ease" }} />
      <text x={size/2} y={size/2 + 1} textAnchor="middle" dominantBaseline="middle"
        fill="#F1F5F9" fontSize={size * 0.22} fontWeight="bold" fontFamily="'Courier New', monospace">{score}</text>
      <text x={size/2} y={size/2 + size*0.2} textAnchor="middle" dominantBaseline="middle"
        fill="#475569" fontSize={size * 0.13} fontFamily="'Courier New', monospace">/100</text>
    </svg>
  );
}

function AiResultCard({ result }) {
  const score = result.score ?? result.overallScore ?? 0;
  const passed = result.passed ?? score >= 70;
  const grade = result.grade ?? (score >= 90 ? "S" : score >= 80 ? "A" : score >= 70 ? "B" : score >= 60 ? "C" : "D");
  const gc = GRADE_COLORS[grade] || "#64748B";

  return (
    <div style={{
      background: passed ? "rgba(16,185,129,0.04)" : "rgba(239,68,68,0.04)",
      border: `1px solid ${passed ? "rgba(16,185,129,0.25)" : "rgba(239,68,68,0.25)"}`,
      borderRadius: 14, padding: 22, marginTop: 18, animation: "slideIn 0.5s ease"
    }}>
      <div style={{ display: "flex", gap: 18, alignItems: "flex-start", marginBottom: 18 }}>
        <ScoreRing score={score} />
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
            <span style={{ fontSize: 20, fontWeight: "bold", color: gc, fontFamily: "'Courier New', monospace",
              background: `${gc}15`, border: `1px solid ${gc}40`, padding: "3px 14px", borderRadius: 20 }}>
              {grade}
            </span>
            <span style={{ fontSize: 13, color: passed ? "#10B981" : "#EF4444", fontWeight: "bold" }}>
              {passed ? "✓ 통과 — 퀘스트 완료!" : "✗ 미통과 — 재시도 가능"}
            </span>
          </div>
          {result.summary && <p style={{ color: "#94A3B8", fontSize: 13, margin: 0, lineHeight: 1.6 }}>{result.summary}</p>}
          {result.developerType && <div style={{ marginTop: 6, fontSize: 12, color: "#A78BFA" }}>🧠 {result.developerType}</div>}
        </div>
      </div>

      {result.strengths?.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: "#10B981", letterSpacing: 3, marginBottom: 8 }}>✓ STRENGTHS</div>
          {result.strengths.map((s, i) => (
            <div key={i} style={{ fontSize: 13, color: "#64748B", marginBottom: 4 }}>
              <span style={{ color: "#10B981" }}>▸ </span>{s}
            </div>
          ))}
        </div>
      )}

      {result.improvements && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 10, color: "#F59E0B", letterSpacing: 3, marginBottom: 8 }}>↑ IMPROVEMENTS</div>
          {(Array.isArray(result.improvements) ? result.improvements : [result.improvements]).map((imp, i) => (
            <div key={i} style={{ fontSize: 13, color: "#64748B", marginBottom: 4 }}>
              <span style={{ color: "#F59E0B" }}>▸ </span>
              {typeof imp === "string" ? imp : imp.suggestion || imp.issue || JSON.stringify(imp)}
            </div>
          ))}
        </div>
      )}

      {(result.detailedFeedback || result.feedback) && (
        <div style={{ background: "rgba(15,23,42,0.7)", borderRadius: 10, padding: "14px 16px", borderLeft: "3px solid #4ECDC4" }}>
          <div style={{ fontSize: 10, color: "#4ECDC4", letterSpacing: 3, marginBottom: 8 }}>💬 AI FEEDBACK</div>
          <p style={{ color: "#94A3B8", fontSize: 13, margin: 0, lineHeight: 1.7 }}>{result.detailedFeedback || result.feedback}</p>
        </div>
      )}

      {result.rewrittenExamples?.length > 0 && (
        <div style={{ marginTop: 16 }}>
          <div style={{ fontSize: 10, color: "#A78BFA", letterSpacing: 3, marginBottom: 10 }}>✏️ AI REWRITES</div>
          {result.rewrittenExamples.map((ex, i) => (
            <div key={i} style={{ background: "rgba(13,17,23,0.6)", borderRadius: 8, padding: 12, marginBottom: 8 }}>
              <div style={{ fontSize: 11, color: "#EF4444", marginBottom: 3 }}>Before</div>
              <div style={{ fontSize: 12, color: "#475569", marginBottom: 8 }}>{ex.original}</div>
              <div style={{ fontSize: 11, color: "#10B981", marginBottom: 3 }}>After</div>
              <div style={{ fontSize: 12, color: "#CBD5E1" }}>{ex.improved}</div>
            </div>
          ))}
        </div>
      )}

      {result.suggestedFocus?.length > 0 && (
        <div style={{ marginTop: 14 }}>
          <div style={{ fontSize: 10, color: "#F59E0B", letterSpacing: 3, marginBottom: 8 }}>🎯 추천 회사 유형</div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            {result.suggestedFocus.map((f, i) => (
              <span key={i} style={{ background: "rgba(245,158,11,0.08)", border: "1px solid rgba(245,158,11,0.25)",
                color: "#F59E0B", padding: "3px 10px", borderRadius: 20, fontSize: 11 }}>{f}</span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function InterviewResultCard({ result }) {
  if (!result) return null;
  const bars = [
    { label: "기술 정확성", val: result.technicalAccuracy, max: 40, color: "#EF4444" },
    { label: "깊이/응용력", val: result.depthAndApplication, max: 30, color: "#F59E0B" },
    { label: "실무 경험", val: result.practicalExperience, max: 20, color: "#10B981" },
    { label: "커뮤니케이션", val: result.communicationClarity, max: 10, color: "#4ECDC4" },
  ];
  return (
    <div style={{ background: "rgba(13,17,23,0.8)", border: "1px solid rgba(255,255,255,0.06)", borderRadius: 14, padding: 20, marginTop: 16, animation: "slideIn 0.4s ease" }}>
      <div style={{ display: "flex", gap: 16, alignItems: "center", marginBottom: 18 }}>
        <ScoreRing score={result.score} size={72} />
        <div>
          <div style={{ fontSize: 13, color: result.passed ? "#10B981" : "#EF4444", fontWeight: "bold", marginBottom: 3 }}>
            {result.passed ? "✓ 통과" : "✗ 미통과"}
          </div>
          <div style={{ fontSize: 12, color: "#475569" }}>세부 채점 결과</div>
        </div>
      </div>
      {bars.map(b => (
        <div key={b.label} style={{ marginBottom: 9 }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, marginBottom: 3 }}>
            <span style={{ color: "#64748B" }}>{b.label}</span>
            <span style={{ color: b.color }}>{b.val}/{b.max}</span>
          </div>
          <div style={{ background: "#0F172A", borderRadius: 3, height: 5, overflow: "hidden" }}>
            <div style={{ background: b.color, height: "100%", width: `${(b.val/b.max)*100}%`, transition: "width 1s ease", borderRadius: 3 }} />
          </div>
        </div>
      ))}
      {result.keyPointsMissed?.length > 0 && (
        <div style={{ marginTop: 14, marginBottom: 12 }}>
          <div style={{ fontSize: 10, color: "#EF4444", letterSpacing: 3, marginBottom: 6 }}>⚠ MISSED</div>
          {result.keyPointsMissed.map((p, i) => (
            <div key={i} style={{ fontSize: 12, color: "#64748B", marginBottom: 3 }}><span style={{ color: "#EF4444" }}>✗ </span>{p}</div>
          ))}
        </div>
      )}
      {result.correctAnswer && (
        <div style={{ background: "rgba(16,185,129,0.04)", border: "1px solid rgba(16,185,129,0.2)", borderRadius: 8, padding: 12 }}>
          <div style={{ fontSize: 10, color: "#10B981", letterSpacing: 3, marginBottom: 6 }}>📖 모범 답안</div>
          <p style={{ color: "#64748B", fontSize: 12, margin: 0, lineHeight: 1.6 }}>{result.correctAnswer}</p>
        </div>
      )}
    </div>
  );
}

function AiCheckForm({ questId, onResult }) {
  const cfg = AI_FORMS[questId];
  const [values, setValues] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  if (!cfg) return null;

  const set = (k, v) => setValues(p => ({ ...p, [k]: v }));
  const setListItem = (k, i, v) => setValues(p => {
    const a = [...(p[k] || [])]; a[i] = v; return { ...p, [k]: a };
  });

  const submit = async () => {
    setLoading(true); setError("");
    try {
      const body = cfg.transform(values);
      const res = await callAiCheck(cfg.endpoint, body);
      if (res.success) onResult(res.data);
      else setError(res.message || "AI 평가 오류");
    } catch (e) { setError("서버 연결 오류: " + e.message); }
    finally { setLoading(false); }
  };

  const inputStyle = {
    width: "100%", background: "#0A0E1A", border: "1px solid rgba(255,255,255,0.08)",
    borderRadius: 8, padding: "9px 13px", color: "#F1F5F9", fontSize: 13,
    outline: "none", boxSizing: "border-box", fontFamily: "'Courier New', monospace", lineHeight: 1.6
  };

  return (
    <div style={{ marginTop: 18, animation: "slideIn 0.3s ease" }}>
      <div style={{ fontSize: 10, color: "#4ECDC4", letterSpacing: 4, marginBottom: 14 }}>🤖 AI SUBMISSION FORM</div>
      {cfg.fields.map(f => (
        <div key={f.key} style={{ marginBottom: 14 }}>
          <label style={{ fontSize: 12, color: "#64748B", display: "block", marginBottom: 6 }}>{f.label}</label>
          {f.type === "text" && <input value={values[f.key] || ""} onChange={e => set(f.key, e.target.value)} placeholder={f.placeholder} style={inputStyle} />}
          {f.type === "textarea" && <textarea value={values[f.key] || ""} onChange={e => set(f.key, e.target.value)} placeholder={f.placeholder} rows={f.rows || 5} style={{ ...inputStyle, resize: "vertical" }} />}
          {f.type === "list" && (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {[...Array(f.count)].map((_, i) => (
                <div key={i} style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <span style={{ fontSize: 12, color: "#334155", minWidth: 16 }}>{i+1}.</span>
                  <input value={(values[f.key]||[])[i]||""} onChange={e => setListItem(f.key, i, e.target.value)}
                    placeholder={f.placeholder} style={{ ...inputStyle, flex: 1 }} />
                </div>
              ))}
            </div>
          )}
        </div>
      ))}
      {error && <div style={{ background: "rgba(239,68,68,0.08)", border: "1px solid rgba(239,68,68,0.25)", borderRadius: 8, padding: 11, marginBottom: 12, fontSize: 13, color: "#EF4444" }}>⚠ {error}</div>}
      <button onClick={submit} disabled={loading} style={{
        width: "100%", padding: "12px", background: loading ? "rgba(78,205,196,0.2)" : "linear-gradient(135deg, #4ECDC4, #2DD4BF)",
        border: "none", borderRadius: 10, color: "#060610", fontSize: 14, fontWeight: "bold",
        cursor: loading ? "not-allowed" : "pointer", fontFamily: "'Courier New', monospace", letterSpacing: 1
      }}>{loading ? "⟳ AI 분석 중... (30초 소요)" : "🤖 AI 제출하기"}</button>
    </div>
  );
}

function MockInterviewPanel({ onComplete }) {
  const [questions] = useState(FALLBACK_QUESTIONS);
  const [idx, setIdx] = useState(0);
  const [answer, setAnswer] = useState("");
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [lastResult, setLastResult] = useState(null);
  const [done, setDone] = useState(false);

  const q = questions[idx];
  const totalScore = results.length ? Math.round(results.reduce((a, r) => a + r.score, 0) / results.length) : 0;

  const submit = async () => {
    if (!answer.trim()) return;
    setLoading(true);
    try {
      const res = await callAiCheck("mock-interview", {
        questId: "2-BOSS", questionId: q.id, question: q.question, answer, category: q.category
      });
      if (res.success) {
        const nr = [...results, res.data];
        setResults(nr); setLastResult(res.data);
        if (idx + 1 >= questions.length) {
          const avg = Math.round(nr.reduce((a, r) => a + r.score, 0) / nr.length);
          setDone(true); onComplete?.(avg);
        } else {
          setTimeout(() => { setIdx(i => i + 1); setAnswer(""); setLastResult(null); }, 2000);
        }
      }
    } catch (e) { }
    finally { setLoading(false); }
  };

  if (done) {
    const passed = totalScore >= 70;
    return (
      <div style={{ textAlign: "center", padding: "28px 0" }}>
        <div style={{ fontSize: 44, marginBottom: 12 }}>{passed ? "🏆" : "📚"}</div>
        <div style={{ fontSize: 24, fontWeight: "bold", color: passed ? "#10B981" : "#EF4444", marginBottom: 6 }}>
          최종 점수: {totalScore}/100
        </div>
        <div style={{ fontSize: 13, color: "#64748B", marginBottom: 20 }}>
          {passed ? "+800 XP 획득! 다음 Act 해금" : "70점 이상 필요. 재도전하세요"}
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {results.map((r, i) => (
            <div key={i} style={{ background: "rgba(13,17,23,0.7)", borderRadius: 8, padding: "9px 14px",
              display: "flex", justifyContent: "space-between" }}>
              <span style={{ fontSize: 13, color: "#64748B" }}>Q{i+1}. {questions[i]?.category}</span>
              <span style={{ fontSize: 13, fontWeight: "bold", color: r.score >= 70 ? "#10B981" : "#EF4444" }}>{r.score}점</span>
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
        <span style={{ fontSize: 12, color: "#475569" }}>Q {idx+1} / {questions.length}</span>
        <span style={{ fontSize: 12, color: "#4ECDC4" }}>{q.category}</span>
      </div>
      <div style={{ background: "#0A0E1A", borderRadius: 3, height: 4, marginBottom: 18, overflow: "hidden" }}>
        <div style={{ background: "#4ECDC4", height: "100%", width: `${(idx/questions.length)*100}%`, transition: "width 0.5s" }} />
      </div>
      <div style={{ background: "rgba(78,205,196,0.04)", border: "1px solid rgba(78,205,196,0.15)", borderRadius: 12, padding: 18, marginBottom: 14 }}>
        <div style={{ fontSize: 10, color: "#4ECDC4", letterSpacing: 3, marginBottom: 8 }}>QUESTION</div>
        <p style={{ fontSize: 14, color: "#F1F5F9", margin: 0, lineHeight: 1.7 }}>{q.question}</p>
      </div>
      {lastResult && <InterviewResultCard result={lastResult} />}
      {!lastResult && (
        <>
          <textarea value={answer} onChange={e => setAnswer(e.target.value)} rows={5}
            placeholder="답변을 구체적으로 작성해주세요..."
            style={{ width: "100%", background: "#0A0E1A", border: "1px solid rgba(255,255,255,0.08)", borderRadius: 8,
              padding: "10px 13px", color: "#F1F5F9", fontSize: 13, outline: "none", resize: "vertical",
              boxSizing: "border-box", lineHeight: 1.6, fontFamily: "'Courier New', monospace", marginBottom: 10 }} />
          <button onClick={submit} disabled={loading || !answer.trim()} style={{
            width: "100%", padding: "12px",
            background: loading ? "rgba(239,68,68,0.2)" : "linear-gradient(135deg, #EF4444, #DC2626)",
            border: "none", borderRadius: 10, color: "#fff",
            fontSize: 14, fontWeight: "bold", cursor: loading ? "not-allowed" : "pointer",
            fontFamily: "'Courier New', monospace"
          }}>{loading ? "⟳ 채점 중..." : "답변 제출 →"}</button>
        </>
      )}
    </div>
  );
}

// ─── Main App ─────────────────────────────────────────────────
const ACTS = [
  { id: 1, title: "ACT I", subtitle: "캐릭터 생성", color: "#4ECDC4", icon: "⚗️",
    quests: [
      { id: "1-1", type: "STUDY", title: "기술 스택 자가 진단", xp: 150, aiCheck: false, tag: "📋 진단", difficulty: 1,
        desc: "현재 기술을 레벨별로 분류하고 시장 JD와 갭 분석",
        tasks: ["보유 기술 상/중/하 자가 평가", "JD 5개 분석 후 갭 파악", "3개월 내 보완 기술 2가지 선정"] },
      { id: "1-2", type: "WRITE", title: "이직 동기 에세이", xp: 200, aiCheck: true, tag: "✍️ AI검사", difficulty: 1,
        desc: "AI가 이직 동기의 명확성·논리성·진정성을 4개 기준으로 평가한다",
        tasks: ["현 직장 불만족 사항 3가지", "이직 후 목표 3가지", "5년 후 비전 200자 → AI 제출"] },
      { id: "1-BOSS", type: "BOSS", title: "🐉 BOSS: 개발자 클래스 판별", xp: 500, aiCheck: false, tag: "⚔️ 보스전", difficulty: 2,
        desc: "Act I 데이터를 종합해 AI가 개발자 유형과 맞춤 전략을 제시한다",
        tasks: ["1-1, 1-2 완료 데이터 종합", "AI 클래스 분석 수령", "맞춤 이직 전략 3가지 확인"] }
    ]
  },
  { id: 2, title: "ACT II", subtitle: "스킬 강화", color: "#A78BFA", icon: "⚔️",
    quests: [
      { id: "2-1", type: "STUDY", title: "약점 기술 집중 공략", xp: 400, aiCheck: false, tag: "📚 학습", difficulty: 3,
        desc: "Act I에서 발견한 약점 기술 2주 심화 학습",
        tasks: ["약점 기술 2가지 학습 계획 수립", "공식 문서 + 책 1권 완독", "실습 코드 GitHub 업로드"] },
      { id: "2-2", type: "WRITE", title: "기술 블로그 검사", xp: 600, aiCheck: true, tag: "✍️ AI검사", difficulty: 3,
        desc: "AI가 기술적 정확성·깊이·코드 품질을 채점하고 S~D 등급 부여",
        tasks: ["주제 선정 (JVM GC, DB 인덱스 등)", "1500자+ 작성, 코드 예제 포함", "AI 제출 → 피드백 반영"] },
      { id: "2-3", type: "BUILD", title: "시스템 설계 챌린지", xp: 500, aiCheck: true, tag: "🏗️ AI검사", difficulty: 4,
        desc: "실전 시스템 설계를 AI가 5개 기준으로 평가한다",
        tasks: ["배달앱/유튜브/채팅 중 선택", "아키텍처 설명 + 고려 사항 작성", "AI 피드백 수령 후 개선"] },
      { id: "2-4", type: "BUILD", title: "코딩 테스트 30문제", xp: 450, aiCheck: false, tag: "💻 코딩", difficulty: 3,
        desc: "알고리즘 실력을 증명하는 15일 스프린트",
        tasks: ["Medium 30문제 (하루 2문제)", "못 푼 문제 AI 풀이 분석", "패턴별 오답 노트 작성"] },
      { id: "2-BOSS", type: "BOSS", title: "🐉 BOSS: 모의 기술 면접", xp: 800, aiCheck: true, tag: "⚔️ 보스전", difficulty: 5,
        desc: "AI 면접관과 10문제 실전 면접. 평균 70점 이상 통과.",
        tasks: ["카테고리별 10문제 랜덤 출제", "AI가 4개 기준 실시간 채점", "평균 70점 이상 통과 시 Act III 해금"] }
    ]
  },
  { id: 3, title: "ACT III", subtitle: "세계 탐색", color: "#F59E0B", icon: "🗺️",
    quests: [
      { id: "3-1", type: "DISCOVER", title: "관심 회사 10곳 리스트업", xp: 250, aiCheck: false, tag: "🔍 탐색", difficulty: 2,
        desc: "원티드·링크드인·잡플래닛으로 타겟 10곳 선정 및 정리",
        tasks: ["관심 회사 10곳 리서치", "기술스택, 팀 규모, 리뷰 정리", "핏 기준으로 5곳 압축"] },
      { id: "3-2", type: "DISCOVER", title: "JD 역분석", xp: 350, aiCheck: true, tag: "📊 AI검사", difficulty: 3,
        desc: "AI가 JD를 분석해 숨겨진 요구사항과 기술 갭을 파악한다",
        tasks: ["타겟 회사 JD 붙여넣기", "AI가 필수/우대 기술 + 갭 분석", "지원 전략 및 매칭 점수 수령"] },
      { id: "3-BOSS", type: "BOSS", title: "🐉 BOSS: 최종 타겟 3곳 확정", xp: 600, aiCheck: false, tag: "⚔️ 보스전", difficulty: 3,
        desc: "모든 데이터를 종합해 지원할 회사 A/B/C를 확정한다",
        tasks: ["핏 분석 + JD 갭 데이터 종합", "A/B/C 우선순위 확정", "회사별 맞춤 전략 수립"] }
    ]
  },
  { id: 4, title: "ACT IV", subtitle: "장비 제작", color: "#10B981", icon: "🛡️",
    quests: [
      { id: "4-1", type: "BUILD", title: "이력서 STAR 검토", xp: 500, aiCheck: true, tag: "📄 AI검사", difficulty: 3,
        desc: "AI가 STAR 기법, 수치화, JD 키워드 매칭을 채점하고 개선 예시를 제공한다",
        tasks: ["이력서 초안 작성", "AI에 제출 → 상세 피드백", "AI 개선 예시 반영 후 최종본"] },
      { id: "4-2", type: "BUILD", title: "GitHub 프로필 리모델링", xp: 300, aiCheck: false, tag: "💻 빌드", difficulty: 2,
        desc: "30초 안에 감탄하게 만드는 GitHub",
        tasks: ["README 프로필 작성", "프로젝트 README 개선 (구조, 스크린샷 포함)", "최근 커밋 활동 추가"] },
      { id: "4-BOSS", type: "BOSS", title: "🐉 BOSS: 지원 패키지 완성", xp: 700, aiCheck: true, tag: "⚔️ 보스전", difficulty: 4,
        desc: "이력서, GitHub, 블로그 전체를 AI로 최종 점검한다",
        tasks: ["최종 이력서 AI 재검토", "포트폴리오 링크 정리", "지원 패키지 완성 확인"] }
    ]
  },
  { id: 5, title: "ACT V", subtitle: "최종 보스전", color: "#EF4444", icon: "👑",
    quests: [
      { id: "5-1", type: "STUDY", title: "인성 면접 연습", xp: 400, aiCheck: true, tag: "🎤 AI검사", difficulty: 3,
        desc: "AI가 인성 면접 답변의 구체성·진정성·성장 마인드를 평가한다",
        tasks: ["핵심 인성 질문 10개 선정", "AI와 1:1 롤플레이", "피드백 반영 후 개선"] },
      { id: "5-2", type: "BUILD", title: "실전 지원 개시", xp: 500, aiCheck: false, tag: "📨 지원", difficulty: 3,
        desc: "드디어 서류를 넣는다",
        tasks: ["A사 지원 및 면접 예상 질문 시뮬레이션", "B사, C사 순차 지원", "면접 일정 관리"] },
      { id: "5-BOSS", type: "BOSS", title: "👑 FINAL BOSS: 합격!", xp: 2000, aiCheck: false, tag: "🏆 클리어", difficulty: 5,
        desc: "게임 클리어. 새로운 챕터의 시작.",
        tasks: ["면접 통과", "오퍼 레터 수령", "AI 연봉 협상 시뮬레이션"] }
    ]
  }
];

const QC = {
  STUDY: { bg: "rgba(96,165,250,0.08)", border: "#60A5FA", icon: "📚" },
  WRITE: { bg: "rgba(167,139,250,0.08)", border: "#A78BFA", icon: "✍️" },
  DISCOVER: { bg: "rgba(251,191,36,0.08)", border: "#FBBf24", icon: "🔍" },
  BUILD: { bg: "rgba(52,211,153,0.08)", border: "#34D399", icon: "⚒️" },
  BOSS: { bg: "rgba(239,68,68,0.05)", border: "#EF4444", icon: "⚔️" }
};

export default function App() {
  const [act, setAct] = useState(null);
  const [quest, setQuest] = useState(null);
  const [phase, setPhase] = useState("map");
  const [completed, setCompleted] = useState({});
  const [aiScores, setAiScores] = useState({});
  const [totalXp, setTotalXp] = useState(0);
  const [aiResult, setAiResult] = useState(null);
  const [showForm, setShowForm] = useState(false);

  const progress = (a) => {
    const done = a.quests.filter(q => completed[q.id]).length;
    return Math.round((done / a.quests.length) * 100);
  };

  const complete = (qid, xp) => {
    if (completed[qid]) return;
    setCompleted(p => ({ ...p, [qid]: true }));
    setTotalXp(p => p + xp);
  };

  const handleAiResult = (result) => {
    setAiResult(result);
    if (!quest) return;
    const score = result.score ?? result.overallScore ?? 0;
    const passed = result.passed ?? score >= 70;
    setAiScores(p => ({ ...p, [quest.id]: score }));
    if (passed) complete(quest.id, quest.xp);
    setShowForm(false);
  };

  const lv = Math.floor(totalXp / 500) + 1;

  const goBack = () => {
    if (phase === "quest") { setPhase("act"); setQuest(null); setAiResult(null); setShowForm(false); }
    else { setPhase("map"); setAct(null); }
  };

  return (
    <div style={{ minHeight: "100vh", background: "#060610", fontFamily: "'Courier New', monospace", color: "#E2E8F0" }}>
      <style>{`
        @keyframes slideIn { from { opacity:0; transform: translateY(14px); } to { opacity:1; transform:translateY(0); } }
        @keyframes float { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-6px)} }
        @keyframes bossP { 0%,100%{box-shadow:0 0 16px rgba(239,68,68,0.25)} 50%{box-shadow:0 0 40px rgba(239,68,68,0.5)} }
        .hov-act { transition: all 0.3s; } .hov-act:hover { transform: translateY(-4px); }
        .hov-q { transition: all 0.2s; cursor: pointer; } .hov-q:hover { transform: translateX(3px); }
        .hov-btn { transition: all 0.2s; } .hov-btn:hover { filter: brightness(1.2); transform: scale(1.02); }
        input, textarea { transition: border-color 0.2s; }
        input:focus, textarea:focus { border-color: rgba(78,205,196,0.4) !important; }
        ::-webkit-scrollbar { width: 3px; } ::-webkit-scrollbar-thumb { background: #1E293B; }
      `}</style>

      {/* Header */}
      <div style={{ position: "sticky", top: 0, zIndex: 100, background: "rgba(6,6,16,0.96)", backdropFilter: "blur(16px)",
        borderBottom: "1px solid rgba(255,255,255,0.05)", padding: "11px 20px",
        display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          {phase !== "map" && (
            <button className="hov-btn" onClick={goBack} style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)",
              color: "#64748B", padding: "5px 12px", borderRadius: 7, cursor: "pointer", fontSize: 12 }}>← Back</button>
          )}
          <span style={{ fontSize: 17, fontWeight: "bold", letterSpacing: 3 }}>⚡ DEV<span style={{ color: "#4ECDC4" }}>QUEST</span></span>
          <span style={{ fontSize: 10, color: "#1E293B", letterSpacing: 2 }}>Spring AI · Backend</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ fontSize: 12, color: "#475569" }}>{totalXp.toLocaleString()} XP</div>
          <div style={{ background: "rgba(78,205,196,0.08)", border: "1px solid rgba(78,205,196,0.3)",
            borderRadius: 8, padding: "4px 12px", fontSize: 12, color: "#4ECDC4", fontWeight: "bold" }}>LV {lv}</div>
        </div>
      </div>

      <div style={{ maxWidth: 860, margin: "0 auto", padding: "0 16px 60px" }}>

        {/* MAP */}
        {phase === "map" && (
          <div style={{ animation: "slideIn 0.4s ease" }}>
            <div style={{ textAlign: "center", padding: "40px 0 24px" }}>
              <div style={{ fontSize: 10, letterSpacing: 6, color: "#1E293B", marginBottom: 5 }}>5YR BACKEND DEVELOPER</div>
              <h1 style={{ fontSize: 30, fontWeight: "bold", margin: 0, letterSpacing: 2, color: "#F8FAFC" }}>이직 퀘스트</h1>
              <p style={{ color: "#334155", fontSize: 12, marginTop: 8 }}>Spring AI 기반 퀘스트 검사 · 퀘스트 완료로 이직 성공</p>
            </div>

            {/* API Status Banner */}
            <div style={{ background: "rgba(78,205,196,0.04)", border: "1px solid rgba(78,205,196,0.15)",
              borderRadius: 10, padding: "10px 16px", marginBottom: 20,
              display: "flex", alignItems: "center", gap: 10, fontSize: 12 }}>
              <span style={{ color: "#4ECDC4" }}>🔌</span>
              <span style={{ color: "#475569" }}>Spring Boot 4.x + Spring AI 연동 필요</span>
              <span style={{ color: "#1E293B", marginLeft: "auto" }}>localhost:8080</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {ACTS.map((a, i) => {
                const prog = progress(a);
                const locked = i > 0 && progress(ACTS[i-1]) < 75;
                return (
                  <div key={a.id} className="hov-act"
                    onClick={() => !locked && (setAct(a), setPhase("act"))}
                    style={{ background: "rgba(10,14,26,0.9)", border: `1px solid ${locked ? "rgba(255,255,255,0.03)" : a.color + "25"}`,
                      borderRadius: 12, padding: "16px 20px", cursor: locked ? "not-allowed" : "pointer",
                      opacity: locked ? 0.4 : 1, display: "flex", alignItems: "center", gap: 16, position: "relative", overflow: "hidden" }}>
                    <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: 3, background: locked ? "#0F172A" : a.color, borderRadius: "12px 0 0 12px" }} />
                    <div style={{ fontSize: 32, minWidth: 44, textAlign: "center", animation: locked ? "none" : "float 3s ease-in-out infinite" }}>
                      {locked ? "🔒" : a.icon}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                        <span style={{ fontSize: 9, letterSpacing: 4, color: a.color }}>{a.title}</span>
                        <span style={{ fontSize: 12, color: "#334155" }}>{a.subtitle}</span>
                        {locked && <span style={{ fontSize: 10, color: "#EF4444", marginLeft: "auto" }}>이전 ACT 75% 필요</span>}
                      </div>
                      <div style={{ fontSize: 14, fontWeight: "bold", color: "#E2E8F0", marginBottom: 8 }}>
                        {a.quests.map(q => q.title.replace("🐉 BOSS: ", "").replace("👑 FINAL BOSS: ", "")).join(" → ")}
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <div style={{ flex: 1, background: "#0A0E1A", borderRadius: 3, height: 3, overflow: "hidden" }}>
                          <div style={{ background: a.color, height: "100%", width: `${prog}%`, transition: "width 0.5s" }} />
                        </div>
                        <span style={{ fontSize: 10, color: "#334155" }}>{prog}% · {a.quests.filter(q => completed[q.id]).length}/{a.quests.length}</span>
                        <span style={{ fontSize: 10, color: "#1E293B" }}>
                          {a.quests.reduce((s, q) => s + q.xp, 0).toLocaleString()} XP
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div style={{ marginTop: 20, padding: "14px 20px", background: "rgba(10,14,26,0.6)",
              border: "1px solid rgba(255,255,255,0.04)", borderRadius: 10,
              display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 16, textAlign: "center" }}>
              {[
                { l: "총 퀘스트", v: ACTS.reduce((a, x) => a + x.quests.length, 0), c: "#4ECDC4" },
                { l: "완료", v: Object.keys(completed).length, c: "#10B981" },
                { l: "AI 검사", v: ACTS.reduce((a, x) => a + x.quests.filter(q => q.aiCheck).length, 0), c: "#A78BFA" },
                { l: "총 XP", v: ACTS.reduce((a, x) => a + x.quests.reduce((b, q) => b + q.xp, 0), 0).toLocaleString(), c: "#F59E0B" },
              ].map(s => (
                <div key={s.l}>
                  <div style={{ fontSize: 20, fontWeight: "bold", color: s.c }}>{s.v}</div>
                  <div style={{ fontSize: 10, color: "#334155", marginTop: 2 }}>{s.l}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ACT VIEW */}
        {phase === "act" && act && (
          <div style={{ animation: "slideIn 0.4s ease" }}>
            <div style={{ padding: "24px 0 18px", display: "flex", alignItems: "center", gap: 14 }}>
              <div style={{ fontSize: 40, animation: "float 3s ease-in-out infinite" }}>{act.icon}</div>
              <div>
                <div style={{ fontSize: 9, letterSpacing: 4, color: act.color, marginBottom: 3 }}>{act.title}</div>
                <h2 style={{ margin: 0, fontSize: 22, fontWeight: "bold" }}>{act.subtitle}</h2>
              </div>
            </div>

            <div style={{ background: "rgba(10,14,26,0.6)", border: `1px solid ${act.color}20`, borderRadius: 10,
              padding: "10px 14px", marginBottom: 18, display: "flex", alignItems: "center", gap: 10 }}>
              <div style={{ flex: 1, background: "#060610", borderRadius: 3, height: 5, overflow: "hidden" }}>
                <div style={{ background: act.color, height: "100%", width: `${progress(act)}%`, transition: "width 0.5s" }} />
              </div>
              <span style={{ fontSize: 12, color: act.color, fontWeight: "bold" }}>{progress(act)}%</span>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
              {act.quests.map(q => {
                const qc = QC[q.type];
                const done = completed[q.id];
                const score = aiScores[q.id];
                return (
                  <div key={q.id} className="hov-q"
                    onClick={() => { setQuest(q); setAiResult(null); setShowForm(false); setPhase("quest"); }}
                    style={{ background: q.type === "BOSS" ? "rgba(239,68,68,0.04)" : qc.bg,
                      border: `1px solid ${done ? "#10B98140" : qc.border + "25"}`,
                      borderRadius: 11, padding: "13px 16px",
                      animation: q.type === "BOSS" ? "bossP 3s ease-in-out infinite" : "none",
                      display: "flex", alignItems: "center", gap: 12 }}>
                    <div style={{ fontSize: 22, minWidth: 32 }}>{qc.icon}</div>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 3 }}>
                        <span style={{ fontSize: 9, color: qc.border, background: `${qc.border}12`, padding: "2px 7px", borderRadius: 4 }}>{q.tag}</span>
                        {q.aiCheck && <span style={{ fontSize: 10, color: "#A78BFA" }}>🤖 AI</span>}
                      </div>
                      <div style={{ fontSize: 14, fontWeight: "bold", color: done ? "#475569" : "#E2E8F0" }}>{q.title}</div>
                      <div style={{ fontSize: 11, color: "#334155", marginTop: 2 }}>{q.desc}</div>
                    </div>
                    <div style={{ textAlign: "right", minWidth: 52 }}>
                      {done ? (
                        <div>
                          <div style={{ fontSize: 16, color: "#10B981" }}>✓</div>
                          {score != null && <div style={{ fontSize: 10, color: "#475569" }}>{score}점</div>}
                        </div>
                      ) : (
                        <div>
                          <div style={{ fontSize: 12, color: "#F59E0B", fontWeight: "bold" }}>+{q.xp}</div>
                          <div style={{ fontSize: 10, color: "#1E293B" }}>XP</div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* QUEST DETAIL */}
        {phase === "quest" && quest && act && (() => {
          const qc = QC[quest.type];
          const done = completed[quest.id];
          const isMock = quest.id === "2-BOSS";
          const hasForm = !!AI_FORMS[quest.id];

          return (
            <div style={{ animation: "slideIn 0.4s ease" }}>
              <div style={{ padding: "24px 0 14px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 14 }}>
                  <div style={{ fontSize: 36 }}>{qc.icon}</div>
                  <div>
                    <div style={{ display: "flex", gap: 7, marginBottom: 5 }}>
                      <span style={{ fontSize: 9, color: qc.border, background: `${qc.border}12`, padding: "2px 8px", borderRadius: 4 }}>{quest.tag}</span>
                      {quest.aiCheck && <span style={{ fontSize: 9, color: "#A78BFA", background: "rgba(167,139,250,0.1)", padding: "2px 8px", borderRadius: 4 }}>🤖 Spring AI 검사</span>}
                    </div>
                    <h2 style={{ margin: 0, fontSize: 20, fontWeight: "bold" }}>{quest.title}</h2>
                    <p style={{ margin: "5px 0 0", fontSize: 13, color: "#475569", lineHeight: 1.5 }}>{quest.desc}</p>
                  </div>
                </div>

                {/* XP Card */}
                <div style={{ background: "rgba(245,158,11,0.04)", border: "1px solid rgba(245,158,11,0.18)",
                  borderRadius: 10, padding: "11px 15px", marginBottom: 16, display: "flex", alignItems: "center", gap: 10 }}>
                  <span style={{ fontSize: 18 }}>🎁</span>
                  <div>
                    <span style={{ fontSize: 14, color: "#F59E0B", fontWeight: "bold" }}>+{quest.xp} XP</span>
                    {quest.aiCheck && <span style={{ fontSize: 11, color: "#64748B", marginLeft: 10 }}>· AI 통과 시 완료 처리</span>}
                  </div>
                </div>

                {/* Tasks */}
                <div style={{ background: "rgba(10,14,26,0.8)", border: `1px solid ${qc.border}18`, borderRadius: 10, padding: 16, marginBottom: 16 }}>
                  <div style={{ fontSize: 9, letterSpacing: 4, color: "#1E293B", marginBottom: 10 }}>TASKS</div>
                  {quest.tasks.map((t, i) => (
                    <div key={i} style={{ display: "flex", gap: 9, marginBottom: i < quest.tasks.length - 1 ? 10 : 0 }}>
                      <div style={{ minWidth: 20, height: 20, borderRadius: "50%", background: `${qc.border}10`,
                        border: `1px solid ${qc.border}30`, display: "flex", alignItems: "center", justifyContent: "center",
                        fontSize: 10, color: qc.border, fontWeight: "bold" }}>{i+1}</div>
                      <div style={{ fontSize: 13, color: "#94A3B8", lineHeight: 1.6, paddingTop: 1 }}>{t}</div>
                    </div>
                  ))}
                </div>

                {/* AI Section */}
                {quest.aiCheck && !done && (
                  <div>
                    {isMock ? (
                      <>
                        <div style={{ fontSize: 10, color: "#EF4444", letterSpacing: 3, marginBottom: 12 }}>⚔️ BOSS BATTLE — AI 모의 기술 면접</div>
                        <MockInterviewPanel onComplete={(score) => {
                          setAiScores(p => ({ ...p, [quest.id]: score }));
                          if (score >= 70) complete(quest.id, quest.xp);
                        }} />
                      </>
                    ) : hasForm ? (
                      !showForm ? (
                        <button className="hov-btn" onClick={() => setShowForm(true)} style={{
                          width: "100%", padding: "12px",
                          background: "rgba(167,139,250,0.08)", border: "1px solid rgba(167,139,250,0.3)",
                          borderRadius: 10, color: "#A78BFA", fontSize: 14, fontWeight: "bold",
                          cursor: "pointer", fontFamily: "'Courier New', monospace"
                        }}>🤖 AI 검사 시작하기</button>
                      ) : (
                        <AiCheckForm questId={quest.id} onResult={handleAiResult} />
                      )
                    ) : null}
                  </div>
                )}

                {/* AI Result */}
                {aiResult && !isMock && <AiResultCard result={aiResult} />}

                {/* Manual complete */}
                {!quest.aiCheck && !done && (
                  <button className="hov-btn" onClick={() => complete(quest.id, quest.xp)} style={{
                    width: "100%", padding: "12px",
                    background: `linear-gradient(135deg, ${act.color}, ${act.color}80)`,
                    border: "none", borderRadius: 10, color: "#060610",
                    fontSize: 14, fontWeight: "bold", cursor: "pointer", fontFamily: "'Courier New', monospace"
                  }}>🏆 완료로 표시하기</button>
                )}

                {/* Done state */}
                {done && (
                  <div style={{ textAlign: "center", padding: 22, background: "rgba(16,185,129,0.04)",
                    border: "1px solid rgba(16,185,129,0.18)", borderRadius: 12 }}>
                    <div style={{ fontSize: 30, marginBottom: 8 }}>✅</div>
                    <div style={{ fontSize: 14, color: "#10B981", fontWeight: "bold" }}>퀘스트 완료!</div>
                    {aiScores[quest.id] != null && (
                      <div style={{ fontSize: 12, color: "#475569", marginTop: 4 }}>AI 점수: {aiScores[quest.id]}/100</div>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })()}
      </div>
    </div>
  );
}
