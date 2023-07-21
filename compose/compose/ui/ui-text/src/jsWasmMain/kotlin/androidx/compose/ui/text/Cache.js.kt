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

package androidx.compose.ui.text

// TODO Use WeakMap once available https://youtrack.jetbrains.com/issue/KT-44309
internal actual typealias WeakKeysCache<K, V> = NoCache<K, V>

internal class NoCache<K : Any, V> : Cache<K, V> {
    override fun get(key: K, loader: (K) -> V): V = loader(key)
}
