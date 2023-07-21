/*
 * Copyright 2023 The Android Open Source Project
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.fail

@OptIn(ExperimentalTestApi::class)
class PopupTest {

    @Test
    fun passCompositionLocalsToPopup() = runSkikoComposeUiTest {
        val compositionLocal = staticCompositionLocalOf<Int> {
            error("not set")
        }

        var actualLocalValue = 0

        setContent {
            CompositionLocalProvider(compositionLocal provides 3) {
                Popup {
                    actualLocalValue = compositionLocal.current
                }
            }
        }

        assertThat(actualLocalValue).isEqualTo(3)
    }

    // https://github.com/JetBrains/compose-multiplatform/issues/3142
    @Test
    fun passLayoutDirectionToPopup() = runSkikoComposeUiTest {
        lateinit var localLayoutDirection: LayoutDirection

        var layoutDirection by mutableStateOf(LayoutDirection.Rtl)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Popup {
                    localLayoutDirection = LocalLayoutDirection.current
                }
            }
        }

        assertThat(localLayoutDirection).isEqualTo(LayoutDirection.Rtl)

        // Test that changing the local propagates it into the popup
        layoutDirection = LayoutDirection.Ltr
        waitForIdle()
        assertThat(localLayoutDirection).isEqualTo(LayoutDirection.Ltr)
    }

    @Test
    fun onDisposeInsidePopup() = runSkikoComposeUiTest {
        var isPopupShowing by mutableStateOf(true)
        var isDisposed = false

        setContent {
            if (isPopupShowing) {
                Popup {
                    DisposableEffect(Unit) {
                        onDispose {
                            isDisposed = true
                        }
                    }
                }
            }
        }

        isPopupShowing = false
        waitForIdle()

        assertThat(isDisposed).isEqualTo(true)
    }

    @Test
    fun useDensityInsidePopup() = runSkikoComposeUiTest {
        var density by mutableStateOf(Density(2f, 1f))
        var densityInsidePopup = 0f

        setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                Popup {
                    densityInsidePopup = LocalDensity.current.density
                }
            }
        }

        assertThat(densityInsidePopup).isEqualTo(2f)

        density = Density(3f, 1f)
        waitForIdle()
        assertThat(densityInsidePopup).isEqualTo(3f)
    }

    @Test
    fun callDismissIfClickedOutsideOfFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        background.events.assertReceivedNoEvents()
        assertThat(onDismissRequestCallCount).isEqualTo(1)
    }

    @Test
    fun callDismissIfClickedOutsideOfNonFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        background.events.assertReceivedLast(PointerEventType.Press, Offset(10f, 10f))
        assertThat(onDismissRequestCallCount).isEqualTo(1)
    }

    @Test
    fun callDismissIfClickedOutsideOfMultipleNonFocusablePopups() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup1 = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )
        val popup2 = PopupState(
            IntRect(30, 30, 70, 70),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup1.Content()
            popup2.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        background.events.assertReceivedLast(PointerEventType.Press, Offset(10f, 10f))
        assertThat(onDismissRequestCallCount).isEqualTo(2)
    }

    @Test
    fun callDismissForNonFocusablePopupsAbove() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup1 = PopupState(
            IntRect(10, 10, 50, 50),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { fail() }
        )
        val popup2 = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { fail() }
        )
        val popup3 = PopupState(
            IntRect(30, 30, 70, 70),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )
        val popup4 = PopupState(
            IntRect(40, 40, 80, 80),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup1.Content()
            popup2.Content()
            popup3.Content()
            popup4.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(55f, 25f))
        background.events.assertReceivedNoEvents()
        assertThat(onDismissRequestCallCount).isEqualTo(2)
    }

    @Test
    fun callDismissForAboveFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup1 = PopupState(
            IntRect(10, 10, 50, 50),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { fail() }
        )
        val popup2 = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { fail() }
        )
        val popup3 = PopupState(
            IntRect(30, 30, 70, 70),
            focusable = true,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )
        val popup4 = PopupState(
            IntRect(40, 40, 80, 80),
            focusable = false,
            dismissOnClickOutside = true,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup1.Content()
            popup2.Content()
            popup3.Content()
            popup4.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(5f, 5f))
        background.events.assertReceivedNoEvents()
        assertThat(onDismissRequestCallCount).isEqualTo(2)
    }

    @Test
    fun passEventIfClickedOutsideOfNonFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        var onDismissRequestCallCount = 0

        val background = FillBox()
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = false,
            onDismissRequest = { onDismissRequestCallCount++ }
        )

        setContent {
            background.Content()
            popup.Content()
        }

        assertThat(onDismissRequestCallCount).isEqualTo(0)
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        background.events.assertReceivedLast(PointerEventType.Press, Offset(10f, 10f))
        assertThat(onDismissRequestCallCount).isEqualTo(0)
    }

    @Test
    fun doNotPassEventIfClickedOutsideOfFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        val background = FillBox()
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = true
        )

        setContent {
            background.Content()
            popup.Content()
        }

        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f))
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f))
        background.events.assertReceivedNoEvents()
    }

    @Test
    fun canScrollOutsideOfNonFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        val background = FillBox()
        val popup = PopupState(IntRect(20, 20, 60, 60), focusable = false)

        setContent {
            background.Content()
            popup.Content()
        }

        scene.sendPointerEvent(PointerEventType.Scroll, Offset(10f, 10f))
        background.events.assertReceivedLast(PointerEventType.Scroll, Offset(10f, 10f))
    }

    @Test
    fun cannotScrollOutsideOfFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        val background = FillBox()
        val popup = PopupState(IntRect(20, 20, 60, 60), focusable = true)

        setContent {
            background.Content()
            popup.Content()
        }

        scene.sendPointerEvent(PointerEventType.Scroll, Offset(10f, 10f))
        background.events.assertReceivedNoEvents()
    }

    @Test
    fun openFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        val openPopup = mutableStateOf(false)
        val background = FillBox {
            openPopup.value = true
        }
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = true,
            onDismissRequest = {
                openPopup.value = false
            }
        )

        setContent {
            background.Content()
            if (openPopup.value) {
                popup.Content()
            }
        }

        // Click (Press-Release cycle) opens popup and sends all events to "background"
        val buttons = PointerButtons(
            isPrimaryPressed = true
        )
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), buttons = buttons, button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(10f, 10f), button = PointerButton.Primary)

        background.events.assertReceived(PointerEventType.Press, Offset(10f, 10f))
        background.events.assertReceivedLast(PointerEventType.Release, Offset(10f, 10f))
        onNodeWithTag(popup.tag).assertIsDisplayed()
    }

    @Test
    fun closeFocusablePopup() = runSkikoComposeUiTest(
        size = Size(100f, 100f)
    ) {
        val openPopup = mutableStateOf(false)
        val background = FillBox()
        val popup = PopupState(
            IntRect(20, 20, 60, 60),
            focusable = true,
            onDismissRequest = {
                openPopup.value = false
            }
        )

        setContent {
            background.Content()
            if (openPopup.value) {
                popup.Content()
            }
        }

        // Moving without popup generates Enter because it's in bounds
        scene.sendPointerEvent(PointerEventType.Move, Offset(15f, 15f))
        background.events.assertReceivedLast(PointerEventType.Enter, Offset(15f, 15f))

        // Open popup
        openPopup.value = true
        onNodeWithTag(popup.tag).assertIsDisplayed()
        background.events.assertReceivedLast(PointerEventType.Exit, Offset(15f, 15f))

        // Click (Press-Move-Release cycle) outside closes popup and sends only Enter event to background
        val buttons = PointerButtons(
            isPrimaryPressed = true
        )
        scene.sendPointerEvent(PointerEventType.Press, Offset(10f, 10f), buttons = buttons, button = PointerButton.Primary)
        onNodeWithTag(popup.tag).assertDoesNotExist() // Wait that it's really closed before next events

        scene.sendPointerEvent(PointerEventType.Move, Offset(11f, 11f), buttons = buttons)
        scene.sendPointerEvent(PointerEventType.Release, Offset(11f, 11f), button = PointerButton.Primary)
        background.events.assertReceivedNoEvents()
    }
}
