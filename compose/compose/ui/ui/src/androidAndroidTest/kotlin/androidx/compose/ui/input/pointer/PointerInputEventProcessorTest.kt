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

package androidx.compose.ui.input.pointer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.InternalCoreApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeWrapper
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.node.Owner
import androidx.compose.ui.node.OwnerSnapshotObserver
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Uptime
import androidx.compose.ui.unit.milliseconds
import androidx.compose.ui.unit.minus
import androidx.compose.ui.platform.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.spy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// TODO(shepshapard): Write the following PointerInputEvent to PointerInputChangeEvent tests
// 2 down, 2 move, 2 up, converted correctly
// 3 down, 3 move, 3 up, converted correctly
// down, up, down, up, converted correctly
// 2 down, 1 up, same down, both up, converted correctly
// 2 down, 1 up, new down, both up, converted correctly
// new is up, throws exception

// TODO(shepshapard): Write the following hit testing tests
// 2 down, one hits, target receives correct event
// 2 down, one moves in, one out, 2 up, target receives correct event stream
// down, up, receives down and up
// down, move, up, receives all 3
// down, up, then down and misses, target receives down and up
// down, misses, moves in bounds, up, target does not receive event
// down, hits, moves out of bounds, up, target receives all events

// TODO(shepshapard): Write the following offset testing tests
// 3 simultaneous moves, offsets are correct

// TODO(shepshapard): Write the following pointer input dispatch path tests:
// down, move, up, on 2, hits all 5 passes

@MediumTest
@RunWith(AndroidJUnit4::class)
class PointerInputEventProcessorTest {

    private lateinit var root: LayoutNode
    private lateinit var pointerInputEventProcessor: PointerInputEventProcessor
    private val testOwner: TestOwner = spy()

    @Before
    fun setup() {
        root = LayoutNode(0, 0, 500, 500)
        root.attach(testOwner)
        pointerInputEventProcessor = PointerInputEventProcessor(root)
    }

    @Test
    fun process_downMoveUp_convertedCorrectlyAndTraversesAllPassesInCorrectOrder() {

        // Arrange
        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0,
            0,
            500,
            500,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val offset = Offset(100f, 200f)
        val offset2 = Offset(300f, 400f)

        val events = arrayOf(
            PointerInputEvent(8712, Uptime.Boot + 3.milliseconds, offset, true),
            PointerInputEvent(8712, Uptime.Boot + 11.milliseconds, offset2, true),
            PointerInputEvent(8712, Uptime.Boot + 13.milliseconds, offset2, false)
        )

        val down = down(8712, 3.milliseconds, offset.x, offset.y)
        val move = down.moveTo(11.milliseconds, offset2.x, offset2.y)
        val up = move.up(13.milliseconds)

        val expectedChanges = arrayOf(down, move, up)

        // Act

        events.forEach { pointerInputEventProcessor.process(it) }

        // Assert

        val log = pointerInputFilter.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log)
            .hasSize(PointerEventPass.values().size * expectedChanges.size)

        // Verify call values
        var count = 0
        expectedChanges.forEach { change ->
            PointerEventPass.values().forEach { pass ->
                val item = log[count]
                PointerEventSubject
                    .assertThat(item.pointerEvent)
                    .isStructurallyEqualTo(pointerEventOf(change))
                assertThat(item.pass).isEqualTo(pass)
                count++
            }
        }
    }

    @Test
    fun process_downHits_targetReceives() {

        // Arrange

        val childOffset = Offset(100f, 200f)
        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            100, 200, 301, 401,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val offsets = arrayOf(
            Offset(100f, 200f),
            Offset(300f, 200f),
            Offset(100f, 400f),
            Offset(300f, 400f)
        )

        val events = Array(4) { index ->
            PointerInputEvent(index, Uptime.Boot + 5.milliseconds, offsets[index], true)
        }

        val expectedChanges = Array(4) { index ->
            PointerInputChange(
                id = PointerId(index.toLong()),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offsets[index] - childOffset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offsets[index] - childOffset,
                    false
                ),
                consumed = ConsumedData()
            )
        }

        // Act

        events.forEach {
            pointerInputEventProcessor.process(it)
        }

        // Assert

        val log =
            pointerInputFilter
                .log
                .getOnPointerEventLog()
                .filter { it.pass == PointerEventPass.Initial }

        // Verify call count
        assertThat(log)
            .hasSize(expectedChanges.size)

        // Verify call values
        expectedChanges.forEachIndexed { index, change ->
            val item = log[index]
            PointerEventSubject
                .assertThat(item.pointerEvent)
                .isStructurallyEqualTo(pointerEventOf(change))
        }
    }

    @Test
    fun process_downMisses_targetDoesNotReceive() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            100, 200, 301, 401,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val offsets = arrayOf(
            Offset(99f, 200f),
            Offset(99f, 400f),
            Offset(100f, 199f),
            Offset(100f, 401f),
            Offset(300f, 199f),
            Offset(300f, 401f),
            Offset(301f, 200f),
            Offset(301f, 400f)
        )

        val events = Array(8) { index ->
            PointerInputEvent(index, Uptime.Boot + 0.milliseconds, offsets[index], true)
        }

        // Act

        events.forEach {
            pointerInputEventProcessor.process(it)
        }

        // Assert

        assertThat(pointerInputFilter.log.getOnPointerEventLog()).hasSize(0)
    }

    @Test
    fun process_downHits3of3_all3PointerNodesReceive() {
        process_partialTreeHits(3)
    }

    @Test
    fun process_downHits2of3_correct2PointerNodesReceive() {
        process_partialTreeHits(2)
    }

    @Test
    fun process_downHits1of3_onlyCorrectPointerNodesReceives() {
        process_partialTreeHits(1)
    }

    private fun process_partialTreeHits(numberOfChildrenHit: Int) {
        // Arrange

        val log = mutableListOf<LogEntry>()
        val childPointerInputFilter = PointerInputFilterMock(log)
        val middlePointerInputFilter = PointerInputFilterMock(log)
        val parentPointerInputFilter = PointerInputFilterMock(log)

        val childLayoutNode =
            LayoutNode(
                100, 100, 200, 200,
                PointerInputModifierImpl2(
                    childPointerInputFilter
                )
            )
        val middleLayoutNode: LayoutNode =
            LayoutNode(
                100, 100, 400, 400,
                PointerInputModifierImpl2(
                    middlePointerInputFilter
                )
            ).apply {
                insertAt(0, childLayoutNode)
            }
        val parentLayoutNode: LayoutNode =
            LayoutNode(
                0, 0, 500, 500,
                PointerInputModifierImpl2(
                    parentPointerInputFilter
                )
            ).apply {
                insertAt(0, middleLayoutNode)
            }
        root.insertAt(0, parentLayoutNode)

        val offset = when (numberOfChildrenHit) {
            3 -> Offset(250f, 250f)
            2 -> Offset(150f, 150f)
            1 -> Offset(50f, 50f)
            else -> throw IllegalStateException()
        }

        val event = PointerInputEvent(0, Uptime.Boot + 5.milliseconds, offset, true)

        // Act

        pointerInputEventProcessor.process(event)

        // Assert

        val filteredLog = log.getOnPointerEventLog().filter { it.pass == PointerEventPass.Initial }

        when (numberOfChildrenHit) {
            3 -> {
                assertThat(filteredLog).hasSize(3)
                assertThat(filteredLog[0].pointerInputFilter)
                    .isSameInstanceAs(parentPointerInputFilter)
                assertThat(filteredLog[1].pointerInputFilter)
                    .isSameInstanceAs(middlePointerInputFilter)
                assertThat(filteredLog[2].pointerInputFilter)
                    .isSameInstanceAs(childPointerInputFilter)
            }
            2 -> {
                assertThat(filteredLog).hasSize(2)
                assertThat(filteredLog[0].pointerInputFilter)
                    .isSameInstanceAs(parentPointerInputFilter)
                assertThat(filteredLog[1].pointerInputFilter)
                    .isSameInstanceAs(middlePointerInputFilter)
            }
            1 -> {
                assertThat(filteredLog).hasSize(1)
                assertThat(filteredLog[0].pointerInputFilter)
                    .isSameInstanceAs(parentPointerInputFilter)
            }
            else -> throw IllegalStateException()
        }
    }

    @Test
    fun process_modifiedChange_isPassedToNext() {

        // Arrange

        val expectedInput = PointerInputChange(
            id = PointerId(0),
            current = PointerInputData(
                Uptime.Boot + 5.milliseconds,
                Offset(100f, 0f),
                true
            ),
            previous = PointerInputData(
                Uptime.Boot + 3.milliseconds,
                Offset(0f, 0f),
                true
            ),
            consumed = ConsumedData(
                positionChange = Offset(0f, 0f)
            )
        )
        val expectedOutput = PointerInputChange(
            id = PointerId(0),
            current = PointerInputData(
                Uptime.Boot + 5.milliseconds,
                Offset(100f, 0f),
                true
            ),
            previous = PointerInputData(
                Uptime.Boot + 3.milliseconds,
                Offset(0f, 0f),
                true
            ),
            consumed = ConsumedData(
                positionChange = Offset(13f, 0f)
            )
        )

        val pointerInputFilter = PointerInputFilterMock(
            mutableListOf(),
            pointerEventHandler = { pointerEvent, pass, _ ->
                if (pass == PointerEventPass.Initial) {
                    pointerEvent
                        .changes
                        .first()
                        .consumePositionChange(13f, 0f)
                }
            }
        )

        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val down = PointerInputEvent(
            0,
            Uptime.Boot + 3.milliseconds,
            Offset(0f, 0f),
            true
        )
        val move = PointerInputEvent(
            0,
            Uptime.Boot + 5.milliseconds,
            Offset(100f, 0f),
            true
        )

        // Act

        pointerInputEventProcessor.process(down)
        pointerInputFilter.log.clear()
        pointerInputEventProcessor.process(move)

        // Assert

        val log = pointerInputFilter.log.getOnPointerEventLog()

        assertThat(log).hasSize(3)
        PointerInputChangeSubject
            .assertThat(log[0].pointerEvent.changes.first())
            .isStructurallyEqualTo(expectedInput)
        PointerInputChangeSubject
            .assertThat(log[1].pointerEvent.changes.first())
            .isStructurallyEqualTo(expectedOutput)
    }

    @Test
    fun process_nodesAndAdditionalOffsetIncreasinglyInset_dispatchInfoIsCorrect() {
        process_dispatchInfoIsCorrect(
            0, 0, 100, 100,
            2, 11, 100, 100,
            23, 31, 100, 100,
            43, 51,
            99, 99
        )
    }

    @Test
    fun process_nodesAndAdditionalOffsetIncreasinglyOutset_dispatchInfoIsCorrect() {
        process_dispatchInfoIsCorrect(
            0, 0, 100, 100,
            -2, -11, 100, 100,
            -23, -31, 100, 100,
            -43, -51,
            1, 1
        )
    }

    @Test
    fun process_nodesAndAdditionalOffsetNotOffset_dispatchInfoIsCorrect() {
        process_dispatchInfoIsCorrect(
            0, 0, 100, 100,
            0, 0, 100, 100,
            0, 0, 100, 100,
            0, 0,
            50, 50
        )
    }

    @Suppress("SameParameterValue")
    private fun process_dispatchInfoIsCorrect(
        pX1: Int,
        pY1: Int,
        pX2: Int,
        pY2: Int,
        mX1: Int,
        mY1: Int,
        mX2: Int,
        mY2: Int,
        cX1: Int,
        cY1: Int,
        cX2: Int,
        cY2: Int,
        aOX: Int,
        aOY: Int,
        pointerX: Int,
        pointerY: Int
    ) {

        // Arrange

        val log = mutableListOf<LogEntry>()
        val childPointerInputFilter = PointerInputFilterMock(log)
        val middlePointerInputFilter = PointerInputFilterMock(log)
        val parentPointerInputFilter = PointerInputFilterMock(log)

        val childOffset = Offset(cX1.toFloat(), cY1.toFloat())
        val childLayoutNode = LayoutNode(
            cX1, cY1, cX2, cY2,
            PointerInputModifierImpl2(
                childPointerInputFilter
            )
        )
        val middleOffset = Offset(mX1.toFloat(), mY1.toFloat())
        val middleLayoutNode: LayoutNode = LayoutNode(
            mX1, mY1, mX2, mY2,
            PointerInputModifierImpl2(
                middlePointerInputFilter
            )
        ).apply {
            insertAt(0, childLayoutNode)
        }
        val parentLayoutNode: LayoutNode = LayoutNode(
            pX1, pY1, pX2, pY2,
            PointerInputModifierImpl2(
                parentPointerInputFilter
            )
        ).apply {
            insertAt(0, middleLayoutNode)
        }

        testOwner.position = IntOffset(aOX, aOY)

        root.insertAt(0, parentLayoutNode)

        val additionalOffset = IntOffset(aOX, aOY)

        val offset = Offset(pointerX.toFloat(), pointerY.toFloat())

        val down = PointerInputEvent(0, Uptime.Boot + 7.milliseconds, offset, true)

        val expectedPointerInputChanges = arrayOf(
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - additionalOffset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - additionalOffset,
                    false
                ),
                consumed = ConsumedData()
            ),
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - middleOffset - additionalOffset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - middleOffset - additionalOffset,
                    false
                ),
                consumed = ConsumedData()
            ),
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - middleOffset - childOffset - additionalOffset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset - middleOffset - childOffset - additionalOffset,
                    false
                ),
                consumed = ConsumedData()
            )
        )

        val expectedSizes = arrayOf(
            IntSize(pX2 - pX1, pY2 - pY1),
            IntSize(mX2 - mX1, mY2 - mY1),
            IntSize(cX2 - cX1, cY2 - cY1)
        )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        val filteredLog = log.getOnPointerEventLog()

        // Verify call count
        assertThat(filteredLog).hasSize(PointerEventPass.values().size * 3)

        // Verify call values
        filteredLog.verifyOnPointerEventCall(
            0,
            parentPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[0]),
            PointerEventPass.Initial,
            expectedSizes[0]
        )
        filteredLog.verifyOnPointerEventCall(
            1,
            middlePointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[1]),
            PointerEventPass.Initial,
            expectedSizes[1]
        )
        filteredLog.verifyOnPointerEventCall(
            2,
            childPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[2]),
            PointerEventPass.Initial,
            expectedSizes[2]
        )
        filteredLog.verifyOnPointerEventCall(
            3,
            childPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[2]),
            PointerEventPass.Main,
            expectedSizes[2]
        )
        filteredLog.verifyOnPointerEventCall(
            4,
            middlePointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[1]),
            PointerEventPass.Main,
            expectedSizes[1]
        )
        filteredLog.verifyOnPointerEventCall(
            5,
            parentPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[0]),
            PointerEventPass.Main,
            expectedSizes[0]
        )
        filteredLog.verifyOnPointerEventCall(
            6,
            parentPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[0]),
            PointerEventPass.Final,
            expectedSizes[0]
        )
        filteredLog.verifyOnPointerEventCall(
            7,
            middlePointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[1]),
            PointerEventPass.Final,
            expectedSizes[1]
        )
        filteredLog.verifyOnPointerEventCall(
            8,
            childPointerInputFilter,
            pointerEventOf(expectedPointerInputChanges[2]),
            PointerEventPass.Final,
            expectedSizes[2]
        )
    }

    /**
     * This test creates a layout of this shape:
     *
     *  -------------
     *  |     |     |
     *  |  t  |     |
     *  |     |     |
     *  |-----|     |
     *  |           |
     *  |     |-----|
     *  |     |     |
     *  |     |  t  |
     *  |     |     |
     *  -------------
     *
     * Where there is one child in the top right, and one in the bottom left, and 2 down touches,
     * one in the top left and one in the bottom right.
     */
    @Test
    fun process_2DownOn2DifferentPointerNodes_hitAndDispatchInfoAreCorrect() {

        // Arrange

        val log = mutableListOf<LogEntry>()
        val childPointerInputFilter1 = PointerInputFilterMock(log)
        val childPointerInputFilter2 = PointerInputFilterMock(log)

        val childLayoutNode1 =
            LayoutNode(
                0, 0, 50, 50,
                PointerInputModifierImpl2(
                    childPointerInputFilter1
                )
            )
        val childLayoutNode2 =
            LayoutNode(
                50, 50, 100, 100,
                PointerInputModifierImpl2(
                    childPointerInputFilter2
                )
            )
        root.apply {
            insertAt(0, childLayoutNode1)
            insertAt(0, childLayoutNode2)
        }

        val offset1 = Offset(25f, 25f)
        val offset2 = Offset(75f, 75f)

        val down = PointerInputEvent(
            Uptime.Boot + 5.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 5.milliseconds, offset1, true),
                PointerInputEventData(1, Uptime.Boot + 5.milliseconds, offset2, true)
            )
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset1,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset1,
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange2 =
            PointerInputChange(
                id = PointerId(1),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset2 - Offset(50f, 50f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset2 - Offset(50f, 50f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        // Verify call count

        val child1Log =
            log.getOnPointerEventLog().filter { it.pointerInputFilter === childPointerInputFilter1 }
        val child2Log =
            log.getOnPointerEventLog().filter { it.pointerInputFilter === childPointerInputFilter2 }
        assertThat(child1Log).hasSize(PointerEventPass.values().size)
        assertThat(child2Log).hasSize(PointerEventPass.values().size)

        // Verify call values

        val expectedBounds = IntSize(50, 50)

        child1Log.verifyOnPointerEventCall(
            0,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Initial,
            expectedBounds
        )
        child1Log.verifyOnPointerEventCall(
            1,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Main,
            expectedBounds
        )
        child1Log.verifyOnPointerEventCall(
            2,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Final,
            expectedBounds
        )

        child2Log.verifyOnPointerEventCall(
            0,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Initial,
            expectedBounds
        )
        child2Log.verifyOnPointerEventCall(
            1,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Main,
            expectedBounds
        )
        child2Log.verifyOnPointerEventCall(
            2,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Final,
            expectedBounds
        )
    }

    /**
     * This test creates a layout of this shape:
     *
     *  ---------------
     *  | t      |    |
     *  |        |    |
     *  |  |-------|  |
     *  |  | t     |  |
     *  |  |       |  |
     *  |  |       |  |
     *  |--|  |-------|
     *  |  |  | t     |
     *  |  |  |       |
     *  |  |  |       |
     *  |  |--|       |
     *  |     |       |
     *  ---------------
     *
     * There are 3 staggered children and 3 down events, the first is on child 1, the second is on
     * child 2 in a space that overlaps child 1, and the third is in a space that overlaps both
     * child 2.
     */
    @Test
    fun process_3DownOnOverlappingPointerNodes_hitAndDispatchInfoAreCorrect() {

        val log = mutableListOf<LogEntry>()
        val childPointerInputFilter1 = PointerInputFilterMock(log)
        val childPointerInputFilter2 = PointerInputFilterMock(log)
        val childPointerInputFilter3 = PointerInputFilterMock(log)

        val childLayoutNode1 = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(
                childPointerInputFilter1
            )
        )
        val childLayoutNode2 = LayoutNode(
            50, 50, 150, 150,
            PointerInputModifierImpl2(
                childPointerInputFilter2
            )
        )
        val childLayoutNode3 = LayoutNode(
            100, 100, 200, 200,
            PointerInputModifierImpl2(
                childPointerInputFilter3
            )
        )

        root.apply {
            insertAt(0, childLayoutNode1)
            insertAt(1, childLayoutNode2)
            insertAt(2, childLayoutNode3)
        }

        val offset1 = Offset(25f, 25f)
        val offset2 = Offset(75f, 75f)
        val offset3 = Offset(125f, 125f)

        val down = PointerInputEvent(
            Uptime.Boot + 5.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 5.milliseconds, offset1, true),
                PointerInputEventData(1, Uptime.Boot + 5.milliseconds, offset2, true),
                PointerInputEventData(2, Uptime.Boot + 5.milliseconds, offset3, true)
            )
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset1,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset1,
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange2 =
            PointerInputChange(
                id = PointerId(1),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset2 - Offset(50f, 50f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset2 - Offset(50f, 50f),
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange3 =
            PointerInputChange(
                id = PointerId(2),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset3 - Offset(100f, 100f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    offset3 - Offset(100f, 100f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        val child1Log =
            log.getOnPointerEventLog().filter { it.pointerInputFilter === childPointerInputFilter1 }
        val child2Log =
            log.getOnPointerEventLog().filter { it.pointerInputFilter === childPointerInputFilter2 }
        val child3Log =
            log.getOnPointerEventLog().filter { it.pointerInputFilter === childPointerInputFilter3 }
        assertThat(child1Log).hasSize(PointerEventPass.values().size)
        assertThat(child2Log).hasSize(PointerEventPass.values().size)
        assertThat(child3Log).hasSize(PointerEventPass.values().size)

        // Verify call values

        val expectedBounds = IntSize(100, 100)

        child1Log.verifyOnPointerEventCall(
            0,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Initial,
            expectedBounds
        )
        child1Log.verifyOnPointerEventCall(
            1,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Main,
            expectedBounds
        )
        child1Log.verifyOnPointerEventCall(
            2,
            null,
            pointerEventOf(expectedChange1),
            PointerEventPass.Final,
            expectedBounds
        )

        child2Log.verifyOnPointerEventCall(
            0,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Initial,
            expectedBounds
        )
        child2Log.verifyOnPointerEventCall(
            1,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Main,
            expectedBounds
        )
        child2Log.verifyOnPointerEventCall(
            2,
            null,
            pointerEventOf(expectedChange2),
            PointerEventPass.Final,
            expectedBounds
        )

        child3Log.verifyOnPointerEventCall(
            0,
            null,
            pointerEventOf(expectedChange3),
            PointerEventPass.Initial,
            expectedBounds
        )
        child3Log.verifyOnPointerEventCall(
            1,
            null,
            pointerEventOf(expectedChange3),
            PointerEventPass.Main,
            expectedBounds
        )
        child3Log.verifyOnPointerEventCall(
            2,
            null,
            pointerEventOf(expectedChange3),
            PointerEventPass.Final,
            expectedBounds
        )
    }

    /**
     * This test creates a layout of this shape:
     *
     *  ---------------
     *  |             |
     *  |      t      |
     *  |             |
     *  |  |-------|  |
     *  |  |       |  |
     *  |  |   t   |  |
     *  |  |       |  |
     *  |  |-------|  |
     *  |             |
     *  |      t      |
     *  |             |
     *  ---------------
     *
     * There are 3 staggered children and 3 down events, the first is on child 1, the second is on
     * child 2 in a space that overlaps child 1, and the third is in a space that overlaps both
     * child 2.
     */
    @Test
    fun process_3DownOnFloatingPointerNodeV_hitAndDispatchInfoAreCorrect() {

        val childPointerInputFilter1 = PointerInputFilterMock()
        val childPointerInputFilter2 = PointerInputFilterMock()

        val childLayoutNode1 = LayoutNode(
            0, 0, 100, 150,
            PointerInputModifierImpl2(
                childPointerInputFilter1
            )
        )
        val childLayoutNode2 = LayoutNode(
            25, 50, 75, 100,
            PointerInputModifierImpl2(
                childPointerInputFilter2
            )
        )

        root.apply {
            insertAt(0, childLayoutNode1)
            insertAt(1, childLayoutNode2)
        }

        val offset1 = Offset(50f, 25f)
        val offset2 = Offset(50f, 75f)
        val offset3 = Offset(50f, 125f)

        val down = PointerInputEvent(
            Uptime.Boot + 7.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 7.milliseconds, offset1, true),
                PointerInputEventData(1, Uptime.Boot + 7.milliseconds, offset2, true),
                PointerInputEventData(2, Uptime.Boot + 7.milliseconds, offset3, true)
            )
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1,
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange2 =
            PointerInputChange(
                id = PointerId(1),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset2 - Offset(25f, 50f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset2 - Offset(25f, 50f),
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange3 =
            PointerInputChange(
                id = PointerId(2),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset3,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset3,
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        val log1 = childPointerInputFilter1.log.getOnPointerEventLog()
        val log2 = childPointerInputFilter2.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log1).hasSize(PointerEventPass.values().size)
        assertThat(log2).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log1.verifyOnPointerEventCall(
                index,
                null,
                pointerEventOf(expectedChange1, expectedChange3),
                pass,
                IntSize(100, 150)
            )
            log2.verifyOnPointerEventCall(
                index,
                null,
                pointerEventOf(expectedChange2),
                pass,
                IntSize(50, 50)
            )
        }
    }

    /**
     * This test creates a layout of this shape:
     *
     *  -----------------
     *  |               |
     *  |   |-------|   |
     *  |   |       |   |
     *  | t |   t   | t |
     *  |   |       |   |
     *  |   |-------|   |
     *  |               |
     *  -----------------
     *
     * There are 3 staggered children and 3 down events, the first is on child 1, the second is on
     * child 2 in a space that overlaps child 1, and the third is in a space that overlaps both
     * child 2.
     */
    @Test
    fun process_3DownOnFloatingPointerNodeH_hitAndDispatchInfoAreCorrect() {

        val childPointerInputFilter1 = PointerInputFilterMock()
        val childPointerInputFilter2 = PointerInputFilterMock()

        val childLayoutNode1 = LayoutNode(
            0, 0, 150, 100,
            PointerInputModifierImpl2(
                childPointerInputFilter1
            )
        )
        val childLayoutNode2 = LayoutNode(
            50, 25, 100, 75,
            PointerInputModifierImpl2(
                childPointerInputFilter2
            )
        )

        root.apply {
            insertAt(0, childLayoutNode1)
            insertAt(1, childLayoutNode2)
        }

        val offset1 = Offset(25f, 50f)
        val offset2 = Offset(75f, 50f)
        val offset3 = Offset(125f, 50f)

        val down = PointerInputEvent(
            Uptime.Boot + 11.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 11.milliseconds, offset1, true),
                PointerInputEventData(1, Uptime.Boot + 11.milliseconds, offset2, true),
                PointerInputEventData(2, Uptime.Boot + 11.milliseconds, offset3, true)
            )
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset1,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset1,
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange2 =
            PointerInputChange(
                id = PointerId(1),
                current = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset2 - Offset(50f, 25f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset2 - Offset(50f, 25f),
                    false
                ),
                consumed = ConsumedData()
            )
        val expectedChange3 =
            PointerInputChange(
                id = PointerId(2),
                current = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset3,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset3,
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        val log1 = childPointerInputFilter1.log.getOnPointerEventLog()
        val log2 = childPointerInputFilter2.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log1).hasSize(PointerEventPass.values().size)
        assertThat(log2).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log1.verifyOnPointerEventCall(
                index,
                null,
                pointerEventOf(expectedChange1, expectedChange3),
                pass,
                IntSize(150, 100)
            )
            log2.verifyOnPointerEventCall(
                index,
                null,
                pointerEventOf(expectedChange2),
                pass,
                IntSize(50, 50)
            )
        }
    }

    /**
     * This test creates a layout of this shape:
     *     0   1   2   3   4
     *   .........   .........
     * 0 .     t .   . t     .
     *   .   |---|---|---|   .
     * 1 . t | t |   | t | t .
     *   ....|---|   |---|....
     * 2     |           |
     *   ....|---|   |---|....
     * 3 . t | t |   | t | t .
     *   .   |---|---|---|   .
     * 4 .     t .   . t     .
     *   .........   .........
     *
     * 4 LayoutNodes with PointerInputModifiers that are clipped by their parent LayoutNode. 4
     * touches touch just inside the parent LayoutNode and inside the child LayoutNodes. 8
     * touches touch just outside the parent LayoutNode but inside the child LayoutNodes.
     *
     * Because layout node bounds are not used to clip pointer input hit testing, all pointers
     * should hit.
     */
    @Test
    fun process_4DownInClippedAreaOfLnsWithPims_onlyCorrectPointersHit() {

        // Arrange

        val pointerInputFilterTopLeft = PointerInputFilterMock()
        val pointerInputFilterTopRight = PointerInputFilterMock()
        val pointerInputFilterBottomLeft = PointerInputFilterMock()
        val pointerInputFilterBottomRight = PointerInputFilterMock()

        val layoutNodeTopLeft = LayoutNode(
            -1, -1, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilterTopLeft
            )
        )
        val layoutNodeTopRight = LayoutNode(
            2, -1, 4, 1,
            PointerInputModifierImpl2(
                pointerInputFilterTopRight
            )
        )
        val layoutNodeBottomLeft = LayoutNode(
            -1, 2, 1, 4,
            PointerInputModifierImpl2(
                pointerInputFilterBottomLeft
            )
        )
        val layoutNodeBottomRight = LayoutNode(
            2, 2, 4, 4,
            PointerInputModifierImpl2(
                pointerInputFilterBottomRight
            )
        )

        val parentLayoutNode = LayoutNode(1, 1, 4, 4).apply {
            insertAt(0, layoutNodeTopLeft)
            insertAt(1, layoutNodeTopRight)
            insertAt(2, layoutNodeBottomLeft)
            insertAt(3, layoutNodeBottomRight)
        }
        root.apply {
            insertAt(0, parentLayoutNode)
        }
        val offsetsTopLeft =
            listOf(
                Offset(0f, 1f),
                Offset(1f, 0f),
                Offset(1f, 1f)
            )

        val offsetsTopRight =
            listOf(
                Offset(3f, 0f),
                Offset(3f, 1f),
                Offset(4f, 1f)
            )

        val offsetsBottomLeft =
            listOf(
                Offset(0f, 3f),
                Offset(1f, 3f),
                Offset(1f, 4f)
            )

        val offsetsBottomRight =
            listOf(
                Offset(3f, 3f),
                Offset(3f, 4f),
                Offset(4f, 3f)
            )

        val allOffsets = offsetsTopLeft + offsetsTopRight + offsetsBottomLeft + offsetsBottomRight

        val pointerInputEvent =
            PointerInputEvent(
                Uptime.Boot + 11.milliseconds,
                (allOffsets.indices).map {
                    PointerInputEventData(it, Uptime.Boot + 11.milliseconds, allOffsets[it], true)
                }
            )

        // Act

        pointerInputEventProcessor.process(pointerInputEvent)

        // Assert

        val expectedChangesTopLeft =
            (offsetsTopLeft.indices).map {
                PointerInputChange(
                    id = PointerId(it.toLong()),
                    current = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsTopLeft[it].x,
                            offsetsTopLeft[it].y
                        ),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsTopLeft[it].x,
                            offsetsTopLeft[it].y
                        ),
                        false
                    ),
                    consumed = ConsumedData()
                )
            }

        val expectedChangesTopRight =
            (offsetsTopLeft.indices).map {
                PointerInputChange(
                    id = PointerId(it.toLong() + 3),
                    current = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsTopRight[it].x - 3f,
                            offsetsTopRight[it].y
                        ),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsTopRight[it].x - 3f,
                            offsetsTopRight[it].y
                        ),
                        false
                    ),
                    consumed = ConsumedData()
                )
            }

        val expectedChangesBottomLeft =
            (offsetsTopLeft.indices).map {
                PointerInputChange(
                    id = PointerId(it.toLong() + 6),
                    current = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsBottomLeft[it].x,
                            offsetsBottomLeft[it].y - 3f
                        ),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsBottomLeft[it].x,
                            offsetsBottomLeft[it].y - 3f
                        ),
                        false
                    ),
                    consumed = ConsumedData()
                )
            }

        val expectedChangesBottomRight =
            (offsetsTopLeft.indices).map {
                PointerInputChange(
                    id = PointerId(it.toLong() + 9),
                    current = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsBottomRight[it].x - 3f,
                            offsetsBottomRight[it].y - 3f
                        ),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        Offset(
                            offsetsBottomRight[it].x - 3f,
                            offsetsBottomRight[it].y - 3f
                        ),
                        false
                    ),
                    consumed = ConsumedData()
                )
            }

        // Verify call values

        val logTopLeft = pointerInputFilterTopLeft.log.getOnPointerEventLog()
        val logTopRight = pointerInputFilterTopRight.log.getOnPointerEventLog()
        val logBottomLeft = pointerInputFilterBottomLeft.log.getOnPointerEventLog()
        val logBottomRight = pointerInputFilterBottomRight.log.getOnPointerEventLog()

        PointerEventPass.values().forEachIndexed { index, pass ->
            logTopLeft.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChangesTopLeft.toTypedArray()),
                expectedPass = pass
            )
            logTopRight.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChangesTopRight.toTypedArray()),
                expectedPass = pass
            )
            logBottomLeft.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChangesBottomLeft.toTypedArray()),
                expectedPass = pass
            )
            logBottomRight.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChangesBottomRight.toTypedArray()),
                expectedPass = pass
            )
        }
    }

    /**
     * This test creates a layout of this shape:
     *
     *   |---|
     *   |tt |
     *   |t  |
     *   |---|t
     *       tt
     *
     *   But where the additional offset suggest something more like this shape.
     *
     *   tt
     *   t|---|
     *    |  t|
     *    | tt|
     *    |---|
     *
     *   Without the additional offset, it would be expected that only the top left 3 pointers would
     *   hit, but with the additional offset, only the bottom right 3 hit.
     */
    @Test
    fun process_rootIsOffset_onlyCorrectPointersHit() {

        // Arrange
        val singlePointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 2, 2,
            PointerInputModifierImpl2(
                singlePointerInputFilter
            )
        )
        root.apply {
            insertAt(0, layoutNode)
        }
        val offsetsThatHit =
            listOf(
                Offset(2f, 2f),
                Offset(2f, 1f),
                Offset(1f, 2f)
            )
        val offsetsThatMiss =
            listOf(
                Offset(0f, 0f),
                Offset(0f, 1f),
                Offset(1f, 0f)
            )
        val allOffsets = offsetsThatHit + offsetsThatMiss
        val pointerInputEvent =
            PointerInputEvent(
                Uptime.Boot + 11.milliseconds,
                (allOffsets.indices).map {
                    PointerInputEventData(it, Uptime.Boot + 11.milliseconds, allOffsets[it], true)
                }
            )
        testOwner.position = IntOffset(1, 1)

        // Act

        pointerInputEventProcessor.process(pointerInputEvent)

        // Assert

        val expectedChanges =
            (offsetsThatHit.indices).map {
                PointerInputChange(
                    id = PointerId(it.toLong()),
                    current = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        offsetsThatHit[it] - Offset(1f, 1f),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 11.milliseconds,
                        offsetsThatHit[it] - Offset(1f, 1f),
                        false
                    ),
                    consumed = ConsumedData()
                )
            }

        val log = singlePointerInputFilter.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChanges.toTypedArray()),
                expectedPass = pass
            )
        }
    }

    @Test
    fun process_downOn3NestedPointerInputModifiers_hitAndDispatchInfoAreCorrect() {

        val pointerInputFilter1 = PointerInputFilterMock()
        val pointerInputFilter2 = PointerInputFilterMock()
        val pointerInputFilter3 = PointerInputFilterMock()

        val modifier = PointerInputModifierImpl2(pointerInputFilter1) then
            PointerInputModifierImpl2(pointerInputFilter2) then
            PointerInputModifierImpl2(pointerInputFilter3)

        val layoutNode = LayoutNode(
            25, 50, 75, 100,
            modifier
        )

        root.apply {
            insertAt(0, layoutNode)
        }

        val offset1 = Offset(50f, 75f)

        val down = PointerInputEvent(
            Uptime.Boot + 7.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 7.milliseconds, offset1, true)
            )
        )

        val expectedChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1 - Offset(25f, 50f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1 - Offset(25f, 50f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert

        val log1 = pointerInputFilter1.log.getOnPointerEventLog()
        val log2 = pointerInputFilter2.log.getOnPointerEventLog()
        val log3 = pointerInputFilter3.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log1).hasSize(PointerEventPass.values().size)
        assertThat(log2).hasSize(PointerEventPass.values().size)
        assertThat(log3).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log1.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange),
                expectedPass = pass,
                expectedBounds = IntSize(50, 50)
            )
            log2.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange),
                expectedPass = pass,
                expectedBounds = IntSize(50, 50)
            )
            log3.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange),
                expectedPass = pass,
                expectedBounds = IntSize(50, 50)
            )
        }
    }

    @Test
    fun process_downOnDeeplyNestedPointerInputModifier_hitAndDispatchInfoAreCorrect() {

        val pointerInputFilter = PointerInputFilterMock()

        val layoutNode1 =
            LayoutNode(
                1, 5, 500, 500,
                PointerInputModifierImpl2(pointerInputFilter)
            )
        val layoutNode2: LayoutNode = LayoutNode(2, 6, 500, 500).apply {
            insertAt(0, layoutNode1)
        }
        val layoutNode3: LayoutNode = LayoutNode(3, 7, 500, 500).apply {
            insertAt(0, layoutNode2)
        }
        val layoutNode4: LayoutNode = LayoutNode(4, 8, 500, 500).apply {
            insertAt(0, layoutNode3)
        }
        root.apply {
            insertAt(0, layoutNode4)
        }

        val offset1 = Offset(499f, 499f)

        val downEvent = PointerInputEvent(
            Uptime.Boot + 7.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 7.milliseconds, offset1, true)
            )
        )

        val expectedChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1 - Offset(1f + 2f + 3f + 4f, 5f + 6f + 7f + 8f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset1 - Offset(1f + 2f + 3f + 4f, 5f + 6f + 7f + 8f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(downEvent)

        // Assert

        val log = pointerInputFilter.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange),
                expectedPass = pass,
                expectedBounds = IntSize(499, 495)
            )
        }
    }

    @Test
    fun process_downOnComplexPointerAndLayoutNodePath_hitAndDispatchInfoAreCorrect() {

        val pointerInputFilter1 = PointerInputFilterMock()
        val pointerInputFilter2 = PointerInputFilterMock()
        val pointerInputFilter3 = PointerInputFilterMock()
        val pointerInputFilter4 = PointerInputFilterMock()

        val layoutNode1 = LayoutNode(
            1, 6, 500, 500,
            PointerInputModifierImpl2(pointerInputFilter1)
                then PointerInputModifierImpl2(pointerInputFilter2)
        )
        val layoutNode2: LayoutNode = LayoutNode(2, 7, 500, 500).apply {
            insertAt(0, layoutNode1)
        }
        val layoutNode3 =
            LayoutNode(
                3, 8, 500, 500,
                PointerInputModifierImpl2(pointerInputFilter3)
                    then PointerInputModifierImpl2(pointerInputFilter4)
            ).apply {
                insertAt(0, layoutNode2)
            }

        val layoutNode4: LayoutNode = LayoutNode(4, 9, 500, 500).apply {
            insertAt(0, layoutNode3)
        }
        val layoutNode5: LayoutNode = LayoutNode(5, 10, 500, 500).apply {
            insertAt(0, layoutNode4)
        }
        root.apply {
            insertAt(0, layoutNode5)
        }

        val offset1 = Offset(499f, 499f)

        val downEvent = PointerInputEvent(
            Uptime.Boot + 3.milliseconds,
            listOf(
                PointerInputEventData(0, Uptime.Boot + 3.milliseconds, offset1, true)
            )
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 3.milliseconds,
                    offset1 - Offset(
                        1f + 2f + 3f + 4f + 5f,
                        6f + 7f + 8f + 9f + 10f
                    ),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 3.milliseconds,
                    offset1 - Offset(
                        1f + 2f + 3f + 4f + 5f,
                        6f + 7f + 8f + 9f + 10f
                    ),
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedChange2 =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 3.milliseconds,
                    offset1 - Offset(3f + 4f + 5f, 8f + 9f + 10f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 3.milliseconds,
                    offset1 - Offset(3f + 4f + 5f, 8f + 9f + 10f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(downEvent)

        // Assert

        val log1 = pointerInputFilter1.log.getOnPointerEventLog()
        val log2 = pointerInputFilter2.log.getOnPointerEventLog()
        val log3 = pointerInputFilter3.log.getOnPointerEventLog()
        val log4 = pointerInputFilter4.log.getOnPointerEventLog()

        // Verify call count
        assertThat(log1).hasSize(PointerEventPass.values().size)
        assertThat(log2).hasSize(PointerEventPass.values().size)
        assertThat(log3).hasSize(PointerEventPass.values().size)
        assertThat(log4).hasSize(PointerEventPass.values().size)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log1.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange1),
                expectedPass = pass,
                expectedBounds = IntSize(499, 494)
            )
            log2.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange1),
                expectedPass = pass,
                expectedBounds = IntSize(499, 494)
            )
            log3.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange2),
                expectedPass = pass,
                expectedBounds = IntSize(497, 492)
            )
            log4.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange2),
                expectedPass = pass,
                expectedBounds = IntSize(497, 492)
            )
        }
    }

    @Test
    fun process_downOnFullyOverlappingPointerInputModifiers_onlyTopPointerInputModifierReceives() {

        val pointerInputFilter1 = PointerInputFilterMock()
        val pointerInputFilter2 = PointerInputFilterMock()

        val layoutNode1 = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(
                pointerInputFilter1
            )
        )
        val layoutNode2 = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(
                pointerInputFilter2
            )
        )

        root.apply {
            insertAt(0, layoutNode1)
            insertAt(1, layoutNode2)
        }

        val down = PointerInputEvent(
            1, Uptime.Boot + 0.milliseconds, Offset(50f, 50f), true
        )

        // Act

        pointerInputEventProcessor.process(down)

        // Assert
        assertThat(pointerInputFilter2.log.getOnPointerEventLog()).hasSize(3)
        assertThat(pointerInputFilter1.log.getOnPointerEventLog()).hasSize(0)
    }

    @Test
    fun process_downOnPointerInputModifierInLayoutNodeWithNoSize_downNotReceived() {

        val pointerInputFilter1 = PointerInputFilterMock()

        val layoutNode1 = LayoutNode(
            0, 0, 0, 0,
            PointerInputModifierImpl2(pointerInputFilter1)
        )

        root.apply {
            insertAt(0, layoutNode1)
        }

        val down = PointerInputEvent(
            1, Uptime.Boot + 0.milliseconds, Offset(0f, 0f), true
        )

        // Act
        pointerInputEventProcessor.process(down)

        // Assert
        assertThat(pointerInputFilter1.log.getOnPointerEventLog()).hasSize(0)
    }

    // Cancel Handlers

    @Test
    fun processCancel_noPointers_doesntCrash() {
        pointerInputEventProcessor.processCancel()
    }

    @Test
    fun processCancel_downThenCancel_pimOnlyReceivesCorrectDownThenCancel() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()

        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(pointerInputFilter)
        )

        root.insertAt(0, layoutNode)

        val pointerInputEvent =
            PointerInputEvent(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(250f, 250f),
                true
            )

        val expectedChange =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(250f, 250f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(250f, 250f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(pointerInputEvent)
        pointerInputEventProcessor.processCancel()

        // Assert

        val log = pointerInputFilter.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size + 1)

        // Verify call values
        PointerEventPass.values().forEachIndexed { index, pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange),
                expectedPass = pass
            )
        }
        log.verifyOnCancelCall(PointerEventPass.values().size)
    }

    @Test
    fun processCancel_downDownOnSamePimThenCancel_pimOnlyReceivesCorrectChangesThenCancel() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()

        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val pointerInputEvent1 =
            PointerInputEvent(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(200f, 200f),
                true
            )

        val pointerInputEvent2 =
            PointerInputEvent(
                Uptime.Boot + 10.milliseconds,
                listOf(
                    PointerInputEventData(
                        7,
                        Uptime.Boot + 10.milliseconds,
                        Offset(200f, 200f),
                        true
                    ),
                    PointerInputEventData(
                        9,
                        Uptime.Boot + 10.milliseconds,
                        Offset(300f, 300f),
                        true
                    )
                )
            )

        val expectedChanges1 =
            listOf(
                PointerInputChange(
                    id = PointerId(7),
                    current = PointerInputData(
                        Uptime.Boot + 5.milliseconds,
                        Offset(200f, 200f),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 5.milliseconds,
                        Offset(200f, 200f),
                        false
                    ),
                    consumed = ConsumedData()
                )
            )

        val expectedChanges2 =
            listOf(
                PointerInputChange(
                    id = PointerId(7),
                    current = PointerInputData(
                        Uptime.Boot + 10.milliseconds,
                        Offset(200f, 200f),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 5.milliseconds,
                        Offset(200f, 200f),
                        true
                    ),
                    consumed = ConsumedData()
                ),
                PointerInputChange(
                    id = PointerId(9),
                    current = PointerInputData(
                        Uptime.Boot + 10.milliseconds,
                        Offset(300f, 300f),
                        true
                    ),
                    previous = PointerInputData(
                        Uptime.Boot + 10.milliseconds,
                        Offset(300f, 300f),
                        false
                    ),
                    consumed = ConsumedData()
                )
            )

        // Act

        pointerInputEventProcessor.process(pointerInputEvent1)
        pointerInputEventProcessor.process(pointerInputEvent2)
        pointerInputEventProcessor.processCancel()

        // Assert

        val log = pointerInputFilter.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size * 2 + 1)

        // Verify call values
        var index = 0
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChanges1.toTypedArray()),
                expectedPass = pass
            )
            index++
        }
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(*expectedChanges2.toTypedArray()),
                expectedPass = pass
            )
            index++
        }
        log.verifyOnCancelCall(index)
    }

    @Test
    fun processCancel_downOn2DifferentPimsThenCancel_pimsOnlyReceiveCorrectDownsThenCancel() {

        // Arrange

        val pointerInputFilter1 = PointerInputFilterMock()
        val layoutNode1 = LayoutNode(
            0, 0, 199, 199,
            PointerInputModifierImpl2(pointerInputFilter1)
        )

        val pointerInputFilter2 = PointerInputFilterMock()
        val layoutNode2 = LayoutNode(
            200, 200, 399, 399,
            PointerInputModifierImpl2(pointerInputFilter2)
        )

        root.insertAt(0, layoutNode1)
        root.insertAt(1, layoutNode2)

        val pointerInputEventData1 =
            PointerInputEventData(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(100f, 100f),
                true
            )

        val pointerInputEventData2 =
            PointerInputEventData(
                9,
                Uptime.Boot + 5.milliseconds,
                Offset(300f, 300f),
                true
            )

        val pointerInputEvent = PointerInputEvent(
            Uptime.Boot + 5.milliseconds,
            listOf(pointerInputEventData1, pointerInputEventData2)
        )

        val expectedChange1 =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(100f, 100f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(100f, 100f),
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedChange2 =
            PointerInputChange(
                id = PointerId(9),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(100f, 100f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(100f, 100f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(pointerInputEvent)
        pointerInputEventProcessor.processCancel()

        // Assert

        val log1 =
            pointerInputFilter1.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }
        val log2 =
            pointerInputFilter2.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log1).hasSize(PointerEventPass.values().size + 1)
        assertThat(log2).hasSize(PointerEventPass.values().size + 1)

        // Verify call values
        var index = 0
        PointerEventPass.values().forEach { pass ->
            log1.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange1),
                expectedPass = pass
            )
            log2.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedChange2),
                expectedPass = pass
            )
            index++
        }
        log1.verifyOnCancelCall(index)
        log2.verifyOnCancelCall(index)
    }

    @Test
    fun processCancel_downMoveCancel_pimOnlyReceivesCorrectDownMoveCancel() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(pointerInputFilter)
        )

        root.insertAt(0, layoutNode)

        val down =
            PointerInputEvent(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(200f, 200f),
                true
            )

        val move =
            PointerInputEvent(
                7,
                Uptime.Boot + 10.milliseconds,
                Offset(300f, 300f),
                true
            )

        val expectedDown =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedMove =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 10.milliseconds,
                    Offset(300f, 300f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    true
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)
        pointerInputEventProcessor.process(move)
        pointerInputEventProcessor.processCancel()

        // Assert

        val log = pointerInputFilter.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size * 2 + 1)

        // Verify call values
        var index = 0
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedDown),
                expectedPass = pass
            )
            index++
        }
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedMove),
                expectedPass = pass
            )
            index++
        }
        log.verifyOnCancelCall(index)
    }

    @Test
    fun processCancel_downCancelMoveUp_pimOnlyReceivesCorrectDownCancel() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(pointerInputFilter)
        )

        root.insertAt(0, layoutNode)

        val down =
            PointerInputEvent(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(200f, 200f),
                true
            )

        val expectedDown =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)
        pointerInputEventProcessor.processCancel()

        // Assert

        val log = pointerInputFilter.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size + 1)

        // Verify call values
        var index = 0
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedDown),
                expectedPass = pass
            )
            index++
        }
        log.verifyOnCancelCall(index)
    }

    @Test
    fun processCancel_downCancelDown_pimOnlyReceivesCorrectDownCancelDown() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 500, 500,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.insertAt(0, layoutNode)

        val down1 =
            PointerInputEvent(
                7,
                Uptime.Boot + 5.milliseconds,
                Offset(200f, 200f),
                true
            )

        val down2 =
            PointerInputEvent(
                7,
                Uptime.Boot + 10.milliseconds,
                Offset(200f, 200f),
                true
            )

        val expectedDown1 =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 5.milliseconds,
                    Offset(200f, 200f),
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedDown2 =
            PointerInputChange(
                id = PointerId(7),
                current = PointerInputData(
                    Uptime.Boot + 10.milliseconds,
                    Offset(200f, 200f),
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 10.milliseconds,
                    Offset(200f, 200f),
                    false
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down1)
        pointerInputEventProcessor.processCancel()
        pointerInputEventProcessor.process(down2)

        // Assert

        val log = pointerInputFilter.log.filter { it is OnPointerEventEntry || it is OnCancelEntry }

        // Verify call count
        assertThat(log).hasSize(PointerEventPass.values().size * 2 + 1)

        // Verify call values
        var index = 0
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedDown1),
                expectedPass = pass
            )
            index++
        }
        log.verifyOnCancelCall(index)
        index++
        PointerEventPass.values().forEach { pass ->
            log.verifyOnPointerEventCall(
                index = index,
                expectedEvent = pointerEventOf(expectedDown2),
                expectedPass = pass
            )
            index++
        }
    }

    @Test
    fun process_layoutNodeRemovedDuringInput_correctPointerInputChangesReceived() {

        // Arrange

        val childPointerInputFilter = PointerInputFilterMock()
        val childLayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(childPointerInputFilter)
        )

        val parentPointerInputFilter = PointerInputFilterMock()
        val parentLayoutNode: LayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(parentPointerInputFilter)
        ).apply {
            insertAt(0, childLayoutNode)
        }

        root.insertAt(0, parentLayoutNode)

        val offset = Offset(50f, 50f)

        val down = PointerInputEvent(0, Uptime.Boot + 7.milliseconds, offset, true)
        val up = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, offset, false)

        val expectedDownChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedUpChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset,
                    false
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    true
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)
        parentLayoutNode.removeAt(0, 1)
        pointerInputEventProcessor.process(up)

        // Assert

        val parentLog = parentPointerInputFilter.log.getOnPointerEventLog()
        val childLog = childPointerInputFilter.log.getOnPointerEventLog()

        // Verify call count
        assertThat(parentLog).hasSize(PointerEventPass.values().size * 2)
        assertThat(childLog).hasSize(PointerEventPass.values().size)

        // Verify call values

        parentLog.verifyOnPointerEventCall(
            index = 0,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Initial
        )
        parentLog.verifyOnPointerEventCall(
            index = 1,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Main
        )
        parentLog.verifyOnPointerEventCall(
            index = 2,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Final
        )
        parentLog.verifyOnPointerEventCall(
            index = 3,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Initial
        )
        parentLog.verifyOnPointerEventCall(
            index = 4,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Main
        )
        parentLog.verifyOnPointerEventCall(
            index = 5,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Final
        )

        childLog.verifyOnPointerEventCall(
            index = 0,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Initial
        )
        childLog.verifyOnPointerEventCall(
            index = 1,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Main
        )
        childLog.verifyOnPointerEventCall(
            index = 2,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Final
        )
    }

    @Test
    fun process_layoutNodeRemovedDuringInput_cancelDispatchedToCorrectPointerInputModifierImpl2() {

        // Arrange

        val childPointerInputFilter = PointerInputFilterMock()
        val childLayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(childPointerInputFilter)
        )

        val parentPointerInputFilter = PointerInputFilterMock()
        val parentLayoutNode: LayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(parentPointerInputFilter)
        ).apply {
            insertAt(0, childLayoutNode)
        }

        root.insertAt(0, parentLayoutNode)

        val down =
            PointerInputEvent(0, Uptime.Boot + 7.milliseconds, Offset(50f, 50f), true)

        val up = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(50f, 50f), false)

        // Act

        pointerInputEventProcessor.process(down)
        parentLayoutNode.removeAt(0, 1)
        pointerInputEventProcessor.process(up)

        // Assert
        assertThat(childPointerInputFilter.log.getOnCancelLog()).hasSize(1)
        assertThat(parentPointerInputFilter.log.getOnCancelLog()).hasSize(0)
    }

    @Test
    fun process_pointerInputModifierRemovedDuringInput_correctPointerInputChangesReceived() {

        // Arrange

        val childPointerInputFilter = PointerInputFilterMock()
        val childLayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(
                childPointerInputFilter
            )
        )

        val parentPointerInputFilter = PointerInputFilterMock()
        val parentLayoutNode: LayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(
                parentPointerInputFilter
            )
        ).apply {
            insertAt(0, childLayoutNode)
        }

        root.insertAt(0, parentLayoutNode)

        val offset = Offset(50f, 50f)

        val down = PointerInputEvent(0, Uptime.Boot + 7.milliseconds, offset, true)
        val up = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, offset, false)

        val expectedDownChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    true
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    false
                ),
                consumed = ConsumedData()
            )

        val expectedUpChange =
            PointerInputChange(
                id = PointerId(0),
                current = PointerInputData(
                    Uptime.Boot + 11.milliseconds,
                    offset,
                    false
                ),
                previous = PointerInputData(
                    Uptime.Boot + 7.milliseconds,
                    offset,
                    true
                ),
                consumed = ConsumedData()
            )

        // Act

        pointerInputEventProcessor.process(down)
        childLayoutNode.modifier = Modifier
        pointerInputEventProcessor.process(up)

        // Assert

        val parentLog = parentPointerInputFilter.log.getOnPointerEventLog()
        val childLog = childPointerInputFilter.log.getOnPointerEventLog()

        // Verify call count
        assertThat(parentLog).hasSize(PointerEventPass.values().size * 2)
        assertThat(childLog).hasSize(PointerEventPass.values().size)

        // Verify call values

        parentLog.verifyOnPointerEventCall(
            index = 0,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Initial
        )
        parentLog.verifyOnPointerEventCall(
            index = 1,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Main
        )
        parentLog.verifyOnPointerEventCall(
            index = 2,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Final
        )
        parentLog.verifyOnPointerEventCall(
            index = 3,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Initial
        )
        parentLog.verifyOnPointerEventCall(
            index = 4,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Main
        )
        parentLog.verifyOnPointerEventCall(
            index = 5,
            expectedEvent = pointerEventOf(expectedUpChange),
            expectedPass = PointerEventPass.Final
        )

        childLog.verifyOnPointerEventCall(
            index = 0,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Initial
        )
        childLog.verifyOnPointerEventCall(
            index = 1,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Main
        )
        childLog.verifyOnPointerEventCall(
            index = 2,
            expectedEvent = pointerEventOf(expectedDownChange),
            expectedPass = PointerEventPass.Final
        )
    }

    @Test
    fun process_pointerInputModifierRemovedDuringInput_cancelDispatchedToCorrectPim() {

        // Arrange

        val childPointerInputFilter = PointerInputFilterMock()
        val childLayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(childPointerInputFilter)
        )

        val parentPointerInputFilter = PointerInputFilterMock()
        val parentLayoutNode: LayoutNode = LayoutNode(
            0, 0, 100, 100,
            PointerInputModifierImpl2(parentPointerInputFilter)
        ).apply {
            insertAt(0, childLayoutNode)
        }

        root.insertAt(0, parentLayoutNode)

        val down =
            PointerInputEvent(0, Uptime.Boot + 7.milliseconds, Offset(50f, 50f), true)

        val up =
            PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(50f, 50f), false)

        // Act

        pointerInputEventProcessor.process(down)
        childLayoutNode.modifier = Modifier
        pointerInputEventProcessor.process(up)

        // Assert
        assertThat(childPointerInputFilter.log.getOnCancelLog()).hasSize(1)
        assertThat(parentPointerInputFilter.log.getOnCancelLog()).hasSize(0)
    }

    @Test
    fun process_downNoPointerInputModifiers_nothingInteractedWithAndNoMovementConsumed() {
        val pointerInputEvent =
            PointerInputEvent(0, Uptime.Boot + 7.milliseconds, Offset(0f, 0f), true)

        val result: ProcessResult = pointerInputEventProcessor.process(pointerInputEvent)

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = false,
                anyMovementConsumed = false
            )
        )
    }

    @Test
    fun process_downNoPointerInputModifiersHit_nothingInteractedWithAndNoMovementConsumed() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()

        val layoutNode = LayoutNode(
            0, 0, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )

        root.apply {
            insertAt(0, layoutNode)
        }

        val offsets =
            listOf(
                Offset(-1f, 0f),
                Offset(0f, -1f),
                Offset(1f, 0f),
                Offset(0f, 1f)
            )
        val pointerInputEvent =
            PointerInputEvent(
                Uptime.Boot + 11.milliseconds,
                (offsets.indices).map {
                    PointerInputEventData(it, Uptime.Boot + 11.milliseconds, offsets[it], true)
                }
            )

        // Act

        val result: ProcessResult = pointerInputEventProcessor.process(pointerInputEvent)

        // Assert

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = false,
                anyMovementConsumed = false
            )
        )
    }

    @Test
    fun process_downPointerInputModifierHit_somethingInteractedWithAndNoMovementConsumed() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )
        root.apply { insertAt(0, layoutNode) }
        val pointerInputEvent =
            PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(0f, 0f), true)

        // Act

        val result = pointerInputEventProcessor.process(pointerInputEvent)

        // Assert

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = true,
                anyMovementConsumed = false
            )
        )
    }

    @Test
    fun process_downHitsPifRemovedPointerMoves_nothingInteractedWithAndNoMovementConsumed() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )
        root.apply { insertAt(0, layoutNode) }
        val down = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(0f, 0f), true)
        pointerInputEventProcessor.process(down)
        val move = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(1f, 0f), true)

        // Act

        root.removeAt(0, 1)
        val result = pointerInputEventProcessor.process(move)

        // Assert

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = false,
                anyMovementConsumed = false
            )
        )
    }

    @Test
    fun process_downHitsPointerMovesNothingConsumed_somethingInteractedWithAndNoMovementConsumed() {

        // Arrange

        val pointerInputFilter = PointerInputFilterMock()
        val layoutNode = LayoutNode(
            0, 0, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )
        root.apply { insertAt(0, layoutNode) }
        val down = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(0f, 0f), true)
        pointerInputEventProcessor.process(down)
        val move = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(1f, 0f), true)

        // Act

        val result = pointerInputEventProcessor.process(move)

        // Assert

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = true,
                anyMovementConsumed = false
            )
        )
    }

    @Test
    fun process_downHitsPointerMovementConsumed_somethingInteractedWithAndMovementConsumed() {

        // Arrange

        val pointerInputFilter: PointerInputFilter =
            PointerInputFilterMock(
                pointerEventHandler = { pointerEvent, pass, _ ->
                    if (pass == PointerEventPass.Initial) {
                        pointerEvent.changes.forEach {
                            it.consumePositionChange(1f, 0f)
                        }
                    }
                }
            )

        val layoutNode = LayoutNode(
            0, 0, 1, 1,
            PointerInputModifierImpl2(
                pointerInputFilter
            )
        )
        root.apply { insertAt(0, layoutNode) }
        val down = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(0f, 0f), true)
        pointerInputEventProcessor.process(down)
        val move = PointerInputEvent(0, Uptime.Boot + 11.milliseconds, Offset(1f, 0f), true)

        // Act

        val result = pointerInputEventProcessor.process(move)

        // Assert

        assertThat(result).isEqualTo(
            ProcessResult(
                dispatchedToAPointerInputModifier = true,
                anyMovementConsumed = true
            )
        )
    }

    @Test
    fun processResult_trueTrue_propValuesAreCorrect() {
        val processResult1 = ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = true
        )
        assertThat(processResult1.dispatchedToAPointerInputModifier).isTrue()
        assertThat(processResult1.anyMovementConsumed).isTrue()
    }

    @Test
    fun processResult_trueFalse_propValuesAreCorrect() {
        val processResult1 = ProcessResult(
            dispatchedToAPointerInputModifier = true,
            anyMovementConsumed = false
        )
        assertThat(processResult1.dispatchedToAPointerInputModifier).isTrue()
        assertThat(processResult1.anyMovementConsumed).isFalse()
    }

    @Test
    fun processResult_falseTrue_propValuesAreCorrect() {
        val processResult1 = ProcessResult(
            dispatchedToAPointerInputModifier = false,
            anyMovementConsumed = true
        )
        assertThat(processResult1.dispatchedToAPointerInputModifier).isFalse()
        assertThat(processResult1.anyMovementConsumed).isTrue()
    }

    @Test
    fun processResult_falseFalse_propValuesAreCorrect() {
        val processResult1 = ProcessResult(
            dispatchedToAPointerInputModifier = false,
            anyMovementConsumed = false
        )
        assertThat(processResult1.dispatchedToAPointerInputModifier).isFalse()
        assertThat(processResult1.anyMovementConsumed).isFalse()
    }
}

abstract class TestOwner : Owner {
    var position: IntOffset? = null

    override val root: LayoutNode
        get() = LayoutNode()

    override fun calculatePosition(): IntOffset {
        return position ?: IntOffset.Zero
    }
}

private class PointerInputModifierImpl2(override val pointerInputFilter: PointerInputFilter) :
    PointerInputModifier

private fun LayoutNode(x: Int, y: Int, x2: Int, y2: Int, modifier: Modifier = Modifier) =
    LayoutNode().apply {
        this.modifier = modifier
        measureBlocks = object : LayoutNode.NoIntrinsicsMeasureBlocks("not supported") {
            override fun measure(
                measureScope: MeasureScope,
                measurables: List<Measurable>,
                constraints: Constraints
            ): MeasureResult =
                measureScope.layout(x2 - x, y2 - y) {}
        }
        attach(mockOwner())
        layoutState = LayoutNode.LayoutState.NeedsRemeasure
        remeasure(Constraints())
        var wrapper: LayoutNodeWrapper? = outerLayoutNodeWrapper
        while (wrapper != null) {
            wrapper.measureResult = innerLayoutNodeWrapper.measureResult
            wrapper = (wrapper as? LayoutNodeWrapper)?.wrapped
        }
        place(x, y)
        detach()
    }

private fun mockOwner(
    position: IntOffset = IntOffset.Zero,
    targetRoot: LayoutNode = LayoutNode()
): Owner = MockOwner(position, targetRoot)

@OptIn(ExperimentalComposeUiApi::class, InternalCoreApi::class)
private class MockOwner(
    private val position: IntOffset,
    private val targetRoot: LayoutNode
) : Owner {
    override fun calculatePosition(): IntOffset = position
    override fun requestFocus(): Boolean = false
    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean = false
    override val root: LayoutNode
        get() = targetRoot
    override val hapticFeedBack: HapticFeedback
        get() = TODO("Not yet implemented")
    override val clipboardManager: ClipboardManager
        get() = TODO("Not yet implemented")
    override val textToolbar: TextToolbar
        get() = TODO("Not yet implemented")
    override val autofillTree: AutofillTree
        get() = TODO("Not yet implemented")
    override val autofill: Autofill?
        get() = null
    override val density: Density
        get() = Density(1f)
    override val semanticsOwner: SemanticsOwner
        get() = TODO("Not yet implemented")
    override val textInputService: TextInputService
        get() = TODO("Not yet implemented")
    override val focusManager: FocusManager
        get() = TODO("Not yet implemented")
    override val windowManager: WindowManager
        get() = TODO("Not yet implemented")
    override val fontLoader: Font.ResourceLoader
        get() = TODO("Not yet implemented")
    override val layoutDirection: LayoutDirection
        get() = LayoutDirection.Ltr
    override var showLayoutBounds: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}

    override fun onRequestMeasure(layoutNode: LayoutNode) {
    }

    override fun onRequestRelayout(layoutNode: LayoutNode) {
    }

    override fun onAttach(node: LayoutNode) {
    }

    override fun onDetach(node: LayoutNode) {
    }

    override fun measureAndLayout() {
    }

    override fun createLayer(
        drawBlock: (Canvas) -> Unit,
        invalidateParentLayer: () -> Unit
    ): OwnedLayer {
        TODO("Not yet implemented")
    }

    override fun onSemanticsChange() {
    }

    override val measureIteration: Long
        get() = 0

    override val viewConfiguration: ViewConfiguration
        get() = TODO("Not yet implemented")
    override val snapshotObserver = OwnerSnapshotObserver { it.invoke() }
}

private fun List<LogEntry>.verifyOnPointerEventCall(
    index: Int,
    expectedPif: PointerInputFilter? = null,
    expectedEvent: PointerEvent,
    expectedPass: PointerEventPass,
    expectedBounds: IntSize? = null
) {
    val logEntry = this[index]
    assertThat(logEntry).isInstanceOf(OnPointerEventEntry::class.java)
    val entry = logEntry as OnPointerEventEntry
    if (expectedPif != null) {
        assertThat(entry.pointerInputFilter).isSameInstanceAs(expectedPif)
    }
    PointerEventSubject
        .assertThat(entry.pointerEvent)
        .isStructurallyEqualTo(expectedEvent)
    assertThat(entry.pass).isEqualTo(expectedPass)
    if (expectedBounds != null) {
        assertThat(entry.bounds).isEqualTo(expectedBounds)
    }
}

private fun List<LogEntry>.verifyOnCancelCall(
    index: Int
) {
    val logEntry = this[index]
    assertThat(logEntry).isInstanceOf(OnCancelEntry::class.java)
}