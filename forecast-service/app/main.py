"""
app/main.py

forecast-service — FastAPI microservice that serves demand predictions
and schedule recommendations, trained on SYNTHETIC data (see
generate_synthetic_data.py). Intended to sit behind the same gateway as
the other KMRL services.

Run locally:
    pip install -r requirements.txt
    uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload

Endpoints:
    GET  /health
    GET  /api/v1/forecast/demand?station=MG Road&direction=UP&weather=Clear
         &timestamp=2025-08-15T08:00:00&is_holiday=true&special_event=false
    GET  /api/v1/forecast/schedule?weather=Clear&timestamp=...&is_holiday=...
         -> predicts corridor-wide peak demand and returns a headway/fleet
            recommendation, same shape as the AI Simulation sandbox already
            shows, so it's a drop-in replacement for the slider-driven
            values once you're ready to wire it into schedule-service.

Scheduling: the nightly proposal job (tomorrow's timetable) runs on its
own every day at 23:00 Asia/Kolkata as long as this process is running -
see app/self_scheduler.py. No external cron or Task Scheduler entry is
needed. The mid-day re-forecast job is event-triggered instead (weather
change / incident flag), so it's only reachable via
POST /api/v1/forecast/reforecast, called from wherever those events
already originate in your system.
"""

from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel

from app.predict import (
    STATIONS, DIRECTIONS, WEATHER_OPTIONS,
    predict_demand, predict_corridor_demand, demand_to_schedule, predict_daily_profile,
)
from app.db import log_prediction, insert_actual, count_actuals, get_recent_actuals
from app.scheduler_jobs import generate_nightly_schedule, reforecast_midday
from app.security import GatewayHeaderAuthenticationMiddleware

app = FastAPI(
    title="KMRL forecast-service",
    description="Demand forecasting trained on SYNTHETIC ridership data — "
                "see generate_synthetic_data.py for the documented assumptions "
                "behind the training set. Swap in real AFC data and retrain "
                "before using this operationally.",
    version="0.1.0-synthetic",
)

app.add_middleware(GatewayHeaderAuthenticationMiddleware)

class DemandResponse(BaseModel):
    station: str
    direction: str
    weather: str
    timestamp: str
    is_holiday: bool
    special_event: bool
    predicted_passenger_count: float
    data_source: str = "SYNTHETIC — see generate_synthetic_data.py"

class ScheduleResponse(BaseModel):
    timestamp: str
    weather: str
    is_holiday: bool
    incident: bool
    corridor_demand: dict
    peak_station: str
    peak_demand: float
    predicted_surge_multiplier: float
    recommended_headway_seconds: int
    recommended_fleet_size: int
    estimated_capacity_pct: int
    data_source: str = "SYNTHETIC — see generate_synthetic_data.py"

class ActualRidershipRecord(BaseModel):
    timestamp: str
    station: str
    direction: str
    weather: str
    is_holiday: bool = False
    special_event: bool = False
    passenger_count: int

class ActualsSubmitResponse(BaseModel):
    id: str
    total_actuals_recorded: int

@app.get("/health")
def health():
    return {"status": "UP", "model": "xgboost-v0.1"}

@app.post("/api/v1/forecast/actuals", response_model=ActualsSubmitResponse)
def submit_actual_ridership(record: ActualRidershipRecord):
    """
    Record ONE real, observed ridership figure — a real number for a real
    day, however you have it right now (manual entry today, AFC gate export
    later — the schema doesn't change either way).

    This is what future retraining will eventually read from instead of
    the synthetic CSV. It starts at zero and only grows from real
    operational days going forward — nothing here is backfilled or
    invented.
    """
    if record.station not in STATIONS:
        raise HTTPException(status_code=400, detail=f"station must be one of {STATIONS}")
    if record.direction not in DIRECTIONS:
        raise HTTPException(status_code=400, detail=f"direction must be one of {DIRECTIONS}")
    if record.weather not in WEATHER_OPTIONS:
        raise HTTPException(status_code=400, detail=f"weather must be one of {WEATHER_OPTIONS}")

    inserted_id = insert_actual(record.model_dump())
    total = count_actuals()
    return ActualsSubmitResponse(id=inserted_id, total_actuals_recorded=total)

@app.get("/api/v1/forecast/actuals/count")
def get_actuals_count():
    """How many real rows have been recorded so far — a quick sanity
    check / progress tracker toward having enough real data to retrain on."""
    return {"total_actuals_recorded": count_actuals()}

@app.get("/api/v1/forecast/actuals/recent")
def get_recent_actual_records(limit: int = Query(50, ge=1, le=500)):
    return {"records": get_recent_actuals(limit)}

@app.get("/api/v1/forecast/demand", response_model=DemandResponse)
def get_demand(
    station: str = Query(..., description=f"One of: {STATIONS}"),
    direction: str = Query(..., description=f"One of: {DIRECTIONS}"),
    weather: str = Query("Clear", description=f"One of: {WEATHER_OPTIONS}"),
    timestamp: str = Query(..., description="ISO 8601, e.g. 2025-08-15T08:00:00"),
    is_holiday: bool = Query(False),
    special_event: bool = Query(False),
):
    try:
        dt = datetime.fromisoformat(timestamp)
    except ValueError:
        raise HTTPException(status_code=400, detail="timestamp must be ISO 8601, e.g. 2025-08-15T08:00:00")

    try:
        demand = predict_demand(station, direction, weather, dt, is_holiday, special_event)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

    log_prediction(
        {"station": station, "direction": direction, "weather": weather,
         "timestamp": timestamp, "is_holiday": is_holiday, "special_event": special_event},
        demand,
    )

    return DemandResponse(
        station=station, direction=direction, weather=weather,
        timestamp=timestamp, is_holiday=is_holiday, special_event=special_event,
        predicted_passenger_count=round(demand, 1),
    )

class NightlyScheduleRequest(BaseModel):
    weather: str = "Clear"
    is_holiday: bool = False
    special_event: bool = False
    force: bool = False

class ReforecastRequest(BaseModel):
    reason: str
    weather: str = "Clear"
    incident: bool = False

@app.post("/api/v1/forecast/generate-daily-schedule")
def trigger_nightly_schedule(request: NightlyScheduleRequest):
    """
    Trigger point for tomorrow's proposed timetable. Nothing in this
    service schedules itself — call this from an external trigger (Jenkins
    nightly job, a cron hitting this endpoint, etc.), same as any other
    scheduled task in this system. Every trip this produces lands as
    PROPOSED in schedule-service and needs OC/SADA sign-off before
    DynamicScheduleEngine will ever dispatch it — see
    app/scheduler_jobs.py and app/schedule_client.py for the full chain.
    """
    if request.weather not in WEATHER_OPTIONS:
        raise HTTPException(status_code=400, detail=f"weather must be one of {WEATHER_OPTIONS}")
    return generate_nightly_schedule(
        weather=request.weather,
        is_holiday=request.is_holiday,
        special_event=request.special_event,
        force=request.force,
    )

@app.post("/api/v1/forecast/reforecast")
def trigger_midday_reforecast(request: ReforecastRequest):
    """
    Trigger point for a mid-day re-forecast — wire this to whatever
    already flips the weather/incident flags today (Disruption Simulator,
    Festival Calendar page, etc.) so a real condition change kicks off a
    fresh set of proposals for the rest of the day, instead of the old
    manual-slider sandbox value. Still produces PROPOSED trips only.
    """
    if request.weather not in WEATHER_OPTIONS:
        raise HTTPException(status_code=400, detail=f"weather must be one of {WEATHER_OPTIONS}")
    return reforecast_midday(reason=request.reason, weather=request.weather, incident=request.incident)

@app.get("/api/v1/forecast/schedule", response_model=ScheduleResponse)
def get_schedule_recommendation(
    weather: str = Query("Clear", description=f"One of: {WEATHER_OPTIONS}"),
    timestamp: str = Query(..., description="ISO 8601, e.g. 2025-08-15T08:00:00"),
    is_holiday: bool = Query(False),
    special_event: bool = Query(False),
    incident: bool = Query(False, description="Corridor incident flag, same as the AI Simulation toggle"),
):
    try:
        dt = datetime.fromisoformat(timestamp)
    except ValueError:
        raise HTTPException(status_code=400, detail="timestamp must be ISO 8601, e.g. 2025-08-15T08:00:00")

    if weather not in WEATHER_OPTIONS:
        raise HTTPException(status_code=400, detail=f"weather must be one of {WEATHER_OPTIONS}")

    corridor = predict_corridor_demand(weather, dt, is_holiday, special_event)
    peak_key = max(corridor, key=corridor.get)
    peak_demand = corridor[peak_key]

    schedule = demand_to_schedule(peak_demand, weather, incident)

    return ScheduleResponse(
        timestamp=timestamp, weather=weather, is_holiday=is_holiday, incident=incident,
        corridor_demand={k: round(v, 1) for k, v in corridor.items()},
        peak_station=peak_key, peak_demand=round(peak_demand, 1),
        **schedule,
    )

@app.get("/api/v1/forecast/daily-profile")
def get_daily_forecast_profile(
    service_date: str = Query(..., description="Service date in YYYY-MM-DD format"),
    weather: str = Query("Clear", description=f"One of: {WEATHER_OPTIONS}"),
    is_holiday: bool = Query(False),
    special_event: bool = Query(False),
    incident: bool = Query(False),
):
    """
    Returns entire day's 36 demand & headway slots (05:00 - 23:00) in ONE single call (< 50ms).
    Used by schedule-service for instantaneous full-day schedule generation.
    """
    try:
        parsed_date = datetime.strptime(service_date.strip(), "%Y-%m-%d")
    except ValueError:
        raise HTTPException(status_code=400, detail="service_date must be YYYY-MM-DD")

    slots = predict_daily_profile(weather, parsed_date, is_holiday, special_event, incident)
    return {
        "service_date": service_date,
        "weather": weather,
        "is_holiday": is_holiday,
        "special_event": special_event,
        "incident": incident,
        "total_slots": len(slots),
        "slots": slots
    }

