from __future__ import annotations

import csv
import json
import math
import os
import random
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from statistics import mean

from app.ai_models import (
    estimate_route_centroid,
    haversine_km,
    normalize_crowd_density,
    normalize_lighting,
    probability_to_level,
    predict_anomaly_signal,
    predict_area_risk,
    predict_proactive_risk,
)
from app.spatial_features import (
    build_spatial_feature_context,
    derive_location_risk_features,
    distance_to_bounds_km,
)
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from pydantic import BaseModel, Field
from app.auth.db import (
    DB_BACKEND,
    DB_PATH,
    DB_PATH_IS_PERSISTENT,
    DB_PATH_SOURCE,
    RENDER_RUNTIME,
    STRICT_DB_PATH,
    get_connection,
    init_auth_db,
)
from app.auth.routes import router as auth_router

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _resolve_dataset_path() -> Path:
    env_path = os.getenv("SMARTCOMMUNITY_DATASET_PATH", "").strip()
    if env_path:
        configured = Path(env_path).expanduser()
        if configured.exists():
            return configured

    candidates = [
        PROJECT_ROOT / "datasets" / "india_crime_dataset.csv",
        PROJECT_ROOT / "backend" / "datasets" / "india_crime_dataset.csv",
        Path.cwd() / "datasets" / "india_crime_dataset.csv",
        Path.cwd().parent / "datasets" / "india_crime_dataset.csv",
    ]

    for candidate in candidates:
        if candidate.exists():
            return candidate

    # Fallback path so responses still include a deterministic dataset file name.
    return candidates[0]


DATASET_PATH = _resolve_dataset_path()

app = FastAPI(
    title="Smart Community SOS API",
    version="1.1.0",
    description="Backend API for map and heatmap data plus secure user authentication."
)

init_auth_db()
app.include_router(auth_router)

allowed_origins_value = os.getenv("ALLOWED_ORIGINS", "*").strip()
allowed_origins = [origin.strip() for origin in allowed_origins_value.split(",") if origin.strip()]
if not allowed_origins:
    allowed_origins = ["*"]

app.add_middleware(GZipMiddleware, minimum_size=1000)
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=("*" not in allowed_origins),
    allow_methods=["*"],
    allow_headers=["*"],
)

USER_TTL_SECONDS = 120
SOS_TTL_SECONDS = 900
ACTIVE_USERS: dict[str, dict] = {}
SOS_EVENTS: list[dict] = []
HELPER_LEADERBOARD_STATS: dict[str, dict] = {}
FCM_SERVER_KEY = os.getenv("FCM_SERVER_KEY", "").strip()
RISK_LEVEL_MODERATE_FLOOR = 0.46
RISK_LEVEL_HIGH_FLOOR = 0.63


def _read_bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _read_int_env(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw.strip())
    except ValueError:
        return default


LOW_MEMORY_MODE = _read_bool_env("SMARTCOMMUNITY_LOW_MEMORY_MODE", False)
MAX_INCIDENTS_IN_MEMORY = _read_int_env(
    "SMARTCOMMUNITY_MAX_INCIDENTS",
    30000 if LOW_MEMORY_MODE else 0,
)
ENABLE_DBSCAN = _read_bool_env(
    "SMARTCOMMUNITY_ENABLE_DBSCAN",
    not LOW_MEMORY_MODE,
)
MAX_HEATMAP_POINTS_RESPONSE = _read_int_env(
    "SMARTCOMMUNITY_MAX_HEATMAP_POINTS",
    20000 if LOW_MEMORY_MODE else 0,
)
INCIDENTS_SOURCE_COUNT = 0


class UserHeartbeat(BaseModel):
    user_id: str = Field(min_length=1, max_length=120)
    latitude: float
    longitude: float
    location_label: str | None = None
    fcm_token: str | None = None


class SosRequest(BaseModel):
    requester_id: str = Field(min_length=1, max_length=120)
    latitude: float
    longitude: float
    location_label: str | None = None
    message: str = Field(default="Emergency help needed nearby")
    radius_m: int = Field(default=1000, ge=100, le=5000)


class SosResponse(BaseModel):
    event_id: str = Field(min_length=1)
    responder_id: str = Field(min_length=1, max_length=120)
    response: str = Field(pattern="^(accept|decline)$")


class SosCancelRequest(BaseModel):
    event_id: str = Field(min_length=1)
    requester_id: str = Field(min_length=1, max_length=120)


class SosQuickUpdateRequest(BaseModel):
    event_id: str = Field(min_length=1)
    requester_id: str = Field(min_length=1, max_length=120)
    update_code: str = Field(min_length=2, max_length=80)
    update_message: str = Field(min_length=3, max_length=220)


class SosAcknowledgeRequest(BaseModel):
    event_id: str = Field(min_length=1)
    requester_id: str = Field(min_length=1, max_length=120)
    helper_id: str = Field(min_length=1, max_length=120)


class FcmTokenRegistration(BaseModel):
    user_id: str = Field(min_length=1, max_length=120)
    fcm_token: str = Field(min_length=20, max_length=600)


class AreaRiskPredictionRequest(BaseModel):
    latitude: float | None = Field(default=None, ge=-90.0, le=90.0)
    longitude: float | None = Field(default=None, ge=-180.0, le=180.0)
    crime_frequency_area: float = Field(default=0.0, ge=0.0)
    hour_of_day: int = Field(default=12, ge=0, le=23)
    crowd_density: str = Field(default="medium")
    lighting_condition: str = Field(default="moderate")
    user_reports: int = Field(default=0, ge=0)
    user_reports_count: int | None = Field(default=None, ge=0)


class RoutePoint(BaseModel):
    latitude: float
    longitude: float


class AnomalyCheckRequest(BaseModel):
    current_latitude: float
    current_longitude: float
    speed_kmph: float = Field(default=0.0, ge=0.0, le=240.0)
    hour_of_day: int = Field(default=12, ge=0, le=23)
    usual_route: list[RoutePoint] = Field(default_factory=list)


class ProactiveAlertRequest(BaseModel):
    upcoming_latitude: float
    upcoming_longitude: float
    hour_of_day: int = Field(default=20, ge=0, le=23)
    day_of_week: str = Field(default="Monday")
    crowd_density: str | None = Field(default=None)
    lighting_condition: str | None = Field(default=None)


def _now_ts() -> float:
    return time.time()


def _haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius_earth_m = 6_371_000
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lon2 - lon1)

    a = (
        math.sin(delta_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return radius_earth_m * c


def _prune_presence_and_events() -> None:
    now = _now_ts()

    expired_users = [
        user_id
        for user_id, details in ACTIVE_USERS.items()
        if now - details["updated_at"] > USER_TTL_SECONDS
    ]
    for user_id in expired_users:
        ACTIVE_USERS.pop(user_id, None)

    SOS_EVENTS[:] = [
        event for event in SOS_EVENTS
        if now - event["created_at"] <= SOS_TTL_SECONDS
    ]


def _send_fcm_notification(tokens: list[str], title: str, body: str, data: dict[str, str]) -> None:
    if not FCM_SERVER_KEY or not tokens:
        return

    endpoint = "https://fcm.googleapis.com/fcm/send"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"key={FCM_SERVER_KEY}",
    }

    payload = {
        "registration_ids": tokens,
        "priority": "high",
        "notification": {
            "title": title,
            "body": body,
            "sound": "default",
        },
        "data": data,
    }

    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            _ = response.read()
    except (urllib.error.URLError, TimeoutError):
        # Push is best-effort; local polling still works if FCM delivery fails.
        return


def _to_float(value: str, fallback: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return fallback


def _ensure_helper_stats(user_id: str) -> dict:
    return HELPER_LEADERBOARD_STATS.setdefault(
        user_id,
        {
            "accepted_help_count": 0,
            "acknowledged_help_count": 0,
            "trophies": 0,
            "last_acknowledged_at": 0.0,
        },
    )


def _build_helper_leaderboard() -> tuple[str | None, list[dict]]:
    known_user_ids: set[str] = set(ACTIVE_USERS.keys()) | set(HELPER_LEADERBOARD_STATS.keys())

    for event in SOS_EVENTS:
        requester_id = event.get("requester_id")
        if requester_id:
            known_user_ids.add(requester_id)
        for responder_id in (event.get("responses") or {}).keys():
            known_user_ids.add(responder_id)

    try:
        with get_connection() as connection:
            rows = connection.execute("SELECT username FROM users").fetchall()
            for row in rows:
                username = row["username"]
                if username:
                    known_user_ids.add(str(username))
    except Exception:
        # Leaderboard should still work with in-memory users if DB access fails.
        pass

    leaderboard: list[dict] = []
    for user_id in known_user_ids:
        stats = HELPER_LEADERBOARD_STATS.get(user_id, {})
        leaderboard.append(
            {
                "user_id": user_id,
                "accepted_help_count": int(stats.get("accepted_help_count", 0)),
                "acknowledged_help_count": int(stats.get("acknowledged_help_count", 0)),
                "trophies": int(stats.get("trophies", 0)),
                "last_acknowledged_at": float(stats.get("last_acknowledged_at", 0.0)),
            }
        )

    leaderboard.sort(
        key=lambda item: (
            -item["acknowledged_help_count"],
            -item["trophies"],
            -item["accepted_help_count"],
            -item["last_acknowledged_at"],
            item["user_id"],
        )
    )

    champion_user_id = None
    if leaderboard and leaderboard[0]["acknowledged_help_count"] > 0:
        champion_user_id = leaderboard[0]["user_id"]

    for index, item in enumerate(leaderboard, start=1):
        item["rank"] = index
        item["is_champion"] = champion_user_id is not None and item["user_id"] == champion_user_id
        item.pop("last_acknowledged_at", None)

    return champion_user_id, leaderboard


@lru_cache(maxsize=1)
def load_incidents() -> list[dict]:
    incidents: list[dict] = []
    global INCIDENTS_SOURCE_COUNT
    INCIDENTS_SOURCE_COUNT = 0

    if not DATASET_PATH.exists():
        return incidents

    max_rows = MAX_INCIDENTS_IN_MEMORY if MAX_INCIDENTS_IN_MEMORY > 0 else None
    reservoir_rng = random.Random(42)

    with DATASET_PATH.open("r", encoding="utf-8", newline="") as csv_file:
        reader = csv.DictReader(csv_file)
        for row in reader:
            latitude = _to_float(row.get("latitude"))
            longitude = _to_float(row.get("longitude"))

            if latitude == 0.0 and longitude == 0.0:
                continue

            risk_score = _to_float(row.get("risk_score"))
            severity_level = _to_float(row.get("severity_level"))
            point_weight = max(risk_score, severity_level / 5.0, 0.15)

            incident = {
                "incident_id": row.get("incident_id"),
                "crime_type": row.get("crime_type", "unknown"),
                "city": row.get("city", "Unknown"),
                "area_name": row.get("area_name", "Unknown"),
                "date": row.get("date"),
                "time": row.get("time"),
                "hour_of_day": int(_to_float(row.get("hour_of_day"))),
                "day_of_week": row.get("day_of_week", "Unknown"),
                "is_weekend": int(_to_float(row.get("is_weekend"))),
                "is_night": int(_to_float(row.get("is_night"))),
                "crowd_density": normalize_crowd_density(row.get("crowd_density")),
                "lighting_condition": normalize_lighting(row.get("lighting_condition")),
                "latitude": latitude,
                "longitude": longitude,
                "risk_score": risk_score,
                "severity_level": severity_level,
                "crime_frequency_area": _to_float(row.get("crime_frequency_area")),
                "user_reports_count": int(_to_float(row.get("user_reports_count"))),
                "verified_reports": int(_to_float(row.get("verified_reports"))),
                "point_weight": round(point_weight, 4),
            }

            INCIDENTS_SOURCE_COUNT += 1

            if max_rows is None:
                incidents.append(incident)
                continue

            if len(incidents) < max_rows:
                incidents.append(incident)
                continue

            replacement_index = reservoir_rng.randint(0, INCIDENTS_SOURCE_COUNT - 1)
            if replacement_index < max_rows:
                incidents[replacement_index] = incident

    return incidents


@lru_cache(maxsize=1)
def city_centroids() -> dict[str, tuple[float, float]]:
    grouped: dict[str, list[tuple[float, float]]] = {}
    for incident in load_incidents():
        grouped.setdefault(incident["city"], []).append((incident["latitude"], incident["longitude"]))

    centroids: dict[str, tuple[float, float]] = {}
    for city, points in grouped.items():
        lat = sum(point[0] for point in points) / max(1, len(points))
        lon = sum(point[1] for point in points) / max(1, len(points))
        centroids[city] = (lat, lon)

    return centroids


@lru_cache(maxsize=1)
def risk_feature_context() -> dict:
    grid_size = 110 if LOW_MEMORY_MODE else 140
    return build_spatial_feature_context(load_incidents(), grid_rows=grid_size, grid_cols=grid_size)


def nearest_zone_prior(latitude: float, longitude: float, max_distance_km: float = 2.0) -> dict | None:
    zones = dbscan_zones().get("zones", [])
    if not zones:
        return None

    nearest = min(
        zones,
        key=lambda zone: haversine_km(
            latitude,
            longitude,
            float(zone["latitude"]),
            float(zone["longitude"]),
        ),
    )
    distance_km = haversine_km(
        latitude,
        longitude,
        float(nearest["latitude"]),
        float(nearest["longitude"]),
    )
    if distance_km > max_distance_km:
        return None

    return {
        "risk_score": float(nearest.get("risk_score", 0.0)),
        "risk_level": str(nearest.get("risk_level", "low")).lower(),
        "incident_count": int(nearest.get("incident_count", 0)),
        "distance_km": round(distance_km, 4),
    }


def nearest_incident(latitude: float, longitude: float) -> dict | None:
    incidents = load_incidents()
    if not incidents:
        return None

    nearest_item = min(
        incidents,
        key=lambda incident: _haversine_meters(
            latitude,
            longitude,
            incident["latitude"],
            incident["longitude"],
        ),
    )
    return nearest_item


def day_of_week_to_is_weekend(day_of_week: str) -> int:
    normalized = (day_of_week or "").strip().lower()
    return 1 if normalized in {"saturday", "sunday"} else 0


def route_baseline_center(route_points: list[RoutePoint], fallback_incident: dict | None) -> tuple[float, float] | None:
    points = [
        {"latitude": point.latitude, "longitude": point.longitude}
        for point in route_points
    ]
    route_center = estimate_route_centroid(points)
    if route_center is not None:
        return route_center

    if fallback_incident is None:
        return None

    city = fallback_incident.get("city", "Unknown")
    centroid = city_centroids().get(city)
    if centroid is not None:
        return centroid

    return (
        float(fallback_incident["latitude"]),
        float(fallback_incident["longitude"]),
    )


@app.post("/api/users/heartbeat")
def users_heartbeat(payload: UserHeartbeat) -> dict:
    _prune_presence_and_events()
    ACTIVE_USERS[payload.user_id] = {
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "location_label": payload.location_label,
        "fcm_token": payload.fcm_token,
        "updated_at": _now_ts(),
    }
    return {
        "status": "ok",
        "active_users": len(ACTIVE_USERS),
    }


@app.get("/api/users/presence")
def user_presence(user_id: str) -> dict:
    _prune_presence_and_events()
    details = ACTIVE_USERS.get(user_id)
    if details is None:
        return {
            "status": "offline",
            "user_id": user_id,
        }

    now = _now_ts()
    return {
        "status": "online",
        "user_id": user_id,
        "latitude": details.get("latitude"),
        "longitude": details.get("longitude"),
        "location_label": details.get("location_label"),
        "updated_at": details.get("updated_at"),
        "age_seconds": int(max(0, now - float(details.get("updated_at", now)))),
    }


@app.post("/api/fcm/token")
def register_fcm_token(payload: FcmTokenRegistration) -> dict:
    _prune_presence_and_events()

    details = ACTIVE_USERS.get(payload.user_id)
    if details is None:
        ACTIVE_USERS[payload.user_id] = {
            "latitude": 0.0,
            "longitude": 0.0,
            "location_label": None,
            "fcm_token": payload.fcm_token,
            "updated_at": _now_ts(),
        }
    else:
        details["fcm_token"] = payload.fcm_token
        details["updated_at"] = _now_ts()

    return {
        "status": "ok",
    }


@app.post("/api/sos/request")
def create_sos_request(payload: SosRequest) -> dict:
    _prune_presence_and_events()

    event_id = str(uuid.uuid4())
    created_at = _now_ts()
    event = {
        "event_id": event_id,
        "requester_id": payload.requester_id,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "location_label": payload.location_label,
        "message": payload.message,
        "radius_m": payload.radius_m,
        "responses": {},
        "acknowledgements": {},
        "accepted_helpers_tallied": set(),
        "quick_updates": [],
        "latest_quick_update": None,
        "created_at": created_at,
        "cancelled": False,
        "cancelled_at": None,
    }
    SOS_EVENTS.append(event)

    recipient_ids: list[str] = []
    recipient_tokens: list[str] = []
    for user_id, details in ACTIVE_USERS.items():
        if user_id == payload.requester_id:
            continue

        distance_m = _haversine_meters(
            payload.latitude,
            payload.longitude,
            details["latitude"],
            details["longitude"],
        )
        if distance_m <= payload.radius_m:
            recipient_ids.append(user_id)
            token = details.get("fcm_token")
            if token:
                recipient_tokens.append(token)

    _send_fcm_notification(
        tokens=recipient_tokens,
        title="Nearby SOS request",
        body=f"Someone nearby needs help within {payload.radius_m}m.",
        data={
            "type": "sos_alert",
            "event_id": event_id,
            "requester_id": payload.requester_id,
            "radius_m": str(payload.radius_m),
            "latitude": str(payload.latitude),
            "longitude": str(payload.longitude),
        },
    )

    return {
        "status": "sent",
        "event_id": event_id,
        "expires_in_seconds": SOS_TTL_SECONDS,
        "notified_users": len(recipient_ids),
        "recipient_user_ids": recipient_ids[:200],
    }


@app.post("/api/sos/respond")
def respond_to_sos(payload: SosResponse) -> dict:
    _prune_presence_and_events()

    for event in SOS_EVENTS:
        if event["event_id"] != payload.event_id:
            continue

        if event.get("cancelled"):
            return {
                "status": "cancelled",
                "event_id": payload.event_id,
            }

        if _now_ts() - event["created_at"] > SOS_TTL_SECONDS:
            return {
                "status": "expired",
                "event_id": payload.event_id,
            }

        responses = event.setdefault("responses", {})

        responses[payload.responder_id] = {
            "response": payload.response,
            "responded_at": _now_ts(),
        }

        if payload.response == "accept":
            accepted_helpers_tallied = event.setdefault("accepted_helpers_tallied", set())
            if payload.responder_id not in accepted_helpers_tallied:
                accepted_helpers_tallied.add(payload.responder_id)
                helper_stats = _ensure_helper_stats(payload.responder_id)
                helper_stats["accepted_help_count"] += 1

        requester_details = ACTIVE_USERS.get(event["requester_id"])
        requester_token = requester_details.get("fcm_token") if requester_details else None
        if requester_token:
            _send_fcm_notification(
                tokens=[requester_token],
                title="SOS response update",
                body=f"{payload.responder_id} selected {payload.response} for your SOS request.",
                data={
                    "type": "sos_response",
                    "event_id": payload.event_id,
                    "responder_id": payload.responder_id,
                    "response": payload.response,
                },
            )

        return {
            "status": "ok",
            "event_id": payload.event_id,
            "responder_id": payload.responder_id,
            "response": payload.response,
        }

    return {
        "status": "not_found",
        "event_id": payload.event_id,
    }


@app.post("/api/sos/cancel")
def cancel_sos(payload: SosCancelRequest) -> dict:
    _prune_presence_and_events()

    for event in SOS_EVENTS:
        if event["event_id"] != payload.event_id:
            continue

        if event["requester_id"] != payload.requester_id:
            return {
                "status": "forbidden",
                "event_id": payload.event_id,
            }

        if event.get("cancelled"):
            return {
                "status": "already_cancelled",
                "event_id": payload.event_id,
            }

        event["cancelled"] = True
        event["cancelled_at"] = _now_ts()

        accepted_count = sum(
            1
            for response in event.get("responses", {}).values()
            if response.get("response") == "accept"
        )

        return {
            "status": "cancelled",
            "event_id": payload.event_id,
            "accepted_count": accepted_count,
        }

    return {
        "status": "not_found",
        "event_id": payload.event_id,
    }


@app.post("/api/sos/quick-update")
def share_sos_quick_update(payload: SosQuickUpdateRequest) -> dict:
    _prune_presence_and_events()
    now = _now_ts()

    for event in SOS_EVENTS:
        if event["event_id"] != payload.event_id:
            continue

        if event["requester_id"] != payload.requester_id:
            return {
                "status": "forbidden",
                "event_id": payload.event_id,
            }

        if event.get("cancelled"):
            return {
                "status": "cancelled",
                "event_id": payload.event_id,
            }

        if now - event["created_at"] > SOS_TTL_SECONDS:
            return {
                "status": "expired",
                "event_id": payload.event_id,
            }

        update_code = payload.update_code.strip().lower()
        update_message = payload.update_message.strip()

        update_record = {
            "update_code": update_code,
            "update_message": update_message,
            "created_at": now,
        }
        event.setdefault("quick_updates", []).append(update_record)
        event["latest_quick_update"] = update_record

        recipient_ids: set[str] = set()
        for responder_id, details in event.get("responses", {}).items():
            if details.get("response") == "accept":
                recipient_ids.add(responder_id)

        event_radius_m = int(event.get("radius_m", 1000))
        for user_id, details in ACTIVE_USERS.items():
            if user_id == payload.requester_id:
                continue

            distance_m = _haversine_meters(
                event["latitude"],
                event["longitude"],
                details["latitude"],
                details["longitude"],
            )
            if distance_m <= event_radius_m:
                recipient_ids.add(user_id)

        recipient_ids.discard(payload.requester_id)

        recipient_tokens: list[str] = []
        for user_id in recipient_ids:
            token = ACTIVE_USERS.get(user_id, {}).get("fcm_token")
            if token:
                recipient_tokens.append(token)

        _send_fcm_notification(
            tokens=recipient_tokens,
            title="SOS update from requester",
            body=update_message,
            data={
                "type": "sos_quick_update",
                "event_id": payload.event_id,
                "requester_id": payload.requester_id,
                "update_code": update_code,
                "update_message": update_message,
            },
        )

        return {
            "status": "ok",
            "event_id": payload.event_id,
            "update_code": update_code,
            "update_message": update_message,
            "notified_users": len(recipient_ids),
            "recipient_user_ids": sorted(recipient_ids)[:200],
        }

    return {
        "status": "not_found",
        "event_id": payload.event_id,
    }


@app.post("/api/sos/acknowledge")
def acknowledge_helper_support(payload: SosAcknowledgeRequest) -> dict:
    _prune_presence_and_events()
    now = _now_ts()

    for event in SOS_EVENTS:
        if event["event_id"] != payload.event_id:
            continue

        if event["requester_id"] != payload.requester_id:
            return {
                "status": "forbidden",
                "event_id": payload.event_id,
            }

        if now - event["created_at"] > SOS_TTL_SECONDS:
            return {
                "status": "expired",
                "event_id": payload.event_id,
            }

        helper_response = (
            event.get("responses", {})
            .get(payload.helper_id, {})
            .get("response")
        )
        if helper_response != "accept":
            return {
                "status": "invalid_helper",
                "event_id": payload.event_id,
                "helper_id": payload.helper_id,
            }

        acknowledgements = event.setdefault("acknowledgements", {})
        if payload.helper_id in acknowledgements:
            existing_stats = _ensure_helper_stats(payload.helper_id)
            return {
                "status": "already_acknowledged",
                "event_id": payload.event_id,
                "helper_id": payload.helper_id,
                "trophies": existing_stats["trophies"],
            }

        acknowledgements[payload.helper_id] = now

        helper_stats = _ensure_helper_stats(payload.helper_id)
        helper_stats["acknowledged_help_count"] += 1
        helper_stats["trophies"] += 1
        helper_stats["last_acknowledged_at"] = now

        helper_token = ACTIVE_USERS.get(payload.helper_id, {}).get("fcm_token")
        if helper_token:
            _send_fcm_notification(
                tokens=[helper_token],
                title="Your help was acknowledged",
                body=f"{payload.requester_id} acknowledged your SOS support.",
                data={
                    "type": "sos_acknowledgement",
                    "event_id": payload.event_id,
                    "requester_id": payload.requester_id,
                    "helper_id": payload.helper_id,
                    "trophies": str(helper_stats["trophies"]),
                },
            )

        return {
            "status": "acknowledged",
            "event_id": payload.event_id,
            "helper_id": payload.helper_id,
            "trophies": helper_stats["trophies"],
            "acknowledged_help_count": helper_stats["acknowledged_help_count"],
        }

    return {
        "status": "not_found",
        "event_id": payload.event_id,
    }


@app.get("/api/sos/requester")
def sos_requester_status(requester_id: str) -> dict:
    _prune_presence_and_events()
    now = _now_ts()

    active_events = [
        event
        for event in SOS_EVENTS
        if event["requester_id"] == requester_id
        and not event.get("cancelled", False)
        and now - event["created_at"] <= SOS_TTL_SECONDS
    ]

    if not active_events:
        return {
            "status": "none",
            "active_event": None,
        }

    event = max(active_events, key=lambda item: item["created_at"])
    responses = event.get("responses", {})
    acknowledgements = event.get("acknowledgements", {})

    helper_accepts = []
    for responder_id, details in responses.items():
        if details.get("response") != "accept":
            continue

        acknowledged_at = acknowledgements.get(responder_id)
        helper_accepts.append(
            {
                "responder_id": responder_id,
                "responded_at": int(details.get("responded_at", now)),
                "acknowledged": responder_id in acknowledgements,
                "acknowledged_at": int(acknowledged_at) if acknowledged_at is not None else None,
            }
        )
    helper_accepts.sort(key=lambda item: item["responded_at"])

    return {
        "status": "active",
        "active_event": {
            "event_id": event["event_id"],
            "message": event["message"],
            "latest_quick_update_message": (
                event.get("latest_quick_update", {}) or {}
            ).get("update_message"),
            "location_label": event.get("location_label"),
            "latitude": event["latitude"],
            "longitude": event["longitude"],
            "radius_m": int(event.get("radius_m", 1000)),
            "age_seconds": int(now - event["created_at"]),
            "expires_in_seconds": max(0, int(SOS_TTL_SECONDS - (now - event["created_at"]))),
            "accepted_count": len(helper_accepts),
            "acknowledged_count": sum(1 for helper in helper_accepts if helper["acknowledged"]),
            "helper_accepts": helper_accepts,
        },
    }


@app.get("/api/sos/nearby")
def sos_nearby(
    user_id: str,
    latitude: float,
    longitude: float,
    radius_m: int = Query(default=1000, ge=100, le=5000),
) -> dict:
    _prune_presence_and_events()
    now = _now_ts()

    alerts = []
    for event in SOS_EVENTS:
        if event["requester_id"] == user_id:
            continue

        if event.get("cancelled"):
            continue

        effective_radius_m = min(radius_m, int(event.get("radius_m", radius_m)))
        distance_m = _haversine_meters(
            latitude,
            longitude,
            event["latitude"],
            event["longitude"],
        )
        if distance_m > effective_radius_m:
            continue

        latest_quick_update = event.get("latest_quick_update", {}) or {}
        quick_update_message = latest_quick_update.get("update_message")
        if quick_update_message:
            message = f"{event['message']} - Update: {quick_update_message}"
        else:
            message = event["message"]

        alerts.append(
            {
                "event_id": event["event_id"],
                "requester_id": event["requester_id"],
                "message": message,
                "quick_update_message": quick_update_message,
                "location_label": event.get("location_label"),
                "latitude": event["latitude"],
                "longitude": event["longitude"],
                "radius_m": int(event.get("radius_m", radius_m)),
                "distance_m": round(distance_m, 1),
                "age_seconds": int(now - event["created_at"]),
                "accepted_count": sum(
                    1
                    for response in event.get("responses", {}).values()
                    if response.get("response") == "accept"
                ),
                "user_response": (
                    event.get("responses", {})
                    .get(user_id, {})
                    .get("response")
                ),
            }
        )

    alerts.sort(key=lambda item: item["distance_m"])
    return {
        "count": len(alerts),
        "alerts": alerts[:50],
    }


@app.get("/api/helpers/leaderboard")
def helper_leaderboard(limit: int = Query(default=100, ge=1, le=500)) -> dict:
    _prune_presence_and_events()
    champion_user_id, leaderboard = _build_helper_leaderboard()

    return {
        "count": min(limit, len(leaderboard)),
        "champion_user_id": champion_user_id,
        "leaderboard": leaderboard[:limit],
    }


@lru_cache(maxsize=1)
def dataset_summary() -> dict:
    incidents = load_incidents()
    source_incidents = INCIDENTS_SOURCE_COUNT or len(incidents)
    if not incidents:
        return {
            "dataset_file": DATASET_PATH.name,
            "total_incidents": 0,
            "loaded_incidents": 0,
            "source_incidents": 0,
            "sampled": False,
            "low_memory_mode": LOW_MEMORY_MODE,
            "average_risk_score": 0.0,
            "top_cities": [],
            "top_crime_types": [],
            "bounds": {
                "min_latitude": 0.0,
                "max_latitude": 0.0,
                "min_longitude": 0.0,
                "max_longitude": 0.0,
            },
        }

    latitudes = [incident["latitude"] for incident in incidents]
    longitudes = [incident["longitude"] for incident in incidents]
    risk_scores = [incident["risk_score"] for incident in incidents]
    cities = Counter(incident["city"] for incident in incidents)
    crime_types = Counter(incident["crime_type"] for incident in incidents)

    return {
        "dataset_file": DATASET_PATH.name,
        "total_incidents": len(incidents),
        "loaded_incidents": len(incidents),
        "source_incidents": source_incidents,
        "sampled": source_incidents > len(incidents),
        "low_memory_mode": LOW_MEMORY_MODE,
        "average_risk_score": round(mean(risk_scores), 3),
        "top_cities": [
            {"city": city, "count": count}
            for city, count in cities.most_common(5)
        ],
        "top_crime_types": [
            {"crime_type": crime_type, "count": count}
            for crime_type, count in crime_types.most_common(5)
        ],
        "bounds": {
            "min_latitude": min(latitudes),
            "max_latitude": max(latitudes),
            "min_longitude": min(longitudes),
            "max_longitude": max(longitudes),
        },
    }


@lru_cache(maxsize=1)
def heatmap_grid(rows: int = 120, cols: int = 120) -> dict:
    incidents = load_incidents()
    bounds = dataset_summary()["bounds"]

    min_latitude = bounds["min_latitude"]
    max_latitude = bounds["max_latitude"]
    min_longitude = bounds["min_longitude"]
    max_longitude = bounds["max_longitude"]

    lat_span = max(max_latitude - min_latitude, 0.0001)
    lng_span = max(max_longitude - min_longitude, 0.0001)
    buckets: dict[tuple[int, int], dict] = {}

    for incident in incidents:
        lat_ratio = (incident["latitude"] - min_latitude) / lat_span
        lng_ratio = (incident["longitude"] - min_longitude) / lng_span

        row_index = min(rows - 1, max(0, int(lat_ratio * rows)))
        col_index = min(cols - 1, max(0, int(lng_ratio * cols)))
        key = (row_index, col_index)

        if key not in buckets:
            buckets[key] = {
                "sum_latitude": 0.0,
                "sum_longitude": 0.0,
                "weight": 0.0,
                "count": 0,
            }

        bucket = buckets[key]
        bucket["sum_latitude"] += incident["latitude"]
        bucket["sum_longitude"] += incident["longitude"]
        bucket["weight"] += incident["point_weight"]
        bucket["count"] += 1

    cells = []
    for bucket in buckets.values():
        cells.append(
            [
                round(bucket["sum_latitude"] / bucket["count"], 6),
                round(bucket["sum_longitude"] / bucket["count"], 6),
                round(bucket["weight"] / bucket["count"], 4),
                bucket["count"],
            ]
        )

    return {
        "rows": rows,
        "cols": cols,
        "source_incidents": len(incidents),
        "cells_count": len(cells),
        "bounds": bounds,
        "points": cells,
    }


def _grid_zones_fallback(bounds: dict, incidents_count: int, min_samples: int) -> dict:
    grid = heatmap_grid(rows=120, cols=120)
    points = grid.get("points", [])

    if not points:
        return {
            "source_incidents": incidents_count,
            "zones_count": 0,
            "bounds": bounds,
            "zones": [],
            "source": "grid_fallback",
        }

    candidate_limit = 250 if LOW_MEMORY_MODE else 400
    ranked = sorted(
        points,
        key=lambda point: (float(point[2]), int(point[3])),
        reverse=True,
    )[:candidate_limit]

    weights = [float(point[2]) for point in ranked]
    min_weight = min(weights)
    max_weight = max(weights)
    weight_span = max(1e-6, max_weight - min_weight)

    zones: list[dict] = []
    for index, point in enumerate(ranked):
        latitude = float(point[0])
        longitude = float(point[1])
        avg_weight = float(point[2])
        incident_count = int(point[3])

        normalized_weight = (avg_weight - min_weight) / weight_span
        sample_signal = min(1.0, incident_count / max(1, min_samples))
        risk_score = (0.78 * normalized_weight) + (0.22 * sample_signal)

        if risk_score >= 0.68:
            risk_level = "high"
        elif risk_score >= 0.42:
            risk_level = "moderate"
        else:
            risk_level = "low"

        zones.append(
            {
                "cluster_id": index,
                "latitude": round(latitude, 6),
                "longitude": round(longitude, 6),
                "incident_count": incident_count,
                "avg_weight": round(avg_weight, 4),
                "risk_score": round(risk_score, 4),
                "risk_level": risk_level,
            }
        )

    zones.sort(key=lambda zone: zone["risk_score"], reverse=True)
    return {
        "source_incidents": incidents_count,
        "zones_count": len(zones),
        "bounds": bounds,
        "zones": zones,
        "source": "grid_fallback",
    }


@lru_cache(maxsize=1)
def dbscan_zones(eps_meters: int = 850, min_samples: int = 16) -> dict:
    incidents = load_incidents()
    bounds = dataset_summary()["bounds"]

    if not incidents:
        return {
            "source_incidents": 0,
            "zones_count": 0,
            "bounds": bounds,
            "zones": [],
        }

    if not ENABLE_DBSCAN:
        return _grid_zones_fallback(bounds=bounds, incidents_count=len(incidents), min_samples=min_samples)

    try:
        from sklearn.cluster import DBSCAN
    except Exception:
        return _grid_zones_fallback(bounds=bounds, incidents_count=len(incidents), min_samples=min_samples)

    coordinates_radians = [
        [math.radians(incident["latitude"]), math.radians(incident["longitude"])]
        for incident in incidents
    ]

    epsilon_radians = eps_meters / 6_371_000
    model = DBSCAN(
        eps=epsilon_radians,
        min_samples=min_samples,
        metric="haversine",
    )
    labels = model.fit_predict(coordinates_radians)

    clusters: dict[int, list[dict]] = {}
    for incident, label in zip(incidents, labels):
        if label == -1:
            continue
        clusters.setdefault(int(label), []).append(incident)

    if not clusters:
        return {
            "source_incidents": len(incidents),
            "zones_count": 0,
            "bounds": bounds,
            "zones": [],
        }

    cluster_sizes = [len(members) for members in clusters.values()]
    min_cluster_size = min(cluster_sizes)
    max_cluster_size = max(cluster_sizes)
    size_span = max(1, max_cluster_size - min_cluster_size)

    zones = []
    for cluster_id, members in clusters.items():
        count = len(members)
        total_weight = sum(member["point_weight"] for member in members)
        weighted_lat = sum(member["latitude"] * member["point_weight"] for member in members)
        weighted_lon = sum(member["longitude"] * member["point_weight"] for member in members)
        severity_values = [member.get("severity_level", 0.0) for member in members]

        avg_weight = total_weight / count
        avg_severity = (sum(severity_values) / max(1, len(severity_values))) / 5.0
        normalized_size = (count - min_cluster_size) / size_span
        risk_score = (0.55 * avg_weight) + (0.30 * avg_severity) + (0.15 * normalized_size)

        zones.append(
            {
                "cluster_id": cluster_id,
                "latitude": round(weighted_lat / max(total_weight, 0.0001), 6),
                "longitude": round(weighted_lon / max(total_weight, 0.0001), 6),
                "incident_count": count,
                "avg_weight": round(avg_weight, 4),
                "risk_score": round(risk_score, 4),
            }
        )

    sorted_scores = sorted(zone["risk_score"] for zone in zones)
    low_index = int(len(sorted_scores) * 0.40)
    high_index = int(len(sorted_scores) * 0.80)
    moderate_floor = sorted_scores[min(low_index, len(sorted_scores) - 1)]
    high_floor = sorted_scores[min(high_index, len(sorted_scores) - 1)]

    for zone in zones:
        if zone["risk_score"] >= high_floor:
            zone["risk_level"] = "high"
        elif zone["risk_score"] >= moderate_floor:
            zone["risk_level"] = "moderate"
        else:
            zone["risk_level"] = "low"

    zones.sort(key=lambda zone: zone["risk_score"], reverse=True)
    return {
        "source_incidents": len(incidents),
        "zones_count": len(zones),
        "bounds": bounds,
        "zones": zones,
        "source": "dbscan",
    }


@app.get("/api/health")
def health() -> dict:
    summary = dataset_summary()
    return {
        "status": "ok",
        "dataset": summary["dataset_file"],
        "total_incidents": summary["total_incidents"],
    }


@app.get("/")
def root_health() -> dict:
    return {
        "status": "ok",
        "service": "Smart Community SOS API",
    }


@app.get("/api/auth-db-health")
def auth_db_health() -> dict:
    now_ts = int(time.time())
    try:
        with get_connection() as connection:
            users_count = int(
                connection.execute("SELECT COUNT(*) AS count FROM users").fetchone()["count"]
            )
            messages_count = int(
                connection.execute("SELECT COUNT(*) AS count FROM direct_messages").fetchone()["count"]
            )
            active_location_shares = int(
                connection.execute(
                    """
                    SELECT COUNT(*) AS count
                    FROM live_location_shares
                    WHERE is_active = 1 AND expires_at >= ?
                    """,
                    (now_ts,),
                ).fetchone()["count"]
            )

        return {
            "status": "ok",
            "db_backend": DB_BACKEND,
            "db_path": str(DB_PATH),
            "db_path_source": DB_PATH_SOURCE,
            "db_path_persistent": DB_PATH_IS_PERSISTENT,
            "strict_db_path": STRICT_DB_PATH,
            "render_runtime": RENDER_RUNTIME,
            "users_count": users_count,
            "direct_messages_count": messages_count,
            "active_live_location_shares": active_location_shares,
        }
    except Exception as exc:
        return {
            "status": "error",
            "detail": str(exc),
        }


@app.get("/api/heatmap/summary")
def heatmap_summary() -> dict:
    return dataset_summary()


@app.get("/api/heatmap/points")
def heatmap_points(
    city: str | None = Query(default=None),
    limit: int = Query(default=0, ge=0, le=100000),
) -> dict:
    incidents = load_incidents()

    if city:
        filtered = [
            incident for incident in incidents
            if incident["city"].lower() == city.lower()
        ]
    else:
        filtered = incidents

    points = [
        [
            incident["latitude"],
            incident["longitude"],
            incident["point_weight"],
        ]
        for incident in filtered
    ]

    effective_limit = limit
    if effective_limit <= 0 and MAX_HEATMAP_POINTS_RESPONSE > 0:
        effective_limit = MAX_HEATMAP_POINTS_RESPONSE

    sampled = False
    if effective_limit > 0 and len(points) > effective_limit:
        sampled = True
        stride = max(1, math.ceil(len(points) / effective_limit))
        points = points[::stride][:effective_limit]

    return {
        "count": len(points),
        "city_filter": city,
        "sampled": sampled,
        "limit": effective_limit if effective_limit > 0 else None,
        "points": points,
    }


@app.get("/api/incidents/nearby")
def nearby_incidents(
    latitude: float,
    longitude: float,
    limit: int = Query(default=3, ge=1, le=10),
    radius_m: int = Query(default=2500, ge=250, le=15000),
) -> dict:
    incidents = load_incidents()
    if not incidents:
        return {
            "count": 0,
            "source": "empty_dataset",
            "radius_m": radius_m,
            "incidents": [],
        }

    ranked: list[tuple[float, dict]] = []
    for incident in incidents:
        distance_m = _haversine_meters(
            latitude,
            longitude,
            float(incident["latitude"]),
            float(incident["longitude"]),
        )
        if distance_m <= radius_m:
            ranked.append((distance_m, incident))

    source = "radius_match"
    if not ranked:
        source = "nearest_fallback"
        ranked = [
            (
                _haversine_meters(
                    latitude,
                    longitude,
                    float(incident["latitude"]),
                    float(incident["longitude"]),
                ),
                incident,
            )
            for incident in incidents
        ]

    ranked.sort(key=lambda item: (item[0], -float(item[1].get("risk_score", 0.0))))
    selected = ranked[:limit]

    result = []
    for distance_m, incident in selected:
        result.append(
            {
                "incident_id": incident.get("incident_id"),
                "crime_type": incident.get("crime_type", "Unknown"),
                "city": incident.get("city", "Unknown"),
                "area_name": incident.get("area_name", "Unknown"),
                "date": incident.get("date"),
                "time": incident.get("time"),
                "severity_level": round(float(incident.get("severity_level", 0.0)), 2),
                "risk_score": round(float(incident.get("risk_score", 0.0)), 4),
                "crowd_density": incident.get("crowd_density", "medium"),
                "lighting_condition": incident.get("lighting_condition", "moderate"),
                "distance_m": round(distance_m, 1),
                "latitude": round(float(incident["latitude"]), 6),
                "longitude": round(float(incident["longitude"]), 6),
            }
        )

    return {
        "count": len(result),
        "source": source,
        "radius_m": radius_m,
        "incidents": result,
    }


@app.get("/api/heatmap/grid")
def heatmap_grid_points(
    rows: int = Query(default=120, ge=20, le=250),
    cols: int = Query(default=120, ge=20, le=250),
) -> dict:
    return heatmap_grid(rows=rows, cols=cols)


@app.get("/api/heatmap/zones")
def heatmap_dbscan_zones(
    eps_meters: int = Query(default=850, ge=200, le=3000),
    min_samples: int = Query(default=16, ge=4, le=80),
) -> dict:
    return dbscan_zones(eps_meters=eps_meters, min_samples=min_samples)


@app.post("/api/ai/risk-predict")
def ai_risk_predict(payload: AreaRiskPredictionRequest) -> dict:
    user_reports = payload.user_reports_count if payload.user_reports_count is not None else payload.user_reports
    location_features: dict[str, float | str] = {}
    coverage_distance_km = 0.0
    zone_prior: dict | None = None
    calibrated_by_zone = False

    if payload.latitude is not None and payload.longitude is not None:
        context = risk_feature_context()
        coverage_distance_km = distance_to_bounds_km(
            latitude=payload.latitude,
            longitude=payload.longitude,
            bounds=context["bounds"],
        )

        if coverage_distance_km > 250.0:
            baseline_score = 0.06
            return {
                "risk_score": round(baseline_score, 4),
                "risk_probability": round(baseline_score, 4),
                "risk_percent": round(baseline_score * 100.0, 2),
                "risk_level": "low",
                "model_name": "coverage_guard_backend",
                "model_ready": True,
                "message": "Location is outside dataset coverage. Baseline low-risk estimate is shown.",
                "recommendation": "Dataset currently covers India. Add regional incidents for precise scoring.",
                "scale": "0-100",
                "location_based": True,
                "coverage_distance_km": round(coverage_distance_km, 3),
            }

        nearest = nearest_incident(payload.latitude, payload.longitude)
        location_features = derive_location_risk_features(
            context=context,
            latitude=payload.latitude,
            longitude=payload.longitude,
            fallback_city=(nearest or {}).get("city"),
        )
        zone_prior = nearest_zone_prior(payload.latitude, payload.longitude)

    effective_crime_frequency_area = payload.crime_frequency_area
    if effective_crime_frequency_area <= 0.0 and location_features:
        effective_crime_frequency_area = float(location_features.get("nearby_avg_crime_frequency_area", 0.0))

    prediction = predict_area_risk(
        crime_frequency_area=effective_crime_frequency_area,
        hour_of_day=payload.hour_of_day,
        crowd_density=payload.crowd_density,
        lighting_condition=payload.lighting_condition,
        user_reports_count=user_reports,
        extra_features=location_features or None,
    )

    calibrated_score = float(prediction["risk_score"])
    if zone_prior is not None:
        zone_risk_score = float(zone_prior["risk_score"])
        zone_risk_level = str(zone_prior["risk_level"])
        zone_incidents = int(zone_prior["incident_count"])

        zone_floor = None
        if zone_risk_level == "high" and zone_incidents >= 20:
            zone_floor = min(0.95, max(0.58, zone_risk_score * 0.90))
            if zone_incidents >= 500 and zone_risk_score >= 0.67:
                zone_floor = max(zone_floor, 0.65)
        elif zone_risk_level == "moderate" and zone_incidents >= 20:
            zone_floor = min(0.85, max(0.46, zone_risk_score * 0.82))

        if zone_floor is not None and calibrated_score < zone_floor:
            calibrated_score = zone_floor
            calibrated_by_zone = True

    if calibrated_by_zone:
        calibrated_score = max(0.0, min(1.0, calibrated_score))
        prediction = {
            **prediction,
            "risk_score": round(calibrated_score, 4),
            "risk_probability": round(calibrated_score, 4),
            "risk_percent": round(calibrated_score * 100.0, 2),
            "risk_level": probability_to_level(
                calibrated_score,
                moderate_floor=RISK_LEVEL_MODERATE_FLOOR,
                high_floor=RISK_LEVEL_HIGH_FLOOR,
            ),
            "model_name": f"{prediction['model_name']}+zone_prior",
        }

    level = str(prediction["risk_level"]).upper()
    nearby_incidents = int(round(float(location_features.get("nearby_incidents", 0.0)))) if location_features else None

    recommendation = {
        "HIGH": "Avoid isolated routes and share live location immediately.",
        "MODERATE": "Stay in well-lit areas and keep emergency shortcuts ready.",
        "LOW": "Risk is currently low, remain alert and follow normal precautions.",
    }.get(level, "Stay alert and avoid low-visibility roads.")

    if nearby_incidents is not None:
        message = (
            f"This area has {level} risk ({prediction['risk_percent']:.1f}%) "
            f"based on {nearby_incidents} nearby incidents and contextual features"
        )
    else:
        message = f"This area has {level} risk ({prediction['risk_percent']:.1f}%) right now"

    return {
        **prediction,
        "message": message,
        "recommendation": recommendation,
        "scale": "0-100",
        "location_based": bool(location_features),
        "nearby_incidents": nearby_incidents,
        "coverage_distance_km": round(coverage_distance_km, 3),
        "zone_prior_risk": round(float(zone_prior["risk_score"]), 4) if zone_prior is not None else None,
        "zone_prior_level": str(zone_prior["risk_level"]) if zone_prior is not None else None,
        "calibrated_by_zone": calibrated_by_zone,
    }


@app.post("/api/ai/anomaly-check")
def ai_anomaly_check(payload: AnomalyCheckRequest) -> dict:
    nearest = nearest_incident(payload.current_latitude, payload.current_longitude)
    baseline = route_baseline_center(payload.usual_route, nearest)

    if baseline is None:
        baseline = (payload.current_latitude, payload.current_longitude)
        baseline_source = "current_location"
    elif payload.usual_route:
        baseline_source = "usual_route"
    else:
        baseline_source = "city_centroid"

    deviation_km = haversine_km(
        payload.current_latitude,
        payload.current_longitude,
        baseline[0],
        baseline[1],
    )
    prediction = predict_anomaly_signal(
        deviation_km=deviation_km,
        speed_kmph=payload.speed_kmph,
        hour_of_day=payload.hour_of_day,
    )

    if prediction["is_anomaly"]:
        message = "You deviated from your normal path. Are you safe?"
    else:
        message = "Movement pattern appears normal for now."

    return {
        **prediction,
        "deviation_score_km": round(deviation_km, 4),
        "baseline_latitude": round(baseline[0], 6),
        "baseline_longitude": round(baseline[1], 6),
        "baseline_source": baseline_source,
        "message": message,
    }


@app.post("/api/ai/proactive-alert")
def ai_proactive_alert(payload: ProactiveAlertRequest) -> dict:
    nearest = nearest_incident(payload.upcoming_latitude, payload.upcoming_longitude)

    city = (nearest or {}).get("city", "Unknown")
    crime_frequency_area = float((nearest or {}).get("crime_frequency_area", 0.0))
    crowd_density = normalize_crowd_density(payload.crowd_density or (nearest or {}).get("crowd_density", "medium"))
    lighting_condition = normalize_lighting(payload.lighting_condition or (nearest or {}).get("lighting_condition", "moderate"))
    is_weekend = day_of_week_to_is_weekend(payload.day_of_week)

    prediction = predict_proactive_risk(
        city=city,
        crime_frequency_area=crime_frequency_area,
        hour_of_day=payload.hour_of_day,
        is_weekend=is_weekend,
        crowd_density=crowd_density,
        lighting_condition=lighting_condition,
    )

    risk_level = str(prediction["risk_level"]).lower()
    if risk_level == "high":
        message = f"This area becomes unsafe after {payload.hour_of_day:02d}:00. Consider an alternate route."
    elif risk_level == "moderate":
        message = "Risk may increase soon. Prefer crowded and well-lit roads."
    else:
        message = "No major short-horizon risk spike predicted for this route."

    return {
        **prediction,
        "city_context": city,
        "hour_context": payload.hour_of_day,
        "is_weekend": is_weekend,
        "crowd_density": crowd_density,
        "lighting_condition": lighting_condition,
        "message": message,
    }


@app.get("/api/ai/model-status")
def ai_model_status() -> dict:
    models_dir = PROJECT_ROOT / "backend" / "models"
    expected = {
        "risk_logistic": models_dir / "risk_logistic.joblib",
        "risk_gradient_boosting": models_dir / "risk_gradient_boosting.joblib",
        "risk_random_forest": models_dir / "risk_random_forest.joblib",
        "risk_best": models_dir / "risk_best.joblib",
        "anomaly_isolation_forest": models_dir / "anomaly_isolation_forest.joblib",
        "proactive_alert_model": models_dir / "proactive_alert_model.joblib",
    }

    availability = {
        key: path.exists()
        for key, path in expected.items()
    }

    return {
        "status": "ok",
        "all_models_ready": all(availability.values()),
        "models": availability,
        "checked_at_utc": datetime.utcnow().isoformat(),
    }
