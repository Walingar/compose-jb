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

package androidx.compose.foundation

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Providers
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.AmbientTextInputService
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextEditOp
import androidx.compose.ui.text.input.DeleteSurroundingTextEditOp
import androidx.compose.ui.text.input.EditOperation
import androidx.compose.ui.text.input.FinishComposingTextEditOp
import androidx.compose.ui.text.input.SetComposingRegionEditOp
import androidx.compose.ui.text.input.SetComposingTextEditOp
import androidx.compose.ui.text.input.SetSelectionEditOp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalFoundationApi::class)
class TextFieldOnValueChangeTextFieldValueTest {
    @get:Rule
    val rule = createComposeRule()

    val onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit = mock()

    lateinit var onEditCommandCallback: (List<EditOperation>) -> Unit

    @Before
    fun setUp() {
        val textInputService = mock<TextInputService>()
        val inputSessionToken = 10 // any positive number is fine.

        whenever(textInputService.startInput(any(), any(), any(), any()))
            .thenReturn(inputSessionToken)

        rule.setContent {
            Providers(
                AmbientTextInputService provides textInputService
            ) {
                val state = remember {
                    mutableStateOf(
                        TextFieldValue(
                            "abcde",
                            TextRange.Zero
                        )
                    )
                }
                BasicTextField(
                    value = state.value,
                    onValueChange = {
                        state.value = it
                        onValueChange(it)
                    }
                )
            }
        }

        // Perform click to focus in.
        rule.onNode(hasSetTextAction())
            .performGesture { click(Offset(1f, 1f)) }

        rule.runOnIdle {
            // Verify startInput is called and capture the callback.
            val onEditCommandCaptor = argumentCaptor<(List<EditOperation>) -> Unit>()
            verify(textInputService, times(1)).startInput(
                value = any(),
                imeOptions = any(),
                onEditCommand = onEditCommandCaptor.capture(),
                onImeActionPerformed = any()
            )
            assertThat(onEditCommandCaptor.allValues.size).isEqualTo(1)
            onEditCommandCallback = onEditCommandCaptor.firstValue
            assertThat(onEditCommandCallback).isNotNull()
            clearInvocations(onValueChange)
        }
    }

    private fun performEditOperation(op: EditOperation) {
        arrayOf(listOf(op)).forEach {
            rule.runOnUiThread {
                onEditCommandCallback(it)
            }
        }
    }

    @Test
    fun commitText_onValueChange_call_once() {
        // Committing text should be reported as value change
        performEditOperation(CommitTextEditOp("ABCDE", 1))
        rule.runOnIdle {
            verify(onValueChange, times(1))
                .invoke(
                    eq(
                        TextFieldValue(
                            "ABCDEabcde",
                            TextRange(5)
                        )
                    )
                )
        }
    }

    @Test
    fun setComposingRegion_onValueChange_call_once() {
        val textFieldValueCaptor = argumentCaptor<TextFieldValue>()
        // Composition change will be reported as a change
        performEditOperation(SetComposingRegionEditOp(0, 5))

        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(textFieldValueCaptor.capture())
            assertThat(textFieldValueCaptor.firstValue.text).isEqualTo("abcde")
            assertThat(textFieldValueCaptor.firstValue.selection).isEqualTo(TextRange.Zero)
            assertThat(textFieldValueCaptor.firstValue.composition).isEqualTo(TextRange(0, 5))
        }
    }

    @Test
    fun setComposingText_onValueChange_call_once() {
        val textFieldValueCaptor = argumentCaptor<TextFieldValue>()
        val composingText = "ABCDE"

        performEditOperation(SetComposingTextEditOp(composingText, 1))

        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(textFieldValueCaptor.capture())
            assertThat(textFieldValueCaptor.firstValue.text).isEqualTo("ABCDEabcde")
            assertThat(textFieldValueCaptor.firstValue.selection).isEqualTo(TextRange(5))
            assertThat(textFieldValueCaptor.firstValue.composition).isEqualTo(TextRange(0, 5))
        }
    }

    @Test
    fun setSelection_onValueChange_call_once() {
        // Selection change is a part of value-change in EditorModel text field
        performEditOperation(SetSelectionEditOp(1, 1))
        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(
                eq(
                    TextFieldValue(
                        "abcde",
                        TextRange(1)
                    )
                )
            )
        }
    }

    @Test
    fun clearComposition_onValueChange_call_once() {
        val textFieldValueCaptor = argumentCaptor<TextFieldValue>()
        val composingText = "ABCDE"

        performEditOperation(SetComposingTextEditOp(composingText, 1))

        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(textFieldValueCaptor.capture())
            assertThat(textFieldValueCaptor.firstValue.text).isEqualTo("ABCDEabcde")
            assertThat(textFieldValueCaptor.firstValue.selection).isEqualTo(TextRange(5))
            assertThat(textFieldValueCaptor.firstValue.composition).isEqualTo(
                TextRange(0, composingText.length)
            )
        }

        // Composition change will be reported as a change
        clearInvocations(onValueChange)
        val compositionClearCaptor = argumentCaptor<TextFieldValue>()
        performEditOperation(FinishComposingTextEditOp())
        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(compositionClearCaptor.capture())
            assertThat(compositionClearCaptor.firstValue.text).isEqualTo("ABCDEabcde")
            assertThat(compositionClearCaptor.firstValue.selection).isEqualTo(TextRange(5))
            assertThat(compositionClearCaptor.firstValue.composition).isNull()
        }
    }

    @Test
    fun deleteSurroundingText_onValueChange_call_once() {
        performEditOperation(DeleteSurroundingTextEditOp(0, 1))
        rule.runOnIdle {
            verify(onValueChange, times(1)).invoke(
                eq(
                    TextFieldValue(
                        "bcde",
                        TextRange.Zero
                    )
                )
            )
        }
    }
}
