# AI Features Implementation Checklist

Use this as the single tracker for planned AI capabilities in SmartCommunitySOS.

## Current Plan (Implement One by One)

- [x] 1. Area Risk Prediction (CORE AI FEATURE)
  - Goal: Predict how unsafe a location is at a given time.
  - Input features:
    - `crime_frequency_area`
    - `time` (hour / night)
    - `crowd_density`
    - `lighting_condition`
    - `user_reports`
  - Candidate models:
    - Logistic Regression (baseline)
    - Random Forest (stronger non-linear model)
  - App output example:
    - "This area has HIGH risk (0.82) right now"
  - Implementation checklist:
    - [x] Prepare training dataset + feature engineering pipeline
    - [x] Train/evaluate Logistic Regression baseline
    - [x] Train/evaluate Random Forest
    - [x] Pick best model based on validation metrics
    - [x] Export model artifact + inference helper
    - [x] Add backend endpoint for risk prediction
    - [x] Integrate prediction card in Android app

- [x] 2. Danger Heatmap Generation
  - Goal: Show unsafe zones visually on the map.
  - Input features:
    - `latitude`, `longitude`
    - `crime_frequency_area`
    - `severity`
  - Candidate techniques:
    - K-Means
    - DBSCAN
  - App output:
    - Red zones = danger
    - Green zones = safe
  - Implementation checklist:
    - [x] Build geo-feature preparation step
    - [x] Compare K-Means vs DBSCAN clustering quality
    - [x] Generate risk-zone labels (high/moderate/low)
    - [x] Expose backend API for zone outputs
    - [x] Render zone/dot overlay in Android map

- [x] 3. Behavior Anomaly Detection
  - Goal: Detect unusual movement behavior for a user.
  - Input features:
    - `usual_route`
    - `deviation_score`
    - `speed`
  - Candidate models:
    - Isolation Forest
    - Simple threshold rules (fast fallback)
  - App output example:
    - "You deviated from your normal path. Are you safe?"
  - Implementation checklist:
    - [x] Define route baseline and deviation metric
    - [x] Build anomaly score pipeline
    - [x] Train/test Isolation Forest
    - [x] Add threshold fallback logic
    - [x] Add backend endpoint for anomaly checks
    - [x] Trigger in-app warning workflow

- [x] 4. Predictive Unsafe Area Alerts
  - Goal: Warn user before entering potentially unsafe locations.
  - Input features:
    - upcoming location
    - historical crime patterns
    - time
  - Candidate models:
    - Time-series risk scoring
    - Classification model for near-future risk
  - App output example:
    - "This area becomes unsafe after 9 PM"
  - Implementation checklist:
    - [x] Build short-horizon risk forecasting dataset
    - [x] Train baseline prediction model
    - [x] Add backend endpoint for proactive alert checks
    - [x] Add pre-entry warning UX in app navigation/map flow

## Execution Order

1. Area Risk Prediction
2. Danger Heatmap Generation
3. Behavior Anomaly Detection
4. Predictive Unsafe Area Alerts

## Definition of Done (Per Feature)

- [x] Data pipeline + feature engineering committed
- [x] Model training notebook/script added
- [x] Validation metrics documented
- [x] Backend API integrated and tested
- [x] Android UI integrated and tested
- [x] Demo scenario verified on emulator/device
