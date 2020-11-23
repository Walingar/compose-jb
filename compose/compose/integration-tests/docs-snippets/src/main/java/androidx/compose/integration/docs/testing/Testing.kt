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

// Ignore lint warnings in documentation snippets
@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

package androidx.compose.integration.docs.testing

import android.view.KeyEvent as AndroidKeyEvent
import android.view.KeyEvent.KEYCODE_A as KeyCodeA
import android.view.KeyEvent.ACTION_DOWN as ActionDown
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.swipeLeft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * This file lets DevRel track changes to snippets present in
 * https://developer.android.com/jetpack/compose/testing
 *
 * No action required if it's modified.
 */

@Composable private fun TestingSnippet1() {
    MyButton(modifier = Modifier.semantics { contentDescription = "Like button" })
}

private object TestingSnippet3 {
    // file: app/src/androidTest/java/com/package/MyComposeTest.kt

    class MyComposeTest {

        @get:Rule
        val composeTestRule = createAndroidComposeRule<MyActivity>()
        // createComposeRule() if you don't need access to the activityTestRule

        @Test
        fun MyTest() {
            // Start the app
            composeTestRule.setContent {
                MyAppTheme {
                    MainScreen(uiState = exampleUiState, /*...*/)
                }
            }

            composeTestRule.onNodeWithText("Continue").performClick()

            composeTestRule.onNodeWithText("Welcome").assertIsDisplayed()
        }
    }
}

@Composable private fun TestingSnippets4() {
    // single node
    // It's API, see line below.
    // onNode(<<SemanticsMatcher>>, useUnmergedTree = false): SemanticsNodeInteraction
    composeTestRule.onNode(hasText("Button")) // Equivalent to onNodeWithText("Button")

    // multiple nodes
    // It's API, see line below.
    // onAllNodes(<<SemanticsMatcher>>): SemanticsNodeInteractionCollection

    // Example
    composeTestRule.onAllNodes(hasText("Button")) // Equivalent to onAllNodesWithText("Button")
}

@Composable private fun TestingSnippets5() {
    MyButton {
        Text("Hello")
        Text("World")
    }

    composeTestRule.onRoot().printToLog("TAG")

    composeTestRule.onRoot(useUnmergedTree = true).printToLog("TAG")
}

@Composable private fun TestingSnippets6() {
    composeTestRule.onNodeWithText("World", useUnmergedTree = true).assertIsDisplayed()
}

// assertions

@Composable private fun TestingSnippets7() {
    // Single matcher:
    composeTestRule.onNode(matcher).assert(hasText("Button")) // hasText is a SemanticsMatcher
    // Multiple matchers can use and / or
    composeTestRule.onNode(matcher).assert(hasText("Button") or hasText("Button2"))
}

@Composable private fun TestingSnippets8() {
    // Check number of matched nodes
    composeTestRule.onAllNodesWithContentDescription("Beatle").assertCountEquals(4)
    // At least one matches
    composeTestRule.onAllNodesWithContentDescription("Beatle").assertAny(hasTestTag("Drummer"))
    // All of them match
    composeTestRule.onAllNodesWithContentDescription("Beatle").assertAll(hasClickAction())
}

@Composable private fun SemanticsNodeInteraction.TestingSnippets9() {
    val listOfActions = listOf(
        // start snippet
        performClick(),
        performSemanticsAction(key),
        performKeyPress(keyEvent),
        performGesture { swipeLeft() }
        // end snippet
    )
}

@Composable private fun TestingSnippets10() {
    // It's API, look for changes below.
    val matcher = SemanticsMatcher("test", { true })
    hasParent(matcher)
    hasAnySibling(matcher)
    hasAnyAncestor(matcher)
    hasAnyDescendant(matcher)
}

@Composable private fun TestingSnippets11() {
    composeTestRule.onNode(hasParent(hasText("Button")))
        .assertIsDisplayed()
}

@Composable private fun TestingSnippets12() {
    composeTestRule.onNode(hasTestTag("Players"))
        .onChildren()
        .filter(hasClickAction())
        .assertCountEquals(4)
        .onFirst()
        .assert(hasText("John"))
}

@OptIn(ExperimentalCoroutinesApi::class)
private object TestingSnippets13 {
    class MyTest() {

        private val themeIsDark = MutableStateFlow(false)

        @Before
        fun setUp() {
            composeTestRule.setContent {
                JetchatTheme(
                    isDarkTheme = themeIsDark.collectAsState(false).value
                ) {
                    MainScreen()
                }
            }
        }

        @Test
        fun changeTheme_scrollIsPersisted() {
            composeTestRule.onNodeWithContentDescription("Continue").performClick()

            // Set theme to dark
            themeIsDark.value = true

            // Check that we're still on the same page
            composeTestRule.onNodeWithContentDescription("Welcome").assertIsDisplayed()
        }
    }
}

/*
Fakes needed for snippets to build:
 */

private val matcher = hasText("Button")
private val text = ""
private val composeTestRule = createAndroidComposeRule<MyActivity>()
@Composable private fun MyButton(modifier: Modifier) {}
@Composable private fun MyAppTheme(content: @Composable () -> Unit) {}
@Composable private fun JetchatTheme(isDarkTheme: Boolean, content: @Composable () -> Unit) {}
private val exampleUiState = Unit
@Composable private fun MainScreen(uiState: Any = Unit) {}
private class MyActivity : ComponentActivity()
@Composable private fun MyButton(content: @Composable RowScope.() -> Unit) { }
private lateinit var key: SemanticsPropertyKey<AccessibilityAction<() -> Boolean>>
private var keyEvent = KeyEvent(AndroidKeyEvent(ActionDown, KeyCodeA))
