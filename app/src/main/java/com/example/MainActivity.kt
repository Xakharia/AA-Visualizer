package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.AudioMonitorService
import com.example.ui.dashboard.CarDashboardScreen
import com.example.ui.equalizer.EqualizerScreen
import com.example.ui.lyrics.LyricsScreen
import com.example.ui.settings.SettingsAndToolsScreen
import com.example.ui.theme.AAVisualizerTheme
import com.example.ui.visualizer.VisualizerScreen
import com.example.viewmodel.MainViewModel

const val ROUTE_VISUALIZER = "visualizer"
const val ROUTE_EQUALIZER = "equalizer"
const val ROUTE_LYRICS = "lyrics"
const val ROUTE_CAR_DASHBOARD = "car_dashboard"
const val ROUTE_SETTINGS = "settings"

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val APP_TABS = listOf(
    NavTab(ROUTE_VISUALIZER, "Visualizer", Icons.Default.GraphicEq),
    NavTab(ROUTE_EQUALIZER, "Equalizer", Icons.Default.Equalizer),
    NavTab(ROUTE_LYRICS, "Lyrics", Icons.Default.Lyrics),
    NavTab(ROUTE_CAR_DASHBOARD, "Auto", Icons.Default.DirectionsCar),
    NavTab(ROUTE_SETTINGS, "Engine", Icons.Default.Tune)
)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startMonitorService()

        val initialTab = intent?.getStringExtra(EXTRA_START_TAB) ?: ROUTE_VISUALIZER

        setContent {
            val config by viewModel.visualizerConfig.collectAsStateWithLifecycle()
            AAVisualizerTheme(
                themeStyle = config.themeStyle,
                accentHex = config.customAccentHex
            ) {
                AAVisualizerRoot(
                    viewModel = viewModel,
                    startRoute = initialTab
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemStatus()
    }

    private fun startMonitorService() {
        val intent = Intent(this, AudioMonitorService::class.java).apply {
            action = AudioMonitorService.ACTION_START
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    companion object {
        const val EXTRA_START_TAB = "start_tab"
    }
}

@Composable
fun AAVisualizerRoot(
    viewModel: MainViewModel,
    startRoute: String = ROUTE_VISUALIZER
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: startRoute

    val frame by viewModel.analysisFrame.collectAsStateWithLifecycle()
    val config by viewModel.visualizerConfig.collectAsStateWithLifecycle()
    val playback by viewModel.currentPlayback.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val albumBitmap by viewModel.albumBitmap.collectAsStateWithLifecycle()
    val albumColors by viewModel.albumAccentColors.collectAsStateWithLifecycle()
    val activeSource by viewModel.activeInputSource.collectAsStateWithLifecycle()
    val engineStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val dspStatus by viewModel.dspStatus.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val eqEnabled by viewModel.eqEnabled.collectAsStateWithLifecycle()
    val currentDevice by viewModel.currentDevice.collectAsStateWithLifecycle()
    val deviceDetailName by viewModel.deviceDetailName.collectAsStateWithLifecycle()
    val deviceRoutings by viewModel.deviceRoutings.collectAsStateWithLifecycle()
    val appRoutings by viewModel.appRoutings.collectAsStateWithLifecycle()
    val autoEqSwitchEnabled by viewModel.autoEqSwitchEnabled.collectAsStateWithLifecycle()
    val autoLyricsEnabled by viewModel.autoLyricsEnabled.collectAsStateWithLifecycle()
    val notificationAccessGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val screensaverPreview by viewModel.isScreensaverPreview.collectAsStateWithLifecycle()

    val hideChrome = (isFullscreen && currentRoute == ROUTE_VISUALIZER) ||
        (screensaverPreview && currentRoute == ROUTE_SETTINGS)

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = if (hideChrome) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail && !hideChrome) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        APP_TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !hideChrome) {
                    NavigationRail {
                        APP_TABS.forEach { tab ->
                            NavigationRailItem(
                                selected = currentRoute == tab.route,
                                onClick = { navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable(ROUTE_VISUALIZER) {
                        VisualizerScreen(
                            frame = frame,
                            config = config,
                            playback = playback,
                            lyricsState = lyricsState,
                            albumBitmap = albumBitmap,
                            albumColors = albumColors,
                            activeSource = activeSource,
                            engineStatus = engineStatus,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { viewModel.setFullscreen(!isFullscreen) },
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNextTrack = viewModel::nextTrack,
                            onPrevTrack = viewModel::prevTrack,
                            onPlayDemoTrack = viewModel::playDemoTrack,
                            onPickLocalFile = viewModel::playLocalAudioUri,
                            onSelectStyle = viewModel::selectVisualizerStyle,
                            onUpdateConfig = viewModel::updateVisualizerConfig
                        )
                    }
                    composable(ROUTE_EQUALIZER) {
                        EqualizerScreen(
                            activeProfile = activeProfile,
                            profiles = profiles,
                            eqEnabled = eqEnabled,
                            dspStatus = dspStatus,
                            currentDevice = currentDevice,
                            deviceDetailName = deviceDetailName,
                            deviceRoutings = deviceRoutings,
                            appRoutings = appRoutings,
                            autoEqSwitchEnabled = autoEqSwitchEnabled,
                            onToggleEq = viewModel::toggleEqEnabled,
                            onSelectProfile = viewModel::selectProfile,
                            onBandChange = viewModel::updateBand,
                            onBassBoostChange = viewModel::updateBassBoost,
                            onTrebleChange = viewModel::updateTreble,
                            onMasterGainChange = viewModel::updateMasterGain,
                            onBalanceChange = viewModel::updateBalance,
                            onToggleAgc = viewModel::toggleAgc,
                            onToggleSmartBass = viewModel::toggleSmartBass,
                            onToggleLimiter = viewModel::toggleLimiter,
                            onCreateCustom = { name -> viewModel.createCustomProfile(name) },
                            onDuplicate = viewModel::duplicateProfile,
                            onRename = viewModel::renameProfile,
                            onMoveOrder = viewModel::moveProfileOrder,
                            onDeleteCustom = viewModel::deleteCustomProfile,
                            onSetAutoEqSwitch = viewModel::setAutoEqSwitch,
                            onSetDeviceRouting = viewModel::setDeviceRouting,
                            onSetAppRouting = viewModel::setAppRouting,
                            onDeleteAppRouting = viewModel::deleteAppRouting
                        )
                    }
                    composable(ROUTE_LYRICS) {
                        LyricsScreen(
                            playback = playback,
                            lyricsState = lyricsState,
                            autoLyricsEnabled = autoLyricsEnabled,
                            onSetAutoLyrics = viewModel::setAutoLyrics,
                            onRefreshLyrics = viewModel::fetchLyrics,
                            onAdjustOffset = viewModel::adjustLyricsOffset
                        )
                    }
                    composable(ROUTE_CAR_DASHBOARD) {
                        CarDashboardScreen(
                            frame = frame,
                            config = config,
                            playback = playback,
                            activeProfile = activeProfile,
                            currentDevice = currentDevice,
                            lyricsState = lyricsState,
                            albumBitmap = albumBitmap,
                            albumColors = albumColors,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNextTrack = viewModel::nextTrack,
                            onPrevTrack = viewModel::prevTrack,
                            onCycleEqProfile = viewModel::cycleNextProfile,
                            onUpdateConfig = viewModel::updateVisualizerConfig
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsAndToolsScreen(
                            activeSource = activeSource,
                            engineStatus = engineStatus,
                            notificationAccessGranted = notificationAccessGranted,
                            config = config,
                            frame = frame,
                            playback = playback,
                            screensaverPreview = screensaverPreview,
                            onSetScreensaverPreview = viewModel::setScreensaverPreview,
                            onSelectInputSource = viewModel::switchInputSource,
                            onMediaProjectionResult = viewModel::startPlaybackCaptureWithProjection,
                            onUpdateConfig = viewModel::updateVisualizerConfig
                        )
                    }
                }
            }
        }
    }
}
