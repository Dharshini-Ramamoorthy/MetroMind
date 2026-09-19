import React, { useState, useEffect } from "react";
import { Wrench, Plus, Search, Filter, Loader2, AlertCircle, CheckCircle, Clock, X, RefreshCw } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
// Goes through api-gateway (port 8080), not maintenance-service's port
// (8084) directly - maintenance-service now requires every request to be
// authenticated via the X-User-Id / X-User-Role headers that only the
// gateway's JWT filter adds, so calling :8084 straight from the browser
// gets a 401 on everything. Same pattern as SchedulePage's API_BASE.
export const MAINTENANCE_API_BASE_URL = "http://localhost:8080/api/v1/maintenance";
// MaintenanceResponse.cofDocumentUrl comes back as a relative path
// (/api/v1/maintenance/tickets/{id}/certificate-of-fitness/document) - this
// is just the gateway origin to prefix it with.
const GATEWAY_ORIGIN = "http://localhost:8080";
// ── Design Tokens ─────────────────────────────────────────────────────────────
const teal = "#009688";
const amber = "#F59E0B";
const rose = "#EF4444";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const SANS    = "'Inter', 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, Arial, sans-serif";
const MONO    = "'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace";
function StatusBadge({ status, trainPulled }) {
    const cfg = {
        OPEN: { bg: "rgba(0,150,136,0.1)", color: teal, icon: <Wrench size={10}/>, label: "OPEN" },
        IN_PROGRESS: { bg: "rgba(245,158,11,0.12)", color: "#92400E", icon: <Clock size={10}/>, label: "IN PROGRESS" },
        // Same violet used for "pending approval" elsewhere in the app
        // (SchedulePage's PROPOSED, ApproverPanelPage) - a consistent visual
        // language for "sitting on the Approver Panel, not yet decided".
        PENDING_CLOSURE: { bg: "#EDE9FE", color: "#6D28D9", icon: <Clock size={10}/>, label: "PENDING CLOSURE" },
        COMPLETED: { bg: "rgba(16,185,129,0.1)", color: "#065F46", icon: <CheckCircle size={10}/>, label: "COMPLETED" },
        REJECTED: { bg: "rgba(239,68,68,0.1)", color: rose, icon: <X size={10}/>, label: "REJECTED" },
        CANCELLED: { bg: "rgba(100,116,139,0.12)", color: inkM, icon: <X size={10}/>, label: "CANCELLED" },
    };
    // A ticket filed against a train that's mid-duty (Medium/Low/High
    // priority, not Critical) deliberately leaves that train running until
    // its current trip finishes naturally (see MaintenanceServiceImpl.createTicket
    // on the backend). Both that "still running, will be pulled shortly"
    // window and "actually sitting in the workshop right now" show up as
    // the same IN_PROGRESS status - trainPulled is what tells them apart,
    // so this overrides the badge specifically for that one case rather
    // than implying the train is already out of service when it isn't.
    const s = (status === "IN_PROGRESS" && trainPulled === false)
        ? { bg: "rgba(0,150,136,0.1)", color: teal, icon: <Clock size={10}/>, label: "WILL BE MOVED TO MAINTENANCE" }
        : cfg[status];
    return (<span style={{
            display: "inline-flex", alignItems: "center", gap: 5, padding: "4px 10px", borderRadius: 999,
            fontFamily: SANS, fontSize: 11.5, fontWeight: 700, background: s.bg, color: s.color,
        }}>
      {s.icon}
      {s.label}
    </span>);
}
export default function MaintenancePage({ user, userName = user?.username || "Maintenance Manager", userRole = user?.role || "MaintenanceManager", isSignedIn, onNavigate, onLogOut, }) {
    // Live State
    const [records, setRecords] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    // Filters & Search
    const [searchQuery, setSearchQuery] = useState("");
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [showAllResolved, setShowAllResolved] = useState(false);
    // Modal State for New Job Card
    const [showModal, setShowModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formData, setFormData] = useState({
        trainId: "",
        type: "Routine Check",
        priority: "Medium",
        description: "",
    });
    // ── Fetch DB Records ────────────────────────────────────────────────────────
    const fetchRecords = async () => {
        setLoading(true);
        setError(null);
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };
        try {
            const response = await fetch(`${MAINTENANCE_API_BASE_URL}/tickets`, { headers });
            if (response.ok) {
                const data = await response.json();
                const rawList = Array.isArray(data) ? data : [];
                const mappedList = rawList.map((item) => {
                    let prio = "Medium";
                    if (item.priority) {
                        const p = String(item.priority).toUpperCase();
                        if (p === "CRITICAL")
                            prio = "Critical";
                        else if (p === "HIGH")
                            prio = "High";
                        else if (p === "LOW")
                            prio = "Low";
                        else
                            prio = "Medium";
                    }
                    let st = "IN_PROGRESS";
                    const VALID_STATUSES = ["OPEN", "IN_PROGRESS", "PENDING_CLOSURE", "COMPLETED", "REJECTED", "CANCELLED"];
                    if (item.status) {
                        const s = String(item.status).toUpperCase();
                        if (VALID_STATUSES.includes(s))
                            st = s;
                    }
                    return {
                        id: item.id || item._id || "CARD-001",
                        trainId: item.trainNumber || item.trainId || item.train || "KMRL-101",
                        type: item.description || item.type || "Routine Inspection",
                        priority: prio,
                        status: st,
                        assignedTo: item.createdBy || item.assignedTo || "Maintenance Manager",
                        createdAt: item.createdAt || new Date().toISOString(),
                        description: item.description || item.type || "",
                        cofDocumentUrl: item.cofDocumentUrl,
                        cofDocumentOriginalFileName: item.cofDocumentOriginalFileName,
                        cofSubmittedBy: item.cofSubmittedBy,
                        cofSubmittedAt: item.cofSubmittedAt,
                        trainPulled: item.trainPulled !== undefined ? item.trainPulled : true,
                    };
                });
                setRecords(mappedList);
            }
            else {
                throw new Error(`Failed to fetch records: ${response.statusText}`);
            }
        }
        catch (err) {
            console.warn("DB Connection Error:", err);
            setError("Unable to load maintenance records right now. Please try again.");
        }
        finally {
            setLoading(false);
        }
    };
    useEffect(() => {
        fetchRecords();
    }, []);
    // ── Create New Job Card in DB ────────────────────────────────────────────────
    const handleCreateJobCard = async (e) => {
        e.preventDefault();
        if (!formData.trainId.trim()) {
            alert("Trainset is required.");
            return;
        }
        setSubmitting(true);
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };
        const payload = {
            // Trimmed - a stray leading/trailing space here would silently fail
            // to match the exact id fleet-service expects (e.g. "TS-04 " !==
            // "TS-04"), producing the same kind of untracked-ticket problem a
            // wrong identifier does, just harder to spot by eye.
            trainNumber: formData.trainId.trim(),
            description: formData.type + (formData.description ? ` - ${formData.description}` : ""),
            priority: formData.priority.toUpperCase(),
            // No status field - CreateTicketRequest on the backend has no such
            // field at all (a ticket always starts IN_PROGRESS server-side, see
            // MaintenanceServiceImpl.createTicket). Sending one used to be dead
            // weight Jackson silently ignored.
            createdBy: userName,
            createdAt: new Date().toISOString(),
        };
        try {
            const response = await fetch(`${MAINTENANCE_API_BASE_URL}/tickets`, {
                method: "POST",
                headers,
                body: JSON.stringify(payload),
            });
            if (response.ok) {
                setShowModal(false);
                setFormData({ trainId: "", type: "Routine Check", priority: "Medium", description: "" });
                fetchRecords(); // Refresh list from DB
            }
            else if (response.status === 403) {
                alert("Your role does not have permission to create maintenance job cards.");
            }
            else {
                // Was a hardcoded "Failed to create job card. Please try again."
                // regardless of why - which is exactly what made this bug
                // impossible to self-diagnose. GlobalExceptionHandler always
                // returns { message: "..." } with the real, specific reason (e.g.
                // "no train with id 'X' exists on fleet-service", "already has an
                // unresolved ticket") - show that instead.
                let reason = "Please try again.";
                try {
                    const body = await response.json();
                    if (body?.message)
                        reason = body.message;
                }
                catch {
                    // Response wasn't JSON (e.g. a raw 5xx from a proxy) - fall back
                    // to the generic reason rather than throwing here.
                }
                alert(`Failed to create job card: ${reason}`);
            }
        }
        catch (err) {
            console.error("Error creating job card:", err);
            alert("Could not reach the maintenance service. Check your connection and try again.");
        }
        finally {
            setSubmitting(false);
        }
    };
    // ── Cancel a mistaken ticket (IN_PROGRESS -> CANCELLED) ─────────────────────
    // This is the ONLY manual PATCH transition MaintenanceServiceImpl still
    // allows from IN_PROGRESS (see its ALLOWED_TRANSITIONS) - no approval
    // needed, same reasoning as creating the ticket in the first place.
    // There is deliberately no generic "mark as any status" control anymore:
    // IN_PROGRESS -> PENDING_CLOSURE only happens via filing a real CoF
    // (handleSubmitCof below), and PENDING_CLOSURE -> COMPLETED/IN_PROGRESS
    // only happens through a SADA decision on the Approver Panel, never a
    // direct edit here.
    const handleCancelTicket = async (id) => {
        if (!window.confirm("Cancel this ticket and return the train to Standby? This can't be undone."))
            return;
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };
        try {
            const response = await fetch(`${MAINTENANCE_API_BASE_URL}/tickets/${id}/status`, {
                method: "PATCH",
                headers,
                body: JSON.stringify({
                    status: "CANCELLED",
                    approverComments: `Ticket cancelled by ${userName}`,
                }),
            });
            if (response.ok) {
                fetchRecords();
            }
            else if (response.status === 403) {
                alert("Your role does not have permission to cancel maintenance tickets.");
            }
            else {
                let reason = "Please try again.";
                try {
                    const body = await response.json();
                    if (body?.message)
                        reason = body.message;
                }
                catch { }
                alert(`Could not cancel ticket: ${reason}`);
            }
        }
        catch (err) {
            console.error("Failed to cancel ticket:", err);
            alert("Could not reach the maintenance service. Check your connection and try again.");
        }
    };
    // ── Certificate of Fitness modal state ───────────────────────────────────────
    const [cofTicketId, setCofTicketId] = useState(null);
    const [cofSubmitting, setCofSubmitting] = useState(false);
    const [cofForm, setCofForm] = useState({ engineerName: "", remarks: "" });
    const [cofFile, setCofFile] = useState(null);
    const openCofModal = (id) => {
        setCofTicketId(id);
        setCofForm({ engineerName: "", remarks: "" });
        setCofFile(null);
    };
    // Files a Certificate of Fitness: multipart/form-data with a "data" JSON
    // part (engineerName, remarks) and a "document" file part - see
    // MaintenanceController.submitCertificateOfFitness. Moves the ticket
    // IN_PROGRESS -> PENDING_CLOSURE and raises a CERTIFICATE_OF_FITNESS
    // approval task for a SADA to decide on (never MDS - the four-eyes
    // principle this whole flow exists for means whoever files the CoF
    // can't also be the one approving it).
    const handleSubmitCof = async (e) => {
        e.preventDefault();
        if (!cofTicketId)
            return;
        if (!cofForm.engineerName.trim()) {
            alert("Engineer name is required.");
            return;
        }
        if (!cofFile) {
            alert("Please attach the Certificate of Fitness document (PDF, JPEG, or PNG).");
            return;
        }
        setCofSubmitting(true);
        const token = localStorage.getItem("auth_token");
        const body = new FormData();
        // "data" is sent as a Blob with an explicit application/json type so
        // Spring's @RequestPart("data") CertificateOfFitnessRequest binds it
        // correctly - a plain string part would arrive as text/plain and fail
        // to deserialize.
        body.append("data", new Blob([JSON.stringify({
                engineerName: cofForm.engineerName.trim(),
                remarks: cofForm.remarks.trim() || undefined,
            })], { type: "application/json" }));
        body.append("document", cofFile);
        try {
            // Deliberately no "Content-Type" header here - the browser sets
            // multipart/form-data with the correct boundary itself when the
            // body is a FormData; setting it manually (as every other call in
            // this file does for JSON) would omit the boundary and the request
            // would fail to parse server-side.
            const response = await fetch(`${MAINTENANCE_API_BASE_URL}/tickets/${cofTicketId}/certificate-of-fitness`, {
                method: "POST",
                headers: token ? { Authorization: `Bearer ${token}` } : undefined,
                body,
            });
            if (response.ok) {
                setCofTicketId(null);
                fetchRecords();
            }
            else if (response.status === 403) {
                alert("Your role does not have permission to file a Certificate of Fitness.");
            }
            else {
                let reason = "Please try again.";
                try {
                    const b = await response.json();
                    if (b?.message)
                        reason = b.message;
                }
                catch { }
                alert(`Could not submit Certificate of Fitness: ${reason}`);
            }
        }
        catch (err) {
            console.error("Failed to submit Certificate of Fitness:", err);
            alert("Could not reach the maintenance service. Check your connection and try again.");
        }
        finally {
            setCofSubmitting(false);
        }
    };
    // Streams the CoF document back with auth headers attached, then opens
    // it - a plain <a href> can't carry the Bearer token, and this endpoint
    // requires one same as every other maintenance-service route.
    const handleViewCofDocument = async (rec) => {
        if (!rec.cofDocumentUrl)
            return;
        const token = localStorage.getItem("auth_token");
        try {
            const response = await fetch(`${GATEWAY_ORIGIN}${rec.cofDocumentUrl}`, {
                headers: token ? { Authorization: `Bearer ${token}` } : undefined,
            });
            if (!response.ok) {
                alert("Could not load the Certificate of Fitness document.");
                return;
            }
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            window.open(url, "_blank");
            // Give the new tab a moment to actually load the blob before revoking.
            setTimeout(() => URL.revokeObjectURL(url), 30000);
        }
        catch (err) {
            console.error("Failed to load CoF document:", err);
            alert("Could not reach the maintenance service. Check your connection and try again.");
        }
    };
    // ── Client Filtering ────────────────────────────────────────────────────────
    // OPEN/IN_PROGRESS/PENDING_CLOSURE are still-open work - hiding any of
    // those behind a "recent 10" cap would mean an active ticket could
    // silently scroll out of view for MDS/SADA, which is exactly the thing
    // this page exists to surface. Only COMPLETED/CANCELLED/REJECTED
    // (resolved, nothing left to act on) get capped to the 10 most recent -
    // older resolved tickets are still there, just reachable via search or
    // by picking their status in the filter dropdown instead of being shown
    // by default.
    const UNRESOLVED_STATUSES = ["OPEN", "IN_PROGRESS", "PENDING_CLOSURE"];
    const RECENT_RESOLVED_LIMIT = 10;
    const matchedRecords = records.filter((rec) => {
        const matchesSearch = rec.id?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            rec.trainId?.toLowerCase().includes(searchQuery.toLowerCase()) ||
            rec.type?.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesStatus = statusFilter === "ALL" || rec.status === statusFilter;
        return matchesSearch && matchesStatus;
    });
    // Actively searching or filtering to a specific status is an explicit
    // request to see something beyond "what's recent" - the cap only
    // applies to the default, unfiltered view.
    const isNarrowedByUser = searchQuery.trim().length > 0 || statusFilter !== "ALL";
    const sortedByRecency = [...matchedRecords].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    const unresolved = sortedByRecency.filter((r) => UNRESOLVED_STATUSES.includes(r.status));
    const resolved = sortedByRecency.filter((r) => !UNRESOLVED_STATUSES.includes(r.status));
    const visibleResolved = (isNarrowedByUser || showAllResolved) ? resolved : resolved.slice(0, RECENT_RESOLVED_LIMIT);
    const hiddenResolvedCount = (isNarrowedByUser || showAllResolved) ? 0 : Math.max(0, resolved.length - RECENT_RESOLVED_LIMIT);
    const filteredRecords = [...unresolved, ...visibleResolved].sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    // Maintenance page mirrors Schedule page's role rules, inverted:
    // MaintenanceController's POST /tickets and PATCH /tickets/{id}/status
    // are @PreAuthorize("hasAnyRole('MDS','SADA')") on the backend - OC
    // (Operations Controller) is read-only here, same as MDS is read-only
    // on the Schedule page's "Propose Timetable Run" action.
    const canManage = userRole === "ROLE_MAINTENANCE_MANAGER" || userRole === "ROLE_ADMIN";
    return (<div style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
      <SharedHeader activePage="maintenance" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* Page Header Bar (Standardized Enterprise Container) */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div style={{ maxWidth: 1380, width: "100%", margin: "0 auto", padding: "24px 32px", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 16 }}>
          <div>
            <h1 style={{ fontFamily: DISPLAY, fontSize: 22, fontWeight: 800, color: inkH, margin: 0 }}>
              Maintenance Job Cards &amp; Fitness Certificates
            </h1>
            <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, marginTop: 4, margin: 0 }}>
              MetroMind KMRL Operations Control System · Muttom Depot Yard Maintenance &amp; Certificate of Fitness Workflow
            </p>
          </div>
          <div style={{ display: "flex", gap: 10 }}>
            <button onClick={fetchRecords} disabled={loading} style={{
            display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 16px",
            borderRadius: 10, border: `1px solid ${bd}`, background: "#fff", cursor: "pointer",
            fontFamily: SANS, fontSize: 12, fontWeight: 600, color: inkB, opacity: loading ? 0.6 : 1
        }}>
              <RefreshCw size={13} className={loading ? "animate-spin" : ""}/> Sync Telemetry
            </button>
            {canManage && (<button onClick={() => setShowModal(true)} style={{
                display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 16px",
                borderRadius: 10, border: "none", background: teal, cursor: "pointer",
                fontFamily: DISPLAY, fontSize: 12, fontWeight: 700, color: "#fff"
            }}>
                <Plus size={14}/> New Job Card
              </button>)}
          </div>
        </div>
      </div>

      <main style={{ flex: 1, maxWidth: 1380, width: "100%", margin: "0 auto", padding: "24px 32px 40px" }}>

          {/* Search & Filter Controls */}
          <div style={{ background: "#fff", padding: 16, borderRadius: 12, border: `1px solid ${bd}`, display: "flex", gap: 16, marginBottom: 24, alignItems: "center" }}>
            <div style={{ flex: 1, position: "relative" }}>
              <Search size={16} color={inkM} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)" }}/>
              <input type="text" placeholder="Search by Trainset or Type..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} style={{
            width: "100%", padding: "9px 12px 9px 36px", borderRadius: 8, border: `1px solid ${bd}`,
            fontFamily: SANS, fontSize: 13, color: inkH, outline: "none"
        }}/>
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <Filter size={14} color={inkM}/>
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={{
            padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
            fontFamily: SANS, fontSize: 13, color: inkH, outline: "none", background: "#fff"
        }}>
                <option value="ALL">All Statuses</option>
                <option value="IN_PROGRESS">In Progress</option>
                <option value="PENDING_CLOSURE">Pending Closure</option>
                <option value="COMPLETED">Completed</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </div>
          </div>

          {/* Error Banner */}
          {error && (<div style={{ padding: 16, background: "#FEF2F2", border: "1px solid #FCA5A5", borderRadius: 12, color: rose, marginBottom: 24, display: "flex", alignItems: "center", gap: 10 }}>
              <AlertCircle size={18}/>
              <span style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600 }}>{error}</span>
            </div>)}

          {/* DB Records Table */}
          <div style={{ background: "#fff", borderRadius: 16, border: `1px solid ${bd}`, overflow: "hidden", boxShadow: "0 1px 4px rgba(15,23,42,0.05)" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead>
                <tr style={{ borderBottom: `1px solid ${bd}`, background: "#F8FAFC" }}>
                  {["Trainset", "Maintenance Type", "Priority", "Status", "Actions"].map((col) => (<th key={col} style={{ padding: "12px 20px", textAlign: "left", fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>
                      {col}
                    </th>))}
                </tr>
              </thead>
              <tbody>
                {filteredRecords.length === 0 ? (<tr>
                    <td colSpan={5} style={{ padding: "36px", textAlign: "center", color: inkM, fontFamily: SANS, fontSize: 13.5 }}>
                      {loading ? "Loading records..." : "No matching maintenance records found."}
                    </td>
                  </tr>) : (filteredRecords.map((rec, i) => (<tr key={rec.id || i} style={{ borderBottom: i < filteredRecords.length - 1 ? `1px solid ${bd}` : "none" }}>
                      <td style={{ padding: "14px 20px", fontFamily: MONO, fontSize: 12.5, color: inkB }}>
                        {rec.trainId}
                      </td>
                      <td style={{ padding: "14px 20px", fontFamily: SANS, fontSize: 13, color: inkB }}>
                        {rec.type}
                      </td>
                      <td style={{ padding: "14px 20px" }}>
                        <span title={rec.priority === "Critical" ? "Emergency withdrawal: pulled immediately, mid-trip if necessary." : undefined} style={{
                display: "inline-flex", alignItems: "center", gap: 5,
                padding: "3px 8px", borderRadius: 6, fontFamily: SANS, fontSize: 11, fontWeight: 700,
                background: rec.priority === "Critical" ? "rgba(220,38,38,0.12)" : rec.priority === "High" ? "rgba(239,68,68,0.1)" : rec.priority === "Medium" ? "rgba(245,158,11,0.1)" : "rgba(100,116,139,0.1)",
                color: rec.priority === "Critical" ? "#DC2626" : rec.priority === "High" ? rose : rec.priority === "Medium" ? amber : inkM
            }}>
                          {rec.priority === "Critical" && <AlertCircle size={11}/>}
                          {rec.priority}
                        </span>
                      </td>
                      <td style={{ padding: "14px 20px" }}>
                        <StatusBadge status={rec.status} trainPulled={rec.trainPulled}/>
                      </td>
                      <td style={{ padding: "14px 20px" }}>
                        {!canManage ? (<span style={{ fontFamily: SANS, fontSize: 11.5, color: inkM, fontStyle: "italic" }} title="Only Maintenance Supervisors and System Admins can act on maintenance tickets.">
                            Read-only
                          </span>) : rec.status === "IN_PROGRESS" || rec.status === "OPEN" ? (<div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                            <button onClick={() => openCofModal(rec.id)} style={{
                    padding: "5px 12px", borderRadius: 7, border: "none", background: teal,
                    fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: "#fff", cursor: "pointer",
                }}>
                              File CoF
                            </button>
                            <button onClick={() => handleCancelTicket(rec.id)} style={{
                    padding: "5px 12px", borderRadius: 7, border: `1px solid ${bd}`, background: "#fff",
                    fontFamily: SANS, fontSize: 11.5, fontWeight: 600, color: rose, cursor: "pointer",
                }}>
                              Cancel
                            </button>
                          </div>) : rec.status === "PENDING_CLOSURE" ? (<div style={{ display: "flex", flexDirection: "column", gap: 4, alignItems: "flex-start" }}>
                            <span style={{ fontFamily: SANS, fontSize: 11.5, color: "#7C3AED", fontWeight: 700 }}>
                              Awaiting SADA Approval
                            </span>
                            {rec.cofDocumentUrl && (<button onClick={() => handleViewCofDocument(rec)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", fontFamily: SANS, fontSize: 11, color: teal, textDecoration: "underline" }}>
                                View CoF document
                              </button>)}
                          </div>) : rec.cofDocumentUrl ? (<button onClick={() => handleViewCofDocument(rec)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", fontFamily: SANS, fontSize: 11, color: teal, textDecoration: "underline" }}>
                            View CoF document
                          </button>) : (<span style={{ fontFamily: SANS, fontSize: 11.5, color: inkM }}>—</span>)}
                      </td>
                    </tr>)))}
              </tbody>
            </table>
            {hiddenResolvedCount > 0 && (<div style={{
                padding: "10px 20px", borderTop: `1px solid ${bd}`, textAlign: "center",
                fontFamily: SANS, fontSize: 12, color: inkM,
            }}>
                Showing the {RECENT_RESOLVED_LIMIT} most recent completed/closed tickets.{" "}
                {hiddenResolvedCount} older {hiddenResolvedCount === 1 ? "one is" : "ones are"} hidden.{" "}
                <button
                  onClick={() => setShowAllResolved(true)}
                  style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer", padding: 0 }}
                >
                  Show All ({resolved.length})
                </button>
              </div>)}
            {showAllResolved && !isNarrowedByUser && resolved.length > RECENT_RESOLVED_LIMIT && (<div style={{
                padding: "10px 20px", borderTop: `1px solid ${bd}`, textAlign: "center",
                fontFamily: SANS, fontSize: 12, color: inkM,
            }}>
                Showing all {resolved.length} completed/closed tickets.{" "}
                <button
                  onClick={() => setShowAllResolved(false)}
                  style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: teal, background: "none", border: "none", cursor: "pointer", padding: 0 }}
                >
                  Show Less
                </button>
              </div>)}
          </div>
      </main>

      {/* New Job Card Modal */}
      {showModal && (<div style={{
                position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)",
                display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50,
            }} onClick={() => !submitting && setShowModal(false)}>
          <div style={{
                background: "#fff", borderRadius: 16, padding: 28, width: "min(480px, 92vw)",
                boxShadow: "0 20px 40px rgba(15,23,42,0.25)"
            }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 19, fontWeight: 800, color: inkH }}>
                New Job Card
              </h2>
              <button onClick={() => !submitting && setShowModal(false)} style={{ background: "none", border: "none", cursor: "pointer", color: inkM, padding: 4 }} aria-label="Close">
                <X size={18}/>
              </button>
            </div>

            <form onSubmit={handleCreateJobCard} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Trainset ID *
                </label>
                <input type="text" required placeholder="e.g. TS-04" value={formData.trainId} onChange={(e) => setFormData({ ...formData, trainId: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: MONO, fontSize: 13, color: inkH, outline: "none"
            }}/>
                <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, marginTop: 4 }}>
                  Must match the train's ID on the Fleet dashboard exactly, so it links to the right trainset there.
                </p>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Maintenance Type
                </label>
                <select value={formData.type} onChange={(e) => setFormData({ ...formData, type: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 13, color: inkH, outline: "none", background: "#fff"
            }}>
                  <option>Routine Check</option>
                  <option>Brake Inspection</option>
                  <option>Wheel Overhaul</option>
                  <option>Electrical Fault</option>
                  <option>HVAC Repair</option>
                  <option>Bogie Servicing</option>
                </select>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Priority
                </label>
                <select value={formData.priority} onChange={(e) => setFormData({ ...formData, priority: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 13, color: inkH, outline: "none", background: "#fff"
            }}>
                  <option>Critical</option>
                  <option>High</option>
                  <option>Medium</option>
                  <option>Low</option>
                </select>
                <p style={{ fontFamily: SANS, fontSize: 11, color: formData.priority === "Critical" ? rose : inkM, marginTop: 4, lineHeight: 1.5 }}>
                  {formData.priority === "Critical"
                ? "Emergency withdrawal: if this train is mid-trip right now, it's pulled immediately and swapped for a standby train. Reserve this for safety-critical defects (brakes, doors, signalling)."
                : "Graceful withdrawal: if this train is mid-trip right now, it finishes its current run and is released to maintenance once that trip ends — nothing is interrupted for passengers."}
                </p>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Description
                </label>
                <textarea rows={3} placeholder="Optional details for this job card..." value={formData.description} onChange={(e) => setFormData({ ...formData, description: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 13, color: inkH, outline: "none", resize: "vertical"
            }}/>
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 6 }}>
                <button type="button" onClick={() => setShowModal(false)} disabled={submitting} style={{
                padding: "9px 16px", borderRadius: 8, border: `1px solid ${bd}`, background: "#fff",
                fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, cursor: "pointer"
            }}>
                  Cancel
                </button>
                <button type="submit" disabled={submitting} style={{
                padding: "9px 18px", borderRadius: 8, border: "none", background: teal,
                fontFamily: SANS, fontSize: 13, fontWeight: 700, color: "#fff",
                cursor: submitting ? "default" : "pointer", opacity: submitting ? 0.7 : 1,
                display: "inline-flex", alignItems: "center", gap: 6
            }}>
                  {submitting && <Loader2 size={14} className="animate-spin"/>}
                  {submitting ? "Creating..." : "Create Job Card"}
                </button>
              </div>
            </form>
          </div>
        </div>)}

      {/* File Certificate of Fitness Modal */}
      {cofTicketId && (<div style={{
                position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)",
                display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50,
            }} onClick={() => !cofSubmitting && setCofTicketId(null)}>
          <div style={{
                background: "#fff", borderRadius: 16, padding: 28, width: "min(480px, 92vw)",
                boxShadow: "0 20px 40px rgba(15,23,42,0.25)"
            }} onClick={(e) => e.stopPropagation()}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 19, fontWeight: 800, color: inkH }}>
                File Certificate of Fitness
              </h2>
              <button onClick={() => !cofSubmitting && setCofTicketId(null)} style={{ background: "none", border: "none", cursor: "pointer", color: inkM, padding: 4 }} aria-label="Close">
                <X size={18}/>
              </button>
            </div>
            <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, marginBottom: 20, lineHeight: 1.55 }}>
              Submitting this moves the ticket to Pending Closure and sends it to the Approver Panel for a System Admin's sign-off — the train stays in maintenance until it's approved.
            </p>

            <form onSubmit={handleSubmitCof} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Engineer Name *
                </label>
                <input type="text" required placeholder="e.g. Rajesh Kumar" value={cofForm.engineerName} onChange={(e) => setCofForm({ ...cofForm, engineerName: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 13, color: inkH, outline: "none"
            }}/>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Remarks
                </label>
                <textarea rows={3} placeholder="Optional notes on the work performed..." value={cofForm.remarks} onChange={(e) => setCofForm({ ...cofForm, remarks: e.target.value })} style={{
                width: "100%", padding: "9px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 13, color: inkH, outline: "none", resize: "vertical"
            }}/>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkB, display: "block", marginBottom: 5 }}>
                  Certificate Document (PDF, JPEG, or PNG) *
                </label>
                <input type="file" required accept="application/pdf,image/jpeg,image/png" onChange={(e) => setCofFile(e.target.files?.[0] ?? null)} style={{
                width: "100%", padding: "8px 10px", borderRadius: 8, border: `1px solid ${bd}`,
                fontFamily: SANS, fontSize: 12.5, color: inkB, outline: "none", background: "#fff"
            }}/>
                <p style={{ fontFamily: SANS, fontSize: 11, color: inkM, marginTop: 4 }}>
                  Max 10MB. This is the actual signed-off certificate the approver reviews before clearing the train.
                </p>
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 6 }}>
                <button type="button" onClick={() => setCofTicketId(null)} disabled={cofSubmitting} style={{
                padding: "9px 16px", borderRadius: 8, border: `1px solid ${bd}`, background: "#fff",
                fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, cursor: "pointer"
            }}>
                  Cancel
                </button>
                <button type="submit" disabled={cofSubmitting} style={{
                padding: "9px 18px", borderRadius: 8, border: "none", background: teal,
                fontFamily: SANS, fontSize: 13, fontWeight: 700, color: "#fff",
                cursor: cofSubmitting ? "default" : "pointer", opacity: cofSubmitting ? 0.7 : 1,
                display: "inline-flex", alignItems: "center", gap: 6
            }}>
                  {cofSubmitting && <Loader2 size={14} className="animate-spin"/>}
                  {cofSubmitting ? "Submitting..." : "Submit for Approval"}
                </button>
              </div>
            </form>
          </div>
        </div>)}

      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}