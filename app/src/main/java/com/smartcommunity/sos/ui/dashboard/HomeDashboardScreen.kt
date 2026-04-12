package com.smartcommunity.sos.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.smartcommunity.sos.config.ApiConfig
import com.smartcommunity.sos.ui.theme.SmartCommunitySOSTheme
import com.smartcommunity.sos.ui.theme.ButtonBorder
import com.smartcommunity.sos.ui.theme.ButtonSurface
import com.smartcommunity.sos.data.auth.AuthSessionStore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.abs

private val BACKEND_BASE_URL = ApiConfig.baseUrl
private const val HOME_MAP_ZOOM = 16
private const val HOME_TILE_SIZE_PX = 256
private const val HOME_MAP_TIMEOUT_MS = 2000
private const val HOME_API_TIMEOUT_MS = 15000
private val HOME_MAP_TILE_URLS = listOf(
    "https://basemaps.cartocdn.com/voyager/%d/%d/%d@2x.png",
    "https://basemaps.cartocdn.com/light_all/%d/%d/%d@2x.png",
    "https://tile.openstreetmap.org/%d/%d/%d.png"
)
private const val SOS_NOTIFICATION_CHANNEL_ID = "sos_alerts"
private const val SOS_NOTIFICATION_CHANNEL_NAME = "SOS Alerts"
private const val CHAT_NOTIFICATION_CHANNEL_ID = "chat_messages"
private const val CHAT_NOTIFICATION_CHANNEL_NAME = "Messages"
private const val TRUSTED_CONTACTS_PREF_KEY = "trusted_contacts_json"
private const val LAST_DIRECT_MESSAGE_ID_PREF_KEY = "last_direct_message_id"
private const val LIVE_LOCATION_SHARE_MINUTES = 40

private val DashboardDarkColors = darkColorScheme(
    primary = Color(0xFF7BC6FF),
    secondary = Color(0xFF66E2C7),
    background = Color(0xFF232323),
    surface = Color(0xFF2A2A2A),
    surfaceVariant = Color(0x33545454),
    onPrimary = Color(0xFF001826),
    onBackground = Color(0xFFE7F1FF),
    onSurface = Color(0xFFE7F1FF),
    onSurfaceVariant = Color(0xFFC9C9C9),
    outline = Color(0x66B0B0B0),
)

private val GlassCardColor = Color(0xFF2A2A2A)
private val GlassBorderColor = Color(0xFF4A4A4A)
private val UnifiedButtonHeight = 42.dp
private val UnifiedIconButtonSize = 42.dp
private val UnifiedButtonGap = 8.dp

private data class SosNearbyAlert(
    val eventId: String,
    val requesterId: String,
    val message: String,
    val locationLabel: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int,
    val distanceM: Double,
    val ageSeconds: Int,
    val acceptedCount: Int,
    val userResponse: String?
)

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val shortLabel: String,
    val icon: ImageVector
)

private data class TrustedContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val username: String
)

private data class TrustedContactCandidate(
    val userId: Int,
    val name: String,
    val username: String
)

private data class DirectMessageItem(
    val messageId: Long,
    val senderUsername: String,
    val recipientUsername: String,
    val messageText: String,
    val createdAt: String
)

private data class LiveLocationShareItem(
    val senderUsername: String,
    val senderName: String,
    val expiresAtEpoch: Long
)

private data class SharedLiveLocation(
    val senderUsername: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String?,
    val ageSeconds: Int
)

private data class MapNavigationTarget(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

private enum class EvidenceCaptureMode {
    AUDIO,
    VIDEO
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class EvidenceDispatchResult(
    val evidenceUrl: String,
    val deliveredCount: Int
)

private val quickActions = listOf(
    QuickActionItem("Share Location", "Send current location", "SL", Icons.Filled.LocationOn),
    QuickActionItem("Evidence Mode", "Auto-record audio/video", "EM", Icons.Filled.Check)
)

private val defaultTrustedContacts: List<TrustedContact> = emptyList()

@Composable
fun HomeDashboardScreen(currentUsername: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authSessionStore = remember { AuthSessionStore(context) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val appUserId = remember(currentUsername) {
        currentUsername.trim().lowercase().ifBlank { getOrCreateAppUserId(context) }
    }
    var currentLocationLabel by remember { mutableStateOf("Fetching live location...") }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var fcmToken by remember { mutableStateOf<String?>(null) }
    var sosStatus by remember { mutableStateOf<String?>(null) }
    var nearbySosAlerts by remember { mutableStateOf<List<SosNearbyAlert>>(emptyList()) }
    var trustedContacts by remember { mutableStateOf(loadTrustedContacts(context)) }
    var trustedContactCandidates by remember { mutableStateOf<List<TrustedContactCandidate>>(emptyList()) }
    var activeChatContact by remember { mutableStateOf<TrustedContact?>(null) }
    var contactPendingUsernameLink by remember { mutableStateOf<TrustedContact?>(null) }
    var usernameLinkCandidates by remember { mutableStateOf<List<TrustedContactCandidate>>(emptyList()) }
    var usernameLinkQuery by remember { mutableStateOf("") }
    var threadMessages by remember { mutableStateOf<List<DirectMessageItem>>(emptyList()) }
    var pendingMessageText by remember { mutableStateOf("") }
    var chatStatusText by remember { mutableStateOf<String?>(null) }
    var seenAlertIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastNotifiedDirectMessageId by remember {
        mutableStateOf(loadLastNotifiedDirectMessageId(context))
    }
    var activeIncomingSharedLocation by remember { mutableStateOf<SharedLiveLocation?>(null) }
    var activeNavigationTarget by remember { mutableStateOf<MapNavigationTarget?>(null) }
    var activeNavigationRoute by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var mapZoomLevel by rememberSaveable { mutableStateOf(HOME_MAP_ZOOM) }
    var isMapExpanded by rememberSaveable { mutableStateOf(false) }
    var activeSosEventId by remember { mutableStateOf<String?>(null) }
    var activeSosExpiresAtMs by remember { mutableStateOf<Long?>(null) }
    var activeSosRemainingSec by remember { mutableStateOf<Int?>(null) }
    var introAlreadyPlayed by rememberSaveable { mutableStateOf(false) }
    val shouldAnimateIntro = !introAlreadyPlayed
    var notificationPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var showEvidenceModePicker by remember { mutableStateOf(false) }
    var isEvidenceModeActive by rememberSaveable { mutableStateOf(false) }
    var activeEvidenceCaptureMode by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingEvidenceCaptureMode by remember { mutableStateOf<EvidenceCaptureMode?>(null) }
    val activeAudioRecorder = remember { mutableStateOf<MediaRecorder?>(null) }
    var activeAudioEvidenceFile by remember { mutableStateOf<File?>(null) }

    fun sendEvidenceToTrustedContactsInApp(evidenceFile: File, mimeType: String) {
        val token = authSessionStore.getToken()
        if (token.isNullOrBlank()) {
            sosStatus = "Session expired. Login again to send evidence to trusted contacts."
            return
        }

        val recipients = trustedContacts
            .mapNotNull { it.username.trim().lowercase().takeIf { username -> username.isNotBlank() } }
            .distinct()

        if (recipients.isEmpty()) {
            sosStatus = "Evidence saved. Link trusted contacts to app usernames to send directly in-app."
            return
        }

        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    uploadEvidenceAndNotifyTrustedContacts(
                        apiBaseUrl = BACKEND_BASE_URL,
                        authToken = token,
                        recipientUsernames = recipients,
                        evidenceFile = evidenceFile,
                        mimeType = mimeType
                    )
                }
            }

            if (result.isSuccess) {
                val dispatch = result.getOrNull()
                if (dispatch != null) {
                    sosStatus = "Evidence sent in-app to ${dispatch.deliveredCount} trusted contact(s)."
                } else {
                    sosStatus = "Evidence uploaded, but no delivery summary was returned."
                }
            } else {
                val failure = result.exceptionOrNull()?.message.orEmpty()
                sosStatus = when {
                    failure.contains("401") -> "Session expired. Login again to send evidence."
                    failure.contains("404") -> "One or more trusted contacts were not found. Re-link usernames and retry."
                    failure.contains("413") -> "Evidence file is too large to upload."
                    failure.isNotBlank() -> failure
                    else -> "Could not send evidence to trusted contacts."
                }
            }
        }
    }

    fun startAudioEvidenceCapture() {
        val outputDir = File(context.getExternalFilesDir(null), "evidence")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outputFile = File(outputDir, "evidence_audio_${System.currentTimeMillis()}.m4a")

        val recorder = MediaRecorder()
        runCatching {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
        }.onSuccess {
            activeAudioRecorder.value = recorder
            activeAudioEvidenceFile = outputFile
            isEvidenceModeActive = true
            activeEvidenceCaptureMode = EvidenceCaptureMode.AUDIO.name
            sosStatus = "Evidence recording started (audio). Tap Evidence Mode again to stop."
        }.onFailure {
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
            sosStatus = "Could not start audio recorder. Check microphone permission and try again."
        }
    }

    fun stopEvidenceCapture() {
        when (activeEvidenceCaptureMode) {
            EvidenceCaptureMode.AUDIO.name -> {
                val recorder = activeAudioRecorder.value
                val recordedFile = activeAudioEvidenceFile
                if (recorder != null) {
                    runCatching {
                        recorder.stop()
                    }
                    runCatching { recorder.reset() }
                    runCatching { recorder.release() }
                }
                activeAudioRecorder.value = null
                activeAudioEvidenceFile = null
                isEvidenceModeActive = false
                activeEvidenceCaptureMode = null

                val evidenceFile = recordedFile?.takeIf { it.exists() && it.length() > 0L }
                if (evidenceFile != null) {
                    sendEvidenceToTrustedContactsInApp(evidenceFile, "audio/mp4")
                } else {
                    sosStatus = "Evidence recording stopped, but the audio file could not be prepared for sharing."
                }
            }

            EvidenceCaptureMode.VIDEO.name -> {
                isEvidenceModeActive = false
                activeEvidenceCaptureMode = null
                sosStatus = "Video capture session stopped."
            }

            else -> {
                isEvidenceModeActive = false
                activeEvidenceCaptureMode = null
            }
        }
    }

    fun hasEvidencePermission(mode: EvidenceCaptureMode): Boolean {
        val permission = when (mode) {
            EvidenceCaptureMode.AUDIO -> Manifest.permission.RECORD_AUDIO
            EvidenceCaptureMode.VIDEO -> Manifest.permission.CAMERA
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val videoEvidenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (activeEvidenceCaptureMode == EvidenceCaptureMode.VIDEO.name) {
            isEvidenceModeActive = false
            activeEvidenceCaptureMode = null
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val videoUri = result.data?.data
                if (videoUri != null) {
                    scope.launch {
                        val videoFile = withContext(Dispatchers.IO) {
                            copyContentUriToEvidenceFile(
                                context = context,
                                sourceUri = videoUri,
                                fallbackExtension = ".mp4"
                            )
                        }
                        if (videoFile != null) {
                            sendEvidenceToTrustedContactsInApp(videoFile, "video/mp4")
                        } else {
                            sosStatus = "Evidence video captured, but the file could not be prepared for upload."
                        }
                    }
                } else {
                    sosStatus = "Evidence video captured, but no shareable file was returned by camera app."
                }
            } else {
                sosStatus = "Video capture stopped."
            }
        }
    }

    fun navigateHomeMapToUser(latitude: Double, longitude: Double, label: String) {
        val origin = currentLocation
        if (origin == null) {
            sosStatus = "Waiting for your current location fix..."
            return
        }

        val destinationLabel = label.ifBlank { "selected user" }
        activeNavigationTarget = MapNavigationTarget(
            latitude = latitude,
            longitude = longitude,
            label = destinationLabel
        )
        sosStatus = "Calculating shortest path to $destinationLabel..."

        scope.launch {
            val shortestPath = runCatching {
                withContext(Dispatchers.IO) {
                    fetchShortestPathOnRoad(
                        startLatitude = origin.latitude,
                        startLongitude = origin.longitude,
                        destinationLatitude = latitude,
                        destinationLongitude = longitude
                    )
                }
            }.getOrDefault(emptyList())

            activeNavigationRoute = if (shortestPath.size >= 2) {
                shortestPath
            } else {
                listOf(
                    GeoPoint(origin.latitude, origin.longitude),
                    GeoPoint(latitude, longitude)
                )
            }
            mapZoomLevel = minOf(mapZoomLevel, 14)
            isMapExpanded = true
            sosStatus = "Route ready to $destinationLabel. Follow the highlighted path on the map."
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!locationPermissionGranted) {
            currentLocationLabel = "Location permission not granted"
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = granted
    }

    val evidencePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val requestedMode = pendingEvidenceCaptureMode
        pendingEvidenceCaptureMode = null

        if (requestedMode == null) {
            return@rememberLauncherForActivityResult
        }

        val permissionKey = when (requestedMode) {
            EvidenceCaptureMode.AUDIO -> Manifest.permission.RECORD_AUDIO
            EvidenceCaptureMode.VIDEO -> Manifest.permission.CAMERA
        }

        val granted = permissions[permissionKey] == true
        if (!granted) {
            sosStatus = "Permission denied for Evidence Mode."
            return@rememberLauncherForActivityResult
        }

        when (requestedMode) {
            EvidenceCaptureMode.AUDIO -> startAudioEvidenceCapture()
            EvidenceCaptureMode.VIDEO -> {
                val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                if (videoIntent.resolveActivity(context.packageManager) != null) {
                    isEvidenceModeActive = true
                    activeEvidenceCaptureMode = EvidenceCaptureMode.VIDEO.name
                    sosStatus = "Camera opened for evidence video."
                    videoEvidenceLauncher.launch(videoIntent)
                } else {
                    sosStatus = "No camera app available for video capture."
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeAudioRecorder.value?.let { recorder ->
                runCatching { recorder.stop() }
                runCatching { recorder.reset() }
                runCatching { recorder.release() }
            }
            activeAudioRecorder.value = null
            activeAudioEvidenceFile = null
        }
    }

    LaunchedEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(appUserId) {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (token.isNotBlank()) {
                    fcmToken = token
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                registerFcmToken(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    userId = appUserId,
                                    token = token
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(appUserId, locationPermissionGranted) {
        while (true) {
            if (locationPermissionGranted) {
                val location = currentLocation
                if (location != null) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            sendHeartbeat(
                                apiBaseUrl = BACKEND_BASE_URL,
                                userId = appUserId,
                                location = location,
                                locationLabel = currentLocationLabel,
                                fcmToken = fcmToken
                            )
                        }
                    }

                    val alerts = runCatching {
                        withContext(Dispatchers.IO) {
                            fetchNearbySosAlerts(
                                apiBaseUrl = BACKEND_BASE_URL,
                                userId = appUserId,
                                location = location,
                                radiusM = 1000
                            )
                        }
                    }.getOrDefault(emptyList())

                    nearbySosAlerts = alerts
                    val newAlertIds = alerts.map { it.eventId }.toSet() - seenAlertIds
                    if (newAlertIds.isNotEmpty()) {
                        ensureSosNotificationChannel(context)
                        alerts
                            .filter { alert -> alert.eventId in newAlertIds }
                            .forEach { alert ->
                                showSosNotification(context, alert)
                            }
                        seenAlertIds = seenAlertIds + newAlertIds
                    }
                }
            }
            delay(10_000)
        }
    }

    LaunchedEffect(activeSosExpiresAtMs) {
        while (true) {
            val expiresAt = activeSosExpiresAtMs
            if (expiresAt == null) {
                activeSosRemainingSec = null
                break
            }

            val remaining = ((expiresAt - System.currentTimeMillis()) / 1000L).toInt()
            if (remaining <= 0) {
                activeSosRemainingSec = 0
                activeSosEventId = null
                activeSosExpiresAtMs = null
                sosStatus = "SOS window expired. You can send a new request."
                break
            }

            activeSosRemainingSec = remaining
            delay(1000)
        }
    }

    LaunchedEffect(Unit) {
        if (!introAlreadyPlayed) {
            introAlreadyPlayed = true
        }
    }

    LaunchedEffect(notificationPermissionGranted, activeChatContact?.username) {
        while (true) {
            val token = authSessionStore.getToken()
            if (!token.isNullOrBlank()) {
                val inboxMessages = runCatching {
                    withContext(Dispatchers.IO) {
                        fetchDirectInboxMessagesAfter(
                            apiBaseUrl = BACKEND_BASE_URL,
                            authToken = token,
                            afterMessageId = lastNotifiedDirectMessageId
                        )
                    }
                }.getOrDefault(emptyList())

                if (inboxMessages.isNotEmpty()) {
                    if (notificationPermissionGranted) {
                        ensureChatNotificationChannel(context)
                        inboxMessages.forEach { message ->
                            val isOpenChatForSender = activeChatContact?.username?.equals(
                                message.senderUsername,
                                ignoreCase = true
                            ) == true
                            if (!isOpenChatForSender) {
                                showDirectMessageNotification(context, message)
                            }
                        }
                    }

                    val latestMessageId = inboxMessages.maxOf { it.messageId }
                    if (latestMessageId > lastNotifiedDirectMessageId) {
                        lastNotifiedDirectMessageId = latestMessageId
                        saveLastNotifiedDirectMessageId(context, latestMessageId)
                    }
                }
            }
            delay(4000)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val token = authSessionStore.getToken()
            if (!token.isNullOrBlank()) {
                val shares = runCatching {
                    withContext(Dispatchers.IO) {
                        fetchIncomingLiveLocationShares(
                            apiBaseUrl = BACKEND_BASE_URL,
                            authToken = token
                        )
                    }
                }.getOrDefault(emptyList())

                val nextSharedLocation = shares
                    .sortedByDescending { it.expiresAtEpoch }
                    .firstNotNullOfOrNull { share ->
                        runCatching {
                            withContext(Dispatchers.IO) {
                                fetchLiveUserPresence(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    userId = share.senderUsername
                                )
                            }
                        }.getOrNull()?.let { presence ->
                            SharedLiveLocation(
                                senderUsername = share.senderUsername,
                                senderName = share.senderName,
                                latitude = presence.first,
                                longitude = presence.second,
                                locationLabel = presence.third,
                                ageSeconds = presence.fourth
                            )
                        }
                    }

                activeIncomingSharedLocation = nextSharedLocation
            }
            delay(5000)
        }
    }

    LaunchedEffect(activeChatContact?.username) {
        val chatContact = activeChatContact ?: return@LaunchedEffect
        while (activeChatContact?.username == chatContact.username) {
            val token = authSessionStore.getToken()
            if (!token.isNullOrBlank()) {
                val messages = runCatching {
                    withContext(Dispatchers.IO) {
                        fetchDirectMessageThread(
                            apiBaseUrl = BACKEND_BASE_URL,
                            authToken = token,
                            otherUsername = chatContact.username
                        )
                    }
                }
                if (messages.isSuccess) {
                    threadMessages = messages.getOrDefault(emptyList())
                }
            }
            delay(3000)
        }
    }

    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            onDispose { }
        } else {
            val geocoder = Geocoder(context, Locale.getDefault())
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2000L)
                .setMinUpdateDistanceMeters(20f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    currentLocation = location
                    currentLocationLabel = resolveLocationLabel(location, geocoder)
                }
            }

            fusedLocationClient
                .requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    currentLocationLabel = "Current location unavailable"
                }

            onDispose {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }

    MaterialTheme(colorScheme = DashboardDarkColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Base dark background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A))
            )

            // Radial glow - top right
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x332F2F2F),
                                Color(0x22383838),
                                Color(0x00000000)
                            ),
                            center = Offset(0.85f, -0.15f),
                            radius = 800f
                        )
                    )
            )

            // Radial glow - center left
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x22222222),
                                Color(0x11111111),
                                Color(0x00000000)
                            ),
                            center = Offset(0.1f, 0.4f),
                            radius = 700f
                        )
                    )
            )

            // Vertical gradient overlay for depth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x08FFFFFF),
                                Color(0x04000000),
                                Color(0x0A000000)
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 170.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    // Header section with greeting, location, and activity indicator.
                    DashboardAnimatedItem(index = 0, shouldAnimate = shouldAnimateIntro) {
                        HeaderSection(
                            locationText = currentLocationLabel,
                            currentUsername = currentUsername
                        )
                    }
                }

                item {
                    // Main SOS section with the primary emergency action.
                    DashboardAnimatedItem(index = 1, shouldAnimate = shouldAnimateIntro) {
                        SosSection(
                            statusText = sosStatus,
                            activeSosRemainingSec = activeSosRemainingSec,
                            isActive = activeSosEventId != null,
                            onNavigate = {
                                val location = currentLocation
                                if (location == null) {
                                    sosStatus = "Waiting for your current location fix..."
                                } else {
                                    navigateHomeMapToUser(
                                        latitude = location.latitude,
                                        longitude = location.longitude,
                                        label = currentLocationLabel.takeIf { it.isNotBlank() }
                                            ?: "Your SOS location"
                                    )
                                }
                            },
                            onSosPressed = {
                                val location = currentLocation
                                if (location == null) {
                                    sosStatus = "Waiting for your current location fix..."
                                } else {
                                    if (activeSosEventId != null) {
                                        sosStatus = "SOS already active. Countdown is running."
                                    } else {
                                        sosStatus = "Sending SOS..."
                                        scope.launch {
                                            val response = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    sendSosRequest(
                                                        apiBaseUrl = BACKEND_BASE_URL,
                                                        userId = appUserId,
                                                        location = location,
                                                        locationLabel = currentLocationLabel,
                                                        message = "Emergency help needed nearby"
                                                    )
                                                }
                                            }

                                            sosStatus = response.fold(
                                                onSuccess = { result ->
                                                    activeSosEventId = result.eventId
                                                    activeSosExpiresAtMs = System.currentTimeMillis() + (result.expiresInSeconds * 1000L)
                                                    "SOS sent to ${result.notifiedUsers} nearby user(s) within 1 km."
                                                },
                                                onFailure = {
                                                    "Failed to send SOS. Check network/backend and try again."
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    // Quick action grid for the most-used safety shortcuts.
                    DashboardAnimatedItem(index = 2, shouldAnimate = shouldAnimateIntro) {
                        QuickActionGrid(
                            actions = quickActions,
                            onActionClick = { action ->
                                when (action.shortLabel) {
                                    "SL" -> {
                                        val location = currentLocation
                                        val recipients = trustedContacts
                                            .mapNotNull { contact ->
                                                contact.username.takeIf { it.isNotBlank() }?.let { username ->
                                                    contact to username
                                                }
                                            }

                                        when {
                                            recipients.isEmpty() -> {
                                                sosStatus = "Add trusted contacts with linked usernames to share your location."
                                            }

                                            location == null -> {
                                                sosStatus = "Waiting for your current location fix..."
                                            }

                                            else -> {
                                                val token = authSessionStore.getToken()
                                                if (token.isNullOrBlank()) {
                                                    sosStatus = "Session expired. Login again to share your location."
                                                } else {
                                                    val message = buildShareLocationMessage(
                                                        locationLabel = currentLocationLabel,
                                                        location = location
                                                    )
                                                    scope.launch {
                                                        val results = recipients.map { (_, username) ->
                                                            runCatching {
                                                                withContext(Dispatchers.IO) {
                                                                    startLiveLocationShare(
                                                                        apiBaseUrl = BACKEND_BASE_URL,
                                                                        authToken = token,
                                                                        recipientUsername = username,
                                                                        durationMinutes = LIVE_LOCATION_SHARE_MINUTES
                                                                    )
                                                                    sendDirectMessage(
                                                                        apiBaseUrl = BACKEND_BASE_URL,
                                                                        authToken = token,
                                                                        recipientUsername = username,
                                                                        messageText = message
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        val successCount = results.count { it.isSuccess }
                                                        val failedCount = results.size - successCount
                                                        sosStatus = if (failedCount == 0) {
                                                            "Live location sharing started for $successCount trusted contact(s) for $LIVE_LOCATION_SHARE_MINUTES min."
                                                        } else {
                                                            "Live sharing started for $successCount contact(s). Failed for $failedCount contact(s)."
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "EM" -> {
                                        if (isEvidenceModeActive) {
                                            stopEvidenceCapture()
                                        } else {
                                            showEvidenceModePicker = true
                                        }
                                    }

                                    else -> {
                                        sosStatus = "${action.title} will be available soon."
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    // Area safety map section with a placeholder map panel.
                    DashboardAnimatedItem(index = 3, shouldAnimate = shouldAnimateIntro) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            AreaSafetyMapSection(
                                location = currentLocation,
                                sharedLocation = activeIncomingSharedLocation,
                                navigationTarget = activeNavigationTarget,
                                routePoints = activeNavigationRoute,
                                mapZoom = mapZoomLevel,
                                onZoomIn = { mapZoomLevel = (mapZoomLevel + 1).coerceAtMost(22) },
                                onZoomOut = { mapZoomLevel = (mapZoomLevel - 1).coerceAtLeast(3) },
                                onExpandRequest = { isMapExpanded = true },
                                onNavigateToSharedLocation = activeIncomingSharedLocation?.let { shared ->
                                    {
                                        navigateHomeMapToUser(
                                            latitude = shared.latitude,
                                            longitude = shared.longitude,
                                            label = shared.locationLabel?.takeIf { it.isNotBlank() }
                                                ?: "Shared by ${shared.senderName}"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    // Trusted contacts section with a horizontal contact list.
                    DashboardAnimatedItem(index = 4, shouldAnimate = shouldAnimateIntro) {
                        TrustedContactsSection(
                            contacts = trustedContacts,
                            candidates = trustedContactCandidates,
                            sharedLocation = activeIncomingSharedLocation,
                            onSearchChanged = { query ->
                                val normalized = query.trim().lowercase()
                                val token = authSessionStore.getToken()
                                if (normalized.length < 2 || token.isNullOrBlank()) {
                                    trustedContactCandidates = emptyList()
                                } else {
                                    scope.launch {
                                        val matches = runCatching {
                                            withContext(Dispatchers.IO) {
                                                searchRegisteredUsers(
                                                    apiBaseUrl = BACKEND_BASE_URL,
                                                    authToken = token,
                                                    query = normalized
                                                )
                                            }
                                        }.getOrDefault(emptyList())
                                        trustedContactCandidates = matches
                                    }
                                }
                            },
                            onAddContact = { username ->
                                val normalizedUsername = username.trim().lowercase()
                                if (normalizedUsername.isBlank()) {
                                    sosStatus = "Enter a username."
                                } else {
                                    val token = authSessionStore.getToken()
                                    if (token.isNullOrBlank()) {
                                        sosStatus = "Session expired. Login again to add trusted contacts."
                                    } else {
                                        scope.launch {
                                            val lookup = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    lookupRegisteredUserContactByUsername(
                                                        apiBaseUrl = BACKEND_BASE_URL,
                                                        authToken = token,
                                                        username = normalizedUsername
                                                    )
                                                }
                                            }

                                            val contact = lookup.getOrNull()
                                            if (contact == null) {
                                                val failureMessage = lookup.exceptionOrNull()?.message.orEmpty()
                                                sosStatus = when {
                                                    failureMessage.contains("401") -> "Session expired. Login again to add trusted contacts."
                                                    failureMessage.contains("403") -> "Permission denied. Please login again."
                                                    failureMessage.contains("404") -> "No app user found for username '$normalizedUsername'."
                                                    failureMessage.contains("Unable to connect", ignoreCase = true) -> "Network issue. Check internet and try again."
                                                    failureMessage.isNotBlank() -> failureMessage
                                                    else -> "Could not add trusted contact right now. Try again."
                                                }
                                            } else if (trustedContacts.any { it.id == contact.id }) {
                                                sosStatus = "${contact.name} is already in trusted contacts."
                                            } else {
                                                val updated = trustedContacts + contact
                                                trustedContacts = updated
                                                saveTrustedContacts(context, updated)
                                                trustedContactCandidates = emptyList()
                                                sosStatus = "Added ${contact.name} to trusted contacts."
                                            }
                                        }
                                    }
                                }
                            },
                            onRemoveContact = { contact ->
                                val updated = trustedContacts.filterNot { it.id == contact.id }
                                trustedContacts = updated
                                saveTrustedContacts(context, updated)
                                sosStatus = "Removed ${contact.name} from trusted contacts."
                            },
                            onCallContact = { contact ->
                                openDialer(context, contact.phoneNumber)
                            },
                            onNavigateContact = { contact ->
                                val shared = activeIncomingSharedLocation
                                val contactUsername = contact.username.trim()
                                if (
                                    shared == null ||
                                    contactUsername.isBlank() ||
                                    !shared.senderUsername.equals(contactUsername, ignoreCase = true)
                                ) {
                                    sosStatus = "No active shared location from ${contact.name} yet."
                                } else {
                                    navigateHomeMapToUser(
                                        latitude = shared.latitude,
                                        longitude = shared.longitude,
                                        label = shared.locationLabel?.takeIf { it.isNotBlank() }
                                            ?: "Live from ${shared.senderName}"
                                    )
                                }
                            },
                            onMessageContact = { contact ->
                                if (contact.username.isBlank()) {
                                    val token = authSessionStore.getToken()
                                    if (token.isNullOrBlank()) {
                                        sosStatus = "Session expired. Login again to message contacts."
                                    } else {
                                        val legacyUserId = contact.id.removePrefix("user-").toIntOrNull()
                                        if (legacyUserId != null) {
                                            scope.launch {
                                                val recovered = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        lookupRegisteredUserContactById(
                                                            apiBaseUrl = BACKEND_BASE_URL,
                                                            authToken = token,
                                                            userId = legacyUserId
                                                        )
                                                    }
                                                }.getOrNull()

                                                if (recovered != null) {
                                                    val merged = contact.copy(
                                                        name = recovered.name,
                                                        phoneNumber = recovered.phoneNumber,
                                                        username = recovered.username
                                                    )
                                                    val updated = trustedContacts.map {
                                                        if (it.id == contact.id) merged else it
                                                    }
                                                    trustedContacts = updated
                                                    saveTrustedContacts(context, updated)
                                                    activeChatContact = merged
                                                } else {
                                                    contactPendingUsernameLink = contact
                                                    usernameLinkQuery = ""
                                                    usernameLinkCandidates = emptyList()
                                                    sosStatus = "Pick the correct username to link this contact."
                                                }
                                            }
                                        } else {
                                            contactPendingUsernameLink = contact
                                            usernameLinkQuery = ""
                                            usernameLinkCandidates = emptyList()
                                            sosStatus = "Pick the correct username to link this contact."
                                        }
                                    }
                                } else {
                                    activeChatContact = contact
                                }
                            }
                        )
                    }
                }

                item {
                    DashboardAnimatedItem(index = 5, shouldAnimate = shouldAnimateIntro) {
                        SosNearbySection(
                            alerts = nearbySosAlerts,
                            onRespond = { alert, response ->
                                scope.launch {
                                    val result = runCatching {
                                        withContext(Dispatchers.IO) {
                                            sendSosResponse(
                                                apiBaseUrl = BACKEND_BASE_URL,
                                                eventId = alert.eventId,
                                                responderId = appUserId,
                                                response = response
                                            )
                                        }
                                    }
                                    if (result.isSuccess) {
                                        sosStatus = "Response sent: ${response.uppercase()} for nearby SOS."
                                    } else {
                                        sosStatus = "Could not send response. Try again."
                                    }
                                }
                            },
                            onNavigate = { alert ->
                                navigateHomeMapToUser(
                                    latitude = alert.latitude,
                                    longitude = alert.longitude,
                                    label = alert.locationLabel ?: "SOS requester"
                                )
                            }
                        )
                    }
                }
            }

            if (isMapExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable { isMapExpanded = false }
                )

                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.92f)
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                    border = BorderStroke(1.dp, Color(0x66A7C7FF))
                ) {
                    LiveLocationMap(
                        location = currentLocation,
                        sharedLocation = activeIncomingSharedLocation,
                        navigationTarget = activeNavigationTarget,
                        routePoints = activeNavigationRoute,
                        mapZoom = mapZoomLevel,
                        onZoomIn = { mapZoomLevel = (mapZoomLevel + 1).coerceAtMost(22) },
                        onZoomOut = { mapZoomLevel = (mapZoomLevel - 1).coerceAtLeast(3) },
                        modifier = Modifier.fillMaxSize(),
                        onExpandToggle = { isMapExpanded = false },
                        isExpanded = true
                    )
                }
            }

            activeChatContact?.let { chatContact ->
                InAppChatDialog(
                    contact = chatContact,
                    currentUsername = currentUsername,
                    messages = threadMessages,
                    messageDraft = pendingMessageText,
                    statusText = chatStatusText,
                    onMessageDraftChange = { pendingMessageText = it },
                    onDismiss = {
                        activeChatContact = null
                        threadMessages = emptyList()
                        pendingMessageText = ""
                        chatStatusText = null
                    },
                    onSend = {
                        val token = authSessionStore.getToken()
                        if (token.isNullOrBlank()) {
                            chatStatusText = "Session expired. Please login again."
                        } else if (pendingMessageText.trim().isBlank()) {
                            chatStatusText = "Type a message first."
                        } else {
                            val messageBody = pendingMessageText.trim()
                            scope.launch {
                                val sent = runCatching {
                                    withContext(Dispatchers.IO) {
                                        sendDirectMessage(
                                            apiBaseUrl = BACKEND_BASE_URL,
                                            authToken = token,
                                            recipientUsername = chatContact.username,
                                            messageText = messageBody
                                        )
                                    }
                                }
                                if (sent.isSuccess) {
                                    pendingMessageText = ""
                                    chatStatusText = null
                                    threadMessages = runCatching {
                                        withContext(Dispatchers.IO) {
                                            fetchDirectMessageThread(
                                                apiBaseUrl = BACKEND_BASE_URL,
                                                authToken = token,
                                                otherUsername = chatContact.username
                                            )
                                        }
                                    }.getOrDefault(threadMessages)
                                } else {
                                    val failureText = sent.exceptionOrNull()?.message.orEmpty()
                                    chatStatusText = when {
                                        failureText.contains("401") -> "Session expired. Please login again."
                                        failureText.contains("403") -> "Permission denied. Please login again."
                                        failureText.isNotBlank() -> failureText
                                        else -> "Could not send message."
                                    }
                                }
                            }
                        }
                    }
                )
            }

            contactPendingUsernameLink?.let { contactToLink ->
                LinkUsernameDialog(
                    contact = contactToLink,
                    query = usernameLinkQuery,
                    candidates = usernameLinkCandidates,
                    onQueryChanged = { query ->
                        usernameLinkQuery = query
                        val token = authSessionStore.getToken()
                        val normalized = query.trim().lowercase()
                        if (token.isNullOrBlank() || normalized.length < 2) {
                            usernameLinkCandidates = emptyList()
                        } else {
                            scope.launch {
                                usernameLinkCandidates = runCatching {
                                    withContext(Dispatchers.IO) {
                                        searchRegisteredUsers(
                                            apiBaseUrl = BACKEND_BASE_URL,
                                            authToken = token,
                                            query = normalized
                                        )
                                    }
                                }.getOrDefault(emptyList())
                            }
                        }
                    },
                    onSelect = { candidate ->
                        val linked = contactToLink.copy(
                            name = candidate.name,
                            username = candidate.username
                        )
                        val updated = trustedContacts.map {
                            if (it.id == contactToLink.id) linked else it
                        }
                        trustedContacts = updated
                        saveTrustedContacts(context, updated)
                        contactPendingUsernameLink = null
                        usernameLinkCandidates = emptyList()
                        usernameLinkQuery = ""
                        activeChatContact = linked
                    },
                    onDismiss = {
                        contactPendingUsernameLink = null
                        usernameLinkCandidates = emptyList()
                        usernameLinkQuery = ""
                    }
                )
            }
        }
    }

    if (showEvidenceModePicker) {
        AlertDialog(
            onDismissRequest = { showEvidenceModePicker = false },
            title = { Text(text = "Start Evidence Mode") },
            text = { Text(text = "Choose what to capture. Tap Evidence Mode again anytime to stop.") },
            confirmButton = {
                TextButton(onClick = {
                    showEvidenceModePicker = false
                    val mode = EvidenceCaptureMode.AUDIO
                    if (hasEvidencePermission(mode)) {
                        startAudioEvidenceCapture()
                    } else {
                        pendingEvidenceCaptureMode = mode
                        evidencePermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    }
                }) {
                    Text("Voice Recorder")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showEvidenceModePicker = false
                        val mode = EvidenceCaptureMode.VIDEO
                        if (hasEvidencePermission(mode)) {
                            val videoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                            if (videoIntent.resolveActivity(context.packageManager) != null) {
                                isEvidenceModeActive = true
                                activeEvidenceCaptureMode = EvidenceCaptureMode.VIDEO.name
                                sosStatus = "Camera opened for evidence video."
                                videoEvidenceLauncher.launch(videoIntent)
                            } else {
                                sosStatus = "No camera app available for video capture."
                            }
                        } else {
                            pendingEvidenceCaptureMode = mode
                            evidencePermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                    }) {
                        Text("Camera")
                    }
                    TextButton(onClick = { showEvidenceModePicker = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun LinkUsernameDialog(
    contact: TrustedContact,
    query: String,
    candidates: List<TrustedContactCandidate>,
    onQueryChanged: (String) -> Unit,
    onSelect: (TrustedContactCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Username for ${contact.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    label = { Text("Search username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    )
                )
                if (candidates.isEmpty()) {
                    Text(
                        text = "Type at least 2 characters to search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(UnifiedButtonGap)) {
                        candidates.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(candidate) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@${candidate.username} (${candidate.name})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(
                                    onClick = { onSelect(candidate) },
                                    modifier = Modifier.height(UnifiedButtonHeight)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text("Use")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(UnifiedButtonHeight)
            ) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(UnifiedButtonHeight)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun InAppChatDialog(
    contact: TrustedContact,
    currentUsername: String,
    messages: List<DirectMessageItem>,
    messageDraft: String,
    statusText: String?,
    onMessageDraftChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat with @${contact.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Color(0xFF1F1F1F), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        Text(
                            text = "No messages yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            messages.forEach { msg ->
                                val isMine = msg.senderUsername.equals(currentUsername, ignoreCase = true)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                                ) {
                                    Text(
                                        text = msg.messageText,
                                        modifier = Modifier
                                            .background(
                                                if (isMine) Color(0xFF2C2C2C) else Color(0xFF303030),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = onMessageDraftChange,
                    label = { Text("Type message") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                statusText?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                modifier = Modifier.height(UnifiedButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Send")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(UnifiedButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Close")
            }
        }
    )
}

@Composable
private fun DashboardAnimatedItem(
    index: Int,
    shouldAnimate: Boolean,
    content: @Composable () -> Unit
) {
    var visible by remember(index, shouldAnimate) { mutableStateOf(!shouldAnimate) }

    LaunchedEffect(index, shouldAnimate) {
        if (shouldAnimate) {
            delay(80L * index)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 420)) +
            slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(durationMillis = 460)
            )
    ) {
        content()
    }
}

@Composable
private fun HeaderSection(
    locationText: String,
    currentUsername: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Hey ${currentUsername.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} 👋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "Online",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun resolveLocationLabel(location: Location, geocoder: Geocoder): String {
    val latLngText = String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
    if (!Geocoder.isPresent()) {
        return latLngText
    }

    return try {
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val address = addresses?.firstOrNull()
        if (address == null) {
            latLngText
        } else {
            val namedParts = listOfNotNull(
                address.subLocality,
                address.locality,
                address.subAdminArea,
                address.adminArea,
                address.countryName
            )
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            if (namedParts.isNotEmpty()) {
                namedParts.take(3).joinToString(", ")
            } else {
                val maxIndex = address.maxAddressLineIndex
                if (maxIndex >= 0) {
                    val line = (0..maxIndex)
                        .asSequence()
                        .mapNotNull { index -> address.getAddressLine(index) }
                        .map { it.trim() }
                        .firstOrNull { it.isNotBlank() }
                    line ?: latLngText
                } else {
                    val featureName = address.featureName?.trim().orEmpty()
                    if (featureName.isNotBlank()) featureName else latLngText
                }
            }
        }
    } catch (_: IOException) {
        latLngText
    } catch (_: IllegalArgumentException) {
        latLngText
    }
}

@Composable
private fun SosSection(
    statusText: String?,
    activeSosRemainingSec: Int?,
    isActive: Boolean,
    onNavigate: () -> Unit,
    onSosPressed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SosButton(
            onSosPressed = onSosPressed,
            isActive = isActive
        )
        if (activeSosRemainingSec != null) {
            Text(
                text = "SOS active - auto expires in ${activeSosRemainingSec}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Text(text = "Navigate to SOS location")
            }
        }
        if (!statusText.isNullOrBlank()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SosButton(
    onSosPressed: () -> Unit,
    isActive: Boolean
) {
    var pressedDuration by remember { mutableStateOf(0L) }
    var isCurrentlyPressed by remember { mutableStateOf(false) }
    
    // Animate ring scale based on press duration
    val ringOpacity by animateFloatAsState(
        targetValue = if (isCurrentlyPressed) minOf((pressedDuration / 2000f), 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "ringOpacity"
    )
    
    val ringScale by animateFloatAsState(
        targetValue = if (isCurrentlyPressed) 1f + (pressedDuration / 1000f) * 0.3f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "ringScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isCurrentlyPressed = true
                    pressedDuration = 0L
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        if (!event.changes.first().pressed) {
                            if (pressedDuration >= 2000L) {
                                onSosPressed()
                            }
                            isCurrentlyPressed = false
                            pressedDuration = 0L
                            break
                        }
                    }
                }
            }
    ) {
        // Animated outer ring
        if (isCurrentlyPressed) {
            repeat(3) { ringIndex ->
                val delayedOpacity = (ringOpacity - (ringIndex * 0.15f)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .size(200.dp + (30.dp * ringIndex))
                        .graphicsLayer {
                            scaleX = ringScale
                            scaleY = ringScale
                            alpha = delayedOpacity
                        }
                        .border(
                            width = 2.dp,
                            color = ButtonBorder.copy(alpha = delayedOpacity),
                            shape = CircleShape
                        )
                )
            }
        }
        
        // Main SOS button with solid charcoal texture
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(ButtonSurface)
                .border(
                    width = 2.dp,
                    color = ButtonBorder,
                    shape = CircleShape
                )
                .graphicsLayer {
                    scaleX = if (isCurrentlyPressed && pressedDuration >= 2000L) 0.95f else 1f
                    scaleY = if (isCurrentlyPressed && pressedDuration >= 2000L) 0.95f else 1f
                }
        ) {
            // Texture overlay (subtle diagonal lines effect)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x18FFFFFF),
                                Color.Transparent,
                                Color(0x12000000)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(200f, 200f)
                        )
                    )
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isActive) "ACTIVE" else "SOS",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isActive) "Live request" else when {
                        isCurrentlyPressed && pressedDuration < 2000 -> "${(pressedDuration / 100).toInt() / 10f}s"
                        else -> "Hold 2s"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        }
    }
    
    // Track press duration
    LaunchedEffect(isCurrentlyPressed) {
        if (isCurrentlyPressed) {
            while (isCurrentlyPressed && pressedDuration < 2000L) {
                delay(50)
                pressedDuration += 50
            }
        }
    }
}

@Composable
private fun QuickActionGrid(
    actions: List<QuickActionItem>,
    onActionClick: (QuickActionItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "Quick Actions")

        actions.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    ActionCard(
                        item = item,
                        onClick = { onActionClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun ActionCard(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(116.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GlassBorderColor),
        colors = CardDefaults.cardColors(
            containerColor = GlassCardColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlaceholderIcon(label = item.shortLabel, icon = item.icon)
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AreaSafetyMapSection(
    location: Location?,
    sharedLocation: SharedLiveLocation?,
    navigationTarget: MapNavigationTarget?,
    routePoints: List<GeoPoint>,
    mapZoom: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onExpandRequest: () -> Unit,
    onNavigateToSharedLocation: (() -> Unit)?
) {
    val context = LocalContext.current
    val streetViewTarget = navigationTarget?.let { GeoPoint(it.latitude, it.longitude) }
        ?: location?.let { GeoPoint(it.latitude, it.longitude) }
        ?: sharedLocation?.let { GeoPoint(it.latitude, it.longitude) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LiveLocationMap(
            location = location,
            sharedLocation = sharedLocation,
            navigationTarget = navigationTarget,
            routePoints = routePoints,
            mapZoom = mapZoom,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            onExpandToggle = onExpandRequest,
            isExpanded = false
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    streetViewTarget?.let {
                        openStreetViewAtLocation(
                            context = context,
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ButtonBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(text = "Street View")
            }

            OutlinedButton(
                onClick = onExpandRequest,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ButtonBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(text = "Expand map")
            }
        }

        if (sharedLocation != null && onNavigateToSharedLocation != null) {
            OutlinedButton(
                onClick = onNavigateToSharedLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ButtonBorder),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(text = "Navigate to shared trusted contact")
            }
        }
    }
}

@Composable
private fun LiveLocationMap(
    location: Location?,
    sharedLocation: SharedLiveLocation?,
    navigationTarget: MapNavigationTarget?,
    routePoints: List<GeoPoint>,
    mapZoom: Int,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier,
    onExpandToggle: () -> Unit,
    isExpanded: Boolean
) {
    val context = LocalContext.current
    var hasCenteredMap by remember { mutableStateOf(false) }
    val mapView = remember(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 22.0
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_POINTER_UP -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose { mapView.onDetach() }
    }

    val centerPoint = when {
        location != null && navigationTarget != null -> GeoPoint(
            (location.latitude + navigationTarget.latitude) / 2.0,
            (location.longitude + navigationTarget.longitude) / 2.0
        )
        location != null && sharedLocation != null -> GeoPoint(
            (location.latitude + sharedLocation.latitude) / 2.0,
            (location.longitude + sharedLocation.longitude) / 2.0
        )
        navigationTarget != null -> GeoPoint(navigationTarget.latitude, navigationTarget.longitude)
        sharedLocation != null -> GeoPoint(sharedLocation.latitude, sharedLocation.longitude)
        location != null -> GeoPoint(location.latitude, location.longitude)
        else -> null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0B1728)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val userPoint = location?.let { GeoPoint(it.latitude, it.longitude) }
                val sharedPoint = sharedLocation?.let { GeoPoint(it.latitude, it.longitude) }
                val destinationPoint = navigationTarget?.let { GeoPoint(it.latitude, it.longitude) }
                val targetCenter = centerPoint ?: userPoint ?: sharedPoint ?: destinationPoint
                val routeSignature = if (routePoints.isNotEmpty()) {
                    val first = routePoints.first()
                    val last = routePoints.last()
                    "${first.latitude},${first.longitude}|${last.latitude},${last.longitude}|${routePoints.size}"
                } else {
                    "no_route"
                }
                val markerSignature =
                    "${userPoint?.latitude},${userPoint?.longitude}|" +
                        "${sharedPoint?.latitude},${sharedPoint?.longitude}|" +
                        "${destinationPoint?.latitude},${destinationPoint?.longitude}|$routeSignature"

                if (abs(view.zoomLevelDouble - mapZoom.toDouble()) > 0.01) {
                    view.controller.setZoom(mapZoom.toDouble())
                }
                if (!hasCenteredMap || view.tag != markerSignature) {
                    targetCenter?.let {
                        view.controller.setCenter(it)
                        hasCenteredMap = true
                    }
                }
                if (view.tag != markerSignature) {
                    view.overlays.clear()

                    if (routePoints.size >= 2) {
                        val routeOverlay = Polyline().apply {
                            setPoints(routePoints)
                            outlinePaint.color = android.graphics.Color.parseColor("#0D47A1")
                            outlinePaint.strokeWidth = 11f
                            outlinePaint.isAntiAlias = true
                        }
                        view.overlays.add(routeOverlay)
                    }

                    userPoint?.let { point ->
                        view.overlays.add(
                            createMapMarker(
                                context = context,
                                mapView = view,
                                position = point,
                                title = "You",
                                snippet = "Current location",
                                fillColor = android.graphics.Color.parseColor("#4DA3FF"),
                                strokeColor = android.graphics.Color.WHITE
                            )
                        )
                    }

                    sharedPoint?.let { point ->
                        view.overlays.add(
                            createMapMarker(
                                context = context,
                                mapView = view,
                                position = point,
                                title = sharedLocation?.senderName ?: "Shared location",
                                snippet = sharedLocation?.locationLabel ?: "Live shared point",
                                fillColor = android.graphics.Color.parseColor("#4BE07A"),
                                strokeColor = android.graphics.Color.WHITE
                            )
                        )
                    }

                    destinationPoint?.let { point ->
                        view.overlays.add(
                            createMapMarker(
                                context = context,
                                mapView = view,
                                position = point,
                                title = "Navigate to user",
                                snippet = navigationTarget?.label ?: "Destination",
                                fillColor = android.graphics.Color.parseColor("#0D47A1"),
                                strokeColor = android.graphics.Color.WHITE
                            )
                        )
                    }

                    view.tag = markerSignature
                    view.invalidate()
                }
            }
        )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xCC172235),
                border = BorderStroke(1.dp, Color(0x88A7C7FF))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onZoomIn,
                        modifier = Modifier
                            .width(40.dp)
                            .height(30.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonSurface,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, ButtonBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Zoom in",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "Z$mapZoom",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Button(
                        onClick = onZoomOut,
                        modifier = Modifier
                            .width(40.dp)
                            .height(30.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonSurface,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, ButtonBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Zoom out",
                            tint = Color.White
                        )
                    }
                }
            }

            if (sharedLocation != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xCC1A2E1A),
                    border = BorderStroke(1.dp, Color(0xAA7BFF9B))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "You + Shared",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCCE7FF)
                        )
                        Text(
                            text = "Live: ${sharedLocation.senderName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFB6FFC5),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sharedLocation.locationLabel ?: "Updating location...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE9FCEB)
                        )
                    }
                }
            }
        }
    }
private fun latLonToWorldPixels(
    latitude: Double,
    longitude: Double,
    zoom: Int
): Pair<Double, Double> {
    val scale = (1 shl zoom) * HOME_TILE_SIZE_PX.toDouble()
    val worldX = ((longitude + 180.0) / 360.0) * scale
    val sinLatitude = kotlin.math.sin(Math.toRadians(latitude))
    val worldY = (0.5 - kotlin.math.ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * Math.PI)) * scale
    return Pair(worldX, worldY)
}

private fun worldPixelsToLatLon(
    worldX: Double,
    worldY: Double,
    zoom: Int
): Pair<Double, Double> {
    val scale = (1 shl zoom) * HOME_TILE_SIZE_PX.toDouble()
    val longitude = worldX / scale * 360.0 - 180.0
    val n = Math.PI - 2.0 * Math.PI * worldY / scale
    val latitude = Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
    return Pair(latitude, longitude)
}

private fun computeMarkerOffsetOnTile(
    markerLatitude: Double,
    markerLongitude: Double,
    centerLatitude: Double,
    centerLongitude: Double,
    zoom: Int
): Pair<Float, Float> {
    val worldTiles = 2.0.pow(zoom.toDouble())
    fun toWorldX(longitude: Double): Double {
        return ((longitude + 180.0) / 360.0) * worldTiles * HOME_TILE_SIZE_PX
    }
    fun toWorldY(latitude: Double): Double {
        val latRad = Math.toRadians(latitude)
        return (
            (1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0
            ) * worldTiles * HOME_TILE_SIZE_PX
    }

    val dx = toWorldX(markerLongitude) - toWorldX(centerLongitude)
    val dy = toWorldY(markerLatitude) - toWorldY(centerLatitude)
    return Pair(dx.toFloat(), dy.toFloat())
}

private fun fetchMapTile(latitude: Double, longitude: Double, zoom: Int): Bitmap? {
    val latRad = Math.toRadians(latitude)
    val n = 1 shl zoom
    val xTile = ((longitude + 180.0) / 360.0 * n).toInt()
    val yTile = ((1.0 - kotlin.math.ln(kotlin.math.tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()

    val safeX = xTile.coerceIn(0, n - 1)
    val safeY = yTile.coerceIn(0, n - 1)
    for (template in HOME_MAP_TILE_URLS) {
        val connection = (URL(template.format(Locale.US, zoom, safeX, safeY)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = HOME_MAP_TIMEOUT_MS
            readTimeout = HOME_MAP_TIMEOUT_MS
            setRequestProperty("User-Agent", "SmartCommunitySOS/1.0 (clean-map)")
        }

        try {
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)?.let { return it }
                }
            }
        } catch (_: Exception) {
            // Try the next provider.
        } finally {
            connection.disconnect()
        }
    }

    return null
}

private data class SosRequestResult(
    val eventId: String,
    val notifiedUsers: Int,
    val expiresInSeconds: Int
)

@Composable
private fun SosNearbySection(
    alerts: List<SosNearbyAlert>,
    onRespond: (SosNearbyAlert, String) -> Unit,
    onNavigate: (SosNearbyAlert) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Nearby SOS Requests",
            trailingText = "${alerts.size} active"
        )

        if (alerts.isEmpty()) {
            Card(
                border = BorderStroke(1.dp, GlassBorderColor),
                colors = CardDefaults.cardColors(containerColor = GlassCardColor)
            ) {
                Text(
                    text = "No nearby SOS requests right now.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            alerts.take(5).forEach { alert ->
                Card(
                    border = BorderStroke(1.dp, GlassBorderColor),
                    colors = CardDefaults.cardColors(containerColor = GlassCardColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = alert.message,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${alert.distanceM.toInt()}m away • ${alert.acceptedCount} helper(s) accepted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = alert.locationLabel ?: String.format(
                                Locale.US,
                                "%.4f, %.4f",
                                alert.latitude,
                                alert.longitude
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (alert.userResponse == null) {
                            val acceptInteraction = remember { MutableInteractionSource() }
                            val declineInteraction = remember { MutableInteractionSource() }
                            val acceptPressed by acceptInteraction.collectIsPressedAsState()
                            val declinePressed by declineInteraction.collectIsPressedAsState()
                            val acceptScale by animateFloatAsState(
                                targetValue = if (acceptPressed) 0.96f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
                                label = "acceptScale"
                            )
                            val declineScale by animateFloatAsState(
                                targetValue = if (declinePressed) 0.96f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
                                label = "declineScale"
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(UnifiedButtonGap)) {
                                Button(
                                    interactionSource = acceptInteraction,
                                    onClick = { onRespond(alert, "accept") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(UnifiedButtonHeight)
                                        .graphicsLayer {
                                            scaleX = acceptScale
                                            scaleY = acceptScale
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButtonSurface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.dp, ButtonBorder)
                                ) {
                                    Text(text = "Accept")
                                }
                                Button(
                                    interactionSource = declineInteraction,
                                    onClick = { onRespond(alert, "decline") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(UnifiedButtonHeight)
                                        .graphicsLayer {
                                            scaleX = declineScale
                                            scaleY = declineScale
                                        },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ButtonSurface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = BorderStroke(1.dp, ButtonBorder)
                                ) {
                                    Text(text = "Decline")
                                }
                            }
                        } else {
                            Text(
                                text = "Your response: ${alert.userResponse.uppercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { onNavigate(alert) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(UnifiedButtonHeight),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButtonSurface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, ButtonBorder)
                        ) {
                                            Text(text = "Navigate to user")
                        }
                    }
                }
            }
        }
    }
}

private fun openNavigationToRequester(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String
) {
    val googleUri = Uri.parse("google.navigation:q=$latitude,$longitude")
    val googleIntent = Intent(Intent.ACTION_VIEW, googleUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val fallbackUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
    val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    runCatching {
        if (googleIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(googleIntent)
        } else if (fallbackIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(fallbackIntent)
        }
    }
}

private fun openDialer(context: Context, phoneNumber: String) {
    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        if (dialIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(dialIntent)
        }
    }
}

private fun openSmsComposer(context: Context, phoneNumber: String) {
    openSmsComposerForContacts(
        context = context,
        phoneNumbers = listOf(phoneNumber),
        message = "Sharing my safety update."
    )
}

private fun openSmsComposerForContacts(
    context: Context,
    phoneNumbers: List<String>,
    message: String
): Boolean {
    if (phoneNumbers.isEmpty()) {
        return false
    }

    val recipients = phoneNumbers
        .map { sanitizePhoneNumber(it) }
        .filter { it.isNotBlank() }
        .distinct()
    if (recipients.isEmpty()) {
        return false
    }

    val joinedRecipients = recipients.joinToString(separator = ";")
    val smsIntent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("smsto:${Uri.encode(joinedRecipients)}")
    ).apply {
        putExtra("sms_body", message)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return runCatching {
        if (smsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(smsIntent)
            true
        } else {
            false
        }
    }.getOrDefault(false)
}

private fun copyContentUriToEvidenceFile(
    context: Context,
    sourceUri: Uri,
    fallbackExtension: String
): File? {
    val outputDir = File(context.getExternalFilesDir(null), "evidence")
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    val extension = fallbackExtension.ifBlank { ".bin" }
    val outputFile = File(outputDir, "evidence_${System.currentTimeMillis()}$extension")
    return runCatching {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        outputFile.takeIf { it.exists() && it.length() > 0L }
    }.getOrNull()
}

private fun uploadEvidenceAndNotifyTrustedContacts(
    apiBaseUrl: String,
    authToken: String,
    recipientUsernames: List<String>,
    evidenceFile: File,
    mimeType: String
): EvidenceDispatchResult {
    val boundary = "----SOSyncBoundary${System.currentTimeMillis()}"
    val uploadUrl = "$apiBaseUrl/evidence/upload"
    val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = HOME_API_TIMEOUT_MS
        readTimeout = HOME_API_TIMEOUT_MS
        doOutput = true
        setRequestProperty("Authorization", "Bearer $authToken")
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    }

    val uploadResponse = try {
        connection.outputStream.use { stream ->
            val writer = stream.bufferedWriter(Charsets.UTF_8)
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${evidenceFile.name}\"\r\n")
            writer.append("Content-Type: $mimeType\r\n\r\n")
            writer.flush()

            evidenceFile.inputStream().use { input ->
                input.copyTo(stream)
            }

            writer.append("\r\n--$boundary--\r\n")
            writer.flush()
        }

        val responseCode = connection.responseCode
        val bodyText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        if (responseCode !in 200..299) {
            val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
            throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Evidence upload failed (HTTP $responseCode).")
        }

        JSONObject(bodyText)
    } finally {
        connection.disconnect()
    }

    val evidenceUrl = uploadResponse.optString("evidence_url")
    if (evidenceUrl.isBlank()) {
        throw IllegalStateException("Evidence upload did not return a valid URL.")
    }

    var deliveredCount = 0
    recipientUsernames.forEach { username ->
        runCatching {
            sendDirectMessage(
                apiBaseUrl = apiBaseUrl,
                authToken = authToken,
                recipientUsername = username,
                messageText = "Emergency evidence: $evidenceUrl"
            )
        }.onSuccess {
            deliveredCount += 1
        }
    }

    return EvidenceDispatchResult(
        evidenceUrl = evidenceUrl,
        deliveredCount = deliveredCount
    )
}

private fun buildShareLocationMessage(locationLabel: String, location: Location): String {
    val mapsLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    val label = locationLabel.takeIf { it.isNotBlank() } ?: "Current location"
    return "Emergency check-in: I am here now. $label. Open map: $mapsLink"
}

private fun loadTrustedContacts(context: Context): List<TrustedContact> {
    val prefs = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    val stored = prefs.getString(TRUSTED_CONTACTS_PREF_KEY, null)
    if (stored.isNullOrBlank()) {
        return defaultTrustedContacts
    }

    return runCatching {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                val phone = item.optString("phone")
                val username = item.optString("username")
                if (id.isNotBlank() && name.isNotBlank() && phone.isNotBlank()) {
                    add(
                        TrustedContact(
                            id = id,
                            name = name,
                            phoneNumber = phone,
                            username = username
                        )
                    )
                }
            }
        }
    }.getOrElse {
        defaultTrustedContacts
    }
}

private fun saveTrustedContacts(context: Context, contacts: List<TrustedContact>) {
    val array = JSONArray()
    contacts.forEach { contact ->
        array.put(
            JSONObject()
                .put("id", contact.id)
                .put("name", contact.name)
                .put("phone", contact.phoneNumber)
                .put("username", contact.username)
        )
    }

    val prefs = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString(TRUSTED_CONTACTS_PREF_KEY, array.toString()).apply()
}

private fun loadLastNotifiedDirectMessageId(context: Context): Long {
    val prefs = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    return prefs.getLong(LAST_DIRECT_MESSAGE_ID_PREF_KEY, 0L)
}

private fun saveLastNotifiedDirectMessageId(context: Context, messageId: Long) {
    val prefs = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    prefs.edit().putLong(LAST_DIRECT_MESSAGE_ID_PREF_KEY, messageId).apply()
}

private fun sanitizePhoneNumber(input: String): String {
    return input.filter { it.isDigit() || it == '+' }
}

private fun lookupRegisteredUserContactByUsername(
    apiBaseUrl: String,
    authToken: String,
    username: String
): TrustedContact {
    val normalized = username.trim().lowercase()
    val encodedUsername = Uri.encode(normalized)
    val response = getJsonAuthorized(
        "$apiBaseUrl/users/lookup?username=$encodedUsername",
        authToken
    )

    return TrustedContact(
        id = "user-${response.optInt("user_id")}",
        name = response.optString("name"),
        phoneNumber = sanitizePhoneNumber(response.optString("phone_number")),
        username = response.optString("username")
    )
}

private fun lookupRegisteredUserContactById(
    apiBaseUrl: String,
    authToken: String,
    userId: Int
): TrustedContact {
    val response = getJsonAuthorized(
        "$apiBaseUrl/users/by-id?user_id=$userId",
        authToken
    )
    return TrustedContact(
        id = "user-${response.optInt("user_id")}",
        name = response.optString("name"),
        phoneNumber = sanitizePhoneNumber(response.optString("phone_number")),
        username = response.optString("username")
    )
}

private fun searchRegisteredUsers(
    apiBaseUrl: String,
    authToken: String,
    query: String
): List<TrustedContactCandidate> {
    val encodedQuery = Uri.encode(query)
    val response = getJsonAuthorized(
        "$apiBaseUrl/users/search?query=$encodedQuery",
        authToken
    )
    val array = response.optJSONArray("results") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                TrustedContactCandidate(
                    userId = item.optInt("user_id"),
                    name = item.optString("name"),
                    username = item.optString("username")
                )
            )
        }
    }
}

private fun getJsonAuthorized(url: String, authToken: String): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = HOME_API_TIMEOUT_MS
    connection.readTimeout = HOME_API_TIMEOUT_MS
    connection.setRequestProperty("Authorization", "Bearer $authToken")

    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val detail = readErrorDetail(connection)
            throw IllegalStateException(detail ?: "HTTP $responseCode from $url")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

private fun postJsonAuthorized(url: String, authToken: String, payload: JSONObject): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = HOME_API_TIMEOUT_MS
    connection.readTimeout = HOME_API_TIMEOUT_MS
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Authorization", "Bearer $authToken")
    connection.doOutput = true

    return try {
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload.toString())
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val detail = readErrorDetail(connection)
            throw IllegalStateException(detail ?: "HTTP $responseCode from $url")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body.ifBlank { "{}" })
    } finally {
        connection.disconnect()
    }
}

private fun sendDirectMessage(
    apiBaseUrl: String,
    authToken: String,
    recipientUsername: String,
    messageText: String
) {
    val payload = JSONObject()
        .put("recipient_username", recipientUsername)
        .put("message_text", messageText)
    postJsonAuthorized("$apiBaseUrl/messages/send", authToken, payload)
}

private fun startLiveLocationShare(
    apiBaseUrl: String,
    authToken: String,
    recipientUsername: String,
    durationMinutes: Int
) {
    val payload = JSONObject()
        .put("recipient_username", recipientUsername)
        .put("duration_minutes", durationMinutes)
    postJsonAuthorized("$apiBaseUrl/location-share/start", authToken, payload)
}

private fun fetchIncomingLiveLocationShares(
    apiBaseUrl: String,
    authToken: String
): List<LiveLocationShareItem> {
    val response = getJsonAuthorized(
        "$apiBaseUrl/location-share/incoming",
        authToken
    )
    val array = response.optJSONArray("shares") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                LiveLocationShareItem(
                    senderUsername = item.optString("sender_username"),
                    senderName = item.optString("sender_name"),
                    expiresAtEpoch = item.optLong("expires_at")
                )
            )
        }
    }
}

private fun fetchLiveUserPresence(
    apiBaseUrl: String,
    userId: String
): Quadruple<Double, Double, String?, Int>? {
    val encodedUserId = Uri.encode(userId)
    val response = getJson("$apiBaseUrl/api/users/presence?user_id=$encodedUserId")
    if (!response.optString("status").equals("online", ignoreCase = true)) {
        return null
    }
    val latitude = response.optDouble("latitude", Double.NaN)
    val longitude = response.optDouble("longitude", Double.NaN)
    if (latitude.isNaN() || longitude.isNaN()) {
        return null
    }
    val label = if (response.has("location_label") && !response.isNull("location_label")) {
        response.optString("location_label")
    } else {
        null
    }
    return Quadruple(
        first = latitude,
        second = longitude,
        third = label,
        fourth = response.optInt("age_seconds", 0)
    )
}

private fun fetchDirectMessageThread(
    apiBaseUrl: String,
    authToken: String,
    otherUsername: String
): List<DirectMessageItem> {
    val encodedUsername = Uri.encode(otherUsername)
    val response = getJsonAuthorized(
        "$apiBaseUrl/messages/thread?username=$encodedUsername&limit=120",
        authToken
    )
    val array = response.optJSONArray("messages") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                DirectMessageItem(
                    messageId = item.optLong("message_id"),
                    senderUsername = item.optString("sender_username"),
                    recipientUsername = item.optString("recipient_username"),
                    messageText = item.optString("message_text"),
                    createdAt = item.optString("created_at")
                )
            )
        }
    }
}

private fun fetchDirectInboxMessagesAfter(
    apiBaseUrl: String,
    authToken: String,
    afterMessageId: Long
): List<DirectMessageItem> {
    val response = getJsonAuthorized(
        "$apiBaseUrl/messages/inbox?after_message_id=$afterMessageId&limit=100",
        authToken
    )
    val array = response.optJSONArray("messages") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                DirectMessageItem(
                    messageId = item.optLong("message_id"),
                    senderUsername = item.optString("sender_username"),
                    recipientUsername = item.optString("recipient_username"),
                    messageText = item.optString("message_text"),
                    createdAt = item.optString("created_at")
                )
            )
        }
    }
}

private fun buildInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "TC"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

private fun getOrCreateAppUserId(context: Context): String {
    val prefs = context.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("app_user_id", null)
    if (!existing.isNullOrBlank()) {
        return existing
    }
    val generated = "user-${UUID.randomUUID()}"
    prefs.edit().putString("app_user_id", generated).apply()
    return generated
}

private fun postJson(url: String, payload: JSONObject): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = HOME_API_TIMEOUT_MS
    connection.readTimeout = HOME_API_TIMEOUT_MS
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    return try {
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload.toString())
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val detail = readErrorDetail(connection)
            throw IllegalStateException(detail ?: "HTTP $responseCode from $url")
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

private fun getJson(url: String, timeoutMs: Int = HOME_API_TIMEOUT_MS): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = timeoutMs
    connection.readTimeout = timeoutMs

    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode from $url")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body)
    } finally {
        connection.disconnect()
    }
}

private fun readErrorDetail(connection: HttpURLConnection): String? {
    val raw = runCatching {
        connection.errorStream?.bufferedReader()?.use { it.readText() }
    }.getOrNull()?.trim().orEmpty()
    if (raw.isBlank()) {
        return null
    }

    return runCatching {
        JSONObject(raw).optString("detail").takeIf { it.isNotBlank() } ?: raw
    }.getOrDefault(raw)
}

private fun fetchShortestPathOnRoad(
    startLatitude: Double,
    startLongitude: Double,
    destinationLatitude: Double,
    destinationLongitude: Double
): List<GeoPoint> {
    val osrmUrl =
        "https://router.project-osrm.org/route/v1/driving/" +
            "$startLongitude,$startLatitude;$destinationLongitude,$destinationLatitude" +
            "?overview=full&geometries=geojson"

    val response = getJson(osrmUrl, timeoutMs = 5000)
    val routes = response.optJSONArray("routes") ?: return emptyList()
    val firstRoute = routes.optJSONObject(0) ?: return emptyList()
    val geometry = firstRoute.optJSONObject("geometry") ?: return emptyList()
    val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()

    return buildList {
        for (index in 0 until coordinates.length()) {
            val point = coordinates.optJSONArray(index) ?: continue
            if (point.length() < 2) {
                continue
            }
            val lon = point.optDouble(0, Double.NaN)
            val lat = point.optDouble(1, Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                add(GeoPoint(lat, lon))
            }
        }
    }
}

private fun sendHeartbeat(
    apiBaseUrl: String,
    userId: String,
    location: Location,
    locationLabel: String,
    fcmToken: String?
) {
    val payload = JSONObject()
        .put("user_id", userId)
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .put("location_label", locationLabel)
        .put("fcm_token", fcmToken)

    postJson("$apiBaseUrl/api/users/heartbeat", payload)
}

private fun registerFcmToken(
    apiBaseUrl: String,
    userId: String,
    token: String
) {
    val payload = JSONObject()
        .put("user_id", userId)
        .put("fcm_token", token)

    postJson("$apiBaseUrl/api/fcm/token", payload)
}

private fun sendSosRequest(
    apiBaseUrl: String,
    userId: String,
    location: Location,
    locationLabel: String,
    message: String
): SosRequestResult {
    val payload = JSONObject()
        .put("requester_id", userId)
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .put("location_label", locationLabel)
        .put("message", message)
        .put("radius_m", 1000)

    val response = postJson("$apiBaseUrl/api/sos/request", payload)
    return SosRequestResult(
        eventId = response.optString("event_id"),
        notifiedUsers = response.optInt("notified_users", 0),
        expiresInSeconds = response.optInt("expires_in_seconds", 900)
    )
}

private fun sendSosResponse(
    apiBaseUrl: String,
    eventId: String,
    responderId: String,
    response: String
) {
    val payload = JSONObject()
        .put("event_id", eventId)
        .put("responder_id", responderId)
        .put("response", response)

    postJson("$apiBaseUrl/api/sos/respond", payload)
}

private fun fetchNearbySosAlerts(
    apiBaseUrl: String,
    userId: String,
    location: Location,
    radiusM: Int
): List<SosNearbyAlert> {
    val url = (
        "$apiBaseUrl/api/sos/nearby" +
            "?user_id=$userId" +
            "&latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&radius_m=$radiusM"
        )
    val response = getJson(url)
    val alertsArray = response.optJSONArray("alerts") ?: return emptyList()

    return buildList {
        for (index in 0 until alertsArray.length()) {
            val item = alertsArray.getJSONObject(index)
            val label = if (item.has("location_label") && !item.isNull("location_label")) {
                item.optString("location_label")
            } else {
                null
            }
            add(
                SosNearbyAlert(
                    eventId = item.optString("event_id"),
                    requesterId = item.optString("requester_id"),
                    message = item.optString("message", "Emergency help needed nearby"),
                    locationLabel = label,
                    latitude = item.optDouble("latitude"),
                    longitude = item.optDouble("longitude"),
                    radiusM = item.optInt("radius_m", 1000),
                    distanceM = item.optDouble("distance_m", 0.0),
                    ageSeconds = item.optInt("age_seconds", 0),
                    acceptedCount = item.optInt("accepted_count", 0),
                    userResponse = if (item.has("user_response") && !item.isNull("user_response")) {
                        item.optString("user_response")
                    } else {
                        null
                    }
                )
            )
        }
    }
}

private fun ensureSosNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        SOS_NOTIFICATION_CHANNEL_ID,
        SOS_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts when nearby users trigger SOS requests"
    }
    manager.createNotificationChannel(channel)
}

private fun ensureChatNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        CHAT_NOTIFICATION_CHANNEL_ID,
        CHAT_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Alerts for new direct messages"
    }
    manager.createNotificationChannel(channel)
}

@SuppressLint("MissingPermission")
private fun showSosNotification(context: Context, alert: SosNearbyAlert) {
    val title = "Nearby SOS request"
    val locationText = alert.locationLabel ?: String.format(
        Locale.US,
        "%.4f, %.4f",
        alert.latitude,
        alert.longitude
    )
    val body = "${alert.message} • ${alert.distanceM.toInt()}m away near $locationText"

    val notification = NotificationCompat.Builder(context, SOS_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(alert.eventId.hashCode(), notification)
}

@SuppressLint("MissingPermission")
private fun showDirectMessageNotification(context: Context, message: DirectMessageItem) {
    val preview = message.messageText.ifBlank { "Sent you a message" }
    val notification = NotificationCompat.Builder(context, CHAT_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentTitle("New message from ${message.senderUsername}")
        .setContentText(preview)
        .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context)
        .notify((message.messageId % Int.MAX_VALUE).toInt(), notification)
}

@Composable
private fun TrustedContactsSection(
    contacts: List<TrustedContact>,
    candidates: List<TrustedContactCandidate>,
    sharedLocation: SharedLiveLocation?,
    onSearchChanged: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (TrustedContact) -> Unit,
    onCallContact: (TrustedContact) -> Unit,
    onNavigateContact: (TrustedContact) -> Unit,
    onMessageContact: (TrustedContact) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var contactIdentifier by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "Trusted Contacts")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            contacts.forEach { contact ->
                val hasLiveSharedLocation = sharedLocation != null &&
                    contact.username.isNotBlank() &&
                    sharedLocation.senderUsername.equals(contact.username, ignoreCase = true)
                ContactItem(
                    contact = contact,
                    hasLiveSharedLocation = hasLiveSharedLocation,
                    onCall = { onCallContact(contact) },
                    onNavigate = { onNavigateContact(contact) },
                    onMessage = { onMessageContact(contact) },
                    onRemove = { onRemoveContact(contact) }
                )
            }
            AddContactItem(
                onClick = {
                    showAddDialog = true
                }
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "Add Trusted Contact (App User)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter an existing app username. You must be logged in to add contacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = contactIdentifier,
                        onValueChange = {
                            contactIdentifier = it
                            onSearchChanged(it)
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                    if (candidates.isNotEmpty()) {
                        Text(
                            text = "Matching usernames",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(UnifiedButtonGap)) {
                            candidates.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            contactIdentifier = candidate.username
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${candidate.username} (${candidate.name})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = {
                                            onAddContact(candidate.username)
                                            contactIdentifier = candidate.username
                                        },
                                        modifier = Modifier.height(UnifiedButtonHeight)
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddContact(contactIdentifier)
                    },
                    modifier = Modifier.height(UnifiedButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Save")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        contactIdentifier = ""
                        showAddDialog = false
                    },
                    modifier = Modifier.height(UnifiedButtonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ContactItem(
    contact: TrustedContact,
    hasLiveSharedLocation: Boolean,
    onCall: () -> Unit,
    onNavigate: () -> Unit,
    onMessage: () -> Unit,
    onRemove: () -> Unit
) {
    val initials = buildInitials(contact.name)
    var actionsVisible by remember(contact.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.clickable { actionsVisible = !actionsVisible },
        colors = CardDefaults.cardColors(containerColor = GlassCardColor),
        border = BorderStroke(1.dp, GlassBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AvatarCircle(label = initials, size = 44.dp)
            Text(
                text = contact.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            if (contact.username.isNotBlank()) {
                Text(
                    text = "@${contact.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            AnimatedVisibility(
                visible = actionsVisible,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (hasLiveSharedLocation) {
                        Text(
                            text = "Live shared location available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(UnifiedButtonGap)) {
                        ContactActionIcon(onClick = onCall, icon = Icons.Default.Call, contentDescription = "Call contact")
                        if (hasLiveSharedLocation) {
                            ContactActionIcon(
                                onClick = onNavigate,
                                icon = Icons.Default.LocationOn,
                                contentDescription = "Navigate to user",
                                tint = Color(0xFF66E2C7)
                            )
                        }
                        ContactActionIcon(onClick = onMessage, icon = Icons.Default.Send, contentDescription = "Message contact")
                        ContactActionIcon(onClick = onRemove, icon = Icons.Default.Delete, contentDescription = "Remove contact", tint = Color(0xFFFF7A8A))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddContactItem(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = Color(0x332F8CFF),
            border = BorderStroke(1.dp, Color(0xFF4F7DBD))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add contact",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AvatarCircle(label: String, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = avatarBackgroundColorFor(label)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ContactActionIcon(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(UnifiedIconButtonSize)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
private fun PlaceholderIcon(label: String, icon: ImageVector) {
    val (startColor, endColor) = quickActionGradient(label)
    Surface(
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0x88FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(listOf(startColor, endColor)),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private fun createMapMarker(
    context: Context,
    mapView: MapView,
    position: GeoPoint,
    title: String,
    snippet: String,
    fillColor: Int,
    strokeColor: Int
): Marker {
    val marker = Marker(mapView).apply {
        this.position = position
        this.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        this.title = title
        this.snippet = snippet
        this.icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(6, strokeColor)
            setSize(48, 48)
        }
    }
    return marker
}

private fun openStreetViewAtLocation(
    context: Context,
    latitude: Double,
    longitude: Double
) {
    val streetViewUri = Uri.parse("google.streetview:cbll=$latitude,$longitude")
    val appIntent = Intent(Intent.ACTION_VIEW, streetViewUri).apply {
        setPackage("com.google.android.apps.maps")
    }

    val fallbackWebIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$latitude,$longitude")
    )

    when {
        appIntent.resolveActivity(context.packageManager) != null -> context.startActivity(appIntent)
        fallbackWebIntent.resolveActivity(context.packageManager) != null -> context.startActivity(fallbackWebIntent)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x15FFFFFF),
                        Color.Transparent,
                        Color(0x0AFFFFFF)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun quickActionGradient(label: String): Pair<Color, Color> {
    return when (label) {
        "VT" -> Color(0xFF7F5CFF) to Color(0xFF4FA4FF)
        "SL" -> Color(0xFF00B4A6) to Color(0xFF56E39F)
        "FC" -> Color(0xFFFF6A88) to Color(0xFFFFA870)
        "EM" -> Color(0xFFFF8C42) to Color(0xFFFFD166)
        else -> Color(0xFF5A7DFF) to Color(0xFF62E2C7)
    }
}

private fun avatarBackgroundColorFor(label: String): Color {
    return when (label) {
        "MO" -> Color(0x334FA4FF)
        "AI" -> Color(0x3356E39F)
        "RO" -> Color(0x33FF8C42)
        "NE" -> Color(0x33FF6A88)
        "+" -> Color(0x337BC6FF)
        else -> Color(0x29FFFFFF)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailingText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(title = title)
        Text(
            text = trailingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeDashboardScreenPreview() {
    SmartCommunitySOSTheme {
        HomeDashboardScreen(currentUsername = "priya")
    }
}
