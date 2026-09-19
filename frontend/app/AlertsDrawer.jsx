import { useState, useEffect, useCallback, useRef } from "react";
import { X, Check, CheckCircle, Loader2, RefreshCw, Bell } from "lucide-react";
// ─── API base ─────────────────────────────────────────────────────────────────
// Routed through api-gateway (8080) so alert-service gets the
// X-User-Id/X-User-Role headers the gateway's JWT filter adds.
export const ALERT_API_BASE_URL = "http://localhost:8080/api/v1/alerts";
function authHeaders() {
    const token = localStorage.getItem("auth_token");
    return {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };
}
// ─── Tokens ──────────────────────────────────────────────────────────────────
const teal = "#009688";
const emerald = "#10B981";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const border = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
const SEV = {
    sev3: { text: "#DC2626", bg: "#FEF2F2", label: "Critical" },
    sev2: { text: "#D97706", bg: "#FFFBEB", label: "Warning" },
    sev1: { text: "#64748B", bg: "#F1F5F9", label: "Info" },
};
function normalizeSev(raw) {
    const s = (raw || "").toLowerCase();
    if (s.includes("3") || s.includes("crit"))
        return "sev3";
    if (s.includes("2") || s.includes("warn"))
        return "sev2";
    return "sev1";
}
async function apiFetch(path, init) {
    const res = await fetch(`${ALERT_API_BASE_URL}${path}`, {
        ...init,
        headers: { ...authHeaders(), ...(init?.headers || {}) },
    });
    if (!res.ok) {
        let message = `Request failed (${res.status})`;
        try {
            const body = await res.json();
            message = body.detail || body.message || message;
        }
        catch { /* non-JSON error body */ }
        throw new Error(message);
    }
    if (res.status === 204)
        return undefined;
    return res.json();
}
export default function AlertsDrawer({ isOpen, onClose, isSignedIn, userName }) {
    const [alerts, setAlerts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [loadError, setLoadError] = useState(null);
    const [busyId, setBusyId] = useState(null);
    const panelRef = useRef(null);
    const load = useCallback(async () => {
        setLoading(true);
        try {
            const data = await apiFetch("");
            setAlerts(data.map((a) => ({
                id: a.id, time: a.time, sev: normalizeSev(a.severity),
                title: a.title, subtitle: a.subtitle,
            })));
            setLoadError(null);
        }
        catch (err) {
            setLoadError(err instanceof Error ? err.message : "Couldn't load notifications.");
        }
        finally {
            setLoading(false);
        }
    }, []);
    useEffect(() => {
        if (isOpen)
            load();
    }, [isOpen, load]);
    // Close on outside click / Escape — standard dropdown behavior.
    useEffect(() => {
        if (!isOpen)
            return;
        function onDocMouseDown(e) {
            if (panelRef.current && !panelRef.current.contains(e.target))
                onClose();
        }
        function onKeyDown(e) {
            if (e.key === "Escape")
                onClose();
        }
        document.addEventListener("mousedown", onDocMouseDown);
        document.addEventListener("keydown", onKeyDown);
        return () => {
            document.removeEventListener("mousedown", onDocMouseDown);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, [isOpen, onClose]);
    async function handleDismiss(id) {
        if (busyId)
            return;
        setBusyId(id);
        try {
            await apiFetch(`/${id}/clear`, {
                method: "POST",
                body: JSON.stringify({ operatorName: userName ?? "User" }),
            });
            setAlerts(prev => prev.filter(a => a.id !== id));
        }
        catch {
            // Leave the item in place if the dismiss call failed — no silent data loss.
        }
        finally {
            setBusyId(null);
        }
    }
    if (!isOpen)
        return null;
    return (<div ref={panelRef} role="menu" aria-label="Notifications" style={{
            position: "fixed", top: 64, right: 16, zIndex: 9999,
            width: "min(360px, calc(100vw - 32px))",
            maxHeight: "min(440px, calc(100vh - 84px))",
            display: "flex", flexDirection: "column",
            background: "#fff", borderRadius: 14,
            border: `1px solid ${border}`,
            boxShadow: "0 12px 32px rgba(15,23,42,0.16), 0 2px 8px rgba(15,23,42,0.08)",
            overflow: "hidden",
        }}>
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 shrink-0" style={{ borderBottom: `1px solid ${border}` }}>
        <div className="flex items-center gap-2">
          <Bell size={14} color={inkB}/>
          <h2 style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 800, color: inkH }}>Notifications</h2>
          {alerts.length > 0 && (<span style={{
                fontFamily: SANS, fontSize: 10.5, fontWeight: 700, color: "#fff",
                background: "#DC2626", borderRadius: 999, padding: "1px 6px",
            }}>{alerts.length}</span>)}
        </div>
        <div className="flex items-center gap-1">
          <button onClick={onClose} title="Close" className="w-7 h-7 rounded-md flex items-center justify-center hover:bg-slate-100" style={{ background: "none", border: "none", cursor: "pointer" }}>
            <X size={14} color={inkM}/>
          </button>
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto">
        {loadError ? (<div className="px-4 py-6 text-center">
            <p style={{ fontFamily: SANS, fontSize: 12.5, color: "#B91C1C", marginBottom: 6 }}>{loadError}</p>
            <button onClick={load} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer" }}>
              Try again
            </button>
          </div>) : loading && alerts.length === 0 ? (<div className="flex items-center justify-center gap-2 py-10" style={{ color: inkM, fontFamily: SANS, fontSize: 12.5 }}>
            <Loader2 size={14} className="animate-spin"/> Loading…
          </div>) : alerts.length === 0 ? (<div className="flex flex-col items-center justify-center gap-2 py-10 px-6 text-center">
            <CheckCircle size={20} color={emerald}/>
            <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM }}>You're all caught up — no active alerts.</p>
          </div>) : (alerts.map((a, i) => {
            const s = SEV[a.sev];
            const isBusy = busyId === a.id;
            return (<div key={a.id} className="group flex items-start gap-2.5 px-4 py-3" style={{ borderBottom: i < alerts.length - 1 ? `1px solid ${border}` : "none" }}>
                <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 999, background: s.text, marginTop: 5, flexShrink: 0 }}/>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-1.5 flex-wrap mb-0.5">
                    <span style={{ fontFamily: SANS, fontSize: 9.5, fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.03em", color: s.text }}>{s.label}</span>
                    <span style={{ fontFamily: MONO, fontSize: 10, color: inkM }}>· {a.time}</span>
                  </div>
                  <p style={{ fontFamily: DISPLAY, fontSize: 12.5, fontWeight: 700, color: inkH, lineHeight: 1.3 }}>{a.title}</p>
                  <p style={{ fontFamily: SANS, fontSize: 11.5, color: inkM, lineHeight: 1.4, marginTop: 1 }}>{a.subtitle}</p>
                </div>
                {isSignedIn && (<button onClick={() => handleDismiss(a.id)} disabled={isBusy} title="Dismiss" className="shrink-0 opacity-0 group-hover:opacity-100 transition-opacity" style={{ width: 22, height: 22, borderRadius: 6, display: "flex", alignItems: "center", justifyContent: "center", background: "none", border: `1px solid ${border}`, cursor: isBusy ? "default" : "pointer" }}>
                    {isBusy ? <Loader2 size={11} className="animate-spin" color={inkM}/> : <Check size={11} color={emerald}/>}
                  </button>)}
              </div>);
        }))}
      </div>
    </div>);
}
