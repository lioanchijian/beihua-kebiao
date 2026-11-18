package com.buct.course.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.buct.course.ui.system.glass.DampedDragAnimation
import com.buct.course.ui.system.glass.resolvePhysicalLens
import com.buct.course.ui.theme.MotionSpring
import kotlin.math.abs

/** 与底部导航同源的液态玻璃文字分段控件。 */
@Composable
fun GlassSemesterToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalControlBackdrop.current
) {
    if (options.isEmpty()) return
    val count = options.size
    val shape = Capsule()
    val accessibility = rememberGlassAccessibilityMode()
    val isLightTheme = !rememberGlassDarkTheme()
    val hasRealLens = isRuntimeShaderTrulySupported()
    val trackMaterial = GlassMaterials.resolve(GlassMaterialRole.Navigation, accessibility, 0f)
    val indicatorMaterial = GlassMaterials.resolve(GlassMaterialRole.Interactive, accessibility, 0f)
    val containerColor = if (isLightTheme) Color.White.copy(alpha = 0.28f)
    else LocalWallpaperAppearanceColors.current.surface
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val animationScope = rememberCoroutineScope()
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestSelected by rememberUpdatedState(selectedIndex)

    BoxWithConstraints(modifier = modifier.height(36.dp).zIndex(10f)) {
        val density = LocalDensity.current
        val trackPadding = 3.dp
        val trackPaddingPx = with(density) { trackPadding.toPx() }
        val segmentWidth = (maxWidth - trackPadding * 2) / count
        val segmentWidthPx = with(density) { segmentWidth.toPx() }
        val dragAnimation = remember(animationScope, count) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.05f,
                settleAnimationSpec = MotionSpring.navSettle(),
                releaseScaleAnimationSpec = MotionSpring.navRelease(),
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }
        LaunchedEffect(selectedIndex) {
            if (abs(dragAnimation.targetValue - selectedIndex) > 0.001f) {
                dragAnimation.animateToValue(selectedIndex.toFloat())
            }
        }

        val dragState = rememberDraggableState { delta ->
            dragAnimation.updateValue(
                (dragAnimation.targetValue + delta / segmentWidthPx).fastCoerceIn(0f, (count - 1).toFloat())
            )
        }
        fun segmentGesture(index: Int): Modifier = Modifier.pointerInput(index, segmentWidthPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = dragAnimation.value
                var totalDrag = 0f
                var dragging = false
                dragAnimation.press(down.uptimeMillis)
                down.consume()
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        val dx = change.positionChange().x
                        if (dx != 0f) {
                            totalDrag += dx
                            if (!dragging && abs(totalDrag) > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                change.consume()
                                dragAnimation.updateValue(
                                    (start + totalDrag / segmentWidthPx).fastCoerceIn(0f, (count - 1).toFloat()),
                                    change.uptimeMillis
                                )
                            }
                        }
                    }
                } finally {
                    val target = if (dragging) {
                        dragAnimation.value.fastRoundToInt().coerceIn(0, count - 1)
                    } else index
                    dragAnimation.animateToValue(target.toFloat())
                    if (target != latestSelected) latestOnSelect(target)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(segmentWidthPx, count) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = dragAnimation.value
                        var totalDrag = 0f
                        var dragging = false
                        dragAnimation.press(down.uptimeMillis)
                        down.consume()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) { change.consume(); break }
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    totalDrag += dx
                                    if (!dragging && abs(totalDrag) > viewConfiguration.touchSlop) dragging = true
                                    if (dragging) {
                                        change.consume()
                                        dragAnimation.updateValue((start + totalDrag / segmentWidthPx).fastCoerceIn(0f, (count - 1).toFloat()), change.uptimeMillis)
                                    }
                                }
                            }
                        } finally {
                            val target = if (dragging) dragAnimation.value.fastRoundToInt().coerceIn(0, count - 1) else (down.position.x / segmentWidthPx).toInt().coerceIn(0, count - 1)
                            dragAnimation.animateToValue(target.toFloat())
                            if (target != latestSelected) latestOnSelect(target)
                        }
                    }
                }
                .draggable(
                    enabled = false,
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragAnimation.press() },
                    onDragStopped = {
                        val target = dragAnimation.value.fastRoundToInt().coerceIn(0, count - 1)
                        dragAnimation.animateToValue(target.toFloat())
                        if (target != latestSelected) latestOnSelect(target)
                    }
                )
                .then(
                    if (backdrop != null && isBackdropSupported()) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                if (hasRealLens) {
                                    val params = resolvePhysicalLens(
                                        scope = this,
                                        material = trackMaterial,
                                        minCornerRadiusPx = size.minDimension / 2f,
                                        minDimensionPx = size.minDimension,
                                        interactionProgress = 0f,
                                        enableBlur = true,
                                        allowChromaticAberration = false,
                                        pressScalesRefraction = false
                                    )
                                    if (params.blurPx > 0f) blur(params.blurPx) else blur(8.dp.toPx())
                                    if (params.useLens) {
                                        lens(
                                            refractionHeight = params.refractionHeightPx,
                                            refractionAmount = params.refractionAmountPx,
                                            chromaticAberration = false
                                        )
                                    }
                                } else {
                                    blur(8.dp.toPx())
                                }
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.16f) },
                            shadow = { Shadow(alpha = 0.08f) },
                            layerBlock = {
                                val maxGain = 16.dp.toPx()
                                val pressScale = 1f + (maxGain / size.width) * dragAnimation.pressProgress
                                val boost = ((dragAnimation.scaleX + dragAnimation.scaleY) / 2f - 1f).fastCoerceIn(0f, 0.4f)
                                val scale = pressScale * (1f + boost * 0.18f)
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                    } else {
                        Modifier.background(containerColor, shape)
                    }
                )
        ) {
            Box(
                modifier = Modifier
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .offset(x = trackPadding + segmentWidth * dragAnimation.value)
                    .padding(vertical = trackPadding)
                    .graphicsLayer {
                        val t = semesterIndicatorScale(dragAnimation)
                        scaleX = t.first
                        scaleY = t.second
                    }
                    .then(
                        if (backdrop != null && isBackdropSupported()) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { shape },
                                effects = {
                                    vibrancy()
                                    if (hasRealLens) {
                                        val params = resolvePhysicalLens(
                                            scope = this,
                                            material = indicatorMaterial,
                                            minCornerRadiusPx = size.minDimension / 2f,
                                            minDimensionPx = size.minDimension,
                                            interactionProgress = dragAnimation.pressProgress,
                                            enableBlur = false,
                                            allowChromaticAberration = true,
                                            pressScalesRefraction = true
                                        )
                                        if (params.useLens) {
                                            lens(
                                                refractionHeight = params.refractionHeightPx,
                                                refractionAmount = params.refractionAmountPx,
                                                chromaticAberration = params.chromaticAberration
                                            )
                                        }
                                    } else {
                                        blur(8.dp.toPx())
                                    }
                                },
                                highlight = { Highlight.Default.copy(alpha = 0.18f) },
                                shadow = { Shadow(alpha = 0.12f) },
                                innerShadow = { InnerShadow(radius = 4.dp * dragAnimation.pressProgress, alpha = 0.5f * dragAnimation.pressProgress) },
                                layerBlock = {
                                    val t = semesterIndicatorScale(dragAnimation)
                                    scaleX = t.first
                                    scaleY = t.second
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.18f + dragAnimation.pressProgress * 0.06f))
                                }
                            )
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.20f), shape)
                        }
                    )
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = trackPadding).zIndex(20f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, label ->
                    val selectionWeight = (1f - abs(dragAnimation.value - index)).fastCoerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectionWeight > 0.55f) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.lerp(unselectedColor, selectedColor, selectionWeight),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

private fun semesterIndicatorScale(anim: DampedDragAnimation): Pair<Float, Float> {
    var sx = anim.scaleX
    var sy = anim.scaleY
    val velocity = anim.velocity / 6f
    sx /= 1f - (velocity * 0.75f).fastCoerceIn(-0.22f, 0.22f)
    sy *= 1f - (velocity * 0.25f).fastCoerceIn(-0.22f, 0.22f)
    return sx to sy
}