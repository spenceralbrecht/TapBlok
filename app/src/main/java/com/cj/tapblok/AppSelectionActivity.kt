package com.cj.tapblok

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.cj.tapblok.database.BlockedApp
import com.cj.tapblok.database.BlockedAppDao
import com.cj.tapblok.ui.theme.TapBlokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSelected: Boolean = false
)

class AppSelectionViewModel(private val blockedAppDao: BlockedAppDao, private val application: Application) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = application.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val allApps = pm.queryIntentActivities(intent, 0)

            val baseAppList = allApps.mapNotNull { app ->
                val packageName = app.activityInfo?.packageName ?: return@mapNotNull null
                if (ProtectedPackages.isProtected(packageName, application.packageName)) {
                    return@mapNotNull null
                }
                try {
                    AppInfo(
                        appName = app.loadLabel(pm).toString(),
                        packageName = packageName
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AppSelectionViewModel", "Skipping $packageName: ${e.message}")
                    null
                }
            }.sortedBy { it.appName.lowercase() }

            blockedAppDao.getAllBlockedApps().collect { blockedApps ->
                val blockedAppPackages = blockedApps.map { it.packageName }.toSet()
                _apps.value = baseAppList.map { it.copy(isSelected = blockedAppPackages.contains(it.packageName)) }
            }
        }
    }

    fun onAppSelectionChanged(app: AppInfo, isSelected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isSelected) {
                blockedAppDao.insert(BlockedApp(packageName = app.packageName))
            } else {
                blockedAppDao.delete(BlockedApp(packageName = app.packageName))
            }
        }
    }

    fun selectAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val allAppPackages = apps.value.map { BlockedApp(it.packageName) }
            blockedAppDao.insertAll(allAppPackages)
        }
    }

    fun unselectAllApps() {
        viewModelScope.launch(Dispatchers.IO) {
            blockedAppDao.deleteAll()
        }
    }
}

class AppSelectionViewModelFactory(private val application: Application, private val blockedAppDao: BlockedAppDao) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppSelectionViewModel(blockedAppDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AppSelectionActivity : ComponentActivity() {
    private val viewModel: AppSelectionViewModel by viewModels {
        AppSelectionViewModelFactory(
            application,
            (application as App).database.blockedAppDao()
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapBlokTheme {
                val appList by viewModel.apps.collectAsState()
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "Blocked Apps",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.selectAllApps() }) {
                                    Icon(Icons.Default.Checklist, contentDescription = "Select all apps")
                                }
                                IconButton(onClick = { viewModel.unselectAllApps() }) {
                                    Icon(Icons.Default.RemoveDone, contentDescription = "Clear selected apps")
                                }
                            }
                        )
                    }
                ) { padding ->
                    AppSelectionScreen(
                        apps = appList,
                        onAppCheckedChange = { app, isSelected ->
                            viewModel.onAppSelectionChanged(app, isSelected)
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppSelectionScreen(
    apps: List<AppInfo>,
    onAppCheckedChange: (AppInfo, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppListItem(
                app = app,
                onCheckedChange = { isSelected ->
                    onAppCheckedChange(app, isSelected)
                }
            )
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfo,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!app.isSelected) }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(context.packageManager.getApplicationIcon(app.packageName))
                    .build()
            ),
            contentDescription = "${app.appName} icon",
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(16.dp))
        Checkbox(
            checked = app.isSelected,
            onCheckedChange = onCheckedChange,
        )
    }
}
