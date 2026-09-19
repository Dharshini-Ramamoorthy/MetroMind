TASK 1: Rebuild the "Alerts" page as an exact, literal reproduction of the specification below — not an interpretation, not a redesign. Follow every value, label, color, and behavior exactly as written.

TASK 2: Update the footer on the "Reports" page (only the footer, nothing else) to match the same footer spec described below.

=== DESIGN TOKENS (must match exactly, for Alerts page) ===
--canvas: #F8FAFC, --card: #FFFFFF, --ink-heading: #0F172A, --ink-body: #334155, --ink-muted: #64748B, --teal: #009688, --teal-dark: #00786B, --emerald: #10B981, --line: rgba(15,23,42,0.08)
Severity colors: sev3 (critical) = #DC2626 text on #FEF2F2 background, border rgba(220,38,38,0.22); sev2 (warning) = #D97706 text on #FFFBEB background, border rgba(217,119,6,0.22); sev1 (info) = #64748B text on #F1F5F9 background, border rgba(100,116,139,0.18).
Fonts: "Plus Jakarta Sans" for headings, "Inter" for body text, "JetBrains Mono" for all IDs, timestamps, hashes, and technical field values.
Border radius scale: 12px (md), 16px (lg), 22px (xl). Shadows: soft (shadow-sm), large (shadow-lg).

=== HEADER (shared style with other pages) ===
- Sticky, backdrop-blurred white header (rgba(255,255,255,0.88)), bottom border using --line.
- Logo: teal/emerald gradient chevron SVG icon + "MetroMind KMRL" text, Plus Jakarta Sans bold.
- Nav order: Dashboard, Fleet & Induction, Schedule, Alerts (active/underlined), Reports.
- Right side: shared auth-state control already used project-wide — "Sign In" button when guest; white pill badge with green status dot reading "Priya · Operator" + bordered "Log Out" button (with icon) when signed in.

=== PAGE STRIP ===
- Breadcrumb row: "Console / Incident & Alert Management" — first part muted uppercase, second part in teal-dark, separated by a faded slash.
- Title: "Incident & Alert Console"
- Subtext: "Live triage feed for network deviations, with root-cause diagnostics and gated resolution controls for signed-in operators."
- Right side: a pill-shaped posture badge with a colored dot — default state is "degraded": amber background #FFFBEB, amber text #B45309, border rgba(180,83,9,0.2), dot pulsing (animated opacity 1↔0.35 on a ~1.8s loop), label text "Degraded Posture". Also build the "nominal" variant (green background #ECFDF5, green text #047857, non-pulsing dot) and "emergency" variant (red background #FEF2F2, red text #B91C1C, dot pulsing faster ~1.2s) as reusable states, even though "degraded" is what's shown by default.

=== KPI ROW — exactly 4 cards in a 4-column grid ===
1. "Active Deviations" — icon: neutral gray alert-triangle in a #F1F5F9 tinted square — value "6" — subtext "Unresolved, across 3 severity tiers"
2. "Critical Alarms" — icon + value both in red (critical style, red-tinted icon background) — value "1" — subtext "Immediate operational disruption risk"
3. "System Integrity" — icon: green shield in a light green tinted square — value "87%" (unit suffix styled smaller/muted) — below the value, a thin rounded progress bar track (#F1F5F9 background) filled 87% with a gradient from emerald to teal
4. "Network Posture" — icon: amber-tinted square — value "Degraded" in amber/brown text (#B45309) — subtext "1 critical, 3 warning, 2 info deviation(s) open"

=== WORKSPACE — 2 panels side by side, grid-template-columns 1fr 1.2fr, each panel a card with fixed height 660px on desktop (auto height + internal scroll below 1100px) ===

PANEL 1 — "Live Incident Vector Matrix" (small eyebrow label above it: "Triage Feed")
- Panel header right side: a small pill chip with a pulsing teal dot (6px, animated) + bold uppercase-ish text "Live"
- Body: a vertically scrollable list of alert rows. Each row contains, left to right: a monospace timestamp (fixed width, top-aligned), a main content block (severity badge + monospace incident ID on one line, bold title below, muted subtitle below that), and a chevron arrow icon on the far right that's invisible by default and fades in on hover or when the row is selected
- Clicking a row: highlights it with a light teal-tinted background (#F0FDFA) and a 3px solid teal left border accent, and loads that alert's full detail into Panel 2
- Populate exactly these 6 alert rows, in this exact order, all unresolved by default:
  1. Time 14:32 — Sev3 badge "Severity Level-3 · Critical" — ID INC-2291 — Title "Traction power breaker trip — Sub-station 04" — Subtitle "Aluva depot feeder yard"
  2. Time 14:11 — Sev2 badge "Severity Level-2 · Warning" — ID INC-2290 — Title "Platform screen door wireless link variance" — Subtitle "Edapally station, Platform 2"
  3. Time 13:58 — Sev2 — ID INC-2289 — Title "Set Bravo headway overlap — Kaloor corridor" — Subtitle "Kaloor junction approach"
  4. Time 13:40 — Sev1 badge "Severity Level-1 · Info" — ID INC-2288 — Title "Telemetry lag on OCC dashboard refresh" — Subtitle "Operations Control Centre"
  5. Time 13:22 — Sev1 — ID INC-2287 — Title "Software version skew — onboard diagnostics" — Subtitle "Set Delta, Car 2"
  6. Time 12:57 — Sev2 — ID INC-2286 — Title "Wet-rail adhesion advisory — yard bay tracks" — Subtitle "Muttom yard, Bay 5–7"
- If every alert has been resolved/cleared during the session, replace the list with a centered empty-state message: "No active deviations. All incidents have been cleared."

PANEL 2 — "Diagnostic & Resolution" (small eyebrow label above it: "Root-Cause Analyzer")
- Default empty state (no alert selected yet): centered icon (a simple corner-bracket/scan icon) + text "Select an alert from the triage feed to inspect fault detail, predictive impact, and resolution controls."
- When an alert is selected, replace the empty state with:
  1. A title row: severity badge above a bold asset name (Plus Jakarta Sans), with the incident ID in monospace aligned to the top-right
  2. A 2-column grid of small bordered "field" boxes, each with a small uppercase muted label and a bold monospace value below it — populate using the exact field data per alert (see data table below)
  3. A tinted callout box (background/border color matches the alert's severity) titled "DRL AI Predictive Consequence" (with a small clock/history icon), containing the alert's specific predictive-impact paragraph (see data table below)
  4. A "Telemetry Snapshot" section: a bordered box containing a small teal SVG sparkline (a smooth polyline built from an 8-point numeric array unique to each alert, scaled to fit within the box), with a small meta row underneath reading "T‑40s" (left), "Metric spike at detection" (center), "T+0s" (right)
  5. An "Incident Resolution Controls" section (small icon + uppercase label), containing 3 full-width action buttons stacked vertically:
     - "Acknowledge and Clear Alert" (emerald/green tinted style) with sub-label "Removes this incident from active memory"
     - "Isolate Sub-System Node" (amber tinted style) with sub-label "Cuts the affected component from the live network"
     - "Deploy Field Maintenance Dispatch" (blue tinted style) with sub-label "Logs the fault and opens a maintenance work order"
     Each button has a small icon on the left in a rounded tinted square, and — when disabled — a small lock icon on the right and a grayscale/50%-opacity visual treatment with a "not-allowed" cursor.
  6. A gate note below the buttons:
     - If signed in: green-tinted note with checkmark icon: "Signed in as Priya (OP-1042) — actions will be recorded to the audit ledger."
     - If guest: neutral note with a small lock icon: "Sign in to unlock resolution controls for this alert."
  7. Button gating: all 3 action buttons are locked/disabled (grayscale, no hover effect, lock icon shown) when the user is a guest, and fully enabled (full color, hoverable with a subtle lift + shadow) when signed in.

EXACT ALERT DATA TABLE (use these values verbatim for fields/predictive text):
- INC-2291: Asset "Sub-station 04 Breaker (Feeder Bay 2)" — Fields: Location: Aluva Depot, Detected: 14:32:08, Signal Loss: 412 ms, Affected Sets: Set Alpha, Set Bravo — Predictive: "AI pipeline predicts a 14-minute cumulative network delay within the next two headway cycles if the Aluva bottleneck is not isolated within 6 minutes." — Sparkline data: 22, 24, 23, 41, 78, 95, 88, 91
- INC-2290: Asset "Edapally Platform Screen Door Wireless Link" — Fields: Location: Edapally Station, Detected: 14:11:52, Packet Loss: 6.3%, Affected Sets: None (station-side) — Predictive: "If unresolved, dwell time at Edapally may extend by 20–35 seconds per stop during peak headway, with low risk of cascading delay." — Sparkline data: 10, 12, 14, 19, 26, 24, 29, 31
- INC-2289: Asset "CBTC Headway Controller — Kaloor Segment" — Fields: Location: Kaloor Corridor, Detected: 13:58:04, Headway Delta: -18 sec, Affected Sets: Set Bravo, Set Charlie — Predictive: "Continued overlap risks an automatic speed restriction on the Kaloor approach, adding an estimated 3–5 minutes to the affected run." — Sparkline data: 30, 28, 33, 40, 44, 42, 47, 50
- INC-2288: Asset "OCC Telemetry Aggregator — Node 3" — Fields: Location: OCC Core, Detected: 13:40:15, Refresh Lag: 2.1 sec, Affected Sets: None — Predictive: "No operational impact expected. Dashboard refresh lag is within tolerance; monitoring for recurrence." — Sparkline data: 4, 5, 4, 6, 7, 5, 6, 6
- INC-2287: Asset "Onboard Diagnostics Unit — Set Delta / Car 2" — Fields: Location: Set Delta (in service), Detected: 13:22:47, Version Skew: 1 minor rev, Affected Sets: Set Delta — Predictive: "Minor version skew logged for audit only. No functional degradation expected before next scheduled induction." — Sparkline data: 3, 3, 4, 3, 4, 4, 3, 4
- INC-2286: Asset "Yard Bay Track Sensors 5–7" — Fields: Location: Muttom Yard, Detected: 12:57:31, Surface Moisture: High, Affected Sets: Yard shunting only — Predictive: "Elevated adhesion risk during shunting moves. Recommend reduced yard speed until surface moisture drops below advisory threshold." — Sparkline data: 15, 20, 34, 38, 36, 40, 37, 39

BUTTON CLICK BEHAVIOR (only possible when signed in):
- "Acknowledge and Clear Alert": removes the alert from the feed permanently for the session, prepends a new row to the ledger table below (using that alert's ID, current timestamp, its original severity, the signed-in operator's name and ID, and a freshly randomly-generated hex hash string in the format "xxxxxx…xxxxxx"), deselects the alert (Panel 2 returns to empty state), and shows a bottom-center toast: "{Incident ID} cleared and logged to the audit ledger."
- "Isolate Sub-System Node": no state change, just shows a toast: "{Asset name} isolated from the live network."
- "Deploy Field Maintenance Dispatch": no state change, just shows a toast: "Maintenance work order generated for {Incident ID}."

=== LEDGER SECTION (below the workspace, full width) ===
- Eyebrow: "Compliance Record"
- Heading: "Historical resolution & incident archival ledger"
- Right side of this header row: an "Export Ledger" button (outlined white, download icon)
- A card containing a table with columns: Incident ID, Resolution Timestamp, Original Severity, Resolving Operator, Validation Hash
- Table header row has a light canvas-tinted background, uppercase small bold labels
- Populate exactly these 4 starting rows, and newly-cleared alerts get added above these at the top during the session:
  1. INC-2251 — 2026-07-05 22:14 — Sev3 badge — Arun K. (OP-0987) — hash 9f3a2c…e701b4
  2. INC-2247 — 2026-07-05 18:02 — Sev2 badge — Priya (OP-1042) — hash 44d1b7…aa9f02
  3. INC-2239 — 2026-07-05 09:41 — Sev1 badge — Sana R. (OP-1108) — hash c02e91…5b31de
  4. INC-2231 — 2026-07-04 20:37 — Sev2 badge — Priya (OP-1042) — hash 7ba045…12c9f0
- Operator column: small circular gradient avatar (teal → emerald) showing the operator's initials, next to their name, with their operator ID in smaller muted text alongside
- Hash column: a small emerald checkmark icon next to the monospace truncated hash string

=== TOAST NOTIFICATION ===
- Fixed position, bottom-center of the viewport, dark background (--ink-heading), white text, rounded pill shape, shadow-lg
- Contains a small emerald checkmark icon + the message text
- Appears with a slide-up + fade-in transition, stays visible for about 2.6 seconds, then fades back out

=== FOOTER — use this EXACT design on BOTH the Alerts page AND the Reports page (this is the corrected reference footer, apply it identically to both) ===
- Background: dark navy #0B111E, with a 1px top border in #1E293B
- Layout: single row, space-between, vertically centered, wraps on narrow screens
- LEFT side: a rounded square icon (~44px, border-radius ~13px) with a teal gradient background (#009688 to #00786B), containing a white double-chevron/checkmark icon centered inside it. Next to the icon, two stacked lines of text:
  - Top line: "MetroMind" in white bold, followed by "KMRL" in a teal-accent color (#4FD1C5), same bold weight, Plus Jakarta Sans font
  - Bottom line (smaller, muted gray #64748B): "Fleet induction & scheduling console"
- CENTER: two text links, muted gray (#94A3B8), medium weight — "About" and "Contact Systems" — hover color teal-accent (#4FD1C5)
- RIGHT side: "© 2026 KMRL Internal Systems" in muted gray text, followed by a small pill badge with dark background (#131E31), teal text (#009688), bold, showing "v2.4.1"

=== RESPONSIVE BEHAVIOR (Alerts page) ===
- Below 1100px: KPI grid becomes 2 columns; workspace panels stack to 1 column, switching from fixed 660px height to auto height with internal max-height scroll (feed list max-height ~420px, diagnostic content max-height ~640px)
- Below 980px: footer row stacks vertically; diagnostic field grid becomes 1 column
- Below 900px: nav links collapse behind a hamburger toggle
- Below 720px: KPI grid becomes 1 column; page title row stacks vertically instead of side-by-side with the posture badge; footer meta info stacks vertically; ledger table font size shrinks slightly; the "· Operator" label text in the auth badge hides

=== NAVIGATION RULE (apply project-wide, not just this page) ===
Every trigger anywhere in the project whose purpose is to open the Alerts page — the "Alerts" nav link on Dashboard, Sign In, Schedule, Fleet & Induction, Reports, and Alerts itself, plus any other button or card link anywhere meant to reach Alerts — must check the shared sign-in state already implemented across the project:
- IF signed in (header shows "Priya · Operator"): clicking it navigates directly to this Alerts page.
- IF guest (header shows "Sign In"): clicking it navigates to the Sign In page instead — never directly to Alerts.
After a successful sign-in, the user returns to the Dashboard with the signed-in header; from there, Alerts-bound triggers go straight to Alerts. Logging out reverts the shared state to guest and restores the Sign-In detour for all Alerts-bound triggers.

Additionally, specific to this page: the 3 resolution action buttons (Clear/Isolate/Dispatch) inside the Diagnostic panel must independently reflect this same shared sign-in state every time an alert is selected or the sign-in state changes — locked and grayscale when guest, fully enabled when signed in — exactly as described above.

CONSTRAINT: Do not modify the visuals of the Dashboard, Sign In, Schedule, or Fleet & Induction pages while doing this. Only build/replace the Alerts page as specified above, update the Reports page's footer only (leave the rest of Reports untouched), and apply the navigation rule to every existing Alerts-bound trigger across the whole project.