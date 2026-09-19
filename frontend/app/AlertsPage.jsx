import { useState } from "react";
import { LogOut, AlertTriangle, Shield, Activity, Clock, Check, CheckCircle, Lock, Zap, Wrench, Scan, ChevronRight, Download, } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
// ─── Tokens ──────────────────────────────────────────────────────────────────
const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const border = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
const SEV = {
    sev3: { text: "#DC2626", bg: "#FEF2F2", bd: "rgba(220,38,38,0.22)", label: "SEVERITY LEVEL-3 · CRITICAL" },
    sev2: { text: "#D97706", bg: "#FFFBEB", bd: "rgba(217,119,6,0.22)", label: "SEVERITY LEVEL-2 · WARNING" },
    sev1: { text: "#64748B", bg: "#F1F5F9", bd: "rgba(100,116,139,0.18)", label: "SEVERITY LEVEL-1 · INFO" },
};
const INITIAL_ALERTS = [
    {
        id: "INC-2291", time: "14:32", sev: "sev3",
        title: "Traction power breaker trip — Sub-station 04", subtitle: "Aluva depot feeder yard",
        asset: "Sub-station 04 Breaker (Feeder Bay 2)",
        fields: [{ label: "Location", value: "Aluva Depot" }, { label: "Detected", value: "14:32:08" }, { label: "Signal Loss", value: "412 ms" }, { label: "Affected Sets", value: "Set Alpha, Set Bravo" }],
        predictive: "AI pipeline predicts a 14-minute cumulative network delay within the next two headway cycles if the Aluva bottleneck is not isolated within 6 minutes.",
        sparkline: [22, 24, 23, 41, 78, 95, 88, 91],
    },
    {
        id: "INC-2290", time: "14:11", sev: "sev2",
        title: "Platform screen door wireless link variance", subtitle: "Edapally station, Platform 2",
        asset: "Edapally Platform Screen Door Wireless Link",
        fields: [{ label: "Location", value: "Edapally Station" }, { label: "Detected", value: "14:11:52" }, { label: "Packet Loss", value: "6.3%" }, { label: "Affected Sets", value: "None (station-side)" }],
        predictive: "If unresolved, dwell time at Edapally may extend by 20–35 seconds per stop during peak headway, with low risk of cascading delay.",
        sparkline: [10, 12, 14, 19, 26, 24, 29, 31],
    },
    {
        id: "INC-2289", time: "13:58", sev: "sev2",
        title: "Set Bravo headway overlap — Kaloor corridor", subtitle: "Kaloor junction approach",
        asset: "CBTC Headway Controller — Kaloor Segment",
        fields: [{ label: "Location", value: "Kaloor Corridor" }, { label: "Detected", value: "13:58:04" }, { label: "Headway Delta", value: "-18 sec" }, { label: "Affected Sets", value: "Set Bravo, Set Charlie" }],
        predictive: "Continued overlap risks an automatic speed restriction on the Kaloor approach, adding an estimated 3–5 minutes to the affected run.",
        sparkline: [30, 28, 33, 40, 44, 42, 47, 50],
    },
    {
        id: "INC-2288", time: "13:40", sev: "sev1",
        title: "Telemetry lag on OCC dashboard refresh", subtitle: "Operations Control Centre",
        asset: "OCC Telemetry Aggregator — Node 3",
        fields: [{ label: "Location", value: "OCC Core" }, { label: "Detected", value: "13:40:15" }, { label: "Refresh Lag", value: "2.1 sec" }, { label: "Affected Sets", value: "None" }],
        predictive: "No operational impact expected. Dashboard refresh lag is within tolerance; monitoring for recurrence.",
        sparkline: [4, 5, 4, 6, 7, 5, 6, 6],
    },
    {
        id: "INC-2287", time: "13:22", sev: "sev1",
        title: "Software version skew — onboard diagnostics", subtitle: "Set Delta, Car 2",
        asset: "Onboard Diagnostics Unit — Set Delta / Car 2",
        fields: [{ label: "Location", value: "Set Delta (in service)" }, { label: "Detected", value: "13:22:47" }, { label: "Version Skew", value: "1 minor rev" }, { label: "Affected Sets", value: "Set Delta" }],
        predictive: "Minor version skew logged for audit only. No functional degradation expected before next scheduled induction.",
        sparkline: [3, 3, 4, 3, 4, 4, 3, 4],
    },
    {
        id: "INC-2286", time: "12:57", sev: "sev2",
        title: "Wet-rail adhesion advisory — yard bay tracks", subtitle: "Muttom yard, Bay 5–7",
        asset: "Yard Bay Track Sensors 5–7",
        fields: [{ label: "Location", value: "Muttom Yard" }, { label: "Detected", value: "12:57:31" }, { label: "Surface Moisture", value: "High" }, { label: "Affected Sets", value: "Yard shunting only" }],
        predictive: "Elevated adhesion risk during shunting moves. Recommend reduced yard speed until surface moisture drops below advisory threshold.",
        sparkline: [15, 20, 34, 38, 36, 40, 37, 39],
    },
];
const INITIAL_LEDGER = [
    { id: "INC-2251", ts: "2026-07-05 22:14:44", sev: "sev3", operator: "Arun K.", opId: "OP-0987", hash: "9f3a2c…e701b4" },
    { id: "INC-2247", ts: "2026-07-05 18:02:21", sev: "sev2", operator: "Ravi", opId: "OP-1042", hash: "44d1b7…aa9f02" },
    { id: "INC-2239", ts: "2026-07-05 09:41:58", sev: "sev1", operator: "Sana R.", opId: "OP-1108", hash: "c02e91…5b31de" },
    { id: "INC-2231", ts: "2026-07-04 20:37:19", sev: "sev2", operator: "Anjali", opId: "AD-0031", hash: "7ba045…12c9f0" },
];
// ─── Helpers ─────────────────────────────────────────────────────────────────
function empIdPrefix(role) {
    if (role === "Admin")
        return "AD";
    if (role === "MaintenanceManager")
        return "MM";
    return "OP";
}
function initials(name) {
    return name.replace(/\(.*?\)/g, "").trim().split(/\s+/).map(w => w[0] ?? "").join("").toUpperCase().slice(0, 2);
}
function randomHash() {
    const h = () => Math.floor(Math.random() * 16).toString(16);
    const p = (n) => Array.from({ length: n }, h).join("");
    return `${p(6)}…${p(6)}`;
}
function nowTs() {
    const d = new Date(), z = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())} ${z(d.getHours())}:${z(d.getMinutes())}:${z(d.getSeconds())}`;
}
function sparkPath(data, W, H) {
    const min = Math.min(...data), max = Math.max(...data), range = max - min || 1, n = data.length;
    const pts = data.map((v, i) => ({ x: (i / (n - 1)) * W, y: H - 4 - ((v - min) / range) * (H - 8) }));
    let d = `M ${pts[0].x},${pts[0].y}`;
    for (let i = 1; i < pts.length; i++) {
        const cp = (pts[i - 1].x + pts[i].x) / 2;
        d += ` C ${cp},${pts[i - 1].y} ${cp},${pts[i].y} ${pts[i].x},${pts[i].y}`;
    }
    return d;
}
// ─── Severity badge ───────────────────────────────────────────────────────────
function SevBadge({ sev }) {
    const s = SEV[sev];
    return (<span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-bold tracking-wide whitespace-nowrap" style={{ fontFamily: SANS, color: s.text, background: s.bg, border: `1px solid ${s.bd}` }}>
      {s.label}
    </span>);
}
// ─── Alerts Nav ───────────────────────────────────────────────────────────────
function AlertsNav({ isSignedIn, onNavigate, onLogOut, userName, userRole }) {
    const links = [
        { label: "Dashboard", key: "dashboard" },
        { label: "Fleet & Induction", key: "fleet" },
        { label: "Schedule", key: "schedule" },
        { label: "Alerts", key: "alerts" },
        { label: "Reports", key: "reports" },
    ];
    return (<header className="sticky top-0 z-50 w-full" style={{ background: "rgba(248,250,252,0.92)", backdropFilter: "blur(16px)", WebkitBackdropFilter: "blur(16px)", borderBottom: `1px solid ${border}` }}>
      <div className="max-w-7xl mx-auto px-6 h-16 flex items-center gap-6">
        <button onClick={() => onNavigate("dashboard")} className="flex items-center gap-2.5 shrink-0 focus:outline-none">
          <div className="flex items-center justify-center" style={{ width: 28, height: 28, borderRadius: 7, background: `linear-gradient(135deg, ${teal}, ${tealDk})` }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="M3 4L7 7L3 10" stroke={emerald} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M7 4L11 7L7 10" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="font-extrabold text-[15px] tracking-tight" style={{ fontFamily: DISPLAY, color: inkH }}>
            MetroMind <span style={{ color: teal }}>KMRL</span>
          </span>
        </button>

        <nav className="hidden md:flex items-center gap-1 flex-1">
          {links.map(link => {
            const active = link.key === "alerts";
            return (<button key={link.key} onClick={() => onNavigate(link.key)} className="px-3.5 py-2 rounded-lg text-sm transition-colors hover:bg-slate-100" style={{ fontFamily: SANS, color: active ? teal : inkB, fontWeight: active ? 700 : 500,
                    background: active ? "rgba(0,150,136,0.07)" : "transparent",
                    textDecoration: active ? "underline" : "none", textUnderlineOffset: 3 }}>
                {link.label}
              </button>);
        })}
        </nav>

        <div className="flex items-center gap-2 ml-auto shrink-0">
          {isSignedIn ? (<>
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-semibold" style={{ fontFamily: SANS, background: "rgba(16,185,129,0.10)", color: inkH, border: "1px solid rgba(16,185,129,0.22)" }}>
                <span className="w-2 h-2 rounded-full" style={{ background: emerald, boxShadow: "0 0 0 2px rgba(16,185,129,0.25)" }}/>
                <span>{userName ?? "User"}</span><span className="hidden sm:inline"> · {userRole === "Admin" ? "Admin" : userRole === "MaintenanceManager" ? "Maintenance" : "Operator"}</span>
              </div>
              <button onClick={onLogOut} className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium border transition-colors hover:bg-slate-50" style={{ fontFamily: SANS, color: inkM, borderColor: border, background: "#fff" }}>
                <LogOut size={14}/> Log Out
              </button>
            </>) : (<button onClick={() => onNavigate("signin")} className="px-4 py-2 rounded-xl text-sm font-semibold text-white" style={{ fontFamily: DISPLAY, background: `linear-gradient(135deg, ${teal}, ${tealDk})`, borderRadius: 10 }}>
              Sign In
            </button>)}
        </div>
      </div>
    </header>);
}
// ─── Alerts Footer ────────────────────────────────────────────────────────────
function AlertsFooter({ onNavigate }) {
    return (<footer style={{ background: "#0B111E", borderTop: "1px solid #1E293B" }}>
      <div className="max-w-7xl mx-auto px-6 py-5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center shrink-0" style={{ width: 44, height: 44, borderRadius: 13, background: `linear-gradient(135deg, ${teal}, ${tealDk})` }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
              <path d="M4 5.5L9 9L4 12.5" stroke={emerald} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M9 5.5L14 9L9 12.5" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <div>
            <p style={{ fontFamily: DISPLAY, fontWeight: 700, fontSize: 14, color: "#fff", lineHeight: 1.3 }}>
              MetroMind <span style={{ color: "#4FD1C5" }}>KMRL</span>
            </p>
            <p style={{ fontFamily: SANS, fontSize: 12, color: "#64748B", marginTop: 2 }}>Fleet induction &amp; scheduling console</p>
          </div>
        </div>
        <div className="flex items-center gap-6">
          {["About", "Contact Systems"].map(l => (<button key={l} onClick={l === "About" ? () => onNavigate?.("about") : l === "Contact Systems" ? () => onNavigate?.("contact") : undefined} className="text-sm font-medium hover:text-[#4FD1C5] transition-colors" style={{ fontFamily: SANS, color: "#94A3B8" }}>{l}</button>))}
        </div>
        <div className="flex items-center gap-3">
          <p style={{ fontFamily: SANS, fontSize: 12, color: "#94A3B8" }}>© 2026 KMRL Internal Systems</p>
          <span style={{ background: "#131E31", color: teal, fontFamily: SANS, fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 100 }}>v2.4.1</span>
        </div>
      </div>
    </footer>);
}
// ─── KPI Card ────────────────────────────────────────────────────────────────
function KPICard({ icon, iconBg, title, value, valueColor, subtext, bar }) {
    return (<div className="rounded-2xl p-5 flex flex-col gap-2.5" style={{ background: "#fff", border: `1px solid ${border}`, borderRadius: 16, boxShadow: "0 1px 3px rgba(0,0,0,0.04)" }}>
      <div className="w-9 h-9 rounded-xl flex items-center justify-center" style={{ background: iconBg }}>{icon}</div>
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>{title}</p>
        <p className="font-extrabold leading-none" style={{ fontFamily: DISPLAY, color: valueColor || inkH, fontSize: 28 }}>{value}</p>
      </div>
      {bar !== undefined && (<div className="w-full h-1.5 rounded-full overflow-hidden" style={{ background: "#F1F5F9" }}>
          <div className="h-full rounded-full" style={{ width: `${bar}%`, background: `linear-gradient(90deg, ${emerald}, ${teal})` }}/>
        </div>)}
      <p className="text-xs leading-relaxed" style={{ fontFamily: SANS, color: inkM }}>{subtext}</p>
    </div>);
}
// ─── Sparkline ───────────────────────────────────────────────────────────────
function Sparkline({ data }) {
    const W = 220, H = 48;
    const path = sparkPath(data, W, H);
    return (<svg width={W} height={H} viewBox={`0 0 ${W} ${H}`} aria-hidden="true">
      <path d={path} fill="none" stroke={teal} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>);
}
// ─── Diagnostic Panel ────────────────────────────────────────────────────────
function DiagPanel({ alert, isSignedIn, userName, userRole, onClear, onIsolate, onDispatch }) {
    if (!alert) {
        return (<div className="flex flex-col items-center justify-center h-full gap-4 px-8 text-center">
        <div className="w-12 h-12 rounded-2xl flex items-center justify-center" style={{ background: "#F1F5F9" }}>
          <Scan size={22} color={inkM}/>
        </div>
        <p className="text-sm leading-relaxed max-w-[260px]" style={{ fontFamily: SANS, color: inkM }}>
          Select an alert from the triage feed to inspect fault detail, predictive impact, and resolution controls.
        </p>
      </div>);
    }
    const s = SEV[alert.sev];
    function ActionBtn({ icon, iconBg, label, sub, btnBg, btnBd, labelColor, onClick }) {
        const disabled = !isSignedIn;
        return (<button onClick={disabled ? undefined : onClick} className={`w-full flex items-center gap-3 p-3 rounded-xl text-left transition-all ${!disabled ? "hover:shadow-md hover:-translate-y-px" : ""}`} style={{ background: btnBg, border: `1px solid ${btnBd}`, borderRadius: 12, cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.45 : 1, filter: disabled ? "grayscale(0.5)" : "none" }}>
        <div className="w-8 h-8 rounded-lg flex items-center justify-center shrink-0" style={{ background: iconBg }}>{icon}</div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: labelColor }}>{label}</p>
          <p className="text-xs mt-0.5" style={{ fontFamily: SANS, color: inkM }}>{sub}</p>
        </div>
        {disabled && <Lock size={13} style={{ color: inkM, flexShrink: 0 }}/>}
      </button>);
    }
    return (<div className="flex flex-col gap-4 p-5 overflow-y-auto" style={{ height: "100%" }}>
      {/* Header */}
      <div className="flex items-start justify-between gap-3">
        <div>
          <SevBadge sev={alert.sev}/>
          <h3 className="font-extrabold text-[15px] leading-tight mt-2" style={{ fontFamily: DISPLAY, color: inkH }}>{alert.asset}</h3>
        </div>
        <span className="text-xs font-semibold shrink-0 mt-0.5" style={{ fontFamily: MONO, color: inkM }}>{alert.id}</span>
      </div>

      {/* Field grid */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        {alert.fields.map(f => (<div key={f.label} className="rounded-xl p-3" style={{ background: "#F8FAFC", border: `1px solid ${border}`, borderRadius: 10 }}>
            <p className="text-[9px] font-bold uppercase tracking-widest mb-1" style={{ fontFamily: SANS, color: inkM }}>{f.label}</p>
            <p className="text-sm font-bold" style={{ fontFamily: MONO, color: inkH }}>{f.value}</p>
          </div>))}
      </div>

      {/* Predictive callout */}
      <div className="rounded-xl p-3.5" style={{ background: s.bg, border: `1px solid ${s.bd}`, borderRadius: 12 }}>
        <div className="flex items-center gap-2 mb-1.5">
          <Clock size={12} style={{ color: s.text }}/>
          <p className="text-[9px] font-bold uppercase tracking-widest" style={{ fontFamily: SANS, color: s.text }}>DRL AI Predictive Consequence</p>
        </div>
        <p className="text-xs leading-relaxed" style={{ fontFamily: SANS, color: s.text }}>{alert.predictive}</p>
      </div>

      {/* Telemetry Snapshot */}
      <div className="rounded-xl overflow-hidden" style={{ border: `1px solid ${border}`, borderRadius: 12 }}>
        <div className="px-4 py-2.5" style={{ borderBottom: `1px solid ${border}` }}>
          <p className="text-[9px] font-bold uppercase tracking-widest" style={{ fontFamily: SANS, color: inkM }}>Telemetry Snapshot</p>
        </div>
        <div className="px-4 pt-3 pb-2">
          <Sparkline data={alert.sparkline}/>
          <div className="flex items-center justify-between mt-1.5">
            <span className="text-[10px]" style={{ fontFamily: MONO, color: inkM }}>T‑40s</span>
            <span className="text-[10px]" style={{ fontFamily: MONO, color: inkM }}>Metric spike at detection</span>
            <span className="text-[10px]" style={{ fontFamily: MONO, color: inkM }}>T+0s</span>
          </div>
        </div>
      </div>

      {/* Resolution Controls */}
      <div>
        <div className="flex items-center gap-1.5 mb-2.5">
          <Lock size={11} color={inkM}/>
          <p className="text-[9px] font-bold uppercase tracking-widest" style={{ fontFamily: SANS, color: inkM }}>Incident Resolution Controls</p>
        </div>
        <div className="flex flex-col gap-2">
          <ActionBtn icon={<CheckCircle size={15} color="#059669"/>} iconBg="rgba(16,185,129,0.12)" label="Acknowledge and Clear Alert" sub="Removes this incident from active memory" btnBg="rgba(16,185,129,0.06)" btnBd="rgba(16,185,129,0.22)" labelColor="#065F46" onClick={onClear}/>
          <ActionBtn icon={<Zap size={15} color="#B45309"/>} iconBg="rgba(245,158,11,0.12)" label="Isolate Sub-System Node" sub="Cuts the affected component from the live network" btnBg="rgba(245,158,11,0.06)" btnBd="rgba(245,158,11,0.22)" labelColor="#92400E" onClick={onIsolate}/>
          <ActionBtn icon={<Wrench size={15} color="#1D4ED8"/>} iconBg="rgba(59,130,246,0.12)" label="Deploy Field Maintenance Dispatch" sub="Logs the fault and opens a maintenance work order" btnBg="rgba(59,130,246,0.06)" btnBd="rgba(59,130,246,0.22)" labelColor="#1E3A8A" onClick={onDispatch}/>
        </div>
      </div>

      {/* Gate note */}
      {isSignedIn ? (<div className="flex items-center gap-2.5 p-3 rounded-xl" style={{ background: "rgba(16,185,129,0.07)", border: "1px solid rgba(16,185,129,0.2)", borderRadius: 10 }}>
          <CheckCircle size={13} style={{ color: emerald, flexShrink: 0 }}/>
          <p className="text-xs" style={{ fontFamily: SANS, color: "#065F46" }}>Signed in as {userName ?? "User"} — actions will be recorded to the audit ledger.</p>
        </div>) : (<div className="flex items-center gap-2.5 p-3 rounded-xl" style={{ background: "#F8FAFC", border: `1px solid ${border}`, borderRadius: 10 }}>
          <Lock size={13} style={{ color: inkM, flexShrink: 0 }}/>
          <p className="text-xs" style={{ fontFamily: SANS, color: inkM }}>Sign in to unlock resolution controls for this alert.</p>
        </div>)}
    </div>);
}
// ─── Toast ───────────────────────────────────────────────────────────────────
function Toast({ msg, visible }) {
    return (<div style={{
            position: "fixed", bottom: 28, left: "50%",
            transform: `translateX(-50%) translateY(${visible ? 0 : 10}px)`,
            opacity: visible ? 1 : 0, transition: "all 0.28s ease",
            background: inkH, color: "#fff", borderRadius: 100,
            padding: "10px 20px", display: "flex", alignItems: "center", gap: 8,
            boxShadow: "0 8px 32px rgba(15,23,42,0.32)", zIndex: 9999,
            fontFamily: SANS, fontSize: 13, fontWeight: 500, whiteSpace: "nowrap",
            pointerEvents: visible ? "auto" : "none",
        }}>
      <Check size={14} style={{ color: emerald }}/>
      {msg}
    </div>);
}
// ─── Operator avatar ─────────────────────────────────────────────────────────
function Avatar({ name }) {
    return (<div className="w-7 h-7 rounded-full flex items-center justify-center shrink-0 text-[10px] font-bold text-white" style={{ background: `linear-gradient(135deg, ${teal}, ${emerald})` }}>
      {initials(name)}
    </div>);
}
// ─── AlertsPage (default export) ─────────────────────────────────────────────
export default function AlertsPage({ isSignedIn, onNavigate, onLogOut, userName, userRole }) {
    const [alerts, setAlerts] = useState(INITIAL_ALERTS);
    const [selectedId, setSelectedId] = useState(null);
    const [ledger, setLedger] = useState(INITIAL_LEDGER);
    const [toastMsg, setToastMsg] = useState("");
    const [toastVisible, setToastVisible] = useState(false);
    const selected = alerts.find(a => a.id === selectedId) ?? null;
    function showToast(msg) {
        setToastMsg(msg);
        setToastVisible(true);
        setTimeout(() => setToastVisible(false), 2600);
    }
    function handleClear() {
        if (!selected)
            return;
        const prefix = empIdPrefix(userRole);
        const row = {
            id: selected.id, ts: nowTs(), sev: selected.sev,
            operator: userName ?? "User", opId: `${prefix}-${Math.floor(1000 + Math.random() * 9000)}`, hash: randomHash(),
        };
        setLedger(prev => [row, ...prev]);
        setAlerts(prev => prev.filter(a => a.id !== selected.id));
        setSelectedId(null);
        showToast(`${selected.id} cleared and logged to the audit ledger.`);
    }
    function handleIsolate() {
        if (!selected)
            return;
        showToast(`${selected.asset} isolated from the live network.`);
    }
    function handleDispatch() {
        if (!selected)
            return;
        showToast(`Maintenance work order generated for ${selected.id}.`);
    }
    const critCount = alerts.filter(a => a.sev === "sev3").length;
    const warnCount = alerts.filter(a => a.sev === "sev2").length;
    const infoCount = alerts.filter(a => a.sev === "sev1").length;
    const cardSt = { background: "#fff", border: `1px solid ${border}`, borderRadius: 16 };
    return (<div className="min-h-screen flex flex-col" style={{ background: "#F8FAFC" }}>
      <SharedHeader activePage="alerts" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8">

        {/* Page strip */}
        <div className="flex flex-wrap items-start justify-between gap-4 mb-7">
          <div>
            <h1 style={{ fontFamily: DISPLAY, fontSize: 28, fontWeight: 800, color: inkH, letterSpacing: "-0.025em", marginBottom: 6 }}>Incident &amp; Alert Console</h1>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Live triage feed for network deviations, with root-cause diagnostics and gated resolution controls for signed-in operators.
            </p>
          </div>
          {/* Posture badge */}
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-full shrink-0" style={{ background: "#FFFBEB", border: "1px solid rgba(180,83,9,0.2)", color: "#B45309" }}>
            <span className="w-2 h-2 rounded-full" style={{ background: "#D97706", animation: "alertPulse 1.8s ease-in-out infinite" }}/>
            <span className="text-sm font-semibold" style={{ fontFamily: SANS }}>Degraded Posture</span>
          </div>
        </div>

        {/* KPI row */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-7">
          <KPICard icon={<AlertTriangle size={17} color="#64748B"/>} iconBg="#F1F5F9" title="Active Deviations" value={String(alerts.length)} subtext="Unresolved, across 3 severity tiers"/>
          <KPICard icon={<AlertTriangle size={17} color="#DC2626"/>} iconBg="#FEF2F2" title="Critical Alarms" value={String(critCount)} valueColor="#DC2626" subtext="Immediate operational disruption risk"/>
          <KPICard icon={<Shield size={17} color="#059669"/>} iconBg="#ECFDF5" title="System Integrity" value="87%" subtext="Overall network health score" bar={87}/>
          <KPICard icon={<Activity size={17} color="#B45309"/>} iconBg="#FFFBEB" title="Network Posture" value="Degraded" valueColor="#B45309" subtext={`${critCount} critical, ${warnCount} warning, ${infoCount} info deviation(s) open`}/>
        </div>

        {/* Workspace — two panels */}
        <div className="mb-9" style={{ display: "grid", gridTemplateColumns: "1fr 1.2fr", gap: 20 }}>

          {/* Panel 1: Triage Feed */}
          <div>
            <p className="text-[9px] font-bold uppercase tracking-widest mb-2" style={{ fontFamily: SANS, color: inkM }}>Triage Feed</p>
            <div className="flex flex-col overflow-hidden" style={{ ...cardSt, height: 660 }}>
              {/* Panel header */}
              <div className="flex items-center justify-between px-5 py-4 shrink-0" style={{ borderBottom: `1px solid ${border}` }}>
                <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>Live Incident Vector Matrix</h2>
                <div className="flex items-center gap-2">
                  <span className="relative flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full opacity-60" style={{ background: teal }}/>
                    <span className="relative inline-flex rounded-full h-2 w-2" style={{ background: teal }}/>
                  </span>
                  <span className="text-[9px] font-bold uppercase tracking-widest" style={{ fontFamily: SANS, color: teal }}>Live</span>
                </div>
              </div>

              {/* Alert list */}
              <div className="flex-1 overflow-y-auto">
                {alerts.length === 0 ? (<div className="flex items-center justify-center h-full px-6">
                    <p className="text-sm text-center" style={{ fontFamily: SANS, color: inkM }}>
                      No active deviations. All incidents have been cleared.
                    </p>
                  </div>) : alerts.map((a, i) => {
            const isSelected = selectedId === a.id;
            const s = SEV[a.sev];
            return (<div key={a.id} onClick={() => setSelectedId(isSelected ? null : a.id)} className="group flex items-start gap-3 px-5 py-4 cursor-pointer transition-all" style={{
                    borderBottom: i < alerts.length - 1 ? `1px solid ${border}` : "none",
                    background: isSelected ? "#F0FDFA" : "transparent",
                    borderLeft: isSelected ? `3px solid ${teal}` : "3px solid transparent",
                }}>
                      <span className="text-[11px] shrink-0 pt-0.5" style={{ fontFamily: MONO, color: inkM, width: 34, lineHeight: 1.4 }}>{a.time}</span>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                          <SevBadge sev={a.sev}/>
                          <span className="text-[11px] font-semibold" style={{ fontFamily: MONO, color: s.text }}>{a.id}</span>
                        </div>
                        <p className="text-sm font-bold leading-tight mb-0.5" style={{ fontFamily: DISPLAY, color: inkH }}>{a.title}</p>
                        <p className="text-xs" style={{ fontFamily: SANS, color: inkM }}>{a.subtitle}</p>
                      </div>
                      <ChevronRight size={15} style={{
                    color: teal, flexShrink: 0, marginTop: 4,
                    opacity: isSelected ? 1 : 0, transition: "opacity 0.15s",
                }} className="group-hover:opacity-100"/>
                    </div>);
        })}
              </div>
            </div>
          </div>

          {/* Panel 2: Root-Cause Analyzer */}
          <div>
            <p className="text-[9px] font-bold uppercase tracking-widest mb-2" style={{ fontFamily: SANS, color: inkM }}>Root-Cause Analyzer</p>
            <div className="flex flex-col overflow-hidden" style={{ ...cardSt, height: 660 }}>
              <div className="px-5 py-4 shrink-0" style={{ borderBottom: `1px solid ${border}` }}>
                <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>Diagnostic &amp; Resolution</h2>
              </div>
              <div className="flex-1 overflow-y-auto">
                <DiagPanel alert={selected} isSignedIn={isSignedIn} userName={userName} userRole={userRole} onClear={handleClear} onIsolate={handleIsolate} onDispatch={handleDispatch}/>
              </div>
            </div>
          </div>
        </div>

        {/* Ledger */}
        <div className="mb-2">
          <div className="flex items-center justify-between mb-4">
            <div>
              <p className="text-[9px] font-bold uppercase tracking-widest mb-1.5" style={{ fontFamily: SANS, color: inkM }}>Compliance Record</p>
              <h2 className="text-xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>Historical resolution &amp; incident archival ledger</h2>
            </div>
            <button className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-colors hover:bg-slate-100" style={{ fontFamily: DISPLAY, color: inkH, border: `1px solid ${border}`, background: "#fff" }}>
              <Download size={14}/> Export Ledger
            </button>
          </div>

          <div style={cardSt} className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full" style={{ borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC", borderBottom: `1px solid ${border}` }}>
                    {["Incident ID", "Resolution Timestamp", "Original Severity", "Resolving Operator", "Validation Hash"].map(col => (<th key={col} className="px-5 py-3 text-left text-[10px] font-bold uppercase tracking-wider whitespace-nowrap" style={{ fontFamily: SANS, color: inkM }}>{col}</th>))}
                  </tr>
                </thead>
                <tbody>
                  {ledger.map((row, i) => (<tr key={`${row.id}-${i}`} style={{ borderBottom: i < ledger.length - 1 ? `1px solid ${border}` : "none" }}>
                      <td className="px-5 py-3.5">
                        <span className="text-sm font-semibold" style={{ fontFamily: MONO, color: inkH }}>{row.id}</span>
                      </td>
                      <td className="px-5 py-3.5">
                        <span className="text-xs" style={{ fontFamily: MONO, color: inkB }}>{row.ts}</span>
                      </td>
                      <td className="px-5 py-3.5"><SevBadge sev={row.sev}/></td>
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <Avatar name={row.operator}/>
                          <div>
                            <p className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkH }}>{row.operator}</p>
                            <p className="text-[10px]" style={{ fontFamily: MONO, color: inkM }}>{row.opId}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-1.5">
                          <Check size={12} style={{ color: emerald, flexShrink: 0 }}/>
                          <span className="text-xs" style={{ fontFamily: MONO, color: inkM }}>{row.hash}</span>
                        </div>
                      </td>
                    </tr>))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>

      <SharedFooter onNavigate={onNavigate}/>
      <Toast msg={toastMsg} visible={toastVisible}/>

      <style>{`
        @keyframes alertPulse { 0%,100%{opacity:1} 50%{opacity:0.35} }
        .group:hover .group-hover\\:opacity-100 { opacity: 1 !important; }
      `}</style>
    </div>);
}
