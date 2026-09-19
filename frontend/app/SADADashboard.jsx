import React, { useState, useEffect } from "react";
import { Database, TrendingUp, ClipboardCheck, Cpu, ArrowRight, Loader2, ShieldAlert, CheckCircle, RefreshCw } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

export const MAINTENANCE_API_BASE_URL = "http://localhost:8080/api/v1/maintenance";
export const APPROVER_API_BASE_URL = "http://localhost:8080/api/approver";

const teal = "#009688";
const tealDk = "#00786B";
const tealLight = "#E0F2F1";
const emerald = "#10B981";
const emeraldSoft = "#E6F4EA";
const amber = "#F59E0B";
const amberSoft = "#FEF7E0";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const shadow = "0 1px 3px rgba(0,0,0,0.05), 0 1px 2px -1px rgba(0,0,0,0.05)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";

const DEFAULT_REPORTS = [
    { name: "Fleet Weekly Summary", type: "Fleet", generated: "Today, 06:00", status: "Ready" },
    { name: "Headway Analysis — North", type: "Schedule", generated: "Today, 05:30", status: "Ready" },
    { name: "Maintenance Compliance Q2", type: "Maintenance", generated: "Yesterday", status: "Ready" },
    { name: "Alert Trend Report", type: "Alerts", generated: "Yesterday", status: "Ready" },
    { name: "System Interlocking Audit", type: "Safety", generated: "2 days ago", status: "Ready" },
];

export default function SADADashboard({ isSignedIn, onNavigate, onLogOut, userName = "Senior Approver", userRole = "SADA" }) {
    const firstName = userName.split(" ")[0];
    const [loading, setLoading] = useState(() => !getCached("sada_pending"));
    const [dbTicketsCount, setDbTicketsCount] = useState(() => getCached("sada_db_tickets", 0));
    const [unresolvedTicketsCount, setUnresolvedTicketsCount] = useState(() => getCached("sada_unresolved_tickets", 0));
    const [pendingApprovals, setPendingApprovals] = useState(() => getCached("sada_pending", []));
    const [pendingApprovalsCount, setPendingApprovalsCount] = useState(() => getCached("sada_pending_count", 0));
    const [urgentCount, setUrgentCount] = useState(() => getCached("sada_urgent_count", 0));
    const [approverError, setApproverError] = useState(null);
    const [fleetMetrics, setFleetMetrics] = useState(() => getCached("sada_fleet_metrics", { availability: "100.0%", active: 0, standby: 0, maintenance: 0, total: 25 }));
    const [scheduleMetrics, setScheduleMetrics] = useState(() => getCached("sada_schedule_metrics", { windowTrips: 0, completed: 0, active: 0 }));

    const fetchSadaDbData = async () => {
        const hasCache = Boolean(getCached("sada_pending"));
        if (!hasCache) setLoading(true);
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };

        const [ticketsResult, approverResult, fleetResult, scheduleResult] = await Promise.allSettled([
            fetch(`${MAINTENANCE_API_BASE_URL}/tickets`, { headers }).then((res) => res.ok ? res.json() : []),
            fetch(`${APPROVER_API_BASE_URL}/tasks/pending`, { headers }).then((res) => res.ok ? res.json() : []),
            fetch("http://localhost:8080/api/v1/fleet/yard", { headers }).then((res) => res.ok ? res.json() : []),
            fetch("http://localhost:8080/api/v1/schedule/trips/window", { headers }).then((res) => res.ok ? res.json() : []),
        ]);

        if (ticketsResult.status === "fulfilled") {
            const list = Array.isArray(ticketsResult.value) ? ticketsResult.value : [];
            setDbTicketsCount(list.length);
            setCached("sada_db_tickets", list.length);
            const activeTickets = list.filter(t => {
                const s = String(t.status || "").toUpperCase();
                return s === "OPEN" || s === "IN_PROGRESS" || s === "PENDING_CLOSURE";
            });
            setUnresolvedTicketsCount(activeTickets.length);
            setCached("sada_unresolved_tickets", activeTickets.length);
        }

        if (approverResult.status === "fulfilled") {
            const tasks = Array.isArray(approverResult.value) ? approverResult.value : [];
            const mapped = tasks.map((t, idx) => {
                const p = String(t.priority || "").toUpperCase();
                const isHigh = p === "HIGH" || p === "CRITICAL";
                return {
                    id: t.taskId || String(idx),
                    title: t.title || t.requestType || "Approval request",
                    time: t.createdAt ? new Date(t.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "Recently",
                    badge: isHigh ? "High priority" : "Standard",
                    badgeBg: isHigh ? amberSoft : tealLight,
                    badgeColor: isHigh ? "#B06000" : tealDk,
                    isHigh,
                };
            });
            const top5 = mapped.slice(0, 5);
            setPendingApprovals(top5);
            setPendingApprovalsCount(mapped.length);
            const urgent = mapped.filter((m) => m.isHigh).length;
            setUrgentCount(urgent);
            setCached("sada_pending", top5);
            setCached("sada_pending_count", mapped.length);
            setCached("sada_urgent_count", urgent);
            setApproverError(null);
        } else {
            setApproverError("Approver service unavailable.");
        }

        if (fleetResult.status === "fulfilled") {
            const trackGroups = Array.isArray(fleetResult.value) ? fleetResult.value : [];
            const allTrains = trackGroups.flatMap(g => g.trains || []);
            const total = allTrains.length || 25;
            const active = allTrains.filter(t => t.status === "IN_SERVICE").length;
            const standby = allTrains.filter(t => t.status === "STANDBY").length;
            const maintenance = allTrains.filter(t => t.status === "IN_MAINTENANCE").length;
            const avail = (((total - maintenance) / total) * 100).toFixed(1);
            const fm = { availability: `${avail}%`, active, standby, maintenance, total };
            setFleetMetrics(fm);
            setCached("sada_fleet_metrics", fm);
        }

        if (scheduleResult.status === "fulfilled") {
            const windowTrips = Array.isArray(scheduleResult.value) ? scheduleResult.value : [];
            const completed = windowTrips.filter(t => t.status === "COMPLETED").length;
            const active = windowTrips.filter(t => t.status === "IN_SERVICE").length;
            const sm = { windowTrips: windowTrips.length, completed, active };
            setScheduleMetrics(sm);
            setCached("sada_schedule_metrics", sm);
        }

        setLoading(false);
    };

    useEffect(() => {
        fetchSadaDbData();
        const iv = setInterval(fetchSadaDbData, 4000);
        return () => clearInterval(iv);
    }, []);

    return (
        <div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
            <SharedHeader activePage="sadadashboard" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* Full-width White Header Band (Fleet Page Standard) */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>Executive Governance — Welcome, {firstName}</h1>
            </div>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              System Administration &amp; Approver (SADA) Portal — Live fleet records, schedule sign-offs, and SIL-4 safety interlocking.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

                    {/* Executive Stat cards (Fleet Card Design) */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 16, marginBottom: 28 }} className="adm-stats">
                        {[
                            {
                                accent: amber,
                                value: String(pendingApprovalsCount),
                                label: "Pending Approvals",
                                sub: "pending SADA sign-off",
                                caption: `${urgentCount} high priority flagged`,
                                subColor: amber,
                            },
                            {
                                accent: teal,
                                value: `${unresolvedTicketsCount} Open Cards`,
                                label: "Active Maintenance Records",
                                sub: "in workshop repair",
                                caption: `${dbTicketsCount} total job cards logged`,
                                subColor: unresolvedTicketsCount > 0 ? amber : emerald,
                            },
                            {
                                accent: emerald,
                                value: fleetMetrics.availability,
                                label: "Fleet Availability Rate",
                                sub: "fleet ready status",
                                caption: `${fleetMetrics.active} Active, ${fleetMetrics.standby} Standby ready`,
                                subColor: emerald,
                            },
                            {
                                accent: tealDk,
                                value: `${scheduleMetrics.windowTrips} Runs`,
                                label: "Active Window Schedule",
                                sub: "scheduled runs",
                                caption: `${scheduleMetrics.completed} completed, ${scheduleMetrics.active} in service`,
                                subColor: emerald,
                            },
                        ].map(s => (
                            <div key={s.label} className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${s.accent}`, boxShadow: shadow, minHeight: 110 }}>
                                <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>{s.label}</p>
                                <div className="flex items-baseline gap-2 flex-wrap">
                                    <span style={{ fontFamily: DISPLAY, fontSize: 28, fontWeight: 800, color: inkH, lineHeight: 1.1, whiteSpace: "nowrap" }}>{s.value}</span>
                                    <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: s.subColor }}>{s.sub}</span>
                                </div>
                                <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>{s.caption}</p>
                            </div>
                        ))}
                    </div>

                    {/* Two-panel row: Reports + Approvals */}
                    <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr", gap: 20, marginBottom: 40 }} className="adm-panels">

                        {/* Recent Reports */}
                        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow }}>
                            <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                <div>
                                    <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>System Analytics & Governance Reports</p>
                                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>Latest generated compliance and fleet documents</p>
                                </div>
                                <button onClick={() => onNavigate("reports")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal, background: "none", border: "none", cursor: "pointer" }}>
                                    View all <ArrowRight size={12}/>
                                </button>
                            </div>

                            <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr 90px", padding: "9px 20px", background: "#F8FAFC", borderBottom: `1px solid ${bd}` }}>
                                {["REPORT", "TYPE", "GENERATED", "STATUS"].map(col => (
                                    <span key={col} style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>{col}</span>
                                ))}
                            </div>
                            {DEFAULT_REPORTS.map((r, i) => (
                                <div key={r.name} style={{
                                    display: "grid", gridTemplateColumns: "2fr 1fr 1fr 90px",
                                    padding: "12px 20px",
                                    borderBottom: i < DEFAULT_REPORTS.length - 1 ? `1px solid ${bd}` : "none",
                                    alignItems: "center",
                                }}>
                                    <p style={{ fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: inkH, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", paddingRight: 8 }}>{r.name}</p>
                                    <p style={{ fontFamily: SANS, fontSize: 11.5, color: inkM }}>{r.type}</p>
                                    <p style={{ fontFamily: MONO, fontSize: 11, color: inkM }}>{r.generated}</p>
                                    <span style={{
                                        display: "inline-flex", alignItems: "center", gap: 5,
                                        padding: "3px 9px", borderRadius: 999, width: "fit-content",
                                        background: r.status === "Ready" ? emeraldSoft : amberSoft,
                                        color: r.status === "Ready" ? "#137333" : "#B06000",
                                        fontFamily: SANS, fontSize: 11, fontWeight: 700,
                                    }}>
                                        <span style={{ width: 5, height: 5, borderRadius: "50%", background: r.status === "Ready" ? emerald : amber, flexShrink: 0 }}/>
                                        {r.status}
                                    </span>
                                </div>
                            ))}
                        </div>

                        {/* Pending SADA Approvals Queue */}
                        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow, display: "flex", flexDirection: "column" }}>
                            <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                <div>
                                    <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>Schedule & COF Sign-Offs</p>
                                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>Requests awaiting SADA approval decision</p>
                                </div>
                                <button onClick={() => onNavigate("approver")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal, background: "none", border: "none", cursor: "pointer" }}>
                                    Open Approver Panel <ArrowRight size={12}/>
                                </button>
                            </div>

                            <div style={{ flex: 1 }}>
                                {pendingApprovals.length === 0 ? (
                                    <p style={{ padding: "28px 20px", textAlign: "center", color: approverError ? "#B06000" : inkM, fontFamily: SANS, fontSize: 13 }}>
                                        {loading ? "Checking approvals..." : approverError ? approverError : "No pending approvals awaiting sign-off."}
                                    </p>
                                ) : (
                                    pendingApprovals.map((a, i) => (
                                        <div key={a.id + i} style={{ padding: "14px 20px", borderBottom: i < pendingApprovals.length - 1 ? `1px solid ${bd}` : "none", display: "flex", alignItems: "flex-start", gap: 12 }}>
                                            <span style={{ width: 8, height: 8, borderRadius: "50%", background: amber, flexShrink: 0, marginTop: 4 }}/>
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <p style={{ fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: inkH, lineHeight: 1.4, marginBottom: 3 }}>{a.title}</p>
                                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                                    <span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>{a.time}</span>
                                                    <span style={{ padding: "2px 7px", borderRadius: 999, background: a.badgeBg, color: a.badgeColor, fontFamily: SANS, fontSize: 10.5, fontWeight: 700 }}>{a.badge}</span>
                                                </div>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>

            </main>

            <SharedFooter activePage="sadadashboard" onNavigate={onNavigate}/>
        </div>
    );
}
