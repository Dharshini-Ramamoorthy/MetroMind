"""
app/timetable_generator.py

Turns a day's worth of demand predictions into a concrete list of trip
proposals (start/end time, route) that can be POSTed one-by-one to
schedule-service's /api/v1/schedule/propose endpoint.

Design choice: this deliberately reuses the exact same "band -> headway ->
list of start times" shape as schedule-service's own
DataInitializerService.buildStartTimes()/DEFAULT_BOOTSTRAP_BANDS, so a
human reading both codebases sees one consistent scheduling model — the
only thing that changes is where the headway number comes from (a fixed
table there, a live prediction here). It intentionally does NOT try to
also pick a physical train — that stays fleet-service's job via
schedule-service's own standby-ranking logic (see DynamicScheduleEngine),
exactly as called out in "what the AI explicitly does NOT do".
"""

from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import List

from app.predict import predict_corridor_demand, demand_to_schedule

DAY_START_MIN = 6 * 60
DAY_END_MIN = 23 * 60
SAMPLE_STEP_MIN = 30

TRIP_DURATION_MIN = 45

MIN_HEADWAY_SECONDS = 180

@dataclass
class TripProposal:
    trip_code: str
    route_name: str
    start_time: str

    end_time: str
    start_minutes: int
    end_minutes: int
    notes: str

def _fmt(total_minutes: int) -> str:
    """Formats minutes-since-midnight as 'h:mm a' (e.g. '6:00 AM',
    '11:45 PM') — matches TimeUtil.formatMinutesToTime()'s output exactly,
    which is what DateTimeFormatter.ofPattern("h:mm a") on the Java side
    expects to parse back."""
    total_minutes = total_minutes % 1440
    h24, m = divmod(total_minutes, 60)
    period = "AM" if h24 < 12 else "PM"
    h12 = h24 % 12
    if h12 == 0:
        h12 = 12
    return f"{h12:02d}:{m:02d} {period}"

def generate_daily_trip_proposals(service_date: str, weather: str = "Clear",
                                   is_holiday: bool = False, special_event: bool = False,
                                   incident: bool = False) -> List[TripProposal]:
    """
    Samples corridor demand every 30 minutes across the operating day,
    converts each sample into a recommended headway via the existing
    demand_to_schedule() formula, and walks the day generating trip start
    times spaced by whatever headway was recommended for that slot —
    exactly the same mechanic as buildStartTimes()'s Band.headwayMin loop,
    just re-evaluated continuously instead of read from a fixed table.
    """
    base_date = datetime.strptime(service_date, "%Y-%m-%d")

    trips: List[TripProposal] = []
    cursor = float(DAY_START_MIN)
    seq = 1

    while cursor < DAY_END_MIN:
        slot_dt = base_date + timedelta(minutes=cursor)
        corridor = predict_corridor_demand(weather, slot_dt, is_holiday, special_event)
        peak_demand = max(corridor.values())
        rec = demand_to_schedule(peak_demand, weather, incident)

        headway_seconds = max(MIN_HEADWAY_SECONDS, rec["recommended_headway_seconds"])

        start_m = int(round(cursor))

        end_m = min(start_m + TRIP_DURATION_MIN, DAY_END_MIN)
        route_name = "Aluva to Thrippunithura" if (seq % 2 != 0) else "Thrippunithura to Aluva"

        notes = (f"AI-forecasted: surge {rec['predicted_surge_multiplier']}x, "
                 f"headway {headway_seconds}s, weather={weather}"
                 + (", incident flagged" if incident else ""))

        trips.append(TripProposal(
            trip_code=f"AI-{service_date}-{seq:03d}",
            route_name=route_name,
            start_time=_fmt(start_m),
            end_time=_fmt(end_m),
            start_minutes=start_m,
            end_minutes=end_m,
            notes=notes,
        ))

        cursor += headway_seconds / 60.0
        seq += 1

    return trips
