
package com.smartcommunity.sos.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.smartcommunity.sos.ui.theme.ButtonBorder
import com.smartcommunity.sos.ui.theme.ButtonSurface
import com.smartcommunity.sos.ui.theme.SmartCommunitySOSTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private val BACKEND_BASE_URL = if (
    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
    Build.MODEL.contains("Emulator", ignoreCase = true)
) {
    "http://10.0.2.2:8001"
} else {
    "http://127.0.0.1:8001"
}
private const val REQUEST_TIMEOUT_MS = 3000
private const val AI_REQUEST_TIMEOUT_MS = 5000
private const val TILE_FETCH_TIMEOUT_MS = 1000
private const val TILE_SIZE_PX = 256
private const val MAX_TILE_COUNT = 16
private const val MAX_BITMAP_BYTES = 50_000_000L // 50MB limit
private const val TAG = "MapScreen"
private const val MIN_MAP_SCALE = 1f
private const val MAX_MAP_SCALE = 22f
private const val DEFAULT_USER_FOCUS_SCALE = 2.2f
private const val CRIME_CLUSTER_BUCKET_PX = 72f
private const val MAX_LIGHT_HEAT_ZONES = 20
private const val MIN_LIGHT_ZONE_INCIDENTS = 10
private const val OUT_OF_COVERAGE_DISTANCE_KM = 500.0

private val DashboardCities = listOf(
    CityCount("Kochi", 3192),
    CityCount("New Delhi", 2105),
    CityCount("Surat", 1377),
    CityCount("Jaipur", 1277),
    CityCount("Patna", 1150),
    CityCount("Gwalior", 799),
    CityCount("Indore", 768),
    CityCount("Kollam", 714),
    CityCount("Jabalpur", 680),
    CityCount("Vadodara", 542),
    CityCount("Faridabad", 445),
    CityCount("Ahmedabad", 441),
    CityCount("Lucknow", 432),
    CityCount("Bhopal", 428),
    CityCount("Ghaziabad", 418),
    CityCount("Coimbatore", 410),
    CityCount("Kanpur", 401),
    CityCount("Kozhikode", 398),
    CityCount("Nagpur", 389),
    CityCount("Nashik", 382)
)

private val DashboardCrimeTypes = listOf(
    CrimeTypeCount("theft", 18),
    CrimeTypeCount("pickpocketing", 10),
    CrimeTypeCount("harassment", 12),
    CrimeTypeCount("robbery", 7),
    CrimeTypeCount("chain_snatching", 8),
    CrimeTypeCount("vehicular_theft", 7),
    CrimeTypeCount("assault", 8),
    CrimeTypeCount("stalking", 6),
    CrimeTypeCount("burglary", 6),
    CrimeTypeCount("domestic_violence", 6),
    CrimeTypeCount("eve_teasing", 5),
    CrimeTypeCount("mugging", 4),
    CrimeTypeCount("fraud", 5),
    CrimeTypeCount("vandalism", 4),
    CrimeTypeCount("drug_offense", 4)
)

private val CrimePalette = listOf(
    Color(0xFF378ADD), Color(0xFFD85A30), Color(0xFF1D9E75), Color(0xFFBA7517),
    Color(0xFF7F77DD), Color(0xFF639922), Color(0xFFD4537E), Color(0xFF888780),
    Color(0xFFE24B4A), Color(0xFF5DCAA5), Color(0xFFF09595), Color(0xFFEF9F27),
    Color(0xFFAFA9EC), Color(0xFFF0997B), Color(0xFFB4B2A9)
)

private val DashboardHourCounts = listOf(3, 2, 1, 2, 1, 1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 6, 7, 8, 9, 10, 9, 8, 7, 5)
private val DashboardDayCounts = listOf(14000, 14200, 13700, 14100, 14500, 14800, 14600)
private val DashboardWeaponRates = listOf(2, 5, 12, 58, 72)
private val DashboardAreaTypes = listOf("tourist", "commercial", "industrial", "residential", "it_zone")
private val DashboardAreaRiskScores = listOf(0.72f, 0.61f, 0.58f, 0.52f, 0.44f)
private val DashboardHeatLabels = listOf(
    "Good + Low", "Good + Med", "Good + High", "Moderate + Low", "Moderate + Med",
    "Moderate + High", "Poor + Low", "Poor + Med", "Poor + High"
)
private val DashboardHeatValues = listOf(4200, 8100, 9800, 3600, 5900, 6200, 8400, 11200, 12100)
private val DashboardWeekCrimeTypes = listOf("theft", "harassment", "assault", "robbery", "pickpocketing", "burglary", "stalking", "domestic_violence")
private val DashboardWeekdaySeverity = listOf(2.1f, 2.9f, 4.6f, 3.9f, 2.1f, 3.7f, 2.8f, 3.8f)
private val DashboardWeekendSeverity = listOf(2.2f, 3.0f, 4.7f, 4.1f, 2.0f, 3.8f, 2.7f, 4.2f)
private val DashboardInjuryCrimes = listOf("assault", "domestic_violence", "robbery", "mugging", "stalking", "harassment", "burglary", "theft")
private val DashboardInjuryRates = listOf(48, 43, 38, 36, 14, 11, 7, 4)

private data class CityCount(
    val city: String,
    val count: Int
)

private data class CrimeTypeCount(
    val crimeType: String,
    val count: Int
)

private data class HeatCell(
    val latitude: Double,
    val longitude: Double,
    val weight: Double,
    val count: Int
)

private data class ProjectedHeatCell(
    val center: Offset,
    val weight: Double,
    val count: Int
)

private data class CrimeCluster(
    val center: Offset,
    val weight: Double,
    val count: Int,
    val members: Int
)

private data class GeoCrimeCluster(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    val members: Int
)

private data class HeatmapBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
)

private data class HeatmapSummary(
    val datasetFile: String,
    val totalIncidents: Int,
    val averageRiskScore: Double,
    val topCities: List<CityCount>,
    val topCrimeTypes: List<CrimeTypeCount>
)

private data class HeatmapGrid(
    val sourceIncidents: Int,
    val cellsCount: Int,
    val bounds: HeatmapBounds,
    val points: List<HeatCell>
)

private data class DangerZone(
    val clusterId: Int,
    val latitude: Double,
    val longitude: Double,
    val incidentCount: Int,
    val riskScore: Double,
    val riskLevel: String
)

private data class ZoneDialogContent(
    val title: String,
    val message: String
)

private data class AreaRiskPrediction(
    val riskPercent: Double,
    val riskProbability: Double,
    val riskLevel: String,
    val message: String,
    val recommendation: String,
    val modelName: String
)

private sealed interface AiSafetyUiState {
    data object Idle : AiSafetyUiState
    data object Loading : AiSafetyUiState
    data class Ready(
        val risk: AreaRiskPrediction
    ) : AiSafetyUiState

    data class Error(val message: String) : AiSafetyUiState
}

private data class RasterMap(
    val bitmap: Bitmap,
    val zoom: Int,
    val minTileX: Int,
    val minTileY: Int,
    val widthPx: Int,
    val heightPx: Int
)

private sealed interface MapUiState {
    data object Loading : MapUiState
    data class Ready(
        val summary: HeatmapSummary,
        val grid: HeatmapGrid,
        val zones: List<DangerZone>,
        val rasterMap: RasterMap?
    ) : MapUiState
    data class Error(val message: String) : MapUiState
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var reloadToken by remember { mutableIntStateOf(0) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
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

    DisposableEffect(locationPermissionGranted) {
        if (!locationPermissionGranted) {
            onDispose { }
        } else {
            val callback = startRealtimeLocationUpdates(
                fusedLocationClient = fusedLocationClient,
                onLocation = { location -> userLocation = location }
            )
            onDispose {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }

    val uiState by produceState<MapUiState>(
        initialValue = MapUiState.Loading,
        key1 = reloadToken
    ) {
        value = MapUiState.Loading
        value = loadMapData(BACKEND_BASE_URL)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A),
                        Color(0xFF1A1A1A),
                        Color(0xFF1A1A1A)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x26FFFFFF),
                            Color(0x11000000),
                            Color.Transparent
                        ),
                        center = Offset(0.2f, 0.15f),
                        radius = 900f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeatmapCard(
                    uiState = uiState,
                    onRetry = { reloadToken++ }
                )
            }

            item {
                AiSafetyInsightsCard(
                    uiState = uiState,
                    userLocation = userLocation,
                    onRetry = { reloadToken++ }
                )
            }

            item {
                SectionLabel(text = "Crime distribution")
            }

            item {
                CrimeGraphsSection()
            }
        }
    }
}

@Composable
private fun CrimeGraphsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ChartCard(
            title = "Top 20 cities by incident volume",
            subtitle = "Proportional to crime rate index - weighted sampling"
        ) {
            TopCitiesChart()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartCard(
                title = "Crime type breakdown",
                subtitle = "All 15 crime categories",
                modifier = Modifier.weight(1f)
            ) {
                CrimeDonutChart()
            }
            ChartCard(
                title = "Incidents by hour of day",
                subtitle = "24-hour distribution - evening peak visible",
                modifier = Modifier.weight(1f)
            ) {
                HourLineChart()
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChartCard(
                title = "Day of week pattern",
                subtitle = "Mon-Sun incident frequency",
                modifier = Modifier.weight(1f)
            ) {
                DayOfWeekChart()
            }
            ChartCard(
                title = "Severity vs weapon use",
                subtitle = "% with weapon by severity level 1-5",
                modifier = Modifier.weight(1f)
            ) {
                SeverityWeaponChart()
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun TopCitiesChart() {
    val maxValue = DashboardCities.maxOf { it.count }.toFloat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardCities.forEachIndexed { index, city ->
            val barHeight = (26f + (city.count / maxValue) * 120f).dp
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = city.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .height(barHeight)
                        .width(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (index == 0) Color(0xFFD85A30)
                            else if (index < 5) Color(0xFF378ADD)
                            else Color(0xFFB5D4F4)
                        )
                )
                Text(
                    text = city.city,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun CrimeDonutChart() {
    val total = DashboardCrimeTypes.sumOf { it.count }.toFloat().coerceAtLeast(1f)
    val legendItems = DashboardCrimeTypes.mapIndexed { index, item ->
        Triple(item.crimeType, item.count, CrimePalette[index])
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            val diameter = size.minDimension * 0.72f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var startAngle = -90f

            DashboardCrimeTypes.forEachIndexed { index, item ->
                val sweep = 360f * item.count / total
                drawArc(
                    color = CrimePalette[index],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = diameter * 0.18f)
                )
                startAngle += sweep
            }
        }

        legendItems.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(item.third)
                        )
                        Text(
                            text = "${item.first} ${(item.second / total * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourLineChart() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val maxValue = DashboardHourCounts.maxOf { it }.toFloat().coerceAtLeast(1f)
            val chartHeight = size.height * 0.78f
            val chartTop = size.height * 0.12f
            val stepX = size.width / (DashboardHourCounts.size - 1).coerceAtLeast(1)
            val points = DashboardHourCounts.mapIndexed { index, value ->
                Offset(
                    x = index * stepX,
                    y = chartTop + chartHeight - (value / maxValue * chartHeight)
                )
            }

            repeat(4) { index ->
                val y = chartTop + index * (chartHeight / 3f)
                drawLine(
                    color = Color(0x224A4A4A),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f
                )
            }

            for (index in 0 until points.size - 1) {
                drawLine(
                    color = Color(0xFF378ADD),
                    start = points[index],
                    end = points[index + 1],
                    strokeWidth = 5f
                )
            }

            points.forEach { point ->
                drawCircle(color = Color(0xFF378ADD), radius = 5f, center = point)
            }
        }

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            listOf("00", "06", "12", "18", "23").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayOfWeekChart() {
    val maxValue = DashboardDayCounts.maxOrNull()?.toFloat() ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { index, label ->
            val barHeight = (DashboardDayCounts[index] / maxValue * 120f + 18f).dp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .height(barHeight)
                        .width(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (index >= 5) Color(0xFFF5C4B3) else Color(0xFFB5D4F4))
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SeverityWeaponChart() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 4, 5).forEachIndexed { index, severity ->
            val weaponRate = DashboardWeaponRates[index].toFloat()
            val noWeaponRate = 100f - weaponRate
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "S$severity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp)
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(noWeaponRate)
                            .fillMaxHeight()
                            .background(Color(0xFFB5D4F4))
                    )
                    Box(
                        modifier = Modifier
                            .weight(weaponRate)
                            .fillMaxHeight()
                            .background(Color(0xFFD85A30))
                    )
                }
                Text(
                    text = "${weaponRate.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

@Composable
private fun AreaRiskChart() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardAreaTypes.forEachIndexed { index, areaType ->
            val score = DashboardAreaRiskScores[index]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = areaType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(78.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(score)
                            .fillMaxHeight()
                            .background(zoneColorForScore(score))
                    )
                }
                Text(
                    text = String.format(Locale.US, "%.2f", score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

@Composable
private fun LightingCrowdHeatmap() {
    val cells = listOf(
        Triple("Good", "Low", 4200), Triple("Good", "Med", 8100), Triple("Good", "High", 9800),
        Triple("Moderate", "Low", 3600), Triple("Moderate", "Med", 5900), Triple("Moderate", "High", 6200),
        Triple("Poor", "Low", 8400), Triple("Poor", "Med", 11200), Triple("Poor", "High", 12100)
    )
    val maxValue = DashboardHeatValues.maxOrNull()?.toFloat() ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEachIndexed { index, cell ->
                    val value = cell.third
                    val norm = value / maxValue
                    val color = Color(
                        red = (55 + norm * (216 - 55)).toInt(),
                        green = (138 + norm * (90 - 138)).toInt(),
                        blue = (221 + norm * (48 - 221)).toInt()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${cell.first}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Text(
                            text = "${cell.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekendSeverityChart() {
    Column(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardWeekCrimeTypes.forEachIndexed { index, crime ->
                val weekday = DashboardWeekdaySeverity[index]
                val weekend = DashboardWeekendSeverity[index]
                Column(
                    modifier = Modifier.width(86.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height((weekday * 24f).dp)
                            .width(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF85B7EB))
                    )
                    Box(
                        modifier = Modifier
                            .height((weekend * 24f).dp)
                            .width(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFD85A30))
                    )
                    Text(
                        text = crime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(Color(0xFF85B7EB), RoundedCornerShape(2.dp)))
                Text(text = "Weekday", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(Color(0xFFD85A30), RoundedCornerShape(2.dp)))
                Text(text = "Weekend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InjuryChart() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardInjuryCrimes.forEachIndexed { index, crime ->
            val rate = DashboardInjuryRates[index].toFloat()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = crime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(90.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF333333))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rate / 60f)
                            .fillMaxHeight()
                            .background(if (rate > 35f) Color(0xFFD85A30) else if (rate > 15f) Color(0xFFBA7517) else Color(0xFF85B7EB))
                    )
                }
                Text(
                    text = "${rate.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(30.dp)
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(
    uiState: MapUiState,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Area Safety Map",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            HeatmapView(
                uiState = uiState,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun AiSafetyInsightsCard(
    uiState: MapUiState,
    userLocation: Location?,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "AI Safety Insights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            when (uiState) {
                MapUiState.Loading -> {
                    Text(
                        text = "Loading map context for AI predictions...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MapUiState.Error -> {
                    RetryBackendButton(onRetry = onRetry)
                }

                is MapUiState.Ready -> {
                    if (userLocation == null) {
                        Text(
                            text = "Enable location to run AI safety risk checks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val aiLocationLatKey = remember(userLocation.latitude) {
                            (userLocation.latitude * 1000.0).roundToInt() / 1000.0
                        }
                        val aiLocationLonKey = remember(userLocation.longitude) {
                            (userLocation.longitude * 1000.0).roundToInt() / 1000.0
                        }

                        val aiState by produceState<AiSafetyUiState>(
                            initialValue = AiSafetyUiState.Loading,
                            key1 = uiState.grid.cellsCount,
                            key2 = aiLocationLatKey,
                            key3 = aiLocationLonKey
                        ) {
                            value = loadAiSafetyInsights(BACKEND_BASE_URL, uiState.grid, userLocation)
                        }

                        when (val state = aiState) {
                            AiSafetyUiState.Idle,
                            AiSafetyUiState.Loading -> {
                                Text(
                                    text = "Running AI risk model...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            is AiSafetyUiState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                RetryBackendButton(onRetry = onRetry)
                            }

                            is AiSafetyUiState.Ready -> {
                                val riskColor = when (state.risk.riskLevel.lowercase(Locale.US)) {
                                    "high" -> Color(0xFFF24B4B)
                                    "moderate" -> Color(0xFFF5C542)
                                    else -> Color(0xFF2BB673)
                                }

                                StatusBadge(
                                    text = "Risk: ${state.risk.riskLevel.uppercase(Locale.US)} (${String.format(Locale.US, "%.1f", state.risk.riskPercent)}%)"
                                )
                                Text(
                                    text = state.risk.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = riskColor
                                )
                                Text(
                                    text = state.risk.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Model: ${state.risk.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HeatmapView(
    uiState: MapUiState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF08111E),
                        Color(0xFF0B1728),
                        Color(0xFF10243A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            MapUiState.Loading -> {
                CircularProgressIndicator(color = Color(0xFF7BC6FF))
            }

            is MapUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RetryBackendButton(onRetry = onRetry)
                }
            }

            is MapUiState.Ready -> {
                CrimeDotMapView(
                    grid = uiState.grid
                )
            }
        }
    }
}

@Composable
private fun CrimeDotMapView(
    grid: HeatmapGrid
) {
    val context = LocalContext.current
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
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
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

    var hasInitializedViewport by remember(grid.bounds) { mutableStateOf(false) }
    var selectedZoneDialog by remember { mutableStateOf<ZoneDialogContent?>(null) }
    val overlaySignature = "map-lite-${grid.cellsCount}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1A1A))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (!hasInitializedViewport) {
                    view.controller.setCenter(
                        GeoPoint(
                            (grid.bounds.minLatitude + grid.bounds.maxLatitude) / 2.0,
                            (grid.bounds.minLongitude + grid.bounds.maxLongitude) / 2.0
                        )
                    )
                    view.controller.setZoom(5.4)
                    hasInitializedViewport = true
                }

                if (view.tag != overlaySignature) {
                    view.overlays.clear()
                    addLightHeatmapZones(
                        mapView = view,
                        cells = grid.points,
                        onZoneTapped = { selectedZoneDialog = it }
                    )
                    view.tag = overlaySignature
                    view.invalidate()
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val nextZoom = (mapView.zoomLevelDouble + 1.0).coerceAtMost(22.0)
                    mapView.controller.setZoom(nextZoom)
                },
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Text(text = "+")
            }
            Button(
                onClick = {
                    val nextZoom = (mapView.zoomLevelDouble - 1.0).coerceAtLeast(3.0)
                    mapView.controller.setZoom(nextZoom)
                },
                modifier = Modifier.size(40.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, ButtonBorder)
            ) {
                Text(text = "-")
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clickable {
                    val center = mapView.mapCenter as? GeoPoint
                    center?.let {
                        openStreetViewAtLocation(
                            context = context,
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                    }
                },
            shape = RoundedCornerShape(10.dp),
            color = Color(0xB20E1B2A),
            border = BorderStroke(1.dp, Color(0x669DCBFF))
        ) {
            Text(
                text = "Street View",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE7F1FF),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        selectedZoneDialog?.let { zoneDialog ->
            AlertDialog(
                onDismissRequest = { selectedZoneDialog = null },
                title = { Text(text = zoneDialog.title) },
                text = { Text(text = zoneDialog.message) },
                confirmButton = {
                    TextButton(onClick = { selectedZoneDialog = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun CrimeHeatLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CrimeLegendChip(color = Color(0xFFF24B4B), label = "High")
        CrimeLegendChip(color = Color(0xFFF5C542), label = "Moderate")
        CrimeLegendChip(color = Color(0xFF2BB673).copy(alpha = 0.45f), label = "Low")
    }
}

@Composable
private fun CrimeLegendChip(
    color: Color,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF1F1F1F),
        border = BorderStroke(1.dp, Color(0xFF3A3A3A))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun createCrimeDotMarker(
    mapView: MapView,
    point: GeoPoint,
    color: Color,
    sizePx: Int,
    title: String
): Marker {
    val marker = Marker(mapView)
    marker.position = point
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    marker.title = title
    marker.icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()))
        setStroke(2, android.graphics.Color.argb(180, 255, 255, 255))
        setSize(sizePx, sizePx)
    }
    return marker
}

private fun addCrimeDotMarkers(
    mapView: MapView,
    cells: List<HeatCell>
) {
    if (cells.isEmpty()) return

    val maxCount = cells.maxOf { it.count }.coerceAtLeast(1)
    cells
        .sortedBy { it.count }
        .forEach { cell ->
            val ratio = cell.count.toFloat() / maxCount.toFloat()
            val color = crimeColorForCount(ratio)
            val sizePx = crimeDotSizePx(ratio)
            val marker = createCrimeDotMarker(
                mapView = mapView,
                point = GeoPoint(cell.latitude, cell.longitude),
                color = color,
                sizePx = sizePx,
                title = "${cell.count} crime reports"
            )
            mapView.overlays.add(marker)
        }
}

private fun addLightHeatmapZones(
    mapView: MapView,
    cells: List<HeatCell>,
    onZoneTapped: (ZoneDialogContent) -> Unit
) {
    if (cells.isEmpty()) return

    val renderable = cells
        .asSequence()
        .filter { it.count >= MIN_LIGHT_ZONE_INCIDENTS }
        .sortedByDescending { (it.count * it.weight).toFloat() }
        .take(MAX_LIGHT_HEAT_ZONES)
        .toList()

    if (renderable.isEmpty()) {
        return
    }

    val maxScore = renderable.maxOf { (it.count * it.weight).toFloat() }.coerceAtLeast(1f)
    renderable
        .sortedBy { (it.count * it.weight).toFloat() }
        .forEach { cell ->
            val score = (cell.count * cell.weight).toFloat()
            val ratio = (score / maxScore).coerceIn(0f, 1f)
            val radiusMeters = 20_000.0 + (ratio * 70_000.0)
            val riskBand = when {
                ratio >= 0.72f -> "High"
                ratio >= 0.38f -> "Moderate"
                else -> "Low"
            }
            val fillColor = when {
                ratio >= 0.72f -> android.graphics.Color.argb(92, 229, 57, 53)
                ratio >= 0.38f -> android.graphics.Color.argb(84, 251, 192, 45)
                else -> android.graphics.Color.argb(74, 67, 160, 71)
            }

            val zoneTitle = "$riskBand Risk Zone"
            val zoneMessage = buildString {
                append("Incidents in zone: ${cell.count}\n")
                append("Weighted score: ${String.format(Locale.US, "%.2f", score)}\n")
                append("Center: ${String.format(Locale.US, "%.4f", cell.latitude)}, ")
                append(String.format(Locale.US, "%.4f", cell.longitude))
            }

            val polygon = Polygon(mapView).apply {
                points = approximateCirclePoints(
                    center = GeoPoint(cell.latitude, cell.longitude),
                    radiusMeters = radiusMeters,
                    segments = 14
                )
                title = zoneTitle
                subDescription = zoneMessage
                strokeColor = android.graphics.Color.TRANSPARENT
                strokeWidth = 0f
                this.fillColor = fillColor
                setOnClickListener { _, _, _ ->
                    onZoneTapped(
                        ZoneDialogContent(
                            title = zoneTitle,
                            message = zoneMessage
                        )
                    )
                    true
                }
            }
            mapView.overlays.add(polygon)
        }
}

private fun approximateCirclePoints(
    center: GeoPoint,
    radiusMeters: Double,
    segments: Int
): MutableList<GeoPoint> {
    val earthRadiusMeters = 6_371_000.0
    val latRad = Math.toRadians(center.latitude)
    val lonRad = Math.toRadians(center.longitude)
    val angularDistance = radiusMeters / earthRadiusMeters
    val points = mutableListOf<GeoPoint>()

    for (index in 0..segments) {
        val bearing = (2.0 * PI * index) / segments.toDouble()
        val pointLat = asin(
            sin(latRad) * cos(angularDistance) +
                cos(latRad) * sin(angularDistance) * cos(bearing)
        )
        val pointLon = lonRad + atan2(
            sin(bearing) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(pointLat)
        )

        points.add(GeoPoint(Math.toDegrees(pointLat), Math.toDegrees(pointLon)))
    }

    return points
}

private fun addDangerZoneOverlays(
    mapView: MapView,
    zones: List<DangerZone>
) {
    if (zones.isEmpty()) return

    val maxIncidents = zones.maxOf { it.incidentCount }.coerceAtLeast(1)
    zones
        .sortedBy { it.riskScore }
        .forEach { zone ->
            val ratio = (zone.incidentCount.toFloat() / maxIncidents).coerceIn(0f, 1f)
            val radiusMeters = 320.0 + (ratio * 900.0)
            val fillColor = zoneFillColor(zone.riskLevel)
            val center = GeoPoint(zone.latitude, zone.longitude)

            val polygon = Polygon(mapView).apply {
                points = Polygon.pointsAsCircle(center, radiusMeters)
                strokeColor = android.graphics.Color.TRANSPARENT
                strokeWidth = 0f
                this.fillColor = fillColor
            }
            mapView.overlays.add(polygon)

            val centerMarker = createCrimeDotMarker(
                mapView = mapView,
                point = center,
                color = Color(0xFF7A5D2D).copy(alpha = 0.65f),
                sizePx = 9,
                title = "${zone.riskLevel.uppercase(Locale.US)} risk - ${zone.incidentCount} incidents"
            )
            mapView.overlays.add(centerMarker)
        }
}

private fun zoneFillColor(riskLevel: String): Int {
    val color = when (riskLevel.lowercase(Locale.US)) {
        "high" -> Color(0xFFF24B4B).copy(alpha = 0.36f)
        "moderate" -> Color(0xFFF5C542).copy(alpha = 0.30f)
        else -> Color(0xFF2BB673).copy(alpha = 0.25f)
    }

    return android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}

private fun crimeColorForCount(ratio: Float): Color {
    return when {
        ratio >= 0.7f -> Color(0xFFF24B4B)
        ratio >= 0.35f -> Color(0xFFF5C542)
        else -> Color(0xFF2BB673).copy(alpha = 0.45f)
    }
}

private fun crimeDotSizePx(ratio: Float): Int {
    return (14f + ratio.coerceIn(0f, 1f) * 20f).roundToInt().coerceIn(12, 34)
}

private fun buildGeoCrimeClusters(
    cells: List<HeatCell>,
    bounds: HeatmapBounds
): List<GeoCrimeCluster> {
    if (cells.isEmpty()) {
        return emptyList()
    }

    val latSpan = (bounds.maxLatitude - bounds.minLatitude).coerceAtLeast(0.0001)
    val lonSpan = (bounds.maxLongitude - bounds.minLongitude).coerceAtLeast(0.0001)
    val bucketSize = 0.04

    val buckets = linkedMapOf<Pair<Int, Int>, MutableList<HeatCell>>()
    cells.forEach { cell ->
        val y = (((cell.latitude - bounds.minLatitude) / latSpan) / bucketSize).toInt()
        val x = (((cell.longitude - bounds.minLongitude) / lonSpan) / bucketSize).toInt()
        val key = Pair(x, y)
        buckets.getOrPut(key) { mutableListOf() }.add(cell)
    }

    return buckets.values.map { bucket ->
        val totalCount = bucket.sumOf { it.count }.coerceAtLeast(1).toDouble()
        val weightedLat = bucket.sumOf { it.latitude * it.count.toDouble() } / totalCount
        val weightedLon = bucket.sumOf { it.longitude * it.count.toDouble() } / totalCount

        GeoCrimeCluster(
            latitude = weightedLat,
            longitude = weightedLon,
            count = totalCount.toInt(),
            members = bucket.size
        )
    }
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
private fun FallbackMapBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val horizontalStep = size.height / 6f
        val verticalStep = size.width / 6f

        repeat(7) { index ->
            val y = index * horizontalStep
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f
            )
        }

        repeat(7) { index ->
            val x = index * verticalStep
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
        }
    }
}

private fun projectHeatCell(
    latitude: Double,
    longitude: Double,
    rasterMap: RasterMap?,
    gridBounds: HeatmapBounds,
    canvasWidth: Float,
    canvasHeight: Float
): Offset {
    if (rasterMap != null) {
        val worldX = longitudeToWorldPx(longitude, rasterMap.zoom)
        val worldY = latitudeToWorldPx(latitude, rasterMap.zoom)
        val leftWorld = rasterMap.minTileX * TILE_SIZE_PX.toDouble()
        val topWorld = rasterMap.minTileY * TILE_SIZE_PX.toDouble()
        val x = (((worldX - leftWorld) / rasterMap.widthPx.toDouble()) * canvasWidth).toFloat()
        val y = (((worldY - topWorld) / rasterMap.heightPx.toDouble()) * canvasHeight).toFloat()
        return Offset(x, y)
    }

    val xRatio = ((longitude - gridBounds.minLongitude) / (gridBounds.maxLongitude - gridBounds.minLongitude)).coerceIn(0.0, 1.0)
    val yRatio = ((gridBounds.maxLatitude - latitude) / (gridBounds.maxLatitude - gridBounds.minLatitude)).coerceIn(0.0, 1.0)
    return Offset((xRatio * canvasWidth).toFloat(), (yRatio * canvasHeight).toFloat())
}

private fun heatRadiusPx(cell: HeatCell, baseSize: Float): Float {
    val normalizedWeight = cell.weight.coerceIn(0.0, 1.0)
    val normalizedCount = (cell.count.toFloat() / 250f).coerceIn(0f, 1f)
    return (baseSize * (0.018f + normalizedWeight.toFloat() * 0.04f)) + (normalizedCount * 18f)
}

private fun densityColorFor(ratio: Double): Color {
    val clampedRatio = ratio.coerceIn(0.0, 1.0).toFloat()
    val alpha = (0.12f + clampedRatio * 0.22f).coerceIn(0.12f, 0.34f)
    return when {
        clampedRatio >= 0.75f -> Color(0xFFF24B4B).copy(alpha = alpha)
        clampedRatio >= 0.45f -> Color(0xFFF59E0B).copy(alpha = alpha)
        else -> Color(0xFF2BB673).copy(alpha = alpha)
    }
}

private fun clusterRadiusPx(cluster: CrimeCluster, baseSize: Float): Float {
    val normalizedCount = (cluster.count.toFloat() / 450f).coerceIn(0f, 1f)
    val normalizedMembers = (cluster.members.toFloat() / 8f).coerceIn(0f, 1f)
    return (baseSize * 0.012f) + (normalizedCount * baseSize * 0.022f) + (normalizedMembers * 6f)
}

private fun buildCrimeClusters(
    cells: List<ProjectedHeatCell>,
    bucketSize: Float
): List<CrimeCluster> {
    if (cells.isEmpty()) {
        return emptyList()
    }

    val buckets = linkedMapOf<Pair<Int, Int>, MutableList<ProjectedHeatCell>>()
    cells.forEach { cell ->
        val key = Pair(
            (cell.center.x / bucketSize).toInt(),
            (cell.center.y / bucketSize).toInt()
        )
        buckets.getOrPut(key) { mutableListOf() }.add(cell)
    }

    return buckets.values.map { bucket ->
        val totalCount = bucket.sumOf { it.count }.coerceAtLeast(1).toDouble()
        val totalWeight = bucket.sumOf { it.weight * it.count.toDouble() }
        val weightedCenterX = bucket.sumOf { it.center.x * it.count.toDouble() } / totalCount
        val weightedCenterY = bucket.sumOf { it.center.y * it.count.toDouble() } / totalCount

        CrimeCluster(
            center = Offset(weightedCenterX.toFloat(), weightedCenterY.toFloat()),
            weight = totalWeight / totalCount,
            count = totalCount.toInt(),
            members = bucket.size
        )
    }
}

private fun zoneColorForScore(score: Float): Color {
    return when {
        score >= 0.7f -> Color(0xFFF24B4B)
        score >= 0.45f -> Color(0xFFF59E0B)
        else -> Color(0xFF2BB673)
    }
}

private fun longitudeToWorldPx(longitude: Double, zoom: Int): Double {
    val scale = TILE_SIZE_PX * 2.0.pow(zoom.toDouble())
    return (longitude + 180.0) / 360.0 * scale
}

private fun latitudeToWorldPx(latitude: Double, zoom: Int): Double {
    val constrainedLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
    val latitudeRad = Math.toRadians(constrainedLatitude)
    val scale = TILE_SIZE_PX * 2.0.pow(zoom.toDouble())
    return (1.0 - (ln(tan((PI / 4.0) + (latitudeRad / 2.0))) / PI)) / 2.0 * scale
}
@Composable
private fun RetryBackendButton(onRetry: () -> Unit) {
    Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonSurface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, ButtonBorder)
    ) {
        Text(text = "Retry")
    }
}

@Composable
private fun SummarySection(uiState: MapUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        border = BorderStroke(1.dp, Color(0xFF4A4A4A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (uiState) {
                MapUiState.Loading -> {
                    Text(
                        text = "Loading dataset summary from backend...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is MapUiState.Error -> {
                    Text(
                        text = "The backend did not respond quickly enough. Start it on port 8001, then retry from the card above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MapUiState.Ready -> {
                    SummaryLine(label = "Dataset", value = uiState.summary.datasetFile)
                    SummaryLine(label = "Total incidents", value = uiState.summary.totalIncidents.toString())
                    SummaryLine(label = "Average risk score", value = String.format(Locale.US, "%.3f", uiState.summary.averageRiskScore))
                    SummaryLine(label = "Rendered crime dots", value = uiState.grid.cellsCount.toString())
                    SummaryLine(label = "Map tiles", value = if (uiState.rasterMap == null) "Fallback grid" else "Loaded from OpenStreetMap")
                }
            }
        }
    }
}

@Composable
private fun TopCitiesSection(uiState: MapUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Top Cities In Dataset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            when (uiState) {
                is MapUiState.Ready -> {
                    uiState.summary.topCities.forEach { city ->
                        SummaryLine(label = city.city, value = city.count.toString())
                    }
                }

                MapUiState.Loading -> {
                    Text(
                        text = "Waiting for backend response...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MapUiState.Error -> {
                    Text(
                        text = "City breakdown becomes available after the backend responds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TopCrimeTypesSection(uiState: MapUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Top Crime Types In Dataset",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            when (uiState) {
                is MapUiState.Ready -> {
                    uiState.summary.topCrimeTypes.forEach { crimeType ->
                        SummaryLine(label = crimeType.crimeType.replace('_', ' '), value = crimeType.count.toString())
                    }
                }

                MapUiState.Loading -> {
                    Text(
                        text = "Waiting for backend response...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is MapUiState.Error -> {
                    Text(
                        text = "Crime-type breakdown becomes available after the backend responds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
@Composable
private fun SummaryLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

private suspend fun loadMapData(apiBaseUrl: String): MapUiState {
    return withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val summaryDeferred = async { fetchJson("$apiBaseUrl/api/heatmap/summary") }
                val gridDeferred = async { fetchJson("$apiBaseUrl/api/heatmap/grid?rows=120&cols=120") }

                val summaryJson = summaryDeferred.await()
                val gridJson = gridDeferred.await()
                val bounds = jsonToBounds(gridJson.getJSONObject("bounds"))

                MapUiState.Ready(
                    summary = HeatmapSummary(
                        datasetFile = summaryJson.getString("dataset_file"),
                        totalIncidents = summaryJson.getInt("total_incidents"),
                        averageRiskScore = summaryJson.getDouble("average_risk_score"),
                        topCities = jsonArrayToCities(summaryJson.getJSONArray("top_cities")),
                        topCrimeTypes = jsonArrayToCrimeTypes(summaryJson.getJSONArray("top_crime_types"))
                    ),
                    grid = HeatmapGrid(
                        sourceIncidents = gridJson.getInt("source_incidents"),
                        cellsCount = gridJson.getInt("cells_count"),
                        bounds = bounds,
                        points = jsonArrayToHeatCells(gridJson.getJSONArray("points"))
                    ),
                    zones = emptyList(),
                    rasterMap = null
                )
            }
        } catch (_: Exception) {
            MapUiState.Error(
                "Could not reach the backend. Make sure it's running on port 8001 with: python -m uvicorn app.main:app --host 0.0.0.0 --port 8001"
            )
        }
    }
}

private suspend fun loadAiSafetyInsights(
    apiBaseUrl: String,
    grid: HeatmapGrid,
    userLocation: Location
): AiSafetyUiState {
    return withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val coverageDistanceKm = distanceToCoverageBoundsKm(
                    bounds = grid.bounds,
                    latitude = userLocation.latitude,
                    longitude = userLocation.longitude
                )

                if (coverageDistanceKm > OUT_OF_COVERAGE_DISTANCE_KM) {
                    val baselinePercent = 6.0
                    return@coroutineScope AiSafetyUiState.Ready(
                        risk = AreaRiskPrediction(
                            riskPercent = baselinePercent,
                            riskProbability = baselinePercent / 100.0,
                            riskLevel = "low",
                            message = "Current location is outside dataset coverage. Baseline risk ${String.format(Locale.US, "%.1f", baselinePercent)}% shown.",
                            recommendation = "Dataset currently covers India. Add regional data for precise local scoring.",
                            modelName = "coverage_guard"
                        )
                    )
                }

                val nearestCell = nearestHeatCell(grid.points, userLocation.latitude, userLocation.longitude)
                val now = LocalDateTime.now()
                val hour = now.hour

                val localIntensity = (nearestCell?.count ?: 1).coerceAtLeast(1)
                val fallbackCrimeFrequencyAreaInput = (
                    ((nearestCell?.weight ?: 0.12) * 20.0) +
                        (sqrt(localIntensity.toDouble()) * 0.35)
                    ).coerceIn(1.0, 20.0)
                val crimeFrequencyAreaInput = 0.0
                val userReportsInput = (localIntensity / 2).coerceIn(0, 1000)
                val crowdDensity = when {
                    localIntensity >= 18 -> "high"
                    localIntensity >= 8 -> "medium"
                    else -> "low"
                }
                val lightingCondition = when {
                    hour >= 20 || hour <= 5 -> "poor"
                    hour in 7..17 -> "good"
                    else -> "moderate"
                }

                val riskDeferred = async {
                    runCatching {
                        postJson(
                            url = "$apiBaseUrl/api/ai/risk-predict",
                            payload = JSONObject()
                                .put("latitude", userLocation.latitude)
                                .put("longitude", userLocation.longitude)
                                .put("crime_frequency_area", crimeFrequencyAreaInput)
                                .put("hour_of_day", hour)
                                .put("crowd_density", crowdDensity)
                                .put("lighting_condition", lightingCondition)
                                .put("user_reports", userReportsInput),
                            timeoutMs = AI_REQUEST_TIMEOUT_MS
                        )
                    }.getOrElse {
                        val fallbackPercent = ((fallbackCrimeFrequencyAreaInput / 20.0) * 100.0).coerceIn(5.0, 95.0)
                        val fallbackLevel = when {
                            fallbackPercent >= 70.0 -> "high"
                            fallbackPercent >= 45.0 -> "moderate"
                            else -> "low"
                        }
                        JSONObject()
                            .put("risk_score", fallbackPercent / 100.0)
                            .put("risk_probability", fallbackPercent / 100.0)
                            .put("risk_percent", fallbackPercent)
                            .put("risk_level", fallbackLevel)
                            .put("model_name", "local_fallback")
                            .put("message", "This area has ${fallbackLevel.uppercase(Locale.US)} risk (${String.format(Locale.US, "%.1f", fallbackPercent)}%) right now")
                            .put("recommendation", "Using fallback estimate. Refresh after backend stabilizes.")
                    }
                }

                AiSafetyUiState.Ready(
                    risk = jsonToAreaRiskPrediction(riskDeferred.await())
                )
            }
        } catch (_: Exception) {
            AiSafetyUiState.Error(
                "AI safety services are temporarily unavailable. Check backend models and try again."
            )
        }
    }
}

private fun nearestHeatCell(points: List<HeatCell>, latitude: Double, longitude: Double): HeatCell? {
    if (points.isEmpty()) return null

    return points.minByOrNull { cell ->
        val latDelta = cell.latitude - latitude
        val lonDelta = cell.longitude - longitude
        (latDelta * latDelta) + (lonDelta * lonDelta)
    }
}

private fun distanceToCoverageBoundsKm(
    bounds: HeatmapBounds,
    latitude: Double,
    longitude: Double
): Double {
    val clampedLatitude = latitude.coerceIn(bounds.minLatitude, bounds.maxLatitude)
    val clampedLongitude = longitude.coerceIn(bounds.minLongitude, bounds.maxLongitude)
    return haversineDistanceKm(latitude, longitude, clampedLatitude, clampedLongitude)
}

private fun haversineDistanceKm(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val radiusKm = 6371.0
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaPhi = Math.toRadians(lat2 - lat1)
    val deltaLambda = Math.toRadians(lon2 - lon1)

    val a =
        sin(deltaPhi / 2.0).pow(2.0) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2.0).pow(2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return radiusKm * c
}

private fun jsonToAreaRiskPrediction(json: JSONObject): AreaRiskPrediction {
    val riskPercent = json.optDouble("risk_percent", json.optDouble("risk_probability", 0.0) * 100.0)
    return AreaRiskPrediction(
        riskPercent = riskPercent,
        riskProbability = json.optDouble("risk_probability", riskPercent / 100.0),
        riskLevel = json.optString("risk_level", "low"),
        message = json.optString("message", "Risk prediction unavailable."),
        recommendation = json.optString("recommendation", ""),
        modelName = json.optString("model_name", "unknown")
    )
}

private fun buildRasterMap(bounds: HeatmapBounds): RasterMap? {
    val startTime = System.currentTimeMillis()
    return try {
        val zoom = chooseTileZoom(bounds)
        val minTileX = floor(longitudeToWorldPx(bounds.minLongitude, zoom) / TILE_SIZE_PX).toInt()
        val maxTileX = floor(longitudeToWorldPx(bounds.maxLongitude, zoom) / TILE_SIZE_PX).toInt()
        val minTileY = floor(latitudeToWorldPx(bounds.maxLatitude, zoom) / TILE_SIZE_PX).toInt()
        val maxTileY = floor(latitudeToWorldPx(bounds.minLatitude, zoom) / TILE_SIZE_PX).toInt()

        val tilesWide = (maxTileX - minTileX + 1).coerceAtLeast(1)
        val tilesHigh = (maxTileY - minTileY + 1).coerceAtLeast(1)
        
        // Validate bitmap dimensions before creation
        val width = tilesWide * TILE_SIZE_PX
        val height = tilesHigh * TILE_SIZE_PX
        val estimatedBytes = (width.toLong() * height * 4) // ARGB_8888 = 4 bytes per pixel
        
        if (estimatedBytes > MAX_BITMAP_BYTES) {
            Log.w(TAG, "Bitmap too large: ${width}x${height} (${estimatedBytes / 1_000_000}MB). Skipping raster map.")
            return null
        }
        
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError creating bitmap ${width}x${height}: ${e.message}")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error creating bitmap: ${e.message}", e)
            return null
        }
        
        val canvas = AndroidCanvas(bitmap)
        var tilesAttempted = 0
        var tilesFailed = 0

        for (tileX in minTileX..maxTileX) {
            for (tileY in minTileY..maxTileY) {
                tilesAttempted++
                val elapsed = System.currentTimeMillis() - startTime
                
                // Abort if taking too long to prevent ANR
                if (elapsed > 5000) {
                    Log.w(TAG, "Raster map building timeout after ${elapsed}ms. Aborting after $tilesAttempted tiles.")
                    return null
                }
                
                val tile = fetchTileBitmap(zoom, tileX, tileY)
                if (tile == null) {
                    tilesFailed++
                    // Skip this tile instead of aborting entire map
                    continue
                }
                val left = (tileX - minTileX) * TILE_SIZE_PX
                val top = (tileY - minTileY) * TILE_SIZE_PX
                canvas.drawBitmap(tile, left.toFloat(), top.toFloat(), null)
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.i(TAG, "Built raster map: ${width}x${height}, zoom=$zoom, ${totalTime}ms (${tilesAttempted} tiles, $tilesFailed failed)")
        
        if (tilesFailed > tilesAttempted / 2) {
            Log.w(TAG, "Too many failed tiles ($tilesFailed/$tilesAttempted). Discarding raster map.")
            return null
        }
        
        RasterMap(
            bitmap = bitmap,
            zoom = zoom,
            minTileX = minTileX,
            minTileY = minTileY,
            widthPx = bitmap.width,
            heightPx = bitmap.height
        )
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error in buildRasterMap: ${e.message}", e)
        null
    }
}
private fun chooseTileZoom(bounds: HeatmapBounds): Int {
    val candidates = listOf(6, 5, 4)
    return candidates.firstOrNull { zoom ->
        val minTileX = floor(longitudeToWorldPx(bounds.minLongitude, zoom) / TILE_SIZE_PX).toInt()
        val maxTileX = floor(longitudeToWorldPx(bounds.maxLongitude, zoom) / TILE_SIZE_PX).toInt()
        val minTileY = floor(latitudeToWorldPx(bounds.maxLatitude, zoom) / TILE_SIZE_PX).toInt()
        val maxTileY = floor(latitudeToWorldPx(bounds.minLatitude, zoom) / TILE_SIZE_PX).toInt()
        (maxTileX - minTileX + 1) * (maxTileY - minTileY + 1) <= MAX_TILE_COUNT
    } ?: 4
}

private fun fetchTileBitmap(zoom: Int, tileX: Int, tileY: Int): Bitmap? {
    val endpoint = URL("https://tile.openstreetmap.org/$zoom/$tileX/$tileY.png")
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = TILE_FETCH_TIMEOUT_MS
    connection.readTimeout = TILE_FETCH_TIMEOUT_MS
    connection.setRequestProperty("User-Agent", "SmartCommunitySOS/1.0")

    return try {
        if (connection.responseCode !in 200..299) {
            Log.w(TAG, "Tile request failed: HTTP ${connection.responseCode} for zoom=$zoom, tile=($tileX,$tileY)")
            null
        } else {
            connection.inputStream.use { input -> BitmapFactory.decodeStream(input) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to fetch tile: ${e.message}")
        null
    } finally {
        try {
            connection.disconnect()
        } catch (e: Exception) {
            // Ignore disconnection errors
        }
    }
}

private fun fetchJson(url: String): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = REQUEST_TIMEOUT_MS
    connection.readTimeout = REQUEST_TIMEOUT_MS
    connection.useCaches = false

    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("HTTP $responseCode from $url")
        }

        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        Log.i(TAG, "Successfully fetched JSON from $url")
        return JSONObject(payload)
    } catch (e: Exception) {
        connection.disconnect()
        Log.e(TAG, "Error fetching JSON from $url: ${e.message}")
        throw e
    }
}

private fun postJson(
    url: String,
    payload: JSONObject,
    timeoutMs: Int = REQUEST_TIMEOUT_MS
): JSONObject {
    val endpoint = URL(url)
    val connection = endpoint.openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = timeoutMs
    connection.readTimeout = timeoutMs
    connection.useCaches = false
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")

    try {
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream
                ?.bufferedReader()
                ?.use { it.readText() }
                ?.take(400)
                ?: ""
            connection.disconnect()
            throw IllegalStateException("HTTP $responseCode from $url ${if (errorBody.isNotBlank()) "- $errorBody" else ""}")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        Log.i(TAG, "Successfully posted JSON to $url")
        return JSONObject(responseBody)
    } catch (e: Exception) {
        connection.disconnect()
        Log.e(TAG, "Error posting JSON to $url: ${e.message}")
        throw e
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

@SuppressLint("MissingPermission")
private fun startRealtimeLocationUpdates(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onLocation: (Location?) -> Unit
): LocationCallback {
    val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onLocation(location)
        }
    }

    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
        .setMinUpdateIntervalMillis(1500L)
        .setMinUpdateDistanceMeters(8f)
        .build()

    fusedLocationClient
        .requestLocationUpdates(request, callback, Looper.getMainLooper())
        .addOnFailureListener {
            onLocation(null)
        }

    val cancellationTokenSource = CancellationTokenSource()
    fusedLocationClient
        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
        .addOnSuccessListener { location ->
            onLocation(location)
        }
        .addOnFailureListener {
            onLocation(null)
        }

    return callback
}

private fun jsonToBounds(json: JSONObject): HeatmapBounds {
    return HeatmapBounds(
        minLatitude = json.getDouble("min_latitude"),
        maxLatitude = json.getDouble("max_latitude"),
        minLongitude = json.getDouble("min_longitude"),
        maxLongitude = json.getDouble("max_longitude")
    )
}

private fun jsonArrayToHeatCells(array: JSONArray): List<HeatCell> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONArray(index)
            add(
                HeatCell(
                    latitude = item.getDouble(0),
                    longitude = item.getDouble(1),
                    weight = item.getDouble(2),
                    count = item.getInt(3)
                )
            )
        }
    }
}

private fun jsonArrayToCities(array: JSONArray): List<CityCount> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(CityCount(city = item.getString("city"), count = item.getInt("count")))
        }
    }
}

private fun jsonArrayToCrimeTypes(array: JSONArray): List<CrimeTypeCount> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(CrimeTypeCount(crimeType = item.getString("crime_type"), count = item.getInt("count")))
        }
    }
}

private fun jsonArrayToDangerZones(array: JSONArray): List<DangerZone> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(
                DangerZone(
                    clusterId = item.getInt("cluster_id"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    incidentCount = item.getInt("incident_count"),
                    riskScore = item.getDouble("risk_score"),
                    riskLevel = item.getString("risk_level")
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MapScreenPreview() {
    SmartCommunitySOSTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Preview disabled for the live backend map. Run the app to see backend data.",
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Start
            )
        }
    }
}


