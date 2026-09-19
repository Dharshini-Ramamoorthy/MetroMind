import { useState, useEffect, useCallback, useRef } from "react";
import { ShieldCheck, ArrowRight, RefreshCw, Activity, MapPin, Search, Train, Loader2 } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

// ─── Design Tokens ─────────────────────────────────────────────────────────────
const teal     = "#009688";
const tealDk   = "#00786B";
const tealLt   = "#E0F2F1";
const emerald  = "#10B981";
const rose     = "#EF4444";
const inkH     = "#0F172A";
const inkB     = "#334155";
const inkM     = "#64748B";
const line     = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const SANS    = "'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const MONO    = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace";

// ─── API Bases ─────────────────────────────────────────────────────────────────
// Goes through api-gateway (port 8080), not the fleet-service port directly -
// fleet-service requires every request to be authenticated via the
// X-User-Id / X-User-Role headers that only the gateway's JWT filter adds, so
// calling :8085 straight from the browser gets a 401 on every request.
const API_BASE =
  (typeof import.meta !== "undefined" && import.meta.env?.VITE_FLEET_API_BASE) ||
  "http://localhost:8080/api/v1";

class ApiError extends Error {}

async function apiFetch(path, options) {
  let res;
  const token = localStorage.getItem("auth_token");
  try {
    res = await fetch(`${API_BASE}${path}`, {
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...options,
    });
  } catch {
    throw new ApiError("Can't reach fleet service.");
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      message = body.detail || body.message || message;
    } catch {}
    throw new ApiError(message);
  }
  if (res.status === 204) return undefined;
  return res.json();
}

// ─── Operational Time Window Calculator ──────────────────────────────────────
export function getOperationalTimeWindowInfo() {
  const now = new Date();
  const currentMinutes = now.getHours() * 60 + now.getMinutes();

  if (currentMinutes >= 450 && currentMinutes < 630) {
    return { serviceType: "Morning Peak Window", headway: "4 – 5 minutes" };
  } else if (currentMinutes >= 630 && currentMinutes < 1020) {
    return { serviceType: "Midday Off-Peak", headway: "7 – 10 minutes" };
  } else if (currentMinutes >= 1020 && currentMinutes < 1230) {
    return { serviceType: "Evening Peak Window", headway: "4 – 5 minutes" };
  } else if (currentMinutes >= 1230 && currentMinutes < 1380) {
    return { serviceType: "Late Evening Service", headway: "8 – 12 minutes" };
  } else if (currentMinutes >= 1380 || currentMinutes < 300) {
    return { serviceType: "Night Maintenance", headway: "Non-Operational" };
  } else {
    return { serviceType: "Early Morning Service", headway: "6 – 8 minutes" };
  }
}

// ─── Shapes (JS, no static types) ─────────────────────────────────────────────
// TrainStatus: "IN_SERVICE" | "STANDBY" | "IN_MAINTENANCE"
// TrainAsset: { id, trainNumber, model, status, currentDepot, track,
//               assignedTripCode?, assignedRoute?, healthIndex,
//               lastServicedAt, brakePressureKpa, totalMileageKm }
// TrackGroup: { label, trains: TrainAsset[] }
// FleetSummary: { active, standby, maintenance, total }
// AuditLedgerEntry: { id, timestamp, trainNumber, vector, opCode, hash }

function StatusBadge({ status }) {
  if (status === "IN_SERVICE") {
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold shrink-0"
        style={{ background: "rgba(16,185,129,0.12)", color: emerald, fontFamily: SANS }}>
        <span className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: emerald }} />
        In Service
      </span>
    );
  }
  if (status === "STANDBY") {
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold shrink-0"
        style={{ background: tealLt, color: tealDk, fontFamily: SANS }}>
        <span className="w-1.5 h-1.5 rounded-full" style={{ background: teal }} />
        Standby Ready
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-bold shrink-0"
      style={{ background: "rgba(239,68,68,0.12)", color: rose, fontFamily: SANS }}>
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: rose }} />
      In Maintenance
    </span>
  );
}

export default function FleetPage({
  isSignedIn,
  onNavigate,
  onLogOut,
  userName,
  userRole,
}) {
  const windowInfo = getOperationalTimeWindowInfo();

  const [summary, setSummary]   = useState(() => getCached("fleet_summary", {
    active: 0,
    standby: 0,
    maintenance: 0,
    total: 25,
  }));
  const [tracks, setTracks]     = useState(() => getCached("fleet_tracks", []));
  const [ledger, setLedger]     = useState(() => getCached("fleet_ledger", []));
  const [isLoaded, setIsLoaded] = useState(() => Boolean(getCached("fleet_tracks")?.length));
  const [showAllLedger, setShowAllLedger] = useState(false);
  const LEDGER_PREVIEW_LIMIT = 6;
  const [selectedAsset, setSelectedAsset] = useState(() => {
    const cachedTracks = getCached("fleet_tracks", []);
    return (cachedTracks.length > 0 && cachedTracks[0].trains?.length > 0) ? cachedTracks[0].trains[0] : null;
  });
  const [searchQuery, setSearchQuery]     = useState("");
  const [statusFilter, setStatusFilter]   = useState("ALL");
  const [loading, setLoading]   = useState(false);

  const selectedAssetIdRef = useRef(null);
  useEffect(() => {
    selectedAssetIdRef.current = selectedAsset?.id ?? null;
  }, [selectedAsset]);

  const loadData = useCallback(async () => {
    try {
      const [sumData, trackData, ledgerData] = await Promise.all([
        apiFetch("/fleet/summary"),
        apiFetch("/fleet/yard"),
        apiFetch("/ledger"),
      ]);

      // Calculate exact counts directly from real trackData trains list for 100% accuracy
      const allTrains = (trackData || []).flatMap(g => g.trains || []);
      const activeCount = allTrains.filter(t => t.status === "IN_SERVICE").length;
      const standbyCount = allTrains.filter(t => t.status === "STANDBY").length;
      const maintenanceCount = allTrains.filter(t => t.status === "IN_MAINTENANCE").length;
      const totalCount = allTrains.length || sumData?.total || 25;

      const nextSummary = {
        active: activeCount,
        standby: standbyCount,
        maintenance: maintenanceCount,
        total: totalCount,
      };
      setSummary(nextSummary);
      setIsLoaded(true);
      setTracks(trackData || []);
      const sortedLedger = [...(ledgerData || [])].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      setLedger(sortedLedger);

      // Cache for instant navigation transitions
      setCached("fleet_summary", nextSummary);
      setCached("fleet_tracks", trackData || []);
      setCached("fleet_ledger", sortedLedger);

      const currentId = selectedAssetIdRef.current;
      if (currentId) {
        for (const grp of trackData) {
          const match = grp.trains.find(t => t.id === currentId);
          if (match) {
            setSelectedAsset(match);
            break;
          }
        }
      } else if (trackData.length > 0 && trackData[0].trains.length > 0) {
        setSelectedAsset(trackData[0].trains[0]);
      }
    } catch {
      setTracks(prev => {
        const allTrains = (prev || []).flatMap(g => g.trains || []);
        if (allTrains.length > 0) {
          setSummary({
            active: allTrains.filter(t => t.status === "IN_SERVICE").length,
            standby: allTrains.filter(t => t.status === "STANDBY").length,
            maintenance: allTrains.filter(t => t.status === "IN_MAINTENANCE").length,
            total: allTrains.length,
          });
        }
        return prev;
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 3000);
    return () => clearInterval(interval);
  }, [loadData]);

  const filteredTracks = tracks.map(group => ({
    ...group,
    trains: (group.trains || []).filter(t => {
      const matchesSearch = t.trainNumber?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                            t.id?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                            t.track?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesFilter = statusFilter === "ALL" || t.status === statusFilter;
      return matchesSearch && matchesFilter;
    })
  })).filter(group => group.trains.length > 0);

  return (
    <div className="min-h-screen flex flex-col page-transition" style={{ background: "#F8FAFC" }}>
      <SharedHeader activePage="fleet" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole} />

      {/* Header Band */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${line}` }}>
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>KMRL 25-Trainset Fleet &amp; Telemetry Console</h1>
            </div>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Full asset tracking for all 25 Alstom Metropolis trainsets across Muttom Depot &amp; Mainline Corridor.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

        {/* Global 25-Train Fleet KPI Summary */}
        <section className="grid grid-cols-1 sm:grid-cols-4 gap-4">
          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${line}`, borderLeft: `4px solid ${inkH}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Total KMRL Trains</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{isLoaded ? summary.total : "--"}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkM }}>total trainsets</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>3-Car Alstom Metropolis fleet</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${line}`, borderLeft: `4px solid ${emerald}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Active Mainline Fleet</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{isLoaded ? summary.active : "--"}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: emerald }}>in active service</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Running {windowInfo.headway} headway runs</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${line}`, borderLeft: `4px solid ${teal}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Yard Standby Reserve</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{isLoaded ? summary.standby : "--"}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: teal }}>ready at Muttom</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Pre-checked for injection</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${line}`, borderLeft: `4px solid ${rose}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Maintenance Servicing</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{isLoaded ? summary.maintenance : "--"}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: rose }}>in maintenance</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Routine maintenance sheds</p>
          </div>
        </section>

        {/* Search & Filter Controls */}
        <div className="flex flex-wrap items-center justify-between gap-4 p-4 rounded-2xl bg-white border" style={{ borderColor: line }}>
          <div className="flex items-center gap-2 flex-1 max-w-md px-3 py-2 rounded-xl bg-slate-50 border" style={{ borderColor: line }}>
            <Search size={16} color={inkM} />
            <input
              type="text"
              placeholder="Search train by name, ID (e.g. Set 01, Periyar, Bay A)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="bg-transparent text-xs w-full outline-none font-medium text-slate-800"
              style={{ fontFamily: SANS }}
            />
          </div>

          <div className="flex items-center gap-2">
            <span className="text-xs font-bold" style={{ fontFamily: SANS, color: inkM }}>Status Filter:</span>
            {["ALL", "IN_SERVICE", "STANDBY", "IN_MAINTENANCE"].map((st) => (
              <button
                key={st}
                onClick={() => setStatusFilter(st)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all"
                style={{
                  fontFamily: SANS,
                  background: statusFilter === st ? teal : "rgba(15,23,42,0.04)",
                  color: statusFilter === st ? "#fff" : inkB,
                }}
              >
                {st === "ALL" ? "All 25 Sets" : st === "IN_SERVICE" ? (isLoaded ? `Active (${summary.active})` : "Active") : st === "STANDBY" ? (isLoaded ? `Standby (${summary.standby})` : "Standby") : (isLoaded ? `Maintenance (${summary.maintenance})` : "Maintenance")}
              </button>
            ))}
          </div>
        </div>

        {/* Main Content Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

          {/* Left 2 Cols: Staging Tracks */}
          <div className="lg:col-span-2 flex flex-col gap-6">
            <div className="rounded-2xl overflow-hidden" style={{ background: "#fff", border: `1px solid ${line}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <div className="px-6 py-4 border-b flex items-center justify-between" style={{ borderColor: line, background: "#FAFBFC" }}>
                <div className="flex items-center gap-2">
                  <Train size={16} color={teal} />
                  <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>KMRL 25 Trainsets Roster &amp; Depot Bays</h2>
                </div>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkM }}>Showing {filteredTracks.reduce((acc, g) => acc + g.trains.length, 0)} of 25 Trains</span>
              </div>

              <div className="divide-y" style={{ borderColor: line }}>
                {filteredTracks.map((group) => (
                  <div key={group.label} className="p-5">
                    <p className="text-xs font-bold uppercase tracking-wider mb-3" style={{ fontFamily: SANS, color: inkM }}>{group.label}</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {group.trains.map((train) => {
                        const isSelected = selectedAsset?.id === train.id;
                        return (
                          <div
                            key={train.id}
                            onClick={() => setSelectedAsset(train)}
                            className="p-4 rounded-xl cursor-pointer transition-all border flex flex-col justify-between gap-2.5 hover:shadow-md"
                            style={{
                              background: isSelected ? "rgba(0,150,136,0.04)" : "#F8FAFC",
                              borderColor: isSelected ? teal : line,
                              boxShadow: isSelected ? `0 0 0 3px rgba(0,150,136,0.15)` : "none",
                            }}
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-sm font-bold" style={{ fontFamily: MONO, color: inkH }}>{train.trainNumber}</span>
                              <StatusBadge status={train.status} />
                            </div>

                            <div>
                              <p className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkB }}>
                                {train.currentDepot} · <span className="font-mono text-slate-500">{train.track}</span>
                              </p>
                              <p className="text-[11px] mt-0.5 truncate" style={{ fontFamily: SANS, color: inkM }}>
                                {train.assignedTripCode ? `Duty: ${train.assignedTripCode} (${train.assignedRoute})` : "Unassigned / Yard Standby"}
                              </p>
                            </div>

                            <div className="flex items-center justify-between pt-2 border-t text-[11px]" style={{ borderColor: line, fontFamily: SANS, color: inkM }}>
                              <span>Health Check: <strong style={{ color: train.healthIndex > 90 ? emerald : rose }}>{train.healthIndex}%</strong></span>
                              <span className="flex items-center gap-1 text-teal-700 font-semibold hover:underline">
                                Health Telemetry <ArrowRight size={10} />
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Right 1 Col: Telemetry Inspector */}
          <div className="flex flex-col gap-5">
            {selectedAsset ? (
              <div className="rounded-2xl p-6 flex flex-col gap-5 sticky top-20" style={{ background: "#fff", border: `1px solid ${line}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
                
                {/* Asset Header */}
                <div className="flex items-start justify-between border-b pb-4" style={{ borderColor: line }}>
                  <div>
                    <span className="text-[10px] font-bold uppercase tracking-wider" style={{ fontFamily: SANS, color: inkM }}>{selectedAsset.model}</span>
                    <h3 className="text-lg font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{selectedAsset.trainNumber}</h3>
                    <p className="text-xs" style={{ fontFamily: MONO, color: inkM }}>Train ID: {selectedAsset.id}</p>
                  </div>
                  <StatusBadge status={selectedAsset.status} />
                </div>

                {/* Telemetry Checks */}
                <div className="flex flex-col gap-3">
                  <p className="text-xs font-bold uppercase tracking-wider" style={{ fontFamily: SANS, color: inkM }}>Automated Telemetry &amp; Health Checks</p>
                  
                  <div className="p-3 rounded-xl flex items-center justify-between" style={{ background: "#F8FAFC", border: `1px solid ${line}` }}>
                    <div className="flex items-center gap-2">
                      <Activity size={16} color={teal} />
                      <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkB }}>Overall Health Score</span>
                    </div>
                    <span className="text-xs font-bold" style={{ fontFamily: MONO, color: selectedAsset.healthIndex > 90 ? emerald : rose }}>
                      {selectedAsset.healthIndex}% Optimal
                    </span>
                  </div>

                  <div className="p-3 rounded-xl flex items-center justify-between" style={{ background: "#F8FAFC", border: `1px solid ${line}` }}>
                    <div className="flex items-center gap-2">
                      <ShieldCheck size={16} color={emerald} />
                      <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkB }}>Pneumatic Brake Pressure</span>
                    </div>
                    <span className="text-xs font-bold font-mono" style={{ color: selectedAsset.brakePressureKpa >= 850 ? emerald : rose }}>
                      {selectedAsset.brakePressureKpa} kPa (Spec 850-950)
                    </span>
                  </div>

                  <div className="p-3 rounded-xl flex items-center justify-between" style={{ background: "#F8FAFC", border: `1px solid ${line}` }}>
                    <div className="flex items-center gap-2">
                      <MapPin size={16} color={teal} />
                      <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: inkB }}>Track Location</span>
                    </div>
                    <span className="text-xs font-bold" style={{ fontFamily: SANS, color: inkH }}>
                      {selectedAsset.track}
                    </span>
                  </div>
                </div>

                {/* Duty Assignment */}
                <div className="p-4 rounded-xl" style={{ background: "rgba(0,150,136,0.05)", border: "1px solid rgba(0,150,136,0.15)" }}>
                  <p className="text-xs font-bold mb-1" style={{ fontFamily: SANS, color: tealDk }}>OCC Schedule Duty Binding</p>
                  {selectedAsset.assignedTripCode ? (
                    <div>
                      <p className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>{selectedAsset.assignedTripCode}</p>
                      <p className="text-xs" style={{ fontFamily: SANS, color: inkM }}>Route: {selectedAsset.assignedRoute}</p>
                    </div>
                  ) : (
                    <p className="text-xs" style={{ fontFamily: SANS, color: inkM }}>
                      No active timetable run assigned. Available in Standby pool for OCC dispatch.
                    </p>
                  )}
                </div>

                {/* Filing/withdrawing is now exclusively a Maintenance-page
                    action (see MaintenancePage.tsx create-ticket flow) so
                    there's exactly one code path that decides graceful vs
                    emergency withdrawal. This card is read-only status +
                    a shortcut there instead of a second action button. */}
                <div className="flex flex-col gap-2 pt-2 border-t" style={{ borderColor: line }}>
                  <button
                    onClick={() => onNavigate("schedule")}
                    className="w-full py-2.5 rounded-xl font-bold text-xs text-white transition-all hover:opacity-90 flex items-center justify-center gap-2"
                    style={{ background: `linear-gradient(135deg, ${teal}, ${tealDk})`, fontFamily: DISPLAY }}
                  >
                    View Duty in Schedule Console <ArrowRight size={13} />
                  </button>

                  {selectedAsset.status === "IN_MAINTENANCE" ? (
                    <div className="w-full py-2.5 rounded-xl font-bold text-xs text-center border bg-slate-50 text-rose-600" style={{ fontFamily: SANS, borderColor: line }}>
                      Train Currently In Maintenance
                    </div>
                  ) : (
                    <button
                      onClick={() => onNavigate("maintenance")}
                      className="w-full py-2.5 rounded-xl font-bold text-xs text-rose-600 border border-slate-200 hover:bg-rose-50 transition-all flex items-center justify-center gap-2 cursor-pointer"
                      style={{ fontFamily: SANS }}
                    >
                      File Maintenance Ticket <ArrowRight size={13} />
                    </button>
                  )}
                </div>

              </div>
            ) : (
              <div className="rounded-2xl p-8 text-center border" style={{ background: "#fff", borderColor: line }}>
                <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>Select any of the 25 train sets from the roster to inspect telemetry.</p>
              </div>
            )}
          </div>
        </div>

        {/* Transition Audit Ledger */}
        <section className="pb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-bold" style={{ fontFamily: DISPLAY, color: inkH }}>Recent Fleet Activity</h2>
            <div className="flex items-center gap-3">
              <span className="text-xs" style={{ fontFamily: SANS, color: inkM }}>Latest status transitions</span>
              {ledger.length > LEDGER_PREVIEW_LIMIT && (
                <button
                  onClick={() => setShowAllLedger(v => !v)}
                  style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: tealDk, background: "none", border: "none", cursor: "pointer" }}
                >
                  {showAllLedger ? "Show Less" : `Show All (${ledger.length})`}
                </button>
              )}
            </div>
          </div>

          <div className="rounded-2xl overflow-hidden" style={{ background: "#fff", border: `1px solid ${line}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            {ledger.length === 0 ? (
              <div className="py-10 text-center">
                <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>No recent activity to show.</p>
              </div>
            ) : (
              (showAllLedger ? ledger : ledger.slice(0, LEDGER_PREVIEW_LIMIT)).map((row, i, arr) => {
                const [fromState, toState] = (row.vector || "").split("->").map(s => s.trim());
                return (
                  <div
                    key={row.id}
                    className="flex items-center gap-4 px-5 py-3.5 hover:bg-slate-50 transition-colors"
                    style={{ borderBottom: i < arr.length - 1 ? `1px solid ${line}` : "none" }}
                  >
                    <div style={{ width: 34, height: 34, borderRadius: 10, background: tealLt, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Train size={15} color={tealDk} />
                    </div>

                    <div style={{ minWidth: 0, flexShrink: 0, width: 130 }}>
                      <p className="text-xs font-bold truncate" style={{ fontFamily: SANS, color: inkH }}>{row.trainNumber}</p>
                    </div>

                    <div className="flex items-center gap-2 flex-1 min-w-0">
                      {fromState && toState ? (
                        <>
                          <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full" style={{ fontFamily: SANS, background: "#F1F5F9", color: inkM }}>{fromState}</span>
                          <ArrowRight size={12} color={inkM} className="shrink-0" />
                          <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full" style={{ fontFamily: SANS, background: tealLt, color: tealDk }}>{toState}</span>
                        </>
                      ) : (
                        <span className="text-xs truncate" style={{ fontFamily: SANS, color: inkB }}>{row.vector}</span>
                      )}
                    </div>

                    <span className="text-xs shrink-0" style={{ fontFamily: MONO, color: inkM }}>{row.timestamp}</span>
                  </div>
                );
              })
            )}
          </div>
        </section>

      </main>

      <SharedFooter onNavigate={onNavigate} />
    </div>
  );
}