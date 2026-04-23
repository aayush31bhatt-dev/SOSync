# Smart Community SOS

Minimal Android base scaffold for the safety app MVP.

## Included

- Root Gradle files
- `app` module
- Jetpack Compose starter activity
- Theme and color files
- Gradle wrapper scripts

## Open In Android Studio

1. Open this folder in Android Studio.
2. Let Android Studio create `local.properties` automatically.
3. Install the Android SDK if Studio prompts for it.
4. Sync the project.

## Current Note

The project still needs `gradle/wrapper/gradle-wrapper.jar` to use `gradlew` from the terminal. If you want, I can fetch that next from Gradle's official source so the wrapper is complete.

## Run Without USB (Host Backend Online)

### 1. Deploy Backend

You can deploy to Render using `backend/render.yaml`.

1. Push your repo to GitHub.
2. In Render, create a new Blueprint and select this repository.
3. Render will detect `backend/render.yaml` and create the `sosync-api` web service.
4. After deploy, note your public API URL (example: `https://sosync-api.onrender.com`).

### MongoDB (Recommended For Persistent Auth Data)

The backend now supports MongoDB for auth, messages, and live-location tables.

1. Create a MongoDB Atlas cluster (free tier is enough for demos).
2. In Atlas, create a database user and allow your Render outbound IP range (or `0.0.0.0/0` for quick setup).
3. Copy your Mongo connection string and set Render env var:
	- `MONGODB_URI=mongodb+srv://<user>:<pass>@<cluster>/`
4. Ensure these backend env vars are set:
	- `AUTH_DB_BACKEND=mongodb`
	- `MONGODB_DB_NAME=sosync`
	- Optional: `AUTH_DB_STRICT_BACKEND=true` if you want deploy to fail when MongoDB is unavailable.
5. Redeploy on Render.

Use `/api/auth-db-health` to verify:
- `db_backend` should be `mongodb`.
- If Mongo is unavailable and strict mode is off, backend falls back to SQLite and reports `db_backend_fallback_reason`.

### 2. Configure App API URL

The Android app now supports a configurable base URL through Gradle property `apiBaseUrl`.

Build with hosted URL:

```powershell
./gradlew.bat :app:assembleDebug -PapiBaseUrl=https://your-api-domain
```

If `apiBaseUrl` is omitted (or set to `auto`), the app falls back to local development mode:

- Emulator: `http://10.0.2.2:8001`
- Physical phone: `http://127.0.0.1:8001`

### 3. Install on Phone

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After this, mobile app traffic goes directly to your online backend, no USB reverse needed.
