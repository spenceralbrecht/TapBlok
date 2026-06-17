package com.cj.tapblok

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cj.tapblok.database.AppDatabase
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureMonitoringDefaultEnabled(this)
        setContent {
            TapBlokTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color(0xFF07140F).toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    var hasUsagePermission by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(isServiceRunning(context, AppMonitoringService::class.java)) }
    var isDeviceOwner by remember { mutableStateOf(HardModeManager.isDeviceOwner(context)) }
    var blockedAppAttempts by remember { mutableStateOf(0) }
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(BatteryOptimizationManager.isIgnoringBatteryOptimizations(context))
    }
    var hasFineLocationPermission by remember {
        mutableStateOf(LocationRuleManager.hasFineLocationPermission(context))
    }
    var hasBackgroundLocationPermission by remember {
        mutableStateOf(LocationRuleManager.hasBackgroundLocationPermission(context))
    }
    var vpnReady by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var websiteServiceRunning by remember { mutableStateOf(WebsiteBlockVpnService.isRunning) }
    var websiteServiceStarting by remember { mutableStateOf(false) }
    var websiteStartRefreshToken by remember { mutableStateOf(0) }
    var homeRuleState by remember { mutableStateOf(LocationRuleManager.state(context)) }
    var blockingPausedAtHome by remember {
        mutableStateOf(LocationRuleManager.shouldAllowBlockedAppsAtHome(context))
    }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val database = remember(context) { AppDatabase.getDatabase(context) }
    val blockedAppsFlow = remember(database) { database.blockedAppDao().getAllBlockedApps() }
    val blockedWebsitesFlow = remember(database) { database.blockedWebsiteDao().getAllBlockedWebsites() }
    val blockedApps by blockedAppsFlow.collectAsState(initial = emptyList())
    val blockedWebsites by blockedWebsitesFlow.collectAsState(initial = emptyList())

    fun startWebsiteBlockingFromDashboard() {
        WebsiteBlockVpnService.startIfAllowed(context)
        websiteServiceRunning = WebsiteBlockVpnService.isRunning
        websiteServiceStarting = !websiteServiceRunning
        if (websiteServiceStarting) {
            websiteStartRefreshToken += 1
        }
    }

    var overrideStarted by remember { mutableStateOf(false) }
    var overrideProgress by remember { mutableStateOf(0) }
    var overrideLeft by remember { mutableStateOf(randomTwoDigit()) }
    var overrideRight by remember { mutableStateOf(randomTwoDigit()) }
    var overrideAnswer by remember { mutableStateOf("") }
    var overrideError by remember { mutableStateOf<String?>(null) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasUsagePermission = hasUsageStatsPermission(context)
        canDrawOverlays = Settings.canDrawOverlays(context)
        if (hasUsagePermission && canDrawOverlays) {
            isServiceRunning = ensureMonitoringDefaultEnabled(context) ||
                isServiceRunning(context, AppMonitoringService::class.java)
        } else {
            isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
        }
        isDeviceOwner = HardModeManager.isDeviceOwner(context)
        vpnReady = VpnService.prepare(context) == null
        websiteServiceRunning = WebsiteBlockVpnService.isRunning
        homeRuleState = LocationRuleManager.state(context)
        blockingPausedAtHome = LocationRuleManager.shouldAllowBlockedAppsAtHome(context)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringBatteryOptimizations =
            BatteryOptimizationManager.isIgnoringBatteryOptimizations(context)
        isServiceRunning = ensureMonitoringDefaultEnabled(context) ||
            isServiceRunning(context, AppMonitoringService::class.java)
    }

    val fineLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasFineLocationPermission = isGranted
        if (isGranted) {
            LocationRuleManager.refreshCurrentHomeState(context, startWhenAway = false)
        }
        hasBackgroundLocationPermission = LocationRuleManager.hasBackgroundLocationPermission(context)
        homeRuleState = LocationRuleManager.state(context)
        blockingPausedAtHome = LocationRuleManager.shouldAllowBlockedAppsAtHome(context)
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        vpnReady = VpnService.prepare(context) == null
        if (vpnReady) {
            val monitoringStarted = ensureMonitoringDefaultEnabled(context) ||
                isServiceRunning(context, AppMonitoringService::class.java)
            isServiceRunning = monitoringStarted
            blockingPausedAtHome = LocationRuleManager.shouldAllowBlockedAppsAtHome(context)
            if (monitoringStarted && !blockingPausedAtHome) {
                startWebsiteBlockingFromDashboard()
            } else if (blockingPausedAtHome) {
                Toast.makeText(
                    context,
                    "Website blocking is paused by Home rules.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            websiteServiceRunning = WebsiteBlockVpnService.isRunning
        }
    }

    val qrCodeScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        val scannedContent = result.contents
        if (scannedContent != null && UnlockTokenManager.isValidUnlockToken(context, scannedContent)) {
            if (isServiceRunning) {
                context.stopService(Intent(context, AppMonitoringService::class.java))
                Toast.makeText(context, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                isServiceRunning = false
            } else {
                startMonitoringService(context)
                Toast.makeText(context, "Monitoring started.", Toast.LENGTH_SHORT).show()
                isServiceRunning = true
            }
        } else if (scannedContent != null) {
            Toast.makeText(context, "Incorrect QR Code", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = hasUsageStatsPermission(context)
                canDrawOverlays = Settings.canDrawOverlays(context)
                if (hasUsagePermission && canDrawOverlays) {
                    isServiceRunning = ensureMonitoringDefaultEnabled(context) ||
                        isServiceRunning(context, AppMonitoringService::class.java)
                } else {
                    isServiceRunning = isServiceRunning(context, AppMonitoringService::class.java)
                }
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                blockedAppAttempts = prefs.getInt("blocked_app_attempts", 0)
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                hasFineLocationPermission = LocationRuleManager.hasFineLocationPermission(context)
                hasBackgroundLocationPermission = LocationRuleManager.hasBackgroundLocationPermission(context)
                isIgnoringBatteryOptimizations =
                    BatteryOptimizationManager.isIgnoringBatteryOptimizations(context)
                isDeviceOwner = HardModeManager.isDeviceOwner(context)
                vpnReady = VpnService.prepare(context) == null
                websiteServiceRunning = WebsiteBlockVpnService.isRunning
                websiteServiceStarting = false
                homeRuleState = LocationRuleManager.state(context)
                blockingPausedAtHome = LocationRuleManager.shouldAllowBlockedAppsAtHome(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allPermissionsGranted = hasUsagePermission && canDrawOverlays

    LaunchedEffect(allPermissionsGranted) {
        if (allPermissionsGranted) {
            isServiceRunning = ensureMonitoringDefaultEnabled(context) ||
                isServiceRunning(context, AppMonitoringService::class.java)
        }
    }

    LaunchedEffect(websiteStartRefreshToken) {
        if (websiteStartRefreshToken > 0) {
            delay(1200)
            websiteServiceRunning = WebsiteBlockVpnService.isRunning
            websiteServiceStarting = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF07140F)
    ) {
        if (allPermissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF07140F),
                                Color(0xFF0D2A1D),
                                Color(0xFFF3F6EF)
                            ),
                            endY = 1100f
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 44.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    HomeTopBar(isServiceRunning = isServiceRunning)

                    ProtectionHero(
                        isServiceRunning = isServiceRunning,
                        blockedAppAttempts = blockedAppAttempts,
                        blockedAppCount = blockedApps.size,
                        blockedWebsiteCount = blockedWebsites.size,
                        vpnReady = vpnReady,
                        websiteServiceRunning = websiteServiceRunning,
                        onStartMonitoring = {
                            isServiceRunning = ensureMonitoringDefaultEnabled(context)
                        }
                    )

                    if (!hasFineLocationPermission) {
                        RequirementPrompt(
                            icon = Icons.Default.MyLocation,
                            title = "Enable home detection",
                            description = "Location lets HomeBlok know when home rules should activate.",
                            actionLabel = "Grant location",
                            onAction = {
                                fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        )
                    }

                    if (!isIgnoringBatteryOptimizations) {
                        RequirementPrompt(
                            icon = Icons.Default.Security,
                            title = "Keep protection awake",
                            description = "Allow unrestricted battery use so Android does not pause blocking.",
                            actionLabel = "Allow battery access",
                            onAction = {
                                val requestIntent =
                                    BatteryOptimizationManager.requestIgnoreBatteryOptimizationsIntent(context)
                                runCatching {
                                    batteryOptimizationLauncher.launch(requestIntent)
                                }.onFailure {
                                    batteryOptimizationLauncher.launch(
                                        BatteryOptimizationManager.appBatterySettingsIntent(context)
                                    )
                                }
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeading(text = "Control center")
                        ControlTileRow(
                            left = {
                                ControlTile(
                                    icon = Icons.Default.Apps,
                                    label = "Apps",
                                    subtitle = "Blocked list",
                                    onClick = { context.startActivity(Intent(context, AppSelectionActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            },
                            right = {
                                ControlTile(
                                    icon = Icons.Default.Language,
                                    label = "Websites",
                                    subtitle = "Domains",
                                    onClick = { context.startActivity(Intent(context, WebsiteRulesActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        )
                        ControlTileRow(
                            left = {
                                ControlTile(
                                    icon = Icons.Default.LocationOn,
                                    label = "Home",
                                    subtitle = "Rules",
                                    onClick = { context.startActivity(Intent(context, HomeRulesActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            },
                            right = {
                                ControlTile(
                                    icon = Icons.Default.Lock,
                                    label = "Hard Mode",
                                    subtitle = "Lockdown",
                                    onClick = { context.startActivity(Intent(context, HardModeActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        )

                        ProtectionChecklist(
                            isServiceRunning = isServiceRunning,
                            hasUsagePermission = hasUsagePermission,
                            canDrawOverlays = canDrawOverlays,
                            blockedAppCount = blockedApps.size,
                            blockedWebsiteCount = blockedWebsites.size,
                            vpnReady = vpnReady,
                            websiteServiceRunning = websiteServiceRunning,
                            websiteServiceStarting = websiteServiceStarting,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                            hasFineLocationPermission = hasFineLocationPermission,
                            hasBackgroundLocationPermission = hasBackgroundLocationPermission,
                            homeRuleState = homeRuleState,
                            blockingPausedAtHome = blockingPausedAtHome,
                            onStartMonitoring = {
                                isServiceRunning = ensureMonitoringDefaultEnabled(context)
                            },
                            onGrantUsage = {
                                settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            onGrantOverlay = {
                                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                            },
                            onManageApps = {
                                context.startActivity(Intent(context, AppSelectionActivity::class.java))
                            },
                            onManageWebsites = {
                                context.startActivity(Intent(context, WebsiteRulesActivity::class.java))
                            },
                            onAllowWebsiteBlocking = {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    vpnPermissionLauncher.launch(intent)
                                } else {
                                    vpnReady = true
                                    val monitoringStarted = ensureMonitoringDefaultEnabled(context) ||
                                        isServiceRunning(context, AppMonitoringService::class.java)
                                    isServiceRunning = monitoringStarted
                                    blockingPausedAtHome = LocationRuleManager.shouldAllowBlockedAppsAtHome(context)
                                    if (monitoringStarted && !blockingPausedAtHome) {
                                        startWebsiteBlockingFromDashboard()
                                    } else if (blockingPausedAtHome) {
                                        Toast.makeText(
                                            context,
                                            "Website blocking is paused by Home rules.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    websiteServiceRunning = WebsiteBlockVpnService.isRunning
                                }
                            },
                            onGrantBattery = {
                                val requestIntent =
                                    BatteryOptimizationManager.requestIgnoreBatteryOptimizationsIntent(context)
                                runCatching {
                                    batteryOptimizationLauncher.launch(requestIntent)
                                }.onFailure {
                                    batteryOptimizationLauncher.launch(
                                        BatteryOptimizationManager.appBatterySettingsIntent(context)
                                    )
                                }
                            },
                            onHomeRules = {
                                context.startActivity(Intent(context, HomeRulesActivity::class.java))
                            }
                        )

                        Spacer(modifier = Modifier.height(96.dp))
                        SectionHeading(text = "Utilities")
                        ControlTileRow(
                            left = {
                                ControlTile(
                                    icon = Icons.Default.Nfc,
                                    label = "NFC",
                                    subtitle = "Write tag",
                                    onClick = { context.startActivity(Intent(context, NfcWriteActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            },
                            right = {
                                ControlTile(
                                    icon = Icons.Default.QrCode2,
                                    label = "QR Code",
                                    subtitle = "Show code",
                                    onClick = { context.startActivity(Intent(context, QrCodeActivity::class.java)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        )
                        ControlTile(
                            icon = Icons.Default.QrCodeScanner,
                            label = "Scan unlock code",
                            subtitle = "Use a trusted QR code to control a session",
                            onClick = {
                                if (hasCameraPermission) {
                                    qrCodeScannerLauncher.launch(ScanOptions().setOrientationLocked(true))
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            compact = true
                        )
                    }

                    if (isServiceRunning && !isDeviceOwner) {
                        FrictionOverrideCard(
                            overrideStarted = overrideStarted,
                            overrideProgress = overrideProgress,
                            overrideLeft = overrideLeft,
                            overrideRight = overrideRight,
                            overrideAnswer = overrideAnswer,
                            overrideError = overrideError,
                            onStart = {
                                overrideStarted = true
                                overrideProgress = 0
                                overrideLeft = randomTwoDigit()
                                overrideRight = randomTwoDigit()
                                overrideAnswer = ""
                                overrideError = null
                            },
                            onAnswerChange = { value ->
                                overrideAnswer = value.filter { it.isDigit() }.take(3)
                                overrideError = null
                            },
                            onSubmit = {
                                val answer = overrideAnswer.toIntOrNull()
                                if (answer == overrideLeft + overrideRight) {
                                    val nextProgress = overrideProgress + 1
                                    if (nextProgress >= 25) {
                                        context.stopService(Intent(context, AppMonitoringService::class.java))
                                        Toast.makeText(context, "Monitoring stopped.", Toast.LENGTH_SHORT).show()
                                        isServiceRunning = false
                                        overrideStarted = false
                                        overrideProgress = 0
                                    } else {
                                        overrideProgress = nextProgress
                                        overrideLeft = randomTwoDigit()
                                        overrideRight = randomTwoDigit()
                                        overrideAnswer = ""
                                        overrideError = null
                                    }
                                } else {
                                    overrideAnswer = ""
                                    overrideError = "Incorrect. Try this one again."
                                }
                            },
                            onCancel = {
                                overrideStarted = false
                                overrideAnswer = ""
                                overrideError = null
                            }
                        )
                    } else if (isServiceRunning && isDeviceOwner) {
                        Text(
                            text = "Hard Mode is active. The in-app force stop is disabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE1F6E7)
                        )
                    }
                }
            }
        } else {
            // Permissions screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color(0xFFBDF7CE)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "HomeBlok needs a few permissions to monitor and block apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB9D9C4),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (!hasUsagePermission) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBDF7CE),
                            contentColor = Color(0xFF06371F)
                        )
                    ) {
                        Text("Grant Usage Access")
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBDF7CE),
                            contentColor = Color(0xFF06371F)
                        )
                    ) {
                        Text("Grant Overlay Permission")
                    }
                }
            }
        }
    }
}

private fun randomTwoDigit(): Int = Random.nextInt(10, 100)

@Composable
private fun HomeTopBar(isServiceRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "HomeBlok",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Text(
                text = "Friction that actually holds.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB9D9C4)
            )
        }
        Box(
            modifier = Modifier
                .background(
                    color = if (isServiceRunning) Color(0xFFBDF7CE) else Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isServiceRunning) "ACTIVE" else "READY",
                style = MaterialTheme.typography.labelMedium,
                color = if (isServiceRunning) Color(0xFF06371F) else Color.White
            )
        }
    }
}

@Composable
private fun ProtectionHero(
    isServiceRunning: Boolean,
    blockedAppAttempts: Int,
    blockedAppCount: Int,
    blockedWebsiteCount: Int,
    vpnReady: Boolean,
    websiteServiceRunning: Boolean,
    onStartMonitoring: () -> Unit
) {
    val hasAppTargets = blockedAppCount > 0
    val hasWebsiteTargets = blockedWebsiteCount > 0
    val websiteBlockingActive = hasWebsiteTargets && vpnReady && websiteServiceRunning
    val eyebrow = when {
        isServiceRunning && hasAppTargets && (!hasWebsiteTargets || websiteBlockingActive) -> "Protected session"
        isServiceRunning && hasWebsiteTargets && websiteBlockingActive -> "Website session"
        isServiceRunning && hasWebsiteTargets -> "Website setup needed"
        isServiceRunning -> "Session running"
        else -> "Ready to protect"
    }
    val headline = when {
        isServiceRunning && hasAppTargets && (!hasWebsiteTargets || websiteBlockingActive) -> "Your exits are blocked"
        isServiceRunning && hasWebsiteTargets && websiteBlockingActive -> "Websites are blocked"
        isServiceRunning && hasWebsiteTargets -> "Finish website setup"
        isServiceRunning && hasAppTargets -> "App blocking is active"
        isServiceRunning -> "Choose what to block"
        else -> "Start a protected session"
    }
    val body = when {
        isServiceRunning && hasAppTargets && hasWebsiteTargets && websiteBlockingActive ->
            "Apps, websites, and override friction are armed."
        isServiceRunning && hasAppTargets && hasWebsiteTargets ->
            "Selected apps are blocked. Finish website VPN setup to block domains."
        isServiceRunning && hasAppTargets -> "Selected apps and override friction are armed."
        isServiceRunning && hasWebsiteTargets && websiteBlockingActive ->
            "Selected domains are blocked through the website VPN."
        isServiceRunning && hasWebsiteTargets -> "Allow the website VPN before selected domains can be blocked."
        isServiceRunning -> "Monitoring is on, but no apps or websites are selected yet."
        else -> "Turn on app and website blocking with one tap."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0B7B49),
                            Color(0xFF0E5E3B),
                            Color(0xFF0B2318)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF087A49),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFBDF7CE)
                        )
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFE7F8EC)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusChip(label = "Attempts", value = blockedAppAttempts.toString(), modifier = Modifier.weight(1f))
                    StatusChip(label = "Override", value = "25 math", modifier = Modifier.weight(1f))
                }

                if (!isServiceRunning) {
                    Button(
                        onClick = onStartMonitoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF06371F)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Start protected session")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFBDF7CE)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

@Composable
private fun ProtectionChecklist(
    isServiceRunning: Boolean,
    hasUsagePermission: Boolean,
    canDrawOverlays: Boolean,
    blockedAppCount: Int,
    blockedWebsiteCount: Int,
    vpnReady: Boolean,
    websiteServiceRunning: Boolean,
    websiteServiceStarting: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    hasFineLocationPermission: Boolean,
    hasBackgroundLocationPermission: Boolean,
    homeRuleState: HomeRuleState,
    blockingPausedAtHome: Boolean,
    onStartMonitoring: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
    onManageApps: () -> Unit,
    onManageWebsites: () -> Unit,
    onAllowWebsiteBlocking: () -> Unit,
    onGrantBattery: () -> Unit,
    onHomeRules: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFEFA)),
        border = BorderStroke(1.dp, Color(0xFFE0E7DD))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ControlIcon(
                    icon = Icons.Default.Security,
                    enabled = true,
                    size = 46.dp,
                    iconSize = 25.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Protection checklist",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF17211B)
                    )
                    Text(
                        text = "If blocking fails, start here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667268)
                    )
                }
            }

            ProtectionCheckRow(
                title = "Monitoring service",
                detail = if (isServiceRunning) "Running" else "Stopped",
                healthy = isServiceRunning,
                actionLabel = if (isServiceRunning) null else "Start",
                onAction = onStartMonitoring
            )
            ProtectionCheckRow(
                title = "Usage access",
                detail = if (hasUsagePermission) "Allowed" else "Required to detect opened apps",
                healthy = hasUsagePermission,
                actionLabel = if (hasUsagePermission) null else "Grant",
                onAction = onGrantUsage
            )
            ProtectionCheckRow(
                title = "Overlay permission",
                detail = if (canDrawOverlays) "Allowed" else "Required to show the block screen",
                healthy = canDrawOverlays,
                actionLabel = if (canDrawOverlays) null else "Grant",
                onAction = onGrantOverlay
            )
            ProtectionCheckRow(
                title = "Blocked apps",
                detail = if (blockedAppCount > 0) "$blockedAppCount selected" else "No apps selected",
                healthy = blockedAppCount > 0,
                actionLabel = if (blockedAppCount > 0) "Edit" else "Add",
                onAction = onManageApps
            )
            ProtectionCheckRow(
                title = "Website VPN",
                detail = when {
                    blockedWebsiteCount == 0 -> "Add domains before VPN matters"
                    blockingPausedAtHome -> "Paused by home rules"
                    !vpnReady -> "Permission needed"
                    websiteServiceStarting -> "Starting..."
                    websiteServiceRunning -> "Running"
                    isServiceRunning -> "Restart needed"
                    else -> "Start monitoring to block domains"
                },
                healthy = blockedWebsiteCount > 0 && vpnReady && websiteServiceRunning && !blockingPausedAtHome,
                actionLabel = when {
                    blockedWebsiteCount == 0 -> "Add"
                    blockingPausedAtHome -> "Review"
                    !vpnReady -> "Allow"
                    websiteServiceStarting -> null
                    !websiteServiceRunning -> if (isServiceRunning) "Restart" else "Start"
                    else -> null
                },
                onAction = when {
                    blockedWebsiteCount == 0 -> onManageWebsites
                    blockingPausedAtHome -> onHomeRules
                    else -> onAllowWebsiteBlocking
                }
            )
            ProtectionCheckRow(
                title = "Blocked websites",
                detail = if (blockedWebsiteCount > 0) "$blockedWebsiteCount domains" else "No domains selected",
                healthy = blockedWebsiteCount > 0,
                actionLabel = if (blockedWebsiteCount > 0) "Edit" else "Add",
                onAction = onManageWebsites
            )
            ProtectionCheckRow(
                title = "Battery access",
                detail = if (isIgnoringBatteryOptimizations) "Unrestricted" else "Android may pause blocking",
                healthy = isIgnoringBatteryOptimizations,
                actionLabel = if (isIgnoringBatteryOptimizations) null else "Allow",
                onAction = onGrantBattery
            )
            ProtectionCheckRow(
                title = "Home rules",
                detail = when {
                    !homeRuleState.enabled -> "Off"
                    !hasFineLocationPermission || !hasBackgroundLocationPermission -> "Location setup needed"
                    blockingPausedAtHome -> "Blocking paused at home"
                    else -> "Not pausing blocking"
                },
                healthy = homeRuleState.enabled &&
                    hasFineLocationPermission &&
                    hasBackgroundLocationPermission &&
                    !blockingPausedAtHome,
                actionLabel = when {
                    homeRuleState.enabled && (!hasFineLocationPermission || !hasBackgroundLocationPermission) -> "Open"
                    homeRuleState.enabled -> "Review"
                    else -> "Set up"
                },
                onAction = onHomeRules
            )
        }
    }
}

@Composable
private fun ProtectionCheckRow(
    title: String,
    detail: String,
    healthy: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (healthy) Color(0xFF087A49) else Color(0xFFD35F2B),
                    shape = CircleShape
                )
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF17211B)
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF667268)
            )
        }
        actionLabel?.let {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (healthy) Color(0xFFE6F4EA) else Color(0xFF087A49),
                    contentColor = if (healthy) Color(0xFF087A49) else Color.White
                )
            ) {
                Text(it)
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFDFEFA).copy(alpha = 0.92f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF17211B)
        )
    }
}

@Composable
private fun ControlTileRow(
    left: @Composable RowScope.() -> Unit,
    right: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        left()
        right()
    }
}

@Composable
private fun ControlTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .height(if (compact) 104.dp else 118.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFEFA)),
        border = BorderStroke(1.dp, Color(0xFFE0E7DD))
    ) {
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ControlIcon(icon = icon, enabled = enabled, size = 48.dp, iconSize = 26.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (enabled) Color(0xFF17211B) else Color(0xFF70776F),
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667268),
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF8D968E)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                ControlIcon(icon = icon, enabled = enabled, size = 48.dp, iconSize = 26.dp)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) Color(0xFF17211B) else Color(0xFF70776F),
                            maxLines = 1
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF667268),
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF8D968E)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFFE6F4EA), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color(0xFF087A49) else Color(0xFF6F786F),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun RequirementPrompt(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E7DD))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFFE6F4EA), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF087A49)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF17211B)
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF667268)
                    )
                }
            }
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF087A49),
                    contentColor = Color.White
                )
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun FrictionOverrideCard(
    overrideStarted: Boolean,
    overrideProgress: Int,
    overrideLeft: Int,
    overrideRight: Int,
    overrideAnswer: String,
    overrideError: String?,
    onStart: () -> Unit,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111A15))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Friction Override",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                text = "Stopping a session requires 25 two-digit additions.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC3D1C8)
            )
            if (!overrideStarted) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBDF7CE),
                        contentColor = Color(0xFF06371F)
                    ),
                    onClick = onStart
                ) {
                    Text("Start math override")
                }
            } else {
                LinearProgressIndicator(
                    progress = { overrideProgress / 25f },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFBDF7CE),
                    trackColor = Color.White.copy(alpha = 0.16f)
                )
                Text(
                    text = "Problem ${overrideProgress + 1} of 25",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFBDF7CE)
                )
                Text(
                    text = "$overrideLeft + $overrideRight",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = overrideAnswer,
                    onValueChange = onAnswerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Answer") },
                    singleLine = true,
                    isError = overrideError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                overrideError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFB4AB)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onSubmit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBDF7CE),
                            contentColor = Color(0xFF06371F)
                        )
                    ) {
                        Text("Submit")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
