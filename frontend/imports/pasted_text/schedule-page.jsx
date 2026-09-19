Rebuild the "Schedule" page from scratch so it exactly matches this specification, and wire its navigation according to the shared sign-in rule described at the end. Do not simplify, restyle, reinterpret, or omit any part of it.

=== DESIGN TOKENS (must match exactly) ===
Colors: canvas background #F8FAFC, card white #FFFFFF, heading text #0F172A, body text #334155, muted text #64748B, teal #009688, teal-dark #00786B, teal-light #E0F2F1, emerald #10B981, amber #F59E0B, rose #EF4444, border rgba(15,23,42,0.08).
Fonts: "Plus Jakarta Sans" (700/800 weight) for all headings, "Inter" for body text.
Border radius scale: 6px (sm), 12px (md), 16px (lg), 22px (xl).
Shadows: soft card shadow (shadow-sm), medium hover shadow (shadow-md), large modal shadow (shadow-xl).

=== HEADER ===
- Sticky white header (not transparent/blurred like the Dashboard) with a 2px bottom gradient line (teal → emerald) as a visual accent strip.
- Logo: a 38x38px rounded-square icon with a teal-to-emerald gradient background, containing a white chevron/arrow SVG icon inside, next to the text "MetroMind KMRL" (bold, Plus Jakarta Sans).
- Nav links, in order: Dashboard, Fleet & Induction, Schedule (this one active/underlined), Alerts, Reports.
- Right side: shared auth-state area — "Sign In" button when guest, or "Priya · Operator" badge (green status dot) + "Log Out" button when signed in.

=== PAGE HEADER SECTION ===
- Title: "Gantt Schedule Console"
- Subtext: "Monitor system baselines against active, live tracking parameters across core route corridors."
- Two pill-style toggle control groups side by side, each with two buttons where the first is visually "active" (white background, subtle shadow) and the second is inactive (transparent):
  1. "Live Timeline" (active) / "Baseline Comparison"
  2. "All Corridors" (active) / "Line Blue"
- A "Propose Adjustment" button on the right side of this header row: outlined style (white background, border, not filled), with a small plus-icon SVG to the left of the text.

=== GANTT GRID CARD (the core component) ===
Build this as a card with rounded corners (22px) and a soft shadow, containing a horizontally-scrollable grid:
- Grid structure: 2 columns — a fixed 240px left "labels" column and a flexible right "timeline" column. The whole grid has a minimum width of 1040px so it scrolls horizontally on smaller screens.

LEFT LABELS COLUMN:
- Top header cell: "Active Train Sets" (uppercase, small, muted gray, on a light canvas background)
- Then exactly 4 rows, each 90px tall, bottom-bordered, containing a train name + a small colored status pill next to it, plus a smaller gray subtext line below:
  1. "KMRL Set Alpha" — pill "Active" (green background #E6F4EA, green text #137333) — subtext "Aluva — Petta Corridor"
  2. "KMRL Set Bravo" — pill "Delayed" (red background #FCE8E6, red text #C5221F) — subtext "Express Shuttle Loop"
  3. "KMRL Set Charlie" — pill "Active" (green) — subtext "Aluva — Petta Corridor"
  4. "KMRL Set Delta" — pill "Standby" (amber background #FEF7E0, amber text #B06000) — subtext "Muttom Yard Reserve"

RIGHT TIMELINE COLUMN:
- Top header row: an 8-column grid representing hourly ticks: 06:00 am, 07:00 am, 08:00 am, 09:00 am, 10:00 am, 11:00 am, 12:00 pm, 01:00 pm — each cell shows the time in bold with "am"/"pm" as a smaller label beneath.
- Below that, one row per train (matching the 4 rows on the left, same 90px height, aligned), each containing:
  - A faint vertical grid overlay of 8 columns behind everything (very light gray dividers) purely for visual alignment
  - One or more colored horizontal "blocks" positioned absolutely using inline left% and width% values (do NOT use fixed pixel positions — use percentages so they scale with the scrollable width), each block being a native HTML <details>/<summary> element (collapsible), styled with:
    - A colored left border accent (4px) and a soft tinted background matching one of 4 variants: teal-variant (background #E0F2F1, text teal-dark, border teal), emerald-variant (background #E6F4EA, text #137333, border emerald), amber-variant (background #FEF7E0, text #B06000, border amber), rose-variant (background #FCE8E6, text #C5221F, border rose)
    - The <summary> shows a bold truncated label (e.g. "Morning Peak Run") and a smaller time-range line below it (e.g. "06:20 AM - 09:15 AM")
    - Clicking/expanding the block reveals a dropdown panel positioned directly below it (absolute position, white background, dark border, shadow, ~280px wide) titled "Station Stop Timetable", listing rows of station name (left, bold) + time (right, muted gray), separated by dashed lines

  Populate the exact rows and blocks as follows:
  ROW 1 (Set Alpha):
    - Block 1: teal-variant, positioned left:4% width:38%, label "Morning Peak Run", time "06:20 AM - 09:15 AM". Dropdown stations: Aluva (Origin) Dep 06:20 AM; Kalamassery Arr 06:45 AM; Edapally Jn Arr 07:10 AM; Kaloor Terminal Arr 08:35 AM; Petta (Terminus) Arr 09:15 AM.
    - Block 2: emerald-variant, left:48% width:25%, label "Midday Off-Peak Loop", time "10:00 AM - 12:00 PM". Dropdown stations: Petta (Origin) Dep 10:00 AM; Ernakulam South Arr 10:40 AM; Aluva (Terminus) Arr 12:00 PM.

  ROW 2 (Set Bravo):
    - A diagonal red hatch-pattern "conflict hotspot" zone rendered behind the block (repeating 45-degree stripes, translucent red), positioned left:10% width:40%, purely decorative/non-interactive.
    - Block: amber-variant, left:10% width:40%, label "Delayed Passenger Run", time "06:45 AM - 10:00 AM". Dropdown stations: Muttom Yard (Out) Dep 06:45 AM; Edapally (Stalled) Hold 07:40 AM (this row shown in red/rose text to flag the stall); Ernakulam South "Delayed Hold".

  ROW 3 (Set Charlie):
    - Block 1: teal-variant, left:2% width:35%, label "Morning Peak Run", time "06:10 AM - 09:00 AM". Dropdown stations: Aluva Terminal Dep 06:10 AM; Cusat Junction Arr 07:05 AM; Petta Station Arr 09:00 AM.
    - Block 2: emerald-variant, left:42% width:35%, label "Midday Inter-Terminal Run", time "09:30 AM - 12:15 PM". This dropdown should align to the right (open leftward) instead of the default left-aligned direction. Dropdown stations: Petta Terminal Dep 09:30 AM; Palarivattom Arr 10:55 AM; Aluva Terminal Arr 12:15 PM.

  ROW 4 (Set Delta):
    - Block: rose-variant, left:25% width:20%, label "Emergency Induction Run", time "08:00 AM - 09:36 AM". Dropdown stations: Muttom Shed Dep 08:00 AM; JLN Stadium Clearance Arr 08:50 AM; plus a highlighted note line below in red-tinted background: "Mainline System Clearance Inspection".

=== MODAL: "Propose Schedule Adjustment" ===
- Triggered by the "Propose Adjustment" button; dark semi-transparent blurred backdrop overlay; modal card centered, rounded corners (16px), max-width ~480px.
- Modal header: "Propose Schedule Adjustment" title + an "×" close button top-right.
- Form fields, in order:
  1. "Target Fleet Asset" — dropdown/select with options: KMRL Set Alpha (Aluva — Petta Corridor), KMRL Set Bravo (Express Shuttle Loop), KMRL Set Charlie (Aluva — Petta Corridor), KMRL Set Delta (Muttom Yard Reserve)
  2. Two side-by-side time inputs: "Proposed Start Time" (default 06:20) and "Proposed End Time" (default 09:15)
  3. "Justification / Headway Dispatch Notes" — a 3-row textarea with placeholder text about interlocking signaling parameters or weather factors
- Modal footer: "Dismiss" button (outlined/cancel style) and "Submit Proposal" button (filled teal, primary)
- Clicking the "×", "Dismiss", or clicking outside the modal closes it without saving. Submitting the form closes the modal and shows a success confirmation message: "Adjustment proposed to KMRL baseline approver panel successfully."

=== METRIC CARDS (below the Gantt card) ===
A 3-column grid of cards, each with a header row (label + small icon on the right where applicable):
1. "Schedule Conflict Warning" — icon: red alert triangle — big value "1 Detected" — subtext "Set Bravo overlapping headway tracking guidelines near Edapally junction corridor layout."
2. "Fleet Utilization Efficiency" — icon: green checkmark — big value "84.2%" — subtext "Optimal active alignment parameters tracked relative to baseline capacity projections."
3. "Live Dispatch Notes" — no icon — big value "2 Pinned" — subtext "System holds active operational safety markers near yard bay tracks due to wet weather patterns."

=== FOOTER ===
- Dark slate background (#0F172A) with a 2px top gradient line (teal → emerald).
- Left: logo (white version) + "Fleet induction & scheduling console" tagline in muted gray.
- Center: "About" and "Contact Systems" links.
- Right: "© 2026 KMRL Internal Systems" text + a small pill badge showing "v2.4.1" (teal-tinted background, teal text).

=== RESPONSIVE BEHAVIOR ===
- Below 980px: metric cards stack to 1 column.
- Below 900px: nav links hide behind a hamburger toggle; header wraps if needed.
- The Gantt grid card itself always scrolls horizontally rather than compressing, since it has a fixed minimum width.

=== NAVIGATION / ROUTING RULE (applies project-wide, not just this page) ===
Every single element anywhere in the project whose purpose is to take the user to the Schedule page — this includes but is not limited to: the "Schedule" nav link on the Dashboard, Sign In, Fleet & Induction, and Schedule pages themselves; any button, card link, or call-to-action anywhere that is meant to open/view the Schedule console — must follow this exact rule based on the shared sign-in state:

- IF the user is signed in (header shows "Priya · Operator"): clicking it navigates directly to the Schedule page built above.
- IF the user is a guest (header shows "Sign In"): clicking it navigates to the Sign In page instead — never to the Schedule page directly.

After a successful sign-in, the user returns to the Dashboard with the signed-in header. From there, clicking any Schedule-bound trigger now goes straight to the Schedule page per the rule above. Logging out reverts the shared state to guest, after which clicking any Schedule-bound trigger goes back to routing through Sign In.

Do not change the Dashboard, Sign In, or Fleet & Induction pages' visuals while doing this — only build/replace the Schedule page as specified, and apply this routing rule to every existing Schedule-bound trigger across the whole project.