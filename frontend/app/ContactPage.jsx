import React from "react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { Phone, Mail, Monitor, Shield, Wrench, Cpu, MapPin, Clock, Server } from "lucide-react";
// ─── Tokens ──────────────────────────────────────────────────────────────────
const teal = "#009688";
const emerald = "#10B981";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const canvas = "#F8FAFC";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
// ─── Quick Contact Card ───────────────────────────────────────────────────────
function QuickCard({ icon, iconBg, dept, desc, phone, email, responseTime, responseColor }) {
    return (<div className="rounded-2xl p-5 flex flex-col gap-4 transition-all hover:shadow-md hover:-translate-y-px" style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
      <div className="flex items-start justify-between">
        <div className="w-10 h-10 rounded-xl flex items-center justify-center" style={{ background: iconBg }}>
          {icon}
        </div>
        <span className="text-[10px] font-bold px-2.5 py-1 rounded-full" style={{ background: `${responseColor}15`, color: responseColor, fontFamily: SANS }}>
          {responseTime}
        </span>
      </div>
      <div>
        <h3 className="font-bold text-sm mb-1" style={{ fontFamily: DISPLAY, color: inkH }}>{dept}</h3>
        <p className="text-xs leading-relaxed" style={{ fontFamily: SANS, color: inkM }}>{desc}</p>
      </div>
      <div className="border-t" style={{ borderColor: bd, borderStyle: "dashed" }}/>
      <div className="flex flex-col gap-2">
        <a href={`tel:${phone.replace(/\s/g, "")}`} className="flex items-center gap-2 group">
          <Phone size={13} style={{ color: inkM }}/>
          <span className="text-xs font-semibold group-hover:text-teal-600 transition-colors" style={{ fontFamily: MONO, color: inkH }}>{phone}</span>
        </a>
        <a href={`mailto:${email}`} className="flex items-center gap-2 group">
          <Mail size={13} style={{ color: inkM }}/>
          <span className="text-xs group-hover:text-teal-600 transition-colors" style={{ fontFamily: SANS, color: inkB }}>{email}</span>
        </a>
      </div>
    </div>);
}
// ─── Escalation Row ──────────────────────────────────────────────────────────
function EscRow({ sevLabel, sevColor, sevBg, sevBd, title, desc, responseTarget, method }) {
    return (<div className="flex items-start gap-4 flex-1 px-5 py-4 min-h-0" style={{ borderBottom: `1px solid ${bd}` }}>
      <div className="shrink-0 pt-0.5">
        <span className="inline-flex items-center px-2.5 py-1 rounded-lg text-[10px] font-bold tracking-wide whitespace-nowrap" style={{ fontFamily: SANS, color: sevColor, background: sevBg, border: `1px solid ${sevBd}`, minWidth: 110, justifyContent: "center" }}>
          {sevLabel}
        </span>
      </div>
      <div className="flex-1 min-w-0">
        <p className="font-bold text-sm mb-1" style={{ fontFamily: DISPLAY, color: inkH }}>{title}</p>
        <p className="text-xs leading-relaxed mb-2.5" style={{ fontFamily: SANS, color: inkM }}>{desc}</p>
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full" style={{ background: "rgba(0,150,136,0.08)", color: teal, fontFamily: SANS }}>
            ⏱ {responseTarget}
          </span>
          <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full" style={{ background: "#F1F5F9", color: inkM, fontFamily: SANS }}>
            {method}
          </span>
        </div>
      </div>
    </div>);
}
export default function ContactPage({ isSignedIn, onNavigate, onLogOut, userName, userRole, }) {
    const cards = [
        {
            icon: <Monitor size={18} color="#DC2626"/>, iconBg: "#FEF2F2",
            dept: "Operations Control Centre (OCC)", desc: "Live dispatch queries, headway incidents, and mainline operational escalations.",
            phone: "+91 484 266 0001", email: "occ@kmrl.co.in",
            responseTime: "Immediate 24/7", responseColor: "#DC2626",
        },
        {
            icon: <Cpu size={18} color="#3B82F6"/>, iconBg: "#EFF6FF",
            dept: "IT & Telemetry Support Desk", desc: "Console access, telemetry feed faults, system credentials, and SIL-4 logs.",
            phone: "+91 484 266 0042", email: "it.support@kmrl.co.in",
            responseTime: "30 min SLA", responseColor: "#3B82F6",
        },
        {
            icon: <Shield size={18} color="#D97706"/>, iconBg: "#FFFBEB",
            dept: "Safety & Compliance Directorate", desc: "Safety incident reporting, regulatory queries, and compliance audits.",
            phone: "+91 484 266 0088", email: "safety@kmrl.co.in",
            responseTime: "2 hr SLA", responseColor: "#D97706",
        },
        {
            icon: <Wrench size={18} color="#059669"/>, iconBg: "#ECFDF5",
            dept: "Depot & Trackside Maintenance", desc: "Muttom depot workorders, trackside fault reports, and rolling stock service.",
            phone: "+91 484 266 0110", email: "maintenance.depot@kmrl.co.in",
            responseTime: "45 min SLA", responseColor: "#059669",
        },
    ];
    const escalations = [
        {
            sevLabel: "Sev-3 · Critical", sevColor: "#DC2626", sevBg: "#FEF2F2", sevBd: "rgba(220,38,38,0.22)",
            title: "Safety or Operational Emergency",
            desc: "Power failures, signal loss, collision risk, or platform evacuation.",
            responseTarget: "< 5 minutes", method: "📞 Hot-line Direct Call",
        },
        {
            sevLabel: "Sev-2 · Warning", sevColor: "#D97706", sevBg: "#FFFBEB", sevBd: "rgba(217,119,6,0.22)",
            title: "Service Disruption or Significant Fault",
            desc: "Headway variance beyond threshold, PSD link failures, CBTC radio degradation.",
            responseTarget: "< 15 minutes", method: "📞 OCC Hot-line / Radio",
        },
        {
            sevLabel: "Sev-1 · Info", sevColor: "#64748B", sevBg: "#F1F5F9", sevBd: "rgba(100,116,139,0.18)",
            title: "Operational Query or Minor Anomaly",
            desc: "Non-critical telemetry lag, minor version skew, routine schedule queries.",
            responseTarget: "< 2 hours", method: "✉ Direct System Log",
        },
    ];
    const staticDirectory = [
        { label: "Muttom Operation Control Complex", value: "Metro Depot, Muttom, Choornikkara, Aluva - 683106", icon: <MapPin size={14} color={teal}/> },
        { label: "Control Room Shift Desk Hours", value: "24 Hours / 7 Days a week continuous coverage", icon: <Clock size={14} color={teal}/> },
        { label: "Internal SIP Gateway Node", value: "sip.occ.internal.kmrl.net (Port 5060)", icon: <Server size={14} color={teal}/> },
    ];
    return (<div className="min-h-screen flex flex-col" style={{ background: canvas }}>
      <SharedHeader activePage="contact" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* Page header strip */}
      <div style={{ background: canvas, borderBottom: `1px solid ${bd}` }}>
        <div className="max-w-7xl mx-auto px-6 py-5 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-[26px] font-extrabold mb-1" style={{ fontFamily: DISPLAY, color: inkH }}>KMRL Systems Directory &amp; Contacts</h1>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Official contact desk directory for Operations, IT Systems, Safety, and Maintenance departments.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

        {/* Quick contact cards */}
        <section>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {cards.map(c => <QuickCard key={c.dept} {...c}/>)}
          </div>
        </section>

        {/* Two-column layout */}
        <section>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 items-stretch">

            {/* Left: OCC Directory & Headquarters Info */}
            <div className="rounded-2xl overflow-hidden flex flex-col" style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, boxShadow: "0 1px 4px rgba(0,0,0,0.05)" }}>
              <div className="px-5 py-4 shrink-0" style={{ borderBottom: `1px solid ${bd}` }}>
                <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>Operations Control Centre Directory</h2>
                <p className="text-xs mt-0.5" style={{ fontFamily: SANS, color: inkM }}>Static contact directory for internal systems communication.</p>
              </div>

              <div className="p-5 flex flex-col gap-5 flex-1">
                {staticDirectory.map((item, i) => (<div key={item.label} style={{ display: "flex", gap: 12, alignItems: "flex-start", paddingBottom: i < staticDirectory.length - 1 ? 16 : 0, borderBottom: i < staticDirectory.length - 1 ? `1px solid ${bd}` : "none" }}>
                    <div style={{ width: 32, height: 32, borderRadius: 8, background: "rgba(0,150,136,0.1)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      {item.icon}
                    </div>
                    <div>
                      <p style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 700, color: inkH, marginBottom: 3 }}>{item.label}</p>
                      <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, lineHeight: 1.5 }}>{item.value}</p>
                    </div>
                  </div>))}
              </div>

              <div style={{ padding: "16px 20px", background: "#F8FAFC", borderTop: `1px solid ${bd}` }}>
                <p style={{ fontFamily: MONO, fontSize: 11, color: inkM }}>
                  KMRL Telemetry Hotline: <strong>1800-425-0011</strong> (Toll Free)
                </p>
              </div>
            </div>

            {/* Right: Escalation Matrix */}
            <div className="rounded-2xl overflow-hidden flex flex-col" style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, boxShadow: "0 1px 4px rgba(0,0,0,0.05)" }}>
              <div className="px-5 py-4 shrink-0" style={{ borderBottom: `1px solid ${bd}` }}>
                <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>Escalation Protocol Matrix</h2>
                <p className="text-xs mt-0.5" style={{ fontFamily: SANS, color: inkM }}>Severity-tiered response guidelines for OCC personnel.</p>
              </div>
              <div className="flex flex-col flex-1">
                {escalations.map((row, i) => (<EscRow key={row.sevLabel} {...row} {...(i === escalations.length - 1 ? { style: { borderBottom: "none" } } : {})}/>))}
              </div>
            </div>

          </div>
        </section>
      </main>

      <SharedFooter activePage="contact" onNavigate={onNavigate}/>
    </div>);
}
