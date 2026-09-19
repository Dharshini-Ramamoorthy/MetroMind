import { AlertTriangle } from "lucide-react";
export default function SharedToast({ visible }) {
    return (<div role="alert" aria-live="assertive" aria-atomic="true" style={{
            // Fixed bottom-center, 24px from edge
            position: "fixed",
            bottom: 24,
            left: "50%",
            // Fade + slide: 150ms ease-out in, 150ms ease-in out
            transform: `translateX(-50%) translateY(${visible ? 0 : 8}px)`,
            opacity: visible ? 1 : 0,
            pointerEvents: "none",
            transition: visible
                ? "opacity 0.15s ease-out, transform 0.15s ease-out"
                : "opacity 0.15s ease-in,  transform 0.15s ease-in",
            // Highest z-order — above header, cards, modals
            zIndex: 1200,
            // Layout
            display: "flex",
            alignItems: "flex-start",
            gap: 10,
            // Visual spec: dark rounded rect, 12px radius, soft shadow
            background: "#0F172A",
            borderRadius: 12,
            padding: "12px 16px",
            boxShadow: "0 8px 32px rgba(0,0,0,0.28), 0 2px 8px rgba(0,0,0,0.14)",
            // Hug content with max-width so message wraps instead of truncating
            width: "auto",
            maxWidth: 360,
        }}>
      {/* Amber warning icon */}
      <AlertTriangle size={15} style={{ color: "#F59E0B", flexShrink: 0, marginTop: 1 }} aria-hidden="true"/>
      {/* Message text — white/light-gray, Inter 13.5px medium */}
      <span style={{
            fontFamily: "Inter, sans-serif",
            fontSize: 13.5,
            fontWeight: 500,
            color: "#F1F5F9",
            lineHeight: 1.5,
            whiteSpace: "normal",
        }}>
        No edit access — you don&apos;t have permission to modify this page.
      </span>
    </div>);
}
