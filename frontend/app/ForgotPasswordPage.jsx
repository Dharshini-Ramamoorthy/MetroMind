import React, { useState } from "react";
import { CheckCircle, AlertTriangle, ArrowLeft, KeyRound } from "lucide-react";
import SharedFooter from "./SharedFooter";
const teal = "#009688";
const emeraldSoft = "#E6F4EA";
const roseSoft = "#FEF2F2";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
export default function ForgotPasswordPage({ onNavigate, apiBaseUrl = "http://localhost:8080/api/v1/auth" }) {
    const [email, setEmail] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);
    const isValidEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    async function handleSendResetLink() {
        setError(null);
        if (!email || !isValidEmail) {
            setError("Please enter a valid email address.");
            return;
        }
        setLoading(true);
        try {
            const res = await fetch(`${apiBaseUrl}/forgot-password`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email }),
            });
            let data = null;
            try {
                data = await res.json();
            }
            catch { }
            if (!res.ok) {
                throw new Error(data?.message || "Failed to send reset email.");
            }
            setLoading(false);
            setSuccess(true);
        }
        catch (err) {
            setLoading(false);
            setError(err.message || "Could not reach authentication server.");
        }
    }
    return (<div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
      <nav style={{ background: "#fff", borderBottom: `1px solid ${bd}`, padding: "0 32px", height: 60, display: "flex", alignItems: "center" }}>
        <span style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 17, color: inkH }}>
          MetroMind <span style={{ color: teal }}>KMRL</span>
        </span>
        <button onClick={() => onNavigate("signin")} style={{ marginLeft: "auto", background: "none", border: "none", cursor: "pointer", fontFamily: SANS, fontSize: 13.5, color: inkM, display: "flex", alignItems: "center", gap: 6 }}>
          <ArrowLeft size={14}/> Back to Sign In
        </button>
      </nav>

      <main style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "60px 24px" }}>
        <div style={{ width: "100%", maxWidth: 420, background: "#fff", border: `1px solid ${bd}`, borderRadius: 20, padding: 32, display: "flex", flexDirection: "column", gap: 20 }}>
          <div style={{ width: 42, height: 42, borderRadius: 12, background: "#E6F4EA", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <KeyRound size={20} color={teal}/>
          </div>
          <h2 style={{ fontFamily: DISPLAY, fontSize: 24, fontWeight: 800, color: inkH, margin: 0 }}>Forgot Password?</h2>
          <p style={{ fontFamily: SANS, fontSize: 13.5, color: inkM, margin: 0 }}>
            {success ? `Reset link sent to ${email}.` : "Enter your email to reset your password."}
          </p>

          {error && <div style={{ background: roseSoft, color: "#B91C1C", padding: 12, borderRadius: 10, fontSize: 13, display: "flex", gap: 8 }}><AlertTriangle size={15}/> {error}</div>}
          {success && <div style={{ background: emeraldSoft, color: "#14532D", padding: 12, borderRadius: 10, fontSize: 13, display: "flex", gap: 8 }}><CheckCircle size={15}/> Reset link sent! Check your inbox.</div>}

          {!success && (<>
              <input type="email" value={email} onChange={e => { setEmail(e.target.value); setError(null); }} placeholder="user@kmrl.co.in" style={{ width: "100%", padding: 10, borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box" }}/>
              <button onClick={handleSendResetLink} disabled={loading || !isValidEmail} style={{ width: "100%", padding: 12, borderRadius: 11, border: "none", background: teal, color: "#fff", fontWeight: 700, cursor: loading ? "not-allowed" : "pointer" }}>
                {loading ? "Sending..." : "Send Reset Link"}
              </button>
            </>)}

          <button onClick={() => onNavigate("signin")} style={{ width: "100%", padding: 11, borderRadius: 11, border: `1px solid ${bd}`, background: "#fff", color: inkB, fontWeight: 600, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 6 }}>
            <ArrowLeft size={14}/> Return to Sign In
          </button>
        </div>
      </main>
      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}
