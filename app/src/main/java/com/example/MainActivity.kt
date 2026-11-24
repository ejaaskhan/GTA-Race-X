package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.GameScreen
import com.example.engine.GameGLSurfaceView
import com.example.ui.AuthScreen
import com.example.ui.CareerOverlay
import com.example.ui.GameViewModel
import com.example.ui.GameplayHudOverlay
import com.example.ui.GarageOverlay
import com.example.ui.MainMenuOverlay
import com.example.ui.PlayModeOverlay
import com.example.ui.ProfileOverlay
import com.example.ui.SettingsOverlay
import com.example.ui.SplashOverlay
import com.example.ui.performSignOut
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.GtaRaceXTheme
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.TextPrimary
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureImmersiveFullscreen()

        setContent {
            GtaRaceXTheme {
                GtaRaceXApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveFullscreen()
        }
    }

    private fun configureImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun GtaRaceXApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var showSplash by remember { mutableStateOf(true) }
    var flyInToken by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    flyInToken++
                }
            )
        } else {
            val uid = user.uid
            key(uid) {
                AuthenticatedGameHost(
                    uid = uid,
                    flyInToken = flyInToken
                )
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SplashOverlay(
                onFinished = {
                    if (showSplash) {
                        showSplash = false
                        flyInToken++
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedGameHost(
    uid: String,
    flyInToken: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: GameViewModel = viewModel(
        key = uid,
        factory = GameViewModel.Factory(context, uid)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    LaunchedEffect(flyInToken) {
        if (flyInToken > 0) {
            viewModel.triggerLaunchFlyIn()
        }
    }

    // Auto-dismiss transient status banner after 2.4 seconds
    LaunchedEffect(state.statusBanner) {
        if (state.statusBanner != null) {
            delay(2400L)
            viewModel.dismissBanner()
        }
    }

    val glSurfaceView = remember(uid) {
        GameGLSurfaceView(context, viewModel.renderer)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceView.onResume()
                    viewModel.onAppResumed()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceView.onPause()
                    viewModel.onAppPaused()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
            viewModel.onAppPaused()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Real-time hardware-accelerated OpenGL ES 3.0 3D City & Sports Car Viewport
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize()
        )

        // Active Jetpack Compose Automotive UI Overlay
        when (state.screen) {
            GameScreen.MAIN_MENU -> {
                MainMenuOverlay(
                    state = state,
                    onNavigate = viewModel::navigateTo
                )
            }
            GameScreen.PLAY_SELECTOR -> {
                PlayModeOverlay(
                    activeCarName = state.activeCarBuild.spec.name,
                    onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                    onLaunchMode = { mode -> viewModel.launchMode(mode) }
                )
            }
            GameScreen.GARAGE -> {
                GarageOverlay(
                    state = state,
                    onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                    onOpenProfile = { viewModel.navigateTo(GameScreen.PROFILE) },
                    onPreviewCar = viewModel::previewCarInGarage,
                    onEquipCar = viewModel::equipCar,
                    onUnlockCar = viewModel::unlockCar,
                    onUpdateVisuals = viewModel::updateVisuals,
                    onUpgradeStat = viewModel::upgradeStat
                )
            }
            GameScreen.CAREER -> {
                CareerOverlay(
                    state = state,
                    onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                    onLaunchEvent = { event -> viewModel.launchMode(event.mode, event) }
                )
            }
            GameScreen.PROFILE -> {
                ProfileOverlay(
                    state = state,
                    onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                    onInspectCarInGarage = viewModel::openCarInGarage
                )
            }
            GameScreen.SETTINGS -> {
                SettingsOverlay(
                    state = state,
                    onBack = { viewModel.navigateTo(GameScreen.MAIN_MENU) },
                    onSetQuality = viewModel::setGraphicsQuality,
                    onSetCamera = viewModel::setCameraMode,
                    onSetTimeOfDay = viewModel::setTimeOfDayPreset,
                    onSetWeather = viewModel::setWeatherPreset,
                    onSetSensitivity = viewModel::setSteeringSensitivity,
                    onSetAudio = viewModel::setEngineAudioEnabled,
                    onSetMusic = viewModel::setMusicEnabled,
                    onSetSfx = viewModel::setSfxEnabled,
                    onSetShowFps = viewModel::setShowFpsCounter,
                    onSignOut = {
                        viewModel.audioSynthesizer.stop()
                        performSignOut(credentialManager, scope)
                    }
                )
            }
            GameScreen.RACING -> {
                GameplayHudOverlay(
                    state = state,
                    telemetry = telemetry,
                    controlInput = viewModel.controlInput,
                    onTogglePause = viewModel::togglePause,
                    onCycleCamera = viewModel::cycleCameraMode,
                    onRecoverCar = viewModel::recoverCar,
                    onRestart = viewModel::restartCurrentSession,
                    onExitToMenu = { viewModel.navigateTo(GameScreen.MAIN_MENU) }
                )
            }
        }

        // Transient Toast Banner for Garage Unlocks, Upgrades, or Cloud Status
        AnimatedVisibility(
            visible = state.statusBanner != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 18.dp)
        ) {
            Surface(
                color = CarbonDark.copy(alpha = 0.94f),
                border = BorderStroke(1.5.dp, NitroCyan),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = state.statusBanner.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }

        // Non-blocking Cloud Sync Warning Banner if Firestore reports an error
        AnimatedVisibility(
            visible = state.cloudSyncError != null && state.screen != GameScreen.RACING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 8.dp)
        ) {
            Surface(
                color = RacingCrimson.copy(alpha = 0.90f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = state.cloudSyncError.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
