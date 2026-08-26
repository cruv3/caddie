package com.caddie.app.overlay.ui

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.caddie.app.overlay.OverlayUiState
import com.caddie.app.overlay.RunState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

@Composable
fun OverlayRoot(
    stateFlow: StateFlow<OverlayUiState>,
    onConfirm: () -> Unit = {},
    onDecline: () -> Unit = {},
    onParticipantSwipe: () -> Unit = {},
    onConfirmationPresented: (String) -> Unit = {},
    onTextQuestionAnswer: (questionId: String, answer: String) -> Unit = { _, _ -> },
) {
    val state by stateFlow.collectAsStateValue()
    Box(modifier = Modifier.fillMaxSize()) {

        AnimatedVisibility(
            visible = state.topMessage != null && state.textQuestionId == null,
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

        AnimatedVisibility(
            visible = state.state != RunState.Hidden && state.textQuestionId == null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
        ) {
            BottomPill(state)
        }

        state.textQuestionId?.let { questionId ->
            TextQuestionAnswerCard(
                question = state.topMessage.orEmpty(),
                questionId = questionId,
                onSubmit = onTextQuestionAnswer,
            )
        }

        state.confirmationText?.let { description ->
            ConfirmationCard(
                description = description,
                onConfirm = onConfirm,
                onDecline = onDecline,
                onParticipantSwipe = onParticipantSwipe,
                onPresented = { onConfirmationPresented(description) },
            )
        }
    }
}

@Composable
private fun BoxScope.TextQuestionAnswerCard(
    question: String,
    questionId: String,
    onSubmit: (questionId: String, answer: String) -> Unit,
) {
    var answer by remember(questionId) { mutableStateOf("") }
    val submit = {
        val trimmed = answer.trim()
        if (trimmed.isNotEmpty()) onSubmit(questionId, trimmed)
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Your answer") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = submit,
                enabled = answer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    description: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onParticipantSwipe: () -> Unit,
    onPresented: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .onGloballyPositioned { onPresented() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Aktion prüfen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Stimmt etwas nicht? Sage zuerst „Hey Jarvis“ und danach kurz, was falsch ist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                SwipeToConfirmTrack(
                    onConfirm = onConfirm,
                    onParticipantSwipe = onParticipantSwipe,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Aktion ablehnen")
                }
            }
        }
    }
}

@Composable
private fun SwipeToConfirmTrack(
    onConfirm: () -> Unit,
    onParticipantSwipe: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbSize = 52.dp
    var offsetX by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { thumbSize.toPx() }
        val maxOffset = (trackPx - thumbPx).coerceAtLeast(1f)
        val threshold = maxOffset * 0.75f

        Text(
            text = if (confirmed) "Bestätigt" else "Zum Bestätigen wischen  →",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = thumbSize, end = 8.dp),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(2.dp)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onParticipantSwipe() },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (!confirmed) {
                                offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffset)
                            }
                        },
                        onDragEnd = {
                            if (confirmed) return@detectHorizontalDragGestures
                            if (offsetX >= threshold) {
                                confirmed = true
                                offsetX = maxOffset
                                onConfirm()
                            } else {
                                offsetX = 0f
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (confirmed) Icons.Filled.Check
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
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
        color = pillColor(state.state).copy(alpha = 0.78f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(min = 56.dp)
                .padding(horizontal = 18.dp, vertical = 10.dp),
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
                modifier = Modifier.weight(1f, fill = false),
            ) { label ->
                Text(
                    text = label,
                    color = pillTextColor(state.state),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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
        RunState.Paused -> PauseBars()
        RunState.Done -> Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp),
        )
        RunState.Error -> Icon(
            Icons.Filled.Close,
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
private fun PauseBars() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            if (index > 0) Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onTertiaryContainer),
            )
        }
    }
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
    RunState.Paused -> MaterialTheme.colorScheme.tertiaryContainer
    RunState.Done -> MaterialTheme.colorScheme.primary
    RunState.Error -> MaterialTheme.colorScheme.error
    RunState.Hidden -> MaterialTheme.colorScheme.surfaceContainer
}

@Composable
private fun pillTextColor(state: RunState): Color = when (state) {
    RunState.Listening, RunState.Thinking, RunState.Acting -> MaterialTheme.colorScheme.onPrimaryContainer
    RunState.Paused -> MaterialTheme.colorScheme.onTertiaryContainer
    RunState.Done -> MaterialTheme.colorScheme.onPrimary
    RunState.Error -> MaterialTheme.colorScheme.onError
    RunState.Hidden -> MaterialTheme.colorScheme.onSurface
}

private fun pillLabel(state: OverlayUiState): String = when (state.state) {
    RunState.Listening -> "listening…"
    RunState.Thinking -> state.currentStepLabel.ifEmpty { "thinking" }
    RunState.Acting -> state.currentStepLabel.ifEmpty { "working" }
    RunState.Paused -> state.currentStepLabel.ifEmpty { "paused" }
    RunState.Done -> "done"
    RunState.Error -> "Error"
    RunState.Hidden -> ""
}
