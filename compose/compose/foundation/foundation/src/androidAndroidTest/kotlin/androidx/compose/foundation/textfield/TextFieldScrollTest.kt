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

package androidx.compose.foundation.textfield

import android.os.Build
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.animation.core.ManualAnimationClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.InteractionState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.animation.FlingConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextFieldScrollerPosition
import androidx.compose.foundation.text.TextLayoutResultProxy
import androidx.compose.foundation.text.maxLinesHeight
import androidx.compose.foundation.text.textFieldScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.textFieldScrollable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.testutils.assertPixels
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.scrollorientationlocking.Orientation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * These tests are for testing the text field scrolling modifiers [Modifier.textFieldScroll] and
 * [Modifier.textFieldScrollable] working together.
 * The tests are structured in a way that
 * - two modifiers are applied to the text which exposes its [TextLayoutResult]
 * - swipe gesture applied
 * - [TextFieldScrollerPosition] state is checked to see if scrolling happened
 * Previously we were able to test using CoreTextField. But with the decoration box change these
 * two modifiers are already applied to the CoreTextField internally. Therefore we have no access
 * to the [TextFieldScrollerPosition] object anymore. As such, CoreTextField was replaced with
 * [BasicText] which is equivalent for testing these modifiers
 */

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class, InternalTextApi::class)
class TextFieldScrollTest {

    private val TextfieldTag = "textField"

    private val longText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
        "eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam," +
        " quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu " +
        "fugiat nulla pariatur."

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun textFieldScroll_horizontal_scrollable_withLongInput() {
        val scrollerPosition = TextFieldScrollerPosition(Orientation.Horizontal)

        rule.setupHorizontallyScrollableContent(
            scrollerPosition, longText, Modifier.preferredSize(width = 300.dp, height = 50.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.maximum).isLessThan(Float.POSITIVE_INFINITY)
            assertThat(scrollerPosition.maximum).isGreaterThan(0f)
        }
    }

    @Test
    fun textFieldScroll_vertical_scrollable_withLongInput() {
        val scrollerPosition = TextFieldScrollerPosition()

        rule.setupVerticallyScrollableContent(
            scrollerPosition = scrollerPosition,
            text = longText,
            modifier = Modifier.preferredSize(width = 300.dp, height = 50.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.maximum).isLessThan(Float.POSITIVE_INFINITY)
            assertThat(scrollerPosition.maximum).isGreaterThan(0f)
        }
    }

    @Test
    fun textFieldScroll_vertical_scrollable_withLongInput_whenMaxLinesProvided() {
        val scrollerPosition = TextFieldScrollerPosition()

        rule.setupVerticallyScrollableContent(
            modifier = Modifier.preferredWidth(100.dp),
            scrollerPosition = scrollerPosition,
            text = longText,
            maxLines = 3
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.maximum).isLessThan(Float.POSITIVE_INFINITY)
            assertThat(scrollerPosition.maximum).isGreaterThan(0f)
        }
    }

    @Test
    fun textFieldScroll_horizontal_notScrollable_withShortInput() {
        val scrollerPosition = TextFieldScrollerPosition(Orientation.Horizontal)

        rule.setupHorizontallyScrollableContent(
            scrollerPosition = scrollerPosition,
            text = "text",
            modifier = Modifier.preferredSize(width = 300.dp, height = 50.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.maximum).isEqualTo(0f)
        }
    }

    @Test
    fun textFieldScroll_vertical_notScrollable_withShortInput() {
        val scrollerPosition = TextFieldScrollerPosition()

        rule.setupVerticallyScrollableContent(
            scrollerPosition = scrollerPosition,
            text = "text",
            modifier = Modifier.preferredSize(width = 300.dp, height = 100.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.maximum).isEqualTo(0f)
        }
    }

    @Test
    @LargeTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun textField_singleLine_scrolledAndClipped() {
        val parentSize = 200
        val textFieldSize = 50
        val tag = "OuterBox"

        with(rule.density) {
            rule.setContent {
                Box(
                    Modifier
                        .preferredSize(parentSize.toDp())
                        .background(color = Color.White)
                        .testTag(tag)
                ) {
                    ScrollableContent(
                        modifier = Modifier.preferredSize(textFieldSize.toDp()),
                        scrollerPosition = TextFieldScrollerPosition(Orientation.Horizontal),
                        text = longText,
                        isVertical = false
                    )
                }
            }
        }

        rule.runOnIdle {}

        rule.onNodeWithTag(tag)
            .captureToImage()
            .assertPixels(expectedSize = IntSize(parentSize, parentSize)) { position ->
                if (position.x > textFieldSize && position.y > textFieldSize) Color.White else null
            }
    }

    @Test
    @LargeTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun textField_multiline_scrolledAndClipped() {
        val parentSize = 200
        val textFieldSize = 50
        val tag = "OuterBox"

        with(rule.density) {
            rule.setContent {
                Box(
                    Modifier
                        .preferredSize(parentSize.toDp())
                        .background(color = Color.White)
                        .testTag(tag)
                ) {
                    ScrollableContent(
                        modifier = Modifier.preferredSize(textFieldSize.toDp()),
                        scrollerPosition = TextFieldScrollerPosition(),
                        text = longText,
                        isVertical = true
                    )
                }
            }
        }

        rule.runOnIdle {}

        rule.onNodeWithTag(tag)
            .captureToImage()
            .assertPixels(expectedSize = IntSize(parentSize, parentSize)) { position ->
                if (position.x > textFieldSize && position.y > textFieldSize) Color.White else null
            }
    }

    @Test
    fun textFieldScroll_horizontal_swipe_whenLongInput() {
        val scrollerPosition = TextFieldScrollerPosition(Orientation.Horizontal)

        rule.setupHorizontallyScrollableContent(
            scrollerPosition = scrollerPosition,
            text = longText,
            modifier = Modifier.preferredSize(width = 300.dp, height = 50.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.offset).isEqualTo(0f)
        }

        rule.onNodeWithTag(TextfieldTag)
            .performGesture { swipeLeft() }

        val firstSwipePosition = rule.runOnIdle {
            scrollerPosition.offset
        }
        assertThat(firstSwipePosition).isGreaterThan(0f)

        rule.onNodeWithTag(TextfieldTag)
            .performGesture { swipeRight() }
        rule.runOnIdle {
            assertThat(scrollerPosition.offset).isLessThan(firstSwipePosition)
        }
    }

    @Test
    fun textFieldScroll_vertical_swipe_whenLongInput() {
        val scrollerPosition = TextFieldScrollerPosition()

        rule.setupVerticallyScrollableContent(
            scrollerPosition = scrollerPosition,
            text = longText,
            modifier = Modifier.preferredSize(width = 300.dp, height = 50.dp)
        )

        rule.runOnIdle {
            assertThat(scrollerPosition.offset).isEqualTo(0f)
        }

        rule.onNodeWithTag(TextfieldTag)
            .performGesture { swipeUp() }

        val firstSwipePosition = rule.runOnIdle {
            scrollerPosition.offset
        }
        assertThat(firstSwipePosition).isGreaterThan(0f)

        rule.onNodeWithTag(TextfieldTag)
            .performGesture { swipeDown() }
        rule.runOnIdle {
            assertThat(scrollerPosition.offset).isLessThan(firstSwipePosition)
        }
    }

    @Test
    fun textFieldScroll_restoresScrollerPosition() {
        val restorationTester = StateRestorationTester(rule)
        var scrollerPosition: TextFieldScrollerPosition? = null

        restorationTester.setContent {
            scrollerPosition = rememberSavedInstanceState(
                saver = TextFieldScrollerPosition.Saver
            ) {
                TextFieldScrollerPosition(Orientation.Horizontal)
            }
            ScrollableContent(
                modifier = Modifier.preferredSize(width = 300.dp, height = 50.dp),
                scrollerPosition = scrollerPosition!!,
                text = longText,
                isVertical = false
            )
        }

        rule.onNodeWithTag(TextfieldTag)
            .performGesture { swipeLeft() }

        val swipePosition = rule.runOnIdle {
            scrollerPosition!!.offset
        }
        assertThat(swipePosition).isGreaterThan(0f)

        rule.runOnIdle {
            scrollerPosition = TextFieldScrollerPosition()
            assertThat(scrollerPosition!!.offset).isEqualTo(0f)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertThat(scrollerPosition!!.offset).isEqualTo(swipePosition)
        }
    }

    @Test
    fun textFieldScrollable_testInspectorValue() {
        val position = TextFieldScrollerPosition(Orientation.Vertical, 10f)
        val interactionState = InteractionState()
        rule.setContent {
            val modifier =
                Modifier.textFieldScrollable(position, interactionState) as InspectableValue
            assertThat(modifier.nameFallback).isEqualTo("textFieldScrollable")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.map { it.name }.asIterable()).containsExactly(
                "scrollerPosition",
                "interactionState",
                "enabled"
            )
        }
    }

    @Test
    fun textFieldScroll_testNestedScrolling() {
        val size = 300.dp
        val text = """
            First Line
            Second Line
            Third Line
            Fourth Line
        """.trimIndent()

        val textFieldScrollPosition = TextFieldScrollerPosition()
        val scrollerPosition = ScrollState(
            0f,
            FlingConfig(FloatExponentialDecaySpec()),
            ManualAnimationClock(0)
        )

        rule.setContent {
            Column(
                Modifier
                    .preferredSize(size)
                    .verticalScroll(scrollerPosition)
            ) {
                ScrollableContent(
                    modifier = Modifier.preferredSize(size, 50.dp),
                    scrollerPosition = textFieldScrollPosition,
                    text = text,
                    isVertical = true
                )
                Box(Modifier.preferredSize(size))
                Box(Modifier.preferredSize(size))
            }
        }

        rule.runOnIdle {
            assertThat(textFieldScrollPosition.offset).isEqualTo(0f)
            assertThat(textFieldScrollPosition.maximum).isGreaterThan(0f)
            assertThat(scrollerPosition.value).isEqualTo(0f)
        }

        with(rule.density) {
            val x = 10.dp.toPx()
            val start = Offset(x, 40.dp.toPx())
            val end = Offset(x, 0f)
            rule.onNodeWithTag(TextfieldTag)
                .performGesture {
                    // scroll first two lines
                    swipe(start, end)
                    // scroll last two lines
                    swipe(start, end)
                    // scroll Scrollable column
                    swipe(start, end)
                }
        }

        rule.runOnIdle {
            assertThat(textFieldScrollPosition.offset).isGreaterThan(0f)
            assertThat(textFieldScrollPosition.offset)
                .isWithin(0.5f).of(textFieldScrollPosition.maximum)
            assertThat(scrollerPosition.value).isGreaterThan(0f)
        }
    }

    private fun ComposeContentTestRule.setupHorizontallyScrollableContent(
        scrollerPosition: TextFieldScrollerPosition,
        text: String,
        modifier: Modifier = Modifier
    ) {
        setContent {
            ScrollableContent(
                scrollerPosition = scrollerPosition,
                text = text,
                isVertical = false,
                modifier = modifier,
                maxLines = 1
            )
        }
    }

    private fun ComposeContentTestRule.setupVerticallyScrollableContent(
        scrollerPosition: TextFieldScrollerPosition,
        text: String,
        modifier: Modifier = Modifier,
        maxLines: Int = Int.MAX_VALUE
    ) {
        setContent {
            ScrollableContent(
                scrollerPosition = scrollerPosition,
                text = text,
                isVertical = true,
                modifier = modifier,
                maxLines = maxLines
            )
        }
    }

    @Composable
    private fun ScrollableContent(
        modifier: Modifier,
        scrollerPosition: TextFieldScrollerPosition,
        text: String,
        isVertical: Boolean,
        maxLines: Int = Int.MAX_VALUE
    ) {
        val textLayoutResultRef: Ref<TextLayoutResultProxy?> = remember { Ref() }
        val resolvedMaxLines = if (isVertical) maxLines else 1

        BasicText(
            text = text,
            onTextLayout = {
                textLayoutResultRef.value = TextLayoutResultProxy(it)
            },
            softWrap = isVertical,
            modifier = modifier
                .testTag(TextfieldTag)
                .maxLinesHeight(resolvedMaxLines, TextStyle.Default)
                .textFieldScrollable(scrollerPosition)
                .textFieldScroll(
                    remember { scrollerPosition },
                    TextFieldValue(text),
                    VisualTransformation.None,
                    { textLayoutResultRef.value }
                )
        )
    }
}