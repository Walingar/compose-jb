/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.gestures

import androidx.compose.ui.Modifier
import androidx.compose.ui.gesture.Direction
import androidx.compose.ui.gesture.ScrollCallback
import androidx.compose.ui.gesture.scrollGestureFilter
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation

internal actual fun Modifier.touchScrollable(
    scrollCallback: ScrollCallback,
    orientation: Orientation,
    canScroll: ((Direction) -> Boolean)?,
    startScrollImmediately: Boolean
): Modifier = scrollGestureFilter(
    scrollCallback,
    orientation,
    canScroll,
    startScrollImmediately
)

internal actual fun Modifier.mouseScrollable(
    scrollCallback: ScrollCallback,
    orientation: Orientation
): Modifier = this