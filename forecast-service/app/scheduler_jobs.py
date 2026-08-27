"""
app/scheduler_jobs.py

The two entry points described in the integration plan:
  1. generate_nightly_schedule()  - once per evening, proposes the whole
     next day's timetable, replacing DataInitializerService's old
     hardcoded-band cron (see that file's own docstring — it already
     documents this handoff and says it must never run its own cron again).
  2. reforecast_midday(reason, ...) - re-run for the rest of today only,
     triggered by a weather change or an incident flag, same pattern as
     the AI Simulation sandbox's live sliders except the surge input is a
     real prediction now.

Neither function calls anything on approver-service or schedule-service
beyond POSTing a proposal — every trip they generate lands as a normal
PROPOSED trip waiting on OC/SADA sign-off, same as a human-authored one.

Wire-level "source" sent to schedule-service is constrained to what
ScheduleService.KNOWN_SOURCES actually accepts: "AI_FORECAST" for the
nightly job, "AI_REFORECAST" for the mid-day job — anything else gets a
400 back. Local dedupe batch tags (below) are a separate, forecast-service
-only concept and can be more specific (e.g. include the trigger reason)
without needing to match schedule-service's vocabulary.

Dedup: keyed on (service_date, batch_tag) in forecast-service's own Mongo
so a job that runs twice for the same day (retry, manual re-trigger)
doesn't flood schedule-service with duplicate proposals. This is
forecast-service tracking its own submission history — it does NOT query
schedule-service for existing trips. schedule-service also now enforces
its own independent headway-floor check on every /propose call, so even
if this local guard were bypassed, a genuinely conflicting proposal still
gets rejected server-side rather than silently double-booking a slot.
"""

import logging
from datetime import datetime, date, timedelta

from app.timetable_generator import generate_daily_trip_proposals
from app.schedule_client import submit_proposal
from app.db import get_db

log = logging.getLogger("forecast-service.scheduler_jobs")

SOURCE_NIGHTLY = "AI_FORECAST"
SOURCE_REFORECAST = "AI_REFORECAST"

def _already_submitted(service_date: str, batch_tag: str) -> bool:
    db = get_db()
    return db.submitted_batches.find_one({"service_date": service_date, "batch_tag": batch_tag}) is not None

def _record_submission_batch(service_date: str, batch_tag: str, source: str, submitted: int, failed: int):
    db = get_db()
    db.submitted_batches.update_one(
        {"service_date": service_date, "batch_tag": batch_tag},
        {"$set": {
            "service_date": service_date,
            "batch_tag": batch_tag,
            "source": source,
            "submitted_at": datetime.utcnow().isoformat(),
            "trips_submitted": submitted,
            "trips_failed": failed,
        }},
        upsert=True,
    )

def generate_nightly_schedule(weather: str = "Clear", is_holiday: bool = False,
                               special_event: bool = False, force: bool = False) -> dict:
    """
    Runs nightly, drafting the service date TWO days out (not tomorrow) —
    e.g. a run on the 10th proposes the timetable for the 12th. This is a
    deliberate lead time, not just "as early as possible": it gives an
    OC/SADA reviewer a full day to look at the AI's draft, edit it via
    PATCH /trips/{id}/adjust if anything's off, and approve it — all
    before the day it actually runs, rather than reviewing same-day or
    the-night-before under time pressure.

    A draft this far out is inherently provisional — train fitness,
    maintenance completions, and weather 2 days ahead aren't fully knowable
    yet, only forecastable. That's exactly why PATCH /trips/{id}/adjust
    stays open on a PLANNED (already-approved) trip right up until it goes
    ACTIVE: if something changes between this nightly draft and the actual
    service day (a train drops out, a weather flag comes in, an incident),
    the approver corrects the already-approved trip directly instead of
    needing a second approval cycle.

    Set force=True to deliberately regenerate (e.g. after correcting a bad
    weather flag), which will just add a second submitted_batches record
    and issue a fresh set of AI- trip codes; it does not delete/cancel the
    earlier batch (schedule-service's own trip cancellation/approval flow
    owns that).
    """
    target_date = (date.today() + timedelta(days=2)).isoformat()
    batch_tag = "NIGHTLY"

    if not force and _already_submitted(target_date, batch_tag):
        log.info("Nightly schedule for %s already proposed; skipping (pass force=True to redo).", target_date)
        return {"service_date": target_date, "status": "skipped_duplicate"}

    trips = generate_daily_trip_proposals(target_date, weather=weather,
                                           is_holiday=is_holiday, special_event=special_event)
    submitted, failed = 0, 0
    for trip in trips:
        result = submit_proposal(trip, target_date, source=SOURCE_NIGHTLY)
        if result is not None:
            submitted += 1
        else:
            failed += 1

    _record_submission_batch(target_date, batch_tag, SOURCE_NIGHTLY, submitted, failed)
    log.info("Nightly schedule for %s: %d proposed, %d failed to submit.", target_date, submitted, failed)
    return {"service_date": target_date, "status": "submitted", "trips_submitted": submitted, "trips_failed": failed}

def reforecast_midday(reason: str, weather: str = "Clear", incident: bool = False) -> dict:
    """
    Re-forecast from *now* to end of the operating day, for TODAY only.
    Does not touch trips whose window has already started/completed —
    DynamicScheduleEngine's own cron is what decides which trips are still
    touchable, this only ever proposes new ones for the remaining window.
    reason is a local dedupe/audit tag only (e.g. "weather_change",
    "incident:INC-042") — it is folded into the batch_tag here so a
    distinct reason can re-trigger even if a generic re-forecast already
    ran today, but it is NOT sent to schedule-service; the wire-level
    source is always the constant SOURCE_REFORECAST.
    """
    today = date.today().isoformat()
    batch_tag = f"REFORECAST:{reason}"

    trips = generate_daily_trip_proposals(today, weather=weather, incident=incident)

    now_minutes = datetime.now().hour * 60 + datetime.now().minute
    remaining = [t for t in trips if t.start_minutes > now_minutes]

    submitted, failed = 0, 0
    for trip in remaining:
        result = submit_proposal(trip, today, source=SOURCE_REFORECAST)
        if result is not None:
            submitted += 1
        else:
            failed += 1

    _record_submission_batch(today, batch_tag, SOURCE_REFORECAST, submitted, failed)
    log.info("Mid-day re-forecast (%s) for %s: %d proposed, %d failed.", reason, today, submitted, failed)
    return {"service_date": today, "reason": reason, "status": "submitted",
            "trips_submitted": submitted, "trips_failed": failed}
