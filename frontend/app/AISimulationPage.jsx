import { useState, useMemo } from "react";
import { ChevronDown, AlertTriangle, CheckCircle } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
// ─── Tokens ──────────────────────────────────────────────────────────────────
const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
// ─── Simulation engine ────────────────────────────────────────────────────────
const WEATHER_FACTORS = {
    "Clear": 1.00,
    "Wet Rails": 1.15,
    "Heavy Monsoon": 1.40,
};
const STATIC_SLOTS = [
    { label: "AM Peak", s: 480 },
    { label: "Mid-Day", s: 300 },
    { label: "PM Peak", s: 540 },
    { label: "Late Evening", s: 360 },
];
function simulate(weather, surge, incident) {
    const wf = WEATHER_FACTORS[weather] ?? 1;
    const rawHeadway = Math.floor(420 * wf / surge);
    const headway = Math.max(120, rawHeadway + (incident ? 60 : 0));
    const fleet = Math.min(20, Math.max(4, Math.ceil(10 * surge / wf)));
    const capacity = Math.min(100, Math.floor(fleet * 10 / surge));
    // AI-optimised headways per slot (AI achieves ~18% gain + surge benefit - weather penalty)
    const aiSlots = STATIC_SLOTS.map(({ s }) => Math.max(90, Math.floor(s * wf / (surge * 1.18)) + (incident ? 45 : 0)));
    const barMax = Math.max(...STATIC_SLOTS.map(s => s.s), ...aiSlots);
    // Metrics
    const avgStatic = STATIC_SLOTS.reduce((a, b) => a + b.s, 0) / 4;
    const avgAI = aiSlots.reduce((a, b) => a + b, 0) / 4;
    const waitReduction = ((avgStatic - avgAI) / 60).toFixed(1);
    const regenSaving = Math.max(5, Math.floor(10 + surge * 6 - (wf - 1) * 20 + (incident ? -3 : 2))).toFixed(1);
    const cbtcSync = incident ? "94.2%" : "99.8%";
    const loadBalance = incident ? "Degraded" : surge > 2.5 ? "High Demand" : "Nominal Flow";
    return { headway, fleet, capacity, aiSlots, barMax, waitReduction, regenSaving, cbtcSync, loadBalance };
}
const PRESETS = {
    "Off-Peak": { weather: "Clear", surge: 1.0, incident: false },
    "AM Peak": { weather: "Clear", surge: 2.5, incident: false },
    "Monsoon Rush": { weather: "Heavy Monsoon", surge: 3.0, incident: false },
    "Corridor Delay": { weather: "Wet Rails", surge: 2.0, incident: true },
};
// ─── Track map ────────────────────────────────────────────────────────────────
const STATIONS = [
    { code: "ALU", name: "Aluva", t: 0.08 },
    { code: "EDP", name: "Edapally", t: 0.30 },
    { code: "JLN", name: "JLN Stadium", t: 0.53 },
    { code: "MGR", name: "MG Road", t: 0.74 },
    { code: "TPT", name: "Thrippunithura", t: 0.92 },
];
function TrackMap({ fleet, incident }) {
    const W = 760;
    const H = 160;
    const trackPad = 60;
    const trackLen = W - trackPad * 2;
    const upY = 52;
    const dnY = 108;
    const mgr = STATIONS[3]; // MG Road — goes red when incident
    // Distribute trains evenly on up/down lines
    const count = Math.min(fleet, 10);
    const trains = Array.from({ length: count }, (_, i) => {
        const frac = (i + 0.5) / count; // evenly spaced 0→1
        const x = trackPad + frac * trackLen;
        const y = i % 2 === 0 ? upY : dnY;
        return { x, y, label: `T${i + 1}` };
    });
    return (<div style={{ background: "#0b0f19", borderRadius: 12, padding: "14px 18px 10px", position: "relative" }}>
      {/* Status pill */}
      <div style={{ position: "absolute", top: 14, right: 18, zIndex: 2 }}>
        {incident ? (<span className="flex items-center gap-1.5 animate-pulse" style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: "#EF4444", background: "rgba(239,68,68,0.15)", border: "1px solid rgba(239,68,68,0.35)", padding: "4px 10px", borderRadius: 100 }}>
            <AlertTriangle size={10}/> Drift Warning: MG Road Section
          </span>) : (<span className="flex items-center gap-1.5" style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: "#10B981", background: "rgba(16,185,129,0.12)", border: "1px solid rgba(16,185,129,0.3)", padding: "4px 10px", borderRadius: 100 }}>
            <CheckCircle size={10}/> Line Clear — Active CBTC Loop
          </span>)}
      </div>

      {/* Track line labels */}
      <div style={{ marginBottom: 2 }}>
        <span style={{ fontFamily: SANS, fontSize: 9, color: "#475569", letterSpacing: "0.04em" }}>Up-Line Track (Eastbound)</span>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ display: "block" }} aria-label="KMRL track alignment map">
        <defs>
          {/* Grid texture */}
          <pattern id="simGrid" width="32" height="32" patternUnits="userSpaceOnUse">
            <circle cx="32" cy="32" r="0.6" fill="rgba(255,255,255,0.04)"/>
          </pattern>
          {/* Station glow */}
          <filter id="stGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="3" result="blur"/>
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
          <filter id="trainGlow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="1.5" result="blur"/>
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
        </defs>

        {/* Grid background */}
        <rect width={W} height={H} fill="url(#simGrid)"/>

        {/* Track lines */}
        <line x1={trackPad} y1={upY} x2={W - trackPad} y2={upY} stroke="rgba(0,150,136,0.35)" strokeWidth="2" strokeLinecap="round"/>
        <line x1={trackPad} y1={dnY} x2={W - trackPad} y2={dnY} stroke="rgba(0,150,136,0.20)" strokeWidth="1.5" strokeLinecap="round" strokeDasharray="6 4"/>

        {/* Stations */}
        {STATIONS.map(st => {
            const x = trackPad + st.t * trackLen;
            const isMGR = st.code === "MGR";
            const isAlert = isMGR && incident;
            const dotColor = isAlert ? "#EF4444" : "#3B82F6";
            return (<g key={st.code}>
              {/* Up-line dot */}
              <circle cx={x} cy={upY} r={9} fill={`${dotColor}18`} stroke={dotColor} strokeWidth="1.5" filter="url(#stGlow)"/>
              <circle cx={x} cy={upY} r={4.5} fill={dotColor} opacity={isAlert ? 1 : 0.9}/>
              {/* Down-line dot */}
              <circle cx={x} cy={dnY} r={6} fill={`${dotColor}12`} stroke={`${dotColor}50`} strokeWidth="1"/>
              <circle cx={x} cy={dnY} r={3} fill={dotColor} opacity={0.6}/>
              {/* Station code (above up line) */}
              <text x={x} y={upY - 15} textAnchor="middle" fontSize="8.5" fontFamily="'JetBrains Mono', monospace" fontWeight="700" fill={dotColor} opacity={0.9}>
                {st.code}
              </text>
              {/* Station name (below down line) */}
              <text x={x} y={H - 4} textAnchor="middle" fontSize="8" fontFamily="Inter, sans-serif" fill="#475569">
                {st.name}
              </text>
            </g>);
        })}

        {/* Train icons */}
        {trains.map(tr => (<g key={tr.label} filter="url(#trainGlow)">
            <rect x={tr.x - 13} y={tr.y - 6} width={26} height={12} rx={4} fill={teal} opacity={0.92}/>
            <text x={tr.x} y={tr.y + 4} textAnchor="middle" fontSize="7.5" fontFamily="Inter, sans-serif" fontWeight="800" fill="#fff">
              {tr.label}
            </text>
          </g>))}

        {/* Direction labels */}
        <text x={W - trackPad + 10} y={upY + 4} fontSize="8" fontFamily="Inter, sans-serif" fill="#334155">▶</text>
        <text x={W - trackPad + 10} y={dnY + 4} fontSize="8" fontFamily="Inter, sans-serif" fill="#334155">◀</text>
      </svg>

      <div style={{ marginTop: -2 }}>
        <span style={{ fontFamily: SANS, fontSize: 9, color: "#475569", letterSpacing: "0.04em" }}>Down-Line Track (Westbound)</span>
      </div>
    </div>);
}
// ─── Timetable chart ──────────────────────────────────────────────────────────
function TimetableSection({ aiSlots, barMax }) {
    return (<div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 18 }}>
      {STATIC_SLOTS.map((slot, i) => {
            const sw = (slot.s / barMax) * 100;
            const aw = (aiSlots[i] / barMax) * 100;
            return (<div key={slot.label}>
            <p style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB, marginBottom: 8 }}>
              {slot.label}
            </p>
            {/* Static bar */}
            <div className="flex items-center gap-3 mb-2">
              <div style={{ flex: 1, height: 9, background: "#E2E8F0", borderRadius: 5, overflow: "hidden" }}>
                <div style={{ width: `${sw}%`, height: "100%", background: "#CBD5E1", borderRadius: 5, transition: "width 0.5s ease" }}/>
              </div>
              <span style={{ fontFamily: MONO, fontSize: 11, color: inkM, width: 42, textAlign: "right", flexShrink: 0 }}>
                {slot.s}s
              </span>
            </div>
            {/* AI bar */}
            <div className="flex items-center gap-3">
              <div style={{ flex: 1, height: 9, background: "rgba(0,150,136,0.10)", borderRadius: 5, overflow: "hidden" }}>
                <div style={{ width: `${aw}%`, height: "100%", background: `linear-gradient(90deg, ${teal}, ${emerald})`, borderRadius: 5, transition: "width 0.5s ease" }}/>
              </div>
              <span style={{ fontFamily: MONO, fontSize: 11, color: teal, width: 42, textAlign: "right", flexShrink: 0, fontWeight: 700, transition: "all 0.3s" }}>
                {aiSlots[i]}s
              </span>
            </div>
          </div>);
        })}
    </div>);
}
export default function AISimulationPage({ isSignedIn, onNavigate, onLogOut, userName, userRole }) {
    const [preset, setPreset] = useState("Off-Peak");
    const [weather, setWeather] = useState("Clear");
    const [surge, setSurge] = useState(1.0);
    const [incident, setIncident] = useState(false);
    const sim = useMemo(() => simulate(weather, surge, incident), [weather, surge, incident]);
    function applyPreset(name) {
        const p = PRESETS[name];
        setPreset(name);
        setWeather(p.weather);
        setSurge(p.surge);
        setIncident(p.incident);
    }
    function handleWeather(v) { setWeather(v); setPreset(null); }
    function handleSurge(v) { setSurge(v); setPreset(null); }
    function handleIncident(v) { setIncident(v); setPreset(null); }
    const metricCards = [
        { label: "Optimized Headway", value: `${sim.headway}s`, sub: "Target spacing deviation" },
        { label: "Fleet Induction", value: `${sim.fleet} sets`, sub: "Immediate action requirement" },
        { label: "System Capacity", value: `${sim.capacity}%`, sub: "Peak-relative line volume" },
    ];
    const optMetrics = [
        { label: "Avg Wait Reduction", value: `-${sim.waitReduction} min`, color: emerald },
        { label: "Regen Brake Saving", value: `${sim.regenSaving}%`, color: teal },
        { label: "CBTC Loop Sync", value: sim.cbtcSync, color: incident ? "#EF4444" : teal },
        { label: "Load Balancing", value: sim.loadBalance, color: sim.loadBalance === "Nominal Flow" ? emerald : sim.loadBalance === "High Demand" ? "#F59E0B" : "#EF4444" },
    ];
    return (<div className="min-h-screen flex flex-col" style={{ background: "#F8FAFC" }}>
      <SharedHeader activePage="aisimulation" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      <main className="flex-1">

        {/* ── Hero ── */}
        <div className="max-w-7xl mx-auto px-6 pt-10 pb-8">
          <h1 style={{ fontFamily: DISPLAY, fontSize: 28, fontWeight: 800, color: inkH, letterSpacing: "-0.025em", lineHeight: 1.15, marginBottom: 12 }}>
            Predictive Dispatch &amp; Simulation Sandbox
          </h1>
          <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, lineHeight: 1.7, maxWidth: 640 }}>
            Analyse MetroMind&apos;s AI headway optimizer and fleet sizing recommendations. Tweak weather variables, passenger surges, and compare optimised schedules against static baseline timetables.
          </p>
        </div>

        {/* ── Workspace: sidebar + right ── */}
        <div className="max-w-7xl mx-auto px-6 pb-6">
          <div className="flex gap-5 items-start">

            {/* ── Left sidebar ── */}
            <div style={{ width: 310, flexShrink: 0, background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, padding: "20px 20px 22px" }}>

              {/* Scenario Presets */}
              <p style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: inkM, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 10 }}>
                Scenario Presets
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 20 }}>
                {Object.keys(PRESETS).map(name => {
            const active = preset === name;
            return (<button key={name} onClick={() => applyPreset(name)} style={{
                    fontFamily: SANS, fontSize: 12, fontWeight: active ? 700 : 500,
                    padding: "9px 8px", borderRadius: 10, textAlign: "center", cursor: "pointer",
                    border: active ? "none" : `1px solid ${bd}`,
                    background: active ? teal : "#F1F5F9",
                    color: active ? "#fff" : inkB,
                    transition: "all 0.15s",
                }}>
                      {name}
                    </button>);
        })}
              </div>

              <div style={{ borderTop: `1px solid ${bd}`, marginBottom: 18 }}/>

              {/* Environmental Variables */}
              <p style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: inkM, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 14 }}>
                Environmental Variables
              </p>

              {/* Weather dropdown */}
              <div style={{ marginBottom: 16 }}>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB, display: "block", marginBottom: 7 }}>
                  Track Weather Conditions
                </label>
                <div style={{ position: "relative" }}>
                  <select value={weather} onChange={e => handleWeather(e.target.value)} style={{
            width: "100%", appearance: "none",
            fontFamily: SANS, fontSize: 13, color: inkB,
            background: "#F8FAFC", border: `1px solid ${bd}`, borderRadius: 10,
            padding: "9px 34px 9px 12px", outline: "none", cursor: "pointer",
        }}>
                    <option>Clear</option>
                    <option>Wet Rails</option>
                    <option>Heavy Monsoon</option>
                  </select>
                  <ChevronDown size={13} style={{ position: "absolute", right: 10, top: "50%", transform: "translateY(-50%)", color: inkM, pointerEvents: "none" }}/>
                </div>
              </div>

              {/* Surge slider */}
              <div style={{ marginBottom: 18 }}>
                <div className="flex items-center justify-between" style={{ marginBottom: 8 }}>
                  <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB }}>
                    Passenger Surge Multiplier
                  </label>
                  <span style={{ fontFamily: MONO, fontSize: 13, fontWeight: 700, color: teal, flexShrink: 0 }}>
                    {surge.toFixed(1)}x
                  </span>
                </div>
                <input type="range" min={1.0} max={4.0} step={0.5} value={surge} onChange={e => handleSurge(parseFloat(e.target.value))} style={{ width: "100%", accentColor: teal, cursor: "pointer" }}/>
                <div className="flex justify-between" style={{ marginTop: 4 }}>
                  <span style={{ fontFamily: SANS, fontSize: 10, color: inkM }}>1.0x</span>
                  <span style={{ fontFamily: SANS, fontSize: 10, color: inkM }}>4.0x</span>
                </div>
              </div>

              {/* Incident toggle */}
              <div style={{ borderTop: `1px solid ${bd}`, paddingTop: 16 }}>
                <div className="flex items-center justify-between">
                  <div>
                    <p style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB, marginBottom: 3 }}>
                      Corridor Incidents
                    </p>
                    <p style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>MG Road Spacing Drift</p>
                  </div>
                  <button role="switch" aria-checked={incident} onClick={() => handleIncident(!incident)} style={{
            width: 42, height: 23, borderRadius: 12,
            background: incident ? teal : "#CBD5E1",
            border: "none", cursor: "pointer", position: "relative",
            flexShrink: 0, transition: "background 0.2s",
        }}>
                    <span style={{
            position: "absolute", top: 3, width: 17, height: 17, borderRadius: "50%",
            background: "#fff", boxShadow: "0 1px 3px rgba(0,0,0,0.2)",
            left: incident ? 22 : 3, transition: "left 0.2s",
        }}/>
                  </button>
                </div>
              </div>
            </div>

            {/* ── Right column ── */}
            <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 14 }}>

              {/* Metric cards */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 12 }}>
                {metricCards.map(m => (<div key={m.label} style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, overflow: "hidden" }}>
                    <div style={{ height: 4, background: `linear-gradient(90deg, ${teal}, ${tealDk})` }}/>
                    <div style={{ padding: "16px 18px 18px" }}>
                      <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em", fontWeight: 600, fontSize: 10 }}>
                        {m.label}
                      </p>
                      <p style={{ fontFamily: DISPLAY, fontSize: 30, fontWeight: 800, color: inkH, lineHeight: 1, marginBottom: 6, transition: "all 0.3s" }}>
                        {m.value}
                      </p>
                      <p style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>{m.sub}</p>
                    </div>
                  </div>))}
              </div>

              {/* Track map */}
              <TrackMap fleet={sim.fleet} incident={incident}/>
            </div>
          </div>
        </div>

        {/* ── Full-width Timetable Deviation Chart ── */}
        <div className="max-w-7xl mx-auto px-6 pb-12">
          <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden" }}>

            {/* Header row */}
            <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: `1px solid ${bd}` }}>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH }}>
                Dynamic Timetable Deviation Chart
              </h2>
              <div className="flex items-center gap-5">
                <div className="flex items-center gap-2">
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: "#CBD5E1", display: "inline-block" }}/>
                  <span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>Static Baseline</span>
                </div>
                <div className="flex items-center gap-2">
                  <span style={{ width: 8, height: 8, borderRadius: "50%", background: teal, display: "inline-block" }}/>
                  <span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>AI Responsive Schedule</span>
                </div>
              </div>
            </div>

            {/* Body: bars left, metrics right */}
            <div style={{ display: "flex", gap: 32, padding: "20px 24px 22px", alignItems: "stretch" }}>

              {/* Left: time-slot bars */}
              <TimetableSection aiSlots={sim.aiSlots} barMax={sim.barMax}/>

              {/* Vertical divider */}
              <div style={{ width: 1, background: bd, flexShrink: 0 }}/>

              {/* Right: AI optimisation metrics 2×2 */}
              <div style={{ width: 320, flexShrink: 0 }}>
                <p style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: inkM, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 12 }}>
                  AI Optimisation Metrics
                </p>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  {optMetrics.map(m => (<div key={m.label} style={{ background: "#F8FAFC", border: `1px solid ${bd}`, borderRadius: 11, padding: "14px 14px 12px" }}>
                      <p style={{ fontFamily: SANS, fontSize: 10, color: inkM, marginBottom: 8, lineHeight: 1.3 }}>{m.label}</p>
                      <p style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: m.color, transition: "all 0.3s", lineHeight: 1 }}>
                        {m.value}
                      </p>
                    </div>))}
                </div>
                <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, marginTop: 12, lineHeight: 1.55 }}>
                  AI model recalculates dispatch intervals live across all 4 corridor time-slots using CBTC sensor data and historical surge patterns.
                </p>
              </div>

            </div>
          </div>
        </div>

      </main>

      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}
