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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.density
import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skiko.*
import org.junit.Assume.assumeFalse
import org.junit.Test

class ComposePanelTest {
    @Test
    fun `don't override user preferred size`() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        runBlocking(MainUIDispatcher) {
            val composePanel = ComposePanel()
            composePanel.preferredSize = Dimension(234, 345)
            assertThat(composePanel.preferredSize).isEqualTo(Dimension(234, 345))

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.isUndecorated = true

                assertThat(composePanel.preferredSize).isEqualTo(Dimension(234, 345))

                frame.pack()
                assertThat(composePanel.size).isEqualTo(Dimension(234, 345))
                assertThat(frame.size).isEqualTo(Dimension(234, 345))
            } finally {
                frame.dispose()
            }
        }
    }

    @Test
    fun `pack to Compose content`() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        runBlocking(MainUIDispatcher) {
            val composePanel = ComposePanel()
            composePanel.setContent {
                Box(Modifier.requiredSize(300.dp, 400.dp))
            }

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.isUndecorated = true

                frame.pack()
                assertThat(composePanel.preferredSize).isEqualTo(Dimension(300, 400))
                assertThat(frame.preferredSize).isEqualTo(Dimension(300, 400))

                frame.isVisible = true
                assertThat(composePanel.preferredSize).isEqualTo(Dimension(300, 400))
                assertThat(frame.preferredSize).isEqualTo(Dimension(300, 400))
            } finally {
                frame.dispose()
            }
        }
    }

    @Test
    fun `a single layout pass at the window start`() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        val layoutPassConstraints = mutableListOf<Constraints>()

        runBlocking(MainUIDispatcher) {
            val composePanel = ComposePanel()
            composePanel.setContent {
                Box(Modifier.fillMaxSize().layout { _, constraints ->
                    layoutPassConstraints.add(constraints)
                    layout(0, 0) {}
                })
            }

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.size = Dimension(300, 400)
                frame.isUndecorated = true
                frame.isVisible = true
                frame.paint(frame.graphics)

                assertThat(layoutPassConstraints).isEqualTo(
                    listOf(
                        Constraints.fixed(
                            width = (300 * frame.density.density).toInt(),
                            height = (400 * frame.density.density).toInt()
                        )
                    )
                )
            } finally {
                frame.dispose()
            }
        }
    }

    @OptIn(ExperimentalSkikoApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun SkiaLayerAnalytics() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        runBlocking(MainUIDispatcher) {
            var rendererIsCalled = false
            val analytics = object : SkiaLayerAnalytics {
                override fun renderer(
                    skikoVersion: String,
                    os: OS,
                    api: GraphicsApi
                ): SkiaLayerAnalytics.RendererAnalytics {
                    rendererIsCalled = true
                    return super.renderer(skikoVersion, os, api)
                }
            }

            val composePanel = ComposePanel(skiaLayerAnalytics = analytics)
            composePanel.size = Dimension(100, 100)

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.size = Dimension(100, 100)
                frame.isUndecorated = true
                frame.isVisible = true
                frame.contentPane.paint(frame.graphics)
                assertThat(rendererIsCalled).isTrue()
            } finally {
                frame.dispose()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `compose state shouldn't reset on panel remove and add with isDisposeOnRemove = false`() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        runBlocking(MainUIDispatcher) {
            var initialStateCounter = 0
            val composePanel = ComposePanel().apply {
                isDisposeOnRemove = false
            }
            composePanel.setContent {
                var state by remember { mutableStateOf(0) }

                LaunchedEffect(state) {
                    if (state == 0) {
                        state++
                        initialStateCounter++
                    }
                }
            }

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.isUndecorated = true

                frame.pack()

                frame.isVisible = true
                delay(1000)
                assertEquals(1, initialStateCounter)

                frame.contentPane.remove(composePanel)
                delay(1000)
                assertEquals(1, initialStateCounter)

                frame.contentPane.add(composePanel)
                delay(1000)
                assertEquals(1, initialStateCounter)
            } finally {
                frame.dispose()
                composePanel.dispose()
            }
        }
    }

    @Test
    fun `compose state should reset on panel remove and add`() {
        assumeFalse(GraphicsEnvironment.getLocalGraphicsEnvironment().isHeadlessInstance)

        runBlocking(MainUIDispatcher) {
            var initialStateCounter = 0
            val composePanel = ComposePanel()
            composePanel.setContent {
                var state by remember { mutableStateOf(0) }

                LaunchedEffect(state) {
                    if (state == 0) {
                        state++
                        initialStateCounter++
                    }
                }
            }

            val frame = JFrame()
            try {
                frame.contentPane.add(composePanel)
                frame.isUndecorated = true

                frame.pack()

                frame.isVisible = true
                delay(1000)
                assertEquals(1, initialStateCounter)

                frame.contentPane.remove(composePanel)
                delay(1000)
                assertEquals(1, initialStateCounter)

                frame.contentPane.add(composePanel)
                delay(1000)
                assertEquals(2, initialStateCounter)
            } finally {
                frame.dispose()
            }
        }
    }
}