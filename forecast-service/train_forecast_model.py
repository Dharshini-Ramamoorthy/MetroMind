"""
train_forecast_model.py

Trains a demand-forecasting model on the synthetic ridership data
(data/synthetic_ridership.csv) and saves it to models/demand_model.joblib
for forecast-service to load.

Model: XGBoost (XGBRegressor) — gradient-boosted decision trees, industry
standard for tabular demand-forecasting problems like this one. Requires
`pip install xgboost` (needs internet — not available in the sandbox this
was originally built in, so this exact script hasn't been run end-to-end
yet; run it locally and confirm the metrics printed below look sane before
trusting the saved model).

Run:
    pip install -r requirements.txt
    python3 train_forecast_model.py
"""

import json
import joblib
import numpy as np
import pandas as pd
from xgboost import XGBRegressor
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.inspection import permutation_importance

DATA_PATH = "data/synthetic_ridership.csv"
MODEL_PATH = "models/demand_model.joblib"
METRICS_PATH = "models/training_metrics.json"

CATEGORICAL_COLS = ["station", "direction", "weather"]
NUMERIC_COLS = ["day_of_week", "hour", "is_weekend", "is_holiday", "special_event"]
TARGET_COL = "passenger_count"

def load_and_prepare():
    df = pd.read_csv(DATA_PATH, low_memory=False)

    df_enc = pd.get_dummies(df, columns=CATEGORICAL_COLS, prefix=CATEGORICAL_COLS)

    feature_cols = NUMERIC_COLS + [
        c for c in df_enc.columns
        if any(c.startswith(p + "_") for p in CATEGORICAL_COLS)
    ]

    X = df_enc[feature_cols]
    y = df_enc[TARGET_COL]
    return X, y, feature_cols

def main():
    print("Loading data...")
    X, y, feature_cols = load_and_prepare()
    print(f"Rows: {len(X):,}  Features: {len(feature_cols)}")

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42
    )

    print("Training XGBRegressor...")
    model = XGBRegressor(
        n_estimators=50,
        learning_rate=0.1,
        max_depth=6,
        subsample=0.85,
        colsample_bytree=0.85,
        reg_lambda=0.5,
        reg_alpha=0.0,
        random_state=42,
        n_jobs=-1,
        eval_metric="mae",
    )
    model.fit(
        X_train, y_train,
        eval_set=[(X_test, y_test)],
        verbose=False,
    )

    print("Evaluating...")
    preds = model.predict(X_test)
    mae = mean_absolute_error(y_test, preds)
    rmse = float(np.sqrt(mean_squared_error(y_test, preds)))
    r2 = r2_score(y_test, preds)
    mean_actual = y_test.mean()

    print(f"MAE:  {mae:.1f} passengers  ({mae/mean_actual*100:.1f}% of mean demand)")
    print(f"RMSE: {rmse:.1f} passengers")
    print(f"R^2:  {r2:.3f}")

    print("Reading XGBoost's built-in feature importance (gain-based)...")
    importances = sorted(
        zip(feature_cols, model.feature_importances_),
        key=lambda t: -t[1]
    )[:10]
    print("Top features:")
    for name, score in importances:
        print(f"  {name:30s} {score:.4f}")

    joblib.dump({"model": model, "feature_cols": feature_cols}, MODEL_PATH)
    print(f"Saved model to {MODEL_PATH}")

    metrics = {
        "model": "XGBRegressor",
        "mae": mae,
        "rmse": rmse,
        "r2": r2,
        "mean_actual_demand": float(mean_actual),
        "n_train_rows": len(X_train),
        "n_test_rows": len(X_test),
        "top_features": [{"feature": n, "importance": float(s)} for n, s in importances],
        "note": "Trained on SYNTHETIC data - metrics describe how well the model "
                "learned the synthetic generator's patterns, not real-world accuracy. "
                "Retrain on real AFC data before trusting these numbers operationally.",
    }
    with open(METRICS_PATH, "w") as f:
        json.dump(metrics, f, indent=2)
    print(f"Saved metrics to {METRICS_PATH}")

if __name__ == "__main__":
    main()
