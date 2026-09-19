import React, { useState, useEffect, useMemo } from "react";
import { Users, Shield, UserCheck, UserX, Search, Filter, Loader2, RefreshCw, CheckCircle, XCircle, ArrowLeft, Mail, ChevronRight, Lock, Key } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
import { getCached, setCached } from "./dataCache";

const API_BASE_URL = "http://localhost:8080/api/v1/users";

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
  ADMIN: { label: "ADMIN", bg: "#F3E8FF", color: "#6B21A8", name: "System Administrator" },
  SADA: { label: "SADA", bg: "#E0F2FE", color: "#0369A1", name: "Schedule Approver" },
  OC: { label: "OC", bg: "#E0F2F1", color: "#00786B", name: "Operations Controller" },
  MDS: { label: "MDS", bg: "#FEF3C7", color: "#92400E", name: "Maintenance Manager" },
};

function getInitials(name) {
  if (!name) return "US";
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export default function UserManagementPage({ isSignedIn, onNavigate, onLogOut, userName = "Admin", userRole = "Admin" }) {
  const [users, setUsers] = useState(() => getCached("admin_users", []));
  const [loading, setLoading] = useState(() => !getCached("admin_users"));
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [actionId, setActionId] = useState(null);
  const [selectedUser, setSelectedUser] = useState(null);

  const fetchUsers = async () => {
    const hasCache = Boolean(getCached("admin_users"));
    if (!hasCache) setLoading(true);
    const token = localStorage.getItem("auth_token");
    try {
      const res = await fetch(API_BASE_URL, {
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const userList = Array.isArray(data) ? data : [];
      setUsers(userList);
      setCached("admin_users", userList);
      setError(null);
    } catch (e) {
      console.warn("User Management fetch error:", e);
      if (!hasCache) {
        setError(e.message || "Could not connect to user-service.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
    const iv = setInterval(fetchUsers, 5000);
    return () => clearInterval(iv);
  }, []);

  const handleRoleChange = async (userId, newRole) => {
    setActionId(userId);
    const token = localStorage.getItem("auth_token");
    try {
      const res = await fetch(`${API_BASE_URL}/${userId}/role`, {
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
      if (selectedUser?.id === userId) setSelectedUser(updatedUser);
    } catch (e) {
      alert(`Failed to update role: ${e.message}`);
    } finally {
      setActionId(null);
    }
  };

  const handleToggleStatus = async (userId, currentActive) => {
    setActionId(userId);
    const token = localStorage.getItem("auth_token");
    try {
      const res = await fetch(`${API_BASE_URL}/${userId}/status`, {
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
      if (selectedUser?.id === userId) setSelectedUser(updatedUser);
    } catch (e) {
      alert(`Failed to update status: ${e.message}`);
    } finally {
      setActionId(null);
    }
  };

  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchesSearch =
        String(u.username || "").toLowerCase().includes(searchQuery.toLowerCase()) ||
        String(u.email || "").toLowerCase().includes(searchQuery.toLowerCase()) ||
        String(u.id || "").toLowerCase().includes(searchQuery.toLowerCase());
      const matchesRole = roleFilter === "ALL" || String(u.role || "").toUpperCase() === roleFilter;
      const matchesStatus =
        statusFilter === "ALL" ||
        (statusFilter === "ACTIVE" && u.active) ||
        (statusFilter === "DEACTIVATED" && !u.active);
      return matchesSearch && matchesRole && matchesStatus;
    });
  }, [users, searchQuery, roleFilter, statusFilter]);

  const activeCount = users.filter((u) => u.active).length;
  const deactivatedCount = users.filter((u) => !u.active).length;

  return (
    <div className="page-transition" style={{ minHeight: "100vh", background: "#F8FAFC", display: "flex", flexDirection: "column" }}>
      <SharedHeader activePage="user-management" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={onLogOut} userName={userName} userRole={userRole} />

      {/* Header Bar */}
      <div style={{ background: "#ffffff", borderBottom: `1px solid ${bd}` }}>
        <div style={{ maxWidth: 1380, width: "100%", margin: "0 auto", padding: "28px 32px", display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 16 }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
              <h1 style={{ fontFamily: DISPLAY, fontSize: 26, fontWeight: 800, color: inkH, letterSpacing: "-0.02em", margin: 0 }}>
                Enterprise User Management Console
              </h1>
            </div>
            <p style={{ fontFamily: SANS, fontSize: 14, color: inkM, margin: 0 }}>
              Full directory of registered KMRL accounts, role permissions (ADMIN, OC, MDS, SADA), and security statuses.
            </p>
          </div>
        </div>
      </div>

      <main style={{ flex: 1, maxWidth: 1380, width: "100%", margin: "0 auto", padding: "32px 32px 48px" }}>
        
        {/* Metric Summary Cards (Fleet Card Design) */}
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 28 }} className="usr-stats">
          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${teal}`, boxShadow: shadow }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Total Registered Users</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{users.length}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: teal }}>registered accounts</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Database identity records</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${emerald}`, boxShadow: shadow }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Active Accounts</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{activeCount}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: emerald }}>active access</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Authorized system access</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid ${rose}`, boxShadow: shadow }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Deactivated Accounts</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>{deactivatedCount}</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: rose }}>deactivated</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>Access restricted / suspended</p>
          </div>

          <div className="rounded-2xl p-5" style={{ background: "#fff", border: `1px solid ${bd}`, borderLeft: `4px solid #6366F1`, boxShadow: shadow }}>
            <p className="text-xs font-bold uppercase tracking-wider mb-1" style={{ fontFamily: SANS, color: inkM }}>Role Governance</p>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-extrabold" style={{ fontFamily: DISPLAY, color: inkH }}>4 Roles</span>
              <span className="text-xs font-semibold" style={{ fontFamily: SANS, color: "#6366F1" }}>RBAC active</span>
            </div>
            <p className="text-xs mt-2" style={{ fontFamily: SANS, color: inkM }}>ADMIN · OC · MDS · SADA</p>
          </div>
        </div>

        {/* Filter and Search Bar */}
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, padding: "18px 20px", marginBottom: 24, boxShadow: shadow, display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 16 }}>
          {/* Search Box */}
          <div style={{ display: "flex", alignItems: "center", gap: 10, flex: 1, minWidth: 280, maxWidth: 460, padding: "9px 14px", borderRadius: 12, background: "#F8FAFC", border: `1px solid ${bd}` }}>
            <Search size={16} color={inkM} />
            <input
              type="text"
              placeholder="Search by username, email, or user ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{ background: "transparent", border: "none", outline: "none", width: "100%", fontFamily: SANS, fontSize: 13, color: inkH }}
            />
          </div>

          {/* Role Filter Tabs */}
          <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
            <span style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkM, marginRight: 4 }}>Role:</span>
            {["ALL", "ADMIN", "SADA", "OC", "MDS"].map((r) => (
              <button
                key={r}
                onClick={() => setRoleFilter(r)}
                style={{
                  fontFamily: SANS, fontSize: 12, fontWeight: 700,
                  padding: "6px 14px", borderRadius: 8, border: "none", cursor: "pointer",
                  background: roleFilter === r ? teal : "rgba(15,23,42,0.04)",
                  color: roleFilter === r ? "#fff" : inkB,
                  transition: "all 0.15s ease",
                }}
              >
                {r === "ALL" ? "All Roles" : r}
              </button>
            ))}
          </div>

          {/* Status Filter */}
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkM, marginRight: 4 }}>Status:</span>
            {["ALL", "ACTIVE", "DEACTIVATED"].map((s) => (
              <button
                key={s}
                onClick={() => setStatusFilter(s)}
                style={{
                  fontFamily: SANS, fontSize: 12, fontWeight: 600,
                  padding: "6px 12px", borderRadius: 8, border: "none", cursor: "pointer",
                  background: statusFilter === s ? inkH : "rgba(15,23,42,0.04)",
                  color: statusFilter === s ? "#fff" : inkB,
                }}
              >
                {s === "ALL" ? "All Status" : s === "ACTIVE" ? "Active" : "Deactivated"}
              </button>
            ))}
          </div>
        </div>

        {/* Directory Table */}
        <div style={{ background: "#fff", border: `1px solid ${bd}`, borderRadius: 16, overflow: "hidden", boxShadow: shadow }}>
          <div style={{ padding: "18px 24px", borderBottom: `1px solid ${bd}`, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 16, fontWeight: 800, color: inkH, margin: 0 }}>Registered Identity Directory</h2>
              <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, marginTop: 2, margin: 0 }}>
                Showing {filteredUsers.length} of {users.length} registered accounts
              </p>
            </div>
          </div>

          {loading && users.length === 0 ? (
            <div style={{ padding: "48px 0", textAlign: "center", fontFamily: SANS, fontSize: 13, color: inkM, display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }}>
              <Loader2 size={18} className="animate-spin" color={teal} /> Loading user directory...
            </div>
          ) : error && users.length === 0 ? (
            <div style={{ padding: "48px 24px", textAlign: "center", fontFamily: SANS }}>
              <p style={{ fontSize: 14, fontWeight: 600, color: rose, marginBottom: 8 }}>{error}</p>
              <button onClick={fetchUsers} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: "#fff", background: teal, padding: "8px 16px", borderRadius: 8, border: "none", cursor: "pointer" }}>
                Retry Connection
              </button>
            </div>
          ) : filteredUsers.length === 0 ? (
            <div style={{ padding: "48px 24px", textAlign: "center", fontFamily: SANS, color: inkM }}>
              No user accounts match your search filters.
            </div>
          ) : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC", borderBottom: `1px solid ${bd}` }}>
                    <th style={{ padding: "12px 24px", textAlign: "left", fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>User Identity</th>
                    <th style={{ padding: "12px 24px", textAlign: "left", fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>Email Address</th>
                    <th style={{ padding: "12px 24px", textAlign: "left", fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>Assigned Role</th>
                    <th style={{ padding: "12px 24px", textAlign: "left", fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>Account Status</th>
                    <th style={{ padding: "12px 24px", textAlign: "right", fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.06em", textTransform: "uppercase" }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((u, i) => {
                    const roleKey = String(u.role || "").toUpperCase();
                    const badge = ROLE_BADGES[roleKey] || { label: roleKey, bg: "#F1F5F9", color: inkB };
                    const isBusy = actionId === u.id;

                    return (
                      <tr key={u.id || i} style={{ borderBottom: i < filteredUsers.length - 1 ? `1px solid ${bd}` : "none", background: selectedUser?.id === u.id ? "rgba(0,150,136,0.03)" : "transparent" }}>
                        {/* Identity Column */}
                        <td style={{ padding: "14px 24px" }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                            <div style={{ width: 36, height: 36, borderRadius: 10, background: `linear-gradient(135deg, ${teal}, ${tealDk})`, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontFamily: DISPLAY, fontSize: 13, fontWeight: 800 }}>
                              {getInitials(u.username)}
                            </div>
                            <div>
                              <p style={{ fontFamily: DISPLAY, fontSize: 13.5, fontWeight: 700, color: inkH, margin: 0 }}>{u.username}</p>
                              <span style={{ fontFamily: MONO, fontSize: 10.5, color: inkM }}>ID: {u.id}</span>
                            </div>
                          </div>
                        </td>

                        {/* Email Column */}
                        <td style={{ padding: "14px 24px" }}>
                          <span style={{ fontFamily: SANS, fontSize: 13, color: inkB }}>{u.email || "No email on record"}</span>
                        </td>

                        {/* Role Column */}
                        <td style={{ padding: "14px 24px" }}>
                          <span
                            style={{
                              fontFamily: SANS, fontSize: 12, fontWeight: 700,
                              padding: "5px 11px", borderRadius: 8,
                              border: `1px solid ${badge.color}30`, background: badge.bg, color: badge.color,
                              display: "inline-block",
                            }}
                          >
                            {badge.label}
                          </span>
                        </td>

                        {/* Status Toggle Column */}
                        <td style={{ padding: "14px 24px" }}>
                          <button
                            onClick={() => handleToggleStatus(u.id, u.active)}
                            disabled={isBusy}
                            style={{
                              display: "inline-flex", alignItems: "center", gap: 6,
                              padding: "4px 12px", borderRadius: 999, border: "none",
                              background: u.active ? emeraldSoft : roseSoft,
                              color: u.active ? "#137333" : "#DC2626",
                              fontFamily: SANS, fontSize: 12, fontWeight: 700,
                              cursor: "pointer", transition: "all 0.15s ease",
                            }}
                          >
                            <span style={{ width: 6, height: 6, borderRadius: "50%", background: u.active ? emerald : rose }} />
                            {u.active ? "Active" : "Deactivated"}
                          </button>
                        </td>

                        {/* Actions Column */}
                        <td style={{ padding: "14px 24px", textAlign: "right" }}>
                          <button
                            onClick={() => setSelectedUser(u)}
                            style={{
                              display: "inline-flex", alignItems: "center", gap: 4,
                              padding: "6px 12px", borderRadius: 8, border: `1px solid ${bd}`,
                              background: "#fff", fontFamily: SANS, fontSize: 12, fontWeight: 600,
                              color: teal, cursor: "pointer",
                            }}
                          >
                            Details <ChevronRight size={13} />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>

      {/* User Detail Drawer / Modal */}
      {selectedUser && (
        <div style={{ position: "fixed", inset: 0, zIndex: 9999, background: "rgba(15,23,42,0.5)", backdropFilter: "blur(4px)", display: "flex", alignItems: "center", justifyContent: "center", padding: 20 }} onClick={() => setSelectedUser(null)}>
          <div style={{ background: "#fff", borderRadius: 20, width: "min(520px, 100%)", boxShadow: "0 24px 72px rgba(15,23,42,0.25)", overflow: "hidden" }} onClick={(e) => e.stopPropagation()}>
            <div style={{ padding: "24px 28px", borderBottom: `1px solid ${bd}`, background: "#F8FAFC", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: 12, background: `linear-gradient(135deg, ${teal}, ${tealDk})`, display: "flex", alignItems: "center", justifyContent: "center", color: "#fff", fontFamily: DISPLAY, fontSize: 16, fontWeight: 800 }}>
                  {getInitials(selectedUser.username)}
                </div>
                <div>
                  <h3 style={{ fontFamily: DISPLAY, fontSize: 18, fontWeight: 800, color: inkH, margin: 0 }}>{selectedUser.username}</h3>
                  <span style={{ fontFamily: MONO, fontSize: 11, color: inkM }}>ID: {selectedUser.id}</span>
                </div>
              </div>
              <button onClick={() => setSelectedUser(null)} style={{ background: "none", border: "none", cursor: "pointer", fontFamily: SANS, fontSize: 18, color: inkM }}>✕</button>
            </div>

            <div style={{ padding: "24px 28px", display: "flex", flexDirection: "column", gap: 18 }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div style={{ background: "#F8FAFC", padding: "12px 14px", borderRadius: 10, border: `1px solid ${bd}` }}>
                  <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, textTransform: "uppercase" }}>System Role</span>
                  <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 800, color: inkH, margin: "4px 0 0" }}>{selectedUser.role}</p>
                </div>
                <div style={{ background: "#F8FAFC", padding: "12px 14px", borderRadius: 10, border: `1px solid ${bd}` }}>
                  <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, textTransform: "uppercase" }}>Security Status</span>
                  <p style={{ fontFamily: DISPLAY, fontSize: 14, fontWeight: 800, color: selectedUser.active ? emerald : rose, margin: "4px 0 0" }}>
                    {selectedUser.active ? "ACTIVE" : "DEACTIVATED"}
                  </p>
                </div>
              </div>

              <div>
                <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, textTransform: "uppercase" }}>Email Address</span>
                <p style={{ fontFamily: SANS, fontSize: 13.5, fontWeight: 600, color: inkB, margin: "4px 0 0" }}>{selectedUser.email || "No email address registered"}</p>
              </div>

              <div>
                <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, textTransform: "uppercase" }}>Permissions Scope</span>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6, marginTop: 6 }}>
                  {String(selectedUser.role || "").toUpperCase() === "ADMIN" && (
                    <>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#F3E8FF", color: "#6B21A8", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Full System Admin</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#F3E8FF", color: "#6B21A8", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>User Management</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#F3E8FF", color: "#6B21A8", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>User Approvals</span>
                    </>
                  )}
                  {String(selectedUser.role || "").toUpperCase() === "SADA" && (
                    <>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#E0F2FE", color: "#0369A1", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Schedule Sign-Off</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#E0F2FE", color: "#0369A1", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>CoF Approval</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#E0F2FE", color: "#0369A1", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Fleet Overrides</span>
                    </>
                  )}
                  {String(selectedUser.role || "").toUpperCase() === "OC" && (
                    <>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#E0F2F1", color: "#00786B", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Schedule Proposal</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#E0F2F1", color: "#00786B", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Corridor Dispatch</span>
                    </>
                  )}
                  {String(selectedUser.role || "").toUpperCase() === "MDS" && (
                    <>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#FEF3C7", color: "#92400E", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>Job Card Creation</span>
                      <span style={{ padding: "3px 10px", borderRadius: 6, background: "#FEF3C7", color: "#92400E", fontFamily: SANS, fontSize: 11, fontWeight: 700 }}>CoF Filing</span>
                    </>
                  )}
                </div>
              </div>

              <div style={{ display: "flex", gap: 10, marginTop: 8 }}>
                <button
                  onClick={() => handleToggleStatus(selectedUser.id, selectedUser.active)}
                  style={{ flex: 1, padding: "10px 0", borderRadius: 10, border: "none", background: selectedUser.active ? roseSoft : emeraldSoft, color: selectedUser.active ? rose : emerald, fontFamily: SANS, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }}
                >
                  {selectedUser.active ? "Deactivate Account" : "Activate Account"}
                </button>
                <button
                  onClick={() => setSelectedUser(null)}
                  style={{ flex: 1, padding: "10px 0", borderRadius: 10, border: `1px solid ${bd}`, background: "#fff", color: inkB, fontFamily: SANS, fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      <SharedFooter activePage="user-management" onNavigate={onNavigate} />
    </div>
  );
}
