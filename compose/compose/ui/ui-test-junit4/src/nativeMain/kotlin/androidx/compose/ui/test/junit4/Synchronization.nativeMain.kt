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

package androidx.compose.ui.test.junit4

import androidx.compose.ui.test.NanoSecondsPerMilliSecond
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.cValue
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.performBlock
import platform.Foundation.runMode
import platform.posix.nanosleep
import platform.posix.timespec

/**
 * Runs the given action on the UI thread.
 *
 * This method is blocking until the action is complete.
 */
internal actual fun <T> runOnUiThread(action: () -> T): T {
    return if (isOnUiThread()) {
        action()
    } else {
        runBlocking {
            suspendCoroutine {
                NSRunLoop.mainRunLoop.performBlock {
                    it.resumeWith(kotlin.runCatching { action() })
                }
                NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, NSDate.new()!!)
            }
        }
    }
}

/**
 * Returns if the call is made on the main thread.
 */
internal actual fun isOnUiThread(): Boolean = NSRunLoop.currentRunLoop === NSRunLoop.mainRunLoop

/**
 * Blocks the calling thread for [timeMillis] milliseconds.
 */
internal actual fun sleep(timeMillis: Long) {
    val time = cValue<timespec> {
        tv_sec = timeMillis / 1000
        tv_nsec = timeMillis.mod(1000L) * NanoSecondsPerMilliSecond
    }

    nanosleep(time, null)
}
