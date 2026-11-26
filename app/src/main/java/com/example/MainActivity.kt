package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.remote.GoogleCredentialAuthHelper
import com.example.domain.SubstanceCatalog
import com.example.ui.HealthSyncViewModel
import com.example.ui.HistoryViewModel
import com.example.ui.SettingsViewModel
import com.example.ui.TodayViewModel
import com.example.ui.components.GoogleSignInWebSheet
import com.example.ui.screens.HealthSyncScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TodayScreen
import com.example.ui.theme.CircadiaTheme
import com.example.ui.theme.MidnightSlate900
import com.example.ui.theme.MidnightSlate950

private const val ROUTE_TODAY = "today"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_HEALTH_SYNC = "health_sync"
private const val ROUTE_SETTINGS = "settings"

private data class CircadiaDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val CircadiaDestinations = listOf(
    CircadiaDestination(ROUTE_TODAY, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    CircadiaDestination(ROUTE_HISTORY, "History", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    CircadiaDestination(ROUTE_HEALTH_SYNC, "Health Sync", Icons.Filled.HealthAndSafety, Icons.Outlined.HealthAndSafety),
    CircadiaDestination(ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

class MainActivity : ComponentActivity() {

    private val appContainer by lazy { CircadiaAppContainer(applicationContext) }
    private val oauthRedirectUriState = mutableStateOf<android.net.Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Load bundled JSON substance half-life catalog
        SubstanceCatalog.initializeFromResources(this)

        // Capture any OAuth deep-link redirect if launched via custom URI
        extractOAuthRedirectUri(intent)?.let { uri ->
            oauthRedirectUriState.value = uri
        }

        enableEdgeToEdge()
        setContent {
            CircadiaTheme {
                CircadiaAppRoot(
                    container = appContainer,
                    oauthRedirectUri = oauthRedirectUriState.value,
                    onConsumeOAuthRedirectUri = { oauthRedirectUriState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN").isNullOrBlank()) {
            configureAppCheck(this, intent)
        }
        extractOAuthRedirectUri(intent)?.let { uri ->
            oauthRedirectUriState.value = uri
        }
    }

    private fun extractOAuthRedirectUri(intent: Intent?): android.net.Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        val isOAuthRedirect =
            data.scheme == "com.aistudio.circadia.k7m2vx" && data.path == "/oauth2redirect"
        return if (isOAuthRedirect) data else null
    }
}

@Composable
private fun CircadiaAppRoot(
    container: CircadiaAppContainer,
    oauthRedirectUri: android.net.Uri?,
    onConsumeOAuthRedirectUri: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_TODAY

    val todayViewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.factory(
            container.substanceRepository,
            container.aiRepository,
            container.prefsRepository
        )
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(
            container.substanceRepository,
            container.healthSyncRepository,
            container.aiRepository,
            container.prefsRepository
        )
    )
    val healthSyncViewModel: HealthSyncViewModel = viewModel(
        factory = HealthSyncViewModel.factory(
            container.healthSyncRepository,
            container.substanceRepository,
            container.prefsRepository
        )
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            container.prefsRepository,
            container.substanceRepository,
            container.persistenceRepository
        )
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val currentSettings = container.prefsRepository.getCurrentSettings()
        if (currentSettings.persistentDbEnabled && currentSettings.cloudSyncEnabled) {
            GoogleCredentialAuthHelper.attemptAutoSignIn(
                context = context,
                scope = coroutineScope,
                onAuthSuccess = { email ->
                    settingsViewModel.onFirebaseGoogleSignedIn(email)
                },
                onUnauthenticated = {}
            )
        }
    }

    // Process incoming Google OAuth 2.0 PKCE redirect
    androidx.compose.runtime.LaunchedEffect(oauthRedirectUri) {
        if (oauthRedirectUri != null) {
            healthSyncViewModel.handleOAuthRedirect(oauthRedirectUri)
            onConsumeOAuthRedirectUri()
            navController.navigate(ROUTE_HEALTH_SYNC) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val backdropBrush = Brush.verticalGradient(
        colors = listOf(MidnightSlate950, MidnightSlate900, MidnightSlate950)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropBrush)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    ) {
                        CircadiaDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
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
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        CircadiaDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    fun routeIndex(route: String?): Int =
                        CircadiaDestinations.indexOfFirst { it.route == route }.coerceAtLeast(0)

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_TODAY,
                        enterTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            val direction = if (toIdx >= fromIdx) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                                towards = direction,
                                animationSpec = tween(240),
                                initialOffset = { fullWidth -> fullWidth / 12 }
                            )
                        },
                        exitTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            val direction = if (toIdx >= fromIdx) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            fadeOut(animationSpec = tween(170)) + slideOutOfContainer(
                                towards = direction,
                                animationSpec = tween(220),
                                targetOffset = { fullWidth -> fullWidth / 14 }
                            )
                        },
                        popEnterTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            val direction = if (toIdx >= fromIdx) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                                towards = direction,
                                animationSpec = tween(240),
                                initialOffset = { fullWidth -> fullWidth / 12 }
                            )
                        },
                        popExitTransition = {
                            val fromIdx = routeIndex(initialState.destination.route)
                            val toIdx = routeIndex(targetState.destination.route)
                            val direction = if (toIdx >= fromIdx) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            fadeOut(animationSpec = tween(170)) + slideOutOfContainer(
                                towards = direction,
                                animationSpec = tween(220),
                                targetOffset = { fullWidth -> fullWidth / 14 }
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 780.dp)
                    ) {
                        composable(ROUTE_TODAY) {
                            val state by todayViewModel.uiState.collectAsStateWithLifecycle()
                            val aiState by todayViewModel.aiState.collectAsStateWithLifecycle()
                            val undoEvent by todayViewModel.undoEvent.collectAsStateWithLifecycle()
                            TodayScreen(
                                uiState = state,
                                aiState = aiState,
                                undoEvent = undoEvent,
                                onSaveOrUpdateEntry = todayViewModel::saveOrUpdateEntry,
                                onQuickAdjust = todayViewModel::applyInlineQuickStep,
                                onConfirmDeleteEntry = todayViewModel::confirmDeleteEntry,
                                onUndo = todayViewModel::undoLastAction,
                                onConsumeUndoEvent = todayViewModel::consumeUndoEvent,
                                onAnalyzeDay = { forceGemini -> todayViewModel.analyzeDay(forceGemini) },
                                onAnalyzeEntry = todayViewModel::analyzeSingleEntry,
                                onShowSavedDayReport = {
                                    state.savedDayReport?.let { todayViewModel.showSavedReport(it) }
                                },
                                onDismissAiSheet = todayViewModel::dismissAiSheet,
                                onOpenSettings = { navigateTo(ROUTE_SETTINGS) },
                                onSelectDecayModel = todayViewModel::setDecayModelMode,
                                onRefreshDay = { todayViewModel.analyzeDay(forceGeminiFallback = false, forceRefresh = true) },
                                onRefreshEntry = { todayViewModel.refreshSingleEntryAnalysis(it, false) },
                                onSearchMedications = container.medicationCatalogRepository::searchCatalog,
                                onResolveMedicationPk = container.medicationCatalogRepository::resolvePharmacokinetics
                            )
                        }
                        composable(ROUTE_HISTORY) {
                            val state by historyViewModel.uiState.collectAsStateWithLifecycle()
                            val aiState by historyViewModel.aiState.collectAsStateWithLifecycle()
                            val undoEvent by historyViewModel.undoEvent.collectAsStateWithLifecycle()
                            HistoryScreen(
                                uiState = state,
                                aiState = aiState,
                                undoEvent = undoEvent,
                                onSelectDate = historyViewModel::selectDate,
                                onPreviousMonth = historyViewModel::previousMonth,
                                onNextMonth = historyViewModel::nextMonth,
                                onSaveOrUpdateEntry = historyViewModel::saveOrUpdateEntry,
                                onQuickAdjust = historyViewModel::applyInlineQuickStep,
                                onConfirmDeleteEntry = historyViewModel::confirmDeleteEntry,
                                onUndo = historyViewModel::undoLastAction,
                                onConsumeUndoEvent = historyViewModel::consumeUndoEvent,
                                onAnalyzeSelectedDay = { forceGemini -> historyViewModel.analyzeSelectedDay(forceGemini) },
                                onAnalyzeEntry = historyViewModel::analyzeSingleEntry,
                                onShowSavedReport = historyViewModel::showSavedReport,
                                onDismissAiSheet = historyViewModel::dismissAiSheet,
                                onOpenSettings = { navigateTo(ROUTE_SETTINGS) },
                                onSelectDecayModel = historyViewModel::setDecayModelMode,
                                onRefreshDay = { historyViewModel.analyzeSelectedDay(forceGeminiFallback = false, forceRefresh = true) },
                                onRefreshEntry = { historyViewModel.refreshSingleEntryAnalysis(it, false) },
                                onSearchMedications = container.medicationCatalogRepository::searchCatalog,
                                onResolveMedicationPk = container.medicationCatalogRepository::resolvePharmacokinetics
                            )
                        }
                        composable(ROUTE_HEALTH_SYNC) {
                            val state by healthSyncViewModel.uiState.collectAsStateWithLifecycle()
                            HealthSyncScreen(
                                uiState = state,
                                onConnectGoogleFit = healthSyncViewModel::startGoogleFitOAuth,
                                onSyncGoogleFitNow = healthSyncViewModel::syncGoogleFitNow,
                                onDisconnectGoogleFit = healthSyncViewModel::disconnectGoogleFit,
                                onImportHealthFile = healthSyncViewModel::importHealthFile
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            val settings by settingsViewModel.settingsState.collectAsStateWithLifecycle()
                            val databaseStats by settingsViewModel.databaseStats.collectAsStateWithLifecycle()
                            val diskDatabaseStats by settingsViewModel.diskDatabaseStats.collectAsStateWithLifecycle()
                            val cloudAuthState by settingsViewModel.cloudAuthState.collectAsStateWithLifecycle()
                            val isPersistenceBusy by settingsViewModel.isPersistenceBusy.collectAsStateWithLifecycle()
                            val cloudSyncWorking by settingsViewModel.cloudSyncWorking.collectAsStateWithLifecycle()
                            val showDisableDialog by settingsViewModel.showDisablePersistenceDialog.collectAsStateWithLifecycle()
                            val healthSyncState by healthSyncViewModel.uiState.collectAsStateWithLifecycle()
                            val feedback by settingsViewModel.feedbackMessage.collectAsStateWithLifecycle()
                            SettingsScreen(
                                settings = settings,
                                databaseStats = databaseStats,
                                diskDatabaseStats = diskDatabaseStats,
                                cloudAuthState = cloudAuthState,
                                isPersistenceBusy = isPersistenceBusy,
                                cloudSyncWorking = cloudSyncWorking,
                                showDisablePersistenceDialog = showDisableDialog,
                                healthSyncState = healthSyncState,
                                feedbackMessage = feedback,
                                onClearFeedback = settingsViewModel::clearFeedback,
                                onTogglePersistentDatabase = settingsViewModel::onTogglePersistentDatabase,
                                onDismissDisablePersistenceDialog = settingsViewModel::dismissDisablePersistenceDialog,
                                onConfirmDisablePersistence = settingsViewModel::confirmDisablePersistence,
                                onToggleCloudSync = settingsViewModel::onToggleCloudSync,
                                onSyncCloudNow = settingsViewModel::syncCloudNow,
                                onClearSavedDatabase = settingsViewModel::clearSavedDatabase,
                                onFirebaseGoogleSignedIn = settingsViewModel::onFirebaseGoogleSignedIn,
                                onFirebaseAuthMessage = settingsViewModel::onFirebaseAuthMessage,
                                onFirebaseSignedOut = settingsViewModel::onFirebaseSignedOut,
                                onSaveBedtime = settingsViewModel::saveTargetBedtime,
                                onSaveAiSettings = settingsViewModel::saveAiSettings,
                                onSaveGoogleClientId = settingsViewModel::saveGoogleClientId,
                                onConnectGoogleFit = healthSyncViewModel::startGoogleFitOAuth,
                                onSyncGoogleFitNow = healthSyncViewModel::syncGoogleFitNow,
                                onDisconnectGoogleFit = healthSyncViewModel::disconnectGoogleFit,
                                onExportJsonBackup = settingsViewModel::exportJsonBackup,
                                onImportJsonBackup = settingsViewModel::importJsonBackup
                            )
                        }
                    }
                }
            }
        }

        val pendingAuthUri by healthSyncViewModel.pendingAuthUri.collectAsStateWithLifecycle()
        pendingAuthUri?.let { authUri ->
            GoogleSignInWebSheet(
                authUri = authUri,
                onWebSignInCompleted = {
                    healthSyncViewModel.completeGoogleWebSignIn()
                },
                onDismiss = {
                    healthSyncViewModel.dismissGoogleSignInSheet()
                }
            )
        }
    }
}
