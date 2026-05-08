package com.llm_smartphone_v2.overlay.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.llm_smartphone_v2.overlay.OverlayUiState
import com.llm_smartphone_v2.overlay.RunState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun OverlayRoot(stateFlow: StateFlow<OverlayUiState>) {
    val state by stateFlow.collectAsStateValue()
    Box(modifier = Modifier.fillMaxSize()) {

        // Top ephemeral bubble (user transcript / "fertig" toast)
        AnimatedVisibility(
            visible = state.topMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp, start = 24.dp, end = 24.dp),
        ) {
            EphemeralBubble(
                message = state.topMessage.orEmpty(),
                isUser = state.topMessageIsUser,
            )
        }

        // Bottom pill — kept as the textual progress indicator inside
        // the glowing border. The border carries the "AI is active"
        // signal; the pill carries the *what is happening right now*
        // label.
        AnimatedVisibility(
            visible = state.state != RunState.Hidden,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
        ) {
            BottomPill(state)
        }
    }
}

@Composable
private fun EphemeralBubble(message: String, isUser: Boolean) {
    val bubbleColor = if (isUser)
        MaterialTheme.colorScheme.surfaceContainerHigh
    else
        MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isUser)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        color = bubbleColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = message,
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun BottomPill(state: OverlayUiState) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        // Translucent pill so the screen behind it stays partially visible
        // — feels less obstructive while the agent is working.
        color = pillColor(state.state).copy(alpha = 0.78f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PillLeading(state.state)
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = pillLabel(state),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + slideInVertically { it / 3 })
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "pill-label",
            ) { label ->
                Text(
                    text = label,
                    color = pillTextColor(state.state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PillLeading(state: RunState) {
    when (state) {
        RunState.Listening -> ListeningWaveform()
        RunState.Thinking -> ThinkingOrb()
        RunState.Acting -> CircularProgressIndicator(
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        RunState.Done -> Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
        RunState.Error -> Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(22.dp),
        )
        RunState.Hidden -> Box(Modifier.size(20.dp))
    }
}

@Composable
private fun ListeningWaveform() {
    val transition = rememberInfiniteTransition(label = "wave")
    val s1 by transition.animateFloat(
        0.5f, 1.2f,
        animationSpec = infiniteRepeatable(tween(420), repeatMode = RepeatMode.Reverse),
        label = "s1",
    )
    val s2 by transition.animateFloat(
        0.5f, 1.2f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 140), repeatMode = RepeatMode.Reverse),
        label = "s2",
    )
    val s3 by transition.animateFloat(
        0.5f, 1.2f,
        animationSpec = infiniteRepeatable(tween(420, delayMillis = 280), repeatMode = RepeatMode.Reverse),
        label = "s3",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(scale = s1)
        Spacer(Modifier.width(4.dp))
        Dot(scale = s2)
        Spacer(Modifier.width(4.dp))
        Dot(scale = s3)
    }
}

@Composable
private fun Dot(scale: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimaryContainer),
    )
}

@Composable
private fun ThinkingOrb() {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        0.85f, 1.15f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier = Modifier
            .size(20.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimaryContainer),
    )
}

@Composable
private fun pillColor(state: RunState): Color = when (state) {
    RunState.Listening, RunState.Thinking, RunState.Acting -> MaterialTheme.colorScheme.primaryContainer
    RunState.Done -> MaterialTheme.colorScheme.primary
    RunState.Error -> MaterialTheme.colorScheme.error
    RunState.Hidden -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
private fun pillTextColor(state: RunState): Color = when (state) {
    RunState.Listening, RunState.Thinking, RunState.Acting -> MaterialTheme.colorScheme.onPrimaryContainer
    RunState.Done -> MaterialTheme.colorScheme.onPrimary
    RunState.Error -> MaterialTheme.colorScheme.onError
    RunState.Hidden -> MaterialTheme.colorScheme.onSurface
}

private fun pillLabel(state: OverlayUiState): String = when (state.state) {
    RunState.Listening -> "höre…"
    RunState.Thinking -> state.currentStepLabel.ifEmpty { "denke nach" }
    RunState.Acting -> state.currentStepLabel.ifEmpty { "arbeite" }
    RunState.Done -> "fertig"
    RunState.Error -> "Fehler"
    RunState.Hidden -> ""
}
