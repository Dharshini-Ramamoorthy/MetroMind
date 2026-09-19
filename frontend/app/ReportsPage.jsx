import React, { useState, useEffect } from "react";
import { Download, Filter, BarChart2, Sparkles, RefreshCw, Eye, TrendingUp, ShieldAlert, Train, Loader2 } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";
// report-service is reached through api-gateway (port 8080), same as
// every other service - see MaintenancePage.tsx's MAINTENANCE_API_BASE_URL.
export const REPORT_API_BASE_URL = "http://localhost:8080/api/v1/reports";
// Backend enums (FLEET/SCHEDULE/... , FINAL/PROCESSING/...) -> the Title
// Case labels this page already renders with.
const CATEGORY_FROM_API = {
    FLEET: "Fleet", SCHEDULE: "Schedule", MAINTENANCE: "Maintenance",
    ALERTS: "Alerts", SAFETY: "Alerts", OVERALL: "Fleet",
};
const STATUS_FROM_API = {
    FINAL: "Final", PROCESSING: "Processing", ARCHIVED: "Archived",
};
function formatGeneratedDate(iso) {
    const d = new Date(iso);
    if (isNaN(d.getTime()))
        return iso;
    const z = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())} ${z(d.getHours())}:${z(d.getMinutes())}`;
}
// Maps a report-service ReportResponse straight onto this page's Report shape.
function fromApiReport(r) {
    return {
        id: r.id,
        title: r.title,
        category: CATEGORY_FROM_API[r.category] ?? "Fleet",
        generatedDate: r.generatedDate ? formatGeneratedDate(r.generatedDate) : "",
        author: r.author,
        fileSize: r.fileSize ?? "-",
        status: STATUS_FROM_API[r.status] ?? "Processing",
        summary: r.summary ?? "",
    };
}
// ── Design Tokens ─────────────────────────────────────────────────────────────
const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const amber = "#F59E0B";
const rose = "#EF4444";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const SANS    = "'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const MONO    = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace";
const INITIAL_REPORTS = [
    {
        id: "REP-2026-081",
        title: "Daily Operations & Headway Variance Summary",
        category: "Schedule",
        generatedDate: "2026-08-05 06:00",
        author: "OCC Operations Control",
        fileSize: "2.4 MB",
        status: "Final",
        summary: "Comprehensive headway analysis across Aluva–Thrippunithura corridor with 98.6% on-time departure compliance.",
    },
    {
        id: "REP-2026-080",
        title: "Fleet Induction & Rake Availability Log",
        category: "Fleet",
        generatedDate: "2026-08-04 18:30",
        author: "Muttom Depot Manager",
        fileSize: "4.1 MB",
        status: "Final",
        summary: "Detailed yard readiness checklist audit and mainline clearance token verifications for 25 trainsets.",
    },
    {
        id: "REP-2026-079",
        title: "Maintenance Job Cards & Component Overhaul Audit",
        category: "Maintenance",
        generatedDate: "2026-08-04 12:15",
        author: "Maintenance Lead",
        fileSize: "1.8 MB",
        status: "Final",
        summary: "Complete review of open, in-progress, and resolved job cards with component wear diagnostics.",
    },
    {
        id: "REP-2026-078",
        title: "Critical Alarm & Deviation Incident Analysis",
        category: "Alerts",
        generatedDate: "2026-08-03 22:00",
        author: "Safety & Systems Auditor",
        fileSize: "3.2 MB",
        status: "Final",
        summary: "Triage log of SEV-1 to SEV-3 alarms including traction power trip events at Sub-station 04.",
    },
];
export default function ReportsPage({ isSignedIn, onNavigate, onLogOut, userName = "User", userRole = "Operator", }) {
    const [reports, setReports] = useState(() => getCached("reports_list", INITIAL_REPORTS));
    const [loading, setLoading] = useState(false);
    const [generating, setGenerating] = useState(false);
    const [categoryFilter, setCategoryFilter] = useState("ALL");
    const [selectedReport, setSelectedReport] = useState(null);
    const [downloadingId, setDownloadingId] = useState(null);
    const [liveSummary, setLiveSummary] = useState(() => getCached("reports_live_summary", null));

    const authHeaders = () => {
        const token = localStorage.getItem("auth_token");
        return {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };
    };

    // Fetches live telemetry from report-service in a single call.
    const fetchLiveSummary = async () => {
        try {
            const res = await fetch(`${REPORT_API_BASE_URL}/live-summary`, { headers: authHeaders() });
            if (res.ok) {
                const data = await res.json();
                setLiveSummary(data);
                setCached("reports_live_summary", data);
            }
        } catch (err) {
            console.warn("Could not fetch live summary:", err);
        }
    };

    // Fetches report list, optionally backend-filtered by category.
    const fetchReports = async () => {
        const hasCached = Boolean(getCached("reports_list"));
        if (!hasCached) setLoading(true);
        try {
            const url = categoryFilter && categoryFilter !== "ALL"
                ? `${REPORT_API_BASE_URL}?category=${encodeURIComponent(categoryFilter.toUpperCase())}`
                : REPORT_API_BASE_URL;
            const res = await fetch(url, { headers: authHeaders() });
            if (res.ok) {
                const data = await res.json();
                if (Array.isArray(data) && data.length > 0) {
                    const mapped = data.map(fromApiReport);
                    mapped.sort((a, b) => new Date(b.generatedDate) - new Date(a.generatedDate));
                    setReports(mapped);
                    setCached("reports_list", mapped);
                }
            }
        } catch (err) {
            console.warn("Could not fetch reports from backend:", err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (isSignedIn) {
            fetchReports();
            fetchLiveSummary();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isSignedIn, categoryFilter]);
    const REPORTS_PREVIEW_LIMIT = 6;
    const [showAllReports, setShowAllReports] = useState(false);
    const sortedReports = [...reports].sort((a, b) => new Date(b.generatedDate) - new Date(a.generatedDate));
    const categoryFiltered = sortedReports.filter((r) => {
        return categoryFilter === "ALL" || r.category === categoryFilter;
    });
    const isReportsFiltered = categoryFilter !== "ALL";
    const filteredReports = (isReportsFiltered || showAllReports)
        ? categoryFiltered
        : categoryFiltered.slice(0, REPORTS_PREVIEW_LIMIT);
    const hiddenReportsCount = (isReportsFiltered || showAllReports)
        ? 0
        : Math.max(0, categoryFiltered.length - REPORTS_PREVIEW_LIMIT);
    const handleDownload = async (id, title) => {
        setDownloadingId(id);
        try {
            const res = await fetch(`${REPORT_API_BASE_URL}/${id}/download`, { headers: authHeaders() });
            if (!res.ok)
                throw new Error(`Download failed (${res.status})`);
            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `${title.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "")}.pdf`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
        }
        catch (err) {
            console.warn("Could not download report PDF:", err);
            alert("Could not download this report right now. Please try again.");
        }
        finally {
            setDownloadingId(null);
        }
    };
    // Builds a fresh report from LIVE fleet/maintenance/schedule data via
    // POST /api/v1/reports/generate, then refreshes the table. Uses the
    // active category filter (falling back to Fleet) so "Generate Live
    const handleGenerateLive = async () => {
        setGenerating(true);
        const categoriesToGenerate = categoryFilter === "ALL" 
            ? ["FLEET", "SCHEDULE", "MAINTENANCE", "ALERTS"]
            : [categoryFilter.toUpperCase()];
        try {
            for (const cat of categoriesToGenerate) {
                const res = await fetch(`${REPORT_API_BASE_URL}/generate`, {
                    method: "POST",
                    headers: authHeaders(),
                    body: JSON.stringify({ 
                        category: cat, 
                        author: userName || "Operator" 
                    }),
                });
                if (!res.ok) {
                    const errBody = await res.json().catch(() => ({}));
                    throw new Error(errBody.message || `Failed to generate ${cat} report (${res.status})`);
                }
            }
            await fetchReports();
        }
        catch (err) {
            console.warn("Could not generate a live report:", err);
            alert("Could not generate a report from live data right now. Please try again.");
        }
        finally {
            setGenerating(false);
        }
    };
    return (<div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
      <SharedHeader activePage="reports" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* Full-width White Header Band (Fleet Page Standard) */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>Operational Reports Console</h1>
            </div>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Comprehensive operational logs, compliance reports, and fleet telemetry records.
            </p>
          </div>

          <div style={{ display: "flex", gap: 12 }}>
            <button onClick={handleGenerateLive} disabled={generating} className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all" style={{ fontFamily: SANS, border: "none", background: teal, color: "#fff", cursor: "pointer", opacity: generating ? 0.6 : 1 }}>
              {generating && <Loader2 size={14} className="animate-spin"/>}
              {generating ? "Generating..." : "Generate Live Report"}
            </button>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

          {/* Metric Cards — live data from /api/v1/reports/live-summary */}
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 28 }}>
            {/* Card 1: Total audit reports in the directory */}
            <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${teal}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Audit Reports</p>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{reports.length}</span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: teal }}>compliance logs</span>
              </div>
              <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>
                {liveSummary ? `${liveSummary.fleet?.total ?? "—"} total trainsets tracked` : "Loading fleet data…"}
              </p>
            </div>

            {/* Card 2: Active fleet trains (fleet-service live) */}
            <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${emerald}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Mainline Active Fleet</p>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>
                  {liveSummary ? (liveSummary.fleet?.active ?? 0) : <span style={{ fontSize: 16, color: inkM }}>—</span>}
                </span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: emerald }}>in active service</span>
              </div>
              <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>
                {liveSummary ? `${liveSummary.fleet?.standby ?? 0} standby · ${liveSummary.fleet?.maintenance ?? 0} under maintenance` : "Fetching…"}
              </p>
            </div>

            {/* Card 3: Open + In-Progress maintenance tickets */}
            <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${amber}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Open Job Cards</p>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>
                  {liveSummary
                    ? ((liveSummary.maintenance?.open ?? 0) + (liveSummary.maintenance?.inProgress ?? 0))
                    : <span style={{ fontSize: 16, color: inkM }}>—</span>}
                </span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: amber }}>unresolved tickets</span>
              </div>
              <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>
                {liveSummary
                  ? `${liveSummary.maintenance?.critical ?? 0} critical · ${liveSummary.maintenance?.high ?? 0} high priority`
                  : "Fetching…"}
              </p>
            </div>

            {/* Card 4: Active trips today (schedule-service live) */}
            <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${rose}`, boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
              <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Active Trips Today</p>
              <div className="flex items-baseline gap-2">
                <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>
                  {liveSummary ? (liveSummary.schedule?.active ?? 0) : <span style={{ fontSize: 16, color: inkM }}>—</span>}
                </span>
                <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: rose }}>active runs</span>
              </div>
              <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>
                {liveSummary
                  ? `${liveSummary.schedule?.completed ?? 0} completed · ${liveSummary.schedule?.planned ?? 0} planned`
                  : "Fetching…"}
              </p>
            </div>
          </div>

          {/* Filter Bar */}
          <div style={{ background: "#fff", padding: 16, borderRadius: 12, border: `1px solid ${bd}`, display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <Filter size={15} color={inkM}/>
              <span style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB }}>Category:</span>
              {["ALL", "Fleet", "Schedule", "Maintenance", "Alerts"].map((cat) => (<button key={cat} onClick={() => setCategoryFilter(cat)} style={{
                padding: "6px 14px", borderRadius: 8, border: "none",
                background: categoryFilter === cat ? teal : "rgba(15,23,42,0.04)",
                color: categoryFilter === cat ? "#fff" : inkB,
                fontFamily: SANS, fontSize: 12.5, fontWeight: categoryFilter === cat ? 700 : 500,
                cursor: "pointer", transition: "all 0.15s"
            }}>
                  {cat}
                </button>))}
            </div>
          </div>

          {/* Reports Table */}
          <div style={{ background: "#fff", borderRadius: 16, border: `1px solid ${bd}`, overflow: "hidden", boxShadow: "0 1px 4px rgba(15,23,42,0.05)" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ borderBottom: `1px solid ${bd}`, background: "#F8FAFC" }}>
                  {["Title & Summary", "Category", "Author", "Generated", "Actions"].map((col) => (<th key={col} style={{ padding: "12px 20px", textAlign: "left", fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>
                      {col}
                    </th>))}
                </tr>
              </thead>
              <tbody>
                {filteredReports.map((rep, i) => (<tr key={rep.id} style={{ borderBottom: i < filteredReports.length - 1 ? `1px solid ${bd}` : "none" }}>
                    <td style={{ padding: "16px 20px", maxWidth: 380 }}>
                      <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: inkH, marginBottom: 4 }}>
                        {rep.title}
                      </p>
                      <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, lineHeight: 1.4 }}>
                        {rep.summary}
                      </p>
                    </td>
                    <td style={{ padding: "16px 20px" }}>
                      <span style={{
                padding: "4px 10px", borderRadius: 6, fontFamily: SANS, fontSize: 11.5, fontWeight: 700,
                background: "rgba(0,150,136,0.1)", color: teal
            }}>
                        {rep.category}
                      </span>
                    </td>
                    <td style={{ padding: "16px 20px", fontFamily: SANS, fontSize: 13, color: inkB }}>
                      {rep.author}
                    </td>
                    <td style={{ padding: "16px 20px", fontFamily: MONO, fontSize: 12, color: inkM }}>
                      {rep.generatedDate}
                    </td>
                    <td style={{ padding: "16px 20px" }}>
                      <div style={{ display: "flex", itemsCenter: "center", gap: 8 }}>
                        <button onClick={() => setSelectedReport(rep)} style={{
                display: "inline-flex", alignItems: "center", gap: 5, padding: "6px 12px",
                borderRadius: 8, border: `1px solid ${bd}`, background: "#fff", cursor: "pointer",
                fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB
            }}>
                          <Eye size={13}/> View
                        </button>
                        <button onClick={() => handleDownload(rep.id, rep.title)} disabled={downloadingId === rep.id} style={{
                display: "inline-flex", alignItems: "center", gap: 5, padding: "6px 14px",
                borderRadius: 8, border: "none", background: teal, color: "#fff", cursor: "pointer",
                fontFamily: SANS, fontSize: 12, fontWeight: 700, opacity: downloadingId === rep.id ? 0.6 : 1
            }}>
                          <Download size={13}/>
                          {downloadingId === rep.id ? "Saving..." : "PDF"}
                        </button>
                      </div>
                    </td>
                  </tr>))}
              </tbody>
            </table>
            {hiddenReportsCount > 0 && (<div style={{
                padding: "12px 20px", borderTop: `1px solid ${bd}`, textAlign: "center",
                fontFamily: SANS, fontSize: 12, color: inkM,
            }}>
                Showing the {REPORTS_PREVIEW_LIMIT} most recent reports.{" "}
                <button
                  onClick={() => setShowAllReports(true)}
                  style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer", padding: 0 }}
                >
                  Show All ({categoryFiltered.length})
                </button>
              </div>)}
            {showAllReports && !isReportsFiltered && categoryFiltered.length > REPORTS_PREVIEW_LIMIT && (<div style={{
                padding: "12px 20px", borderTop: `1px solid ${bd}`, textAlign: "center",
                fontFamily: SANS, fontSize: 12, color: inkM,
            }}>
                Showing all {categoryFiltered.length} reports.{" "}
                <button
                  onClick={() => setShowAllReports(false)}
                  style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer", padding: 0 }}
                >
                  Show Less
                </button>
              </div>)}
          </div>
      </main>

      {/* Report Preview Modal */}
      {selectedReport && (<div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", justifyContent: "center", alignItems: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 16, width: "100%", maxWidth: 560, padding: 28, border: `1px solid ${bd}`, boxShadow: "0 20px 25px -5px rgba(0,0,0,0.1)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
              <div>
                <h2 style={{ fontFamily: DISPLAY, fontSize: 18, fontWeight: 800, color: inkH, marginTop: 8 }}>
                  {selectedReport.title}
                </h2>
              </div>
              <button onClick={() => setSelectedReport(null)} style={{ background: "none", border: "none", fontSize: 20, cursor: "pointer", color: inkM }}>×</button>
            </div>

            <div style={{ background: "#F8FAFC", padding: 16, borderRadius: 12, border: `1px solid ${bd}`, marginBottom: 20 }}>
              <p style={{ fontFamily: SANS, fontSize: 13, color: inkB, lineHeight: 1.6, marginBottom: 12 }}>
                {selectedReport.summary}
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontFamily: SANS, fontSize: 12, color: inkM }}>
                <div><strong>Category:</strong> {selectedReport.category}</div>
                <div><strong>Author:</strong> {selectedReport.author}</div>
                <div><strong>Generated:</strong> {selectedReport.generatedDate}</div>
                <div><strong>File Size:</strong> {selectedReport.fileSize}</div>
              </div>
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setSelectedReport(null)} style={{ padding: "8px 16px", borderRadius: 8, border: `1px solid ${bd}`, background: "#fff", fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, cursor: "pointer" }}>
                Close
              </button>
              <button onClick={() => {
                handleDownload(selectedReport.id, selectedReport.title);
                setSelectedReport(null);
            }} style={{ padding: "8px 18px", borderRadius: 8, border: "none", background: teal, color: "#fff", fontFamily: SANS, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                Download Full Report PDF
              </button>
            </div>
          </div>
        </div>)}

      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}