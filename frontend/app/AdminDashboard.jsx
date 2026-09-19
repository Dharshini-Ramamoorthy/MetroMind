import React, { useState, useEffect } from "react";
import { Users, Database, TrendingUp, ClipboardCheck, Cpu, ArrowRight, Loader2, UserCheck, UserX, Shield, RefreshCw, CheckCircle, XCircle } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

export const API_BASE_URL = "http://localhost:8080";
export const USER_MGMT_API_BASE_URL = `${API_BASE_URL}/api/v1/users`;
export const APPROVER_API_BASE_URL = `${API_BASE_URL}/api/approver`;

const teal = "#009688";
const tealDk = "#00786B";
const tealLight = "#E0F2F1";
const emerald = "#10B981";
const emeraldSoft = "#E6F4EA";
const amber = "#F59E0B";
const amberSoft = "#FEF7E0";
const rose = "#EF4444";
const roseSoft = "#FEE2E2";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const shadow = "0 1px 3px rgba(0,0,0,0.05), 0 1px 2px -1px rgba(0,0,0,0.05)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";

const ROLE_BADGES = {
  ADMIN: { label: "ADMIN", bg: "#F3E8FF", color: "#6B21A8" },
  SADA: { label: "SADA", bg: "#E0F2FE", color: "#0369A1" },
  OC: { label: "OC", bg: "#E0F2F1", color: "#00786B" },
  MDS: { label: "MDS", bg: "#FEF3C7", color: "#92400E" },
};

export default function AdminDashboard({ isSignedIn, onNavigate, onLogOut, userName = "Admin", userRole = "Admin" }) {
    const firstName = userName.split(" ")[0];
    const [loading, setLoading] = useState(() => !getCached("admin_users"));
    const [users, setUsers] = useState(() => getCached("admin_users", []));
    const [userMgmtError, setUserMgmtError] = useState(null);
    const [pendingApprovals, setPendingApprovals] = useState(() => getCached("admin_pending_tasks", []));
    const [pendingApprovalsCount, setPendingApprovalsCount] = useState(() => getCached("admin_pending_count", 0));
    const [urgentCount, setUrgentCount] = useState(() => getCached("admin_urgent_count", 0));
    const [approverError, setApproverError] = useState(null);
    const [actionId, setActionId] = useState(null);

    const fetchAdminDbData = async () => {
        const hasCache = Boolean(getCached("admin_users"));
        if (!hasCache) setLoading(true);
        const token = localStorage.getItem("auth_token");
        const headers = {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
        };

        const [usersResult, approverResult] = await Promise.allSettled([
            fetch(USER_MGMT_API_BASE_URL, { headers }).then((res) => {
                if (!res.ok) throw new Error(`Users API ${res.status}`);
                return res.json();
            }),
            fetch(`${APPROVER_API_BASE_URL}/tasks/pending`, { headers }).then((res) => {
                if (!res.ok) throw new Error(`Approver API ${res.status}`);
                return res.json();
            }),
        ]);

        if (usersResult.status === "fulfilled") {
            const userList = Array.isArray(usersResult.value) ? usersResult.value : [];
            setUsers(userList);
            setCached("admin_users", userList);
            setUserMgmtError(null);
        } else {
            console.warn("Could not fetch users in Admin Dashboard:", usersResult.reason);
            setUserMgmtError("Failed to load user records from user-service.");
        }

        if (approverResult.status === "fulfilled") {
            const allTasks = Array.isArray(approverResult.value) ? approverResult.value : [];
            const registrationTasks = allTasks.filter(t => t.requestType === "USER_REGISTRATION" || String(t.title || "").toUpperCase().includes("REGISTRATION"));
            const mapped = registrationTasks.map((t, idx) => {
                const p = String(t.priority || "").toUpperCase();
                const isHigh = p === "HIGH" || p === "CRITICAL";
                return {
                    id: t.taskId || String(idx),
                    title: t.title || "User Registration Request",
                    description: t.description || "",
                    time: t.createdAt ? new Date(t.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "Recently",
                    badge: isHigh ? "High priority" : "Standard",
                    badgeBg: isHigh ? amberSoft : tealLight,
                    badgeColor: isHigh ? "#B06000" : tealDk,
                    isHigh,
                };
            });
            setPendingApprovals(mapped);
            setPendingApprovalsCount(mapped.length);
            const urgent = mapped.filter((m) => m.isHigh).length;
            setUrgentCount(urgent);
            setCached("admin_pending_tasks", mapped);
            setCached("admin_pending_count", mapped.length);
            setCached("admin_urgent_count", urgent);
            setApproverError(null);
        } else {
            setApproverError(approverResult.reason instanceof Error ? approverResult.reason.message : "Approver service unavailable.");
        }
        setLoading(false);
    };

    useEffect(() => {
        fetchAdminDbData();
        const iv = setInterval(fetchAdminDbData, 4000);
        return () => clearInterval(iv);
    }, []);

    const handleRoleChange = async (userId, newRole) => {
        setActionId(userId);
        const token = localStorage.getItem("auth_token");
        try {
            const res = await fetch(`${USER_MGMT_API_BASE_URL}/${userId}/role`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({ role: newRole }),
            });
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || `HTTP ${res.status}`);
            }
            const updatedUser = await res.json();
            setUsers((prev) => prev.map((u) => (u.id === userId ? updatedUser : u)));
        } catch (e) {
            alert(`Failed to change role: ${e.message}`);
        } finally {
            setActionId(null);
        }
    };

    const handleToggleStatus = async (userId, currentActive) => {
        setActionId(userId);
        const token = localStorage.getItem("auth_token");
        try {
            const res = await fetch(`${USER_MGMT_API_BASE_URL}/${userId}/status`, {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({ active: !currentActive }),
            });
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || `HTTP ${res.status}`);
            }
            const updatedUser = await res.json();
            setUsers((prev) => prev.map((u) => (u.id === userId ? updatedUser : u)));
        } catch (e) {
            alert(`Failed to update status: ${e.message}`);
        } finally {
            setActionId(null);
        }
    };

    const handleDecideTask = async (taskId, decision) => {
        setActionId(taskId);
        const token = localStorage.getItem("auth_token");
        try {
            const res = await fetch(`${APPROVER_API_BASE_URL}/tasks/${taskId}/decision`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
                body: JSON.stringify({ decision }),
            });
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || `HTTP ${res.status}`);
            }
            fetchAdminDbData();
        } catch (e) {
            alert(`Decision failed: ${e.message}`);
        } finally {
            setActionId(null);
        }
    };

    return (
        <div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
            <SharedHeader activePage="admindashboard" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole}/>

      {/* Full-width White Header Band (Fleet Page Standard) */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div className="max-w-7xl mx-auto px-6 py-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH }}>System Administration — Welcome back, {firstName}</h1>
            </div>
            <p className="text-sm" style={{ fontFamily: SANS, color: inkM }}>
              Manage user accounts, assign roles (ADMIN, OC, MDS, SADA), and approve pending registrations.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-8 flex flex-col gap-8">

                    {/* Stat cards (Fleet Card Design) */}
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 16, marginBottom: 28 }} className="adm-stats">
                        {[
                            {
                                accent: teal,
                                value: String(users.length),
                                label: "Total Registered Users",
                                sub: "registered accounts",
                                caption: `${users.filter((u) => u.active).length} active, ${users.filter((u) => !u.active).length} deactivated`,
                                subColor: emerald,
                            },
                            {
                                accent: emerald,
                                value: "4 Roles",
                                label: "Role-Based Access Control",
                                sub: "RBAC active",
                                caption: "ADMIN · OC · MDS · SADA",
                                subColor: emerald,
                            },
                            {
                                accent: amber,
                                value: String(pendingApprovalsCount),
                                label: "Pending Registrations",
                                sub: "registration requests",
                                caption: `${urgentCount} high priority flagged`,
                                subColor: amber,
                            },
                            {
                                accent: tealDk,
                                value: "Active",
                                label: "Security & Governance",
                                sub: "identity protection",
                                caption: "Account security active",
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

                    {/* Main content grid: User Management + Pending Registration Approvals */}
                    <div style={{ display: "grid", gridTemplateColumns: "2fr 1.1fr", gap: 20, marginBottom: 40 }} className="adm-panels">

                        {/* User & Role Management Table */}
                        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow }}>
                            <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                <div>
                                    <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>User & Role Management</p>
                                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>Manage account status and assign exact system roles (ADMIN, OC, MDS, SADA)</p>
                                </div>
                                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                                    <button onClick={() => onNavigate("users")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 700, color: teal, background: "rgba(0,150,136,0.08)", padding: "6px 12px", borderRadius: 8, border: "none", cursor: "pointer" }}>
                                        Full Directory <ArrowRight size={13}/>
                                    </button>
                                </div>
                            </div>

                            {userMgmtError ? (
                                <p style={{ padding: "28px 20px", color: rose, fontFamily: SANS, fontSize: 13, textAlign: "center" }}>{userMgmtError}</p>
                            ) : users.length === 0 ? (
                                <p style={{ padding: "28px 20px", color: inkM, fontFamily: SANS, fontSize: 13, textAlign: "center" }}>{loading ? "Loading users..." : "No users found."}</p>
                            ) : (
                                <div>
                                    <div style={{ display: "grid", gridTemplateColumns: "1.5fr 2fr 1.2fr 1fr 1.2fr", padding: "9px 20px", background: "#F8FAFC", borderBottom: `1px solid ${bd}` }}>
                                        {["USERNAME", "EMAIL", "ROLE", "STATUS", "ACTIONS"].map((col) => (
                                            <span key={col} style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em" }}>{col}</span>
                                        ))}
                                    </div>
                                    {users.map((u, i) => {
                                        const badge = ROLE_BADGES[u.role] || { label: u.role, bg: "#E2E8F0", color: "#475569" };
                                        const isUpdating = actionId === u.id;
                                        return (
                                            <div key={u.id} style={{
                                                display: "grid", gridTemplateColumns: "1.5fr 2fr 1.2fr 1fr 1.2fr",
                                                padding: "12px 20px",
                                                borderBottom: i < users.length - 1 ? `1px solid ${bd}` : "none",
                                                alignItems: "center",
                                                opacity: isUpdating ? 0.5 : 1,
                                            }}>
                                                <div>
                                                    <p style={{ fontFamily: SANS, fontSize: 13, fontWeight: 700, color: inkH }}>{u.username}</p>
                                                    <p style={{ fontFamily: MONO, fontSize: 10.5, color: inkM }}>ID: #{u.id}</p>
                                                </div>
                                                <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkB, overflow: "hidden", textOverflow: "ellipsis" }}>{u.email}</p>
                                                <div>
                                                    <span
                                                        style={{
                                                            fontFamily: SANS, fontSize: 11.5, fontWeight: 700,
                                                            background: badge.bg, color: badge.color,
                                                            border: `1px solid ${badge.color}40`, borderRadius: 6,
                                                            padding: "3px 8px", display: "inline-block",
                                                        }}
                                                    >
                                                        {badge.label || u.role}
                                                    </span>
                                                </div>
                                                <div>
                                                    <span style={{
                                                        display: "inline-flex", alignItems: "center", gap: 4,
                                                        padding: "2px 8px", borderRadius: 999,
                                                        background: u.active ? emeraldSoft : roseSoft,
                                                        color: u.active ? "#047857" : "#B91C1C",
                                                        fontFamily: SANS, fontSize: 11, fontWeight: 700,
                                                    }}>
                                                        <span style={{ width: 5, height: 5, borderRadius: "50%", background: u.active ? emerald : rose }}/>
                                                        {u.active ? "Active" : "Disabled"}
                                                    </span>
                                                </div>
                                                <div>
                                                    <button
                                                        onClick={() => handleToggleStatus(u.id, u.active)}
                                                        disabled={isUpdating}
                                                        style={{
                                                            display: "inline-flex", alignItems: "center", gap: 4,
                                                            padding: "4px 10px", borderRadius: 6,
                                                            border: `1px solid ${u.active ? rose : emerald}`,
                                                            background: "#fff",
                                                            color: u.active ? rose : emerald,
                                                            fontFamily: SANS, fontSize: 11, fontWeight: 600,
                                                            cursor: "pointer",
                                                        }}
                                                    >
                                                        {u.active ? <UserX size={12}/> : <UserCheck size={12}/>}
                                                        {u.active ? "Deactivate" : "Activate"}
                                                    </button>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>

                        {/* Registration Approvals Panel */}
                        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow, display: "flex", flexDirection: "column" }}>
                            <div style={{ padding: "18px 20px 14px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                <div>
                                    <p style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 700, color: inkH }}>Registration Requests</p>
                                    <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, marginTop: 2 }}>Pending user registrations for ADMIN review</p>
                                </div>
                                <button onClick={() => onNavigate("approver")} style={{ display: "flex", alignItems: "center", gap: 4, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: teal, background: "none", border: "none", cursor: "pointer" }}>
                                    Panel <ArrowRight size={12}/>
                                </button>
                            </div>

                            <div style={{ flex: 1, padding: "8px 0" }}>
                                {pendingApprovals.length === 0 ? (
                                    <p style={{ padding: "28px 20px", textAlign: "center", color: approverError ? "#B06000" : inkM, fontFamily: SANS, fontSize: 13 }}>
                                        {loading ? "Checking requests..." : approverError ? approverError : "No registration requests awaiting approval."}
                                    </p>
                                ) : (
                                    pendingApprovals.map((a, i) => (
                                        <div key={a.id + i} style={{ padding: "14px 20px", borderBottom: i < pendingApprovals.length - 1 ? `1px solid ${bd}` : "none", display: "flex", flexDirection: "column", gap: 8 }}>
                                            <div style={{ display: "flex", alignItems: "flex-start", gap: 8 }}>
                                                <span style={{ width: 8, height: 8, borderRadius: "50%", background: amber, flexShrink: 0, marginTop: 5 }}/>
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <p style={{ fontFamily: SANS, fontSize: 13, fontWeight: 700, color: inkH, lineHeight: 1.3 }}>{a.title}</p>
                                                    {a.description && <p style={{ fontFamily: SANS, fontSize: 11.5, color: inkM, marginTop: 2 }}>{a.description}</p>}
                                                </div>
                                            </div>

                                            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 4 }}>
                                                <span style={{ fontFamily: SANS, fontSize: 11, color: inkM }}>{a.time}</span>
                                                <div style={{ display: "flex", gap: 6 }}>
                                                    <button
                                                        onClick={() => handleDecideTask(a.id, "APPROVED")}
                                                        disabled={actionId === a.id}
                                                        style={{
                                                            display: "inline-flex", alignItems: "center", gap: 4,
                                                            padding: "4px 10px", borderRadius: 6, border: "none",
                                                            background: emerald, color: "#fff",
                                                            fontFamily: SANS, fontSize: 11, fontWeight: 700,
                                                            cursor: "pointer",
                                                        }}
                                                    >
                                                        <CheckCircle size={12}/> Approve
                                                    </button>
                                                    <button
                                                        onClick={() => handleDecideTask(a.id, "REJECTED")}
                                                        disabled={actionId === a.id}
                                                        style={{
                                                            display: "inline-flex", alignItems: "center", gap: 4,
                                                            padding: "4px 10px", borderRadius: 6,
                                                            border: `1px solid ${rose}`, background: "#fff", color: rose,
                                                            fontFamily: SANS, fontSize: 11, fontWeight: 700,
                                                            cursor: "pointer",
                                                        }}
                                                    >
                                                        <XCircle size={12}/> Reject
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>

            </main>

            <SharedFooter activePage="admindashboard" onNavigate={onNavigate}/>
        </div>
    );
}