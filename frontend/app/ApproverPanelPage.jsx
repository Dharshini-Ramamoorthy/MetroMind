import { useState, useMemo, useEffect, useCallback } from "react";
import { Calendar, Train, Wrench, ShieldAlert, UserPlus, CheckCircle, XCircle, Lock, MessageSquare, ArrowLeft, AlertTriangle, X, Loader2, Download, ChevronDown, ChevronUp, Clock, FileText, Filter } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

// ─── API & Token System ───────────────────────────────────────────────────────
const API_BASE = "http://localhost:8080";

function authHeaders() {
    const token = localStorage.getItem("auth_token");
    return token ? { Authorization: `Bearer ${token}` } : {};
}

async function apiGet(path) {
    const res = await fetch(`${API_BASE}${path}`, { headers: { ...authHeaders() } });
    if (!res.ok)
        throw new Error(`GET ${path} → ${res.status}`);
    return res.json();
}

async function apiPost(path, body) {
    const res = await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeaders() },
        body: JSON.stringify(body),
    });
    if (!res.ok) {
        let m = `POST ${path} → ${res.status}`;
        try {
            const b = await res.json();
            m = b.error || b.message || m;
        }
        catch { }
        throw new Error(m);
    }
    return res.json();
}

async function viewCofDocument(ticketId) {
    try {
        const res = await fetch(`${API_BASE}/api/v1/maintenance/tickets/${ticketId}/certificate-of-fitness/document`, { headers: { ...authHeaders() } });
        if (!res.ok) {
            alert("Could not load the Certificate of Fitness document.");
            return;
        }
        const url = URL.createObjectURL(await res.blob());
        window.open(url, "_blank");
        setTimeout(() => URL.revokeObjectURL(url), 30000);
    }
    catch {
        alert("Could not reach the maintenance service.");
    }
}

// ─── Design Tokens ─────────────────────────────────────────────────────────────
const teal    = "#009688";
const tealDk  = "#00786B";
const tealLt  = "#E0F2F1";
const emerald = "#10B981";
const amber   = "#F59E0B";
const rose    = "#EF4444";
const inkH    = "#0F172A";
const inkB    = "#334155";
const inkM    = "#64748B";
const bd      = "rgba(15,23,42,0.08)";
const shadow  = "0 1px 3px rgba(0,0,0,0.05), 0 1px 2px -1px rgba(0,0,0,0.05)";
const DISPLAY = "'Plus Jakarta Sans', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const SANS    = "'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const MONO    = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace";

function isBatchDateProposal(id) {
    return /^\d{4}-\d{2}-\d{2}$/.test(id);
}

const TYPE_CONFIG = {
    SCHEDULE_PROPOSAL: { label: "Schedule Proposal", color: "#6366F1", bg: "rgba(99,102,241,0.10)", icon: <Calendar size={15} color="#6366F1"/> },
    CERTIFICATE_OF_FITNESS: { label: "Certificate of Fitness", color: "#F59E0B", bg: "rgba(245,158,11,0.10)", icon: <Train size={15} color="#F59E0B"/> },
    MAINTENANCE_DEFECT: { label: "Maintenance Defect", color: "#DC2626", bg: "rgba(220,38,38,0.10)", icon: <Wrench size={15} color="#DC2626"/> },
    FLEET_OVERRIDE: { label: "Fleet Override", color: "#7C3AED", bg: "rgba(124,58,237,0.10)", icon: <ShieldAlert size={15} color="#7C3AED"/> },
    USER_REGISTRATION: { label: "New Registration", color: "#0891B2", bg: "rgba(8,145,178,0.10)", icon: <UserPlus size={15} color="#0891B2"/> },
};

const TRIP_STATUS = {
    PROPOSED: { bg: "rgba(99,102,241,0.10)", color: "#4338CA", dot: "#6366F1", label: "PROPOSED" },
    PLANNED:  { bg: "rgba(16,185,129,0.10)", color: "#065F46", dot: emerald, label: "APPROVED" },
    ACTIVE:   { bg: "rgba(0,150,136,0.10)",  color: tealDk,    dot: teal,    label: "ACTIVE" },
    COMPLETED:{ bg: "rgba(16,185,129,0.10)", color: "#065F46", dot: emerald, label: "COMPLETED" },
    CANCELLED:{ bg: "rgba(239,68,68,0.10)",  color: "#991B1B", dot: rose,    label: "REJECTED" },
    MISSED:   { bg: "rgba(239,68,68,0.10)",  color: "#991B1B", dot: rose,    label: "REJECTED" },
    REFORECAST:{ bg: "rgba(245,158,11,0.10)", color: "#92400E", dot: amber,  label: "REFORECAST" },
};

function TripStatusPill({ status }) {
    const s = TRIP_STATUS[status] ?? TRIP_STATUS.PLANNED;
    const label = s.label || status;
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", gap: 4,
            padding: "2px 7px", borderRadius: 100,
            background: s.bg, color: s.color,
            fontFamily: SANS, fontSize: 10, fontWeight: 700,
        }}>
            <span style={{ width: 5, height: 5, borderRadius: "50%", background: s.dot }}/>
            {label}
        </span>
    );
}

function timeAgo(dateString) {
    if (!dateString) return "";
    const diff = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000);
    if (diff < 60) return "Just now";
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
}

function isToday(dateString) {
    if (!dateString) return false;
    const d = new Date(dateString);
    const now = new Date();
    return d.getFullYear() === now.getFullYear() &&
           d.getMonth() === now.getMonth() &&
           d.getDate() === now.getDate();
}

function SeverityPill({ priority }) {
    const p = String(priority || "MEDIUM").toUpperCase();
    const colors = {
        CRITICAL: { bg: "rgba(239,68,68,0.12)", color: "#DC2626" },
        HIGH:     { bg: "rgba(245,158,11,0.12)", color: "#D97706" },
        MEDIUM:   { bg: "rgba(99,102,241,0.10)", color: "#4F46E5" },
        LOW:      { bg: "rgba(100,116,139,0.10)", color: inkM },
    };
    const c = colors[p] || colors.MEDIUM;
    return (
        <span style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: c.color, background: c.bg, padding: "2px 7px", borderRadius: 100 }}>
            {p}
        </span>
    );
}

function StatCard({ label, value, sub, accent, subColor, caption }) {
    return (
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "20px 24px", boxShadow: shadow }}>
            <span className="text-xs font-bold uppercase tracking-wider block mb-2" style={{ fontFamily: SANS, color: inkM }}>{label}</span>
            <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{value}</span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: subColor || accent || teal }}>{sub}</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>{caption || "Decision history & queue"}</p>
        </div>
    );
}

function ReadOnlyTripRow({ trip, seqNum }) {
    const sc = TRIP_STATUS[trip.status] ?? TRIP_STATUS.PLANNED;
    return (
        <div style={{
            background: "#fff",
            border: `1px solid ${bd}`,
            borderLeft: `4px solid ${sc.dot}`,
            borderRadius: 10, marginBottom: 6,
            overflow: "hidden",
        }}>
            <div style={{ display: "grid", gridTemplateColumns: "32px 120px 1fr auto", alignItems: "center", gap: 10, padding: "9px 14px" }}>
                <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 700, color: inkM, textAlign: "center" }}>
                    #{String(seqNum).padStart(2, "0")}
                </span>
                <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
                    <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 800, color: inkH }}>{trip.startTime}</span>
                    <span style={{ fontFamily: MONO, fontSize: 10, color: inkM }}>↓ {trip.endTime}</span>
                </div>
                <div style={{ minWidth: 0 }}>
                    <div style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkH, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                        {trip.routeName}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 5, marginTop: 2 }}>
                        <span style={{ fontFamily: MONO, fontSize: 10, color: tealDk, background: tealLt, padding: "1px 6px", borderRadius: 5 }}>
                            {trip.assignedTrainId || "Unassigned"}
                        </span>
                        {trip.assignedTrainName && (
                            <span style={{ fontFamily: SANS, fontSize: 9, color: inkM, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis", maxWidth: 120 }}>
                                {trip.assignedTrainName}
                            </span>
                        )}
                    </div>
                </div>
                <div><TripStatusPill status={trip.status}/></div>
            </div>
        </div>
    );
}

function BatchScheduleReview({ serviceDate, busy, onApproveAll, onRejectAll, onRequestChanges }) {
    const [trips, setTrips] = useState([]);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState(null);

    const loadTrips = useCallback(async () => {
        setLoading(true);
        setErr(null);
        try {
            const data = await apiGet(`/api/v1/schedule/trips/date/${serviceDate}`);
            setTrips(data || []);
        } catch (e) {
            setErr(e instanceof Error ? e.message : "Failed to load proposed trips");
        } finally {
            setLoading(false);
        }
    }, [serviceDate]);

    useEffect(() => { loadTrips(); }, [loadTrips]);

    const proposedTrips = trips.filter(t => t.status === "PROPOSED");

    return (
        <div style={{ background: "#F8FAFC", border: `1px solid ${bd}`, borderRadius: 12, overflow: "hidden", marginTop: 12 }}>
            {/* Header */}
            <div style={{ padding: "12px 16px", borderBottom: `1px solid ${bd}`, background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <div>
                    <h4 style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 800, color: inkH, margin: 0 }}>
                        Proposed Timetable — {serviceDate}
                    </h4>
                    <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, margin: "2px 0 0" }}>
                        {trips.length} Total Trips · {proposedTrips.length} Pending Approval
                    </p>
                </div>
            </div>

            {/* Trip Rows */}
            <div style={{ padding: "10px 14px", maxHeight: 380, overflowY: "auto" }}>
                {loading ? (
                    <div style={{ padding: "30px 0", textAlign: "center", fontFamily: SANS, fontSize: 12, color: inkM }}>
                        <Loader2 size={16} className="animate-spin" style={{ margin: "0 auto 6px" }}/> Loading timetable…
                    </div>
                ) : err ? (
                    <p style={{ fontFamily: SANS, fontSize: 12, color: rose, textAlign: "center", margin: "20px 0" }}>{err}</p>
                ) : trips.length === 0 ? (
                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, textAlign: "center", margin: "20px 0" }}>No trips found for {serviceDate}.</p>
                ) : (
                    trips.map((trip, idx) => {
                        const sc = TRIP_STATUS[trip.status] ?? TRIP_STATUS.PLANNED;
                        return (
                            <div key={trip.id || idx} style={{
                                background: "#fff",
                                border: `1px solid ${bd}`,
                                borderLeft: `4px solid ${sc.dot}`,
                                borderRadius: 10, marginBottom: 6, overflow: "hidden",
                            }}>
                                <div style={{ display: "grid", gridTemplateColumns: "32px 120px 1fr auto", alignItems: "center", gap: 10, padding: "9px 14px" }}>
                                    <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 700, color: inkM, textAlign: "center" }}>
                                        #{String(idx + 1).padStart(2, "0")}
                                    </span>
                                    <div style={{ display: "flex", flexDirection: "column", gap: 1 }}>
                                        <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 800, color: inkH }}>{trip.startTime}</span>
                                        <span style={{ fontFamily: MONO, fontSize: 10, color: inkM }}>↓ {trip.endTime}</span>
                                    </div>
                                    <div style={{ minWidth: 0 }}>
                                        <div style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkH, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                                            {trip.routeName}
                                        </div>
                                        <div style={{ display: "flex", alignItems: "center", gap: 5, marginTop: 2 }}>
                                            <span style={{ fontFamily: MONO, fontSize: 10, color: tealDk, background: tealLt, padding: "1px 6px", borderRadius: 5 }}>
                                                {trip.assignedTrainId || "Unassigned"}
                                            </span>
                                        </div>
                                    </div>
                                    <div><TripStatusPill status={trip.status}/></div>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>

            {/* Footer action buttons */}
            <div style={{ padding: "12px 16px", borderTop: `1px solid ${bd}`, background: "#fff", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", justifyContent: "flex-end" }}>
                <span style={{ fontFamily: SANS, fontSize: 11, color: inkM, marginRight: "auto" }}>
                    {proposedTrips.length} proposed trips · {trips.length - proposedTrips.length} already decided
                </span>
                <button onClick={onRejectAll} disabled={busy} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: rose, background: "transparent", border: `1.5px solid ${rose}`, padding: "7px 16px", borderRadius: 100, cursor: busy ? "default" : "pointer", display: "flex", alignItems: "center", gap: 5 }}>
                    <XCircle size={13}/> Reject All
                </button>
                <button onClick={onRequestChanges} disabled={busy} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: "#D97706", background: "rgba(245,158,11,0.08)", border: "1.5px solid #F59E0B", padding: "7px 16px", borderRadius: 100, cursor: busy ? "default" : "pointer", display: "flex", alignItems: "center", gap: 5 }}>
                    <MessageSquare size={13}/> Request Changes
                </button>
                <button onClick={onApproveAll} disabled={busy} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: "#fff", background: emerald, border: "none", padding: "8px 20px", borderRadius: 100, cursor: busy ? "default" : "pointer", display: "flex", alignItems: "center", gap: 5 }}>
                    <CheckCircle size={13}/> Approve All ({proposedTrips.length})
                </button>
            </div>
        </div>
    );
}


function QueueCard({ task, busy, onApprove, onReject, onRequestChanges, showRequestChanges }) {
    const cfg = TYPE_CONFIG[task.requestType] || { label: task.requestType || "Request", color: "#64748B", bg: "rgba(100,116,139,0.10)", icon: <AlertTriangle size={15} color="#64748B"/> };
    const isBatchAI = task.requestType === "SCHEDULE_PROPOSAL" && isBatchDateProposal(task.targetEntityId);
    const [expanded, setExpanded] = useState(false);

    return (
        <div style={{
            background: "#fff", border: `1px solid ${bd}`, borderRadius: 14,
            borderLeft: `4px solid ${cfg.color}`, overflow: "hidden",
            opacity: busy ? 0.65 : 1, transition: "box-shadow 0.15s",
        }} className="hover:shadow-md">
            <div style={{ display: "flex", gap: 0 }}>
                <div style={{ width: 52, display: "flex", alignItems: "flex-start", justifyContent: "center", paddingTop: 18, flexShrink: 0 }}>
                    <div style={{ width: 32, height: 32, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                        {cfg.icon}
                    </div>
                </div>

                <div style={{ flex: 1, padding: "14px 4px 14px 0" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
                        <span style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: cfg.color, background: cfg.bg, padding: "2px 8px", borderRadius: 100 }}>
                            {cfg.label.toUpperCase()}
                        </span>
                        {isBatchAI && (
                            <span style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: "#059669", background: "rgba(5,150,105,0.10)", padding: "2px 8px", borderRadius: 100 }}>
                                AI FULL-DAY
                            </span>
                        )}
                    </div>

                    <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH, marginBottom: 6 }}>{task.title}</p>
                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkB, lineHeight: 1.6, marginBottom: 10 }}>{task.description}</p>

                    {task.requestType === "CERTIFICATE_OF_FITNESS" && (
                        <div style={{ marginBottom: 10 }}>
                            <button
                                type="button"
                                onClick={() => viewCofDocument(task.targetEntityId)}
                                style={{
                                    display: "inline-flex", alignItems: "center", gap: 6,
                                    padding: "6px 12px", borderRadius: 8,
                                    background: "rgba(0,150,136,0.08)", border: `1px solid rgba(0,150,136,0.25)`,
                                    color: tealDk, fontFamily: SANS, fontSize: 12, fontWeight: 600,
                                    cursor: "pointer",
                                }}
                            >
                                <FileText size={13} color={teal}/> View Submitted CoF Document
                            </button>
                        </div>
                    )}

                    {isBatchAI && (
                        <div style={{ marginBottom: 10 }}>
                            <button
                                type="button"
                                onClick={() => setExpanded(e => !e)}
                                style={{
                                    display: "inline-flex", alignItems: "center", gap: 6,
                                    padding: "5px 12px", borderRadius: 8,
                                    background: expanded ? "rgba(99,102,241,0.10)" : "rgba(15,23,42,0.04)",
                                    border: `1px solid ${expanded ? "rgba(99,102,241,0.30)" : bd}`,
                                    color: expanded ? "#4338CA" : inkB, fontFamily: SANS, fontSize: 11.5, fontWeight: 600,
                                    cursor: "pointer",
                                }}
                            >
                                <Calendar size={12} color={expanded ? "#6366F1" : inkM}/>
                                {expanded ? "Hide Proposed Timetable" : "Review Proposed Timetable"}
                                {expanded ? <ChevronUp size={12}/> : <ChevronDown size={12}/>}
                            </button>
                            {expanded && (
                                <BatchScheduleReview
                                    serviceDate={task.targetEntityId}
                                    busy={busy}
                                    onApproveAll={onApprove}
                                    onRejectAll={onReject}
                                    onRequestChanges={onRequestChanges}
                                />
                            )}
                        </div>
                    )}

                    <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", paddingTop: 4 }}>
                        <span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>
                            Requested by: <strong style={{ color: inkB }}>{task.requestedBy}</strong>
                        </span>
                        {task.createdAt && (
                            <span style={{ fontFamily: SANS, fontSize: 11, color: inkM, display: "flex", alignItems: "center", gap: 4 }}>
                                <Clock size={11}/> {timeAgo(task.createdAt)}
                            </span>
                        )}
                    </div>
                </div>

                {!isBatchAI && (
                    <div style={{ display: "flex", flexDirection: "column", gap: 8, padding: "14px 16px 14px 8px", justifyContent: "center", flexShrink: 0 }}>
                        <button onClick={onApprove} disabled={busy} style={{
                            fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: "#fff",
                            background: emerald, border: "none", borderRadius: 9,
                            padding: "8px 18px", cursor: busy ? "default" : "pointer",
                            display: "flex", alignItems: "center", gap: 6,
                        }}>
                            {busy ? <Loader2 size={13} className="animate-spin"/> : <CheckCircle size={13}/>}
                            Approve
                        </button>
                        {showRequestChanges && (
                            <button onClick={onRequestChanges} disabled={busy} style={{
                                fontFamily: SANS, fontSize: 11.5, fontWeight: 600, color: "#D97706",
                                background: "rgba(245,158,11,0.08)", border: "1px solid rgba(245,158,11,0.30)", borderRadius: 9,
                                padding: "6px 14px", cursor: busy ? "default" : "pointer",
                                display: "flex", alignItems: "center", gap: 5,
                            }}>
                                <MessageSquare size={12}/> Request Changes
                            </button>
                        )}
                        <button onClick={onReject} disabled={busy} style={{
                            fontFamily: SANS, fontSize: 11.5, fontWeight: 600, color: rose,
                            background: "rgba(239,68,68,0.06)", border: "1px solid rgba(239,68,68,0.20)", borderRadius: 9,
                            padding: "6px 14px", cursor: busy ? "default" : "pointer",
                            display: "flex", alignItems: "center", gap: 5,
                        }}>
                            <XCircle size={12}/> Reject
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

function DecisionModal({ task, mode, onClose, onSubmit }) {
    const isRequestChanges = mode === "REQUEST_CHANGES";
    const [comments, setComments] = useState("");
    const [submitting, setSubmitting] = useState(false);

    async function handleSend() {
        if (!comments.trim()) {
            alert(isRequestChanges ? "Please provide instructions for the change request." : "Please provide a rejection reason.");
            return;
        }
        setSubmitting(true);
        await onSubmit({
            comments: comments.trim(),
        });
        setSubmitting(false);
    }

    return (
        <div style={{
            position: "fixed", top: 0, left: 0, right: 0, bottom: 0, zIndex: 99999,
            background: "rgba(15,23,42,0.55)", backdropFilter: "blur(4px)",
            display: "flex", alignItems: "center", justifyContent: "center", padding: 16,
        }} onClick={onClose}>
            <div style={{
                background: "#ffffff", borderRadius: 14, width: "100%", maxWidth: 450,
                boxShadow: "0 25px 60px rgba(15,23,42,0.30)", overflow: "hidden",
                border: `1px solid ${bd}`,
            }} onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div style={{ padding: "14px 18px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between", background: isRequestChanges ? "#FFFBEB" : "#FEF2F2" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        {isRequestChanges ? <MessageSquare size={16} color="#D97706"/> : <XCircle size={16} color="#DC2626"/>}
                        <div>
                            <h3 style={{ fontFamily: DISPLAY, fontSize: 14.5, fontWeight: 800, color: inkH, margin: 0 }}>
                                {isRequestChanges ? "Request Modifications" : "Confirm Rejection"}
                            </h3>
                            <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, margin: "1px 0 0" }}>
                                {task.title}
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", padding: 4, color: inkM }}><X size={15}/></button>
                </div>

                {/* Body */}
                <div style={{ padding: "14px 18px", display: "flex", flexDirection: "column", gap: 8 }}>
                    <label style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                        <span style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkB }}>
                            {isRequestChanges ? "Feedback & Modification Instructions" : "Reason for Rejection"}
                        </span>
                        <textarea
                            value={comments}
                            onChange={e => setComments(e.target.value)}
                            rows={3}
                            autoFocus
                            placeholder={isRequestChanges ? "Enter requested modifications…" : "Enter rejection reason…"}
                            style={{
                                fontFamily: SANS, fontSize: 12.5, padding: "8px 10px", borderRadius: 8,
                                border: `1.5px solid ${bd}`, outline: "none", resize: "none", color: inkH, lineHeight: 1.4,
                                width: "100%", boxSizing: "border-box",
                            }}
                        />
                    </label>
                </div>

                {/* Footer */}
                <div style={{ padding: "10px 18px", background: "#F8FAFC", borderTop: `1px solid ${bd}`, display: "flex", gap: 8, justifyContent: "flex-end" }}>
                    <button onClick={onClose} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB, background: "#fff", border: `1px solid ${bd}`, borderRadius: 8, padding: "6px 14px", cursor: "pointer" }}>
                        Cancel
                    </button>
                    <button onClick={handleSend} disabled={submitting} style={{
                        fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: "#fff",
                        background: isRequestChanges ? "#D97706" : "#DC2626", border: "none", borderRadius: 8, padding: "6px 16px", cursor: submitting ? "default" : "pointer",
                        display: "flex", alignItems: "center", gap: 5,
                    }}>
                        {submitting ? <Loader2 size={12} className="animate-spin"/> : isRequestChanges ? <MessageSquare size={12}/> : <XCircle size={12}/>}
                        {isRequestChanges ? "Submit Request" : "Confirm Rejection"}
                    </button>
                </div>
            </div>
        </div>
    );
}

function HistoryTable({ rows, totalCount, showAll, onToggleShowAll, previewLimit }) {
    return (
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, overflow: "hidden" }}>
            <div style={{ padding: "16px 20px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <h2 style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH }}>Approval History</h2>
                {totalCount > previewLimit && (
                    <button
                        onClick={onToggleShowAll}
                        style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer" }}
                    >
                        {showAll ? "Show Less" : `Show All (${totalCount})`}
                    </button>
                )}
            </div>
            <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                        <tr style={{ background: "#F8FAFC" }}>
                            {["Decision", "Approver", "Comments & Feedback", "Timestamp"].map(col => (
                                <th key={col} style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: inkM, textTransform: "uppercase", letterSpacing: "0.07em", padding: "10px 16px", textAlign: "left", borderBottom: `1px solid ${bd}` }}>{col}</th>
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {rows.length === 0 ? (
                            <tr><td colSpan={4} style={{ padding: "20px 16px", fontFamily: SANS, fontSize: 12, color: inkM, textAlign: "center" }}>No decisions logged yet.</td></tr>
                        ) : (
                            rows.map((row, i) => (
                                <tr key={(row.taskId || "") + (row.timestamp || i)} style={{ borderBottom: i < rows.length - 1 ? `1px solid ${bd}` : "none" }}>
                                    <td style={{ padding: "12px 16px" }}>
                                        {row.decision === "APPROVED" ? (
                                            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: "#059669", display: "flex", alignItems: "center", gap: 5 }}><CheckCircle size={12}/> APPROVED</span>
                                        ) : row.decision === "CHANGES_REQUESTED" ? (
                                            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: "#D97706", display: "flex", alignItems: "center", gap: 5 }}><MessageSquare size={12}/> CHANGES REQUESTED</span>
                                        ) : (
                                            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: "#DC2626", display: "flex", alignItems: "center", gap: 5 }}><XCircle size={12}/> REJECTED</span>
                                        )}
                                    </td>
                                    <td style={{ padding: "12px 16px" }}><span style={{ fontFamily: SANS, fontSize: 12, color: inkB }}>{row.approverUsername}</span></td>
                                    <td style={{ padding: "12px 16px", maxWidth: 360 }}><span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>{row.comments || "—"}</span></td>
                                    <td style={{ padding: "12px 16px" }}><span style={{ fontFamily: MONO, fontSize: 11, color: inkM }}>{new Date(row.timestamp).toLocaleString()}</span></td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

function EmptyState() {
    return (
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "60px 24px", textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: "rgba(16,185,129,0.10)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
                <CheckCircle size={28} color={emerald}/>
            </div>
            <h3 style={{ fontFamily: DISPLAY, fontSize: 18, fontWeight: 700, color: inkH, marginBottom: 8 }}>Queue Clear</h3>
            <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, maxWidth: 340, margin: "0 auto", lineHeight: 1.6 }}>No pending approvals. New requests will appear here automatically.</p>
        </div>
    );
}

function ErrorState({ message, onRetry }) {
    return (
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "48px 24px", textAlign: "center" }}>
            <div style={{ width: 56, height: 56, borderRadius: 16, background: "rgba(239,68,68,0.10)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
                <AlertTriangle size={28} color="#DC2626"/>
            </div>
            <h3 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH, marginBottom: 8 }}>Could Not Fetch Approvals</h3>
            <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, maxWidth: 360, margin: "0 auto 16px" }}>{message}</p>
            <button onClick={onRetry} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: "#fff", background: teal, padding: "8px 18px", borderRadius: 100, border: "none", cursor: "pointer" }}>Retry</button>
        </div>
    );
}

function RestrictedView({ isSignedIn, onNavigate, onLogOut, userName, userRole }) {
    return (
        <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
            <SharedHeader activePage="approver" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>
            <main style={{ flex: 1, maxWidth: 1380, width: "100%", margin: "0 auto", padding: "40px 32px" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: 320 }}>
                    <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, padding: "48px 40px", maxWidth: 440, textAlign: "center" }}>
                        <div style={{ width: 56, height: 56, borderRadius: 16, background: "rgba(0,150,136,0.08)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 20px" }}>
                            <Lock size={26} color={teal}/>
                        </div>
                        <h2 style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: inkH, marginBottom: 12 }}>Approver Authority Required</h2>
                        <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, lineHeight: 1.65, marginBottom: 24 }}>
                            Approvals are managed exclusively by Senior Approver Duty Authority (SADA) for operational requests and System Administrators for user registrations.
                        </p>
                        <button onClick={() => onNavigate("dashboard")} style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 700, color: "#fff", background: teal, padding: "10px 24px", borderRadius: 12, border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: 6, margin: "0 auto" }}>
                            <ArrowLeft size={14}/> Return to Dashboard
                        </button>
                    </div>
                </div>
            </main>
            <SharedFooter onNavigate={onNavigate}/>
        </div>
    );
}

export default function ApproverPanelPage({ isSignedIn, userRole, userName, onNavigate, onLogOut }) {
    const roleClean = String(userRole || "").toUpperCase().trim();
    const isAdmin = roleClean.includes("ADMIN");
    const isSada = roleClean.includes("SADA") || roleClean.includes("APPROVER");
    const isAuthorized = isAdmin || isSada;

    const [queue, setQueue] = useState(() => getCached("approver_queue", []));
    const [history, setHistory] = useState(() => getCached("approver_history", []));
    const [showAllHistory, setShowAllHistory] = useState(false);
    const HISTORY_PREVIEW_LIMIT = 6;
    const [activeFilter, setFilter] = useState("All");
    const [loading, setLoading] = useState(() => !getCached("approver_queue"));
    const [error, setError] = useState(null);
    const [busyTaskId, setBusy] = useState(null);
    const [decisionModalTask, setDecisionModalTask] = useState(null);

    const loadAll = useCallback(async () => {
        const hasCache = Boolean(getCached("approver_queue"));
        if (!hasCache) setLoading(true);
        setError(null);
        try {
            const [pending, hist] = await Promise.all([
                apiGet("/api/approver/tasks/pending"),
                apiGet("/api/approver/history"),
            ]);
            const pendingList = pending || [];
            const sortedHistory = [...(hist || [])].sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
            setQueue(pendingList);
            setHistory(sortedHistory);
            setCached("approver_queue", pendingList);
            setCached("approver_history", sortedHistory);
        } catch (e) {
            if (!hasCache) {
                setError(e instanceof Error ? e.message : "Unknown error");
            }
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!isSignedIn || !isAuthorized) return;
        loadAll();
        const iv = setInterval(loadAll, 6000);
        return () => clearInterval(iv);
    }, [isSignedIn, isAuthorized, loadAll]);

    const roleFilteredQueue = useMemo(() => {
        if (isAdmin) {
            return queue.filter(t => t.requestType === "USER_REGISTRATION" || String(t.title || "").toUpperCase().includes("REGISTRATION"));
        }
        if (isSada) {
            return queue.filter(t => t.requestType !== "USER_REGISTRATION" && !String(t.title || "").toUpperCase().includes("REGISTRATION"));
        }
        return queue;
    }, [queue, isAdmin, isSada]);

    const availableTypes = useMemo(() => Array.from(new Set(roleFilteredQueue.map(t => t.requestType))), [roleFilteredQueue]);
    const visible = useMemo(() => activeFilter === "All" ? roleFilteredQueue : roleFilteredQueue.filter(t => t.requestType === activeFilter), [roleFilteredQueue, activeFilter]);

    const roleFilteredHistory = useMemo(() => {
        if (isAdmin) {
            return history.filter(h => h.requestType === "USER_REGISTRATION" || String(h.title || "").toUpperCase().includes("REGISTRATION") || String(h.title || "").toUpperCase().includes("USER"));
        }
        if (isSada) {
            return history.filter(h => h.requestType !== "USER_REGISTRATION" && !String(h.title || "").toUpperCase().includes("REGISTRATION") && !String(h.title || "").toUpperCase().includes("USER"));
        }
        return history;
    }, [history, isAdmin, isSada]);

    const approvedToday = useMemo(() => roleFilteredHistory.filter(h => h.decision === "APPROVED" && isToday(h.timestamp)).length, [roleFilteredHistory]);
    const rejectedToday = useMemo(() => roleFilteredHistory.filter(h => (h.decision === "REJECTED" || h.decision === "CHANGES_REQUESTED") && isToday(h.timestamp)).length, [roleFilteredHistory]);

    async function decide(task, decision, options) {
        setBusy(task.taskId);
        const comments = options?.comments || null;
        try {
            await apiPost(`/api/approver/tasks/${task.taskId}/decision`, { decision, comments });

            // Direct sync with schedule-service for schedule proposals
            if (task.requestType === "SCHEDULE_PROPOSAL" && task.targetEntityId) {
                const isDate = /^\d{4}-\d{2}-\d{2}$/.test(task.targetEntityId);
                if (isDate) {
                    if (decision === "APPROVED") {
                        try {
                            await apiPost(`/api/v1/schedule/approve-day/${task.targetEntityId}`, {});
                        } catch (err) {
                            console.warn("Direct schedule sync note:", err);
                        }
                    } else if (decision === "REJECTED") {
                        try {
                            await apiPost(`/api/v1/schedule/reject-day/${task.targetEntityId}`, {});
                        } catch (err) {
                            console.warn("Direct schedule sync note:", err);
                        }
                    }
                    // For CHANGES_REQUESTED, do not reject-day so that trips remain editable and not marked REJECTED in schedule-service.
                }
            }

            // Direct sync with maintenance-service for Certificate of Fitness
            if (task.requestType === "CERTIFICATE_OF_FITNESS" && task.targetEntityId) {
                const targetStatus = decision === "APPROVED" ? "COMPLETED" : "IN_PROGRESS";
                try {
                    const token = getAuthToken();
                    await fetch(`http://localhost:8080/api/v1/maintenance/tickets/${task.targetEntityId}/status`, {
                        method: "PATCH",
                        headers: {
                            "Content-Type": "application/json",
                            ...(token ? { Authorization: `Bearer ${token}` } : {})
                        },
                        body: JSON.stringify({ status: targetStatus, comments: comments || "SADA Decision" })
                    });
                } catch (err) {
                    console.warn("Direct maintenance sync note:", err);
                }
            }

            await loadAll();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Decision failed");
        } finally {
            setBusy(null);
        }
    }

    if (!isSignedIn || !isAuthorized) {
        return <RestrictedView isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>;
    }

    return (
        <div className="page-transition" style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
            <SharedHeader activePage="approver" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

            {/* Page Header Bar */}
            <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
                <div style={{ maxWidth: 1380, width: "100%", margin: "0 auto", padding: "24px 32px", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 16 }}>
                    <div>
                        <h1 style={{ fontFamily: DISPLAY, fontSize: 22, fontWeight: 800, color: inkH, margin: 0 }}>
                            {isAdmin ? "User Registration & Access Approvals" : "Operational Approvals Console"}
                        </h1>
                        <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, marginTop: 4, margin: 0 }}>
                            {isAdmin
                                ? "MetroMind KMRL Administration · New Account Sign-Up & Email Governance Verification"
                                : "MetroMind KMRL Operations Control · SADA Schedule Timetable & Certificate of Fitness Authorization"}
                        </p>
                    </div>
                </div>
            </div>

            <main style={{ flex: 1, maxWidth: 1380, margin: "0 auto", width: "100%", padding: "24px 32px 40px" }}>
                {/* Stats */}
                <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 28 }} className="usr-stats">
                    <StatCard label="Pending in Queue" value={String(roleFilteredQueue.length)} sub="awaiting action" accent={roleFilteredQueue.length > 0 ? amber : emerald} subColor={roleFilteredQueue.length > 0 ? amber : emerald} caption="Requests awaiting review" />
                    <StatCard label="Approved Today" value={String(approvedToday)} sub="decision ledger" accent={emerald} subColor={emerald} caption="Authorizations granted" />
                    <StatCard label="Rejected / Modified" value={String(rejectedToday)} sub="decision ledger" accent={rose} subColor={rose} caption="Flagged or returned requests" />
                </div>

                {/* Filters */}
                {availableTypes.length > 1 && (
                    <div style={{ display: "flex", alignItems: "center", gap: 6, flexWrap: "wrap", marginBottom: 20 }}>
                        {["All", ...availableTypes].map(key => {
                            const active = activeFilter === key;
                            const count = key === "All" ? roleFilteredQueue.length : roleFilteredQueue.filter(t => t.requestType === key).length;
                            const label = key === "All" ? "All" : TYPE_CONFIG[key]?.label || key;
                            return (
                                <button key={key} onClick={() => setFilter(key)} style={{
                                    fontFamily: SANS, fontSize: 12, fontWeight: active ? 700 : 500,
                                    padding: "6px 14px", borderRadius: 100,
                                    border: active ? "none" : `1px solid ${bd}`,
                                    background: active ? teal : "#fff", color: active ? "#fff" : inkB,
                                    cursor: "pointer", transition: "all 0.15s",
                                    display: "flex", alignItems: "center", gap: 6,
                                }}>
                                    {label}
                                    <span style={{ background: active ? "rgba(255,255,255,0.25)" : "rgba(15,23,42,0.07)", color: active ? "#fff" : inkM, borderRadius: 100, padding: "0px 6px", fontSize: 10, fontWeight: 700 }}>
                                        {count}
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                )}

                {/* Queue */}
                <div style={{ display: "flex", flexDirection: "column", gap: 12, marginBottom: 36 }}>
                    {loading ? (
                        <div style={{ padding: "60px 0", textAlign: "center", fontFamily: SANS, fontSize: 13, color: inkM }}>Loading approvals…</div>
                    ) : error ? (
                        <ErrorState message={error} onRetry={loadAll}/>
                    ) : visible.length === 0 ? (
                        <EmptyState />
                    ) : (
                        visible.map(task => (
                            <QueueCard
                                key={task.taskId}
                                task={task}
                                busy={busyTaskId === task.taskId}
                                showRequestChanges={isSada}
                                onApprove={() => decide(task, "APPROVED")}
                                onReject={() => setDecisionModalTask({ task, mode: "REJECT" })}
                                onRequestChanges={() => setDecisionModalTask({ task, mode: "REQUEST_CHANGES" })}
                            />
                        ))
                    )}
                </div>

                <HistoryTable
                    rows={showAllHistory ? roleFilteredHistory : roleFilteredHistory.slice(0, HISTORY_PREVIEW_LIMIT)}
                    totalCount={roleFilteredHistory.length}
                    showAll={showAllHistory}
                    onToggleShowAll={() => setShowAllHistory(v => !v)}
                    previewLimit={HISTORY_PREVIEW_LIMIT}
                />
            </main>

            <SharedFooter onNavigate={onNavigate}/>

            {/* Custom Decision / Request Changes Modal */}
            {decisionModalTask && (
                <DecisionModal
                    task={decisionModalTask.task}
                    mode={decisionModalTask.mode}
                    onClose={() => setDecisionModalTask(null)}
                    onSubmit={async (data) => {
                        const { task, mode } = decisionModalTask;
                        setDecisionModalTask(null);
                        const decisionType = mode === "REQUEST_CHANGES" ? "CHANGES_REQUESTED" : "REJECTED";
                        await decide(task, decisionType, {
                            comments: data.comments,
                        });
                    }}
                />
            )}
        </div>
    );
}
