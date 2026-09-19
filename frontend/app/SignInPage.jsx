import React, { useState } from "react";
import { Mail, Lock, Eye, EyeOff, LogIn } from "lucide-react";
import SharedFooter from "./SharedFooter";
const teal = "#009688";
const tealDk = "#00786B";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
export default function SignInPage({ onNavigate, onLoginSuccess, apiBaseUrl = "http://localhost:8080/api/v1/auth", }) {
    const [identifier, setIdentifier] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        if (!identifier.trim() || !password.trim()) {
            setError("Please fill in all fields.");
            return;
        }
        setLoading(true);
        try {
            const response = await fetch(`${apiBaseUrl}/login`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                credentials: "include",
                body: JSON.stringify({
                    username: identifier.trim(),
                    password: password.trim(),
                }),
            });
            // A 403/401/5xx from api-gateway (CORS rejection, circuit breaker,
            // etc.) can come back with an empty body (Content-Length: 0) rather
            // than a JSON error payload - response.json() throws "Unexpected
            // end of JSON input" on that, which used to surface as a confusing
            // crash instead of a real error message. Read as text first and
            // only parse it as JSON if there's actually something there.
            const raw = await response.text();
            const data = raw ? (() => {
                try {
                    return JSON.parse(raw);
                }
                catch {
                    return null;
                }
            })() : null;
            if (!response.ok) {
                throw new Error(data?.message
                    ?? `Sign-in failed (${response.status}). Please try again.`);
            }
            if (!data) {
                throw new Error("Sign-in response was empty. Please try again.");
            }
            // Handle backend payload: { token, user: {...} }
            const rawUser = data.user || data.data?.user || (data.role ? data : null);
            const userData = rawUser ? { ...rawUser, token: data.token ?? rawUser.token } : null;
            if (userData) {
                if (typeof onLoginSuccess === "function") {
                    onLoginSuccess(userData);
                }
                else {
                    console.error("onLoginSuccess callback is missing in props.");
                }
            }
            else {
                throw new Error("Sign-in response was incomplete. Please try again.");
            }
        }
        catch (err) {
            setError(err.message || "Unable to sign in right now. Please try again.");
        }
        finally {
            setLoading(false);
        }
    };
    return (<div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", background: "#F8FAFC" }}>
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
        <form onSubmit={handleSubmit} style={{ width: "100%", maxWidth: 420, background: "#fff", border: `1px solid ${bd}`, borderRadius: 20, padding: 32, display: "flex", flexDirection: "column", gap: 20 }}>
          <div>
            <h2 style={{ fontFamily: DISPLAY, fontSize: 24, fontWeight: 800, color: inkH, margin: 0 }}>Sign In</h2>
            <p style={{ fontFamily: SANS, fontSize: 13.5, color: inkM, marginTop: 4, marginBottom: 0 }}>Welcome back! Log in to your KMRL portal.</p>
          </div>

          {error && (<div style={{ background: "#FEF2F2", border: "1px solid #FCA5A5", color: "#991B1B", padding: "10px 14px", borderRadius: 10, fontSize: 13, fontFamily: SANS }}>
              {error}
            </div>)}

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB, display: "block", marginBottom: 4 }}>
                Username or Email
              </label>
              <div style={{ position: "relative" }}>
                <input type="text" value={identifier} onChange={e => setIdentifier(e.target.value)} placeholder="Enter your username or KMRL email" disabled={loading} style={{ width: "100%", padding: "10px 12px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                <Mail size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
              </div>
            </div>

            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 4 }}>
                <label style={{ fontFamily: SANS, fontSize: 13, fontWeight: 600, color: inkB }}>Password</label>
                <button type="button" onClick={() => onNavigate("forgot-password")} style={{ background: "none", border: "none", color: teal, fontFamily: SANS, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  Forgot Password?
                </button>
              </div>
              <div style={{ position: "relative" }}>
                <input type={showPassword ? "text" : "password"} value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" disabled={loading} style={{ width: "100%", padding: "10px 38px 10px 36px", borderRadius: 10, border: `1.5px solid ${bd}`, outline: "none", boxSizing: "border-box", fontFamily: SANS, fontSize: 14 }}/>
                <Lock size={16} style={{ position: "absolute", left: 12, top: "50%", transform: "translateY(-50%)", color: inkM }}/>
                <button type="button" onClick={() => setShowPassword(!showPassword)} style={{ position: "absolute", right: 12, top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", color: inkM, padding: 0 }}>
                  {showPassword ? <EyeOff size={15}/> : <Eye size={15}/>}
                </button>
              </div>
            </div>
          </div>

          <button type="submit" disabled={loading} style={{
            width: "100%",
            padding: "12px 0",
            borderRadius: 11,
            border: "none",
            background: loading ? tealDk : teal,
            color: "#fff",
            fontWeight: 700,
            fontFamily: SANS,
            cursor: loading ? "not-allowed" : "pointer",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            gap: 8,
            opacity: loading ? 0.7 : 1
        }}>
            <LogIn size={15}/> {loading ? "Signing In..." : "Sign In"}
          </button>

          <p style={{ textAlign: "center", fontFamily: SANS, fontSize: 13, color: inkM, margin: 0 }}>
            Don&apos;t have an account?{" "}
            <button type="button" onClick={() => onNavigate("register")} style={{ background: "none", border: "none", color: teal, fontWeight: 600, cursor: "pointer", fontFamily: SANS, fontSize: 13 }}>
              Register
            </button>
          </p>
        </form>
      </main>

      <SharedFooter onNavigate={onNavigate}/>
    </div>);
}
