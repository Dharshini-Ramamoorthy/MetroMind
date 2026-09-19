import { Train, Calendar, Bell, BarChart2, Zap, Shield, Monitor } from "lucide-react";
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
export const DEFAULT_ABOUT_DATA = {
    userName: "Priya",
    userRole: "Operator",
    userStatus: "online",
    buildVersion: "v2.4.1 Build-Stable",
    trackArchitecture: "CBTC (Moving Block System)",
    interlockingProtocol: "SIL-4 Automated Safety Relay",
    relayNodes: { active: 14, total: 14 },
    radioLinkSampling: "250ms Telemetry Frame Tick",
    databaseStorage: "Distributed Ledger Topology",
    systemLinks: [],
    copyrightYear: new Date().getFullYear(),
};
// ─── Sub-components ───────────────────────────────────────────────────────────
function Eyebrow({ children }) {
    return (<p style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 10 }}>
      {children}
    </p>);
}
function ModuleCard({ icon, title, description, onClick }) {
    return (<div onClick={onClick} style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "20px 20px 18px", cursor: onClick ? "pointer" : "default", transition: "box-shadow 0.15s" }} className="hover:shadow-md">
      <div style={{ width: 36, height: 36, borderRadius: 10, background: "rgba(0,150,136,0.08)", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 12 }}>
        {icon}
      </div>
      <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH, marginBottom: 6 }}>{title}</p>
      <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, lineHeight: 1.6 }}>{description}</p>
    </div>);
}
function StepRow({ num, title, description, last }) {
    return (<div className="flex gap-4" style={{ paddingBottom: last ? 0 : 20 }}>
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", flexShrink: 0 }}>
        <div style={{ width: 32, height: 32, borderRadius: "50%", background: `linear-gradient(135deg, ${teal}, ${tealDk})`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
          <span style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 800, color: "#fff" }}>{num}</span>
        </div>
        {!last && <div style={{ width: 1, flex: 1, background: "rgba(0,150,136,0.15)", marginTop: 6 }}/>}
      </div>
      <div style={{ paddingBottom: last ? 0 : 4 }}>
        <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH, marginBottom: 4 }}>{title}</p>
        <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, lineHeight: 1.6 }}>{description}</p>
      </div>
    </div>);
}
function PrincipleCard({ icon, title, description }) {
    return (<div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "22px 22px 20px", flex: 1 }}>
      <div style={{ width: 38, height: 38, borderRadius: 11, background: "rgba(0,150,136,0.08)", display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 14 }}>
        {icon}
      </div>
      <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH, marginBottom: 8 }}>{title}</p>
      <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, lineHeight: 1.65 }}>{description}</p>
    </div>);
}
// ─── AboutPage ────────────────────────────────────────────────────────────────
export default function AboutPage({ isSignedIn, data = DEFAULT_ABOUT_DATA, onNavigate, onLogOut, userName, userRole, }) {
    const version = (data.buildVersion ?? DEFAULT_ABOUT_DATA.buildVersion).split(" ")[0];
    const stats = [
        { value: "25", label: "Trainsets Managed" },
        { value: "24/7", label: "Control Room Coverage" },
        { value: "< 5 min", label: "Critical Escalation Target" },
        { value: version, label: "Current Platform Version", mono: true },
    ];
    const modules = [
        {
            icon: <Train size={17} color={teal}/>,
            title: "Fleet & Induction",
            description: "Manage rake readiness checks, yard clearance handshakes, and mainline deployment directly from the OCC workspace.",
            key: "fleet",
        },
        {
            icon: <Calendar size={17} color={teal}/>,
            title: "Schedule",
            description: "Visualise and adjust headway intervals across all active corridors, with live conflict detection and operator-gated overrides.",
            key: "schedule",
        },
        {
            icon: <Bell size={17} color={teal}/>,
            title: "Alerts",
            description: "Triage real-time alerts by severity, run AI-assisted diagnostics, and dispatch resolution actions from a single panel.",
            key: "alerts",
        },
        {
            icon: <BarChart2 size={17} color={teal}/>,
            title: "Reports",
            description: "Review performance metrics, headway fluctuations, and fleet allocation summaries across live and historical periods.",
            key: "reports",
        },
    ];
    const steps = [
        {
            title: "Plan the day's induction",
            description: "Operators review trainset readiness and confirm yard clearance before service begins.",
        },
        {
            title: "Monitor the live schedule",
            description: "The console tracks headway deviations in real time, flagging any spacing drift automatically.",
        },
        {
            title: "Respond to alerts",
            description: "The alerts module surfaces critical events by severity, with the agent running root-cause analysis.",
        },
        {
            title: "Escalate when needed",
            description: "Gated action buttons ensure only signed-in operators can initiate a field escalation or safety hold.",
        },
        {
            title: "Close out and report",
            description: "Every action is logged to the audit ledger and surfaced in the Reports module for end-of-shift review.",
        },
    ];
    const principles = [
        {
            icon: <Zap size={18} color={teal}/>,
            title: "Speed when it matters",
            description: "Critical information and action buttons are always one click away. Time pressure is a feature of the domain, not an obstacle — the interface is designed around it.",
        },
        {
            icon: <Shield size={18} color={teal}/>,
            title: "Full auditability",
            description: "Every operator action, diagnostic decision, and schedule adjustment is written to the immutable ledger. Nothing happens off-record.",
        },
        {
            icon: <Monitor size={18} color={teal}/>,
            title: "Built for the control room",
            description: "MetroMind is designed for sustained use under real operational pressure — not as a demo tool, but as the front line of KMRL network control.",
        },
    ];
    return (<div className="min-h-screen flex flex-col" style={{ background: "#F8FAFC" }}>
      <SharedHeader activePage="about" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      <main className="flex-1">

        {/* ── Title strip ── */}
        <div className="max-w-7xl mx-auto px-6 pt-10 pb-2">
          <div className="flex items-start justify-between gap-6 flex-wrap mb-10">
            <div>
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH, marginBottom: 10, lineHeight: 1.2 }}>
                About MetroMind KMRL
              </h1>
              <p style={{ fontFamily: SANS, fontSize: 14, color: inkM, maxWidth: 560, lineHeight: 1.65 }}>
                A unified console for fleet induction planning, scheduling, and live operations support across the Kochi Metro rail network.
              </p>
            </div>
          </div>
        </div>

        {/* ── Our Mission ── */}
        <div className="max-w-7xl mx-auto px-6 mb-8">
          <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 18, overflow: "hidden" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 0 }}>
              {/* Left: mission copy */}
              <div style={{ padding: "28px 32px", borderRight: `1px solid ${bd}` }}>
                <Eyebrow>Our Mission</Eyebrow>
                <h2 style={{ fontFamily: DISPLAY, fontSize: 22, fontWeight: 800, color: inkH, lineHeight: 1.3, marginBottom: 14 }}>
                  Making daily fleet decisions faster, cleaner, and more reliable
                </h2>
                <p style={{ fontFamily: SANS, fontSize: 13, color: inkB, lineHeight: 1.7 }}>
                  MetroMind brings fleet induction, scheduling, and incident response into a single operational view. Instead of switching between spreadsheets, radios, and paper logs, operators and control room staff can stay focused on the real state of the network at any given moment.
                </p>
                <p style={{ fontFamily: SANS, fontSize: 13, color: inkB, lineHeight: 1.7, marginTop: 10 }}>
                  The platform embodies three principles: give operators clear intent only, let them act without switching tools, and make the right action only one click away when something needs escalation.
                </p>
              </div>

              {/* Right: 2×2 stats */}
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 0, width: 380, flexShrink: 0 }}>
                {stats.map((s, i) => (<div key={s.label} style={{
                padding: "22px 20px",
                borderBottom: i < 2 ? `1px solid ${bd}` : "none",
                borderLeft: i % 2 === 1 ? `1px solid ${bd}` : "none",
            }}>
                    <p style={{
                fontFamily: s.mono ? MONO : DISPLAY,
                fontSize: s.value.length > 5 ? 18 : 24,
                fontWeight: 800,
                color: teal,
                lineHeight: 1,
                marginBottom: 6,
            }}>
                      {s.value}
                    </p>
                    <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, lineHeight: 1.4 }}>{s.label}</p>
                  </div>))}
              </div>
            </div>
          </div>
        </div>

        {/* ── Four core modules ── */}
        <div className="max-w-7xl mx-auto px-6 mb-8">
          <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 18, padding: "28px 32px" }}>
            <Eyebrow>About the Platform Design</Eyebrow>
            <h2 style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: inkH, marginBottom: 6 }}>
              Four core modules, one console
            </h2>
            <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, marginBottom: 22, lineHeight: 1.6 }}>
              Each module targets a distinct part of the operations loop — from pre-service induction to end-of-shift reporting — while sharing the same navigation shell, auth state, and audit trail.
            </p>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14 }}>
              {modules.map(m => (<ModuleCard key={m.key} icon={m.icon} title={m.title} description={m.description} onClick={() => onNavigate(m.key)}/>))}
            </div>
          </div>
        </div>

        {/* ── How It Works ── */}
        <div className="max-w-7xl mx-auto px-6 mb-8">
          <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 18, padding: "28px 32px" }}>
            <Eyebrow>How It Works</Eyebrow>
            <h2 style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: inkH, marginBottom: 22 }}>
              From daily planning to incident close-out
            </h2>
            <div style={{ maxWidth: 640 }}>
              {steps.map((s, i) => (<StepRow key={s.title} num={i + 1} title={s.title} description={s.description} last={i === steps.length - 1}/>))}
            </div>
          </div>
        </div>

        {/* ── Principles ── */}
        <div className="max-w-7xl mx-auto px-6 pb-12">
          <Eyebrow>Why It&apos;s Built This Way</Eyebrow>
          <h2 style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: inkH, marginBottom: 18 }}>
            Principles behind the platform
          </h2>
          <div className="flex gap-5">
            {principles.map(p => (<PrincipleCard key={p.title} icon={p.icon} title={p.title} description={p.description}/>))}
          </div>
        </div>
      </main>

      <SharedFooter activePage="about" onNavigate={onNavigate}/>
    </div>);
}
