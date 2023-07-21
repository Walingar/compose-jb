/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.window.window

import androidx.compose.foundation.layout.*
import androidx.compose.material.Slider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.density
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.runApplicationTest
import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseEvent.BUTTON1_DOWN_MASK
import java.awt.event.MouseEvent.BUTTON3_DOWN_MASK
import java.awt.event.MouseEvent.CTRL_DOWN_MASK
import java.awt.event.MouseEvent.MOUSE_DRAGGED
import java.awt.event.MouseEvent.MOUSE_PRESSED
import java.awt.event.MouseEvent.MOUSE_RELEASED
import java.awt.event.MouseEvent.SHIFT_DOWN_MASK
import java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL
import org.jetbrains.skiko.hostOs
import org.junit.Test

sealed class SliderValueEvent {
    object Change: SliderValueEvent()
    object ChangeFinished: SliderValueEvent()
}

@OptIn(ExperimentalComposeUiApi::class)
class WindowInputEventTest {
    @Test
    fun `key strokes call onValueChangeFinished on Slider`()
        = runApplicationTest {
        var window: ComposeWindow? = null


        val sliderValueEvents = mutableListOf<SliderValueEvent>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                val float = remember { mutableStateOf(5f) }

                Column {
                    Slider(
                        float.value,
                        onValueChange = {
                            float.value = it
                            print(sliderValueEvents)
                            sliderValueEvents.add(SliderValueEvent.Change)
                        },
                        onValueChangeFinished = {
                            print(sliderValueEvents)
                            sliderValueEvents.add(SliderValueEvent.ChangeFinished)
                        },
                        valueRange = 0f..10f,
                        steps = 9
                    )

                    Slider(
                        float.value,
                        onValueChange = {},
                        onValueChangeFinished = {},
                        valueRange = 0f..10f,
                        steps = 9
                    )
                }
            }
        }

        awaitIdle()

        val sendKeyPressEvent = { code: Int ->
            window!!.sendKeyEvent(code, id = KeyEvent.KEY_PRESSED)

            Unit
        }

        val sendKeyReleaseEvent = { code: Int ->
            window!!.sendKeyEvent(code, id = KeyEvent.KEY_RELEASED)

            Unit
        }

        val sendKeyStrokeEvent = { code: Int ->
            sendKeyPressEvent(code)
            sendKeyReleaseEvent(code)
        }

        // Focus on slider
        sendKeyStrokeEvent(KeyEvent.VK_TAB)

        sendKeyStrokeEvent(KeyEvent.VK_UP)
        sendKeyStrokeEvent(KeyEvent.VK_DOWN)
        sendKeyStrokeEvent(KeyEvent.VK_LEFT)
        sendKeyStrokeEvent(KeyEvent.VK_RIGHT)
        sendKeyStrokeEvent(KeyEvent.VK_PAGE_UP)
        sendKeyStrokeEvent(KeyEvent.VK_PAGE_DOWN)
        sendKeyStrokeEvent(KeyEvent.VK_HOME)
        sendKeyStrokeEvent(KeyEvent.VK_END)

        awaitIdle()

        val createExpected = { size: Int ->
            List(size) {
                listOf(
                    SliderValueEvent.Change,
                    SliderValueEvent.ChangeFinished
                )
            }.flatten()
        }

        assertThat(sliderValueEvents).isEqualTo(createExpected(8))
    }

    @Test
    fun `catch key handlers`() = runApplicationTest {
        var window: ComposeWindow? = null
        val onKeyEventKeys = mutableSetOf<Key>()
        val onPreviewKeyEventKeys = mutableSetOf<Key>()

        fun clear() {
            onKeyEventKeys.clear()
            onPreviewKeyEventKeys.clear()
        }

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = {
                    onPreviewKeyEventKeys.add(it.key)
                    it.key == Key.Q
                },
                onKeyEvent = {
                    onKeyEventKeys.add(it.key)
                    it.key == Key.W
                }
            ) {
                window = this.window
            }
        }

        awaitIdle()

        window?.sendKeyEvent(KeyEvent.VK_Q)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.Q))
        assertThat(onKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_W)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onKeyEventKeys).isEqualTo(setOf(Key.W))

        clear()
        window?.sendKeyEvent(KeyEvent.VK_E)
        awaitIdle()
        assertThat(onPreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onKeyEventKeys).isEqualTo(setOf(Key.E))
    }

    @Test
    fun `catch key handlers with focused node`() = runApplicationTest {
        var window: ComposeWindow? = null
        val onWindowKeyEventKeys = mutableSetOf<Key>()
        val onWindowPreviewKeyEventKeys = mutableSetOf<Key>()
        val onNodeKeyEventKeys = mutableSetOf<Key>()
        val onNodePreviewKeyEventKeys = mutableSetOf<Key>()

        fun clear() {
            onWindowKeyEventKeys.clear()
            onWindowPreviewKeyEventKeys.clear()
            onNodeKeyEventKeys.clear()
            onNodePreviewKeyEventKeys.clear()
        }

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                onPreviewKeyEvent = {
                    onWindowPreviewKeyEventKeys.add(it.key)
                    it.key == Key.Q
                },
                onKeyEvent = {
                    onWindowKeyEventKeys.add(it.key)
                    it.key == Key.W
                },
            ) {
                window = this.window

                val focusRequester = remember(::FocusRequester)
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Box(
                    Modifier
                        .focusRequester(focusRequester)
                        .focusTarget()
                        .onPreviewKeyEvent {
                            onNodePreviewKeyEventKeys.add(it.key)
                            it.key == Key.E
                        }
                        .onKeyEvent {
                            onNodeKeyEventKeys.add(it.key)
                            it.key == Key.R
                        }
                )
            }
        }

        awaitIdle()

        window?.sendKeyEvent(KeyEvent.VK_Q)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.Q))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onNodeKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_W)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.W))
        assertThat(onWindowKeyEventKeys).isEqualTo(setOf(Key.W))

        clear()
        window?.sendKeyEvent(KeyEvent.VK_E)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.E))
        assertThat(onNodeKeyEventKeys).isEqualTo(emptySet<Key>())
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_R)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.R))
        assertThat(onWindowKeyEventKeys).isEqualTo(emptySet<Key>())

        clear()
        window?.sendKeyEvent(KeyEvent.VK_T)
        awaitIdle()
        assertThat(onWindowPreviewKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onNodePreviewKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onNodeKeyEventKeys).isEqualTo(setOf(Key.T))
        assertThat(onWindowKeyEventKeys).isEqualTo(setOf(Key.T))
    }

    @Test
    fun `catch mouse press + move + release`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val events = mutableListOf<PointerEvent>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier.fillMaxSize().pointerInput(events) {
                        awaitPointerEventScope {
                            while (true) {
                                events += awaitPointerEvent()
                            }
                        }
                    }
                )
            }
        }
        val density by lazy { window.density.density }

        awaitIdle()
        assertThat(events.size).isEqualTo(0)

        window.sendMouseEvent(MouseEvent.MOUSE_ENTERED, x = 100, y = 50)
        awaitIdle()
        assertThat(events.size).isEqualTo(1)
        assertThat(events.last().pressed).isEqualTo(false)
        assertThat(events.last().position).isEqualTo(Offset(100 * density, 50 * density))

        window.sendMouseEvent(MOUSE_PRESSED, 100, 50, modifiers = BUTTON1_DOWN_MASK)
        awaitIdle()
        assertThat(events.size).isEqualTo(2)
        assertThat(events.last().pressed).isEqualTo(true)
        assertThat(events.last().position).isEqualTo(Offset(100 * density, 50 * density))

        window.sendMouseEvent(MOUSE_DRAGGED, 90, 40, modifiers = BUTTON1_DOWN_MASK)
        awaitIdle()
        assertThat(events.size).isEqualTo(3)
        assertThat(events.last().pressed).isEqualTo(true)
        assertThat(events.last().position).isEqualTo(Offset(90 * density, 40 * density))

        window.sendMouseEvent(MOUSE_RELEASED, 80, 30)
        awaitIdle()
        // Synthetic move, because position of the Release isn't the same as in the previous event
        assertThat(events.size).isEqualTo(5)
        assertThat(events[3].type).isEqualTo(PointerEventType.Move)
        assertThat(events[3].pressed).isEqualTo(true)
        assertThat(events[3].position).isEqualTo(Offset(80 * density, 30 * density))
        assertThat(events[4].type).isEqualTo(PointerEventType.Release)
        assertThat(events[4].pressed).isEqualTo(false)
        assertThat(events[4].position).isEqualTo(Offset(80 * density, 30 * density))
    }

    @Test
    fun `catch mouse move`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val onMoves = mutableListOf<Offset>()
        var onEnters = 0
        var onExits = 0

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Move) {
                            onMoves.add(it.changes.first().position)
                        }
                        .onPointerEvent(PointerEventType.Enter) { onEnters++ }
                        .onPointerEvent(PointerEventType.Exit) { onExits++ }
                )
            }
        }
        val density by lazy { window.density.density }

        awaitIdle()
        assertThat(onMoves.size).isEqualTo(0)
        assertThat(onEnters).isEqualTo(0)
        assertThat(onExits).isEqualTo(0)

        window.sendMouseEvent(MouseEvent.MOUSE_ENTERED, x = 100, y = 50)
        awaitIdle()
        assertThat(onMoves.size).isEqualTo(0)
        assertThat(onEnters).isEqualTo(1)
        assertThat(onExits).isEqualTo(0)

        window.sendMouseEvent(MouseEvent.MOUSE_MOVED, x = 90, y = 50)
        awaitIdle()
        assertThat(onMoves.size).isEqualTo(1)
        assertThat(onMoves.last()).isEqualTo(Offset(90 * density, 50 * density))
        assertThat(onEnters).isEqualTo(1)
        assertThat(onExits).isEqualTo(0)

        window.sendMouseEvent(MOUSE_PRESSED, x = 90, y = 50, modifiers = BUTTON1_DOWN_MASK)
        window.sendMouseEvent(MOUSE_DRAGGED, x = 80, y = 50, modifiers = BUTTON1_DOWN_MASK)
        window.sendMouseEvent(MOUSE_RELEASED, x = 80, y = 50)
        awaitIdle()
        assertThat(onMoves.size).isEqualTo(2)
        assertThat(onMoves.last()).isEqualTo(Offset(80 * density, 50 * density))
        assertThat(onEnters).isEqualTo(1)
        assertThat(onExits).isEqualTo(0)

        // TODO(https://github.com/JetBrains/compose-jb/issues/1176) fix catching exit event
//        window.sendMouseEvent(MouseEvent.MOUSE_EXITED, x = 900, y = 500)
//        awaitIdle()
//        assertThat(onMoves.size).isEqualTo(2)
//        assertThat(onEnters).isEqualTo(1)
//        assertThat(onExits).isEqualTo(1)
    }

    @Test
    fun `catch mouse scroll`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val deltas = mutableListOf<Offset>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Scroll) {
                            deltas.add(it.changes.first().scrollDelta)
                        }
                )
            }
        }

        awaitIdle()
        assertThat(deltas.size).isEqualTo(0)

        window.sendMouseWheelEvent(100, 50, WHEEL_UNIT_SCROLL, wheelRotation = 1.0)
        awaitIdle()
        assertThat(deltas.size).isEqualTo(1)
        assertThat(deltas.last()).isEqualTo(Offset(0f, 1f))

        window.sendMouseWheelEvent(100, 50, WHEEL_UNIT_SCROLL, wheelRotation = -1.0)
        awaitIdle()
        assertThat(deltas.size).isEqualTo(2)
        assertThat(deltas.last()).isEqualTo(Offset(0f, -1f))
    }

    @Test
    fun `catch multiple scroll events in one frame`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val deltas = mutableListOf<Offset>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Scroll) {
                            deltas.add(it.changes.first().scrollDelta)
                        }
                )
            }
        }

        awaitIdle()
        assertThat(deltas.size).isEqualTo(0)

        val eventCount = 500

        repeat(eventCount) {
            window.sendMouseWheelEvent(100, 50, WHEEL_UNIT_SCROLL, wheelRotation = 1.0)
        }
        awaitIdle()
        assertThat(deltas.size).isEqualTo(eventCount)
        assertThat(deltas.all { it == Offset(0f, 1f) }).isTrue()
    }

    @Test
    fun `catch only the first scroll event in one frame`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val deltas = mutableListOf<Offset>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier
                        .fillMaxSize()
                        .onFirstPointerEvent(PointerEventType.Scroll) {
                            deltas.add(it.changes.first().scrollDelta)
                        }
                )
            }
        }

        awaitIdle()
        assertThat(deltas.size).isEqualTo(0)

        val eventCount = 500

        repeat(eventCount) {
            window.sendMouseWheelEvent(100, 50, WHEEL_UNIT_SCROLL, wheelRotation = 1.0)
        }
        awaitIdle()
        assertThat(deltas.size).isEqualTo(1)
        assertThat(deltas.first()).isEqualTo(Offset(0f, 1f))
    }

    @Test(timeout = 5000)
    fun `receive buttons and modifiers`() = runApplicationTest {
        lateinit var window: ComposeWindow

        val receivedButtons = mutableListOf<PointerButtons>()
        val receivedKeyboardModifiers = mutableListOf<PointerKeyboardModifiers>()

        launchTestApplication {
            Window(
                onCloseRequest = ::exitApplication,
                state = rememberWindowState(width = 200.dp, height = 100.dp)
            ) {
                window = this.window

                Box(
                    Modifier
                        .fillMaxSize()
                        .onPointerEvent(PointerEventType.Press) {
                            receivedButtons.add(it.buttons)
                            receivedKeyboardModifiers.add(it.keyboardModifiers)
                        }
                        .onPointerEvent(PointerEventType.Scroll) {
                            receivedButtons.add(it.buttons)
                            receivedKeyboardModifiers.add(it.keyboardModifiers)
                        }
                )
            }
        }

        awaitIdle()

        window.sendMouseEvent(
            MOUSE_PRESSED,
            x = 100,
            y = 50,
            modifiers = SHIFT_DOWN_MASK or CTRL_DOWN_MASK or
                BUTTON1_DOWN_MASK or BUTTON3_DOWN_MASK
        )

        awaitIdle()
        assertThat(receivedButtons.size).isEqualTo(1)
        assertThat(receivedButtons.last()).isEqualTo(
            PointerButtons(
                // on macOS ctrl + primary click is treated as secondary click
                isPrimaryPressed = !hostOs.isMacOS,
                isSecondaryPressed = true,
            )
        )
        assertThat(receivedKeyboardModifiers.size).isEqualTo(1)
        assertThat(receivedKeyboardModifiers.last()).isEqualTo(
            PointerKeyboardModifiers(
                isCtrlPressed = true,
                isShiftPressed = true,
                isCapsLockOn = getLockingKeyStateSafe(KeyEvent.VK_CAPS_LOCK),
                isScrollLockOn = getLockingKeyStateSafe(KeyEvent.VK_SCROLL_LOCK),
                isNumLockOn = getLockingKeyStateSafe(KeyEvent.VK_NUM_LOCK),
            )
        )

        window.sendMouseWheelEvent(
            100, 50, WHEEL_UNIT_SCROLL,
            wheelRotation = 1.0,
            modifiers = SHIFT_DOWN_MASK or CTRL_DOWN_MASK or
                BUTTON1_DOWN_MASK or BUTTON3_DOWN_MASK
        )

        awaitIdle()
        assertThat(receivedButtons.size).isEqualTo(2)
        assertThat(receivedButtons.last()).isEqualTo(
            PointerButtons(
                // on macOS ctrl + primary click is treated as secondary click
                isPrimaryPressed = !hostOs.isMacOS,
                isSecondaryPressed = true,
            )
        )
        assertThat(receivedKeyboardModifiers.size).isEqualTo(2)
        assertThat(receivedKeyboardModifiers.last()).isEqualTo(
            PointerKeyboardModifiers(
                isCtrlPressed = true,
                isShiftPressed = true,
                isCapsLockOn = getLockingKeyStateSafe(KeyEvent.VK_CAPS_LOCK),
                isScrollLockOn = getLockingKeyStateSafe(KeyEvent.VK_SCROLL_LOCK),
                isNumLockOn = getLockingKeyStateSafe(KeyEvent.VK_NUM_LOCK),
            )
        )
    }

    @Test
    fun `send scroll into two boxes without intermediate move`() = runApplicationTest {
        var box1ScrollCount = 0
        var box2ScrollCount = 0

        val windowSizeAwt = 100
        val window = ComposeWindow().apply {
            isUndecorated = true
            isResizable = false
            size = Dimension(windowSizeAwt, windowSizeAwt)
        }

        window.isVisible = true

        window.setContent {
            Row {
                Box(
                    Modifier.size(20.dp).onPointerEvent(PointerEventType.Scroll) {
                        box1ScrollCount++
                    }
                )
                Box(
                    Modifier.size(20.dp).onPointerEvent(PointerEventType.Scroll) {
                        box2ScrollCount++
                    }
                )
            }
        }
        awaitIdle()

        window.sendMouseWheelEvent(x = 1, y = 1)
        window.sendMouseWheelEvent(x = 21, y = 1)

        assertThat(box1ScrollCount).isEqualTo(1)
        assertThat(box2ScrollCount).isEqualTo(1)

        window.dispose()
    }

    @Test
    fun `send release into first box without intermediate move`() = runApplicationTest {
        var box1ReleaseCount = 0
        var box2ReleaseCount = 0

        val windowSizeAwt = 100
        val window = ComposeWindow().apply {
            isUndecorated = true
            isResizable = false
            size = Dimension(windowSizeAwt, windowSizeAwt)
        }

        window.isVisible = true

        window.setContent {
            Row {
                Box(
                    Modifier.size(20.dp).onPointerEvent(PointerEventType.Release) {
                        box1ReleaseCount++
                    }
                )
                Box(
                    Modifier.size(20.dp).onPointerEvent(PointerEventType.Release) {
                        box2ReleaseCount++
                    }
                )
            }
        }
        awaitIdle()

        window.sendMouseEvent(id = MOUSE_PRESSED, x = 1, y = 1, modifiers = BUTTON1_DOWN_MASK)
        window.sendMouseEvent(id = MOUSE_RELEASED, x = 21, y = 1)

        assertThat(box1ReleaseCount).isEqualTo(1)
        assertThat(box2ReleaseCount).isEqualTo(0)

        window.dispose()
    }

    private fun getLockingKeyStateSafe(
        mask: Int
    ): Boolean = try {
        Toolkit.getDefaultToolkit().getLockingKeyState(mask)
    } catch (_: Exception) {
        false
    }

    /**
     * Handle only the first received event and drop all the others that are received
     * in a single frame
     */
    private fun Modifier.onFirstPointerEvent(
        eventType: PointerEventType,
        onEvent: AwaitPointerEventScope.(event: PointerEvent) -> Unit
    ) = pointerInput(eventType, onEvent) {
        while (true) {
            awaitPointerEventScope {
                val event = awaitEvent(eventType)
                onEvent(event)
            }
        }
    }

    private suspend fun AwaitPointerEventScope.awaitEvent(
        eventType: PointerEventType
    ): PointerEvent {
        var event: PointerEvent
        do {
            event = awaitPointerEvent()
        } while (
            event.type != eventType
        )
        return event
    }

    private val PointerEvent.pressed get() = changes.first().pressed
    private val PointerEvent.position get() = changes.first().position
}
