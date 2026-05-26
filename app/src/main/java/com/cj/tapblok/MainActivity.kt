package com.cj.tapblok

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cj.tapblok.ui.theme.TapBlokTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    val lifecycleOwner = LocalLifecycleOwner.current

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
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
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
                isIgnoringBatteryOptimizations =
                    BatteryOptimizationManager.isIgnoringBatteryOptimizations(context)
                isDeviceOwner = HardModeManager.isDeviceOwner(context)
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

    Surface(modifier = Modifier.fillMaxSize()) {
        if (allPermissionsGranted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 56.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "HomeBlok",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "App blocking with real-world friction",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (isServiceRunning) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isServiceRunning) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isServiceRunning) "Monitoring Active" else "Monitoring Inactive",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when {
                                    isServiceRunning && blockedAppAttempts > 0 ->
                                        "$blockedAppAttempts blocked attempt${if (blockedAppAttempts != 1) "s" else ""} this session"
                                    isServiceRunning -> "Session in progress"
                                    else -> "Tap below to start a session"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Primary action button — only shown when not monitoring
                if (!isServiceRunning) {
                    Button(
                        onClick = {
                            isServiceRunning = ensureMonitoringDefaultEnabled(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Monitoring",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Secondary actions card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ActionRow(
                            icon = Icons.Default.Apps,
                            label = "Manage Blocked Apps",
                            onClick = { context.startActivity(Intent(context, AppSelectionActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.LocationOn,
                            label = "Home Rules",
                            onClick = { context.startActivity(Intent(context, HomeRulesActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.Lock,
                            label = "Hard Mode",
                            onClick = { context.startActivity(Intent(context, HardModeActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.Nfc,
                            label = "Write NFC Tag",
                            onClick = { context.startActivity(Intent(context, NfcWriteActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.QrCode2,
                            label = "Show QR Code",
                            onClick = { context.startActivity(Intent(context, QrCodeActivity::class.java)) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ActionRow(
                            icon = Icons.Default.QrCodeScanner,
                            label = "Scan QR Code",
                            onClick = {
                                if (hasCameraPermission) {
                                    qrCodeScannerLauncher.launch(ScanOptions().setOrientationLocked(true))
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }
                }

                if (!hasFineLocationPermission) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Location Needed",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Grant location so HomeBlok can tell when you are home.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            ) {
                                Text("Grant")
                            }
                        }
                    }
                }

                if (!isIgnoringBatteryOptimizations) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Battery Protection",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Allow unrestricted battery use so Android does not put HomeBlok to sleep.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
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
                            ) {
                                Text("Allow Unrestricted Battery")
                            }
                        }
                    }
                }

                if (isServiceRunning && !isDeviceOwner) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Friction Override",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Answer 25 two-digit addition problems to stop this session.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!overrideStarted) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        overrideStarted = true
                                        overrideProgress = 0
                                        overrideLeft = randomTwoDigit()
                                        overrideRight = randomTwoDigit()
                                        overrideAnswer = ""
                                        overrideError = null
                                    }
                                ) {
                                    Text("Start Math Override")
                                }
                            } else {
                                Text(
                                    text = "Problem ${overrideProgress + 1} of 25",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$overrideLeft + $overrideRight",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                OutlinedTextField(
                                    value = overrideAnswer,
                                    onValueChange = { value ->
                                        overrideAnswer = value.filter { it.isDigit() }.take(3)
                                        overrideError = null
                                    },
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
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
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
                                        }
                                    ) {
                                        Text("Submit")
                                    }
                                    Button(
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            overrideStarted = false
                                            overrideAnswer = ""
                                            overrideError = null
                                        }
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                } else if (isServiceRunning && isDeviceOwner) {
                    Text(
                        text = "Hard Mode is active. The in-app force stop is disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "HomeBlok needs a few permissions to monitor and block apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (!hasUsagePermission) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    ) {
                        Text("Grant Usage Access")
                    }
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
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
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.38f
            )
        )
    }
}
