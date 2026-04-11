from __future__ import annotations

import math
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib

PROJECT_ROOT = Path(__file__).resolve().parents[2]
MODELS_DIR = PROJECT_ROOT / "backend" / "models"


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


def probability_to_level(probability: float, moderate_floor: float = 0.45, high_floor: float = 0.7) -> str:
    bounded = max(0.0, min(1.0, float(probability)))
    if bounded >= high_floor:
        return "high"
    if bounded >= moderate_floor:
        return "moderate"
    return "low"


@lru_cache(maxsize=32)
def _load_model_bundle_cached(model_name: str, modified_time_ns: int) -> dict[str, Any] | None:
    _ = modified_time_ns
    model_path = MODELS_DIR / model_name

    try:
        bundle = joblib.load(model_path)
    except Exception:
        return None

    if isinstance(bundle, dict):
        return bundle
    return None


def _load_model_bundle(model_name: str) -> dict[str, Any] | None:
    model_path = MODELS_DIR / model_name
    if not model_path.exists():
        return None

    return _load_model_bundle_cached(model_name, model_path.stat().st_mtime_ns)


def _fallback_risk_score(
    crime_frequency_area: float,
    hour_of_day: int,
    crowd_density: str,
    lighting_condition: str,
    user_reports_count: int,
) -> float:
    crowd_weight = {"low": 0.10, "medium": 0.18, "high": 0.28}[crowd_density]
    lighting_weight = {"good": 0.06, "moderate": 0.13, "poor": 0.23}[lighting_condition]
    is_night = 1.0 if hour_of_day >= 20 or hour_of_day <= 5 else 0.0

    base = min(max(crime_frequency_area, 0.0), 20.0) / 20.0
    report_signal = min(max(user_reports_count, 0), 40) / 40.0

    score = (
        0.44 * base
        + 0.16 * is_night
        + crowd_weight
        + lighting_weight
        + 0.16 * report_signal
    )
    return max(0.01, min(0.99, score))


def _coerce_feature_value(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        normalized = value.strip()
        return normalized if normalized else None
    return None


def predict_area_risk(
    crime_frequency_area: float,
    hour_of_day: int,
    crowd_density: str,
    lighting_condition: str,
    user_reports_count: int,
    extra_features: dict[str, Any] | None = None,
) -> dict[str, Any]:
    normalized_hour = int(max(0, min(23, hour_of_day)))
    features = {
        "crime_frequency_area": float(crime_frequency_area),
        "hour_of_day": normalized_hour,
        "is_night": 1 if normalized_hour >= 20 or normalized_hour <= 5 else 0,
        "crowd_density": normalize_crowd_density(crowd_density),
        "lighting_condition": normalize_lighting(lighting_condition),
        "user_reports_count": int(max(0, user_reports_count)),
    }

    if extra_features:
        for key, value in extra_features.items():
            coerced_value = _coerce_feature_value(value)
            if coerced_value is None:
                continue
            if key == "crowd_density" and isinstance(coerced_value, str):
                features[key] = normalize_crowd_density(coerced_value)
            elif key == "lighting_condition" and isinstance(coerced_value, str):
                features[key] = normalize_lighting(coerced_value)
            else:
                features[key] = coerced_value

    bundle = _load_model_bundle("risk_best.joblib")
    if bundle and bundle.get("pipeline") is not None:
        pipeline = bundle["pipeline"]
        prediction_mode = str(bundle.get("prediction_mode", "probability"))
        if prediction_mode == "probability" and hasattr(pipeline, "predict_proba"):
            risk_score = float(pipeline.predict_proba([features])[0][1])
        else:
            risk_score = float(pipeline.predict([features])[0])
        risk_score = max(0.0, min(1.0, risk_score))
        model_name = str(bundle.get("model_name", "risk_model"))
        thresholds = bundle.get("thresholds", {})
        moderate_floor = float(thresholds.get("moderate", 0.45))
        high_floor = float(thresholds.get("high", 0.7))
        model_ready = True
    else:
        risk_score = _fallback_risk_score(
            crime_frequency_area=features["crime_frequency_area"],
            hour_of_day=features["hour_of_day"],
            crowd_density=features["crowd_density"],
            lighting_condition=features["lighting_condition"],
            user_reports_count=features["user_reports_count"],
        )
        model_name = "fallback_heuristic"
        moderate_floor = 0.45
        high_floor = 0.7
        model_ready = False

    risk_percent = risk_score * 100.0
    risk_level = probability_to_level(risk_score, moderate_floor, high_floor)
    return {
        "risk_score": round(risk_score, 4),
        "risk_probability": round(risk_score, 4),
        "risk_percent": round(risk_percent, 2),
        "risk_level": risk_level,
        "model_name": model_name,
        "model_ready": model_ready,
    }


def predict_anomaly_signal(deviation_km: float, speed_kmph: float, hour_of_day: int) -> dict[str, Any]:
    feature_row = [[float(deviation_km), float(speed_kmph), float(hour_of_day)]]

    bundle = _load_model_bundle("anomaly_isolation_forest.joblib")
    fallback = {
        "deviation_km": 1.8,
        "speed_kmph": 42.0,
    }

    if bundle:
        fallback.update(bundle.get("fallback", {}))

    fallback_triggered = deviation_km >= float(fallback["deviation_km"]) or speed_kmph >= float(fallback["speed_kmph"])

    if bundle and bundle.get("pipeline") is not None:
        pipeline = bundle["pipeline"]
        prediction = int(pipeline.predict(feature_row)[0])
        raw_score = float(-pipeline.decision_function(feature_row)[0])
        model_triggered = prediction == -1
        is_anomaly = model_triggered or fallback_triggered
        model_name = "isolation_forest"
        model_ready = True
    else:
        raw_score = max(0.0, deviation_km / max(float(fallback["deviation_km"]), 0.1))
        model_triggered = False
        is_anomaly = fallback_triggered
        model_name = "threshold_fallback"
        model_ready = False

    return {
        "is_anomaly": bool(is_anomaly),
        "anomaly_score": round(raw_score, 4),
        "fallback_triggered": bool(fallback_triggered),
        "model_triggered": bool(model_triggered),
        "model_name": model_name,
        "model_ready": model_ready,
        "fallback_deviation_km": round(float(fallback["deviation_km"]), 3),
        "fallback_speed_kmph": round(float(fallback["speed_kmph"]), 1),
    }


def _fallback_proactive_probability(
    crime_frequency_area: float,
    hour_of_day: int,
    is_weekend: int,
    crowd_density: str,
    lighting_condition: str,
) -> float:
    night_factor = 0.22 if hour_of_day >= 20 or hour_of_day <= 5 else 0.08
    weekend_factor = 0.11 if is_weekend else 0.03
    crowd_factor = {"low": 0.05, "medium": 0.11, "high": 0.19}[crowd_density]
    lighting_factor = {"good": 0.04, "moderate": 0.09, "poor": 0.16}[lighting_condition]
    base = min(max(crime_frequency_area, 0.0), 20.0) / 20.0

    probability = (0.42 * base) + night_factor + weekend_factor + crowd_factor + lighting_factor
    return max(0.01, min(0.99, probability))


def predict_proactive_risk(
    city: str,
    crime_frequency_area: float,
    hour_of_day: int,
    is_weekend: int,
    crowd_density: str,
    lighting_condition: str,
) -> dict[str, Any]:
    features = {
        "city": (city or "Unknown").strip() or "Unknown",
        "crime_frequency_area": float(crime_frequency_area),
        "hour_of_day": int(hour_of_day),
        "is_night": 1 if hour_of_day >= 20 or hour_of_day <= 5 else 0,
        "is_weekend": int(1 if is_weekend else 0),
        "crowd_density": normalize_crowd_density(crowd_density),
        "lighting_condition": normalize_lighting(lighting_condition),
    }

    bundle = _load_model_bundle("proactive_alert_model.joblib")
    if bundle and bundle.get("pipeline") is not None:
        pipeline = bundle["pipeline"]
        probability = float(pipeline.predict_proba([features])[0][1])
        model_name = str(bundle.get("model_name", "proactive_model"))
        thresholds = bundle.get("thresholds", {})
        moderate_floor = float(thresholds.get("moderate", 0.45))
        high_floor = float(thresholds.get("high", 0.7))
        model_ready = True
    else:
        probability = _fallback_proactive_probability(
            crime_frequency_area=features["crime_frequency_area"],
            hour_of_day=features["hour_of_day"],
            is_weekend=features["is_weekend"],
            crowd_density=features["crowd_density"],
            lighting_condition=features["lighting_condition"],
        )
        model_name = "fallback_heuristic"
        moderate_floor = 0.45
        high_floor = 0.7
        model_ready = False

    risk_level = probability_to_level(probability, moderate_floor, high_floor)
    return {
        "risk_probability": round(probability, 4),
        "risk_level": risk_level,
        "model_name": model_name,
        "model_ready": model_ready,
    }


def estimate_route_centroid(route_points: list[dict[str, float]]) -> tuple[float, float] | None:
    if not route_points:
        return None

    latitudes = [float(point["latitude"]) for point in route_points]
    longitudes = [float(point["longitude"]) for point in route_points]
    return (sum(latitudes) / len(latitudes), sum(longitudes) / len(longitudes))


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_km = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return radius_km * c
