package com.smartcommunity.sos.ui.community

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartcommunity.sos.config.ApiConfig
import com.smartcommunity.sos.ui.theme.SmartCommunitySOSTheme
import com.smartcommunity.sos.ui.theme.ButtonBorder
import com.smartcommunity.sos.ui.theme.ButtonSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

private val BACKEND_BASE_URL = ApiConfig.baseUrl
private val GlassCardColor = Color(0xFF262332)
private val GlassBorderColor = Color(0xFF534D69)
private val PastelSky = Color(0xFFAEDCFF)
private val PastelMint = Color(0xFFB8F2E6)
private val PastelRose = Color(0xFFFFD6E7)
private val PastelPeach = Color(0xFFFFD9B8)
private val PastelLavender = Color(0xFFDCCEFF)
private val TrophyGold = Color(0xFFF4C95D)

private data class RequesterHelperAccept(
    val responderId: String,
    val respondedAtEpochSec: Long,
    val acknowledged: Boolean,
    val acknowledgedAtEpochSec: Long?
)

private data class RequesterActiveEvent(
    val eventId: String,
    val message: String,
    val locationLabel: String?,
    val acceptedCount: Int,
    val acknowledgedCount: Int,
    val expiresInSeconds: Int,
    val helperAccepts: List<RequesterHelperAccept>
)

private data class RequesterSosStatus(
    val activeEvent: RequesterActiveEvent?
)

private data class SosNearbyAlert(
    val eventId: String,
    val requesterId: String,
    val message: String,
    val locationLabel: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceM: Double,
    val ageSeconds: Int,
    val acceptedCount: Int,
    val userResponse: String?
)

private data class NearbyIncidentReport(
    val incidentId: String,
    val crimeType: String,
    val city: String,
    val areaName: String,
    val date: String,
    val time: String,
    val severityLevel: Double,
    val riskScore: Double,
    val crowdDensity: String,
    val lightingCondition: String,
    val distanceM: Double,
    val latitude: Double,
    val longitude: Double
)

private data class QuickHelpNeed(
    val title: String,
    val code: String,
    val helperMessage: String,
    val icon: ImageVector,
    val accent: Color
)

private data class HelperLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val acceptedHelpCount: Int,
    val acknowledgedHelpCount: Int,
    val trophies: Int,
    val isChampion: Boolean
)

private data class QuickSosUpdateResult(
    val notifiedUsers: Int
)

private data class AcknowledgeSupportResult(
    val status: String,
    val trophies: Int
)

private val quickHelpNeeds = listOf(
    QuickHelpNeed(
        title = "Need Medical Attention",
        code = "medical_attention",
        helperMessage = "Need medical attention immediately.",
        icon = Icons.Filled.LocalHospital,
        accent = PastelMint
    ),
    QuickHelpNeed(
        title = "Someone Is Following Me",
        code = "being_followed",
        helperMessage = "Someone is following me. Please stay close and assist.",
        icon = Icons.Filled.DirectionsWalk,
        accent = PastelRose
    ),
    QuickHelpNeed(
        title = "Need Safe Escort",
        code = "safe_escort",
        helperMessage = "Need a safe escort to a secure place.",
        icon = Icons.Filled.Shield,
        accent = PastelSky
    ),
    QuickHelpNeed(
        title = "Injury Or Bleeding",
        code = "injury_bleeding",
        helperMessage = "Injury reported. First-aid support needed urgently.",
        icon = Icons.Filled.Warning,
        accent = PastelPeach
    ),
    QuickHelpNeed(
        title = "Call Police Support",
        code = "police_support",
        helperMessage = "Immediate police support needed at my location.",
        icon = Icons.Filled.LocalPolice,
        accent = PastelLavender
    )
)

@Composable
fun CommunityScreen(currentUsername: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appUserId = remember(currentUsername) {
        currentUsername.ifBlank { getOrCreateAppUserId(context) }
    }
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var requesterStatus by remember { mutableStateOf(RequesterSosStatus(activeEvent = null)) }
    var requesterPanelMessage by remember { mutableStateOf<String?>(null) }
    var nearbySosAlerts by remember { mutableStateOf<List<SosNearbyAlert>>(emptyList()) }
    var nearbyFeedMessage by remember { mutableStateOf<String?>(null) }
    var nearbyIncidentReports by remember { mutableStateOf<List<NearbyIncidentReport>>(emptyList()) }
    var nearbyIncidentMessage by remember { mutableStateOf<String?>(null) }
    var selectedNearbyIncident by remember { mutableStateOf<NearbyIncidentReport?>(null) }
    var currentLocationLabel by remember { mutableStateOf<String?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var isNearbyRefreshRunning by remember { mutableStateOf(false) }
    var respondingEventId by remember { mutableStateOf<String?>(null) }
    var quickReportStatusMessage by remember { mutableStateOf<String?>(null) }
    var quickReportSubmittingCode by remember { mutableStateOf<String?>(null) }
    var leaderboardEntries by remember { mutableStateOf<List<HelperLeaderboardEntry>>(emptyList()) }
    var leaderboardStatusMessage by remember { mutableStateOf<String?>(null) }
    var isLeaderboardRefreshRunning by remember { mutableStateOf(false) }
    var acknowledgingHelperId by remember { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!locationPermissionGranted) {
            nearbyFeedMessage = "Location permission is required to fetch nearby SOS alerts."
        }
    }

    fun refreshNearbyFeed() {
        if (isNearbyRefreshRunning) {
            return
        }
        if (!locationPermissionGranted) {
            nearbyFeedMessage = "Enable location permission to access nearby SOS alerts."
            return
        }

        isNearbyRefreshRunning = true
        scope.launch {
            val result = runCatching {
                val location = getBestEffortLocation(fusedLocationClient)
                    ?: throw IllegalStateException("Unable to detect current location")

                val label = withContext(Dispatchers.IO) {
                    resolveLocationLabel(location, Geocoder(context, Locale.getDefault()))
                }

                val alerts = withContext(Dispatchers.IO) {
                    sendHeartbeat(
                        apiBaseUrl = BACKEND_BASE_URL,
                        userId = appUserId,
                        location = location,
                        locationLabel = label,
                        fcmToken = null
                    )
                    fetchNearbySosAlerts(
                        apiBaseUrl = BACKEND_BASE_URL,
                        userId = appUserId,
                        location = location,
                        radiusM = 1200
                    )
                }

                val incidentReports = withContext(Dispatchers.IO) {
                    fetchNearbyIncidentReports(
                        apiBaseUrl = BACKEND_BASE_URL,
                        location = location,
                        limit = 3,
                        radiusM = 3000
                    )
                }

                Triple(label, alerts, incidentReports)
            }

            result.onSuccess { (label, alerts, incidentReports) ->
                currentLocationLabel = label
                nearbySosAlerts = alerts
                nearbyIncidentReports = incidentReports
                nearbyFeedMessage = if (alerts.isEmpty()) {
                    "No active nearby SOS alerts right now."
                } else {
                    "Live feed updated from your latest location."
                }
                nearbyIncidentMessage = if (incidentReports.isEmpty()) {
                    "No recent incident history found nearby."
                } else {
                    "Showing ${incidentReports.size} recent nearby incident(s)."
                }
            }.onFailure {
                nearbyFeedMessage = "Could not refresh nearby alerts right now."
                nearbyIncidentMessage = "Could not load nearby incident history."
            }

            isNearbyRefreshRunning = false
        }
    }

    fun refreshLeaderboard() {
        if (isLeaderboardRefreshRunning) {
            return
        }

        isLeaderboardRefreshRunning = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchHelperLeaderboard(
                        apiBaseUrl = BACKEND_BASE_URL,
                        limit = 120
                    )
                }
            }

            result.onSuccess { leaderboard ->
                leaderboardEntries = leaderboard
                leaderboardStatusMessage = if (leaderboard.isEmpty()) {
                    "No acknowledged helps yet. Be the first to support and get a trophy."
                } else {
                    "Leaderboard refreshed for all users."
                }
            }.onFailure {
                leaderboardStatusMessage = "Could not load leaderboard right now."
            }

            isLeaderboardRefreshRunning = false
        }
    }

    LaunchedEffect(appUserId) {
        while (true) {
            requesterStatus = runCatching {
                withContext(Dispatchers.IO) {
                    fetchRequesterSosStatus(
                        apiBaseUrl = BACKEND_BASE_URL,
                        requesterId = appUserId
                    )
                }
            }.getOrElse {
                RequesterSosStatus(activeEvent = null)
            }
            delay(5000)
        }
    }

    LaunchedEffect(appUserId, locationPermissionGranted) {
        if (locationPermissionGranted) {
            refreshNearbyFeed()
        }
    }

    LaunchedEffect(appUserId, locationPermissionGranted) {
        if (!locationPermissionGranted) {
            return@LaunchedEffect
        }
        while (true) {
            delay(12000)
            refreshNearbyFeed()
        }
    }

    LaunchedEffect(appUserId) {
        refreshLeaderboard()
    }

    LaunchedEffect(appUserId) {
        while (true) {
            delay(15000)
            refreshLeaderboard()
        }
    }

    LaunchedEffect(requesterStatus.activeEvent?.eventId) {
        if (requesterStatus.activeEvent == null) {
            quickReportStatusMessage = null
            quickReportSubmittingCode = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14131D),
                        Color(0xFF1B1828),
                        Color(0xFF151D27)
                    )
                )
            )
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            CommunityHeader(locationLabel = currentLocationLabel)
        }
        item {
            RequesterSosPanel(
                status = requesterStatus,
                statusMessage = requesterPanelMessage,
                acknowledgingHelperId = acknowledgingHelperId,
                onCancel = { eventId ->
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                cancelRequesterSos(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    requesterId = appUserId,
                                    eventId = eventId
                                )
                            }
                        }

                        requesterPanelMessage = result.fold(
                            onSuccess = { "SOS cancelled successfully." },
                            onFailure = { "Could not cancel SOS. Try again." }
                        )

                        requesterStatus = runCatching {
                            withContext(Dispatchers.IO) {
                                fetchRequesterSosStatus(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    requesterId = appUserId
                                )
                            }
                        }.getOrElse {
                            RequesterSosStatus(activeEvent = null)
                        }
                    }
                },
                onAcknowledge = { eventId, helperId ->
                    scope.launch {
                        acknowledgingHelperId = helperId
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                acknowledgeHelperSupport(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    requesterId = appUserId,
                                    eventId = eventId,
                                    helperId = helperId
                                )
                            }
                        }

                        requesterPanelMessage = result.fold(
                            onSuccess = { ackResult ->
                                if (ackResult.status == "already_acknowledged") {
                                    "$helperId was already acknowledged. Trophy count: ${ackResult.trophies}."
                                } else {
                                    "Acknowledged $helperId. Trophy count is now ${ackResult.trophies}."
                                }
                            },
                            onFailure = {
                                "Could not acknowledge helper right now. Try again."
                            }
                        )
                        acknowledgingHelperId = null

                        requesterStatus = runCatching {
                            withContext(Dispatchers.IO) {
                                fetchRequesterSosStatus(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    requesterId = appUserId
                                )
                            }
                        }.getOrElse {
                            RequesterSosStatus(activeEvent = null)
                        }
                        refreshLeaderboard()
                    }
                },
                onRefresh = {
                    scope.launch {
                        requesterStatus = runCatching {
                            withContext(Dispatchers.IO) {
                                fetchRequesterSosStatus(
                                    apiBaseUrl = BACKEND_BASE_URL,
                                    requesterId = appUserId
                                )
                            }
                        }.getOrElse {
                            RequesterSosStatus(activeEvent = null)
                        }
                    }
                }
            )
        }
        item {
            LiveCommunityFeedCard(
                permissionGranted = locationPermissionGranted,
                isRefreshing = isNearbyRefreshRunning,
                statusMessage = nearbyFeedMessage,
                nearbyAlerts = nearbySosAlerts,
                respondingEventId = respondingEventId,
                onRequestPermission = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onRefresh = { refreshNearbyFeed() },
                onRespond = { alert, response ->
                    scope.launch {
                        respondingEventId = alert.eventId
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

                        nearbyFeedMessage = result.fold(
                            onSuccess = {
                                if (response == "accept") {
                                    "You accepted this SOS request."
                                } else {
                                    "You declined this SOS request."
                                }
                            },
                            onFailure = {
                                "Could not send response. Try again."
                            }
                        )
                        respondingEventId = null
                        refreshNearbyFeed()
                    }
                },
                onNavigate = { alert ->
                    openNavigationToLocation(
                        context = context,
                        latitude = alert.latitude,
                        longitude = alert.longitude,
                        label = alert.locationLabel ?: "SOS requester"
                    )
                }
            )
        }
        item {
            val activeEvent = requesterStatus.activeEvent
            if (activeEvent != null) {
                QuickReportSection(
                    activeEvent = activeEvent,
                    statusMessage = quickReportStatusMessage,
                    submittingCode = quickReportSubmittingCode,
                    onQuickHelpSelected = { helpNeed ->
                        scope.launch {
                            quickReportSubmittingCode = helpNeed.code
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    sendQuickSosUpdate(
                                        apiBaseUrl = BACKEND_BASE_URL,
                                        eventId = activeEvent.eventId,
                                        requesterId = appUserId,
                                        helpNeed = helpNeed
                                    )
                                }
                            }

                            quickReportStatusMessage = result.fold(
                                onSuccess = { response ->
                                    "Shared \"${helpNeed.title}\" with ${response.notifiedUsers} nearby helper(s)."
                                },
                                onFailure = {
                                    "Could not share this quick update. Try again."
                                }
                            )
                            quickReportSubmittingCode = null
                            requesterStatus = runCatching {
                                withContext(Dispatchers.IO) {
                                    fetchRequesterSosStatus(
                                        apiBaseUrl = BACKEND_BASE_URL,
                                        requesterId = appUserId
                                    )
                                }
                            }.getOrElse {
                                RequesterSosStatus(activeEvent = null)
                            }
                        }
                    }
                )
            }
        }
        item {
            NearbyAlertsSection(
                incidents = nearbyIncidentReports,
                statusMessage = nearbyIncidentMessage,
                onIncidentSelected = { selectedNearbyIncident = it }
            )
        }
        item {
            LeaderboardSection(
                leaderboardEntries = leaderboardEntries,
                statusMessage = leaderboardStatusMessage,
                isRefreshing = isLeaderboardRefreshRunning,
                onRefresh = { refreshLeaderboard() }
            )
        }
    }

    selectedNearbyIncident?.let { incident ->
        IncidentDetailsDialog(
            incident = incident,
            onDismiss = { selectedNearbyIncident = null }
        )
    }
}

@Composable
private fun CommunityHeader(locationLabel: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = "Community",
                tint = PastelSky,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Community Safety",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "Live SOS coordination panel for requesters with real-time helper accepts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!locationLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PastelMint.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, PastelMint.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Location",
                        tint = PastelMint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Live area: $locationLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = PastelMint
                    )
                }
            }
        }
    }
}

@Composable
private fun RequesterSosPanel(
    status: RequesterSosStatus,
    statusMessage: String?,
    acknowledgingHelperId: String?,
    onCancel: (String) -> Unit,
    onAcknowledge: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        border = BorderStroke(1.dp, GlassBorderColor),
        colors = CardDefaults.cardColors(containerColor = GlassCardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PastelLavender.copy(alpha = 0.15f),
                            PastelSky.copy(alpha = 0.08f),
                            Color(0x05FFFFFF)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Live SOS",
                        tint = PastelRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Your Live SOS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder
                    )
                ) {
                    Text("Refresh")
                }
            }

            val activeEvent = status.activeEvent
            if (activeEvent == null) {
                Text(
                    text = "No active SOS right now. Trigger SOS from Home to start live tracking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = activeEvent.message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${activeEvent.acceptedCount} accepted • ${activeEvent.acknowledgedCount} acknowledged • expires in ${activeEvent.expiresInSeconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activeEvent.locationLabel ?: "Location shared",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeEvent.helperAccepts.isEmpty()) {
                    Text(
                        text = "Waiting for helper accepts...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeEvent.helperAccepts.forEach { helper ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1FFFFFFF),
                                border = BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = helper.responderId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (helper.acknowledged) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.EmojiEvents,
                                                contentDescription = "Trophy",
                                                tint = TrophyGold,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Acknowledged",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TrophyGold
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onAcknowledge(activeEvent.eventId, helper.responderId) },
                                            enabled = acknowledgingHelperId != helper.responderId,
                                            border = BorderStroke(1.dp, ButtonBorder)
                                        ) {
                                            Text(
                                                text = if (acknowledgingHelperId == helper.responderId) {
                                                    "Saving..."
                                                } else {
                                                    "Acknowledge"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { onCancel(activeEvent.eventId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Text("Cancel SOS")
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LiveCommunityFeedCard(
    permissionGranted: Boolean,
    isRefreshing: Boolean,
    statusMessage: String?,
    nearbyAlerts: List<SosNearbyAlert>,
    respondingEventId: String?,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onRespond: (SosNearbyAlert, String) -> Unit,
    onNavigate: (SosNearbyAlert) -> Unit
) {
    Card(
        border = BorderStroke(1.dp, GlassBorderColor),
        colors = CardDefaults.cardColors(containerColor = GlassCardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Campaign,
                        contentDescription = "Feed",
                        tint = PastelPeach,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Live Nearby SOS Feed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onRefresh,
                    enabled = permissionGranted && !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(14.dp)
                        )
                        Text(if (isRefreshing) "Refreshing" else "Refresh")
                    }
                }
            }

            if (!permissionGranted) {
                Text(
                    text = "Location permission is required to load nearby SOS requests.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onRequestPermission,
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Text("Grant Location Permission")
                }
            } else if (nearbyAlerts.isEmpty()) {
                Text(
                    text = "No active nearby SOS requests in your current area.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nearbyAlerts.take(5).forEach { alert ->
                        CommunitySosAlertCard(
                            alert = alert,
                            isSubmitting = respondingEventId == alert.eventId,
                            onRespond = onRespond,
                            onNavigate = onNavigate
                        )
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CommunitySosAlertCard(
    alert: SosNearbyAlert,
    isSubmitting: Boolean,
    onRespond: (SosNearbyAlert, String) -> Unit,
    onNavigate: (SosNearbyAlert) -> Unit
) {
    val locationText = alert.locationLabel ?: String.format(
        Locale.US,
        "%.4f, %.4f",
        alert.latitude,
        alert.longitude
    )

    val responseText = when (alert.userResponse?.lowercase(Locale.US)) {
        "accept" -> "You accepted"
        "decline" -> "You declined"
        else -> null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PastelPeach.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, PastelPeach.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "SOS Alert",
                    tint = PastelPeach,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "$locationText • ${alert.distanceM.toInt()}m • ${alert.acceptedCount} accepted",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onRespond(alert, "accept") },
                    enabled = !isSubmitting && alert.userResponse != "accept",
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { onRespond(alert, "decline") },
                    enabled = !isSubmitting && alert.userResponse != "decline",
                    border = BorderStroke(1.dp, ButtonBorder)
                ) {
                    Text("Decline")
                }
            }

            OutlinedButton(
                onClick = { onNavigate(alert) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Navigate",
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Navigate")
                }
            }

            if (!responseText.isNullOrBlank()) {
                Text(
                    text = responseText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF66E2C7)
                )
            }
        }
    }
}

@Composable
private fun QuickReportSection(
    activeEvent: RequesterActiveEvent,
    statusMessage: String?,
    submittingCode: String?,
    onQuickHelpSelected: (QuickHelpNeed) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Quick Report",
            icon = Icons.Filled.NotificationsActive,
            tint = PastelRose
        )
        Text(
            text = "Active only while SOS is live. Tap any update to notify nearby helpers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Live SOS: ${activeEvent.eventId.take(8)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickHelpNeeds.forEach { helpNeed ->
                OutlinedButton(
                    onClick = { onQuickHelpSelected(helpNeed) },
                    enabled = submittingCode == null,
                    border = BorderStroke(
                        1.dp,
                        if (submittingCode == helpNeed.code) {
                            helpNeed.accent
                        } else {
                            helpNeed.accent.copy(alpha = 0.45f)
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = helpNeed.accent.copy(alpha = 0.08f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = helpNeed.icon,
                            contentDescription = helpNeed.title,
                            tint = helpNeed.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (submittingCode == helpNeed.code) {
                                "Sending..."
                            } else {
                                helpNeed.title
                            }
                        )
                    }
                }
            }
        }
        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NearbyAlertsSection(
    incidents: List<NearbyIncidentReport>,
    statusMessage: String?,
    onIncidentSelected: (NearbyIncidentReport) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Nearby Alerts",
            icon = Icons.Filled.Warning,
            tint = PastelPeach
        )
        if (incidents.isEmpty()) {
            Text(
                text = statusMessage ?: "No nearby incident alerts available right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            incidents.take(3).forEach { incident ->
                NearbyIncidentCard(
                    incident = incident,
                    onClick = { onIncidentSelected(incident) }
                )
            }
        }
        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun NearbyIncidentCard(
    incident: NearbyIncidentReport,
    onClick: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.crimeType.ifBlank { "Incident" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = CircleShape,
                    color = PastelPeach.copy(alpha = 0.25f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Incident location",
                        tint = PastelPeach,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }
            Text(
                text = "${incident.areaName}, ${incident.city}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${incident.distanceM.toInt()}m away • risk ${(incident.riskScore * 100.0).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun IncidentDetailsDialog(
    incident: NearbyIncidentReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Incident",
                    tint = PastelPeach,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = incident.crimeType.ifBlank { "Incident Details" },
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Area: ${incident.areaName}, ${incident.city}")
                Text("Distance: ${incident.distanceM.toInt()}m")
                Text("Date/Time: ${incident.date.ifBlank { "Unknown" }} ${incident.time.ifBlank { "" }}")
                Text("Risk score: ${(incident.riskScore * 100.0).toInt()}%")
                Text("Severity level: ${incident.severityLevel}")
                Text("Crowd density: ${incident.crowdDensity}")
                Text("Lighting: ${incident.lightingCondition}")
                Text("Coordinates: ${String.format(Locale.US, "%.5f, %.5f", incident.latitude, incident.longitude)}")
                if (incident.incidentId.isNotBlank()) {
                    Text("Incident ID: ${incident.incidentId}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LeaderboardSection(
    leaderboardEntries: List<HelperLeaderboardEntry>,
    statusMessage: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(
                title = "Helpers Leaderboard",
                icon = Icons.Filled.EmojiEvents,
                tint = TrophyGold
            )
            OutlinedButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(14.dp)
                    )
                    Text(if (isRefreshing) "Refreshing" else "Refresh")
                }
            }
        }

        val champion = leaderboardEntries.firstOrNull { it.isChampion }
        if (champion != null) {
            Card(
                border = BorderStroke(1.dp, Color(0x66F4C95D)),
                colors = CardDefaults.cardColors(containerColor = Color(0x222E2412))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0x44F4C95D), Color(0x112E2412))
                            )
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = "Champion Trophy",
                            tint = TrophyGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Champion Trophy Holder",
                            style = MaterialTheme.typography.labelLarge,
                            color = TrophyGold,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = champion.userId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Trophies ${champion.trophies} • Acknowledged helps ${champion.acknowledgedHelpCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (leaderboardEntries.isEmpty()) {
            Text(
                text = "No leaderboard records yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                leaderboardEntries.take(10).forEach { entry ->
                    LeaderboardRow(entry = entry)
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LeaderboardRow(entry: HelperLeaderboardEntry) {
    val rankColor = when (entry.rank) {
        1 -> TrophyGold
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color(0xFF66E2C7)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0x1FFFFFFF),
        border = BorderStroke(1.dp, Color(0x33FFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(rankColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${entry.rank}",
                        style = MaterialTheme.typography.labelMedium,
                        color = rankColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.userId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Accepted ${entry.acceptedHelpCount} • Acknowledged ${entry.acknowledgedHelpCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = "Trophies",
                    tint = if (entry.isChampion) TrophyGold else PastelLavender,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = entry.trophies.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.isChampion) TrophyGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (entry.isChampion) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun AlertDot() {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun SectionTitle(
    title: String,
    icon: ImageVector,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.18f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
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
    connection.connectTimeout = 3000
    connection.readTimeout = 3000
    connection.setRequestProperty("Content-Type", "application/json")
    connection.doOutput = true

    return try {
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload.toString())
        }

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

private fun openNavigationToLocation(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String
) {
    val googleUri = Uri.parse("google.navigation:q=$latitude,$longitude")
    val googleIntent = Intent(Intent.ACTION_VIEW, googleUri).apply {
        setPackage("com.google.android.apps.maps")
    }

    val fallbackUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
    val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri)

    runCatching {
        when {
            googleIntent.resolveActivity(context.packageManager) != null -> {
                context.startActivity(googleIntent)
            }

            fallbackIntent.resolveActivity(context.packageManager) != null -> {
                context.startActivity(fallbackIntent)
            }
        }
    }
}

private fun getJson(url: String): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 3000
    connection.readTimeout = 3000

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

private fun fetchRequesterSosStatus(
    apiBaseUrl: String,
    requesterId: String
): RequesterSosStatus {
    val url = "$apiBaseUrl/api/sos/requester?requester_id=$requesterId"
    val response = getJson(url)

    if (response.optString("status") != "active") {
        return RequesterSosStatus(activeEvent = null)
    }

    val active = response.optJSONObject("active_event") ?: return RequesterSosStatus(activeEvent = null)
    val helperAcceptsArray = active.optJSONArray("helper_accepts")

    val helperAccepts = buildList {
        if (helperAcceptsArray != null) {
            for (index in 0 until helperAcceptsArray.length()) {
                val item = helperAcceptsArray.getJSONObject(index)
                add(
                    RequesterHelperAccept(
                        responderId = item.optString("responder_id", "unknown-helper"),
                        respondedAtEpochSec = item.optLong("responded_at", 0L),
                        acknowledged = item.optBoolean("acknowledged", false),
                        acknowledgedAtEpochSec = if (item.has("acknowledged_at") && !item.isNull("acknowledged_at")) {
                            item.optLong("acknowledged_at", 0L)
                        } else {
                            null
                        }
                    )
                )
            }
        }
    }

    return RequesterSosStatus(
        activeEvent = RequesterActiveEvent(
            eventId = active.optString("event_id"),
            message = active.optString("message", "Emergency help needed nearby"),
            locationLabel = if (active.has("location_label") && !active.isNull("location_label")) {
                active.optString("location_label")
            } else {
                null
            },
            acceptedCount = active.optInt("accepted_count", 0),
            acknowledgedCount = active.optInt("acknowledged_count", 0),
            expiresInSeconds = active.optInt("expires_in_seconds", 0),
            helperAccepts = helperAccepts
        )
    )
}

private fun cancelRequesterSos(
    apiBaseUrl: String,
    requesterId: String,
    eventId: String
) {
    val payload = JSONObject()
        .put("requester_id", requesterId)
        .put("event_id", eventId)

    postJson("$apiBaseUrl/api/sos/cancel", payload)
}

private fun acknowledgeHelperSupport(
    apiBaseUrl: String,
    requesterId: String,
    eventId: String,
    helperId: String
): AcknowledgeSupportResult {
    val payload = JSONObject()
        .put("requester_id", requesterId)
        .put("event_id", eventId)
        .put("helper_id", helperId)

    val response = postJson("$apiBaseUrl/api/sos/acknowledge", payload)
    val status = response.optString("status")
    if (status != "acknowledged" && status != "already_acknowledged") {
        throw IllegalStateException("Failed to acknowledge helper")
    }

    return AcknowledgeSupportResult(
        status = status,
        trophies = response.optInt("trophies", 0)
    )
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

            if (namedParts.isEmpty()) latLngText else namedParts.take(3).joinToString(", ")
        }
    } catch (_: Exception) {
        latLngText
    }
}

@SuppressLint("MissingPermission")
private suspend fun getBestEffortLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
): Location? = suspendCancellableCoroutine { continuation ->
    fusedLocationClient.lastLocation
        .addOnSuccessListener { lastLocation ->
            if (lastLocation != null) {
                if (continuation.isActive) {
                    continuation.resume(lastLocation)
                }
                return@addOnSuccessListener
            }

            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                )
                .addOnSuccessListener { currentLocation ->
                    if (continuation.isActive) {
                        continuation.resume(currentLocation)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
        }
        .addOnFailureListener {
            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                )
                .addOnSuccessListener { currentLocation ->
                    if (continuation.isActive) {
                        continuation.resume(currentLocation)
                    }
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
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

private fun sendQuickSosUpdate(
    apiBaseUrl: String,
    eventId: String,
    requesterId: String,
    helpNeed: QuickHelpNeed
): QuickSosUpdateResult {
    val payload = JSONObject()
        .put("event_id", eventId)
        .put("requester_id", requesterId)
        .put("update_code", helpNeed.code)
        .put("update_message", helpNeed.helperMessage)

    val response = postJson("$apiBaseUrl/api/sos/quick-update", payload)
    if (response.optString("status") != "ok") {
        throw IllegalStateException("Quick update failed")
    }

    return QuickSosUpdateResult(
        notifiedUsers = response.optInt("notified_users", 0)
    )
}

private fun fetchHelperLeaderboard(
    apiBaseUrl: String,
    limit: Int
): List<HelperLeaderboardEntry> {
    val response = getJson("$apiBaseUrl/api/helpers/leaderboard?limit=$limit")
    val array = response.optJSONArray("leaderboard") ?: return emptyList()

    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                HelperLeaderboardEntry(
                    rank = item.optInt("rank", index + 1),
                    userId = item.optString("user_id", "unknown-user"),
                    acceptedHelpCount = item.optInt("accepted_help_count", 0),
                    acknowledgedHelpCount = item.optInt("acknowledged_help_count", 0),
                    trophies = item.optInt("trophies", 0),
                    isChampion = item.optBoolean("is_champion", false)
                )
            )
        }
    }
}

private fun fetchNearbySosAlerts(
    apiBaseUrl: String,
    userId: String,
    location: Location,
    radiusM: Int
): List<SosNearbyAlert> {
    val encodedUserId = Uri.encode(userId)
    val url = (
        "$apiBaseUrl/api/sos/nearby" +
            "?user_id=$encodedUserId" +
            "&latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&radius_m=$radiusM"
        )

    val response = getJson(url)
    val alertsArray = response.optJSONArray("alerts") ?: return emptyList()

    return buildList {
        for (index in 0 until alertsArray.length()) {
            val item = alertsArray.optJSONObject(index) ?: continue
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

private fun fetchNearbyIncidentReports(
    apiBaseUrl: String,
    location: Location,
    limit: Int,
    radiusM: Int
): List<NearbyIncidentReport> {
    val response = getJson(
        "$apiBaseUrl/api/incidents/nearby" +
            "?latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&limit=$limit" +
            "&radius_m=$radiusM"
    )
    val array = response.optJSONArray("incidents") ?: return emptyList()

    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                NearbyIncidentReport(
                    incidentId = item.optString("incident_id", ""),
                    crimeType = item.optString("crime_type", "Incident"),
                    city = item.optString("city", "Unknown"),
                    areaName = item.optString("area_name", "Unknown"),
                    date = item.optString("date", ""),
                    time = item.optString("time", ""),
                    severityLevel = item.optDouble("severity_level", 0.0),
                    riskScore = item.optDouble("risk_score", 0.0),
                    crowdDensity = item.optString("crowd_density", "unknown"),
                    lightingCondition = item.optString("lighting_condition", "unknown"),
                    distanceM = item.optDouble("distance_m", 0.0),
                    latitude = item.optDouble("latitude", 0.0),
                    longitude = item.optDouble("longitude", 0.0)
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CommunityScreenPreview() {
    SmartCommunitySOSTheme {
        CommunityScreen(currentUsername = "preview-user")
    }
}
