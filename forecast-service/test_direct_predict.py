from datetime import datetime
from app.predict import predict_daily_profile

def test_direct(date_str, day_name):
    dt = datetime.fromisoformat(date_str)
    slots = predict_daily_profile("Clear", dt, False, False, False)
    print(f"\n=== {day_name} ({date_str}) ===")
    for s in slots:
        start_min = s.get("start_minute")
        if start_min in [360, 420, 480, 540, 660, 780, 1020, 1080, 1200, 1320]:
            hour = start_min // 60
            mins = start_min % 60
            time_str = f"{hour:02d}:{mins:02d}"
            print(f"  {time_str} | Demand: {s.get('peak_demand'):5.1f} | Surge: {s.get('predicted_surge_multiplier'):.2f} | Headway: {s.get('recommended_headway_seconds')}s ({s.get('recommended_headway_seconds')/60:.1f}m) | Fleet: {s.get('recommended_fleet_size'):2d}")

test_direct("2026-08-16", "SUNDAY")
test_direct("2026-08-17", "MONDAY")
test_direct("2026-08-15", "SATURDAY")
