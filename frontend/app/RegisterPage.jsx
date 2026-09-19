import React, { useState } from "react";
import { Mail, Lock, User, Eye, EyeOff, UserPlus, CheckCircle2 } from "lucide-react";
import SharedFooter from "./SharedFooter";

const teal = "#009688";
const tealDk = "#00786B";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";

// Only OC / MDS are offered here on purpose - SADA (System Admin) can never
// be self-registered (see user-service's UserServiceImpl.submitRegistration,
// which rejects it server-side regardless of what a request sends). Keeping
// it out of this dropdown entirely means nobody is invited to try.
const ROLE_OPTIONS = [
  { value: "OC", label: "Operations Controller (OC)" },
  { value: "MDS", label: "Maintenance Supervisor (MDS)" },
  { value: "SADA", label: "Schedule Approving Authority (Approver)" },
];

export default function RegisterPage({ onNavigate, apiBaseUrl = "http://localhost:8080/api/v1/auth" }) {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [role, setRole] = useState("OC");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!username.trim() || !email.trim() || !password.trim()) {
      setError("Please fill in all fields.");
      return;
    }
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
      const response = await fetch(`${apiBaseUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: username.trim(),
          email: email.trim(),
          password,
          role,
        }),
      });

      // Same defensive read as SignInPage - the gateway can return an empty
      // body on a 403/5xx (CORS rejection, circuit breaker) rather than JSON.
      const raw = await response.text();
      const data = raw ? (() => { try { return JSON.parse(raw); } catch { return null; } })() : null;

      if (!response.ok) {
        throw new Error(data?.message ?? `Registration failed (${response.status}). Please try again.`);
      }

      setSubmitted(true);
    } catch (err) {
      setError(err.message || "Unable to submit registration right now. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
      <nav style={{ background: "#fff", borderBottom: `1px solid ${bd}`, padding: "0 32px", height: 60, display: "flex", alignItems: "center" }}>
        <div onClick={() => onNavigate("landing")} style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }}>
          <div style={{
            width: 28, height: 28, borderRadius: 7, flexShrink: 0,
            background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
            display: "flex", alignItems: "center", justifyContent: "center",
          }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <path d="M3 4L7 7L3 10" stroke="#10B981" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M7 4L11 7L7 10" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <span style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 16, color: inkH, letterSpacing: "-0.02em" }}>
            MetroMind <span style={{ color: teal }}>KMRL</span>
          </span>
        </div>
      </nav>

      <main style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", padding: "60px 24px" }}>
        {submitted ? (
          <div style={{ width: "100%", maxWidth: 420, background: "#fff", border: `1px solid ${bd}`, borderRadius: 20, padding: 32, display: "flex", flexDirection: "column", gap: 16, textAlign: "center", alignItems: "center" }}>
            <div style={{ width: 48, height: 48, borderRadius: "50%", background: "rgba(16,185,129,0.1)", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <CheckCircle2 size={24} color="#10B981" />
            </div>
            <h2 style={{ fontFamily: DISPLAY, fontSize: 20, fontWeight: 800, color: inkH, margin: 0 }}>Registration submitted</h2>
            <p style={{ fontFamily: SANS, fontSize: 13.5, color: inkM, lineHeight: 1.6, margin: 0 }}>
              An administrator will review your request. You'll get an email at <strong>{email.trim()}</strong> once it's approved or rejected, and you can sign in with your chosen credentials after that.
            </p>
            <button onClick={() => onNavigate("signin")} style={{
              marginTop: 8, padding: "11px 24px", borderRadius: 11, border: "none",
              background: teal, color: "#fff", fontWeight: 700, fontFamily: SANS, cursor: "pointer",
            }}>
              Back to Sign In
            </button>
          </div>
        ) : (
          <form onSubmit={handleSubmit} style={{ width: "100%", maxWidth: 440, background: "#fff", border: `1px solid ${bd}`, borderRadius: 20, padding: 32, display: "flex", flexDirection: "column", gap: 18 }}>
            <div>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 24, fontWeight: 800, color: inkH, margin: 0 }}>Create Account</h2>
              <p style={{ fontFamily: SANS, fontSize: 13.5, color: inkM, marginTop: 4, marginBottom: 0 }}>
                Register for a KMRL portal account. New accounts require admin approval before you can sign in.
              </p>
            </div>

            {error && (
              <div style={{ background: "#FEF2F2", border: "1px solid #FCA5A5", color: "#991B1B", padding: "10px 14px", borderRadius: 10, fontSize: 13, fontFamily: SANS }}>
                {error}
              </div>
            )}

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                  Username
                </label>
                <div style={{ position: "relative" }}>
                  <input type="text" value={username} onChange={e => setUsername(e.target.value)} placeholder="Choose a username" disabled={loading} style={{ width: "100%", padding: "10px 12px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                  <User size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
                </div>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                  KMRL Email
                </label>
                <div style={{ position: "relative" }}>
                  <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@kmrl.co.in" disabled={loading} style={{ width: "100%", padding: "10px 12px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                  <Mail size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
                </div>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                  Requested Role
                </label>
                <select value={role} onChange={e => setRole(e.target.value)} disabled={loading} style={{ width: "100%", padding: "10px 12px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14, background: "#fff", color: inkB }}>
                  {ROLE_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                  Password
                </label>
                <div style={{ position: "relative" }}>
                  <input type={showPassword ? "text" : "password"} value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 6 characters" disabled={loading} style={{ width: "100%", padding: "10px 38px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                  <Lock size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
                  <button type="button" onClick={() => setShowPassword(!showPassword)} style={{ position: "absolute", right: 12, top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", color: inkM, padding: 0 }}>
                    {showPassword ? <EyeOff size={15}/> : <Eye size={15}/>}
                  </button>
                </div>
              </div>

              <div>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                  Confirm Password
                </label>
                <div style={{ position: "relative" }}>
                  <input type={showPassword ? "text" : "password"} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} placeholder="Re-enter your password" disabled={loading} style={{ width: "100%", padding: "10px 12px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                  <Lock size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
                </div>
              </div>
            </div>

            <button type="submit" disabled={loading} style={{
              width: "100%", padding: "12px 0", borderRadius: 11, border: "none",
              background: loading ? tealDk : teal, color: "#fff", fontWeight: 700, fontFamily: SANS,
              cursor: loading ? "not-allowed" : "pointer", display: "flex", justifyContent: "center",
              alignItems: "center", gap: 8, opacity: loading ? 0.7 : 1,
            }}>
              <UserPlus size={15}/> {loading ? "Submitting..." : "Submit Registration"}
            </button>

            <p style={{ textAlign: "center", fontFamily: SANS, fontSize: 13, color: inkM, margin: 0 }}>
              Already have an account?{" "}
              <button type="button" onClick={() => onNavigate("signin")} style={{ background: "none", border: "none", color: teal, fontWeight: 600, cursor: "pointer", fontFamily: SANS, fontSize: 13 }}>
                Sign In
              </button>
            </p>
          </form>
        )}
      </main>

      <SharedFooter onNavigate={onNavigate}/>
    </div>
  );
}
