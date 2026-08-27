"""
app/predict.py

Core prediction + demand-to-schedule logic, kept separate from the FastAPI
layer so it can be unit-tested / run standalone without needing a web
server up.
"""

import joblib
import pandas as pd
from datetime import datetime
from pathlib import Path

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "demand_model.joblib"

STATIONS = ["Aluva", "Edapally", "JLN Stadium", "MG Road", "Thrippunithura"]
DIRECTIONS = ["UP", "DOWN"]
WEATHER_OPTIONS = ["Clear", "Wet Rails", "Heavy Monsoon"]

_model_bundle = None

def _load_model():
    global _model_bundle
    if _model_bundle is None:
        _model_bundle = joblib.load(MODEL_PATH)
    return _model_bundle

def _build_feature_row(station: str, direction: str, weather: str,
                        dt: datetime, is_holiday: bool, special_event: bool) -> pd.DataFrame:
    bundle = _load_model()
    feature_cols = bundle["feature_cols"]

    row = {c: 0 for c in feature_cols}
    row["day_of_week"] = dt.weekday()
    row["hour"] = dt.hour
    row["is_weekend"] = int(dt.weekday() >= 5)
    row["is_holiday"] = int(is_holiday)
    row["special_event"] = int(special_event)

    station_col = f"station_{station}"
    direction_col = f"direction_{direction}"
    weather_col = f"weather_{weather}"
    for col in (station_col, direction_col, weather_col):
        if col in row:
            row[col] = 1

    return pd.DataFrame([row], columns=feature_cols)

def predict_demand(station: str, direction: str, weather: str, dt: datetime,
                    is_holiday: bool = False, special_event: bool = False) -> float:
    """Predicted passenger count for one half-hour slot at one station/direction."""
    if station not in STATIONS:
        raise ValueError(f"Unknown station '{station}'. Expected one of {STATIONS}")
    if direction not in DIRECTIONS:
        raise ValueError(f"Unknown direction '{direction}'. Expected one of {DIRECTIONS}")
    if weather not in WEATHER_OPTIONS:
        raise ValueError(f"Unknown weather '{weather}'. Expected one of {WEATHER_OPTIONS}")

    bundle = _load_model()
    model = bundle["model"]
    X = _build_feature_row(station, direction, weather, dt, is_holiday, special_event)
    pred = model.predict(X)[0]
    return max(0.0, float(pred))

def predict_corridor_demand(weather: str, dt: datetime, is_holiday: bool = False,
                             special_event: bool = False) -> dict:
    """Predicted demand at every station/direction for one time slot — the
    corridor-wide picture used to size the whole schedule, not just one stop."""
    return {
        f"{station}_{direction}": predict_demand(station, direction, weather, dt, is_holiday, special_event)
        for station in STATIONS
        for direction in DIRECTIONS
    }

TRAIN_CAPACITY = 900
ROUND_TRIP_MINUTES = 96
TARGET_LOAD_FACTOR = 0.85

def demand_to_schedule(peak_station_demand: float, weather: str, incident: bool = False,
                       day_of_week: int = 0, is_holiday: bool = False, special_event: bool = False) -> dict:
    is_sunday = (day_of_week == 6)
    is_saturday = (day_of_week == 5)

    day_multiplier = 1.40 if special_event else (0.80 if is_sunday else (0.92 if is_saturday else (0.85 if is_holiday else 1.0)))
    corridor_hourly_demand = max(50.0, float(peak_station_demand * 5.0 * day_multiplier))

    is_peak = corridor_hourly_demand >= (2200.0 if is_sunday else 2500.0) or peak_station_demand >= 480.0
    surge = max(0.50, min(2.5, (peak_station_demand * day_multiplier) / 450.0))

    weather_coef = {"Clear": 1.0, "Wet Rails": 1.10, "Heavy Monsoon": 1.25}.get(weather, 1.0)
    incident_delay_sec = 120 if incident else 0

    effective_train_capacity = TRAIN_CAPACITY * TARGET_LOAD_FACTOR
    trips_per_hour = (corridor_hourly_demand * weather_coef) / effective_train_capacity

    min_tph = 3.0 if is_sunday else (3.5 if is_saturday else 4.0)
    max_tph = 8.5
    trips_per_hour = max(min_tph, min(max_tph, trips_per_hour))

    raw_headway_min = 60.0 / trips_per_hour
    headway_seconds = int(round(raw_headway_min * 60)) + incident_delay_sec

    headway_seconds = max(420, min(1080 if is_sunday else 900, headway_seconds))

    fleet = int(-(-ROUND_TRIP_MINUTES * 60 // headway_seconds))
    fleet = max(4, min(16, fleet))

    effective_capacity_provided = (3600.0 / headway_seconds) * TRAIN_CAPACITY
    capacity_pct = int(min(120, round((corridor_hourly_demand / effective_capacity_provided) * 100)))

    return {
        "is_peak": is_peak,
        "hourly_passenger_demand": round(corridor_hourly_demand, 1),
        "predicted_surge_multiplier": round(surge, 2),
        "recommended_headway_seconds": headway_seconds,
        "recommended_fleet_size": fleet,
        "estimated_capacity_pct": capacity_pct,
    }

def predict_daily_profile(weather: str, service_date: datetime, is_holiday: bool = False,
                          special_event: bool = False, incident: bool = False) -> list:
    """Vectorized prediction for all 30-min slots from 05:00 to 23:00 in one batch (~30ms)."""
    if weather not in WEATHER_OPTIONS:
        weather = "Clear"

    bundle = _load_model()
    model = bundle["model"]
    feature_cols = bundle["feature_cols"]

    rows = []
    slot_metadata = []

    for m in range(300, 1380, 30):
        dt = datetime(service_date.year, service_date.month, service_date.day, m // 60, m % 60)
        slot_metadata.append((m, dt.isoformat(), dt.weekday()))
        for station in STATIONS:
            for direction in DIRECTIONS:
                row = {c: 0 for c in feature_cols}
                row["day_of_week"] = dt.weekday()
                row["hour"] = dt.hour
                row["is_weekend"] = int(dt.weekday() >= 5)
                row["is_holiday"] = int(is_holiday)
                row["special_event"] = int(special_event)

                station_col = f"station_{station}"
                direction_col = f"direction_{direction}"
                weather_col = f"weather_{weather}"
                for col in (station_col, direction_col, weather_col):
                    if col in row:
                        row[col] = 1
                rows.append(row)

    df = pd.DataFrame(rows, columns=feature_cols)
    preds = model.predict(df)

    idx = 0
    results = []
    for m, iso_ts, dow in slot_metadata:
        slot_preds = preds[idx:idx + 10]
        idx += 10
        peak_demand = max(0.0, float(max(slot_preds)))
        sched = demand_to_schedule(peak_demand, weather, incident, day_of_week=dow, is_holiday=is_holiday, special_event=special_event)
        results.append({
            "start_minute": m,
            "timestamp": iso_ts,
            "weather": weather,
            "is_holiday": is_holiday,
            "incident": incident,
            "peak_demand": round(peak_demand, 1),
            "data_source": "XGBOOST_VECTORIZED",
            **sched
        })

    return results

if __name__ == "__main__":

    demo_dt = datetime(2025, 8, 15, 8, 0)
    print("Predicting demand for Independence Day, 8:00 AM, MG Road, UP, Clear weather:")
    demand = predict_demand("MG Road", "UP", "Clear", demo_dt, is_holiday=True)
    print(f"  Predicted demand: {demand:.0f} passengers")

    schedule = demand_to_schedule(demand, "Clear")
    print("Recommended schedule:")
    for k, v in schedule.items():
        print(f"  {k}: {v}")

    print()
    print("Comparing a normal Tuesday AM peak vs the same slot on a holiday:")
    normal_dt = datetime(2025, 8, 12, 8, 0)
    normal_demand = predict_demand("MG Road", "UP", "Clear", normal_dt, is_holiday=False)
    print(f"  Normal Tuesday 8AM demand:  {normal_demand:.0f}")
    print(f"  Independence Day 8AM demand: {demand:.0f}")
