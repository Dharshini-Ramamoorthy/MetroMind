"""
app/schedule_client.py

Talks to schedule-service to submit AI-generated trip proposals through
the exact same /api/v1/schedule/propose path a human (OC/SADA) uses from
the Gantt Schedule Console — this service never creates an ACTIVE trip
directly and never calls anything on approver-service to auto-approve its
own proposals.

Identity: sent as X-User-Role: SYSTEM. Confirmed against the real
schedule-service project (ScheduleController + GatewayHeaderAuthentication-
Filter) — SYSTEM has been added to both the filter's recognised-role set
and /propose's @PreAuthorize. It is deliberately NOT added anywhere in
approver-service's DECISION_OWNERS map, so this identity can add a
proposal to the queue but can never approve one, its own included.

CONFIRMED PAYLOAD SHAPE (from the real ProposeTripRequest DTO — this
replaces an earlier version of this file that guessed wrong):
  - Fields that exist and are read: tripCode, routeName, startTime,
    endTime, notes, serviceDate, source.
  - startMinutes/endMinutes are NOT part of the request — schedule-service
    derives them itself by parsing startTime/endTime.
  - Times must be "h:mm a" (e.g. "6:00 AM"), not 24-hour "HH:MM" —
    schedule-service's parser silently falls back to "now" on anything it
    can't parse rather than erroring, so getting this format right is
    load-bearing, not cosmetic.
  - serviceDate ("yyyy-MM-dd") is honoured now that both
    DynamicScheduleEngine.generateAndAssignTrip() and ScheduleService
    accept it — without it every proposal used to get silently stamped
    with today's date regardless of what day it was actually for.
"""

import logging
import os
from typing import Optional

import requests

from app.timetable_generator import TripProposal

log = logging.getLogger("forecast-service.schedule_client")

SCHEDULE_SERVICE_URL = os.environ.get("SCHEDULE_SERVICE_URL", "http://localhost:8086")
PROPOSE_PATH = "/api/v1/schedule/propose"

SYSTEM_USER_ID = "forecast-service"
SYSTEM_USER_ROLE = "SYSTEM"

GATEWAY_INTERNAL_SECRET = os.environ.get("GATEWAY_INTERNAL_SECRET", "")

REQUEST_TIMEOUT_SECONDS = 5

class ProposalSubmissionError(Exception):
    pass

def submit_proposal(trip: TripProposal, service_date: str, source: str) -> Optional[dict]:
    """
    POSTs one trip proposal to schedule-service. Best-effort by design,
    same non-fatal-failure philosophy as the rest of this system's
    cross-service calls — one failed submission must never crash the
    whole nightly/mid-day batch. Returns the parsed response body on
    success, None on failure (caller decides whether/how to retry or
    report). A 400 here most often means the server-side headway safety
    floor rejected this slot (another trip already exists too close on
    this route/date) — that's a working guard, not a bug, so it's logged
    at info level rather than warning.
    """
    payload = {
        "tripCode": trip.trip_code,
        "routeName": trip.route_name,
        "startTime": trip.start_time,
        "endTime": trip.end_time,
        "serviceDate": service_date,
        "source": source,
        "notes": trip.notes,
    }

    try:
        headers = {
            "X-User-Id": SYSTEM_USER_ID,
            "X-User-Role": SYSTEM_USER_ROLE,
        }
        if GATEWAY_INTERNAL_SECRET:
            headers["X-Gateway-Secret"] = GATEWAY_INTERNAL_SECRET

        resp = requests.post(
            f"{SCHEDULE_SERVICE_URL}{PROPOSE_PATH}",
            json=payload,
            headers=headers,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        if resp.status_code == 400:
            log.info("Proposal %s rejected by schedule-service (likely headway floor): %s",
                      trip.trip_code, resp.text)
            return None
        resp.raise_for_status()
        return resp.json()
    except requests.RequestException as e:
        log.warning("Failed to submit proposal %s to schedule-service: %s", trip.trip_code, e)
        return None
