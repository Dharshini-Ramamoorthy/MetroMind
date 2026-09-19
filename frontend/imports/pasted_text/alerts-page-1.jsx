I'm attaching a reference image that shows the exact static layout of the "Alerts" page — use it as the visual ground truth for colors, spacing, typography, and component structure. Build/rebuild the Alerts page to match this image exactly.

The image only shows one fixed state (one alert selected, all 6 unresolved). Now add the following DYNAMIC behavior on top of that static design — none of this is visible in the image, so implement it purely from this description:

1. FEED SELECTION
- All 6 alert rows in the left panel are clickable.
- Clicking a row highlights it (light teal background + teal left border, matching the "selected" row style already visible in the image for INC-2291) and loads that specific alert's data into the right "Diagnostic & Resolution" panel — replacing whatever was shown before.
- Only one row can be selected at a time.
- If no row has ever been clicked, the diagnostic panel shows the empty state message: "Select an alert from the triage feed to inspect fault detail, predictive impact, and resolution controls." (Use this as the true starting state — the image shows INC-2291 pre-selected only as a reference for how the panel looks when populated.)

2. PER-ALERT DATA (each row must load its own unique data into the diagnostic panel when clicked)
- INC-2291: Asset "Sub-station 04 Breaker (Feeder Bay 2)" — Location: Aluva Depot, Detected: 14:32:08, Signal Loss: 412 ms, Affected Sets: Set Alpha, Set Bravo — Predictive: "AI pipeline predicts a 14-minute cumulative network delay within the next two headway cycles if the Aluva bottleneck is not isolated within 6 minutes." — Sparkline: 22,24,23,41,78,95,88,91
- INC-2290: Asset "Edapally Platform Screen Door Wireless Link" — Location: Edapally Station, Detected: 14:11:52, Packet Loss: 6.3%, Affected Sets: None (station-side) — Predictive: "If unresolved, dwell time at Edapally may extend by 20–35 seconds per stop during peak headway, with low risk of cascading delay." — Sparkline: 10,12,14,19,26,24,29,31
- INC-2289: Asset "CBTC Headway Controller — Kaloor Segment" — Location: Kaloor Corridor, Detected: 13:58:04, Headway Delta: -18 sec, Affected Sets: Set Bravo, Set Charlie — Predictive: "Continued overlap risks an automatic speed restriction on the Kaloor approach, adding an estimated 3–5 minutes to the affected run." — Sparkline: 30,28,33,40,44,42,47,50
- INC-2288: Asset "OCC Telemetry Aggregator — Node 3" — Location: OCC Core, Detected: 13:40:15, Refresh Lag: 2.1 sec, Affected Sets: None — Predictive: "No operational impact expected. Dashboard refresh lag is within tolerance; monitoring for recurrence." — Sparkline: 4,5,4,6,7,5,6,6
- INC-2287: Asset "Onboard Diagnostics Unit — Set Delta / Car 2" — Location: Set Delta (in service), Detected: 13:22:47, Version Skew: 1 minor rev, Affected Sets: Set Delta — Predictive: "Minor version skew logged for audit only. No functional degradation expected before next scheduled induction." — Sparkline: 3,3,4,3,4,4,3,4
- INC-2286: Asset "Yard Bay Track Sensors 5–7" — Location: Muttom Yard, Detected: 12:57:31, Surface Moisture: High, Affected Sets: Yard shunting only — Predictive: "Elevated adhesion risk during shunting moves. Recommend reduced yard speed until surface moisture drops below advisory threshold." — Sparkline: 15,20,34,38,36,40,37,39

3. GATED ACTION BUTTONS
- The 3 buttons under "Incident Resolution Controls" (Clear / Isolate / Dispatch) are locked, grayscale, and non-clickable when the user is a guest.
- They become fully colored and clickable only when the user is signed in — matching the shared sign-in state used across the rest of the project.
- The gate note beneath them switches text accordingly: signed-in shows "Signed in as Priya (OP-1042) — actions will be recorded to the audit ledger." Guest shows "Sign in to unlock resolution controls for this alert."

4. BUTTON CLICK BEHAVIOR (only when signed in)
- "Acknowledge and Clear Alert": removes that alert from the feed list permanently for the session, adds a new row to the top of the ledger table (using that alert's ID, current timestamp, its severity, "Priya (OP-1042)" as the operator, and a freshly random hex hash like "a1b2c3…d4e5f6"), clears the diagnostic panel back to empty state, and shows a bottom-center toast: "{Incident ID} cleared and logged to the audit ledger."
- "Isolate Sub-System Node": shows a toast "{Asset name} isolated from the live network." — no other state change.
- "Deploy Field Maintenance Dispatch": shows a toast "Maintenance work order generated for {Incident ID}." — no other state change.
- If all 6 alerts get cleared, the feed shows: "No active deviations. All incidents have been cleared."

5. NAVIGATION RULE (project-wide)
Every trigger meant to open Alerts — nav link on every page, or any other button/card meant to reach Alerts — checks the shared sign-in state: signed in → go directly to Alerts; guest → go to Sign In instead. After sign-in, return to Dashboard with the signed-in header; from there Alerts-bound triggers go straight to Alerts. Logging out reverts to guest and restores the Sign-In detour.

Match all colors, spacing, fonts, and layout precisely to the attached reference image. Do not change the Dashboard, Sign In, Schedule, Fleet & Induction, or Reports pages.