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

package androidx.compose.ui.awt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awaitEDT
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.google.common.truth.Truth.assertThat
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JFrame
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.jetbrains.skiko.MainUIDispatcher
import org.junit.Assume
import org.junit.Test

class ComposeFocusTest {

    @Test
    fun `compose window`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton1.Content()
                    composeButton2.Content()
                    composeButton3.Content()
                    composeButton4.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton1, composeButton2, composeButton3, composeButton4
        )
    }

    @Test
    fun `compose panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(
            ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton1.Content()
                            composeButton2.Content()
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                }
            }
        )
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton1, composeButton2, composeButton3, composeButton4
        )
    }

    @Test
    fun `compose panel in the end`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(outerButton1)
            add(outerButton2)

            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton1.Content()
                            composeButton2.Content()
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                }
            })
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            outerButton1, outerButton2, composeButton1, composeButton2, composeButton3, composeButton4
        )
    }

    @Test
    fun `compose panel in the beginning`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton1.Content()
                            composeButton2.Content()
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                }
            })
            add(outerButton3)
            add(outerButton4)
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton1, composeButton2, composeButton3, composeButton4, outerButton3, outerButton4
        )
    }

    @Test
    fun `swing panel in the middle of compose panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton3.Content()
                            SwingPanel(
                                modifier = Modifier.size(100.dp),
                                factory = {
                                    javax.swing.Box.createVerticalBox().apply {
                                        add(innerButton1)
                                    }
                                }
                            )
                            composeButton4.Content()
                        }
                    }
                }
            })
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, innerButton1, composeButton4,
        )
    }

    @Test
    fun `swing panel in the middle of compose window`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton3.Content()
                    SwingPanel(
                        modifier = Modifier.size(100.dp),
                        factory = {
                            javax.swing.Box.createVerticalBox().apply {
                                add(innerButton1)
                            }
                        }
                    )
                    composeButton4.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, innerButton1, composeButton4
        )
    }

    @Test
    fun `swing panel in the end of compose panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(outerButton1)
            add(outerButton2)

            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton1.Content()
                            composeButton2.Content()
                            SwingPanel(
                                modifier = Modifier.size(100.dp),
                                factory = {
                                    javax.swing.Box.createVerticalBox().apply {
                                        add(innerButton1)
                                        add(innerButton2)
                                        add(innerButton3)
                                    }
                                }
                            )
                        }
                    }
                }
            })
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            outerButton1, outerButton2, composeButton1, composeButton2, innerButton1,
            innerButton2, innerButton3
        )
    }

    @Test
    fun `swing panel in the beginning of compose panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            SwingPanel(
                                modifier = Modifier.size(100.dp),
                                factory = {
                                    javax.swing.Box.createVerticalBox().apply {
                                        add(innerButton1)
                                        add(innerButton2)
                                        add(innerButton3)
                                    }
                                }
                            )
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                }
            })
            add(outerButton3)
            add(outerButton4)
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            innerButton1,
            innerButton2, innerButton3, composeButton3, composeButton4, outerButton3, outerButton4
        )
    }

    @Test
    fun `swing panel without compose and outer buttons`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            SwingPanel(
                                modifier = Modifier.size(100.dp),
                                factory = {
                                    javax.swing.Box.createVerticalBox().apply {
                                        add(innerButton1)
                                        add(innerButton2)
                                        add(innerButton3)
                                    }
                                }
                            )
                        }
                    }
                }
            })
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(innerButton1, innerButton2, innerButton3)
    }

    @Test
    fun `empty compose panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(outerButton1)
            add(outerButton2)
            add(ComposePanel().apply {
                setContent {
                }
            })
            add(outerButton3)
            add(outerButton4)
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(outerButton1, outerButton2, outerButton3, outerButton4)
    }

    @Test
    fun `empty swing panel`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton3.Content()
                    SwingPanel(
                        modifier = Modifier.size(100.dp),
                        factory = {
                            javax.swing.Box.createVerticalBox()
                        }
                    )
                    composeButton4.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, composeButton4
        )
    }

    @Test
    fun `popup inside window`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton1.Content()
                    composeButton2.Content()
                    Popup(focusable = true) {
                        composeButton3.Content()
                        composeButton4.Content()
                    }
                    composeButton5.Content()
                    composeButton6.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, composeButton4
        )
    }

    @Test
    fun `popup inside panel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(outerButton1)
            add(outerButton2)

            add(ComposePanel().apply {
                setContent {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize()) {
                            composeButton1.Content()
                            composeButton2.Content()
                            Popup(focusable = true) {
                                composeButton3.Content()
                                composeButton4.Content()
                            }
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                }
            })

            add(outerButton3)
            add(outerButton4)
        })
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, composeButton4
        )
    }

    @Test
    fun `popup with swingPanel`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton1.Content()
                    composeButton2.Content()
                    Popup(focusable = true) {
                        Column(Modifier.fillMaxSize()) {
                            composeButton3.Content()
                            SwingPanel(
                                modifier = Modifier.size(100.dp),
                                factory = {
                                    javax.swing.Box.createVerticalBox().apply {
                                        add(innerButton1)
                                    }
                                }
                            )
                            composeButton4.Content()
                        }
                    }
                    composeButton5.Content()
                    composeButton6.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, innerButton1, composeButton4
        )
    }

    @Test
    fun `popup with swingPanel before it`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton1.Content()
                    composeButton2.Content()
                    SwingPanel(
                        modifier = Modifier.size(100.dp),
                        factory = {
                            javax.swing.Box.createVerticalBox().apply {
                                add(innerButton1)
                            }
                        }
                    )
                    Popup(focusable = true) {
                        Column(Modifier.fillMaxSize()) {
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                    composeButton5.Content()
                    composeButton6.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, composeButton4
        )
    }

    @Test
    fun `popup with swingPanel after it`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    composeButton1.Content()
                    composeButton2.Content()
                    Popup(focusable = true) {
                        Column(Modifier.fillMaxSize()) {
                            composeButton3.Content()
                            composeButton4.Content()
                        }
                    }
                    SwingPanel(
                        modifier = Modifier.size(100.dp),
                        factory = {
                            javax.swing.Box.createVerticalBox().apply {
                                add(innerButton1)
                            }
                        }
                    )
                    composeButton5.Content()
                    composeButton6.Content()
                }
            }
        }
        window.pack()
        window.isVisible = true

        testRandomFocus(
            composeButton3, composeButton4
        )
    }

    @Test
    fun `initial focus in ComposeWindow`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.setContent {
            composeButton1.Content()
        }
        window.pack()
        window.isVisible = true

        assertThat(composeButton1.isFocused).isFalse()

        awaitEDT()
        pressNextFocusKey()
        awaitEDT()
        assertThat(composeButton1.isFocused).isTrue()
    }

    @Test
    fun `initial focus in ComposePanel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent {
                    composeButton1.Content()
                }
            })
        })
        window.pack()
        window.isVisible = true

        assertThat(composeButton1.isFocused).isFalse()

        awaitEDT()
        pressNextFocusKey()
        awaitEDT()
        assertThat(composeButton1.isFocused).isTrue()
    }

    @Test
    fun `change focus in empty ComposeWindow`() = runFocusTest {
        val window = ComposeWindow().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)
        window.setContent { }
        window.pack()
        window.isVisible = true

        awaitEDT()
        pressNextFocusKey()
    }

    @Test
    fun `change focus in empty ComposePanel`() = runFocusTest {
        val window = JFrame().disposeOnEnd()
        window.preferredSize = Dimension(500, 500)

        window.contentPane.add(javax.swing.Box.createVerticalBox().apply {
            add(ComposePanel().apply {
                setContent { }
            })
        })
        window.pack()
        window.isVisible = true

        awaitEDT()
        pressNextFocusKey()
    }

    private val outerButton1 = TestJButton("outerButton1")
    private val outerButton2 = TestJButton("outerButton2")
    private val outerButton3 = TestJButton("outerButton3")
    private val outerButton4 = TestJButton("outerButton4")
    private val innerButton1 = TestJButton("innerButton1")
    private val innerButton2 = TestJButton("innerButton2")
    private val innerButton3 = TestJButton("innerButton3")
    private val composeButton1 = ComposeButton("composeButton1")
    private val composeButton2 = ComposeButton("composeButton2")
    private val composeButton3 = ComposeButton("composeButton3")
    private val composeButton4 = ComposeButton("composeButton4")
    private val composeButton5 = ComposeButton("composeButton5")
    private val composeButton6 = ComposeButton("composeButton6")

    private suspend fun FocusTestScope.testRandomFocus(vararg buttons: Any) {
        fun Any.validateIsFocused() {
            assertThat(
                buttons.filter { it.isFocused() }
            ).containsExactly(this)
        }

        suspend fun cycleForward() {
            var focusedIndex = buttons.indexOfFirst { it.isFocused() }
            println("cycleForward from ${buttons[focusedIndex]}")

            repeat(2 * buttons.size) {
                pressNextFocusKey()
                focusedIndex = (focusedIndex + 1).mod(buttons.size)
                buttons[focusedIndex].validateIsFocused()
            }
        }

        suspend fun cycleBackward() {
            var focusedIndex = buttons.indexOfFirst { it.isFocused() }
            println("cycleBackward from ${buttons[focusedIndex]}")

            repeat(2 * buttons.size) {
                pressPreviousFocusKey()
                focusedIndex = (focusedIndex - 1).mod(buttons.size)
                buttons[focusedIndex].validateIsFocused()
            }
        }

        suspend fun cycleRandom() {
            var focusedIndex = buttons.indexOfFirst { it.isFocused() }
            println("cycleRandom from ${buttons[focusedIndex]}")

            repeat(4 * buttons.size) {
                @Suppress("LiftReturnOrAssignment")
                if (Random.nextBoolean()) {
                    pressNextFocusKey()
                    focusedIndex = (focusedIndex + 1).mod(buttons.size)
                } else {
                    pressPreviousFocusKey()
                    focusedIndex = (focusedIndex - 1).mod(buttons.size)
                }
                buttons[focusedIndex].validateIsFocused()
            }
        }

        suspend fun randomRequest() {
            println("randomRequest")

            val button = buttons.toList().random()
            button.requestFocus()
            awaitEDT()
            button.validateIsFocused()
        }

        suspend fun randomPress() {
            println("randomPress")

            val button = buttons.filterIsInstance<Component>().randomOrNull()
            button?.performClick()
            awaitEDT()
            button?.validateIsFocused()
        }

        awaitEDT()
        println("firstButton")
        buttons.first().requestFocus()
        awaitEDT()
        buttons.first().validateIsFocused()

        repeat(30) {
            when (Random.nextInt(5)) {
                0 -> cycleForward()
                1 -> cycleBackward()
                2 -> cycleRandom()
                3 -> randomRequest()
                4 -> randomPress()
            }
        }
    }
}

fun runFocusTest(action: suspend FocusTestScope.() -> Unit) {
    Assume.assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)
    runBlocking(MainUIDispatcher) {
        val scope = FocusTestScope()
        try {
            scope.action()
        } finally {
            scope.onEnd()
        }
    }
}

class FocusTestScope {
    private val windows = mutableListOf<Window>()

    fun <T : Window> T.disposeOnEnd() : T {
        windows.add(this)
        return this
    }

    fun onEnd() {
        windows.forEach { it.dispose() }
        windows.clear()
    }

    suspend fun pressNextFocusKey() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        focusOwner.dispatchEvent(KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_TAB, '\t'))
        focusOwner.dispatchEvent(KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_TAB, '\t'))
        awaitEDT()
    }

    suspend fun pressPreviousFocusKey() {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        focusOwner.dispatchEvent(KeyEvent(focusOwner, KeyEvent.KEY_PRESSED, 0, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB, '\t'))
        focusOwner.dispatchEvent(KeyEvent(focusOwner, KeyEvent.KEY_RELEASED, 0, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_TAB, '\t'))
        awaitEDT()
    }
}

private fun Any.requestFocus() {
    when (this) {
        is ComposeButton -> requestFocus()
        is Component -> requestFocusInWindow()
        else -> error("Unknown component")
    }
}

private fun Any.isFocused() = when (this) {
    is ComposeButton -> isFocused
    is Component -> hasFocus()
    else -> error("Unknown component")
}

private class ComposeButton(val name: String) {
    var isFocused = false
        private set
    private val focusRequester = FocusRequester()

    @Composable
    fun Content() {
        Button({}, modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.focusRequester(focusRequester)) {}
    }

    fun requestFocus() = focusRequester.requestFocus()

    override fun toString() = name
}
private class TestJButton(name: String) : JButton(name) {
    override fun toString(): String = text
}