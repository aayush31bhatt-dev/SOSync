from __future__ import annotations

import math
from collections import defaultdict
from typing import Any


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


def _to_float(value: Any, fallback: float = 0.0) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return fallback


def _normalize_city(city: Any) -> str:
    normalized = str(city or "").strip()
    return normalized or "Unknown"


def _clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def _cell_index(
    latitude: float,
    longitude: float,
    bounds: dict[str, float],
    rows: int,
    cols: int,
) -> tuple[int, int]:
    min_latitude = float(bounds["min_latitude"])
    max_latitude = float(bounds["max_latitude"])
    min_longitude = float(bounds["min_longitude"])
    max_longitude = float(bounds["max_longitude"])

    lat_span = max(max_latitude - min_latitude, 1e-9)
    lon_span = max(max_longitude - min_longitude, 1e-9)

    lat_ratio = (latitude - min_latitude) / lat_span
    lon_ratio = (longitude - min_longitude) / lon_span

    row_index = int(_clamp(lat_ratio * rows, 0, rows - 1))
    col_index = int(_clamp(lon_ratio * cols, 0, cols - 1))
    return row_index, col_index


def _neighbor_aggregate(
    cell_stats: dict[tuple[int, int], dict[str, float]],
    row_index: int,
    col_index: int,
    rows: int,
    cols: int,
) -> dict[str, float]:
    count = 0.0
    sum_risk = 0.0
    sum_severity = 0.0
    sum_crime_frequency_area = 0.0

    for candidate_row in range(max(0, row_index - 1), min(rows - 1, row_index + 1) + 1):
        for candidate_col in range(max(0, col_index - 1), min(cols - 1, col_index + 1) + 1):
            snapshot = cell_stats.get((candidate_row, candidate_col))
            if snapshot is None:
                continue

            count += float(snapshot["count"])
            sum_risk += float(snapshot["sum_risk"])
            sum_severity += float(snapshot["sum_severity"])
            sum_crime_frequency_area += float(snapshot["sum_crime_frequency_area"])

    return {
        "count": count,
        "sum_risk": sum_risk,
        "sum_severity": sum_severity,
        "sum_crime_frequency_area": sum_crime_frequency_area,
    }


def build_spatial_feature_context(
    incidents: list[dict[str, Any]],
    grid_rows: int = 140,
    grid_cols: int = 140,
) -> dict[str, Any]:
    if not incidents:
        raise ValueError("Cannot build spatial context with empty incidents")

    latitudes = [_to_float(incident.get("latitude")) for incident in incidents]
    longitudes = [_to_float(incident.get("longitude")) for incident in incidents]

    bounds = {
        "min_latitude": min(latitudes),
        "max_latitude": max(latitudes),
        "min_longitude": min(longitudes),
        "max_longitude": max(longitudes),
    }

    cell_stats: dict[tuple[int, int], dict[str, float]] = {}
    cell_city_counts: dict[tuple[int, int], dict[str, int]] = defaultdict(lambda: defaultdict(int))
    city_stats: dict[str, dict[str, float]] = defaultdict(lambda: {"count": 0.0, "sum_risk": 0.0})

    total_risk = 0.0
    for incident in incidents:
        latitude = _to_float(incident.get("latitude"))
        longitude = _to_float(incident.get("longitude"))
        risk_score = _to_float(incident.get("risk_score"))
        severity = _to_float(incident.get("severity_level"))
        crime_frequency_area = _to_float(incident.get("crime_frequency_area"))
        city = _normalize_city(incident.get("city"))

        row_index, col_index = _cell_index(
            latitude=latitude,
            longitude=longitude,
            bounds=bounds,
            rows=grid_rows,
            cols=grid_cols,
        )
        cell_key = (row_index, col_index)

        snapshot = cell_stats.setdefault(
            cell_key,
            {
                "count": 0.0,
                "sum_risk": 0.0,
                "sum_severity": 0.0,
                "sum_crime_frequency_area": 0.0,
            },
        )
        snapshot["count"] += 1.0
        snapshot["sum_risk"] += risk_score
        snapshot["sum_severity"] += severity
        snapshot["sum_crime_frequency_area"] += crime_frequency_area

        cell_city_counts[cell_key][city] += 1
        city_stats[city]["count"] += 1.0
        city_stats[city]["sum_risk"] += risk_score
        total_risk += risk_score

    cell_primary_city: dict[tuple[int, int], str] = {}
    for cell_key, counts in cell_city_counts.items():
        dominant_city = max(counts.items(), key=lambda item: item[1])[0]
        cell_primary_city[cell_key] = dominant_city

    max_neighborhood_count = 1.0
    for row_index, col_index in cell_stats.keys():
        nearby = _neighbor_aggregate(
            cell_stats=cell_stats,
            row_index=row_index,
            col_index=col_index,
            rows=grid_rows,
            cols=grid_cols,
        )
        max_neighborhood_count = max(max_neighborhood_count, float(nearby["count"]))

    total_incidents = float(max(1, len(incidents)))
    global_avg_risk = total_risk / total_incidents

    return {
        "rows": grid_rows,
        "cols": grid_cols,
        "bounds": bounds,
        "cell_stats": cell_stats,
        "cell_primary_city": cell_primary_city,
        "city_stats": city_stats,
        "total_incidents": total_incidents,
        "global_avg_risk": global_avg_risk,
        "max_neighborhood_count": max_neighborhood_count,
    }


def derive_location_risk_features(
    context: dict[str, Any],
    latitude: float,
    longitude: float,
    fallback_city: str | None = None,
) -> dict[str, float | str]:
    row_index, col_index = _cell_index(
        latitude=float(latitude),
        longitude=float(longitude),
        bounds=context["bounds"],
        rows=int(context["rows"]),
        cols=int(context["cols"]),
    )

    nearby = _neighbor_aggregate(
        cell_stats=context["cell_stats"],
        row_index=row_index,
        col_index=col_index,
        rows=int(context["rows"]),
        cols=int(context["cols"]),
    )

    nearby_count = float(nearby["count"])
    if nearby_count > 0:
        nearby_avg_risk = float(nearby["sum_risk"]) / nearby_count
        nearby_avg_severity = float(nearby["sum_severity"]) / nearby_count
        nearby_avg_crime_frequency_area = float(nearby["sum_crime_frequency_area"]) / nearby_count
    else:
        nearby_avg_risk = float(context["global_avg_risk"])
        nearby_avg_severity = 0.0
        nearby_avg_crime_frequency_area = 0.0

    resolved_city = _normalize_city(fallback_city)
    if resolved_city == "Unknown":
        resolved_city = context["cell_primary_city"].get((row_index, col_index), "Unknown")

    city_snapshot = context["city_stats"].get(resolved_city)
    if city_snapshot is None:
        city_avg_risk = float(context["global_avg_risk"])
        city_incident_share = 0.0
    else:
        city_count = max(1.0, float(city_snapshot["count"]))
        city_avg_risk = float(city_snapshot["sum_risk"]) / city_count
        city_incident_share = city_count / float(max(1.0, context["total_incidents"]))

    local_density_ratio = nearby_count / float(max(1.0, context["max_neighborhood_count"]))

    return {
        "city": resolved_city,
        "nearby_incidents": nearby_count,
        "nearby_incidents_log": math.log1p(max(0.0, nearby_count)),
        "nearby_avg_risk": nearby_avg_risk,
        "nearby_avg_severity": nearby_avg_severity,
        "nearby_avg_crime_frequency_area": nearby_avg_crime_frequency_area,
        "local_density_ratio": max(0.0, min(1.0, local_density_ratio)),
        "city_avg_risk": city_avg_risk,
        "city_incident_share": max(0.0, min(1.0, city_incident_share)),
    }


def distance_to_bounds_km(latitude: float, longitude: float, bounds: dict[str, float]) -> float:
    min_latitude = float(bounds["min_latitude"])
    max_latitude = float(bounds["max_latitude"])
    min_longitude = float(bounds["min_longitude"])
    max_longitude = float(bounds["max_longitude"])

    clamped_latitude = _clamp(float(latitude), min_latitude, max_latitude)
    clamped_longitude = _clamp(float(longitude), min_longitude, max_longitude)

    if clamped_latitude == float(latitude) and clamped_longitude == float(longitude):
        return 0.0

    return haversine_km(float(latitude), float(longitude), clamped_latitude, clamped_longitude)
