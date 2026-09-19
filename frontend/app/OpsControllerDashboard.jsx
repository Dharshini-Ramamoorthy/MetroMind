import { useState, useEffect, useCallback } from "react";
import { Train, TrendingUp, Clock, Activity, ArrowRight, Loader2, ShieldCheck, Zap, Radio } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

const teal        = "#009688";
const tealDk      = "#00786B";
const tealLight   = "#E0F2F1";
const emerald     = "#10B981";
const emeraldSoft = "#E6F4EA";
const amber       = "#F59E0B";
const amberSoft   = "#FEF7E0";
const rose        = "#EF4444";
const inkH        = "#0F172A";
const inkB        = "#334155";
const inkM        = "#64748B";
const bd          = "rgba(15,23,42,0.08)";
const shadow      = "0 1px 3px rgba(0,0,0,0.05), 0 1px 2px -1px rgba(0,0,0,0.05)";
const DISPLAY     = "'Plus Jakarta Sans', sans-serif";
const SANS        = "Inter, sans-serif";
const MONO        = "'JetBrains Mono', monospace";

const FLEET_API_BASE =
  (typeof import.meta !== "undefined" && import.meta.env?.VITE_FLEET_API_BASE) ||
  "http://localhost:8080/api/v1";

const SCHEDULE_API_BASE =
  (typeof import.meta !== "undefined" && import.meta.env?.VITE_SCHEDULE_API_BASE) ||
  "http://localhost:8080/api/v1";

class ApiError extends Error {
  constructor(message, service) {
    super(message);
    this.service = service;
  }
}

async function apiFetch(base, service, path) {
  let res;
  const token = localStorage.getItem("auth_token");
  try {
    res = await fetch(`${base}${path}`, {
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    });
  } catch {
    throw new ApiError(`Can't reach ${service} service.`, service);
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      message = body.detail || body.message || message;
    } catch {}
    throw new ApiError(message, service);
  }
  if (res.status === 204) return undefined;
  return res.json();
}

const STATUS_COLOR = {
  ACTIVE:    { bg: tealLight,   color: tealDk,    label: "Active"    },
  PLANNED:   { bg: "#F1F5F9",   color: inkM,      label: "Planned"   },
  PROPOSED:  { bg: "#EFF6FF",   color: "#2563EB", label: "Proposed"  },
  COMPLETED: { bg: emeraldSoft, color: "#137333", label: "Completed" },
  DELAYED:   { bg: amberSoft,   color: "#B06000", label: "Delayed"   },
  CANCELLED: { bg: "#FEF2F2",   color: "#DC2626", label: "Rejected"  },
  REJECTED:  { bg: "#FEF2F2",   color: "#DC2626", label: "Rejected"  },
};

function StatCard({ accent, value, label, sub, subColor, loading, caption }) {
  return (
    <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${accent}`, boxShadow: shadow, minHeight: 110 }}>
      <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>{label}</p>
      <div className="flex items-baseline gap-2 flex-wrap">
        <span style={{ fontFamily: DISPLAY, fontSize: 28, fontWeight: 800, color: inkH, lineHeight: 1.1, whiteSpace: "nowrap" }}>{loading ? "–" : value}</span>
        <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: subColor }}>{loading ? "Loading…" : sub}</span>
      </div>
      <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>{caption || "3-Car Alstom Metropolis fleet"}</p>
    </div>
  );
}

export default function OpsControllerDashboard({
  isSignedIn, onNavigate, onLogOut,
  userName = "Ravi", userRole = "OpsController",
}) {
  const firstName = userName.split(" ")[0];

  const [summary, setSummary]     = useState(() => getCached("oc_summary", null));
  const [trips, setTrips]         = useState(() => getCached("oc_trips", []));
  const [loading, setLoading]     = useState(() => !getCached("oc_summary"));
  const [fleetError, setFleetError]       = useState(null);
  const [scheduleError, setScheduleError] = useState(null);
  const [approverHistory, setApproverHistory] = useState(() => getCached("oc_history", []));

  const loadData = useCallback(async () => {
    const hasCache = Boolean(getCached("oc_summary"));
    if (!hasCache) setLoading(true);
    const token = localStorage.getItem("auth_token");
    const headers = {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };

    const [fleetResult, scheduleResult, historyResult] = await Promise.allSettled([
      apiFetch(FLEET_API_BASE, "fleet", "/fleet/summary"),
      apiFetch(SCHEDULE_API_BASE, "schedule", "/schedule/trips/window"),
      fetch("http://localhost:8080/api/approver/history", { headers }).then((res) => res.ok ? res.json() : []),
    ]);

    if (fleetResult.status === "fulfilled") {
      setSummary(fleetResult.value);
      setCached("oc_summary", fleetResult.value);
      setFleetError(null);
    } else {
      setFleetError(fleetResult.reason instanceof Error ? fleetResult.reason.message : "Fleet service unavailable.");
    }

    if (scheduleResult.status === "fulfilled") {
      const tripsList = Array.isArray(scheduleResult.value) ? scheduleResult.value : [];
      setTrips(tripsList);
      setCached("oc_trips", tripsList);
      setScheduleError(null);
    } else {
      setScheduleError(scheduleResult.reason instanceof Error ? scheduleResult.reason.message : "Schedule service unavailable.");
    }

    if (historyResult.status === "fulfilled") {
      const historyList = Array.isArray(historyResult.value) ? historyResult.value : [];
      const sortedHistory = [...historyList].sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime());
      const ocScheduleHistory = sortedHistory.filter(h => {
        const type = String(h.requestType || "").toUpperCase();
        const title = String(h.title || "").toUpperCase();
        const isSchedule = type === "SCHEDULE_PROPOSAL" || title.includes("SCHEDULE") || title.includes("TRIP") || title.includes("RUN-") || title.includes("TIMETABLE");
        const isMaintOrUser = type === "CERTIFICATE_OF_FITNESS" || type === "MAINTENANCE_DEFECT" || type === "USER_REGISTRATION" || title.includes("FITNESS") || title.includes("COF") || title.includes("CERTIFICATE") || title.includes("DEFECT") || title.includes("REGISTRATION") || title.includes("USER");
        return isSchedule && !isMaintOrUser;
      });
      setApproverHistory(ocScheduleHistory);
      setCached("oc_history", ocScheduleHistory);
    }

    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 3000);
    return () => clearInterval(interval);
  }, [loadData]);

  const upcomingTrips = [...trips]
    .sort((a, b) => a.startMinutes - b.startMinutes)
    .slice(0, 5);

  const delayedTrips = trips.filter(t => t.status === "DELAYED");
  const totalConsideredTrips = trips.filter(t => t.status !== "PLANNED").length;
  const adherencePct = totalConsideredTrips > 0
    ? Math.round(((totalConsideredTrips - delayedTrips.length) / totalConsideredTrips) * 1000) / 10
    : 99.4;

  const isOffline = fleetError && scheduleError;

  return (
    <div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
      <SharedHeader
        activePage="opsdashboard"
        isSignedIn={isSignedIn}
        onNavigate={onNavigate}
        onLogOut={onLogOut}
        userName={userName}
        userRole={userRole}
      />

      {/* Full-width White Header Band (Fleet Page Standard) */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>Welcome back, {firstName}</h1>
            </div>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Operations Control Center — live corridor dispatch, headway monitoring &amp; line telemetry.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

          {(fleetError || scheduleError) && (
            <div style={{
              marginBottom: 20, padding: "12px 16px", borderRadius: 10,
              background: "#FEF2F2", border: "1px solid rgba(239,68,68,0.25)",
              display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap",
            }}>
              <span style={{ fontFamily: SANS, fontSize: 13, color: "#B91C1C" }}>
                {isOffline
                  ? "Couldn't reach fleet or schedule service."
                  : fleetError
                  ? `Fleet service: ${fleetError}`
                  : `Schedule service: ${scheduleError}`}
              </span>
              <button onClick={loadData} style={{ fontFamily: SANS, fontSize: 12.5, fontWeight: 700, color: "#B91C1C", background: "none", border: "none", cursor: "pointer" }}>
                Retry
              </button>
            </div>
          )}

          {/* Stat cards (Standardized Fleet Card Style) */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 16, marginBottom: 28 }} className="oc-stats">
            <StatCard
              accent={teal}
              value={summary ? `${summary.active} / ${summary.total}` : "–"}
              label="Active Trainsets"
              sub="in active service"
              subColor={emerald}
              caption={summary ? `${summary.standby} standby · ${summary.maintenance} in yard` : "3-Car Alstom Metropolis fleet"}
              loading={loading && !summary}
            />
            <StatCard
              accent={emerald}
              value={adherencePct !== null ? `${adherencePct}%` : "99.4%"}
              label="Schedule Adherence"
              sub="on-time performance"
              subColor={delayedTrips.length > 0 ? amber : emerald}
              caption={totalConsideredTrips > 0 ? `${delayedTrips.length} of ${totalConsideredTrips} trips delayed` : "Normal Line Operations"}
              loading={loading && trips.length === 0}
            />
            <StatCard
              accent={tealDk}
              value={summary ? String(summary.standby) : "–"}
              label="Standby Reserve"
              sub="ready at Muttom"
              subColor={tealDk}
              caption="Pre-checked for injection"
              loading={loading && !summary}
            />
            <StatCard
              accent={emerald}
              value={summary ? String(summary.total) : "25"}
              label="Total Fleet Managed"
              sub="total trainsets"
              subColor={tealDk}
              caption="KMRL Aluva–Thrippunithura Corridor"
              loading={loading && !summary}
            />
          </div>

          {/* Two-panel row: Live Schedule & SADA Decision Log */}
          <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr", gap: 20, marginBottom: 40 }} className="oc-panels">

            {/* Live Schedule */}
            <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow }}>
              <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <div>
                  <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>Live Corridor Dispatch</p>
                  <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>Real-time departures on Aluva — Thrippunithura Line</p>
                </div>
                <button onClick={() => onNavigate("schedule")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal, background: "none", border: "none", cursor: "pointer" }}>
                  View all <ArrowRight size={12} />
                </button>
              </div>

              {loading && trips.length === 0 ? (
                <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8, padding: "36px 0", color: inkM, fontFamily: SANS, fontSize: 13 }}>
                  <Loader2 size={15} className="animate-spin" /> Loading live corridor runs…
                </div>
              ) : upcomingTrips.length === 0 ? (
                <div style={{ padding: "36px 20px", textAlign: "center", color: inkM, fontFamily: SANS, fontSize: 13 }}>
                  No active trips in current window.
                </div>
              ) : (
                <>
                  <div style={{ display: "grid", gridTemplateColumns: "1.8fr 0.9fr 1.6fr 0.8fr 0.8fr 0.9fr", padding: "10px 20px", background: "#F8FAFC", borderBottom: `1px solid ${bd}` }}>
                    {["TRAIN", "TRIP", "ROUTE", "DEP.", "ARR.", "STATUS"].map(col => (
                      <span key={col} style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>{col}</span>
                    ))}
                  </div>
                  {upcomingTrips.map((r, i) => {
                    const st = STATUS_COLOR[r.status] || STATUS_COLOR.PLANNED;
                    return (
                      <div key={r.id} style={{
                        display: "grid", gridTemplateColumns: "1.8fr 0.9fr 1.6fr 0.8fr 0.8fr 0.9fr",
                        padding: "13px 20px", alignItems: "center",
                        borderBottom: i < upcomingTrips.length - 1 ? `1px solid ${bd}` : "none",
                      }}>
                        <p style={{ fontFamily: SANS, fontSize: 12.5, fontWeight: 700, color: inkH, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                          {r.assignedTrainName || "Unassigned"}
                        </p>
                        <p style={{ fontFamily: MONO, fontSize: 12, color: inkM, whiteSpace: "nowrap" }}>{r.tripCode}</p>
                        <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkB, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", paddingRight: 8 }}>{r.routeName}</p>
                        <p style={{ fontFamily: MONO, fontSize: 12.5, color: inkH, whiteSpace: "nowrap" }}>{r.startTime}</p>
                        <p style={{ fontFamily: MONO, fontSize: 12.5, color: inkM, whiteSpace: "nowrap" }}>{r.endTime}</p>
                        <span style={{
                          display: "inline-flex", alignItems: "center", gap: 5,
                          padding: "3px 9px", borderRadius: 999, width: "fit-content",
                          background: st.bg, color: st.color, fontFamily: SANS, fontSize: 11, fontWeight: 700,
                        }}>
                          <span style={{ width: 5, height: 5, borderRadius: "50%", background: st.color, flexShrink: 0 }} />
                          {st.label}
                        </span>
                      </div>
                    );
                  })}
                </>
              )}
            </div>

            {/* Schedule Proposal Decisions & Status Panel */}
            <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow, display: "flex", flexDirection: "column" }}>
              <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <div>
                  <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>Schedule Proposal Decisions &amp; Status</p>
                  <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>SADA reviews on your proposed trips &amp; timetable runs</p>
                </div>
                <button onClick={() => onNavigate("schedule")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal, background: "none", border: "none", cursor: "pointer" }}>
                  Schedule <ArrowRight size={12} />
                </button>
              </div>

              <div style={{ flex: 1, padding: "16px 20px", display: "flex", flexDirection: "column", gap: 10, justifyContent: "space-between" }}>
                {loading ? (
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: 28, color: inkM, fontFamily: SANS, fontSize: 13 }}>
                    <Loader2 size={16} style={{ marginRight: 8, animation: "spin 1s linear infinite" }}/> Loading proposal decisions…
                  </div>
                ) : approverHistory.length === 0 ? (
                  <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "28px 0", gap: 8 }}>
                    <ShieldCheck size={28} color="rgba(0,150,136,0.3)"/>
                    <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, textAlign: "center" }}>No schedule proposals decided yet.<br/>When SADA approves or rejects your proposed timetable trips, results will appear here.</p>
                  </div>
                ) : approverHistory.slice(0, 5).map((log, i) => {
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
                  const accentColor = isApproved ? emerald : isRejected ? rose : isPending ? "#6366F1" : amber;
                  const badgeLabel = isApproved ? "APPROVED" : isRejected ? "REJECTED" : isPending ? "PENDING" : "CHANGES REQUESTED";
                  const badgeBg = isApproved ? "#DCFCE7" : isRejected ? "#FFE4E6" : isPending ? "#EEF2FF" : "#FEF3C7";
                  const badgeColor = isApproved ? "#15803D" : isRejected ? "#991B1B" : isPending ? "#4338CA" : "#B45309";
                  return (
                    <div key={log.taskId || log.id || i} style={{ padding: "11px 14px", borderRadius: 12, background: bgColor, border: `1px solid ${borderColor}`, borderLeft: `4px solid ${accentColor}`, boxShadow: "0 1px 2px rgba(0,0,0,0.03)" }}>
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 3 }}>
                        <span style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 800, color: inkH }}>
                          {log.title || "Schedule Proposal"}
                        </span>
                        <span style={{ padding: "2px 8px", borderRadius: 999, fontFamily: SANS, fontSize: 10, fontWeight: 800, background: badgeBg, color: badgeColor }}>
                          {badgeLabel}
                        </span>
                      </div>
                      {log.comments && (
                        <p style={{ fontFamily: SANS, fontSize: 12, color: inkB, margin: "2px 0 4px", fontStyle: "italic" }}>
                          "{log.comments}"
                        </p>
                      )}
                      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", fontFamily: SANS, fontSize: 11, color: inkM, marginTop: 4 }}>
                        <span>Reviewed by: <strong style={{ color: inkH }}>{log.approverUsername || log.decidedBy || "SADA"}</strong></span>
                        <span style={{ fontFamily: MONO, fontSize: 10.5, fontWeight: 600, color: inkM }}>
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

      <SharedFooter activePage="opsdashboard" onNavigate={onNavigate} />

      <style>{`
        @media (max-width: 1024px) { .oc-stats { grid-template-columns: repeat(2, 1fr) !important; } }
        @media (max-width: 768px)  { .oc-panels { grid-template-columns: 1fr !important; } }
        @media (max-width: 560px)  { .oc-stats { grid-template-columns: 1fr 1fr !important; } }
      `}</style>
    </div>
  );
}