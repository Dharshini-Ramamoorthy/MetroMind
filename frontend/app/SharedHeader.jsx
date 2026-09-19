import { useState, useEffect, useCallback } from "react";
import { LogOut, ArrowLeft, Menu, X, Settings, Bell } from "lucide-react";
import AlertsDrawer, { ALERT_API_BASE_URL } from "./AlertsDrawer";

const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";

const OPS_NAV = [
    { label: "Dashboard", key: "dashboard" },
    { label: "Fleet and Induction", key: "fleet" },
    { label: "Schedule", key: "schedule" },
    { label: "Reports", key: "reports" },
    { label: "Maintenance", key: "maintenance" },
];

const MAINT_NAV = [
    { label: "Dashboard", key: "dashboard" },
    { label: "Fleet and Induction", key: "fleet" },
    { label: "Maintenance", key: "maintenance" },
    { label: "Reports", key: "reports" },
];

const SADA_NAV = [
    { label: "Dashboard", key: "dashboard" },
    { label: "Fleet & Induction", key: "fleet" },
    { label: "Schedule", key: "schedule" },
    { label: "Maintenance", key: "maintenance" },
    { label: "Approver Panel", key: "approver" },
    { label: "Reports", key: "reports" },
];

// ADMIN NAV: Admin Dashboard + User Management + Approver Panel ONLY
const ADMIN_NAV = [
    { label: "Dashboard", key: "dashboard" },
    { label: "User Management", key: "users" },
    { label: "Approver Panel", key: "approver" },
];

const DEFAULT_NAV = [
    { label: "Dashboard", key: "dashboard" },
    { label: "Fleet & Induction", key: "fleet" },
    { label: "Schedule", key: "schedule" },
    { label: "Reports", key: "reports" },
    { label: "Maintenance", key: "maintenance" },
];

function getNavLinks(role) {
    const r = (role || "").toUpperCase();
    if (r.includes("ADMIN"))
        return ADMIN_NAV;
    if (r.includes("SADA") || r.includes("APPROVER"))
        return SADA_NAV;
    if (r.includes("OPS") || r.includes("CONTROLLER"))
        return OPS_NAV;
    if (r.includes("MAINTEN"))
        return MAINT_NAV;
    return DEFAULT_NAV;
}

const MINIMAL_PAGES = new Set(["about", "contact"]);

const ROLE_LABELS = {
    ROLE_OPS_CONTROLLER: "Operation Controller",
    ROLE_MAINTENANCE_MANAGER: "Maintenance Manager",
    ROLE_ADMIN: "System Admin",
    ROLE_SADA: "Schedule Approver",
    OpsController: "Operation Controller",
    MaintenanceManager: "Maintenance Manager",
    Admin: "System Admin",
    SADA: "Schedule Approver",
    Approver: "Schedule Approver",
};

function getInitials(name) {
    const parts = name.trim().split(/\s+/);
    if (parts.length === 1)
        return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

const AVATAR_GRADIENTS = [
    ["#009688", "#00786B"],
    ["#6366F1", "#4338CA"],
    ["#EC4899", "#BE185D"],
    ["#F59E0B", "#B45309"],
    ["#10B981", "#065F46"],
    ["#3B82F6", "#1D4ED8"],
];

function getAvatarGradient(name) {
    const idx = name.charCodeAt(0) % AVATAR_GRADIENTS.length;
    return AVATAR_GRADIENTS[idx];
}

export default function SharedHeader({ activePage, isSignedIn, onNavigate, onLogOut, userName = "User", userRole = "_default" }) {
    const [menuOpen, setMenuOpen] = useState(false);
    const isMinimal = MINIMAL_PAGES.has(activePage);
    const navLinks = getNavLinks(userRole);
    const roleLabel = ROLE_LABELS[userRole] ?? userRole;
    const initials = getInitials(userName);
    const [g1, g2] = getAvatarGradient(userName);
    const isSettingsActive = activePage === "settings";

    const [alertsOpen, setAlertsOpen] = useState(false);
    const [activeAlertCount, setActiveAlertCount] = useState(null);

    const refreshAlertCount = useCallback(async () => {
        if (!isSignedIn) return;
        try {
            const token = localStorage.getItem("auth_token");
            const res = await fetch(`${ALERT_API_BASE_URL}/summary`, {
                headers: {
                    "Content-Type": "application/json",
                    ...(token ? { Authorization: `Bearer ${token}` } : {}),
                },
            });
            if (!res.ok) return;
            const data = await res.json();
            setActiveAlertCount(typeof data.totalActive === "number" ? data.totalActive : null);
        } catch {}
    }, [isSignedIn]);

    useEffect(() => {
        refreshAlertCount();
        const interval = setInterval(refreshAlertCount, 20000);
        return () => clearInterval(interval);
    }, [refreshAlertCount]);

    useEffect(() => {
        function openDrawer() { setAlertsOpen(true); }
        window.addEventListener("open-alerts-drawer", openDrawer);
        return () => window.removeEventListener("open-alerts-drawer", openDrawer);
    }, []);

    function handleCloseAlerts() {
        setAlertsOpen(false);
        refreshAlertCount();
    }

    function handleLink(key) {
        setMenuOpen(false);
        onNavigate(key);
    }

    const LogoMark = () => (
        <button onClick={() => handleLink("landing")} className="flex items-center gap-2.5 shrink-0 focus:outline-none" style={{ background: "none", border: "none", cursor: "pointer" }} aria-label="MetroMind KMRL home">
            <div style={{
                width: 28, height: 28, borderRadius: 7, flexShrink: 0,
                background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
                display: "flex", alignItems: "center", justifyContent: "center",
            }}>
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                    <path d="M3 4L7 7L3 10" stroke={emerald} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                    <path d="M7 4L11 7L7 10" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
            </div>
            <span style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 15, color: inkH, letterSpacing: "-0.02em" }}>
                MetroMind <span style={{ color: teal }}>KMRL</span>
            </span>
        </button>
    );

    const AvatarChip = () => (
        <button onClick={() => handleLink("settings")} title={`${userName} · ${roleLabel} — Profile & Settings`} aria-label="Open profile and settings" style={{
            display: "flex", alignItems: "center", gap: 9,
            padding: "5px 14px 5px 5px",
            borderRadius: 999,
            background: isSettingsActive
                ? `linear-gradient(135deg, rgba(0,150,136,0.12), rgba(0,120,107,0.08))`
                : "rgba(255,255,255,0.85)",
            border: isSettingsActive
                ? `1.5px solid ${teal}`
                : "1.5px solid rgba(15,23,42,0.10)",
            cursor: "pointer",
            boxShadow: isSettingsActive
                ? `0 0 0 3px rgba(0,150,136,0.12), 0 2px 8px rgba(0,150,136,0.18)`
                : "0 1px 4px rgba(15,23,42,0.07)",
            transition: "all 0.2s cubic-bezier(0.4,0,0.2,1)",
        }}>
            <div style={{ position: "relative", flexShrink: 0 }}>
                <div style={{
                    width: 30, height: 30, borderRadius: "50%",
                    background: `linear-gradient(135deg, ${g1} 0%, ${g2} 100%)`,
                    display: "flex", alignItems: "center", justifyContent: "center",
                    boxShadow: `0 2px 8px ${g1}55`,
                }}>
                    <span style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 12, color: "#fff", letterSpacing: "0.04em" }}>
                        {initials}
                    </span>
                </div>
                <div style={{
                    position: "absolute", bottom: 0, right: -1,
                    width: 9, height: 9, borderRadius: "50%",
                    background: emerald, border: "2px solid #F8FAFC",
                }}/>
            </div>

            <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: 1.5 }}>
                <span style={{ fontFamily: DISPLAY, fontSize: 12.5, fontWeight: 700, color: inkH, lineHeight: 1, whiteSpace: "nowrap" }}>
                    {userName}
                </span>
                <span style={{ fontFamily: SANS, fontSize: 10, fontWeight: 500, color: teal, lineHeight: 1, whiteSpace: "nowrap", background: `rgba(0,150,136,0.08)`, padding: "1px 5px", borderRadius: 4 }}>
                    {roleLabel}
                </span>
            </div>
            <Settings size={12} color={isSettingsActive ? teal : inkM} style={{ marginLeft: 1, opacity: 0.7 }}/>
        </button>
    );

    const NotificationBell = () => (
        <button onClick={() => setAlertsOpen(o => !o)} title="Alerts" style={{
            position: "relative", width: 34, height: 34, borderRadius: 9,
            display: "flex", alignItems: "center", justifyContent: "center",
            border: "1.5px solid rgba(15,23,42,0.10)", background: "#fff", cursor: "pointer",
            boxShadow: "0 1px 4px rgba(15,23,42,0.06)", flexShrink: 0,
        }}>
            <Bell size={15} color={inkB}/>
            {!!activeAlertCount && (
                <span style={{
                    position: "absolute", top: -4, right: -4, minWidth: 16, height: 16, borderRadius: 999,
                    background: "#DC2626", color: "#fff", fontFamily: SANS, fontSize: 9.5, fontWeight: 800,
                    display: "flex", alignItems: "center", justifyContent: "center", padding: "0 3px",
                    border: "2px solid #F8FAFC",
                }}>
                    {activeAlertCount > 99 ? "99+" : activeAlertCount}
                </span>
            )}
        </button>
    );

    const isRoleAdmin = String(userRole || "").toUpperCase().includes("ADMIN");

    const AuthArea = () => (
        <div className="flex items-center gap-2 shrink-0">
            {isSignedIn ? (
                <>
                    {!isRoleAdmin && <NotificationBell />}
                    <AvatarChip />
                    <button onClick={onLogOut} style={{
                        display: "flex", alignItems: "center", gap: 5,
                        padding: "7px 13px", borderRadius: 8,
                        border: "1.5px solid rgba(15,23,42,0.10)",
                        background: "#fff", fontFamily: SANS, fontSize: 12.5,
                        fontWeight: 600, color: inkM, cursor: "pointer",
                        boxShadow: "0 1px 4px rgba(15,23,42,0.06)",
                    }}>
                        <LogOut size={13}/> Sign Out
                    </button>
                </>
            ) : (
                <button onClick={() => handleLink("signin")} style={{
                    padding: "8px 18px", borderRadius: 10, border: "none",
                    background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
                    fontFamily: DISPLAY, fontSize: 13.5, fontWeight: 700, color: "#fff",
                    cursor: "pointer", boxShadow: "0 2px 8px rgba(0,150,136,0.25)",
                }}>
                    Sign In
                </button>
            )}
        </div>
    );

    return (
        <>
            <header data-shared-header="true" className="sticky top-0 z-50 w-full" style={{
                background: "rgba(248,250,252,0.92)",
                backdropFilter: "blur(16px)", WebkitBackdropFilter: "blur(16px)",
                borderBottom: `1px solid ${bd}`,
            }}>
                <div style={{ maxWidth: "88rem", margin: "0 auto", padding: "0 24px", height: 60, display: "flex", alignItems: "center", gap: 8 }}>
                    <LogoMark />

                    {isMinimal && (
                        <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 14 }}>
                            <button onClick={() => handleLink(isSignedIn ? "dashboard" : "landing")} style={{
                                display: "flex", alignItems: "center", gap: 6,
                                background: "none", border: "none", cursor: "pointer",
                                fontFamily: SANS, fontSize: 13.5, fontWeight: 600, color: teal,
                            }}>
                                <ArrowLeft size={14}/>
                                {isSignedIn ? "Back to Dashboard" : "Back"}
                            </button>
                            <AuthArea />
                        </div>
                    )}

                    {!isMinimal && (
                        <>
                            <nav className="hidden lg:flex items-center gap-0.5 flex-1 ml-4">
                                {navLinks.map(link => {
                                    const isDashboardLink = link.key === "dashboard";
                                    const isDashboardPage = activePage.includes("dashboard") || activePage === "opsdashboard" || activePage === "maintenancedashboard" || activePage === "admindashboard" || activePage === "sadadashboard";
                                    const active = isDashboardLink ? isDashboardPage : activePage === link.key;
                                    return (
                                        <button key={`${link.key}-${link.label}`} onClick={() => handleLink(link.key)} style={{
                                            position: "relative", padding: "8px 13px", borderRadius: 8,
                                            background: "none", border: "none", cursor: "pointer",
                                            fontFamily: SANS, fontSize: 13.5,
                                            color: active ? inkH : inkB,
                                            fontWeight: active ? 700 : 500,
                                            whiteSpace: "nowrap",
                                        }}>
                                            {link.label}
                                            {active && (
                                                <span style={{
                                                    position: "absolute", bottom: 1, left: 13, right: 13,
                                                    height: 2, borderRadius: 999, background: teal,
                                                    display: "block",
                                                }}/>
                                            )}
                                        </button>
                                    );
                                })}
                            </nav>

                            <div className="hidden lg:flex ml-auto">
                                <AuthArea />
                            </div>

                            {isSignedIn && !isRoleAdmin && (
                                <div className="lg:hidden ml-auto">
                                    <NotificationBell />
                                </div>
                            )}
                            <button className="lg:hidden w-9 h-9 flex items-center justify-center rounded-lg border" style={{ border: `1px solid ${bd}`, background: "#fff", cursor: "pointer" }} onClick={() => setMenuOpen(o => !o)}>
                                {menuOpen ? <X size={17} color={inkM}/> : <Menu size={17} color={inkM}/>}
                            </button>
                        </>
                    )}
                </div>

                {menuOpen && !isMinimal && (
                    <div className="lg:hidden" style={{ background: "rgba(248,250,252,0.98)", borderBottom: `1px solid ${bd}`, padding: "8px 20px 16px" }}>
                        {navLinks.map(link => {
                            const isDashboardPage = activePage.includes("dashboard") || activePage === "opsdashboard" || activePage === "maintenancedashboard" || activePage === "admindashboard" || activePage === "sadadashboard";
                            const active = link.key === "dashboard" ? isDashboardPage : activePage === link.key;
                            return (
                                <button key={`mob-${link.key}-${link.label}`} onClick={() => handleLink(link.key)} style={{
                                    display: "block", width: "100%", textAlign: "left",
                                    padding: "10px 12px", borderRadius: 8, border: "none", background: "none",
                                    fontFamily: SANS, fontSize: 14,
                                    fontWeight: active ? 700 : 500,
                                    color: active ? teal : inkB,
                                    cursor: "pointer",
                                }}>
                                    {link.label}
                                </button>
                            );
                        })}

                        {isSignedIn && (
                            <button onClick={() => handleLink("settings")} style={{
                                display: "block", width: "100%", textAlign: "left",
                                padding: "10px 12px", borderRadius: 8, border: "none", background: "none",
                                fontFamily: SANS, fontSize: 14, fontWeight: 500,
                                color: isSettingsActive ? teal : inkB, cursor: "pointer",
                            }}>
                                ⚙ Profile &amp; Settings
                            </button>
                        )}

                        <div style={{ paddingTop: 12, borderTop: `1px solid ${bd}`, marginTop: 8 }}>
                            {isSignedIn ? (
                                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                        <div style={{
                                            width: 26, height: 26, borderRadius: "50%",
                                            background: `linear-gradient(135deg, ${g1}, ${g2})`,
                                            display: "flex", alignItems: "center", justifyContent: "center",
                                        }}>
                                            <span style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 10, color: "#fff" }}>{initials}</span>
                                        </div>
                                        <span style={{ fontFamily: SANS, fontSize: 12.5, color: inkM }}>{userName} · {roleLabel}</span>
                                    </div>
                                    <button onClick={onLogOut} style={{ display: "flex", alignItems: "center", gap: 5, fontFamily: SANS, fontSize: 12.5, color: inkM, background: "none", border: "none", cursor: "pointer" }}>
                                        <LogOut size={13}/> Log Out
                                    </button>
                                </div>
                            ) : (
                                <button onClick={() => handleLink("signin")} style={{
                                    width: "100%", padding: "10px", borderRadius: 10, border: "none",
                                    background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
                                    fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: "#fff", cursor: "pointer",
                                }}>
                                    Sign In
                                </button>
                            )}
                        </div>
                    </div>
                )}
            </header>

            <AlertsDrawer isOpen={alertsOpen} onClose={handleCloseAlerts} isSignedIn={isSignedIn} userName={userName} userRole={userRole}/>
        </>
    );
}
