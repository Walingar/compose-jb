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

package androidx.compose.material3

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import kotlinx.coroutines.launch
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class TooltipTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun plainTooltip_noContent_size() {
        rule.setMaterialContent(lightColorScheme()) { TestPlainTooltip() }

        rule.onNodeWithTag(ContainerTestTag)
            .assertHeightIsEqualTo(TooltipMinHeight)
            .assertWidthIsEqualTo(TooltipMinWidth)
    }

    @Test
    fun richTooltip_noContent_size() {
        rule.setMaterialContent(lightColorScheme()) { TestRichTooltip() }
        rule.onNodeWithTag(ContainerTestTag)
            .assertHeightIsEqualTo(TooltipMinHeight)
            .assertWidthIsEqualTo(TooltipMinWidth)
    }

    @Test
    fun plainTooltip_customSize_size() {
        val customWidth = 100.dp
        val customHeight = 100.dp

        rule.setMaterialContent(lightColorScheme()) {
            TestPlainTooltip(modifier = Modifier.size(customWidth, customHeight))
        }

        rule.onNodeWithTag(ContainerTestTag)
            .assertHeightIsEqualTo(customHeight)
            .assertWidthIsEqualTo(customWidth)
    }

    @Test
    fun richTooltip_customSize_size() {
        val customWidth = 100.dp
        val customHeight = 100.dp

        rule.setMaterialContent(lightColorScheme()) {
            TestRichTooltip(modifier = Modifier.size(customWidth, customHeight))
        }

        rule.onNodeWithTag(ContainerTestTag)
            .assertHeightIsEqualTo(customHeight)
            .assertWidthIsEqualTo(customWidth)
    }

    @Ignore // b/264907895
    @Test
    fun plainTooltip_content_padding() {
        rule.setMaterialContent(lightColorScheme()) {
            TestPlainTooltip(
                tooltipContent = {
                    Text(
                        text = "Test",
                        modifier = Modifier.testTag(TextTestTag)
                    )
                }
            )
        }

        rule.onNodeWithTag(TextTestTag)
            .assertLeftPositionInRootIsEqualTo(8.dp)
            .assertTopPositionInRootIsEqualTo(4.dp)
    }

    @Test
    fun richTooltip_content_padding() {
        rule.setMaterialContent(lightColorScheme()) {
            TestRichTooltip(
                title = { Text(text = "Subhead", modifier = Modifier.testTag(SubheadTestTag)) },
                text = { Text(text = "Text", modifier = Modifier.testTag(TextTestTag)) },
                action = { Text(text = "Action", modifier = Modifier.testTag(ActionTestTag)) },
            )
        }

        val subhead = rule.onNodeWithTag(SubheadTestTag)
        val text = rule.onNodeWithTag(TextTestTag)

        val subheadBaseline = subhead.getFirstBaselinePosition()
        val textBaseLine = text.getFirstBaselinePosition()

        val subheadBound = subhead.getUnclippedBoundsInRoot()
        val textBound = text.getUnclippedBoundsInRoot()

        rule.onNodeWithTag(SubheadTestTag)
            .assertLeftPositionInRootIsEqualTo(RichTooltipHorizontalPadding)
            .assertTopPositionInRootIsEqualTo(28.dp - subheadBaseline)

        rule.onNodeWithTag(TextTestTag)
            .assertLeftPositionInRootIsEqualTo(RichTooltipHorizontalPadding)
            .assertTopPositionInRootIsEqualTo(subheadBound.bottom + 24.dp - textBaseLine)

        rule.onNodeWithTag(ActionTestTag)
            .assertLeftPositionInRootIsEqualTo(RichTooltipHorizontalPadding)
            .assertTopPositionInRootIsEqualTo(textBound.bottom + 16.dp)
    }

    @Ignore // b/264887805
    @Test
    fun plainTooltip_behavior() {
        val tooltipState = PlainTooltipState()
        rule.setMaterialContent(lightColorScheme()) {
            PlainTooltipBox(
                tooltip = { Text(text = "Test", modifier = Modifier.testTag(TextTestTag)) },
                tooltipState = tooltipState,
                modifier = Modifier.testTag(ContainerTestTag)
            ) { Anchor(tooltipState) }
        }

        // Tooltip should initially be not visible
        assert(!tooltipState.isVisible)

        // Long press the icon and check that the tooltip is now showing
        rule.onNodeWithTag(AnchorTestTag)
            .performTouchInput { longClick() }

        assert(tooltipState.isVisible)

        // Tooltip should dismiss itself after 1.5s
        rule.waitUntil(TooltipDuration + 100L) { !tooltipState.isVisible }
    }

    @Ignore // b/264887805
    @Test
    fun richTooltip_behavior_noAction() {
        val tooltipState = RichTooltipState()
        rule.setMaterialContent(lightColorScheme()) {
            RichTooltipBox(
                title = { Text(text = "Subhead", modifier = Modifier.testTag(SubheadTestTag)) },
                text = { Text(text = "Text", modifier = Modifier.testTag(TextTestTag)) },
                tooltipState = tooltipState,
                modifier = Modifier.testTag(ContainerTestTag)
            ) { Anchor(tooltipState) }
        }

        // Tooltip should initially be not visible
        assert(!tooltipState.isVisible)

        // Long press the icon and check that the tooltip is now showing
        rule.onNodeWithTag(AnchorTestTag)
            .performTouchInput { longClick() }

        assert(tooltipState.isVisible)

        // Tooltip should dismiss itself after 1.5s
        rule.waitUntil(TooltipDuration + 100L) { !tooltipState.isVisible }
    }

    @Test
    fun richTooltip_behavior_persistent() {
        val tooltipState = RichTooltipState()
        rule.setMaterialContent(lightColorScheme()) {
            val scope = rememberCoroutineScope()
            RichTooltipBox(
                title = { Text(text = "Subhead", modifier = Modifier.testTag(SubheadTestTag)) },
                text = { Text(text = "Text", modifier = Modifier.testTag(TextTestTag)) },
                action = {
                    TextButton(
                        onClick = { scope.launch { tooltipState.dismiss() } },
                        modifier = Modifier.testTag(ActionTestTag)
                    ) { Text(text = "Action") }
                },
                tooltipState = tooltipState,
                modifier = Modifier.testTag(ContainerTestTag)
            ) { Anchor(tooltipState) }
        }

        // Tooltip should initially be not visible
        assert(!tooltipState.isVisible)

        // Long press the icon and check that the tooltip is now showing
        rule.onNodeWithTag(AnchorTestTag)
            .performTouchInput { longClick() }
        assert(tooltipState.isVisible)

        // Tooltip should still be visible after the normal TooltipDuration, since we have an action.
        rule.waitUntil(TooltipDuration + 100L) { tooltipState.isVisible }

        // Click the action and check that it closed the tooltip
        rule.onNodeWithTag(ActionTestTag)
            .performTouchInput { click() }
        assert(!tooltipState.isVisible)
    }

    @Composable
    private fun TestPlainTooltip(
        modifier: Modifier = Modifier,
        tooltipState: PlainTooltipState = remember { PlainTooltipState() },
        tooltipContent: @Composable () -> Unit = {}
    ) {
        val scope = rememberCoroutineScope()

        PlainTooltipBox(
            tooltip = tooltipContent,
            modifier = modifier.testTag(ContainerTestTag),
            tooltipState = tooltipState
        ) {}

        scope.launch { tooltipState.show() }
    }

    @Composable
    private fun TestRichTooltip(
        modifier: Modifier = Modifier,
        tooltipState: RichTooltipState = remember { RichTooltipState() },
        text: @Composable () -> Unit = {},
        action: (@Composable () -> Unit)? = null,
        title: (@Composable () -> Unit)? = null
    ) {
        val scope = rememberCoroutineScope()

        RichTooltipBox(
            text = text,
            action = action,
            title = title,
            modifier = modifier.testTag(ContainerTestTag),
            tooltipState = tooltipState
        ) {}

        scope.launch { tooltipState.show() }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Anchor(
        tooltipState: TooltipState
    ) {
        val scope = rememberCoroutineScope()

        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            modifier = Modifier
                .testTag(AnchorTestTag)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        scope.launch {
                            tooltipState.show()
                        }
                    }
                )
        )
    }
}

private const val ContainerTestTag = "Container"
private const val TextTestTag = "Text"
private const val SubheadTestTag = "Subhead"
private const val ActionTestTag = "Action"
private const val AnchorTestTag = "Anchor'"