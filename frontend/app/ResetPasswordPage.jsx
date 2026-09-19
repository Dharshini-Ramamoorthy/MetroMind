import React, { useState, useEffect } from "react";
import { CheckCircle, AlertTriangle } from "lucide-react";
import SharedFooter from "./SharedFooter";
const teal = "#009688";
const inkH = "#0F172A";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
export default function ResetPasswordPage({ onNavigate, apiBaseUrl = "http://localhost:8080/api/v1/auth" }) {
    const [token, setToken] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const tokenParam = params.get("token");
        if (tokenParam)
            setToken(tokenParam);
        else
            setError("Invalid reset link. Token missing.");
    }, []);
    async function handleResetPassword() {
        setError(null);
        if (password.length < 6) {
            setError("Password must be at least 6 characters.");
            return;
        }
        if (password !== confirmPassword) {
            setError("Passwords do not match.");
            return;
        }
        setLoading(true);
        try {
            const res = await fetch(`${apiBaseUrl}/reset-password`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ token, newPassword: password }),
            });
            let data = null;
            try {
                data = await res.json();
            }
            catch { }
            if (!res.ok) {
                throw new Error(data?.message || "Failed to reset password.");
            }
            setLoading(false);
            setSuccess(true);
        }
        catch (err) {
            setLoading(false);
            setError(err.message || "Error contacting server.");
        }
    }
    return (<div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
      <main style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "60px 24px" }}>
        <div style={{ width: "100%", maxWidth: 420, background: "#fff", border: `1px solid ${bd}`, borderRadius: 20, padding: 32, display: "flex", flexDirection: "column", gap: 20 }}>
          <h2 style={{ fontFamily: DISPLAY, fontSize: 24, fontWeight: 800, color: inkH, margin: 0 }}>Set New Password</h2>

          {error && <div style={{ background: "#FEF2F2", color: "#B91C1C", padding: 12, borderRadius: 10, fontSize: 13, display: "flex", gap: 8 }}><AlertTriangle size={15}/> {error}</div>}
          {success && <div style={{ background: "#E6F4EA", color: "#14532D", padding: 12, borderRadius: 10, fontSize: 13, display: "flex", gap: 8 }}><CheckCircle size={15}/> Password updated!</div>}

          {!success ? (<>
              <input type="password" placeholder="New Password" value={password} onChange={e => setPassword(e.target.value)} style={{ width: "100%", padding: 10, borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box" }}/>
              <input type="password" placeholder="Confirm New Password" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} style={{ width: "100%", padding: 10, borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box" }}/>
              <button onClick={handleResetPassword} disabled={loading || !token} style={{ width: "100%", padding: 12, borderRadius: 11, border: "none", background: teal, color: "#fff", fontWeight: 700, cursor: loading ? "not-allowed" : "pointer" }}>
                {loading ? "Updating..." : "Reset Password"}
              </button>
            </>) : (<button onClick={() => onNavigate("signin")} style={{ width: "100%", padding: 12, borderRadius: 11, border: "none", background: teal, color: "#fff", fontWeight: 700, cursor: "pointer" }}>
              Sign In Now
            </button>)}
        </div>
      </main>
      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}
