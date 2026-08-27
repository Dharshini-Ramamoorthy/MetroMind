"""
app/db.py

MongoDB connection for forecast-service, matching the same pattern your
other services already use (fleet-service -> kmrl_fleet_db, schedule-service
-> kmrl_schedule_db). This service gets its own database: kmrl_forecast_db.

Two collections live here:
  - predictions       : every prediction this service has ever served,
                         logged automatically. This is what lets you later
                         measure real accuracy (predicted vs. what actually
                         happened) once enough actuals exist.
  - ridership_actuals : REAL observed ridership, recorded via
                         POST /api/v1/forecast/actuals. Starts empty. This
                         is what eventually replaces the synthetic CSV as
                         the training data source, once enough real rows
                         have accumulated (see retrain_on_real_data.py).

Requires: pip install pymongo (already in requirements.txt)
Set the MONGO_URI environment variable to override the local-dev default.
"""

import os
from datetime import datetime, timezone
from pymongo import MongoClient

MONGO_URI = os.environ.get("MONGO_URI", "mongodb://localhost:27017/kmrl_forecast_db")

_client = None
_db = None

def get_db():
    global _client, _db
    if _db is None:
        _client = MongoClient(MONGO_URI)
        _db = _client.get_default_database()
    return _db

def log_prediction(request_params: dict, predicted_value):
    """Fire-and-forget log of a prediction that was just served. Failures
    here must never break the actual prediction response — same
    non-fatal-failure philosophy your other services already use for
    cross-service calls."""
    try:
        db = get_db()
        db.predictions.insert_one({
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "request": request_params,
            "predicted_value": predicted_value,
        })
    except Exception as e:

        print(f"[forecast-service] Warning: failed to log prediction: {e}")

def insert_actual(record: dict) -> str:
    """Stores one real observed ridership record. Same schema as the
    synthetic CSV (station, direction, timestamp, weather, is_holiday,
    special_event, passenger_count) so training code can treat real and
    synthetic rows identically once you're ready to blend/replace."""
    db = get_db()
    record = dict(record)
    record["recorded_at"] = datetime.now(timezone.utc).isoformat()
    result = db.ridership_actuals.insert_one(record)
    return str(result.inserted_id)

def count_actuals() -> int:
    db = get_db()
    return db.ridership_actuals.count_documents({})

def get_recent_actuals(limit: int = 50):
    db = get_db()
    docs = list(db.ridership_actuals.find({}, {"_id": 0}).sort("recorded_at", -1).limit(limit))
    return docs
