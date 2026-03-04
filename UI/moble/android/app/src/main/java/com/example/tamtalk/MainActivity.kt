package com.example.tamtalk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.compose.foundation.layout.Row
import com.example.tamtalk.R
import com.example.tamtalk.audio.AudioUdpSender
import com.example.tamtalk.audio.AudioStreamingService
import com.example.tamtalk.ui.theme.TamtalkTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    companion object {
        val hardwareVolumeDelta = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TamtalkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceSenderScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                hardwareVolumeDelta.tryEmit(+1)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                hardwareVolumeDelta.tryEmit(-1)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }
}

private enum class AppPage { Main, Settings, About }

@Composable
fun VoiceSenderScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tamtalk_prefs", Context.MODE_PRIVATE) }
    val softwareVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

    var host by remember { mutableStateOf(prefs.getString("host", "192.168.1.50") ?: "192.168.1.50") }
    var portText by remember { mutableStateOf(prefs.getString("port", "9240") ?: "9240") }
    var clientId by remember { mutableStateOf(prefs.getString("clientId", "android-client") ?: "android-client") }
    var status by remember { mutableStateOf("Idle") }
    var isStreaming by remember { mutableStateOf(false) }
    var keepActiveWhenClosed by remember { mutableStateOf(prefs.getBoolean("keepActiveWhenClosed", false)) }
    var alwaysActive by remember { mutableStateOf(!prefs.getBoolean("pushToTalkEnabled", true)) }
    var micVolume by remember { mutableStateOf(prefs.getFloat("micVolume", 1.0f).coerceIn(0.0f, 2.0f)) }
    var isPushTalking by remember { mutableStateOf(false) }
    var pttStartedStream by remember { mutableStateOf(false) }
    var sender by remember { mutableStateOf<AudioUdpSender?>(null) }
    var currentPage by remember { mutableStateOf(AppPage.Main) }
    val pttInteractionSource = remember { MutableInteractionSource() }
    val pttPressed by pttInteractionSource.collectIsPressedAsState()

    fun startBackgroundService(port: Int) {
        val serviceIntent = Intent(context, AudioStreamingService::class.java).apply {
            action = AudioStreamingService.actionStart
            putExtra(AudioStreamingService.extraHost, host)
            putExtra(AudioStreamingService.extraPort, port)
            putExtra(AudioStreamingService.extraClientId, clientId)
            putExtra(AudioStreamingService.extraKeepAliveWhileScreenOff, keepActiveWhenClosed)
            putExtra(AudioStreamingService.extraVolumeGain, micVolume)
            putExtra(AudioStreamingService.extraAlwaysActive, alwaysActive)
        }
        startForegroundService(context, serviceIntent)
        isStreaming = true
        status = if (alwaysActive) {
            "Streaming in background to $host:$port"
        } else {
            "Connected in background (muted)"
        }
    }

    fun stopBackgroundService() {
        val stopIntent = Intent(context, AudioStreamingService::class.java).apply {
            action = AudioStreamingService.actionStop
        }
        context.startService(stopIntent)
        isStreaming = false
        status = "Stopped"
    }

    fun startLocalStream(port: Int) {
        val active = AudioUdpSender(
            hostIp = host,
            port = port,
            clientId = clientId,
            inputGain = micVolume,
            initialTransmitEnabled = alwaysActive
        )
        sender = active
        active.start { error -> status = error }
        isStreaming = true
        status = if (alwaysActive) "Streaming to $host:$port" else "Connected (muted, hold Push-to-Talk)"
    }

    fun stopLocalStream() {
        sender?.stop()
        sender = null
        isStreaming = false
        pttStartedStream = false
    }

    fun shutdownApp() {
        stopLocalStream()

        val stopIntent = Intent(context, AudioStreamingService::class.java).apply {
            action = AudioStreamingService.actionStop
        }
        context.startService(stopIntent)

        isPushTalking = false
        isStreaming = false
        status = "Shutting down..."

        context.findActivity()?.apply {
            moveTaskToBack(true)
            finishAndRemoveTask()
        }
    }

    fun pushLiveVolumeUpdate() {
        sender?.setInputGain(micVolume)

        if (AudioStreamingService.isRunning) {
            val volumeIntent = Intent(context, AudioStreamingService::class.java).apply {
                action = AudioStreamingService.actionUpdateVolume
                putExtra(AudioStreamingService.extraVolumeGain, micVolume)
            }
            context.startService(volumeIntent)
        }
    }

    fun pushLiveTransmitModeUpdate() {
        sender?.setTransmitEnabled(alwaysActive)

        if (AudioStreamingService.isRunning) {
            val modeIntent = Intent(context, AudioStreamingService::class.java).apply {
                action = AudioStreamingService.actionUpdateTransmitMode
                putExtra(AudioStreamingService.extraAlwaysActive, alwaysActive)
            }
            context.startService(modeIntent)
        }
    }

    fun setBackgroundTransmitEnabled(enabled: Boolean) {
        val modeIntent = Intent(context, AudioStreamingService::class.java).apply {
            action = AudioStreamingService.actionUpdateTransmitMode
            putExtra(AudioStreamingService.extraAlwaysActive, enabled)
        }
        context.startService(modeIntent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            status = "Microphone permission denied"
            return@rememberLauncherForActivityResult
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            status = "Invalid port"
            return@rememberLauncherForActivityResult
        }

        if (keepActiveWhenClosed) {
            startBackgroundService(port)
        } else {
            startLocalStream(port)
        }
    }

    fun beginPushToTalk() {
        if (alwaysActive) {
            status = "Push-to-talk is disabled while Always Active is ON"
            return
        }

        val backgroundModeActive = keepActiveWhenClosed && isStreaming

        if (backgroundModeActive || AudioStreamingService.isRunning) {
            setBackgroundTransmitEnabled(true)
            isPushTalking = true
            status = "Push-to-talk active"
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            status = "Invalid port"
            return
        }

        if (!isPushTalking) {
            val wasStreaming = isStreaming
            if (!wasStreaming) {
                startLocalStream(port)
                pttStartedStream = true
            } else {
                pttStartedStream = false
            }

            sender?.setTransmitEnabled(true)
            isPushTalking = true
            status = "Push-to-talk active"
        }
    }

    fun endPushToTalk() {
        if (!isPushTalking)
            return

        val backgroundModeActive = keepActiveWhenClosed && isStreaming

        if (backgroundModeActive || AudioStreamingService.isRunning) {
            setBackgroundTransmitEnabled(false)
            isPushTalking = false
            status = "Push-to-talk released"
            return
        }

        if (pttStartedStream) {
            stopLocalStream()
        } else {
            sender?.setTransmitEnabled(false)
        }

        isPushTalking = false
        status = "Push-to-talk released"
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!keepActiveWhenClosed) {
                sender?.stop()
            }
        }
    }

    LaunchedEffect(host, portText, clientId, keepActiveWhenClosed, alwaysActive, micVolume) {
        prefs.edit()
            .putString("host", host)
            .putString("port", portText)
            .putString("clientId", clientId)
            .putBoolean("keepActiveWhenClosed", keepActiveWhenClosed)
            .putBoolean("pushToTalkEnabled", !alwaysActive)
            .putFloat("micVolume", micVolume)
            .apply()
    }

    LaunchedEffect(micVolume) {
        pushLiveVolumeUpdate()
    }

    LaunchedEffect(pttPressed) {
        if (pttPressed) {
            beginPushToTalk()
        } else {
            endPushToTalk()
        }
    }

    LaunchedEffect(Unit) {
        MainActivity.hardwareVolumeDelta.collectLatest { delta ->
            val step = 0.05f
            micVolume = (micVolume + (delta * step)).coerceIn(0.0f, 2.0f)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "TamTalk")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { currentPage = AppPage.Main }, modifier = Modifier.weight(1f)) { Text("Main") }
            Button(onClick = { currentPage = AppPage.Settings }, modifier = Modifier.weight(1f)) { Text("Settings") }
            Button(onClick = { currentPage = AppPage.About }, modifier = Modifier.weight(1f)) { Text("About") }
        }

        if (currentPage == AppPage.Main) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        alwaysActive = !alwaysActive
                        pushLiveTransmitModeUpdate()
                        if (alwaysActive && isPushTalking) {
                            stopLocalStream()
                            isPushTalking = false
                            status = "Always Active enabled"
                        } else if (alwaysActive && isStreaming) {
                            status = if (AudioStreamingService.isRunning) {
                                "Streaming in background to $host:${portText.toIntOrNull() ?: 0}"
                            } else {
                                "Streaming to $host:${portText.toIntOrNull() ?: 0}"
                            }
                        } else if (!alwaysActive && isStreaming) {
                            status = if (AudioStreamingService.isRunning) {
                                "Connected in background (muted)"
                            } else {
                                "Connected (muted, hold Push-to-Talk)"
                            }
                        }
                    },
                    enabled = isStreaming,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (alwaysActive) "Always Active: ON" else "Always Active: OFF")
                }

                Button(
                    onClick = {},
                    enabled = isStreaming && !alwaysActive,
                    interactionSource = pttInteractionSource,
                    shape = CircleShape,
                    modifier = Modifier.size(84.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_btn_speak_now),
                        contentDescription = "Hold to Talk",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(text = "Voice Volume: ${String.format("%.2f", micVolume)}x")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { micVolume = (micVolume - 0.1f).coerceAtLeast(0.0f) }) { Text("Vol -") }

                Slider(
                    value = micVolume,
                    onValueChange = { micVolume = it.coerceIn(0.0f, 2.0f) },
                    valueRange = 0.0f..2.0f,
                    modifier = Modifier.weight(1f)
                )

                Button(onClick = { micVolume = (micVolume + 0.1f).coerceAtMost(2.0f) }) { Text("Vol +") }
            }

            Button(
                onClick = {
                    if (isStreaming) {
                        if (keepActiveWhenClosed || AudioStreamingService.isRunning) {
                            stopBackgroundService()
                        } else {
                            stopLocalStream()
                            status = "Stopped"
                        }
                        return@Button
                    }

                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        val port = portText.toIntOrNull()
                        if (port == null || port !in 1..65535) {
                            status = "Invalid port"
                            return@Button
                        }

                        if (keepActiveWhenClosed) {
                            startBackgroundService(port)
                        } else {
                            startLocalStream(port)
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isStreaming) "Disconnect" else "Connect")
            }
        }

        if (currentPage == AppPage.Settings) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = keepActiveWhenClosed,
                    onCheckedChange = if (isStreaming) null else { { keepActiveWhenClosed = it } }
                )
                Text(text = "Keep app running in background")
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host IP") },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Port") },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Name") },
                enabled = !isStreaming,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { shutdownApp() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Shutdown")
            }
        }

        if (currentPage == AppPage.About) {
            Text(text = "TamTalk")
            Text(text = "by Nicko Balbastro")
            Text(text = "Software Version: $softwareVersion")
        }

        Text(text = "Status: $status")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TamtalkTheme {
        VoiceSenderScreen()
    }
}