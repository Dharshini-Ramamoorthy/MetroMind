const teal = "#009688";
const tealDk = "#00786B";
const emerald = "#10B981";
const DISPLAY = "'Plus Jakarta Sans', sans-serif";
const SANS = "Inter, sans-serif";
const MONO = "'JetBrains Mono', monospace";
export default function SharedFooter({ activePage, onNavigate }) {
    const links = [
        { label: "About", key: "about" },
        { label: "Contact Systems", key: "contact" },
    ];
    return (<footer style={{ background: "#0B111E", borderTop: "1px solid #1E293B" }}>
      <div className="max-w-7xl mx-auto px-6 py-5 flex flex-wrap items-center justify-between gap-4">

        {/* Left */}
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center shrink-0" style={{ width: 44, height: 44, borderRadius: 13, background: `linear-gradient(135deg, ${teal}, ${tealDk})` }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
              <path d="M4 5.5L9 9L4 12.5" stroke={emerald} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M9 5.5L14 9L9 12.5" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          <div>
            <p style={{ fontFamily: DISPLAY, fontWeight: 700, fontSize: 14, color: "#fff", lineHeight: 1.3 }}>
              MetroMind <span style={{ color: "#4FD1C5" }}>KMRL</span>
            </p>
            <p style={{ fontFamily: SANS, fontSize: 12, color: "#64748B", marginTop: 2 }}>
              Fleet induction &amp; scheduling console
            </p>
          </div>
        </div>

        {/* Center */}
        <div className="flex items-center gap-6">
          {links.map(link => {
            const isActive = activePage === link.key;
            return (<button key={link.key} data-ro-allow="true" onClick={() => onNavigate(link.key)} className="text-sm font-medium transition-colors hover:text-[#4FD1C5]" style={{ fontFamily: SANS, color: isActive ? "#4FD1C5" : "#94A3B8", fontWeight: isActive ? 600 : 500 }}>
                {link.label}
              </button>);
        })}
        </div>

        {/* Right */}
        <div className="flex items-center">
          <span style={{ background: "#131E31", color: teal, fontFamily: MONO, fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 100 }}>
            v2.4.1
          </span>
        </div>
      </div>
    </footer>);
}
