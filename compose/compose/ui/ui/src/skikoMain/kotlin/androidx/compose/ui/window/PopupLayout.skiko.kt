/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.window

import androidx.compose.runtime.*
import androidx.compose.ui.LocalComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.SkiaBasedOwner
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round

@Composable
internal fun PopupLayout(
    popupPositionProvider: PopupPositionProvider,
    focusable: Boolean,
    onClickOutside: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable () -> Unit
) {
    val scene = LocalComposeScene.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    var parentBounds by remember { mutableStateOf(IntRect.Zero) }

    // getting parent bounds
    Layout(
        content = {},
        modifier = Modifier.onGloballyPositioned { childCoordinates ->
            val coordinates = childCoordinates.parentCoordinates!!
            parentBounds = IntRect(
                coordinates.localToWindow(Offset.Zero).round(),
                coordinates.size
            )
        },
        measurePolicy = { _, _ ->
            layout(0, 0) {}
        }
    )

    val parentComposition = rememberCompositionContext()
    val (owner, composition) = remember {
        val owner = SkiaBasedOwner(
            scene = scene,
            platform = scene.platform,
            pointerPositionUpdater = scene.pointerPositionUpdater,
            coroutineContext = parentComposition.effectCoroutineContext,
            initDensity = density,
            initLayoutDirection = layoutDirection,
            focusable = focusable,
            onClickOutside = onClickOutside,
            onPreviewKeyEvent = onPreviewKeyEvent,
            onKeyEvent = onKeyEvent
        )
        scene.attach(owner)

        val composition = owner.setContent(parent = parentComposition) {
            Layout(
                content = content,
                modifier = modifier,
                measurePolicy = { measurables, constraints ->
                    val width = constraints.maxWidth
                    val height = constraints.maxHeight

                    layout(constraints.maxWidth, constraints.maxHeight) {
                        measurables.forEach {
                            val placeable = it.measure(constraints)
                            val position = popupPositionProvider.calculatePosition(
                                anchorBounds = parentBounds,
                                windowSize = IntSize(width, height),
                                layoutDirection = this@Layout.layoutDirection,
                                popupContentSize = IntSize(placeable.width, placeable.height)
                            )
                            owner.bounds = IntRect(
                                position,
                                IntSize(placeable.width, placeable.height)
                            )
                            placeable.place(position.x, position.y)
                        }
                    }
                }
            )
        }
        owner to composition
    }
    DisposableEffect(Unit) {
        onDispose {
            scene.detach(owner)
            composition.dispose()
            owner.dispose()
        }
    }
    SideEffect {
        owner.density = density
        owner.layoutDirection = layoutDirection
    }
}
