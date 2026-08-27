"""
generate_synthetic_data.py

Generates realistic SYNTHETIC ridership history for the KMRL Aluva-Petta
line, for use as a stand-in while real AFC (gate) data is being collected.

This is clearly synthetic data - not real KMRL ridership. It's built from
explicit, documented assumptions (peak-hour curve, weekday/weekend split,
monsoon effect, Kerala + national holiday calendar, occasional special
events) so a forecasting model trained on it learns genuine structure
(time-of-day, day-of-week, weather, holiday effects) rather than pure
noise. Swap this script's output for a real AFC export later and nothing
downstream (training script, forecast-service) needs to change, as long
as the column schema below is preserved.

Output: data/synthetic_ridership.csv
Columns:
    timestamp        - half-hourly slot start, ISO 8601
    station           - one of the 5 stations on the AI Simulation map
    direction         - "UP" (Aluva->Petta) or "DOWN" (Petta->Aluva)
    day_of_week       - 0=Monday .. 6=Sunday
    hour              - 0-23
    is_weekend        - 0/1
    is_holiday        - 0/1
    holiday_name      - string or ""
    weather            - Clear / Wet Rails / Heavy Monsoon (matches the
                          AI Simulation sandbox's weather categories)
    special_event     - 0/1 (e.g. stadium event near JLN Stadium)
    passenger_count   - SYNTHETIC target variable
"""

import csv
import random
from datetime import datetime, timedelta, date

random.seed(42)

STATIONS = ["Aluva", "Edapally", "JLN Stadium", "MG Road", "Thrippunithura"]
DIRECTIONS = ["UP", "DOWN"]

STATION_MULTIPLIER = {
    "Aluva": 0.85,
    "Edapally": 1.05,
    "JLN Stadium": 1.15,
    "MG Road": 1.30,
    "Thrippunithura": 0.80,
}

HOLIDAYS = {
    "2024-01-26": "Republic Day",
    "2024-03-25": "Holi",
    "2024-04-11": "Eid al-Fitr",
    "2024-04-14": "Vishu",
    "2024-05-01": "May Day",
    "2024-08-15": "Independence Day",
    "2024-08-26": "Onam (Thiruvonam)",
    "2024-10-02": "Gandhi Jayanti",
    "2024-10-31": "Diwali",
    "2024-12-25": "Christmas",
    "2025-01-01": "New Year",
    "2025-01-14": "Makar Sankranti",
    "2025-01-26": "Republic Day",
    "2025-03-14": "Holi",
    "2025-04-14": "Vishu",
    "2025-05-01": "May Day",
    "2025-08-15": "Independence Day",
    "2025-09-05": "Onam (Thiruvonam)",
    "2025-10-02": "Gandhi Jayanti",
    "2025-10-20": "Diwali",
    "2025-12-25": "Christmas",
}

SPECIAL_EVENT_DATES = {
    "2024-02-10", "2024-04-20", "2024-11-16",
    "2025-02-08", "2025-04-19", "2025-11-15",
}

START_DATE = date(2024, 1, 1)
END_DATE = date(2025, 7, 31)

def is_monsoon_season(d: date) -> bool:

    return d.month in (6, 7, 8, 9)

def weather_for_day(d: date, rng: random.Random) -> str:
    if is_monsoon_season(d):
        roll = rng.random()
        if roll < 0.35:
            return "Heavy Monsoon"
        elif roll < 0.70:
            return "Wet Rails"
        else:
            return "Clear"
    else:
        roll = rng.random()
        if roll < 0.85:
            return "Clear"
        elif roll < 0.97:
            return "Wet Rails"
        else:
            return "Heavy Monsoon"

def base_hourly_curve(hour: int) -> float:
    """Typical urban metro demand curve: AM peak, PM peak, quiet late night."""
    curve = {
        0: 0.05, 1: 0.02, 2: 0.02, 3: 0.02, 4: 0.05, 5: 0.20,
        6: 0.55, 7: 0.95, 8: 1.00, 9: 0.85, 10: 0.55, 11: 0.45,
        12: 0.50, 13: 0.50, 14: 0.45, 15: 0.50, 16: 0.65, 17: 0.90,
        18: 1.00, 19: 0.95, 20: 0.70, 21: 0.45, 22: 0.25, 23: 0.10,
    }
    return curve[hour]

def generate_row(ts: datetime, station: str, direction: str, rng: random.Random,
                  day_weather: str) -> dict:
    d = ts.date()
    dow = ts.weekday()
    hour = ts.hour
    is_weekend = dow >= 5
    holiday_name = HOLIDAYS.get(d.isoformat(), "")
    is_holiday = bool(holiday_name)
    special_event = d.isoformat() in SPECIAL_EVENT_DATES and station == "JLN Stadium"

    base = base_hourly_curve(hour) * STATION_MULTIPLIER[station]

    if is_weekend:
        base = base * 0.65 + (0.25 if 10 <= hour <= 20 else 0)

    if is_holiday and not is_weekend:
        base = base * 0.55 + (0.30 if 10 <= hour <= 21 else 0)

    if day_weather == "Wet Rails":
        base *= 1.08
    elif day_weather == "Heavy Monsoon":
        base *= 1.15

    if special_event and 16 <= hour <= 21:
        base *= 1.6

    peak_capacity = 900
    mean_count = base * peak_capacity
    noise = rng.gauss(0, mean_count * 0.10)
    count = max(0, round(mean_count + noise))

    return {
        "timestamp": ts.isoformat(),
        "station": station,
        "direction": direction,
        "day_of_week": dow,
        "hour": hour,
        "is_weekend": int(is_weekend),
        "is_holiday": int(is_holiday),
        "holiday_name": holiday_name,
        "weather": day_weather,
        "special_event": int(special_event),
        "passenger_count": count,
    }

def main():
    rng = random.Random(42)
    rows = []
    d = START_DATE
    while d <= END_DATE:
        day_weather = weather_for_day(d, rng)
        for hour in range(24):
            for minute in (0, 30):
                ts = datetime(d.year, d.month, d.day, hour, minute)
                for station in STATIONS:
                    for direction in DIRECTIONS:
                        rows.append(generate_row(ts, station, direction, rng, day_weather))
        d += timedelta(days=1)

    out_path = "data/synthetic_ridership.csv"
    fieldnames = list(rows[0].keys())
    with open(out_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Wrote {len(rows):,} rows to {out_path}")
    print(f"Date range: {START_DATE} to {END_DATE}")

if __name__ == "__main__":
    main()
