package com.kyuusanq3.mixauto.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyuusanq3.mixauto.data.media.isNotificationListenerEnabled
import com.kyuusanq3.mixauto.ui.theme.CarBodyText
import com.kyuusanq3.mixauto.ui.theme.CarDimensions
import com.kyuusanq3.mixauto.ui.theme.CarHeadlineText
import com.kyuusanq3.mixauto.ui.theme.CarLabelText
import com.kyuusanq3.mixauto.ui.theme.ElectricCyan
import com.kyuusanq3.mixauto.ui.theme.OledBlack

const val CURRENT_ONBOARDING_VERSION = 1

enum class OnboardingStepId {
    LOCATION,
    NOTIFICATION_ACCESS,
    MICROPHONE,
}

data class OnboardingStep(
    val id: OnboardingStepId,
    val sinceVersion: Int,
    val title: String,
    val body: String,
    val icon: ImageVector,
    val isOptional: Boolean,
)

val ALL_ONBOARDING_STEPS = listOf(
    OnboardingStep(
        id = OnboardingStepId.LOCATION,
        sinceVersion = 1,
        title = "Location access",
        body = "Mix Auto uses your location for the map puck, turn-by-turn navigation, and nearby destination search.",
        icon = Icons.Filled.LocationOn,
        isOptional = false,
    ),
    OnboardingStep(
        id = OnboardingStepId.NOTIFICATION_ACCESS,
        sinceVersion = 1,
        title = "Notification access",
        body = "Allow notification access so the dashboard can show now playing info and control YouTube Music, Spotify, and other media apps.",
        icon = Icons.Filled.Notifications,
        isOptional = false,
    ),
    OnboardingStep(
        id = OnboardingStepId.MICROPHONE,
        sinceVersion = 1,
        title = "Microphone access",
        body = "Voice search lets you speak a destination instead of typing while driving.",
        icon = Icons.Filled.Mic,
        isOptional = true,
    ),
)

fun pendingOnboardingSteps(storedVersion: Int): List<OnboardingStep> =
    ALL_ONBOARDING_STEPS.filter { it.sinceVersion > storedVersion }

@Composable
fun OnboardingWizard(
    pendingSteps: List<OnboardingStep>,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pendingSteps.isEmpty()) {
        LaunchedEffect(Unit) {
            onComplete()
        }
        return
    }

    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = pendingSteps[currentStepIndex]
    val isLastStep = currentStepIndex >= pendingSteps.lastIndex

    fun advanceStep() {
        if (currentStepIndex >= pendingSteps.lastIndex) {
            onComplete()
        } else {
            currentStepIndex++
        }
    }

    val advanceStepState = rememberUpdatedState(newValue = ::advanceStep)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { advanceStep() }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { advanceStep() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentStep.id) {
        if (currentStep.id != OnboardingStepId.NOTIFICATION_ACCESS) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                isNotificationListenerEnabled(context)
            ) {
                advanceStepState.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = OledBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CarDimensions.PaneGap * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CarLabelText(
                    text = "Setup ${currentStepIndex + 1} of ${pendingSteps.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = CarDimensions.PaneGap),
                )
                StepDots(
                    stepCount = pendingSteps.size,
                    currentIndex = currentStepIndex,
                )
            }

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "onboardingStep",
            ) { step ->
                StepContent(step = step)
            }

            StepActions(
                step = currentStep,
                isLastStep = isLastStep,
                onGrantLocation = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onOpenNotificationSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                    )
                },
                onGrantMicrophone = {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onSkip = { advanceStep() },
            )
        }
    }
}

@Composable
private fun StepDots(
    stepCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        repeat(stepCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentIndex) 12.dp else 8.dp)
                    .background(
                        color = if (index == currentIndex) {
                            ElectricCyan
                        } else {
                            ElectricCyan.copy(alpha = 0.35f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = step.icon,
            contentDescription = step.title,
            tint = ElectricCyan,
            modifier = Modifier
                .size(CarDimensions.PrimaryTapTarget)
                .padding(bottom = CarDimensions.PaneGap * 2),
        )
        CarHeadlineText(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = CarDimensions.PaneGap),
        )
        CarBodyText(
            text = step.body,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 5,
            modifier = Modifier.padding(horizontal = CarDimensions.PaneGap),
        )
        if (step.isOptional) {
            CarLabelText(
                text = "Optional",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = CarDimensions.PaneGap),
            )
        }
    }
}

@Composable
private fun StepActions(
    step: OnboardingStep,
    isLastStep: Boolean,
    onGrantLocation: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onGrantMicrophone: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val finishLabel = if (isLastStep) "Get Started" else "Continue"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CarDimensions.PaneGap),
    ) {
        when (step.id) {
            OnboardingStepId.LOCATION -> {
                val alreadyGranted = hasLocationPermission(context)
                Button(
                    onClick = if (alreadyGranted) onSkip else onGrantLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarLabelText(
                        text = if (alreadyGranted) finishLabel else "Grant location",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (!alreadyGranted) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CarDimensions.MinTapTarget),
                    ) {
                        CarLabelText(
                            text = if (isLastStep) "Get Started without location" else "Skip for now",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            OnboardingStepId.NOTIFICATION_ACCESS -> {
                val alreadyEnabled = isNotificationListenerEnabled(context)
                Button(
                    onClick = if (alreadyEnabled) onSkip else onOpenNotificationSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarLabelText(
                        text = if (alreadyEnabled) finishLabel else "Open access settings",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (!alreadyEnabled) {
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CarDimensions.MinTapTarget),
                    ) {
                        CarLabelText(
                            text = if (isLastStep) "Get Started without media access" else "Skip for now",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            OnboardingStepId.MICROPHONE -> {
                val alreadyGranted = hasMicrophonePermission(context)
                Button(
                    onClick = if (alreadyGranted) onSkip else onGrantMicrophone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarLabelText(
                        text = if (alreadyGranted) finishLabel else "Grant microphone",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CarDimensions.MinTapTarget),
                ) {
                    CarLabelText(
                        text = if (isLastStep) "Get Started without voice search" else "Skip",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

private fun hasMicrophonePermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
