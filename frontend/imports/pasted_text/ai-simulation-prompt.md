# Figma Prompt — MetroMind KMRL: AI Simulation Page

Paste the block below into Figma (Figma Make / Figma AI / First Draft) as one prompt.

---

## PROMPT

Design a web app screen called **"AI Simulation"** for an internal metro-operations dashboard named **MetroMind KMRL**. Match this exact structure, styling, and dynamic behavior:

### Global style
- Font: "Plus Jakarta Sans" (headings, bold/800) + "Inter" (body).
- Background canvas: `#F8FAFC`. Cards: white `#FFFFFF`, 22px corner radius, subtle shadow, 1px border `rgba(15,23,42,0.08)`.
- Primary accent: teal `#009688` (dark teal `#00786B`), secondary accent: emerald `#10B981`.
- Layout is edge-to-edge / flush-left (no centered max-width container) — header, footer, and full-width sections all touch the true left edge of the viewport, with padding only on the right.

### Header (copy exactly from the Dashboard page — do not restyle)
- Sticky top bar, frosted white background, bottom border.
- Left: logo mark (two overlapping chevrons in teal/emerald gradient) + wordmark "MetroMind KMRL".
- Center-left: horizontal nav links: **Dashboard, Fleet & Induction, Schedule, AI Simulation, Alerts, Reports, About**.
  - Add "AI Simulation" to this nav bar if it's missing.
  - The **currently active/selected nav link gets a green underline** (2px, animates in from left to right on click/selection, same as the existing hover/active underline pattern) instead of teal.
  - Clicking "AI Simulation": check sign-in state first.
    - **Signed in** → navigate directly to the AI Simulation page.
    - **Not signed in** → redirect to the Sign In page; after a successful login, continue on to AI Simulation.
- Right: operator auth badge (green status dot + operator name "Priya • Operator") and a "Log Out" button with icon.

### Footer (copy exactly from the Dashboard page — do not restyle)
- Dark navy background `#0B111E`, three-column layout:
  1. Brand block: logo, one-line description, "Restricted Operator Domain" pill with lock icon.
  2. "Line Infrastructure" telemetry list (label + status value rows, dashed separators).
  3. "Relay Telemetry" list (same row style).
- Bottom bar: left = product tag + version pill (e.g. "v2.4.1"); right = footer nav links + copyright line.

### Page hero (below header, above workspace)
- Small eyebrow label: "Interactive Testing".
- H1: "Predictive Dispatch & Simulation Sandbox".
- One paragraph of muted gray lead copy describing the sandbox purpose.

### Main workspace — two-column layout (350px sidebar + flexible content)
**Left sidebar card ("Scenario Presets" + "Environmental Variables"):**
- 2×2 grid of preset buttons: Off-Peak, AM Peak, Monsoon Rush, Corridor Delay. Selected preset is filled teal; others are light gray pills.
- Dropdown: "Track Weather Conditions" — Clear / Wet Rails / Heavy Monsoon.
- Slider: "Passenger Surge Multiplier" 1.0x–4.0x (0.5 steps), value shown live next to the label.
- Toggle switch: "Corridor Incidents — MG Road Spacing Drift" (on/off).
- **All four controls are live inputs** — every change instantly recalculates the whole page (no submit button).

**Right column, top row — 3 metric cards:**
- "Optimized Headway" (large number in seconds, e.g. "420s").
- "Fleet Induction" (e.g. "10 sets").
- "System Capacity" (percentage, e.g. "100%").
- Each card has a thin teal top accent bar.

**Right column, second block — "Live Spacing & Track Alignment Map":**
- Dark schematic panel (near-black `#0b0f19`) with a faint grid texture.
- Two horizontal parallel track lines labeled "Up-Line Track (Eastbound)" and "Down-Line Track (Westbound)".
- 5 stations plotted left to right: Aluva (ALU) → Edapally (EDP) → JLN Stadium (JLN) → MG Road (MGR) → Petta (PTA), each a glowing blue dot with code + full name underneath.
- Small pill-shaped train icons distributed along both tracks, alternating which line they sit on, each labeled T1, T2, T3…, count and spacing driven by the calculated fleet size.
- A status pill top-right: green "Line Clear — Active CBTC Loop" normally; turns **red and pulses**, reading "Drift Warning: MG Road Section", when the incident toggle is on — and the MG Road station dot itself turns red and pulses too.

### Full-width section below the two-column area — "Dynamic Timetable Deviation Chart"
This card is **not** confined to the sidebar's column — it spans the entire page width edge-to-edge, same as the header/footer, so there's no dead space beside it.
- Header row: title left, legend right ("Static Baseline" gray dot, "AI Responsive Schedule" teal dot).
- Below: a left block (flexible width) and a fixed ~340px-wide right panel, with roughly a 32px gap, filling the full card width — no leftover empty margin on either side.
- **Left block** — 4 stacked time-slot rows (AM Peak, Mid-Day, PM Peak, Late Evening), each showing two horizontal progress bars: a gray "static baseline" bar and a teal "AI responsive" bar underneath it, each with its value in seconds to the right. Bar widths are all scaled relative to the same maximum value across every row, so they're visually comparable.
- **Right panel** — "AI Optimisation Metrics" in a 2×2 stat grid: Avg Wait Reduction, Regen Brake Saving, CBTC Loop Sync, Load Balancing — plus a one-line caption underneath.

### Required dynamic behavior (all driven live by the 4 sidebar inputs)
1. Changing weather / surge / incident instantly recalculates and updates, with no page reload:
   - Optimized Headway, Fleet Induction count, System Capacity %.
   - Number of train icons rendered on the track map (matches recalculated fleet size).
   - Track status pill color/text and MG Road station dot color (alert vs. clear).
   - All 8 progress bars (4 slots × static + AI) and their second labels.
   - All 4 AI Optimisation Metrics values (wait reduction, regen saving, CBTC sync, load balance label).
2. The 4 scenario preset buttons are shortcuts that set all three inputs at once and immediately trigger the same recalculation; manually adjusting any single control deselects the preset buttons.
3. Weather makes headways worse (Clear → Wet Rails +15% → Heavy Monsoon +40%); higher passenger surge shortens headways (more frequent trains); the incident toggle adds a fixed safety delay; all values are clamped to a safe minimum floor.

---

## Notes for whoever builds this in Figma
- If Figma Make/AI is being used to generate a working prototype (not just static frames), keep the calculation logic above as plain-language rules — it converts cleanly into component state + a single "recompute" function bound to all four inputs.
- Keep one design source of truth for the header and footer (as components/instances) so the Dashboard and AI Simulation pages never visually drift apart.