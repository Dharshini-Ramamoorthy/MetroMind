TASK: Rebuild the "Reports" page as an exact, literal reproduction of the specification below — not an interpretation, not a redesign. Follow every value, label, color, and structure exactly as written. Then apply the navigation rule at the end.

=== COLOR TOKENS (use these exact hex/rgba values, this page's palette is intentionally different from other pages) ===
--canvas: #F4F7F6
--card: #FFFFFF
--ink-heading: #0F172A
--ink-body: #334155
--ink-muted: #627A70
--teal: #009688
--teal-dark: #00786B
--emerald: #10B981
--crimson: #DC2626
--amber: #F59E0B
--line: rgba(0, 150, 136, 0.09)
Border radius: 12px / 16px / 22px
Fonts: "Plus Jakarta Sans" for all headings and metric numbers, "Inter" for everything else.

=== HEADER ===
- Sticky, backdrop-blurred, background rgba(244,247,246,0.85), bottom border using the --line color above.
- Logo: gradient chevron SVG icon (teal → emerald, no background box) + text "MetroMind KMRL".
- Nav order: Dashboard, Fleet & Induction, Schedule, Alerts, Reports (active — bold + underlined).
- Right side: shared auth-state control already used across the project — "Sign In" button when guest; white pill badge with green dot reading "Priya · Operator" + a bordered "Log Out" button (with logout icon) when signed in.

=== PAGE TITLE BLOCK ===
H1: "Network Performance Reports"
Subtext: "Live metrics analysis, historical headway variations, and active train fleet timelines."

=== FILTER BAR (one card row) ===
Left: two dropdowns —
1. "All Lines (Aluva - Tripunithura)" / "Phase 1 Mainline" / "Phase 1A Extension"
2. "Reporting Period: Live Telemetry" / "Past 7 Days" / "Past 30 Days"
Right: dark filled button "Export CSV" with a download icon.

=== METRICS ROW — exactly 4 cards, responsive auto-fit grid, min-width 240px ===
1. Label "Avg Network Headway" — value "4 min 12s" (id="liveHeadwayVal", updates live) — green up-arrow, text "Optimal Stability"
2. Label "On-Time Performance" — value "98.2%" — green up-arrow, text "+0.4% vs yesterday"
3. Label "Total Daily Ridership" — value "94,310" — red down-arrow, text "-1.2% off peak load"
4. Label "Active Incidents" — value "01" — amber text, no arrow, "Minor headway correction"

=== CONTENT GRID — 2 panels, side-by-side above 1150px width, stacked below ===

PANEL 1: "Headway Fluctuations (Real-Time Variation Loop)"
- Header right side: pulsing teal dot with animated ring (ping effect) + uppercase teal text "Live Feed"
- Body: SVG line chart, viewBox="0 0 500 220"
  - 4 faint horizontal gridlines
  - Gradient-filled area under the line (teal, ~22% opacity fading to 0%)
  - Smooth teal stroke line through 7 data points
  - A circle "current" marker at the last/rightmost point (teal fill, white stroke, radius 6)
  - X-axis labels below: "-25s", "-20s", "-15s", "-10s", "-5s", "Current Metric"
  - Script: every 2500ms, shift the data array, push one new random Y value (range 40–180), redraw the path/area/marker, and update the headway metric text to "4 min {random 0–22}s"

PANEL 2: "Fleet Monitoring Matrix"
Table, columns: Train ID | Current Block / Position | Target Speed | Schedule Deviation | Status
Rows exactly:
1. KMRL-TR04 | Edapally (Platform 2) | 42 km/h | +4s (Nominal) | badge "In-Sync" (emerald)
2. KMRL-TR11 | Kaloor Transit Link | 38 km/h | -12s (Catching up) | badge "In-Sync" (emerald)
3. KMRL-TR18 | Ernakulam South Loop | 15 km/h | +1m 45s (Approaching) | badge "Regulating" (amber)
4. KMRL-TR25 | MG Road Terminal Intersect | 0 km/h | -3m 12s (Dwell Hold) | badge "Delayed" (crimson)
Table header background: #EAEFEF. Badges are pill-shaped with a small dot + tinted background matching their color.

=== SECONDARY GRID — 2 panels, ~1.8fr / 1.2fr split above 900px, stacked below ===

PANEL 3: "Live System Status Logs" — vertical alert list, each item with a colored left border + tinted background:
1. Amber/warn — "KMRL-TR25 Hold Pattern Enforced" — "MG Road platform gate alignment recycling sequence in progress. Safety buffers holding line approach vectors." — "1 min ago"
2. Teal/info — "Wayside Interlocking Synced" — "Aluva Terminal sector automated blocks validated successfully with trackside loops." — "4 mins ago"
3. Teal/info — "Traction Power Grid Balance" — "Substation 03 routing configuration shifted automatically to manage high acceleration loads efficiently." — "12 mins ago"

PANEL 4: "Fleet Allocation Density" — 3 stacked progress groups (label + % on top, rounded track below):
1. "Phase 1 Mainline Core" — 72% Capacity — teal fill
2. "Phase 1A Extension Links" — 45% Capacity — emerald fill
3. "Maintenance Yard Reserves" — 18% Available — muted gray fill

=== FOOTER ===
Background #0E1A17, text color #819B92.
Left: "Fleet induction & scheduling console" + small teal-tinted pill "v2.4.1".
Right: "About", "Contact Systems" links + "© 2026 KMRL Internal Systems".

=== RESPONSIVE RULES ===
- Below 1150px: content-grid becomes 1 column.
- Below 900px: secondary-grid becomes 1 column; nav links collapse to hamburger.
- Below 720px: hide the "· Operator" label text in the auth badge; footer row stacks vertically.

=== NAVIGATION RULE (apply project-wide) ===
Every trigger anywhere in the project meant to open Reports — the "Reports" nav link on every page, and any other button/card link intended to reach Reports — must check the existing shared sign-in state:
- Signed in ("Priya · Operator" showing) → go directly to this Reports page.
- Guest ("Sign In" showing) → go to the Sign In page instead, never directly to Reports.
After successful sign-in, return to Dashboard with the signed-in header; from there Reports-bound triggers go straight to Reports. Logging out reverts to guest and restores the Sign-In detour.

CONSTRAINT: Do not modify the Dashboard, Sign In, Schedule, or Fleet & Induction pages' visuals. Only build/replace the Reports page and apply the navigation rule above to existing Reports-bound triggers project-wide.