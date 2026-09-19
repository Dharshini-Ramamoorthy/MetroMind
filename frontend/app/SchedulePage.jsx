import { useState, useRef, useEffect, useCallback, useMemo } from "react";
import { CheckCircle, Plus, X, ChevronDown, Train, RefreshCw, Clock, Calendar, Trash2, Loader2, LayoutGrid, List, AlertCircle, Info, Zap, Activity, ShieldCheck, Cpu, Send, AlertTriangle } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";
// ─── Enterprise Design Tokens (KMRL Light Theme) ─────────────────────────────
const teal = "#009688";
const tealDark = "#00786B";
const tealLight = "#E0F2F1";
const emerald = "#10B981";
const amber = "#F59E0B";
const rose = "#EF4444";
const indigo = "#6366F1";
const heading = "#0F172A";
const body = "#334155";
const muted = "#64748B";
const border = "rgba(15,23,42,0.08)";
const bgPage = "#F8FAFC";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
// ─── API Base ─────────────────────────────────────────────────────────────────
const API_BASE = "http://localhost:8080/api/v1";
function getAuthToken() {
    return localStorage.getItem("auth_token")
        || localStorage.getItem("token")
        || localStorage.getItem("jwt_token")
        || localStorage.getItem("kmrl_token")
        || "";
}
async function apiFetch(path, options) {
    const token = getAuthToken();
    let res;
    try {
        res = await fetch(`${API_BASE}${path}`, {
            headers: {
                "Content-Type": "application/json",
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            ...options,
        });
    }
    catch {
        throw new Error("Unable to connect to schedule service.");
    }
    if (!res.ok) {
        if (res.status === 401 || res.status === 403) {
            throw new Error("Session expired or unauthorized role. Please sign out and sign in again as admin.");
        }
        let msg = `Request failed (${res.status})`;
        try {
            const b = await res.json();
            msg = b.detail || b.message || b.error || msg;
        }
        catch { }
        throw new Error(msg);
    }
    if (res.status === 204)
        return undefined;
    return res.json();
}
// ─── Constants ────────────────────────────────────────────────────────────────
const SERVICE_START_MIN = 5 * 60; // 05:00 AM (300)
const SERVICE_END_MIN = 23 * 60; // 11:00 PM (1380)
const FULL_DAY_MINUTES = SERVICE_END_MIN - SERVICE_START_MIN; // 1080 min
function todayIso() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
function parseTimeToMinutes(tStr) {
    if (!tStr)
        return 0;
    const parts = tStr.trim().split(" ");
    if (parts.length < 2)
        return 0;
    const [h, m] = parts[0].split(":").map(Number);
    const ampm = parts[1].toUpperCase();
    let hours = h % 12;
    if (ampm === "PM")
        hours += 12;
    return hours * 60 + (m || 0);
}
function durationLabel(startM, endM) {
    const d = Math.max(1, endM - startM);
    return d >= 60 ? `${Math.floor(d / 60)}h ${d % 60}m` : `${d}m`;
}
// Crisp Emerald Green Card Variant Styles matching KMRL signature design system
function getVariantStyle(status, isReforecast = false) {
    if (status === "CANCELLED" || status === "REJECTED")
        return { bg: "#FEF2F2", text: "#DC2626", border: "#FECACA", dot: "#DC2626" };
    if (status === "CHANGES_REQUESTED" || status === "REQUEST_CHANGES" || status === "NEEDS_REVISION")
        return { bg: "#FFFBEB", text: "#B45309", border: "#FDE68A", dot: "#F59E0B" };
    if (status === "DELAYED")
        return { bg: "#FFFBEB", text: "#92400E", border: "#FDE68A", dot: amber };
    if (status === "PROPOSED")
        return { bg: "#EFF6FF", text: "#2563EB", border: "#BFDBFE", dot: "#3B82F6" };
    return { bg: "#ECFDF5", text: "#047857", border: "#A7F3D0", dot: emerald };
}
function StatusBadge({ status, source }) {
    const isReforecast = status === "REFORECAST";
    const isChanges = status === "CHANGES_REQUESTED" || status === "REQUEST_CHANGES" || status === "NEEDS_REVISION";
    const displayStatus = (status === "CANCELLED" || status === "REJECTED")
        ? "REJECTED"
        : isChanges
        ? "CHANGES REQUESTED"
        : status === "MISSED"
        ? "PROPOSED"
        : (status || "PLANNED").replace(/_/g, " ");
    const vs = getVariantStyle(status, isReforecast);
    return (<span style={{
            fontFamily: SANS, fontSize: 8.5, fontWeight: 800, letterSpacing: "0.05em",
            textTransform: "uppercase", color: vs.text,
            background: "rgba(255,255,255,0.95)",
            border: `1px solid ${vs.border}`, borderRadius: 100, padding: "2px 7px",
            display: "inline-flex", alignItems: "center", gap: 4, whiteSpace: "nowrap",
        }}>
      <span style={{ width: 4, height: 4, borderRadius: "50%", background: vs.dot }}/>
      {displayStatus}
    </span>);
}
// ─── Vertical Train Column Gantt Block ────────────────────────────────────────
function VerticalGanttBlock({ trip, isToday, windowStartMin, windowTotalMin, canAdjust, onAdjust, onDelete, onNavigate, }) {
    const [popover, setPopover] = useState(false);
    const ref = useRef(null);
    const isReforecast = trip.source === "AI_REFORECAST";
    const startM = parseTimeToMinutes(trip.startTime) || trip.startMinutes || SERVICE_START_MIN;
    const endM = parseTimeToMinutes(trip.endTime) || trip.endMinutes || (startM + 45);
    const nowMin = useMemo(() => {
        const n = new Date();
        return n.getHours() * 60 + n.getMinutes();
    }, []);
    const effectiveStatus = trip.status || "PLANNED";
    const vs = getVariantStyle(effectiveStatus, isReforecast);
    const relStartM = startM - windowStartMin;
    const relEndM = endM - windowStartMin;
    if (relEndM <= 0 || relStartM >= windowTotalMin)
        return null;
    // Optimized grid height (1620px = 1.5px per minute) for perfect spacing and zero text cut-off!
    const topPx = Math.max(0, Math.min(1560, (relStartM / windowTotalMin) * 1620));
    const blockHeight = Math.max(68, Math.min(180, Math.round(((endM - startM) / windowTotalMin) * 1620)));
    useEffect(() => {
        if (!popover)
            return;
        function handler(e) {
            if (ref.current && !ref.current.contains(e.target))
                setPopover(false);
        }
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, [popover]);
    const canDelete = canAdjust && (trip.status === "PROPOSED" || trip.status === "CHANGES_REQUESTED");
    const canEdit = canAdjust && (trip.status === "PROPOSED" || trip.status === "PLANNED" || trip.status === "CHANGES_REQUESTED");
    return (<div ref={ref} style={{ position: "absolute", top: topPx, height: blockHeight, left: "4%", width: "92%", zIndex: popover ? 40 : 10 }}>
      <button onClick={() => setPopover(p => !p)} style={{
            width: "100%", height: "100%", borderRadius: 10,
            background: vs.bg,
            border: `1.5px solid ${vs.border}`,
            padding: "6px 8px",
            display: "flex", flexDirection: "column", justifyContent: "space-between",
            textAlign: "left", cursor: "pointer", outline: "none",
            boxShadow: "0 2px 6px rgba(4,120,87,0.06)",
            overflow: "hidden", transition: "transform 0.12s, box-shadow 0.12s",
        }} className="hover:scale-[1.01] hover:shadow-md" title={`${trip.tripCode}: ${trip.startTime} - ${trip.endTime}`}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 4 }}>
          <span style={{ fontFamily: DISPLAY, fontSize: 12, fontWeight: 800, color: vs.text, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
            {trip.tripCode}
          </span>
          <ChevronDown size={12} style={{ color: vs.text, transform: popover ? "rotate(180deg)" : "rotate(0deg)", transition: "transform 0.15s", flexShrink: 0 }}/>
        </div>
        <div style={{ fontFamily: MONO, fontSize: 10.5, fontWeight: 800, color: vs.text, letterSpacing: "-0.01em", whiteSpace: "nowrap", margin: "2px 0" }}>
          {trip.startTime} – {trip.endTime}
        </div>
        <div style={{ display: "flex", alignItems: "center" }}>
          <StatusBadge status={effectiveStatus} source={trip.source}/>
        </div>
      </button>

      {popover && (<div style={{
                position: "absolute", top: "105%", left: "50%", transform: "translateX(-50%)",
                width: 310, background: "#ffffff", border: `1px solid ${border}`,
                borderRadius: 14, boxShadow: "0 16px 48px rgba(15,23,42,0.20)",
                padding: "14px 16px", zIndex: 50, cursor: "default",
            }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", borderBottom: `1px solid ${border}`, paddingBottom: 8, marginBottom: 10 }}>
            <div>
              <span style={{ fontFamily: DISPLAY, fontSize: 13, fontWeight: 800, color: heading }}>{trip.tripCode}</span>
              <span style={{ fontFamily: SANS, fontSize: 11, color: muted, marginLeft: 6 }}>{durationLabel(startM, endM)}</span>
            </div>
            <StatusBadge status={effectiveStatus} source={trip.source}/>
          </div>



          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, fontFamily: SANS }}>
              <span style={{ color: muted }}>Route Corridor:</span>
              <strong style={{ color: heading }}>{trip.routeName}</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, fontFamily: SANS }}>
              <span style={{ color: muted }}>Timing:</span>
              <strong style={{ fontFamily: MONO, color: heading }}>{trip.startTime} – {trip.endTime}</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, fontFamily: SANS }}>
              <span style={{ color: muted }}>Assigned Train:</span>
              <strong style={{ fontFamily: MONO, color: tealDark }}>{trip.assignedTrainName ? `${trip.assignedTrainName} (${trip.assignedTrainId})` : "Unassigned"}</strong>
            </div>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <button onClick={() => onNavigate("fleet")} style={{
                width: "100%", fontFamily: SANS, fontSize: 11, fontWeight: 700,
                color: tealDark, background: tealLight, border: "none", borderRadius: 8,
                padding: "7px 0", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6,
            }}>
              View Fleet Telemetry →
            </button>
            {canEdit && (<button onClick={() => { setPopover(false); onAdjust(trip); }} style={{
                    width: "100%", fontFamily: SANS, fontSize: 11, fontWeight: 700,
                    color: "#fff", background: indigo, border: "none", borderRadius: 8,
                    padding: "7px 0", cursor: "pointer",
                }}>
                Modify Schedule
              </button>)}
            {canDelete && (<button onClick={() => { setPopover(false); onDelete(trip); }} style={{
                    width: "100%", fontFamily: SANS, fontSize: 11, fontWeight: 700,
                    color: rose, background: "#FEF2F2", border: `1px solid #FECACA`, borderRadius: 8,
                    padding: "6px 0", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 4,
                }}>
                <Trash2 size={11}/> Remove Trip
              </button>)}
          </div>
        </div>)}
    </div>);
}
// ─── Service Band List View ───────────────────────────────────────────────────
function ServiceBandList({ trips, canAdjust, onAdjust, onDelete }) {
    const bands = [
        { label: "Early Morning Service (05:00 – 08:00)", trips: trips.filter(t => (t.startMinutes || parseTimeToMinutes(t.startTime)) < 8 * 60) },
        { label: "Morning Peak Window (08:00 – 10:00)", trips: trips.filter(t => { const m = t.startMinutes || parseTimeToMinutes(t.startTime); return m >= 8 * 60 && m < 10 * 60; }) },
        { label: "Midday Off-Peak (10:00 – 17:00)", trips: trips.filter(t => { const m = t.startMinutes || parseTimeToMinutes(t.startTime); return m >= 10 * 60 && m < 17 * 60; }) },
        { label: "Evening Peak Window (17:00 – 20:00)", trips: trips.filter(t => { const m = t.startMinutes || parseTimeToMinutes(t.startTime); return m >= 17 * 60 && m < 20 * 60; }) },
        { label: "Late Evening Service (20:00 – 23:00)", trips: trips.filter(t => (t.startMinutes || parseTimeToMinutes(t.startTime)) >= 20 * 60) },
    ].filter(b => b.trips.length > 0);
    return (<div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      {bands.map((band, bi) => (<div key={bi} style={{ background: "#fff", border: `1px solid ${border}`, borderRadius: 12, overflow: "hidden" }}>
          <div style={{ background: "#F8FAFC", borderBottom: `1px solid ${border}`, padding: "12px 16px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <span style={{ fontFamily: DISPLAY, fontSize: 12, fontWeight: 800, color: heading }}>{band.label}</span>
            <span style={{ fontFamily: MONO, fontSize: 11, color: muted }}>{band.trips.length} runs</span>
          </div>
          <div style={{ padding: "8px 12px" }}>
            {band.trips.map(trip => {
                return (<div key={trip.id} style={{ display: "grid", gridTemplateColumns: "100px 140px 1fr 140px 100px 80px", alignItems: "center", gap: 10, padding: "8px 10px", borderBottom: `1px solid ${border}`, borderRadius: 8 }} className="hover:bg-slate-50">
                  <span style={{ fontFamily: MONO, fontSize: 12, fontWeight: 800, color: heading }}>{trip.tripCode}</span>
                  <span style={{ fontFamily: MONO, fontSize: 11, color: muted }}>{trip.startTime} – {trip.endTime}</span>
                  <span style={{ fontFamily: SANS, fontSize: 11, color: body }} className="truncate">{trip.routeName}</span>
                  <span style={{ fontFamily: MONO, fontSize: 11, fontWeight: 700, color: tealDark, background: tealLight, padding: "2px 8px", borderRadius: 6, width: "fit-content" }}>
                    {trip.assignedTrainId || "Unassigned"}
                  </span>
                  <div><StatusBadge status={trip.status}/></div>
                  <div style={{ display: "flex", gap: 4, justifyContent: "flex-end" }}>
                    {canAdjust && (trip.status === "PROPOSED" || trip.status === "PLANNED" || trip.status === "CHANGES_REQUESTED") && (<button onClick={() => onAdjust(trip)} style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: indigo, background: "#EEF2FF", border: "none", borderRadius: 6, padding: "3px 8px", cursor: "pointer" }}>Edit</button>)}
                    {canAdjust && (trip.status === "PROPOSED" || trip.status === "CHANGES_REQUESTED") && (<button onClick={() => onDelete(trip)} style={{ fontFamily: SANS, fontSize: 10, fontWeight: 700, color: rose, background: "#FEF2F2", border: "none", borderRadius: 6, padding: "3px 8px", cursor: "pointer" }}><Trash2 size={11}/></button>)}
                  </div>
                </div>);
            })}
          </div>
        </div>))}
    </div>);
}
// ─── Propose Modal ────────────────────────────────────────────────────────────
function ProposeModal({ onClose, onSubmit, defaultDate }) {
    const [tripCode, setTripCode] = useState("RUN-210");
    const [route, setRoute] = useState("Aluva to Thrippunithura");
    const [startTime, setStart] = useState("10:00 AM");
    const [endTime, setEnd] = useState("10:45 AM");
    const [serviceDate, setService] = useState(defaultDate);
    const [trainId, setTrainId] = useState("TS-01");
    const [errorMsg, setErrorMsg] = useState("");

    function handleSubmit() {
        setErrorMsg("");
        if (!trainId.trim()) {
            setErrorMsg("Train Set ID is required (e.g. TS-01).");
            return;
        }
        if (!tripCode.trim()) {
            setErrorMsg("Run Code is required (e.g. RUN-210).");
            return;
        }
        onSubmit({
            tripCode: tripCode.trim(),
            routeName: route.trim(),
            startTime: startTime.trim(),
            endTime: endTime.trim(),
            serviceDate: serviceDate.trim(),
            assignedTrainId: trainId.trim()
        });
    }

    return (<div style={{ position: "fixed", inset: 0, zIndex: 9999, background: "rgba(15,23,42,0.5)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }}>
      <div style={{ background: "#fff", borderRadius: 16, width: "min(460px,100%)", boxShadow: "0 24px 72px rgba(15,23,42,0.25)", padding: 24 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
          <h3 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 800, color: heading }}>Schedule Timetable Trip</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={16} color={muted}/></button>
        </div>
        {errorMsg && (
          <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, padding: "8px 12px", marginBottom: 12, color: rose, fontFamily: SANS, fontSize: 12, display: "flex", alignItems: "center", gap: 6 }}>
            <AlertCircle size={14} /> {errorMsg}
          </div>
        )}
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Service Date</span>
            <input type="date" value={serviceDate} onChange={e => setService(e.target.value)} style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Run Code</span>
            <input value={tripCode} onChange={e => setTripCode(e.target.value)} placeholder="e.g. RUN-210" style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Route Corridor</span>
            <select value={route} onChange={e => setRoute(e.target.value)} style={{ fontFamily: SANS, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none", background: "#fff" }}>
              <option value="Aluva to Thrippunithura">Aluva to Thrippunithura (Track 1 UP)</option>
              <option value="Thrippunithura to Aluva">Thrippunithura to Aluva (Track 2 DN)</option>
            </select>
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Assign Train Set (Mandatory)</span>
            <input value={trainId} onChange={e => setTrainId(e.target.value)} placeholder="e.g. TS-01" style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
          </label>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Start Time</span>
              <input value={startTime} onChange={e => setStart(e.target.value)} placeholder="10:00 AM" style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>End Time</span>
              <input value={endTime} onChange={e => setEnd(e.target.value)} placeholder="10:45 AM" style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
            </label>
          </div>
          <div style={{ display: "flex", gap: 10, marginTop: 6 }}>
            <button onClick={onClose} style={{ flex: 1, fontFamily: SANS, fontSize: 12, color: body, background: "#F1F5F9", border: "none", borderRadius: 10, padding: "10px 0", cursor: "pointer" }}>Cancel</button>
            <button onClick={handleSubmit} style={{ flex: 2, fontFamily: SANS, fontSize: 12, fontWeight: 700, color: "#fff", background: teal, border: "none", borderRadius: 10, padding: "10px 0", cursor: "pointer" }}>Submit Proposal</button>
          </div>
        </div>
      </div>
    </div>);
}
// ─── Adjust Modal ─────────────────────────────────────────────────────────────
function AdjustModal({ trip, onClose, onSubmit }) {
    const [routeName, setRoute] = useState(trip.routeName);
    const [startTime, setStart] = useState(trip.startTime);
    const [endTime, setEnd] = useState(trip.endTime);
    const [trainId, setTrainId] = useState(trip.assignedTrainId || "TS-01");
    const [reason, setReason] = useState("");
    const [errorMsg, setErrorMsg] = useState("");

    function handleSubmit() {
        setErrorMsg("");
        if (!trainId.trim()) {
            setErrorMsg("Train Set ID is required (e.g. TS-01).");
            return;
        }
        onSubmit(trip.id, {
            routeName: routeName.trim(),
            startTime: startTime.trim(),
            endTime: endTime.trim(),
            assignedTrainId: trainId.trim(),
            reason: reason.trim()
        });
    }

    return (<div style={{ position: "fixed", inset: 0, zIndex: 9999, background: "rgba(15,23,42,0.55)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }}>
      <div style={{ background: "#fff", borderRadius: 16, width: "min(480px,100%)", boxShadow: "0 24px 72px rgba(15,23,42,0.25)", padding: 24 }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
          <div>
            <h3 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 800, color: heading }}>Modify Schedule — {trip.tripCode}</h3>
            <p style={{ fontFamily: SANS, fontSize: 11, color: muted }}>{trip.status === "PLANNED" ? "Live correction — applies immediately." : "Edits proposed draft before approval."}</p>
          </div>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={16} color={muted}/></button>
        </div>
        {errorMsg && (
          <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, padding: "8px 12px", marginBottom: 12, color: rose, fontFamily: SANS, fontSize: 12, display: "flex", alignItems: "center", gap: 6 }}>
            <AlertCircle size={14} /> {errorMsg}
          </div>
        )}
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Route Corridor</span>
            <select value={routeName} onChange={e => setRoute(e.target.value)} style={{ fontFamily: SANS, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none", background: "#fff" }}>
              <option value="Aluva to Thrippunithura">Aluva to Thrippunithura (Track 1 UP)</option>
              <option value="Thrippunithura to Aluva">Thrippunithura to Aluva (Track 2 DN)</option>
            </select>
          </label>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Start Time</span>
              <input value={startTime} onChange={e => setStart(e.target.value)} style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
              <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>End Time</span>
              <input value={endTime} onChange={e => setEnd(e.target.value)} style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
            </label>
          </div>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Assigned Train Set (Mandatory)</span>
            <input value={trainId} onChange={e => setTrainId(e.target.value)} placeholder="e.g. TS-01" style={{ fontFamily: MONO, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: heading }}>Reason (audit trail)</span>
            <textarea value={reason} onChange={e => setReason(e.target.value)} rows={2} placeholder="Optional reason" style={{ fontFamily: SANS, fontSize: 12, padding: "8px 12px", borderRadius: 10, border: `1px solid ${border}`, outline: "none" }}/>
          </label>
          <div style={{ display: "flex", gap: 10, marginTop: 6 }}>
            <button onClick={onClose} style={{ flex: 1, fontFamily: SANS, fontSize: 12, color: body, background: "#F1F5F9", border: "none", borderRadius: 10, padding: "10px 0", cursor: "pointer" }}>Cancel</button>
            <button onClick={handleSubmit} style={{ flex: 2, fontFamily: SANS, fontSize: 12, fontWeight: 700, color: "#fff", background: indigo, border: "none", borderRadius: 10, padding: "10px 0", cursor: "pointer" }}>
              {trip.status === "PLANNED" ? "Save Live Correction" : "Save Adjustment"}
            </button>
          </div>
        </div>
      </div>
    </div>);
}
// ─── Main SchedulePage ────────────────────────────────────────────────────────
export default function SchedulePage({ isSignedIn, onNavigate, onLogOut, userName, userRole, }) {
    const today = todayIso();
    const [selectedDate, setSelectedDate] = useState(today);
    const [trips, setTrips] = useState(() => getCached(`schedule_trips_${today}`, []));
    const [loading, setLoading] = useState(false);
    const [generating, setGenerating] = useState(false);
    const [proposeOpen, setProposeOpen] = useState(false);
    const [adjusting, setAdjusting] = useState(null);
    const [toast, setToast] = useState(null);
    const [viewMode, setViewMode] = useState("GANTT");
    const [submitting, setSubmitting] = useState(false);
    const [dateDecision, setDateDecision] = useState(null);
    const [decisionComments, setDecisionComments] = useState(null);
    const isToday = selectedDate === today;
    const isFuture = selectedDate > today;
    const isPast = selectedDate < today;
    const roleClean = String(userRole || "").toUpperCase().trim();
    const isOC = roleClean.includes("OC") || roleClean.includes("OPS") || roleClean.includes("CONTROLLER");
    const canAct = isOC;
    // Silent load helper (silent = true doesn't flash loading spinners during polling)
    const load = useCallback(async (silent = false) => {
        const cached = getCached(`schedule_trips_${selectedDate}`);
        if (!silent && !cached)
            setLoading(true);
        try {
            const token = getAuthToken();
            const headers = { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) };

            const [tripsRes, historyRes] = await Promise.allSettled([
                fetch(`${API_BASE}/schedule/trips/date/${selectedDate}`, { headers }),
                fetch(`http://localhost:8080/api/approver/history`, { headers }),
            ]);

            let data = [];
            if (tripsRes.status === "fulfilled" && tripsRes.value.ok) {
                data = await tripsRes.value.json();
            }

            let historyList = [];
            if (historyRes.status === "fulfilled" && historyRes.value.ok) {
                historyList = await historyRes.value.json();
            }

            // Check if there is an approval/rejection decision for this serviceDate
            const dateHistory = Array.isArray(historyList)
                ? historyList.filter(h => String(h.title || "").includes(selectedDate) || String(h.taskId || "").includes(selectedDate))
                : [];
            const sortedRecords = dateHistory.length > 0
                ? dateHistory.sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime())
                : [];
            const latestRecord = sortedRecords[0] || null;
            const latestDecision = latestRecord?.decision || null;
            const latestComments = latestRecord?.comments || null;
            setDateDecision(latestDecision);
            setDecisionComments(latestComments);

            if (data && data.length > 0) {
                const isChangesRequested = latestDecision === "CHANGES_REQUESTED" || latestDecision === "REQUEST_CHANGES";
                const isApproved = latestDecision === "APPROVED";
                const isRejected = latestDecision === "REJECTED";

                const reconciled = data.map(trip => {
                    if (isChangesRequested) {
                        // When SADA has requested changes, reflect CHANGES_REQUESTED instead of REJECTED
                        if (trip.status === "PROPOSED" || trip.status === "REJECTED" || trip.status === "CANCELLED") {
                            return { ...trip, status: "CHANGES_REQUESTED", changeReason: latestComments || trip.changeReason };
                        }
                    } else if (isApproved && trip.status === "PROPOSED") {
                        return { ...trip, status: "PLANNED" };
                    } else if (isRejected) {
                        if (trip.status === "PROPOSED" || trip.status === "CANCELLED") {
                            return { ...trip, status: "REJECTED" };
                        }
                    }
                    return trip;
                });

                const sorted = [...reconciled].sort((a, b) => {
                    const am = a.startMinutes || parseTimeToMinutes(a.startTime);
                    const bm = b.startMinutes || parseTimeToMinutes(b.startTime);
                    return am - bm;
                });
                setTrips(sorted);
                setCached(`schedule_trips_${selectedDate}`, sorted);
            }
            else {
                setTrips([]);
                setCached(`schedule_trips_${selectedDate}`, []);
            }
        }
        catch {
            if (!silent && !cached)
                setTrips([]);
        }
        finally {
            setLoading(false);
        }
    }, [selectedDate]);
    useEffect(() => {
        load(false);
        const iv = setInterval(() => load(true), 5000);
        return () => clearInterval(iv);
    }, [load]);
    // Default viewMode: GANTT for all dates
    useEffect(() => {
        setViewMode("GANTT");
    }, [selectedDate]);
    function showToast(msg, err = false) { setToast({ msg, err }); }
    async function handleGenerate() {
        if (isPast) {
            showToast("A schedule cannot be generated for a past service date.", true);
            return;
        }
        setGenerating(true);
        showToast(`Generating AI full-day schedule for ${selectedDate}…`);
        try {
            const r = await apiFetch(`/schedule/generate/${selectedDate}`, { method: "POST" });
            showToast(r?.message || `AI Schedule successfully generated for ${selectedDate}.`);
            await load(false);
        }
        catch (e) {
            showToast(`AI generation failed: ${e.message}`, true);
        }
        finally {
            setGenerating(false);
        }
    }
    async function handleDelete(trip) {
        try {
            await apiFetch(`/schedule/trips/${trip.id}`, { method: "DELETE", body: JSON.stringify({ reason: "Removed from Schedule page" }) });
            showToast(`${trip.tripCode} removed.`);
            await load(false);
        }
        catch (e) {
            showToast(`Remove failed: ${e.message}`, true);
        }
    }
    async function handlePropose(f) {
        try {
            await apiFetch("/schedule/propose", { method: "POST", body: JSON.stringify({ ...f, source: "MANUAL" }) });
            setProposeOpen(false);
            showToast(`Proposal for ${f.tripCode} submitted to SADA approval queue.`);
            await load(false);
        }
        catch (e) {
            showToast(e.message || "Cannot schedule trip: Validation failed.", true);
        }
    }
    async function handleAdjust(id, f) {
        try {
            await apiFetch(`/schedule/trips/${id}/adjust`, { method: "PATCH", body: JSON.stringify(f) });
            setAdjusting(null);
            showToast(`Trip schedule updated successfully.`);
            await load(false);
        }
        catch (e) {
            showToast(e.message || "Cannot modify schedule: Validation failed.", true);
        }
    }
    async function handleResubmitProposal() {
        setSubmitting(true);
        try {
            const token = getAuthToken();
            const headers = {
                "Content-Type": "application/json",
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            };
            const payload = {
                targetEntityId: selectedDate,
                requestType: "SCHEDULE_PROPOSAL",
                title: `Daily Schedule Proposal (Revised) - ${selectedDate} (${trips.length} trips)`,
                description: `Revised daily operations schedule for ${selectedDate} with changes addressed by Operations Controller (${userName || "OC"}).`,
                priority: "HIGH",
                requestedBy: userName || "OperationsController",
                assignedApproverRole: "ROLE_SADA"
            };
            const res = await fetch("http://localhost:8080/api/approver/tasks/submit", {
                method: "POST",
                headers,
                body: JSON.stringify(payload)
            });
            if (!res.ok) {
                const errJson = await res.json().catch(() => ({}));
                throw new Error(errJson.message || `Submission failed (${res.status})`);
            }
            showToast(`Revised schedule for ${selectedDate} re-submitted to SADA approval queue.`);
            await load(false);
        } catch (e) {
            showToast(e.message || "Failed to re-submit schedule proposal.", true);
        } finally {
            setSubmitting(false);
        }
    }
    // Live Anti-Collision Audit: detect overlapping trip windows on the same train set
    const collisions = useMemo(() => {
        const conflicts = [];
        const map = new Map();
        trips.forEach(t => {
            if (t.assignedTrainId) {
                if (!map.has(t.assignedTrainId))
                    map.set(t.assignedTrainId, []);
                map.get(t.assignedTrainId).push(t);
            }
        });
        map.forEach((trainTrips, trainId) => {
            trainTrips.sort((a, b) => (a.startMinutes || 0) - (b.startMinutes || 0));
            for (let i = 0; i < trainTrips.length - 1; i++) {
                const t1 = trainTrips[i];
                const t2 = trainTrips[i + 1];
                const t1End = t1.endMinutes || ((t1.startMinutes || 300) + 45);
                const t2Start = t2.startMinutes || 300;
                if (t2Start < t1End) {
                    conflicts.push({
                        trainId,
                        t1Code: t1.tripCode,
                        t2Code: t2.tripCode,
                        msg: `Overlap on ${t1.assignedTrainName || trainId}: ${t1.tripCode} ends at ${t1.endTime} but ${t2.tripCode} starts at ${t2.startTime}`,
                    });
                }
            }
        });
        return conflicts;
    }, [trips]);
    async function handleAutoFixCollisions() {
        try {
            await apiFetch(`/schedule/auto-resolve-collisions/${selectedDate}`, { method: "POST" });
            showToast("Anti-Collision Engine resolved all overlapping trip windows!");
            await load(false);
        }
        catch (e) {
            showToast(`Auto-resolve failed: ${e.message}`, true);
        }
    }
    // Strictly group trips by their real backend-assigned train ID (never override in round-robin)
    const assignedTrainCols = (() => {
        const kmrlRivers = {
            "TS-01": "KMRL Set 01 (Periyar)",
            "TS-02": "KMRL Set 02 (Pamba)",
            "TS-03": "KMRL Set 03 (Kabani)",
            "TS-04": "KMRL Set 04 (Bhavani)",
            "TS-05": "KMRL Set 05 (Chaliyar)",
            "TS-06": "KMRL Set 06 (Bharathapuzha)",
            "TS-07": "KMRL Set 07 (Meenachil)",
            "TS-08": "KMRL Set 08 (Kaveri)",
            "TS-09": "KMRL Set 09 (Muvattupuzha)",
            "TS-10": "KMRL Set 10 (Chalakkudy)",
            "TS-11": "KMRL Set 11 (Achankovil)",
            "TS-12": "KMRL Set 12 (Manimala)",
            "TS-13": "KMRL Set 13 (Neyyar)",
            "TS-14": "KMRL Set 14 (Kallada)",
            "TS-15": "KMRL Set 15 (Valapattanam)",
            "TS-16": "KMRL Set 16 (Gayathri)",
            "TS-17": "KMRL Set 17 (Siruvani)",
            "TS-18": "KMRL Set 18 (Korapuzha)",
            "TS-19": "KMRL Set 19 (Irikkur)",
            "TS-20": "KMRL Set 20 (Thanikkudam)",
            "TS-21": "KMRL Set 21 (Pamba-II)",
            "TS-22": "KMRL Set 22 (Kuthiran)",
            "TS-23": "KMRL Set 23 (Pennar)",
            "TS-24": "KMRL Set 24 (Chaliyar-II)",
            "TS-25": "KMRL Set 25 (Neyyar-II)",
        };
        const map = new Map();

        trips.forEach((trip) => {
            let key = trip.assignedTrainId;
            if (!key) {
                key = "UNASSIGNED";
            }
            let name = kmrlRivers[key] || trip.assignedTrainName || key;
            if (key === "UNASSIGNED") name = "Train Not Assigned / Cancelled";

            if (!map.has(key)) {
                map.set(key, { id: key, name: name, trips: [] });
            }
            map.get(key).trips.push(trip);
        });

        return Array.from(map.values()).sort((a, b) => a.id.localeCompare(b.id));
    })();
    // Vertical Gantt time ticks: 05:00 AM to 11:00 PM (every 2 hours)
    const timeTicks = [
        { label: "05:00 AM", min: 5 * 60 },
        { label: "07:00 AM", min: 7 * 60 },
        { label: "09:00 AM", min: 9 * 60 },
        { label: "11:00 AM", min: 11 * 60 },
        { label: "01:00 PM", min: 13 * 60 },
        { label: "03:00 PM", min: 15 * 60 },
        { label: "05:00 PM", min: 17 * 60 },
        { label: "07:00 PM", min: 19 * 60 },
        { label: "09:00 PM", min: 21 * 60 },
        { label: "11:00 PM", min: 23 * 60 },
    ];
    return (<div className="page-transition" style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: bgPage }}>
      <SharedHeader activePage="schedule" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* ── Page Header Bar (Standardized Enterprise Container) ───────────── */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${border}` }}>
        <div style={{ maxWidth: 1380, width: "100%", margin: "0 auto", padding: "24px 32px", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 16 }}>
          <div>
            <h1 style={{ fontFamily: DISPLAY, fontSize: 22, fontWeight: 800, color: heading, margin: 0 }}>
              Dynamic Timetable Console
            </h1>
            <p style={{ fontFamily: SANS, fontSize: 13, color: muted, marginTop: 4, margin: 0 }}>
              MetroMind KMRL Operations Control System · Line 1 Mainline Corridor (Aluva – Thrippunithura) · Service 05:00 AM – 11:00 PM
            </p>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            {/* Date Selector */}
            <label style={{ display: "flex", alignItems: "center", gap: 6, padding: "6px 12px", borderRadius: 10, border: `1px solid ${border}`, background: "#fff", fontFamily: SANS, fontSize: 12, color: body }}>
              <Calendar size={14} color={teal}/>
              <input type="date" value={selectedDate} onChange={e => setSelectedDate(e.target.value)} style={{ outline: "none", border: "none", background: "transparent", fontFamily: MONO, fontSize: 12, cursor: "pointer" }}/>
            </label>

            {canAct && (<button onClick={handleGenerate} disabled={generating || isPast} title={isPast ? "Past service dates cannot be generated." : "Generate the schedule only when you explicitly click this button."} style={{ display: "flex", alignItems: "center", gap: 6, fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: isPast ? muted : "#fff", background: isPast ? "#E2E8F0" : "linear-gradient(135deg, #009688, #00786B)", border: "none", borderRadius: 10, padding: "8px 16px", cursor: generating || isPast ? "not-allowed" : "pointer", boxShadow: isPast ? "none" : "0 2px 8px rgba(0,150,136,0.25)" }}>
                {generating ? <Loader2 size={14} className="animate-spin"/> : <Cpu size={14}/>}
                {generating ? "Generating…" : "Generate Operations Schedule"}
              </button>)}

            {canAct && (<button onClick={() => setProposeOpen(true)} style={{ display: "flex", alignItems: "center", gap: 6, fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: body, background: "#fff", border: `1px solid ${border}`, borderRadius: 10, padding: "8px 16px", cursor: "pointer" }}>
                <Plus size={14} color={teal}/> Schedule Trip
              </button>)}
          </div>
        </div>
      </div>

      {/* ── Main Container (Standardized 1380px Width) ────────────────────── */}
      <main style={{ flex: 1, maxWidth: 1380, width: "100%", margin: "0 auto", padding: "24px 32px 40px" }}>

        {/* Anti-Collision Safety Warning Banner */}
        {collisions.length > 0 && (<div style={{
                background: "linear-gradient(135deg, #FEF2F2 0%, #FEE2E2 100%)",
                border: "1px solid #FCA5A5", borderRadius: 14, padding: "14px 20px",
                marginBottom: 20, display: "flex", alignItems: "center", justifyContent: "space-between",
                boxShadow: "0 4px 16px rgba(239,68,68,0.12)"
            }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{ width: 36, height: 36, borderRadius: 10, background: "#DC2626", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <ShieldCheck size={20} color="#fff"/>
              </div>
              <div>
                <div style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 800, color: "#991B1B" }}>
                  Operational Overlap Warning: {collisions.length} Schedule Conflict{collisions.length > 1 ? "s" : ""} Flagged
                </div>
                <div style={{ fontFamily: SANS, fontSize: 12, color: "#B91C1C", marginTop: 2 }}>
                  {collisions[0].msg}
                </div>
              </div>
            </div>
            <button onClick={handleAutoFixCollisions} style={{
                fontFamily: DISPLAY, fontSize: 12, fontWeight: 800, color: "#fff",
                background: "#DC2626", border: "none", borderRadius: 10, padding: "8px 16px",
                cursor: "pointer", display: "flex", alignItems: "center", gap: 6,
                boxShadow: "0 2px 8px rgba(220,38,38,0.3)"
            }}>
              <ShieldCheck size={14}/> Auto-Resolve Safety Conflicts
            </button>
          </div>)}



        {/* Schedule Status Banner — reading a date NEVER generates a schedule. */}
        {!loading && trips.length > 0 && (() => {
          const hasChangesRequested = dateDecision === "CHANGES_REQUESTED" || trips.some(t => t.status === "CHANGES_REQUESTED");
          if (hasChangesRequested) {
            return (
              <div style={{
                display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap",
                padding: "14px 20px", background: "#FFFBEB", border: "1px solid #FDE68A", borderLeft: "4px solid #F59E0B",
                borderRadius: 12, marginBottom: 20, fontFamily: SANS, fontSize: 12.5, color: "#92400E",
                boxShadow: "0 2px 8px rgba(245,158,11,0.08)"
              }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10, flex: 1, minWidth: 260 }}>
                  <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0 }}/>
                  <span>
                    <strong style={{ color: "#78350F" }}>SADA Requested Changes for {selectedDate}.</strong>{" "}
                    {decisionComments ? <span style={{ fontStyle: "italic" }}>Review note: "{decisionComments}". </span> : ""}
                    {trips.filter(t => t.status === "CHANGES_REQUESTED").length} proposed run{trips.filter(t => t.status === "CHANGES_REQUESTED").length > 1 ? "s" : ""} require revision. You can edit, adjust, or remove trips below, then re-submit for SADA sign-off.
                  </span>
                </div>
                {canAct && (
                  <button
                    onClick={handleResubmitProposal}
                    disabled={submitting}
                    style={{
                      display: "inline-flex", alignItems: "center", gap: 6,
                      fontFamily: DISPLAY, fontSize: 11.5, fontWeight: 700,
                      color: "#fff", background: "#D97706",
                      border: "none", borderRadius: 8, padding: "8px 16px",
                      cursor: submitting ? "default" : "pointer",
                      boxShadow: "0 2px 6px rgba(217,119,6,0.3)",
                      whiteSpace: "nowrap"
                    }}
                  >
                    {submitting ? <Loader2 size={13} className="animate-spin"/> : <Send size={13}/>}
                    {submitting ? "Submitting…" : "Re-Submit to SADA"}
                  </button>
                )}
              </div>
            );
          }
          return (
            <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "12px 16px", background: isFuture ? "#ECFDF5" : "#F8FAFC", border: `1px solid ${isFuture ? "#A7F3D0" : border}`, borderRadius: 12, marginBottom: 20, fontFamily: SANS, fontSize: 12, color: isFuture ? "#047857" : body }}>
              <Info size={16} color={isFuture ? "#059669" : muted}/>
              <span>
                <strong>{isFuture ? "Proposed Operations Schedule" : "Operations Schedule"} — Service Date: {selectedDate}.</strong>{" "}
                {trips.filter(t => t.status === "PROPOSED").length > 0
                  ? `${trips.filter(t => t.status === "PROPOSED").length} proposed runs registered, awaiting SADA approval.`
                  : `${trips.length} schedule runs registered.`}
              </span>
            </div>
          );
        })()}

        {/* Explicit empty state — past and future dates with no generated data. */}
        {!loading && trips.length === 0 && (<div style={{ background: "#fff", border: `1px solid ${border}`, borderRadius: 16, padding: "56px 28px", textAlign: "center", boxShadow: "0 2px 8px rgba(15,23,42,0.04)" }}>
            <div style={{ width: 52, height: 52, margin: "0 auto 14px", borderRadius: 14, background: isPast ? "#F1F5F9" : "#ECFDF5", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <Calendar size={24} color={isPast ? muted : teal}/>
            </div>
            <div style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 800, color: heading }}>
              Schedule not generated
            </div>
            <div style={{ maxWidth: 560, margin: "8px auto 0", fontFamily: SANS, fontSize: 12.5, lineHeight: 1.6, color: muted }}>
              {isPast
                ? `No schedule data was generated for ${selectedDate}. This is a historical date, so the system will not generate a new schedule automatically.`
                : `No schedule has been generated for ${selectedDate} yet. Click below to generate.`}
            </div>
            {!isPast && canAct && (<button onClick={handleGenerate} disabled={generating} style={{ marginTop: 20, display: "inline-flex", alignItems: "center", gap: 7, fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: "#fff", background: "linear-gradient(135deg, #009688, #00786B)", border: "none", borderRadius: 10, padding: "10px 18px", cursor: generating ? "default" : "pointer", boxShadow: "0 2px 8px rgba(0,150,136,0.25)" }}>
                {generating ? <Loader2 size={14} className="animate-spin"/> : <Cpu size={14}/>}
                {generating ? "Generating…" : "Generate Operations Schedule"}
              </button>)}
          </div>)}

        {/* Loading State */}
        {loading && (<div style={{ padding: "60px 0", textAlign: "center", fontFamily: SANS, fontSize: 13, color: muted, display: "flex", alignItems: "center", justifyContent: "center", gap: 10 }}>
            <Loader2 size={20} className="animate-spin" color={teal}/> Fetching schedule telemetry…
          </div>)}

        {/* ── GANTT CHART VIEW (Full-Width Train Columns Layout) ─────────────── */}
        {!loading && trips.length > 0 && (<div style={{ width: "100%", background: "#fff", border: `1px solid ${border}`, borderRadius: 14, overflow: "hidden", boxShadow: "0 2px 8px rgba(15,23,42,0.04)" }}>
            <div style={{ overflowX: "auto" }}>
              <div style={{ minWidth: Math.max(760, 110 + Math.max(assignedTrainCols.length, 1) * 170) }}>
                
                {/* Header Row: Trainset Columns */}
                <div style={{ display: "grid", gridTemplateColumns: `110px repeat(${Math.max(assignedTrainCols.length, 1)}, minmax(160px, 1fr))`, borderBottom: `1px solid ${border}`, background: "#F8FAFC" }}>
                  <div style={{ padding: "12px", fontFamily: SANS, fontSize: 10, fontWeight: 800, color: muted, textTransform: "uppercase", letterSpacing: "0.07em", borderRight: `1px solid ${border}`, display: "flex", alignItems: "center", gap: 6 }}>
                    <Clock size={13} color={teal}/> Time
                  </div>
                  {assignedTrainCols.map(col => (<div key={col.id} style={{ padding: "10px 12px", borderRight: `1px solid ${border}`, display: "flex", flexDirection: "column", alignItems: "center", gap: 2, textAlign: "center" }}>
                      <span style={{ fontFamily: DISPLAY, fontSize: 12, fontWeight: 800, color: heading, display: "flex", alignItems: "center", gap: 5 }}>
                        <Train size={12} color={teal}/> {col.name}
                      </span>
                      <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 700, color: tealDark, background: tealLight, padding: "1px 7px", borderRadius: 4 }}>
                        {col.id}
                      </span>
                    </div>))}
                </div>

                {/* Body Grid Area (1620px Height = 1.5px per Minute for 18-hour day) */}
                <div style={{ display: "grid", gridTemplateColumns: `110px repeat(${assignedTrainCols.length}, minmax(160px, 1fr))`, height: 1620, position: "relative" }}>
                  
                  {/* Left Time Axis */}
                  <div style={{ position: "relative", borderRight: `1px solid ${border}`, background: "#F8FAFC", height: 1620 }}>
                    {timeTicks.map((t, idx) => {
                const topPx = ((t.min - SERVICE_START_MIN) / FULL_DAY_MINUTES) * 1620;
                return (<div key={idx} style={{ position: "absolute", top: topPx, left: 0, width: "100%", padding: "0 10px", transform: "translateY(-50%)" }}>
                          <span style={{ fontFamily: MONO, fontSize: 10, fontWeight: 700, color: muted }}>
                            {t.label}
                          </span>
                        </div>);
            })}
                  </div>

                  {/* Train Columns */}
                  {assignedTrainCols.map(col => (<div key={col.id} style={{ position: "relative", borderRight: `1px solid ${border}`, height: 1620, background: "#ffffff" }}>
                      {/* Grid horizontal rules */}
                      <div style={{ position: "absolute", inset: 0, pointerEvents: "none" }}>
                        {timeTicks.map((t, idx) => {
                    const topPx = ((t.min - SERVICE_START_MIN) / FULL_DAY_MINUTES) * 1620;
                    return <div key={idx} style={{ position: "absolute", top: topPx, left: 0, width: "100%", borderTop: `1px solid ${border}` }}/>;
                })}
                      </div>

                      {/* Render vertical blocks for each trip on this train */}
                      {col.trips.map(trip => (<VerticalGanttBlock key={trip.id} trip={trip} isToday={isToday} windowStartMin={SERVICE_START_MIN} windowTotalMin={FULL_DAY_MINUTES} canAdjust={canAct} onAdjust={setAdjusting} onDelete={handleDelete} onNavigate={onNavigate}/>))}
                    </div>))}
                </div>
              </div>
            </div>
          </div>)}

      </main>

      <SharedFooter onNavigate={onNavigate}/>

      {/* Modals */}
      {proposeOpen && <ProposeModal onClose={() => setProposeOpen(false)} onSubmit={handlePropose} defaultDate={selectedDate}/>}
      {adjusting && <AdjustModal trip={adjusting} onClose={() => setAdjusting(null)} onSubmit={handleAdjust}/>}

      {/* Toast Notification */}
      {toast && (<div style={{
                position: "fixed", bottom: 28, left: "50%", transform: "translateX(-50%)", zIndex: 9999,
                background: toast.err ? "#1E0505" : "#051E14", color: toast.err ? "#FCA5A5" : "#6EE7B7",
                borderRadius: 14, padding: "12px 20px", boxShadow: "0 8px 32px rgba(0,0,0,0.35)",
                display: "flex", alignItems: "center", gap: 10, fontFamily: SANS, fontSize: 13, fontWeight: 600,
            }}>
          {toast.err ? <AlertCircle size={16}/> : <CheckCircle size={16}/>}
          <span>{toast.msg}</span>
          <button onClick={() => setToast(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "inherit", opacity: 0.6, marginLeft: 8 }}><X size={14}/></button>
        </div>)}
    </div>);
}
