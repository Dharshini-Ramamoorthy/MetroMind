import { Lock, X } from "lucide-react";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const inkH = "#0F172A";
const inkM = "#64748B";
const inkMuted = "#94A3B8";
const amber = "#D97706";
const amberSoft = "rgba(245,158,11,0.10)";
const amberBorder = "rgba(245,158,11,0.22)";
const EASE = "cubic-bezier(0.16, 1, 0.3, 1)";
const ROLE_DISPLAY = {
    OpsController: "Operation Controller",
    MaintenanceManager: "Maintenance Manager",
    Admin: "Admin",
    Operator: "Operator",
    Approver: "Approver",
};
export default function ReadOnlyPopup({ pageName, userRole, onClose }) {
    const roleLabel = ROLE_DISPLAY[userRole] ?? userRole;
    return (<div style={{
            position: "fixed", inset: 0, zIndex: 9000,
            background: "rgba(15,23,42,0.50)",
            backdropFilter: "blur(4px)", WebkitBackdropFilter: "blur(4px)",
            display: "flex", alignItems: "center", justifyContent: "center",
            padding: 24,
            animation: "ro-fade-in 0.18s ease",
        }} onClick={onClose}>
      <div onClick={e => e.stopPropagation()} style={{
            background: "#fff", borderRadius: 18, padding: "32px 32px 28px",
            maxWidth: 440, width: "100%",
            boxShadow: "0 24px 64px rgba(15,23,42,0.22)",
            border: `1px solid ${amberBorder}`,
            animation: `ro-slide-in 0.22s ${EASE}`,
            position: "relative",
        }}>
        {/* Dismiss X */}
        <button onClick={onClose} style={{
            position: "absolute", top: 16, right: 16,
            width: 28, height: 28, borderRadius: 7,
            border: "1px solid rgba(15,23,42,0.08)",
            background: "#F8FAFC", color: inkM,
            display: "flex", alignItems: "center", justifyContent: "center",
            cursor: "pointer",
        }} aria-label="Dismiss">
          <X size={13}/>
        </button>

        {/* Icon */}
        <div style={{
            width: 50, height: 50, borderRadius: 14, marginBottom: 20,
            background: amberSoft, border: `1px solid ${amberBorder}`,
            display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          <Lock size={22} color={amber} strokeWidth={2}/>
        </div>

        {/* Heading */}
        <h2 style={{
            fontFamily: DISPLAY, fontSize: 19, fontWeight: 800,
            color: inkH, marginBottom: 10, letterSpacing: "-0.025em",
        }}>
          No permission to edit
        </h2>

        {/* Body */}
        <p style={{
            fontFamily: SANS, fontSize: 14, color: inkM,
            lineHeight: 1.65, marginBottom: 8,
        }}>
          You are signed in as <strong style={{ color: inkH }}>{roleLabel}</strong>.
          The <strong style={{ color: inkH }}>{pageName}</strong> page is view-only for this role — no changes, approvals, or data updates can be made from here.
        </p>
        <p style={{
            fontFamily: SANS, fontSize: 13, color: inkMuted,
            lineHeight: 1.6, marginBottom: 28,
        }}>
          All data and status information on this page is fully visible. To make changes, sign in with an authorised role.
        </p>

        {/* Dismiss button */}
        <button onClick={onClose} style={{
            width: "100%", padding: "12px 0", borderRadius: 10, border: "none",
            background: inkH,
            fontFamily: DISPLAY, fontSize: 14, fontWeight: 700, color: "#fff",
            cursor: "pointer",
            transition: `opacity 0.15s`,
        }} className="hover:opacity-80">
          Got it
        </button>
      </div>

      <style>{`
        @keyframes ro-fade-in  { from { opacity: 0 } to { opacity: 1 } }
        @keyframes ro-slide-in { from { opacity: 0; transform: scale(0.95) translateY(8px) } to { opacity: 1; transform: scale(1) translateY(0) } }
      `}</style>
    </div>);
}
