import { useState, useEffect } from "react";
import { User, Mail, Lock, Eye, EyeOff, CheckCircle, AlertCircle, Shield, ArrowLeft, Save, Loader2 } from "lucide-react";
import SharedHeader from "./SharedHeader";
import SharedFooter from "./SharedFooter";
// ─── Design Tokens ─────────────────────────────────────────────────────────────
const teal = "#009688";
const tealDk = "#00786B";
const tealLt = "#E0F2F1";
const emerald = "#10B981";
const rose = "#EF4444";
const amber = "#F59E0B";
const inkH = "#0F172A";
const inkM = "#64748B";
const line = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
// ─── API Base (user-service on :8081) ─────────────────────────────────────────
const USER_API = (typeof import.meta !== "undefined" && import.meta.env?.VITE_AUTH_API_BASE) ||
    "http://localhost:8080/api/v1/auth";
// ─── Role label mapping (ERole enum → display label) ──────────────────────────
const ROLE_LABELS = {
    ROLE_ADMIN: "System Admin",
    ROLE_OPS_CONTROLLER: "Operation Controller",
    ROLE_MAINTENANCE_MANAGER: "Maintenance Manager",
    // Frontend role keys (from App.tsx) for fallback display
    OpsController: "Operation Controller",
    MaintenanceManager: "Maintenance Manager",
    Admin: "System Admin",
    Operator: "Operator",
    Approver: "Approver",
};
// ─── Avatar Helpers ───────────────────────────────────────────────────────────
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
    const idx = (name.charCodeAt(0) || 0) % AVATAR_GRADIENTS.length;
    return AVATAR_GRADIENTS[idx];
}
// ─── Password Strength ────────────────────────────────────────────────────────
function getPasswordStrength(pw) {
    if (!pw)
        return { score: 0, label: "", color: line };
    let score = 0;
    if (pw.length >= 8)
        score++;
    if (pw.length >= 12)
        score++;
    if (/[A-Z]/.test(pw))
        score++;
    if (/[0-9]/.test(pw))
        score++;
    if (/[^A-Za-z0-9]/.test(pw))
        score++;
    if (score <= 1)
        return { score, label: "Weak", color: rose };
    if (score <= 3)
        return { score, label: "Medium", color: amber };
    return { score, label: "Strong", color: emerald };
}
// ─── Input Field Component ────────────────────────────────────────────────────
function FieldInput({ label, id, type = "text", value, onChange, placeholder, icon, readOnly = false, hint, error, rightElement, }) {
    return (<div className="flex flex-col gap-1.5">
      <label htmlFor={id} style={{ fontFamily: SANS, fontSize: 12, fontWeight: 700, color: inkM, letterSpacing: "0.04em", textTransform: "uppercase" }}>
        {label}
      </label>
      <div style={{ position: "relative" }}>
        <div style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", pointerEvents: "none" }}>
          {icon}
        </div>
        <input id={id} type={type} value={value} readOnly={readOnly} onChange={e => onChange?.(e.target.value)} placeholder={placeholder} style={{
            width: "100%", padding: "11px 12px 11px 40px",
            paddingRight: rightElement ? "44px" : "12px",
            borderRadius: 10,
            border: `1.5px solid ${error ? rose : readOnly ? line : "rgba(15,23,42,0.14)"}`,
            background: readOnly ? "#F8FAFC" : "#fff",
            fontFamily: SANS, fontSize: 13.5,
            color: readOnly ? inkM : inkH, fontWeight: readOnly ? 400 : 500,
            outline: "none", boxSizing: "border-box", transition: "border-color 0.15s",
        }} onFocus={e => { if (!readOnly)
        e.currentTarget.style.borderColor = teal; }} onBlur={e => { e.currentTarget.style.borderColor = error ? rose : "rgba(15,23,42,0.14)"; }}/>
        {rightElement && (<div style={{ position: "absolute", right: 12, top: "50%", transform: "translateY(-50%)" }}>
            {rightElement}
          </div>)}
      </div>
      {error && (<span style={{ fontFamily: SANS, fontSize: 11.5, color: rose, display: "flex", alignItems: "center", gap: 4 }}>
          <AlertCircle size={12}/> {error}
        </span>)}
      {hint && !error && (<span style={{ fontFamily: SANS, fontSize: 11.5, color: inkM }}>{hint}</span>)}
    </div>);
}
// ─── Toast Component ──────────────────────────────────────────────────────────
function Toast({ msg, type }) {
    return (<div className="fixed bottom-6 right-6 z-50 flex items-center gap-3 px-5 py-3.5 rounded-2xl shadow-xl text-sm font-semibold" style={{
            background: type === "success" ? "#ECFDF5" : "#FFF1F2",
            border: `1.5px solid ${type === "success" ? "#A7F3D0" : "#FECDD3"}`,
            color: type === "success" ? "#065F46" : "#9F1239",
            fontFamily: SANS, animation: "slideUp 0.25s ease",
        }}>
      {type === "success" ? <CheckCircle size={17} color={emerald}/> : <AlertCircle size={17} color={rose}/>}
      {msg}
    </div>);
}
// ─── Section Card ─────────────────────────────────────────────────────────────
function SectionCard({ title, subtitle, icon, children }) {
    return (<div style={{ background: "#fff", borderRadius: 16, border: `1px solid ${line}`, overflow: "hidden", boxShadow: "0 1px 4px rgba(0,0,0,0.05)" }}>
      <div style={{ padding: "18px 24px 16px", borderBottom: `1px solid ${line}`, background: "#FAFBFC", display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: tealLt, display: "flex", alignItems: "center", justifyContent: "center" }}>
          {icon}
        </div>
        <div>
          <p style={{ fontFamily: DISPLAY, fontSize: 14.5, fontWeight: 800, color: inkH, margin: 0 }}>{title}</p>
          <p style={{ fontFamily: SANS, fontSize: 12, color: inkM, margin: 0 }}>{subtitle}</p>
        </div>
      </div>
      <div style={{ padding: "22px 24px" }}>{children}</div>
    </div>);
}
// ─── Main SettingsPage ────────────────────────────────────────────────────────
export default function SettingsPage({ isSignedIn, onNavigate, onLogOut, userName = "User", userRole = "_default", userEmail = "", userId = "", }) {
    // Profile state — initialised from props, then overwritten by API
    const [profile, setProfile] = useState({
        id: userId,
        username: userName,
        email: userEmail,
        role: userRole,
    });
    const [profileForm, setProfileForm] = useState({ username: userName, email: userEmail });
    const [profileSaving, setProfileSaving] = useState(false);
    const [profileErrors, setProfileErrors] = useState({});
    // Password state
    const [pwForm, setPwForm] = useState({ current: "", newPw: "", confirm: "" });
    const [showPw, setShowPw] = useState({ current: false, newPw: false, confirm: false });
    const [pwSaving, setPwSaving] = useState(false);
    const [pwErrors, setPwErrors] = useState({});
    const [toast, setToast] = useState(null);
    const [loading, setLoading] = useState(false);
    const initials = getInitials(profile.username);
    const [g1, g2] = getAvatarGradient(profile.username);
    const pwStrength = getPasswordStrength(pwForm.newPw);
    const roleLabel = ROLE_LABELS[profile.role] ?? profile.role;
    // ── Fetch profile: localStorage first (email), then confirm from backend via Bearer token
    useEffect(() => {
        const fetchProfile = async () => {
            setLoading(true);
            try {
                // Step 1: Load from localStorage immediately so email is never empty
                const saved = localStorage.getItem("kmrl_user");
                if (saved) {
                    const parsed = JSON.parse(saved);
                    setProfile(prev => ({
                        ...prev,
                        id: String(parsed.id || prev.id),
                        username: parsed.username || prev.username,
                        email: parsed.email || prev.email,
                        role: parsed.role || prev.role,
                    }));
                    setProfileForm({ username: parsed.username || userName, email: parsed.email || "" });
                }
                // Step 2: Verify with backend using Bearer token
                const token = localStorage.getItem("auth_token");
                const headers = { "Content-Type": "application/json" };
                if (token)
                    headers["Authorization"] = `Bearer ${token}`;
                const res = await fetch(`${USER_API}/me`, { headers, credentials: "include" });
                if (res.ok) {
                    const data = await res.json();
                    const updated = {
                        id: String(data.id || ""),
                        username: data.username || userName,
                        email: data.email || "",
                        role: data.role || userRole,
                    };
                    setProfile(updated);
                    setProfileForm({ username: updated.username, email: updated.email });
                    // Also update localStorage so it stays in sync
                    const existing = JSON.parse(localStorage.getItem("kmrl_user") || "{}");
                    localStorage.setItem("kmrl_user", JSON.stringify({ ...existing, ...updated }));
                }
            }
            catch {
                // Network offline — props/localStorage data is already shown
            }
            finally {
                setLoading(false);
            }
        };
        fetchProfile();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    function showToast(msg, type) {
        setToast({ msg, type });
        setTimeout(() => setToast(null), 4000);
    }
    // ── Profile Save → PUT /api/v1/auth/me ──────────────────────────────────────
    async function handleProfileSave(e) {
        e.preventDefault();
        const errors = {};
        if (!profileForm.username.trim() || profileForm.username.trim().length < 3) {
            errors.username = "Username must be at least 3 characters.";
        }
        if (!profileForm.email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profileForm.email)) {
            errors.email = "Please enter a valid email address.";
        }
        if (Object.keys(errors).length > 0) {
            setProfileErrors(errors);
            return;
        }
        setProfileSaving(true);
        setProfileErrors({});
        try {
            const token = localStorage.getItem("auth_token");
            const headers = { "Content-Type": "application/json" };
            if (token)
                headers["Authorization"] = `Bearer ${token}`;
            const res = await fetch(`${USER_API}/me`, {
                method: "PUT",
                headers,
                credentials: "include",
                body: JSON.stringify(profileForm),
            });
            // Safe JSON parse — handle possible empty body
            let data = {};
            const ct = res.headers.get("content-type") || "";
            if (ct.includes("application/json")) {
                try {
                    data = await res.json();
                }
                catch {
                    data = {};
                }
            }
            if (!res.ok)
                throw new Error(data.message || `Update failed (${res.status}).`);
            const u = data.user || profileForm;
            setProfile(prev => ({ ...prev, username: u.username, email: u.email }));
            setProfileForm({ username: u.username, email: u.email });
            // Keep localStorage in sync
            const existing = JSON.parse(localStorage.getItem("kmrl_user") || "{}");
            localStorage.setItem("kmrl_user", JSON.stringify({ ...existing, username: u.username, email: u.email }));
            showToast("Profile updated successfully!", "success");
        }
        catch (err) {
            showToast(err.message || "Failed to update profile.", "error");
        }
        finally {
            setProfileSaving(false);
        }
    }
    // ── Password Save → PUT /api/v1/auth/me/password ────────────────────────────
    async function handlePasswordSave(e) {
        e.preventDefault();
        const errors = {};
        if (!pwForm.current)
            errors.current = "Current password is required.";
        if (!pwForm.newPw || pwForm.newPw.length < 6)
            errors.newPw = "New password must be at least 6 characters.";
        if (pwForm.newPw !== pwForm.confirm)
            errors.confirm = "Passwords do not match.";
        if (pwStrength.score < 2)
            errors.newPw = "Password is too weak. Add numbers and uppercase letters.";
        if (Object.keys(errors).length > 0) {
            setPwErrors(errors);
            return;
        }
        setPwSaving(true);
        setPwErrors({});
        try {
            const token = localStorage.getItem("auth_token");
            const headers = { "Content-Type": "application/json" };
            if (token)
                headers["Authorization"] = `Bearer ${token}`;
            const res = await fetch(`${USER_API}/me/password`, {
                method: "PUT",
                headers,
                credentials: "include",
                body: JSON.stringify({
                    currentPassword: pwForm.current,
                    newPassword: pwForm.newPw,
                }),
            });
            // Safe JSON parse — backend returns Map<String,String> but guard against empty body
            let data = {};
            const ct = res.headers.get("content-type") || "";
            if (ct.includes("application/json")) {
                try {
                    data = await res.json();
                }
                catch {
                    data = {};
                }
            }
            if (!res.ok)
                throw new Error(data.message || `Password change failed (${res.status}).`);
            setPwForm({ current: "", newPw: "", confirm: "" });
            showToast(data.message || "Password changed successfully!", "success");
        }
        catch (err) {
            showToast(err.message || "Failed to change password.", "error");
        }
        finally {
            setPwSaving(false);
        }
    }
    // ── Logout via API → POST /api/v1/auth/logout ──────────────────────────────
    async function handleFullLogout() {
        try {
            await fetch(`${USER_API}/logout`, { method: "POST", credentials: "include" });
        }
        catch { /* ignore */ }
        onLogOut();
    }
    return (<div className="min-h-screen flex flex-col" style={{ background: "#F8FAFC" }}>
      <SharedHeader activePage="settings" isSignedIn={isSignedIn} onNavigate={onNavigate} onLogOut={handleFullLogout} userName={profile.username} userRole={userRole}/>

      {/* Page Header */}
      <div style={{ background: "#fff", borderBottom: `1px solid ${line}` }}>
        <div className="max-w-4xl mx-auto px-6 py-6 flex items-center gap-4">
          <button onClick={() => onNavigate("dashboard")} style={{
            display: "flex", alignItems: "center", gap: 6,
            background: "none", border: "none", cursor: "pointer",
            fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkM,
            padding: "6px 10px", borderRadius: 8, transition: "background 0.15s",
        }} className="hover:bg-slate-100">
            <ArrowLeft size={14}/> Back
          </button>
          <div style={{ width: 1, height: 28, background: line }}/>
          <div>
            <h1 style={{ fontFamily: DISPLAY, fontSize: 22, fontWeight: 800, color: inkH, margin: 0 }}>
              Profile &amp; Account Settings
            </h1>
            <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, margin: 0 }}>
              Manage your KMRL MetroMind account details and security preferences.
            </p>
          </div>
        </div>
      </div>

      <main className="flex-1 max-w-4xl mx-auto w-full px-6 py-8 flex flex-col gap-6">

        {/* ── Profile Hero Card ───────────────────────────────────────────────── */}
        <div style={{
            background: `linear-gradient(135deg, ${teal} 0%, ${tealDk} 100%)`,
            borderRadius: 18, padding: "24px 28px",
            display: "flex", alignItems: "center", gap: 20,
            boxShadow: "0 4px 24px rgba(0,150,136,0.25)",
            position: "relative", overflow: "hidden",
        }}>
          <div style={{ position: "absolute", right: -40, top: -40, width: 180, height: 180, borderRadius: "50%", background: "rgba(255,255,255,0.06)" }}/>
          <div style={{ position: "absolute", right: 40, bottom: -60, width: 140, height: 140, borderRadius: "50%", background: "rgba(255,255,255,0.04)" }}/>

          <div style={{ position: "relative", flexShrink: 0 }}>
            <div style={{
            width: 72, height: 72, borderRadius: "50%",
            background: `linear-gradient(135deg, ${g1}, ${g2})`,
            border: "3px solid rgba(255,255,255,0.30)",
            display: "flex", alignItems: "center", justifyContent: "center",
            boxShadow: "0 4px 16px rgba(0,0,0,0.20)",
        }}>
              <span style={{ fontFamily: DISPLAY, fontWeight: 900, fontSize: 24, color: "#fff" }}>{initials}</span>
            </div>
            <div style={{
            position: "absolute", bottom: 0, right: 0,
            width: 22, height: 22, borderRadius: "50%",
            background: emerald, border: "2.5px solid #fff",
            display: "flex", alignItems: "center", justifyContent: "center",
        }}>
              <div style={{ width: 7, height: 7, borderRadius: "50%", background: "#fff" }}/>
            </div>
          </div>

          <div style={{ flex: 1 }}>
            <p style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: "#fff", margin: 0 }}>{profile.username}</p>
            <p style={{ fontFamily: SANS, fontSize: 13, color: "rgba(255,255,255,0.80)", margin: "2px 0 10px" }}>{profile.email}</p>
            <div className="flex flex-wrap gap-2">
              <span style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: "#fff", background: "rgba(255,255,255,0.18)", padding: "3px 10px", borderRadius: 999 }}>
                {roleLabel}
              </span>
            </div>
          </div>

          {loading && (<div style={{ flexShrink: 0 }}>
              <Loader2 size={18} color="rgba(255,255,255,0.60)" className="animate-spin"/>
            </div>)}
        </div>

        {/* ── Section 1: Profile Information ──────────────────────────────────── */}
        <SectionCard title="Profile Information" subtitle="Update your display name and email." icon={<User size={18} color={teal}/>}>
          <form onSubmit={handleProfileSave} className="flex flex-col gap-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <FieldInput id="username" label="Username" value={profileForm.username} onChange={v => setProfileForm(f => ({ ...f, username: v }))} placeholder="e.g. priya_kumar" icon={<User size={15} color={inkM}/>} error={profileErrors.username} hint="This name appears across the header and audit logs."/>
              <FieldInput id="role" label="System Role" value={roleLabel} icon={<Shield size={15} color={inkM}/>} readOnly hint="Role is assigned by admin and cannot be changed here."/>
            </div>

            <FieldInput id="email" label="Email Address" type="email" value={profileForm.email} onChange={v => setProfileForm(f => ({ ...f, email: v }))} placeholder="e.g. priya.kumar@kmrl.in" icon={<Mail size={15} color={inkM}/>} error={profileErrors.email} hint="Used for login, notifications and password reset."/>

            <div style={{ display: "flex", justifyContent: "flex-end", paddingTop: 4 }}>
              <button type="submit" disabled={profileSaving} style={{
            display: "flex", alignItems: "center", gap: 8,
            padding: "10px 24px", borderRadius: 10, border: "none",
            background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
            fontFamily: DISPLAY, fontSize: 13.5, fontWeight: 700, color: "#fff",
            cursor: profileSaving ? "not-allowed" : "pointer",
            opacity: profileSaving ? 0.75 : 1,
            boxShadow: "0 2px 8px rgba(0,150,136,0.28)", transition: "opacity 0.15s",
        }}>
                {profileSaving
            ? <><Loader2 size={14} className="animate-spin"/> Saving…</>
            : <><Save size={14}/> Save Profile</>}
              </button>
            </div>
          </form>
        </SectionCard>

        {/* ── Section 2: Change Password ──────────────────────────────────────── */}
        <SectionCard title="Change Password" subtitle="Keep your account secure with a strong unique password." icon={<Lock size={18} color={teal}/>}>
          <form onSubmit={handlePasswordSave} className="flex flex-col gap-5">

            <FieldInput id="currentPassword" label="Current Password" type={showPw.current ? "text" : "password"} value={pwForm.current} onChange={v => setPwForm(f => ({ ...f, current: v }))} placeholder="Enter your current password" icon={<Lock size={15} color={inkM}/>} error={pwErrors.current} rightElement={<button type="button" onClick={() => setShowPw(s => ({ ...s, current: !s.current }))} style={{ background: "none", border: "none", cursor: "pointer", padding: 2 }}>
                  {showPw.current ? <EyeOff size={15} color={inkM}/> : <Eye size={15} color={inkM}/>}
                </button>}/>

            <FieldInput id="newPassword" label="New Password" type={showPw.newPw ? "text" : "password"} value={pwForm.newPw} onChange={v => setPwForm(f => ({ ...f, newPw: v }))} placeholder="At least 6 characters" icon={<Lock size={15} color={inkM}/>} error={pwErrors.newPw} rightElement={<button type="button" onClick={() => setShowPw(s => ({ ...s, newPw: !s.newPw }))} style={{ background: "none", border: "none", cursor: "pointer", padding: 2 }}>
                  {showPw.newPw ? <EyeOff size={15} color={inkM}/> : <Eye size={15} color={inkM}/>}
                </button>}/>

            {/* Password Strength Bar */}
            {pwForm.newPw && (<div style={{ marginTop: -8 }}>
                <div style={{ display: "flex", gap: 4, marginBottom: 4 }}>
                  {[1, 2, 3, 4, 5].map(i => (<div key={i} style={{
                    flex: 1, height: 4, borderRadius: 999,
                    background: i <= pwStrength.score ? pwStrength.color : line,
                    transition: "background 0.25s",
                }}/>))}
                </div>
                <p style={{ fontFamily: SANS, fontSize: 11.5, color: pwStrength.color, fontWeight: 600, margin: 0 }}>
                  {pwStrength.label} password
                  {pwStrength.score < 3 && " — add uppercase letters, numbers, and symbols."}
                </p>
              </div>)}

            <FieldInput id="confirmPassword" label="Confirm New Password" type={showPw.confirm ? "text" : "password"} value={pwForm.confirm} onChange={v => setPwForm(f => ({ ...f, confirm: v }))} placeholder="Re-enter new password" icon={<Lock size={15} color={inkM}/>} error={pwErrors.confirm} hint={pwForm.confirm && pwForm.newPw === pwForm.confirm ? "✓ Passwords match" : undefined} rightElement={<button type="button" onClick={() => setShowPw(s => ({ ...s, confirm: !s.confirm }))} style={{ background: "none", border: "none", cursor: "pointer", padding: 2 }}>
                  {showPw.confirm ? <EyeOff size={15} color={inkM}/> : <Eye size={15} color={inkM}/>}
                </button>}/>

            {/* Password Requirements Checklist */}
            <div style={{ background: "#F8FAFC", border: `1px solid ${line}`, borderRadius: 10, padding: "12px 14px" }}>
              <p style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, marginBottom: 6, textTransform: "uppercase", letterSpacing: "0.04em" }}>
                Password Requirements
              </p>
              {[
            ["At least 6 characters", pwForm.newPw.length >= 6],
            ["At least one uppercase letter", /[A-Z]/.test(pwForm.newPw)],
            ["At least one number", /[0-9]/.test(pwForm.newPw)],
            ["At least one special character", /[^A-Za-z0-9]/.test(pwForm.newPw)],
        ].map(([label, met]) => (<div key={label} style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 3 }}>
                  <div style={{
                width: 14, height: 14, borderRadius: "50%",
                background: met ? emerald : line,
                display: "flex", alignItems: "center", justifyContent: "center",
                flexShrink: 0, transition: "background 0.2s",
            }}>
                    {met && <CheckCircle size={9} color="#fff"/>}
                  </div>
                  <span style={{ fontFamily: SANS, fontSize: 11.5, color: met ? emerald : inkM, transition: "color 0.2s" }}>
                    {label}
                  </span>
                </div>))}
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", paddingTop: 4 }}>
              <button type="submit" disabled={pwSaving} style={{
            display: "flex", alignItems: "center", gap: 8,
            padding: "10px 24px", borderRadius: 10, border: "none",
            background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
            fontFamily: DISPLAY, fontSize: 13.5, fontWeight: 700, color: "#fff",
            cursor: pwSaving ? "not-allowed" : "pointer",
            opacity: pwSaving ? 0.75 : 1,
            boxShadow: "0 2px 8px rgba(0,150,136,0.28)", transition: "opacity 0.15s",
        }}>
                {pwSaving
            ? <><Loader2 size={14} className="animate-spin"/> Updating…</>
            : <><Lock size={14}/> Update Password</>}
              </button>
            </div>
          </form>
        </SectionCard>

      </main>

      <SharedFooter onNavigate={onNavigate}/>

      {toast && <Toast msg={toast.msg} type={toast.type}/>}

      <style>{`
        @keyframes slideUp {
          from { opacity: 0; transform: translateY(12px); }
          to   { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>);
}
