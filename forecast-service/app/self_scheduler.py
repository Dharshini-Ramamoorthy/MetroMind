"""
app/self_scheduler.py

Runs the nightly job automatically, inside this process, on a fixed
schedule - no external cron, no Windows Task Scheduler entry, nothing
extra to remember to start. As long as forecast-service itself is
running, the nightly proposal job will fire.

The nightly job (see scheduler_jobs.generate_nightly_schedule) always
drafts the service date TWO days ahead of whenever it runs - the 2-day
lead time itself lives there, not here; this module is only responsible
for making sure it fires once every day at a fixed hour.

Deliberately does NOT self-schedule reforecast_midday() the same way:
that job is triggered by an *event* (a weather change or an incident
being flagged), not a fixed time of day, so it stays reachable only via
POST /api/v1/forecast/reforecast, called from wherever those events
already originate (e.g. the Disruption Simulator page). A fixed-interval
re-forecast would fire even when nothing has actually changed.

Uses APScheduler's BackgroundScheduler, which runs its jobs on a thread
inside the same process as the FastAPI app - it stops the moment the
service stops, and needs no separate process, service, or OS-level
scheduler to exist.
"""

import logging
from typing import Optional
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger

from app.scheduler_jobs import generate_nightly_schedule

log = logging.getLogger("forecast-service.self_scheduler")

NIGHTLY_HOUR = 23
NIGHTLY_MINUTE = 0

_scheduler: Optional[BackgroundScheduler] = None

def _run_nightly_job():
    try:
        result = generate_nightly_schedule()
        log.info("Self-scheduled nightly job finished: %s", result)
    except Exception as e:

        log.error("Self-scheduled nightly job failed: %s", e)

def start():
    global _scheduler
    if _scheduler is not None:
        return
    _scheduler = BackgroundScheduler(timezone="Asia/Kolkata")
    _scheduler.add_job(
        _run_nightly_job,
        trigger=CronTrigger(hour=NIGHTLY_HOUR, minute=NIGHTLY_MINUTE),
        id="nightly_schedule_generation",
        replace_existing=True,
    )
    _scheduler.start()
    log.info("Self-scheduler started: nightly job will run at %02d:%02d Asia/Kolkata daily.",
              NIGHTLY_HOUR, NIGHTLY_MINUTE)

def stop():
    global _scheduler
    if _scheduler is not None:
        _scheduler.shutdown(wait=False)
        _scheduler = None
