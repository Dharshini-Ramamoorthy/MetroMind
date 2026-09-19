import React, { useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, useNavigate, Navigate } from "react-router-dom";
import LandingPage from "./LandingPage";
import SignInPage from "./SignInPage";
import RegisterPage from "./RegisterPage";
import ForgotPasswordPage from "./ForgotPasswordPage";
import ResetPasswordPage from "./ResetPasswordPage";

// Role-based Dashboard Components
import AdminDashboard from "./AdminDashboard";
import SADADashboard from "./SADADashboard";
import OpsControllerDashboard from "./OpsControllerDashboard";
import MaintenanceManagerDashboard from "./MaintenanceManagerDashboard";

// Feature Components
import MaintenancePage from "./MaintenancePage";
import FleetPage from "./FleetPage";
import SchedulePage from "./SchedulePage";
import AboutPage from "./AboutPage";
import ContactPage from "./ContactPage";
import ReportsPage from "./ReportsPage";
import ApproverPanelPage from "./ApproverPanelPage";
import SettingsPage from "./SettingsPage";
import UserManagementPage from "./UserManagementPage";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
export const AUTH_API_BASE_URL = `${API_BASE_URL}/api/v1/auth`;
export const MAINTENANCE_API_BASE_URL = `${API_BASE_URL}/api/v1/maintenance`;

function useGlobalFonts() {
    useEffect(() => {
        if (document.getElementById("kmrl-font-link"))
            return;
        const link = document.createElement("link");
        link.id = "kmrl-font-link";
        link.rel = "stylesheet";
        link.href =
            "https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@500;600;700;800&family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap";
        document.head.appendChild(link);
        const style = document.createElement("style");
        style.id = "kmrl-font-base";
        style.innerHTML = `
      html, body, #root {
        font-family: 'Inter', system-ui, -apple-system, sans-serif;
      }
      h1, h2, h3, h4, h5, h6 {
        font-family: 'Plus Jakarta Sans', 'Inter', system-ui, sans-serif;
      }
    `;
        document.head.appendChild(style);
    }, []);
}

// Maps backend ERole code (ADMIN / OC / MDS / SADA) to frontend role key
export function normalizeRole(role) {
    if (!role) return "";
    const upper = String(role).toUpperCase().trim();
    if (upper === "ROLE_ADMIN" || upper === "ROLE_OPS_CONTROLLER" || upper === "ROLE_MAINTENANCE_MANAGER" || upper === "ROLE_SADA") {
        return upper;
    }
    const clean = upper.replace(/^ROLE_/, "");
    if (clean === "ADMIN")  return "ROLE_ADMIN";
    if (clean === "OC")     return "ROLE_OPS_CONTROLLER";
    if (clean === "MDS")    return "ROLE_MAINTENANCE_MANAGER";
    if (clean === "SADA")   return "ROLE_SADA";
    return clean;
}

export function toBackendRole(roleKey) {
    switch (roleKey) {
        case "ROLE_ADMIN":               return "ADMIN";
        case "ROLE_OPS_CONTROLLER":      return "OC";
        case "ROLE_MAINTENANCE_MANAGER": return "MDS";
        case "ROLE_SADA":                return "SADA";
        default: return roleKey || "";
    }
}

function AppRoutes() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [currentUser, setCurrentUser] = useState(() => {
        const savedUser = localStorage.getItem("kmrl_user");
        if (savedUser) {
            try {
                return JSON.parse(savedUser);
            } catch {
                return null;
            }
        }
        return null;
    });

    useEffect(() => {
        async function verifySession() {
            const savedUser = localStorage.getItem("kmrl_user");
            const token = localStorage.getItem("auth_token");
            if (!token || !savedUser) {
                setLoading(false);
                return;
            }
            try {
                const response = await fetch(`${AUTH_API_BASE_URL}/me`, {
                    method: "GET",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        "Content-Type": "application/json",
                    },
                });
                if (response.ok) {
                    const verifiedUser = await response.json();
                    const normalized = { ...verifiedUser, role: normalizeRole(verifiedUser.role), token };
                    setCurrentUser(normalized);
                    localStorage.setItem("kmrl_user", JSON.stringify(normalized));
                } else {
                    localStorage.removeItem("kmrl_user");
                    localStorage.removeItem("auth_token");
                    setCurrentUser(null);
                }
            } catch (error) {
                console.warn("Auth Service offline:", error);
            } finally {
                setLoading(false);
            }
        }
        verifySession();
    }, []);

    const activeUser = currentUser;
    const activeRole = normalizeRole(activeUser?.role);
    const isSignedIn = Boolean(activeUser);

    const handleNavigate = (page) => {
        switch (page) {
            case "landing":
            case "home":
                navigate("/");
                break;
            case "signin":
                navigate("/signin");
                break;
            case "register":
                navigate("/register");
                break;
            case "forgot-password":
                navigate("/forgot-password");
                break;
            case "reset-password":
                navigate("/reset-password");
                break;
            case "maintenancedashboard":
            case "maintenance-dashboard":
                navigate("/maintenance-manager-dashboard");
                break;
            case "opsdashboard":
            case "ops-dashboard":
                navigate("/ops-dashboard");
                break;
            case "admindashboard":
            case "admin-dashboard":
                navigate("/admin-dashboard");
                break;
            case "sadadashboard":
            case "sada-dashboard":
                navigate("/sada-dashboard");
                break;
            case "dashboard":
                if (activeRole === "ROLE_ADMIN") navigate("/admin-dashboard");
                else if (activeRole === "ROLE_OPS_CONTROLLER") navigate("/ops-dashboard");
                else if (activeRole === "ROLE_MAINTENANCE_MANAGER") navigate("/maintenance-manager-dashboard");
                else if (activeRole === "ROLE_SADA") navigate("/sada-dashboard");
                else navigate("/");
                break;
            case "fleet":
                navigate("/fleet");
                break;
            case "schedule":
                navigate("/schedule");
                break;
            case "alerts":
                window.dispatchEvent(new CustomEvent("open-alerts-drawer"));
                break;
            case "reports":
                navigate("/reports");
                break;
            case "maintenance":
                navigate("/maintenance");
                break;
            case "approver":
            case "approver-panel":
                navigate("/approver-panel");
                break;
            case "users":
            case "user-management":
                navigate("/user-management");
                break;
            case "about":
                navigate("/about");
                break;
            case "contact":
                navigate("/contact");
                break;
            case "settings":
                navigate("/settings");
                break;
            case "logout":
                localStorage.removeItem("kmrl_user");
                localStorage.removeItem("auth_token");
                setCurrentUser(null);
                navigate("/");
                break;
            default:
                navigate("/");
        }
    };

    const handleLoginSuccess = (user) => {
        const roleKey = normalizeRole(user?.role);
        const updatedUser = { ...user, role: roleKey };
        localStorage.setItem("kmrl_user", JSON.stringify(updatedUser));
        if (user.token) {
            localStorage.setItem("auth_token", user.token);
        }
        setCurrentUser(updatedUser);

        if (roleKey === "ROLE_ADMIN") {
            navigate("/admin-dashboard");
        } else if (roleKey === "ROLE_OPS_CONTROLLER") {
            navigate("/ops-dashboard");
        } else if (roleKey === "ROLE_MAINTENANCE_MANAGER") {
            navigate("/maintenance-manager-dashboard");
        } else if (roleKey === "ROLE_SADA") {
            navigate("/sada-dashboard");
        } else {
            navigate("/");
        }
    };

    if (loading) {
        return (
            <div style={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: "100vh", fontFamily: "Inter, sans-serif" }}>
                <p style={{ color: "#009688", fontWeight: 600 }}>Connecting to KMRL Operations Core...</p>
            </div>
        );
    }

    return (
        <Routes>
            <Route path="/" element={<LandingPage onNavigate={handleNavigate} isSignedIn={isSignedIn} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>}/>
            <Route path="/signin" element={<SignInPage onNavigate={handleNavigate} onLoginSuccess={handleLoginSuccess} apiBaseUrl={AUTH_API_BASE_URL}/>}/>
            <Route path="/register" element={<RegisterPage onNavigate={handleNavigate} apiBaseUrl={AUTH_API_BASE_URL}/>}/>
            <Route path="/forgot-password" element={<ForgotPasswordPage onNavigate={handleNavigate} apiBaseUrl={AUTH_API_BASE_URL}/>}/>
            <Route path="/reset-password" element={<ResetPasswordPage onNavigate={handleNavigate} apiBaseUrl={AUTH_API_BASE_URL}/>}/>

            {/* Role Dashboards */}
            <Route path="/admin-dashboard" element={activeRole === "ROLE_ADMIN" ? (<AdminDashboard isSignedIn={isSignedIn} user={activeUser} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/sada-dashboard" element={activeRole === "ROLE_SADA" || activeRole === "ROLE_ADMIN" ? (<SADADashboard isSignedIn={isSignedIn} user={activeUser} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/ops-dashboard" element={activeRole === "ROLE_OPS_CONTROLLER" || activeRole === "ROLE_ADMIN" ? (<OpsControllerDashboard isSignedIn={isSignedIn} user={activeUser} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/maintenance-manager-dashboard" element={activeRole === "ROLE_MAINTENANCE_MANAGER" || activeRole === "ROLE_ADMIN" ? (<MaintenanceManagerDashboard user={activeUser} isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>

            <Route path="/maintenance" element={isSignedIn ? (<MaintenancePage user={activeUser} userName={activeUser?.username || "Operations Controller"} userRole={activeUser?.role} isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/approver-panel" element={isSignedIn ? (<ApproverPanelPage isSignedIn={isSignedIn} userRole={activeUser?.role || "ROLE_SADA"} userName={activeUser?.username} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/user-management" element={activeRole === "ROLE_ADMIN" ? (<UserManagementPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>

            {/* Feature Pages */}
            <Route path="/fleet" element={isSignedIn ? (<FleetPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/schedule" element={isSignedIn ? (<SchedulePage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} onSignIn={() => handleNavigate("signin")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/reports" element={isSignedIn ? (<ReportsPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>

            <Route path="/about" element={<AboutPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>}/>
            <Route path="/settings" element={isSignedIn ? (<SettingsPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>) : (<Navigate to="/" replace/>)}/>
            <Route path="/contact" element={<ContactPage isSignedIn={isSignedIn} onNavigate={handleNavigate} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>}/>

            <Route path="*" element={<LandingPage onNavigate={handleNavigate} isSignedIn={isSignedIn} onLogOut={() => handleNavigate("logout")} userName={activeUser?.username} userRole={activeUser?.role}/>}/>
        </Routes>
    );
}

export default function App() {
    useGlobalFonts();
    return (
        <BrowserRouter>
            <AppRoutes />
        </BrowserRouter>
    );
}
