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

package androidx.compose.foundation.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.onDispose
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class LazyItemStateRestoration {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun visibleItemsStateRestored() {
        val restorationTester = StateRestorationTester(rule)
        var counter0 = 1
        var counter1 = 10
        var counter2 = 100
        var realState = arrayOf(0, 0, 0)
        restorationTester.setContent {
            LazyColumn {
                item {
                    realState[0] = rememberSavedInstanceState { counter0++ }
                    Box(Modifier.size(1.dp))
                }
                items((1..2).toList()) {
                    if (it == 1) {
                        realState[1] = rememberSavedInstanceState { counter1++ }
                    } else {
                        realState[2] = rememberSavedInstanceState { counter2++ }
                    }
                    Box(Modifier.size(1.dp))
                }
            }
        }

        rule.runOnIdle {
            assertThat(realState[0]).isEqualTo(1)
            assertThat(realState[1]).isEqualTo(10)
            assertThat(realState[2]).isEqualTo(100)
            realState = arrayOf(0, 0, 0)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertThat(realState[0]).isEqualTo(1)
            assertThat(realState[1]).isEqualTo(10)
            assertThat(realState[2]).isEqualTo(100)
        }
    }

    @Test
    fun itemsStateRestoredWhenWeScrolledBackToIt() {
        val restorationTester = StateRestorationTester(rule)
        var counter0 = 1
        lateinit var state: LazyListState
        var itemDisposed = false
        var realState = 0
        restorationTester.setContent {
            LazyColumn(
                Modifier.size(20.dp),
                state = rememberLazyListState().also { state = it }
            ) {
                items((0..1).toList()) {
                    if (it == 0) {
                        realState = rememberSavedInstanceState { counter0++ }
                        onDispose {
                            itemDisposed = true
                        }
                    }
                    Box(Modifier.size(30.dp))
                }
            }
        }

        rule.runOnIdle {
            assertThat(realState).isEqualTo(1)
            runBlocking {
                state.snapToItemIndex(1, 5)
            }
        }

        rule.runOnIdle {
            assertThat(itemDisposed).isEqualTo(true)
            realState = 0
            runBlocking {
                state.snapToItemIndex(0, 0)
            }
        }

        rule.runOnIdle {
            assertThat(realState).isEqualTo(1)
        }
    }

    @Test
    fun itemsStateRestoredWhenWeScrolledRestoredAndScrolledBackTo() {
        val restorationTester = StateRestorationTester(rule)
        var counter0 = 1
        var counter1 = 10
        lateinit var state: LazyListState
        var realState = arrayOf(0, 0)
        restorationTester.setContent {
            LazyColumn(
                Modifier.size(20.dp),
                state = rememberLazyListState().also { state = it }
            ) {
                items((0..1).toList()) {
                    if (it == 0) {
                        realState[0] = rememberSavedInstanceState { counter0++ }
                    } else {
                        realState[1] = rememberSavedInstanceState { counter1++ }
                    }
                    Box(Modifier.size(30.dp))
                }
            }
        }

        rule.runOnIdle {
            assertThat(realState[0]).isEqualTo(1)
            runBlocking {
                state.snapToItemIndex(1, 5)
            }
        }

        rule.runOnIdle {
            assertThat(realState[1]).isEqualTo(10)
            realState = arrayOf(0, 0)
        }

        restorationTester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertThat(realState[1]).isEqualTo(10)
            runBlocking {
                state.snapToItemIndex(0, 0)
            }
        }

        rule.runOnIdle {
            assertThat(realState[0]).isEqualTo(1)
        }
    }

    @Test
    fun nestedLazy_itemsStateRestoredWhenWeScrolledBackToIt() {
        val restorationTester = StateRestorationTester(rule)
        var counter0 = 1
        lateinit var state: LazyListState
        var itemDisposed = false
        var realState = 0
        restorationTester.setContent {
            LazyColumn(
                Modifier.size(20.dp),
                state = rememberLazyListState().also { state = it }
            ) {
                items((0..1).toList()) {
                    if (it == 0) {
                        LazyRow {
                            item {
                                realState = rememberSavedInstanceState { counter0++ }
                                onDispose {
                                    itemDisposed = true
                                }
                                Box(Modifier.size(30.dp))
                            }
                        }
                    } else {
                        Box(Modifier.size(30.dp))
                    }
                }
            }
        }

        rule.runOnIdle {
            assertThat(realState).isEqualTo(1)
            runBlocking {
                state.snapToItemIndex(1, 5)
            }
        }

        rule.runOnIdle {
            assertThat(itemDisposed).isEqualTo(true)
            realState = 0
            runBlocking {
                state.snapToItemIndex(0, 0)
            }
        }

        rule.runOnIdle {
            assertThat(realState).isEqualTo(1)
        }
    }
}
