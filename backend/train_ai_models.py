from __future__ import annotations

import csv
import json
import math
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.cluster import DBSCAN, KMeans
from sklearn.ensemble import GradientBoostingRegressor, IsolationForest, RandomForestRegressor
from sklearn.feature_extraction import DictVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    mean_absolute_error,
    mean_squared_error,
    precision_score,
    r2_score,
    recall_score,
    roc_auc_score,
    silhouette_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from app.spatial_features import build_spatial_feature_context, derive_location_risk_features


ROOT_DIR = Path(__file__).resolve().parents[1]
BACKEND_DIR = Path(__file__).resolve().parent
DATASET_PATH = ROOT_DIR / "datasets" / "india_crime_dataset.csv"
MODELS_DIR = BACKEND_DIR / "models"
METRICS_JSON_PATH = MODELS_DIR / "training_metrics.json"
METRICS_MD_PATH = ROOT_DIR / "AI_MODEL_METRICS.md"
RANDOM_STATE = 42


def normalize_crowd_density(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized in {"high", "h"}:
        return "high"
    if normalized in {"low", "l"}:
        return "low"
    return "medium"


def normalize_lighting(value: str | None) -> str:
    normalized = (value or "").strip().lower()
    if normalized in {"good", "bright", "well_lit"}:
        return "good"
    if normalized in {"poor", "dark", "dim"}:
        return "poor"
    return "moderate"


def to_float(value: Any, fallback: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return fallback


def to_int(value: Any, fallback: int = 0) -> int:
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return fallback


def parse_timestamp(date_value: str | None, time_value: str | None) -> datetime:
    date_part = (date_value or "1970-01-01").strip()
    time_part = (time_value or "00:00").strip()
    composed = f"{date_part} {time_part}"
    for fmt in ("%Y-%m-%d %H:%M", "%Y-%m-%d %H:%M:%S"):
        try:
            return datetime.strptime(composed, fmt)
        except ValueError:
            continue
    return datetime(1970, 1, 1, 0, 0)


def load_rows() -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with DATASET_PATH.open("r", encoding="utf-8", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        for raw in reader:
            latitude = to_float(raw.get("latitude"))
            longitude = to_float(raw.get("longitude"))
            if latitude == 0.0 and longitude == 0.0:
                continue

            row = {
                "incident_id": raw.get("incident_id", ""),
                "city": (raw.get("city") or "Unknown").strip() or "Unknown",
                "area_name": (raw.get("area_name") or "Unknown").strip() or "Unknown",
                "date": raw.get("date"),
                "time": raw.get("time"),
                "hour_of_day": to_int(raw.get("hour_of_day")),
                "day_of_week": (raw.get("day_of_week") or "Unknown").strip() or "Unknown",
                "is_weekend": to_int(raw.get("is_weekend")),
                "is_night": to_int(raw.get("is_night")),
                "latitude": latitude,
                "longitude": longitude,
                "crowd_density": normalize_crowd_density(raw.get("crowd_density")),
                "lighting_condition": normalize_lighting(raw.get("lighting_condition")),
                "severity_level": to_float(raw.get("severity_level")),
                "user_reports_count": to_int(raw.get("user_reports_count")),
                "crime_frequency_area": to_float(raw.get("crime_frequency_area")),
                "risk_score": to_float(raw.get("risk_score")),
                "timestamp": parse_timestamp(raw.get("date"), raw.get("time")),
            }
            rows.append(row)
    return rows


def classification_metrics(y_true: np.ndarray, probabilities: np.ndarray, threshold: float = 0.5) -> dict[str, float]:
    predictions = (probabilities >= threshold).astype(int)
    metrics = {
        "accuracy": float(accuracy_score(y_true, predictions)),
        "f1": float(f1_score(y_true, predictions, zero_division=0)),
        "precision": float(precision_score(y_true, predictions, zero_division=0)),
        "recall": float(recall_score(y_true, predictions, zero_division=0)),
    }

    unique_labels = np.unique(y_true)
    if len(unique_labels) > 1:
        metrics["roc_auc"] = float(roc_auc_score(y_true, probabilities))
    else:
        metrics["roc_auc"] = 0.5
    return metrics


def regression_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    clipped_predictions = np.clip(y_pred.astype(float), 0.0, 1.0)
    return {
        "mae": float(mean_absolute_error(y_true, clipped_predictions)),
        "rmse": float(np.sqrt(mean_squared_error(y_true, clipped_predictions))),
        "r2": float(r2_score(y_true, clipped_predictions)),
    }


def build_risk_feature_rows(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    spatial_context = build_spatial_feature_context(rows, grid_rows=140, grid_cols=140)
    features: list[dict[str, Any]] = []

    for row in rows:
        hour_of_day = int(np.clip(row["hour_of_day"], 0, 23))
        spatial_features = derive_location_risk_features(
            context=spatial_context,
            latitude=float(row["latitude"]),
            longitude=float(row["longitude"]),
            fallback_city=row["city"],
        )

        crime_frequency_area = max(0.0, float(row["crime_frequency_area"]))
        if crime_frequency_area <= 0.0:
            crime_frequency_area = float(spatial_features["nearby_avg_crime_frequency_area"])

        features.append(
            {
                "crime_frequency_area": crime_frequency_area,
                "hour_of_day": hour_of_day,
                "hour_sin": math.sin((2.0 * math.pi * hour_of_day) / 24.0),
                "hour_cos": math.cos((2.0 * math.pi * hour_of_day) / 24.0),
                "is_night": int(row["is_night"]),
                "crowd_density": row["crowd_density"],
                "lighting_condition": row["lighting_condition"],
                "user_reports_count": int(max(0, row["user_reports_count"])),
                "nearby_incidents": float(spatial_features["nearby_incidents"]),
                "nearby_incidents_log": float(spatial_features["nearby_incidents_log"]),
                "nearby_avg_risk": float(spatial_features["nearby_avg_risk"]),
                "nearby_avg_severity": float(spatial_features["nearby_avg_severity"]),
                "nearby_avg_crime_frequency_area": float(spatial_features["nearby_avg_crime_frequency_area"]),
                "local_density_ratio": float(spatial_features["local_density_ratio"]),
                "city_avg_risk": float(spatial_features["city_avg_risk"]),
                "city_incident_share": float(spatial_features["city_incident_share"]),
            }
        )

    return features


def train_risk_models(rows: list[dict[str, Any]]) -> dict[str, Any]:
    max_training_rows = 30000
    if len(rows) > max_training_rows:
        rng = np.random.default_rng(RANDOM_STATE)
        sampled_indices = rng.choice(len(rows), size=max_training_rows, replace=False)
        sampled_indices.sort()
        working_rows = [rows[int(index)] for index in sampled_indices]
    else:
        working_rows = rows

    y_scores = np.array([float(np.clip(row["risk_score"], 0.0, 1.0)) for row in working_rows], dtype=float)
    risk_threshold = float(np.quantile(y_scores, 0.65))

    features = build_risk_feature_rows(working_rows)

    quantile_edges = np.quantile(y_scores, [0.2, 0.4, 0.6, 0.8])
    if len(np.unique(np.round(quantile_edges, 6))) > 1:
        stratify_labels = np.digitize(y_scores, bins=quantile_edges, right=True)
    else:
        stratify_labels = None

    x_train, x_test, y_train_score, y_test_score = train_test_split(
        features,
        y_scores,
        test_size=0.2,
        random_state=RANDOM_STATE,
        stratify=stratify_labels,
    )

    y_train_cls = np.array([1 if value >= risk_threshold else 0 for value in y_train_score], dtype=int)
    y_test_cls = np.array([1 if value >= risk_threshold else 0 for value in y_test_score], dtype=int)

    moderate_floor = float(np.quantile(y_train_score, 0.40))
    high_floor = float(np.quantile(y_train_score, 0.80))

    logistic_pipeline = Pipeline(
        steps=[
            ("vectorizer", DictVectorizer(sparse=True)),
            (
                "model",
                LogisticRegression(
                    max_iter=600,
                    random_state=RANDOM_STATE,
                    class_weight="balanced",
                    solver="liblinear",
                ),
            ),
        ]
    )
    logistic_pipeline.fit(x_train, y_train_cls)
    logistic_prob = logistic_pipeline.predict_proba(x_test)[:, 1]
    logistic_score_metrics = regression_metrics(y_test_score, logistic_prob)
    logistic_classification = classification_metrics(y_test_cls, logistic_prob)

    gradient_boosting_pipeline = Pipeline(
        steps=[
            ("vectorizer", DictVectorizer(sparse=True)),
            (
                "model",
                GradientBoostingRegressor(
                    random_state=RANDOM_STATE,
                    n_estimators=240,
                    learning_rate=0.05,
                    max_depth=4,
                    subsample=0.85,
                ),
            ),
        ]
    )
    gradient_boosting_pipeline.fit(x_train, y_train_score)
    gradient_boosting_scores = np.clip(gradient_boosting_pipeline.predict(x_test), 0.0, 1.0)
    gradient_boosting_score_metrics = regression_metrics(y_test_score, gradient_boosting_scores)
    gradient_boosting_classification = classification_metrics(y_test_cls, gradient_boosting_scores)

    random_forest_pipeline = Pipeline(
        steps=[
            ("vectorizer", DictVectorizer(sparse=True)),
            (
                "model",
                RandomForestRegressor(
                    n_estimators=120,
                    max_depth=18,
                    min_samples_leaf=4,
                    random_state=RANDOM_STATE,
                    n_jobs=1,
                ),
            ),
        ]
    )
    random_forest_pipeline.fit(x_train, y_train_score)
    random_forest_scores = np.clip(random_forest_pipeline.predict(x_test), 0.0, 1.0)
    random_forest_score_metrics = regression_metrics(y_test_score, random_forest_scores)
    random_forest_classification = classification_metrics(y_test_cls, random_forest_scores)

    candidates = [
        (
            "gradient_boosting_regressor",
            gradient_boosting_pipeline,
            gradient_boosting_score_metrics,
            gradient_boosting_classification,
        ),
        (
            "random_forest_regressor",
            random_forest_pipeline,
            random_forest_score_metrics,
            random_forest_classification,
        ),
    ]
    candidates.sort(key=lambda item: (item[2]["mae"], item[2]["rmse"], -item[2]["r2"]))
    best_name, best_pipeline, best_regression_metrics, best_classification_metrics = candidates[0]

    joblib.dump(
        {
            "pipeline": logistic_pipeline,
            "model_name": "logistic_regression_probability",
            "prediction_mode": "probability",
            "task": "risk_score_regression",
            "target_threshold": risk_threshold,
            "thresholds": {"moderate": moderate_floor, "high": high_floor},
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": {
                "score_regression": logistic_score_metrics,
                "high_risk_classification": logistic_classification,
            },
        },
        MODELS_DIR / "risk_logistic.joblib",
    )
    joblib.dump(
        {
            "pipeline": gradient_boosting_pipeline,
            "model_name": "gradient_boosting_regressor",
            "prediction_mode": "regression",
            "task": "risk_score_regression",
            "target_threshold": risk_threshold,
            "thresholds": {"moderate": moderate_floor, "high": high_floor},
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": {
                "score_regression": gradient_boosting_score_metrics,
                "high_risk_classification": gradient_boosting_classification,
            },
        },
        MODELS_DIR / "risk_gradient_boosting.joblib",
    )
    joblib.dump(
        {
            "pipeline": random_forest_pipeline,
            "model_name": "random_forest_regressor",
            "prediction_mode": "regression",
            "task": "risk_score_regression",
            "target_threshold": risk_threshold,
            "thresholds": {"moderate": moderate_floor, "high": high_floor},
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": {
                "score_regression": random_forest_score_metrics,
                "high_risk_classification": random_forest_classification,
            },
        },
        MODELS_DIR / "risk_random_forest.joblib",
    )
    joblib.dump(
        {
            "pipeline": best_pipeline,
            "model_name": best_name,
            "prediction_mode": "regression",
            "task": "risk_score_regression",
            "target_threshold": risk_threshold,
            "thresholds": {"moderate": moderate_floor, "high": high_floor},
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": {
                "score_regression": best_regression_metrics,
                "high_risk_classification": best_classification_metrics,
            },
        },
        MODELS_DIR / "risk_best.joblib",
    )

    return {
        "target_threshold": round(risk_threshold, 4),
        "score_thresholds": {
            "moderate": round(moderate_floor, 4),
            "high": round(high_floor, 4),
        },
        "feature_engineering": {
            "spatial_grid": "3x3 neighborhood over 140x140 geo buckets",
            "added_features": [
                "nearby_incidents",
                "nearby_incidents_log",
                "nearby_avg_risk",
                "nearby_avg_severity",
                "nearby_avg_crime_frequency_area",
                "local_density_ratio",
                "city_avg_risk",
                "city_incident_share",
                "hour_sin",
                "hour_cos",
            ],
        },
        "logistic_high_risk_baseline": {
            "score_regression": logistic_score_metrics,
            "high_risk_classification": logistic_classification,
        },
        "gradient_boosting_regressor": {
            "score_regression": gradient_boosting_score_metrics,
            "high_risk_classification": gradient_boosting_classification,
        },
        "random_forest_regressor": {
            "score_regression": random_forest_score_metrics,
            "high_risk_classification": random_forest_classification,
        },
        "best_model": best_name,
        "best_model_mode": "regression",
        "best_model_metrics": {
            "score_regression": best_regression_metrics,
            "high_risk_classification": best_classification_metrics,
        },
        "source_rows": len(rows),
        "used_rows": len(working_rows),
        "sampling_applied": len(working_rows) != len(rows),
        "train_size": len(x_train),
        "test_size": len(x_test),
    }


def compare_heatmap_clustering(rows: list[dict[str, Any]]) -> dict[str, Any]:
    coordinates = np.array([[row["latitude"], row["longitude"]] for row in rows], dtype=float)
    if len(coordinates) > 6000:
        rng = np.random.default_rng(RANDOM_STATE)
        sampled_indices = rng.choice(len(coordinates), size=6000, replace=False)
        coordinates = coordinates[sampled_indices]

    scaled = StandardScaler().fit_transform(coordinates)

    k_value = int(np.clip(np.sqrt(len(scaled) / 2), 8, 24))
    kmeans = KMeans(n_clusters=k_value, random_state=RANDOM_STATE, n_init=10)
    kmeans_labels = kmeans.fit_predict(scaled)
    kmeans_silhouette = float(silhouette_score(scaled, kmeans_labels)) if len(np.unique(kmeans_labels)) > 1 else 0.0

    coords_radians = np.radians(coordinates)
    dbscan = DBSCAN(eps=850 / 6_371_000, min_samples=16, metric="haversine")
    dbscan_labels = dbscan.fit_predict(coords_radians)

    clustered_mask = dbscan_labels != -1
    clustered_points = scaled[clustered_mask]
    clustered_labels = dbscan_labels[clustered_mask]

    if len(clustered_points) > 10 and len(np.unique(clustered_labels)) > 1:
        dbscan_silhouette = float(silhouette_score(clustered_points, clustered_labels))
    else:
        dbscan_silhouette = 0.0

    noise_ratio = float(np.mean(dbscan_labels == -1)) if len(dbscan_labels) else 1.0
    dbscan_cluster_count = int(len(set(dbscan_labels)) - (1 if -1 in dbscan_labels else 0))

    if dbscan_silhouette >= (kmeans_silhouette * 0.95) and noise_ratio <= 0.35:
        recommended = "dbscan"
    else:
        recommended = "kmeans"

    return {
        "kmeans": {
            "clusters": k_value,
            "silhouette": round(kmeans_silhouette, 4),
        },
        "dbscan": {
            "clusters": dbscan_cluster_count,
            "silhouette": round(dbscan_silhouette, 4),
            "noise_ratio": round(noise_ratio, 4),
            "eps_meters": 850,
            "min_samples": 16,
        },
        "recommended": recommended,
        "risk_zone_labeling": "quantile bands on cluster risk_score: top 20% high, next 40% moderate, remaining low",
    }


def train_anomaly_model(rows: list[dict[str, Any]]) -> dict[str, Any]:
    city_centroids: dict[str, tuple[float, float]] = {}
    grouped: dict[str, list[tuple[float, float]]] = defaultdict(list)
    for row in rows:
        grouped[row["city"]].append((row["latitude"], row["longitude"]))

    for city, points in grouped.items():
        city_centroids[city] = (
            float(np.mean([point[0] for point in points])),
            float(np.mean([point[1] for point in points])),
        )

    def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        radius_km = 6371.0
        p1 = np.radians(lat1)
        p2 = np.radians(lat2)
        d1 = np.radians(lat2 - lat1)
        d2 = np.radians(lon2 - lon1)
        a = np.sin(d1 / 2) ** 2 + np.cos(p1) * np.cos(p2) * np.sin(d2 / 2) ** 2
        c = 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))
        return float(radius_km * c)

    deviation_values: list[float] = []
    speed_values: list[float] = []
    matrix_rows: list[list[float]] = []

    for row in rows:
        centroid = city_centroids[row["city"]]
        deviation_km = haversine_km(row["latitude"], row["longitude"], centroid[0], centroid[1])
        crowd_bonus = {"low": 1.0, "medium": 3.0, "high": 6.0}[row["crowd_density"]]
        speed_proxy_kmph = 10.0 + (row["severity_level"] * 5.2) + crowd_bonus + (3.0 if row["is_night"] else 0.0)

        deviation_values.append(deviation_km)
        speed_values.append(speed_proxy_kmph)
        matrix_rows.append([deviation_km, speed_proxy_kmph, float(row["hour_of_day"])])

    x_data = np.array(matrix_rows, dtype=float)

    risk_threshold = float(np.quantile([row["risk_score"] for row in rows], 0.72))
    deviation_threshold = float(np.quantile(deviation_values, 0.85))
    speed_threshold = float(np.quantile(speed_values, 0.90))

    pseudo_labels = np.array(
        [
            1
            if row["risk_score"] >= risk_threshold and deviation >= deviation_threshold
            else 0
            for row, deviation in zip(rows, deviation_values)
        ],
        dtype=int,
    )

    x_train, x_test, _, y_test = train_test_split(
        x_data,
        pseudo_labels,
        test_size=0.2,
        random_state=RANDOM_STATE,
        stratify=pseudo_labels,
    )

    anomaly_pipeline = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            (
                "model",
                IsolationForest(
                    n_estimators=320,
                    contamination=0.12,
                    random_state=RANDOM_STATE,
                ),
            ),
        ]
    )
    anomaly_pipeline.fit(x_train)

    predicted_labels = anomaly_pipeline.predict(x_test)
    predicted_anomalies = np.where(predicted_labels == -1, 1, 0)

    metrics = {
        "precision": float(precision_score(y_test, predicted_anomalies, zero_division=0)),
        "recall": float(recall_score(y_test, predicted_anomalies, zero_division=0)),
        "f1": float(f1_score(y_test, predicted_anomalies, zero_division=0)),
        "estimated_anomaly_rate": float(np.mean(predicted_anomalies)),
    }

    fallback = {
        "deviation_km": round(max(1.4, deviation_threshold), 3),
        "speed_kmph": round(max(36.0, speed_threshold), 2),
    }

    joblib.dump(
        {
            "pipeline": anomaly_pipeline,
            "model_name": "isolation_forest",
            "fallback": fallback,
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": metrics,
        },
        MODELS_DIR / "anomaly_isolation_forest.joblib",
    )

    return {
        "route_baseline_metric": "distance from city centroid (km)",
        "deviation_threshold_km": fallback["deviation_km"],
        "speed_fallback_threshold_kmph": fallback["speed_kmph"],
        "metrics": {key: round(value, 4) for key, value in metrics.items()},
        "train_size": int(len(x_train)),
        "test_size": int(len(x_test)),
    }


def train_proactive_alert_model(rows: list[dict[str, Any]]) -> dict[str, Any]:
    risk_threshold = float(np.quantile([row["risk_score"] for row in rows], 0.65))

    by_city: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        by_city[row["city"]].append(row)

    examples: list[dict[str, Any]] = []
    labels: list[int] = []
    for city_rows in by_city.values():
        ordered = sorted(city_rows, key=lambda item: item["timestamp"])
        for index in range(len(ordered) - 1):
            current = ordered[index]
            next_item = ordered[index + 1]
            examples.append(
                {
                    "city": current["city"],
                    "crime_frequency_area": current["crime_frequency_area"],
                    "hour_of_day": current["hour_of_day"],
                    "is_night": current["is_night"],
                    "is_weekend": current["is_weekend"],
                    "crowd_density": current["crowd_density"],
                    "lighting_condition": current["lighting_condition"],
                }
            )
            labels.append(1 if next_item["risk_score"] >= risk_threshold else 0)

    x_train, x_test, y_train, y_test = train_test_split(
        examples,
        np.array(labels, dtype=int),
        test_size=0.2,
        random_state=RANDOM_STATE,
        stratify=np.array(labels, dtype=int),
    )

    proactive_pipeline = Pipeline(
        steps=[
            ("vectorizer", DictVectorizer(sparse=True)),
            (
                "model",
                LogisticRegression(
                    max_iter=1000,
                    random_state=RANDOM_STATE,
                    class_weight="balanced",
                    solver="liblinear",
                ),
            ),
        ]
    )
    proactive_pipeline.fit(x_train, y_train)

    proactive_probabilities = proactive_pipeline.predict_proba(x_test)[:, 1]
    proactive_metrics = classification_metrics(y_test, proactive_probabilities)

    joblib.dump(
        {
            "pipeline": proactive_pipeline,
            "model_name": "proactive_logistic_regression",
            "target_threshold": risk_threshold,
            "thresholds": {"moderate": 0.45, "high": 0.7},
            "trained_at": datetime.utcnow().isoformat(),
            "metrics": proactive_metrics,
        },
        MODELS_DIR / "proactive_alert_model.joblib",
    )

    return {
        "target_threshold": round(risk_threshold, 4),
        "metrics": proactive_metrics,
        "train_size": len(x_train),
        "test_size": len(x_test),
        "dataset_strategy": "city-ordered events with one-step-ahead high-risk target",
    }


def write_metrics_markdown(metrics: dict[str, Any]) -> None:
    risk = metrics["area_risk_prediction"]
    heatmap = metrics["danger_heatmap_comparison"]
    anomaly = metrics["behavior_anomaly_detection"]
    proactive = metrics["predictive_unsafe_alerts"]

    markdown = f"""# AI Model Training Metrics

This report is generated by `backend/train_ai_models.py`.

## Area Risk Prediction

- Risk threshold (for high-risk classification view): `{risk['target_threshold']}`
- Score thresholds:
    - Moderate: `{risk['score_thresholds']['moderate']}`
    - High: `{risk['score_thresholds']['high']}`
- Spatial feature engineering: {risk['feature_engineering']['spatial_grid']}
- Added features: {', '.join(risk['feature_engineering']['added_features'])}
- Logistic high-risk baseline:
    - AUC `{risk['logistic_high_risk_baseline']['high_risk_classification']['roc_auc']:.4f}`
    - F1 `{risk['logistic_high_risk_baseline']['high_risk_classification']['f1']:.4f}`
- Gradient Boosting Regressor:
    - Score MAE `{risk['gradient_boosting_regressor']['score_regression']['mae']:.4f}`
    - Score RMSE `{risk['gradient_boosting_regressor']['score_regression']['rmse']:.4f}`
    - Score R2 `{risk['gradient_boosting_regressor']['score_regression']['r2']:.4f}`
- Random Forest Regressor:
    - Score MAE `{risk['random_forest_regressor']['score_regression']['mae']:.4f}`
    - Score RMSE `{risk['random_forest_regressor']['score_regression']['rmse']:.4f}`
    - Score R2 `{risk['random_forest_regressor']['score_regression']['r2']:.4f}`
- Selected model: `{risk['best_model']}` (`{risk['best_model_mode']}` mode)

## Danger Heatmap Comparison

- K-Means silhouette: `{heatmap['kmeans']['silhouette']}` with `{heatmap['kmeans']['clusters']}` clusters
- DBSCAN silhouette: `{heatmap['dbscan']['silhouette']}` with noise ratio `{heatmap['dbscan']['noise_ratio']}`
- Recommended clustering approach: `{heatmap['recommended']}`
- Zone labels policy: {heatmap['risk_zone_labeling']}

## Behavior Anomaly Detection

- Route baseline metric: {anomaly['route_baseline_metric']}
- Isolation Forest F1: `{anomaly['metrics']['f1']}`
- Fallback thresholds:
  - Deviation: `{anomaly['deviation_threshold_km']} km`
  - Speed: `{anomaly['speed_fallback_threshold_kmph']} km/h`

## Predictive Unsafe Area Alerts

- Training strategy: {proactive['dataset_strategy']}
- AUC: `{proactive['metrics']['roc_auc']:.4f}`
- F1: `{proactive['metrics']['f1']:.4f}`

## Generated Artifacts

- `backend/models/risk_logistic.joblib`
- `backend/models/risk_gradient_boosting.joblib`
- `backend/models/risk_random_forest.joblib`
- `backend/models/risk_best.joblib`
- `backend/models/anomaly_isolation_forest.joblib`
- `backend/models/proactive_alert_model.joblib`
- `backend/models/training_metrics.json`
"""

    METRICS_MD_PATH.write_text(markdown, encoding="utf-8")


def main() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    rows = load_rows()
    if not rows:
        raise RuntimeError("No usable rows found in dataset for model training")

    results = {
        "generated_at_utc": datetime.utcnow().isoformat(),
        "dataset": {
            "file": str(DATASET_PATH.relative_to(ROOT_DIR)),
            "rows": len(rows),
        },
        "area_risk_prediction": train_risk_models(rows),
        "danger_heatmap_comparison": compare_heatmap_clustering(rows),
        "behavior_anomaly_detection": train_anomaly_model(rows),
        "predictive_unsafe_alerts": train_proactive_alert_model(rows),
    }

    METRICS_JSON_PATH.write_text(json.dumps(results, indent=2), encoding="utf-8")
    write_metrics_markdown(results)

    print("Training complete.")
    print(f"Metrics JSON: {METRICS_JSON_PATH}")
    print(f"Metrics Markdown: {METRICS_MD_PATH}")


if __name__ == "__main__":
    main()
