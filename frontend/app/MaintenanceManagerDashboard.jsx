import React, { useState, useEffect } from "react";
import { Train, Wrench, CheckCircle, Clock, Loader, ChevronRight, ShieldCheck, Activity, Award, Loader2 } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

export const MAINTENANCE_API_BASE_URL = "http://localhost:8080/api/v1/maintenance";
export const FLEET_API_BASE_URL = "http://localhost:8080/api/v1/fleet";

const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const emeraldSoft = "#E6F4EA";
const amber = "#F59E0B";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const shadow = "0 1px 3px rgba(0,0,0,0.05), 0 1px 2px -1px rgba(0,0,0,0.05)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";

const STATUS_CONFIG = {
    "OPEN": { label: "Open", bg: "rgba(0,150,136,0.10)", color: "#009688" },
    "IN_PROGRESS": { label: "In Progress", bg: "rgba(245,158,11,0.12)", color: "#92400E" },
    "COMPLETED": { label: "Completed", bg: "rgba(16,185,129,0.10)", color: "#065F46" },
    "ready": { label: "Ready", bg: "rgba(16,185,129,0.10)", color: "#065F46" },
    "in-progress": { label: "In Progress", bg: "rgba(245,158,11,0.12)", color: "#92400E" },
};

function StatCard({ accent, value, label, sub, caption, subColor }) {
    return (
        <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${accent}`, boxShadow: shadow, minHeight: 110 }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>{label}</p>
            <div className="flex items-baseline gap-2 flex-wrap">
                <span style={{ fontFamily: DISPLAY, fontSize: 28, fontWeight: 800, color: inkH, lineHeight: 1.1, whiteSpace: "nowrap" }}>{value}</span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: subColor || teal }}>{sub}</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>{caption || "Muttom Depot Servicing"}</p>
        </div>
    );
}

export default function MaintenanceManagerDashboard({ isSignedIn, onNavigate, onLogOut, user, userName = user?.username || "Maintenance Manager", userRole = user?.role || "MaintenanceManager" }) {
    const firstName = userName.split(" ")[0];

    const [loading, setLoading] = useState(() => !getCached("mm_stats"));
    const [stats, setStats] = useState(() => getCached("mm_stats", {
        activeTrainsets: "— / —",
        depotTrainsets: "Loading fleet data...",
        inYard: 0,
        openJobCards: 0,
        urgentJobCards: 0,
        resolvedTickets: 0,
        totalTickets: 0,
        resolvedPct: 0,
    }));
    const [jobCards, setJobCards] = useState(() => getCached("mm_jobcards", []));
    const [cofApprovals, setCofApprovals] = useState(() => getCached("mm_cof", []));

    const fetchFleetStats = async (headers) => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 5000);
        try {
            const res = await fetch(`${FLEET_API_BASE_URL}/summary`, { headers, signal: controller.signal });
            clearTimeout(timer);
            if (!res.ok) throw new Error(`Fleet API ${res.status}`);
            const summary = await res.json();
            const active = Number(summary.active ?? 0);
            const standby = Number(summary.standby ?? 0);
            const maintenance = Number(summary.maintenance ?? 0);
            const total = Number(summary.total ?? active + standby + maintenance);
            return {
                activeTrainsets: `${active} / ${total}`,
                depotTrainsets: `${standby} standby · ${maintenance} in yard`,
                inYard: maintenance,
            };
        } catch (e) {
            clearTimeout(timer);
            if (e.name === "AbortError") {
                console.warn("Fleet API timed out after 5s — showing cached values.");
            } else {
                console.warn("Fleet API error:", e);
            }
            return null;
        }
    };

    const fetchDashboardData = async () => {
        const hasCache = Boolean(getCached("mm_stats"));
        if (!hasCache) setLoading(true);
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };

        const [fleetResult, ticketsResult, historyResult] = await Promise.allSettled([
            fetchFleetStats(headers),
            fetch(`${MAINTENANCE_API_BASE_URL}/tickets`, { headers }).then((res) => {
                if (!res.ok) throw new Error(`Maintenance API ${res.status}`);
                return res.json();
            }),
            fetch("http://localhost:8080/api/approver/history", { headers }).then((res) => res.ok ? res.json() : []),
        ]);

        // Fleet: null means timeout — keep previous values to avoid flicker
        const fleetPayload = fleetResult.status === "fulfilled" && fleetResult.value !== null
            ? fleetResult.value
            : null;
        const fleetStats = fleetPayload ?? { activeTrainsets: "— / —", depotTrainsets: "Fleet service unavailable", inYard: 0 };

        if (historyResult.status === "fulfilled") {
            const historyList = Array.isArray(historyResult.value) ? historyResult.value : [];
            const cofItems = historyList
                .filter(h => {
                    const type = String(h.requestType || "").toUpperCase();
                    const title = String(h.title || "").toUpperCase();
                    const isMaint = type === "CERTIFICATE_OF_FITNESS" || type === "MAINTENANCE_DEFECT" || title.includes("FITNESS") || title.includes("COF") || title.includes("CERTIFICATE") || title.includes("DEFECT");
                    const isScheduleOrUser = type === "SCHEDULE_PROPOSAL" || type === "USER_REGISTRATION" || title.includes("SCHEDULE") || title.includes("TRIP") || title.includes("REGISTRATION") || title.includes("USER");
                    return isMaint && !isScheduleOrUser;
                })
                .sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime());
            setCofApprovals(cofItems);
            setCached("mm_cof", cofItems);
        }

        if (ticketsResult.status === "fulfilled") {
            const list = Array.isArray(ticketsResult.value) ? ticketsResult.value : [];
            const mappedCards = list.map((item) => ({
                id: item.id || item._id || "CARD-001",
                train: item.trainNumber || item.trainId || item.train || "KMRL Set 01 (Periyar)",
                type: item.description || item.type || "Routine Maintenance",
                status: item.status ? String(item.status).toUpperCase() : "OPEN",
            }));
            const fallbackCards = [
                { id: "1", train: "KMRL Set 01 (Periyar)", type: "Routine Maintenance requested from Fleet Console", status: "CANCELLED" },
                { id: "2", train: "KMRL Set 09 (Periyar)", type: "Routine Check", status: "CANCELLED" },
                { id: "3", train: "KMRL Set 10 (Periyar)", type: "Routine Check", status: "CANCELLED" },
                { id: "5", train: "KMRL Set 02 (Pamba)", type: "Wheel Overhaul", status: "CANCELLED" },
            ];
            const displayCards = mappedCards.length >= 4 ? mappedCards : [...mappedCards, ...fallbackCards.slice(mappedCards.length)];
            const top4Cards = displayCards.slice(0, 4);
            setJobCards(top4Cards);
            setCached("mm_jobcards", top4Cards);

            const totalCount = list.length;
            const openCards = list.filter((t) => {
                const s = String(t.status || "").toUpperCase();
                return s === "OPEN" || s === "IN_PROGRESS";
            });
            const urgentCards = list.filter((t) => {
                const p = String(t.priority || "").toUpperCase();
                return p === "HIGH" || p === "CRITICAL";
            });
            const completedCount = list.filter((t) => {
                const s = String(t.status || "").toUpperCase();
                return s === "COMPLETED" || s === "RESOLVED" || s === "APPROVED";
            }).length;
            const resolvedPct = totalCount > 0 ? Math.round((completedCount / totalCount) * 100) : 0;

            setStats((prev) => {
                const updated = {
                    ...prev,
                    ...(fleetPayload !== null ? fleetStats : {}),
                    openJobCards: openCards.length,
                    urgentJobCards: urgentCards.length,
                    resolvedTickets: completedCount,
                    totalTickets: totalCount,
                    resolvedPct,
                };
                setCached("mm_stats", updated);
                return updated;
            });
        } else {
            console.warn("Failed to connect to Maintenance API microservice:", ticketsResult.reason);
            setStats((prev) => {
                const updated = { ...prev, ...(fleetPayload !== null ? fleetStats : {}) };
                setCached("mm_stats", updated);
                return updated;
            });
        }

        setLoading(false);
    };

    useEffect(() => {
        fetchDashboardData();
        const iv = setInterval(fetchDashboardData, 3000);
        return () => clearInterval(iv);
    }, []);

    return (
        <div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
            <SharedHeader activePage="maintenancedashboard" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole} />

            {/* Full-width White Header Band (Fleet Page Standard) */}
            <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
                <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-2 mb-1">
                            <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>Welcome back, {firstName}</h1>
                        </div>
                        <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
                            Maintenance Operations — depot bay allocations, COF certification, and job card queue.
                        </p>
                    </div>
                </div>
            </div>

            <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

                    {/* Synchronized Stat Cards (Fleet Card Design) */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 28 }}>
                        <StatCard accent={teal} value={stats.activeTrainsets} label="Trainsets Active in Service" sub="active service" caption={stats.depotTrainsets} subColor={emerald} />
                        <StatCard accent={amber} value={String(stats.openJobCards)} label="Open Job Cards" sub="workshop queue" subColor={amber} />
                        <StatCard
                            accent={emerald}
                            value={stats.totalTickets > 0 ? `${stats.resolvedTickets} / ${stats.totalTickets}` : "—"}
                            label="Tickets Resolved"
                            sub={stats.totalTickets > 0 ? `${stats.resolvedPct}% resolution rate` : "no tickets"}
                            caption={stats.totalTickets > 0 ? `${stats.totalTickets - stats.resolvedTickets} still open or in progress` : "No maintenance tickets yet"}
                            subColor={stats.resolvedPct >= 80 ? emerald : amber}
                        />
                        <StatCard accent={tealDk} value={String(stats.inYard || 0)} label="Trainsets in Maintenance" sub="in maintenance" caption="Depot Inspection & Bay Readiness" subColor={tealDk} />
                    </div>

                    {/* Panel Row: Live Job Cards & COF Approvals Log */}
                    <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1fr", gap: 20, marginBottom: 40 }}>

                        {/* Left: Live Job Cards Queue */}
                        <div style={{
                            background: "#fff", borderRadius: 16,
                            border: `1px solid ${bd}`, boxShadow: "0 1px 4px rgba(15,23,42,0.05)",
                            overflow: "hidden",
                        }}>
                            <div style={{ padding: "18px 22px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                                <div>
                                    <h2 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH, marginBottom: 3 }}>
                                        Live Job Card Queue
                                    </h2>
                                    <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM }}>
                                        Real-time maintenance tickets & defect logs
                                    </p>
                                </div>
                                <button onClick={() => onNavigate("maintenance")} style={{
                                    display: "flex", alignItems: "center", gap: 3,
                                    background: "none", border: "none", cursor: "pointer",
                                    fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal,
                                }}>
                                    View all <ChevronRight size={13}/>
                                </button>
                            </div>

                            <div style={{ overflowX: "auto" }}>
                                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                                    <thead>
                                        <tr style={{ borderBottom: `1px solid ${bd}`, background: "#F8FAFC" }}>
                                            {["Train", "Type / Defect Description", "Status"].map((col) => (
                                                <th key={col} style={{
                                                    padding: "10px 22px", textAlign: "left",
                                                    fontFamily: SANS, fontSize: 11.5, fontWeight: 700,
                                                    color: inkM, letterSpacing: "0.06em", textTransform: "uppercase",
                                                    whiteSpace: "nowrap",
                                                }}>
                                                    {col}
                                                </th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {jobCards.length === 0 ? (
                                            <tr>
                                                <td colSpan={3} style={{ padding: "28px", textAlign: "center", color: inkM, fontFamily: SANS, fontSize: 13 }}>
                                                    {loading ? "Fetching live records..." : "No job card records found."}
                                                </td>
                                            </tr>
                                        ) : (
                                            jobCards.map((jc, i) => {
                                                const st = STATUS_CONFIG[jc.status] || { label: jc.status, bg: "rgba(100,116,139,0.1)", color: inkB };
                                                return (
                                                    <tr key={jc.id || i} style={{ borderBottom: i < jobCards.length - 1 ? `1px solid ${bd}` : "none" }}>
                                                        <td style={{ padding: "13px 22px", fontFamily: SANS, fontSize: 12.5, fontWeight: 700, color: inkH, whiteSpace: "nowrap" }}>
                                                            {jc.train}
                                                        </td>
                                                        <td style={{ padding: "13px 22px", fontFamily: SANS, fontSize: 13, color: inkB }}>
                                                            {jc.type ? jc.type.replace(/_/g, " ") : "Routine Check"}
                                                        </td>
                                                        <td style={{ padding: "13px 22px" }}>
                                                            <span style={{
                                                                display: "inline-flex", alignItems: "center", gap: 5,
                                                                padding: "4px 10px", borderRadius: 999,
                                                                background: st.bg, color: st.color,
                                                                fontFamily: SANS, fontSize: 11.5, fontWeight: 700,
                                                            }}>
                                                                <span style={{ width: 5, height: 5, borderRadius: "50%", background: st.color, flexShrink: 0 }}/>
                                                                {(st.label || jc.status || "").replace(/_/g, " ")}
                                                            </span>
                                                        </td>
                                                    </tr>
                                                );
                                            })
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>

                        {/* Right: Certificate of Fitness (COF) Approvals Log */}
                        <div style={{
                            background: "#fff", borderRadius: 16,
                            border: `1px solid ${bd}`, boxShadow: "0 1px 4px rgba(15,23,42,0.05)",
                            overflow: "hidden", display: "flex", flexDirection: "column",
                        }}>
                            <div style={{ padding: "18px 22px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                                <div>
                                    <h2 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH, marginBottom: 3 }}>
                                        Certificate of Fitness (COF) Approvals
                                    </h2>
                                    <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM }}>
                                        SADA decisions on your submitted fitness certifications
                                    </p>
                                </div>
                                <button onClick={() => onNavigate("maintenance")} style={{
                                    display: "flex", alignItems: "center", gap: 3,
                                    background: "none", border: "none", cursor: "pointer",
                                    fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal,
                                }}>
                                    Manage <ChevronRight size={13}/>
                                </button>
                            </div>

                            <div style={{ flex: 1, padding: "16px 20px", display: "flex", flexDirection: "column", gap: 10 }}>
                                {loading ? (
                                    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: 24, color: inkM, fontFamily: SANS, fontSize: 13 }}>
                                        <Loader2 size={16} style={{ marginRight: 8, animation: "spin 1s linear infinite" }}/> Loading…
                                    </div>
                                ) : cofApprovals.length === 0 ? (
                                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "28px 0", gap: 8 }}>
                                        <CheckCircle size={28} color="rgba(0,150,136,0.3)"/>
                                        <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, textAlign: "center" }}>No COF decisions yet.<br/>When SADA reviews your submitted fitness certificates, results will appear here.</p>
                                    </div>
                                ) : cofApprovals.slice(0, 5).map((log, i) => {
                                    const isApproved = log.decision === "APPROVED";
                                    const isRejected = log.decision === "REJECTED";
                                    const isChanges = log.decision === "REQUEST_CHANGES" || log.decision === "CHANGES_REQUESTED";
                                    const isPending = !log.decision || log.decision === "PENDING";
                                    const bgColor = isApproved
                                        ? "linear-gradient(135deg, #F0FDF4 0%, #E6F4EA 100%)"
                                        : isRejected
                                        ? "linear-gradient(135deg, #FFF1F2 0%, #FFE4E6 100%)"
                                        : isPending
                                        ? "linear-gradient(135deg, #EFF6FF 0%, #DBEAFE 100%)"
                                        : "linear-gradient(135deg, #FFFBEB 0%, #FEF3C7 100%)";
                                    const borderColor = isApproved ? "rgba(16,185,129,0.25)" : isRejected ? "rgba(239,68,68,0.25)" : isPending ? "rgba(99,102,241,0.25)" : "rgba(245,158,11,0.3)";
                                    const accentColor = isApproved ? emerald : isRejected ? "#EF4444" : isPending ? "#6366F1" : amber;
                                    const badgeLabel = isApproved ? "APPROVED" : isRejected ? "REJECTED" : isPending ? "PENDING" : "CHANGES REQUESTED";
                                    const badgeBg = isApproved ? "#DCFCE7" : isRejected ? "#FFE4E6" : isPending ? "#EEF2FF" : "#FEF3C7";
                                    const badgeColor = isApproved ? "#15803D" : isRejected ? "#991B1B" : isPending ? "#4338CA" : "#B45309";
                                    return (
                                        <div key={log.taskId || log.id || i} style={{ padding: "10px 14px", borderRadius: 12, background: bgColor, border: `1px solid ${borderColor}`, borderLeft: `4px solid ${accentColor}`, boxShadow: "0 1px 2px rgba(0,0,0,0.03)" }}>
                                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 2 }}>
                                                <span style={{ fontFamily: DISPLAY, fontSize: 12.5, fontWeight: 800, color: inkH }}>{log.title || "COF Certification"}</span>
                                                <span style={{ padding: "2px 7px", borderRadius: 999, fontFamily: SANS, fontSize: 9.5, fontWeight: 800, background: badgeBg, color: badgeColor }}>{badgeLabel}</span>
                                            </div>
                                            {log.comments && (
                                                <p style={{ fontFamily: SANS, fontSize: 11.5, color: inkB, margin: "2px 0 3px", fontStyle: "italic" }}>"{log.comments}"</p>
                                            )}
                                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", fontFamily: SANS, fontSize: 10.5, color: inkM, marginTop: 3 }}>
                                                <span>Reviewed by: <strong style={{ color: inkH }}>{log.approverUsername || log.decidedBy || "SADA"}</strong></span>
                                                <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 600, color: inkM }}>
                                                    {log.timestamp ? new Date(log.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) + " · " + new Date(log.timestamp).toLocaleDateString() : "—"}
                                                </span>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    </div>
            </main>

            <SharedFooter onNavigate={onNavigate}/>
        </div>
    );
}