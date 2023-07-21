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

package androidx.compose.ui.text.intl

import platform.Foundation.*

internal class NativeLocale(val locale: NSLocale) : PlatformLocale {
    override val language: String
        get() = locale.languageCode

    override val script: String
        get() = locale.scriptCode.orEmpty()

    override val region: String
        get() = locale.countryCode ?: "US"

    // todo add tests to check IETF BCP47 on all platforms
    override fun toLanguageTag(): String = locale.localeIdentifier
}

internal actual fun createPlatformLocaleDelegate(): PlatformLocaleDelegate =
    object : PlatformLocaleDelegate {
        override val current: LocaleList
            get() = LocaleList(NSLocale.preferredLanguages.map {
                Locale(NativeLocale(NSLocale(it as String)))
            })


        override fun parseLanguageTag(languageTag: String): PlatformLocale {
            return NativeLocale(NSLocale(languageTag))
        }
    }

internal actual fun PlatformLocale.isRtl(): Boolean =
    NSLocale.characterDirectionForLanguage(language) == NSLocaleLanguageDirectionRightToLeft
