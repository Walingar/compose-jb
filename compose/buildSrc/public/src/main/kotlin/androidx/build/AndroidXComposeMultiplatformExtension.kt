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

package androidx.build

/**
  * This extension provides the default multiplatform target configurations and
  * source set dependencies.
  *
  * Such configurations tend to be repeated in each subproject build.gradles.
  * So have it in one place to reduce clutter.
  *
  */
abstract class AndroidXComposeMultiplatformExtension {
    /**
      * Provides the default target configuration and source set dependencies for android.
      */
    abstract fun android(): Unit

    /**
      * Provides the default target configuration and source set dependencies
      * for desktop/jvm.
      */
    abstract fun desktop(): Unit

    /**
      * Provides the default target configuration and source set dependencies
      * for javascript.
      */
    abstract fun js(): Unit

    /**
      * Provides the default target configuration and source set dependencies
      * for wasm.
      */
    abstract fun wasm(): Unit

    /**
      * Provides the default target configuration and source set dependencies
      * for all darwin native targets.
      */
    abstract fun darwin(): Unit

    /**
     * Configures native compilation tasks with flags to link required frameworks
     */
    abstract fun configureDarwinFlags(): Unit

    abstract val isKotlinWasmTargetEnabled: Boolean
}