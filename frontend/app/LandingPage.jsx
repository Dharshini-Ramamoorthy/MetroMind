import { useState, useRef } from "react";
import { ArrowRight, CheckCircle, Train, Calendar, Bell, Menu, X, LogOut } from "lucide-react";
import SharedFooter from "./SharedFooter";
// ─── Design Tokens ──────────────────────────────────────────────────────────
const teal = "#009688";
const tealDk = "#00786B";
const tealLight = "#E0F2F1";
const emerald = "#10B981";
const emeraldSoft = "#E6F4EA";
const inkH = "#0F172A";
const inkB = "#334155";
const inkM = "#64748B";
const bd = "rgba(15,23,42,0.08)";
const EASE = "cubic-bezier(0.16, 1, 0.3, 1)";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
// ─── Sub-components ───────────────────────────────────────────────────────────
function ChevronMark({ size = 32 }) {
    const r = Math.round(size * 0.265);
    return (<div style={{
            width: size, height: size, borderRadius: r, flexShrink: 0,
            background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
            display: "flex", alignItems: "center", justifyContent: "center",
        }}>
      <svg width={size * 0.44} height={size * 0.44} viewBox="0 0 14 14" fill="none">
        <path d="M3 4L7 7L3 10" stroke={emerald} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
        <path d="M7 4L11 7L7 10" stroke="#fff" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
    </div>);
}
function FeatureCard({ icon, title, description }) {
    const [hov, setHov] = useState(false);
    return (<div onMouseEnter={() => setHov(true)} onMouseLeave={() => setHov(false)} style={{
            background: "#fff", border: `1px solid ${bd}`, borderRadius: 14, padding: "26px 24px",
            boxShadow: hov ? "0 8px 32px rgba(15,23,42,0.10)" : "0 1px 3px rgba(0,0,0,0.05)",
            transform: hov ? "translateY(-3px)" : "translateY(0)",
            transition: `transform 0.28s ${EASE}, box-shadow 0.28s ${EASE}`,
        }}>
      <div style={{
            width: 40, height: 40, borderRadius: 10, marginBottom: 16,
            background: tealLight, color: tealDk,
            display: "flex", alignItems: "center", justifyContent: "center",
        }}>
        {icon}
      </div>
      <h3 style={{ fontFamily: DISPLAY, fontSize: 15.5, fontWeight: 700, color: inkH, marginBottom: 8 }}>
        {title}
      </h3>
      <p style={{ fontFamily: SANS, fontSize: 13.5, color: inkM, lineHeight: 1.65 }}>
        {description}
      </p>
    </div>);
}
export default function LandingPage({ isSignedIn, onNavigate, onLogOut, userName = "Guest", userRole = "Operator", }) {
    const [menuOpen, setMenuOpen] = useState(false);
    const featuresRef = useRef(null);
    function scrollToFeatures(e) {
        e.preventDefault();
        featuresRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        setMenuOpen(false);
    }
    return (<div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: SANS }}>

      {/* ── Sticky Header ── */}
      <header style={{
            position: "sticky", top: 0, zIndex: 50,
            backdropFilter: "blur(16px)", WebkitBackdropFilter: "blur(16px)",
            background: "rgba(248,250,252,0.92)",
            borderBottom: `1px solid ${bd}`,
        }}>
        <div style={{ maxWidth: 1200, margin: "0 auto", padding: "0 32px", height: 62, display: "flex", alignItems: "center", gap: 8 }}>

          {/* Logo */}
          <button onClick={() => onNavigate("landing")} style={{ display: "flex", alignItems: "center", gap: 10, background: "none", border: "none", cursor: "pointer", flexShrink: 0 }} aria-label="MetroMind Home">
            <ChevronMark size={28}/>
            <span style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: 15, color: inkH, letterSpacing: "-0.02em" }}>
              MetroMind <span style={{ color: teal }}>KMRL</span>
            </span>
          </button>

          {/* Navigation Links */}
          <nav className="landing-desktop-nav" style={{ display: "flex", alignItems: "center", gap: 2, marginLeft: 28 }}>
            {[
            { label: "Features", action: scrollToFeatures },
            { label: "Contact", action: () => onNavigate("contact") },
            { label: "About", action: () => onNavigate("about") },
        ].map(link => (<button key={link.label} onClick={link.action} style={{
                background: "none", border: "none", cursor: "pointer",
                padding: "8px 14px", borderRadius: 8,
                fontFamily: SANS, fontSize: 14, fontWeight: 500, color: inkB,
            }}>
                {link.label}
              </button>))}
          </nav>

          {/* Action Area: Sign In Only */}
          <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
            {isSignedIn ? (<>
                <button onClick={() => onNavigate("dashboard")} style={{
                padding: "8px 16px", borderRadius: 10, border: `1px solid ${bd}`,
                background: "#fff", fontFamily: SANS, fontSize: 13.5, fontWeight: 600, color: inkB,
                cursor: "pointer",
            }}>
                  Dashboard
                </button>
                <div style={{
                display: "flex", alignItems: "center", gap: 6, padding: "7px 12px",
                borderRadius: 999, border: `1px solid rgba(16,185,129,0.22)`,
                background: "rgba(16,185,129,0.07)",
                fontFamily: SANS, fontSize: 12.5, fontWeight: 600, color: inkH,
            }}>
                  <span style={{ width: 6, height: 6, borderRadius: "50%", background: emerald }}/>
                  <span>{userName}</span>
                  <span style={{ color: inkM, fontWeight: 400 }}>· {userRole}</span>
                </div>
                <button onClick={onLogOut} style={{ background: "none", border: "none", cursor: "pointer", color: inkM, display: "flex", alignItems: "center" }}>
                  <LogOut size={15}/>
                </button>
              </>) : (<>
              <button onClick={() => onNavigate("register")} style={{
                padding: "9px 18px", borderRadius: 10, border: `1px solid ${bd}`,
                background: "#fff", fontFamily: SANS, fontSize: 13.5, fontWeight: 600, color: inkB,
                cursor: "pointer",
            }}>
                Register
              </button>
              <button onClick={() => onNavigate("signin")} style={{
                padding: "9px 22px", borderRadius: 10, border: "none",
                background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
                fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: "#fff",
                cursor: "pointer", boxShadow: "0 2px 10px rgba(0,150,136,0.30)",
                letterSpacing: "-0.01em",
            }}>
                Sign In
              </button>
              </>)}

            {/* Mobile Menu Toggle */}
            <button className="landing-hamburger" onClick={() => setMenuOpen(o => !o)} style={{ background: "none", border: `1px solid ${bd}`, borderRadius: 8, width: 36, height: 36, display: "none", alignItems: "center", justifyContent: "center", cursor: "pointer", color: inkM }} aria-label="Toggle menu">
              {menuOpen ? <X size={16}/> : <Menu size={16}/>}
            </button>
          </div>
        </div>

        {/* Mobile Dropdown */}
        {menuOpen && (<div style={{ background: "rgba(248,250,252,0.98)", borderBottom: `1px solid ${bd}`, padding: "10px 20px 16px" }}>
            {[
                { label: "Features", action: scrollToFeatures },
                { label: "Contact", action: () => { onNavigate("contact"); setMenuOpen(false); } },
                { label: "About", action: () => { onNavigate("about"); setMenuOpen(false); } },
            ].map(link => (<button key={link.label} onClick={link.action} style={{
                    display: "block", width: "100%", textAlign: "left", background: "none", border: "none",
                    padding: "10px 12px", borderRadius: 8, fontFamily: SANS, fontSize: 14,
                    fontWeight: 500, color: inkB, cursor: "pointer",
                }}>{link.label}</button>))}

            {!isSignedIn && (<div style={{ marginTop: 8, paddingTop: 8, borderTop: `1px solid ${bd}`, display: "flex", flexDirection: "column", gap: 8 }}>
                <button onClick={() => { onNavigate("signin"); setMenuOpen(false); }} style={{ width: "100%", padding: "10px", borderRadius: 8, border: "none", background: teal, fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: "#fff", cursor: "pointer" }}>
                  Sign In
                </button>
                <button onClick={() => { onNavigate("register"); setMenuOpen(false); }} style={{ width: "100%", padding: "10px", borderRadius: 8, border: `1px solid ${bd}`, background: "#fff", fontFamily: SANS, fontSize: 14, fontWeight: 600, color: inkB, cursor: "pointer" }}>
                  Register
                </button>
              </div>)}
          </div>)}
      </header>

      {/* ── Hero Section ── */}
      <section style={{ maxWidth: 1200, margin: "0 auto", padding: "72px 32px 80px" }}>
        <div className="hero-grid" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 56, alignItems: "center" }}>

          {/* Left Text Column */}
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 22 }}>
              <span style={{ display: "block", width: 20, height: 2, borderRadius: 999, background: teal, flexShrink: 0 }}/>
              <span style={{ fontFamily: SANS, fontSize: 11.5, fontWeight: 700, color: inkM, letterSpacing: "0.09em", textTransform: "uppercase" }}>
                Kochi Metro Rail Limited
              </span>
            </div>

            <h1 style={{
            fontFamily: DISPLAY, fontWeight: 800, fontSize: "clamp(30px, 4vw, 42px)",
            color: inkH, letterSpacing: "-0.03em", lineHeight: 1.15, marginBottom: 20,
        }}>
              One system for every train&apos;s day, from depot to platform.
            </h1>

            <p style={{ fontFamily: SANS, fontSize: 16, color: inkB, lineHeight: 1.7, marginBottom: 36, maxWidth: 480 }}>
              MetroMind brings fleet readiness, induction planning, and line scheduling into a single shared view — so depot teams and controllers always work from the same information.
            </p>

            <button onClick={() => onNavigate(isSignedIn ? "dashboard" : "signin")} style={{
            display: "inline-flex", alignItems: "center", gap: 8,
            padding: "13px 28px", borderRadius: 12, border: "none",
            background: `linear-gradient(135deg, ${teal}, ${tealDk})`,
            fontFamily: DISPLAY, fontSize: 15, fontWeight: 700, color: "#fff",
            cursor: "pointer", boxShadow: "0 4px 20px rgba(0,150,136,0.32)",
            letterSpacing: "-0.01em",
        }}>
              Get Started <ArrowRight size={15}/>
            </button>
          </div>

          {/* Right Feature Card */}
          <div style={{
            background: "#fff", borderRadius: 18,
            boxShadow: "0 4px 8px rgba(15,23,42,0.06), 0 24px 48px rgba(15,23,42,0.10)",
            overflow: "hidden", border: `1px solid ${bd}`,
        }}>
            <div style={{ height: 3, background: `linear-gradient(90deg, ${teal}, ${emerald})` }}/>

            <div style={{ padding: "28px 28px 24px" }}>
              <h2 style={{ fontFamily: DISPLAY, fontSize: 16.5, fontWeight: 800, color: inkH, lineHeight: 1.35, marginBottom: 8 }}>
                Why depot and line teams choose MetroMind
              </h2>
              <p style={{ fontFamily: SANS, fontSize: 13, color: inkM, marginBottom: 24, lineHeight: 1.55 }}>
                Purpose-built for KMRL operations — not adapted from generic tools.
              </p>

              <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
                {[
            {
                title: "One shared view",
                desc: "Controllers, depot teams, and approvers all work from the same information.",
            },
            {
                title: "Fewer schedule conflicts",
                desc: "Catch overlaps and delays before they reach the platform.",
            },
            {
                title: "Clear accountability",
                desc: "Every induction and schedule change is logged, with a record of who approved it.",
            },
        ].map(item => (<div key={item.title} style={{ display: "flex", gap: 12 }}>
                    <div style={{
                width: 22, height: 22, borderRadius: "50%", flexShrink: 0, marginTop: 1,
                background: emeraldSoft, display: "flex", alignItems: "center", justifyContent: "center",
            }}>
                      <CheckCircle size={13} color={emerald} strokeWidth={2.5}/>
                    </div>
                    <div>
                      <p style={{ fontFamily: DISPLAY, fontSize: 13.5, fontWeight: 700, color: inkH, marginBottom: 3 }}>
                        {item.title}
                      </p>
                      <p style={{ fontFamily: SANS, fontSize: 12.5, color: inkM, lineHeight: 1.6 }}>
                        {item.desc}
                      </p>
                    </div>
                  </div>))}
              </div>

              <div style={{ borderTop: `1px solid ${bd}`, marginTop: 22, paddingTop: 16, display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ width: 7, height: 7, borderRadius: "50%", background: emerald, flexShrink: 0, boxShadow: "0 0 0 3px rgba(16,185,129,0.18)" }}/>
                <span style={{ fontFamily: SANS, fontSize: 12, color: inkM }}>
                  Trusted by depot operations teams across the network.
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── Features Grid Section ── */}
      <section ref={featuresRef} id="features" style={{ background: "#fff", borderTop: `1px solid ${bd}`, borderBottom: `1px solid ${bd}` }}>
        <div style={{ maxWidth: 1200, margin: "0 auto", padding: "72px 32px 80px" }}>
          <div style={{ marginBottom: 44 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 16 }}>
              <span style={{ display: "block", width: 16, height: 2, borderRadius: 999, background: teal, flexShrink: 0 }}/>
              <span style={{ fontFamily: SANS, fontSize: 11, fontWeight: 700, color: inkM, letterSpacing: "0.1em", textTransform: "uppercase" }}>
                Features
              </span>
            </div>
            <h2 style={{ fontFamily: DISPLAY, fontWeight: 800, fontSize: "clamp(24px, 3vw, 32px)", color: inkH, letterSpacing: "-0.025em", marginBottom: 12 }}>
              Everything the line needs, in one place
            </h2>
            <p style={{ fontFamily: SANS, fontSize: 15, color: inkM, maxWidth: 520, lineHeight: 1.65 }}>
              Three core capabilities cover the daily cycle of running a metro fleet, from the depot yard to the last service.
            </p>
          </div>

          <div className="features-grid" style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 20 }}>
            <FeatureCard icon={<Train size={18}/>} title="Induction planning" description="Deciding which rakes enter service each day based on maintenance clearance, mileage balancing, and fitness certificates."/>
            <FeatureCard icon={<Calendar size={18}/>} title="Live scheduling" description="Tracking headways and running times, catching conflicts before they reach a platform."/>
            <FeatureCard icon={<Bell size={18}/>} title="Alerts & reporting" description="Getting flagged when something needs attention, with a clear daily record of what happened on the line."/>
          </div>
        </div>
      </section>

      {/* ── Footer ── */}
      <SharedFooter onNavigate={onNavigate}/>

      {/* Responsive Styles */}
      <style>{`
        @media (max-width: 900px) {
          .hero-grid      { grid-template-columns: 1fr !important; gap: 40px !important; }
          .features-grid  { grid-template-columns: 1fr 1fr !important; }
          .landing-desktop-nav { display: none !important; }
          .landing-hamburger   { display: flex !important; }
        }
        @media (max-width: 560px) {
          .features-grid  { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </div>);
}
