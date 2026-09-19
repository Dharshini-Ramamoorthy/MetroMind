Here's the full prompt with those three additions folded in:

Design a two-screen Figma flow for "MetroMind KMRL," an internal metro rail
operations console: a Sign In screen and a Forgot Password screen that share
the exact same page shell. Style: clean enterprise/SaaS, teal (#009688) and
emerald (#10B981) accents on white/light-gray, Plus Jakarta Sans for
headings, Inter for body text, generous whitespace, soft shadows, 12-16px
rounded corners.
═══════════════════════════════════════
SHARED PAGE SHELL (identical on both screens)
═══════════════════════════════════════

Top ribbon — thin dark bar (#0F172A), full width:

Left: small green pulsing dot + "KMRL Internal Systems — Authorized
Personnel Only" in light gray, 12px
Right: rounded pill badge "PRODUCTION", green-tinted background,
uppercase, bold, 10px


Nav bar — white, translucent/blurred background, bottom border:

Left: logo mark (34px rounded square, teal-to-emerald gradient,
white bold "MM") + wordmark "MetroMind KMRL", bold, 18px
Right: "Back to Guest View" text link with a left-chevron icon, gray
Interaction: clicking "Back to Guest View" navigates directly to
the Landing Page (the public, signed-out view of the app). This
works identically on both the Sign In screen and the Forgot Password
screen, regardless of which state (A or B) the Forgot Password screen
is in — it always exits straight back to the Landing Page, with no
confirmation step and no dependency on origin_context or any other
session variable.


Main area — two-column split (~55/45), full remaining height, thin
vertical divider between columns:
LEFT COLUMN (plain white/canvas, vertically centered, ~56px padding):

Small uppercase teal eyebrow: "MetroMind KMRL"
Large bold headline, ~36px: "The control layer behind every train
on the line."
Gray supporting paragraph, ~15px, max-width ~400px: "Fleet status,
induction planning, and live alerts — one console, kept in sync
with the network in real time."
Vertical stack of 3 white rounded stat cards, subtle shadow, bold
teal number left + short gray label right, lifting on hover:
"21 / 27" — Trains active across the network right now
"98.2%" — On-time performance this month
"4 min 12s" — Current target headway, DRL-optimized

RIGHT COLUMN — centered white card, max-width ~380px, content swaps
between the two screens (detailed below).
Footer — do not design a new footer for this flow. Reuse the exact
footer component that already exists on the Fleet & Induction page
(dark bar, gradient logo mark + "MetroMind KMRL" wordmark + "Fleet
induction & scheduling console" tagline on the left, "About" and
"Contact Systems" links centered, version pill on the right). Pull in
that existing footer instance/component as-is rather than rebuilding it.

Remove the "© 2026 KMRL Internal Systems" text and the "v2.4.1"
line from it wherever it currently appears — the footer should end
with just the version pill, no copyright/date text underneath or
beside it.
Promote this corrected footer to a single global component
("Footer / Global") and swap every page in the file — this Sign In
screen, the Forgot Password screen, the landing page, all three role
dashboards, and every module detail page — to use that one shared
instance. Any future edit to the master component should update the
footer everywhere at once, so there is exactly one footer design in
the whole file, not a per-page copy.



═══════════════════════════════════════
SCREEN 1 — SIGN IN (right column content)
═══════════════════════════════════════

Heading "Welcome back", 26px bold
Subtext: "Sign in with your KMRL operator account to continue."
Error banner (hidden by default, shown as a variant): light red
background/border, warning-triangle icon, red text — placeholder
message: "Please enter a valid email address."
Email field: label "Email", mail icon inset left, placeholder
"you@kmrl.co.in"
Password field: label "Password", lock icon inset left, eye-toggle
icon button inset right, placeholder "Enter your password"
Row: "Keep me signed in" checkbox left, "Forgot password?" teal link
right
Full-width primary button: teal-to-dark-teal gradient, white text,
lock icon, label "Sign In" (states: "Sign In" → "Verifying…" →
"Access Granted"), soft teal glow on hover
Footnote: small lock icon + gray text: "Access is restricted to
authorized KMRL personnel. Sign-in attempts are logged and reported
to IT Security."

PROTOTYPE INTERACTION LOGIC for the "Sign In" button, branching on the
entered email's domain:

Ends with "@systemadmin.kmrl..." → navigate to "System Admin Dashboard" frame
Ends with "@operationalcontroller.kmrl..." → navigate to "Operational Controller Dashboard" frame
Ends with "@depotmanager.kmrl..." → navigate to "Maintenance / Depot
Manager Dashboard" frame
Any other domain → stay on this screen, show the error banner
variant with message: "We don't recognize that email domain. Use
your systemadmin.kmrl, operationalcontroller.kmrl, or depotmanager.kmrl address."
"Forgot password?" link → navigate to Screen 2 (Forgot Password).

═══════════════════════════════════════
SCREEN 2 — FORGOT PASSWORD (right column content, two states)
═══════════════════════════════════════
STATE A — Request reset link (default):

Heading "Reset your password", 26px bold
Subtext: "Enter your registered email and we'll send you a reset link."
Error banner (hidden by default): light red background, warning icon,
red text — placeholder: "Please enter a valid email address."
Email field: label "Email", mail icon inset left, placeholder
"you@kmrl.co.in"
Full-width primary button: teal gradient, white text, envelope icon,
label "Send reset link"
Below it, full-width secondary button: light gray/outlined style,
left-chevron icon, label "Back to Sign In"

STATE B — Success (after "Send reset link" is triggered):

Same heading "Reset your password"
Success banner: light green/mint background, green border, checkmark
icon, green text — message: "Mail sent. Check your inbox for the
reset link."
Subtext updates to: "Mail sent to [email]. Check your inbox for the
reset link."
Email field and "Send reset link" button are hidden — only "Back to
Sign In" remains visible

PROTOTYPE INTERACTION LOGIC:

"Send reset link" → if email is empty/invalid, stay in State A and
show the error banner. If valid, transition to State B.
After ~2.5s in State B, auto-navigate back to Screen 1 (Sign In).
"Back to Sign In" button (both states) → navigate directly back to
Screen 1 at any time.
This screen has no domain routing of its own — role-based dashboard
routing only happens from Screen 1, after the user signs back in
with their reset password.

═══════════════════════════════════════
DYNAMIC CONTENT — mark these as editable text layers / bound to
variables across both screens (NOT baked into background art):
═══════════════════════════════════════

The 3 stat numbers + labels (left column, shared)
Headline + lead paragraph (left column, shared)
Email and password input values/placeholders
Error banner message text (and its show/hide state)
Success banner message text, including the interpolated email
Sign In button label/loading states
Forgot Password subtext (changes between State A and State B)
"Send reset link" button label/loading state ("Send reset link" →
"Sending…")

Static chrome — top ribbon, nav bar, card shells, icons, dividers,
button shapes, and the global footer — should stay fixed across both
screens for visual consistency.
═══════════════════════════════════════
ADDITIONAL FIXES — apply across the existing file
═══════════════════════════════════════
1. Fleet & Induction page — remove breadcrumb

On the Fleet & Induction page, remove the breadcrumb/label text
currently reading "Home / Fleet & Induction" entirely — delete the
text layer and close up the spacing above the page title so the
page header sits cleanly with no leftover gap.

2. Alerts page — remove subheading label

On the Alerts page, remove the text currently reading "Console ·
Incident & Alert Management" entirely — delete the text layer and
close up the spacing so the page header/title sits directly where
that label used to be, with no leftover gap.

3. Make the "no edit access" error message a single global component

Currently the Fleet & Induction page and the Maintenance page each
show a different wording for the read-only error message when the
Operations Controller clicks a restricted action (train selection /
checkboxes on Fleet & Induction; "Start Job," "Submit Resolution,"
"Send to Approver" on Maintenance). These must be unified.
Build one single master toast/snackbar component named
"Toast / No Edit Access" with exactly one message across the entire
file: "No edit access — you don't have permission to modify this
page."
Replace every existing instance of this error message — on the Fleet
& Induction page and on the Maintenance page — with an instance of
this same master component. Do not keep separate/duplicated toast
components with different wording; there should be exactly one
source component that both pages reference.
Wire it so that when access_mode = read_only for the Operations
Controller role, any click on a restricted action on either page
(train selection or checkbox toggle on Fleet & Induction; "Start
Job," "Submit Resolution," or "Send to Approver" on Maintenance)
triggers this same shared toast instance, styled and positioned
identically (dark rounded rectangle, warning/lock icon, fade-in/
fade-out, auto-dismiss ~3s, fixed position so it's never clipped).
Any future change to the message text or styling should be made once
on the master component and propagate automatically to both pages.