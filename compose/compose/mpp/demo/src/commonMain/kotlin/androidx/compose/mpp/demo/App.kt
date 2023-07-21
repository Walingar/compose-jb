package androidx.compose.mpp.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.mpp.demo.textfield.TextFields
import androidx.compose.mpp.demo.textfield.android.AndroidTextFieldSamples
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val MainScreen = Screen.Selection(
    "Demo",
    Screen.Example("Example1") { Example1() },
    Screen.Example("ImageViewer") { ImageViewer() },
    Screen.Example("RoundedCornerCrashOnJS") { RoundedCornerCrashOnJS() },
    Screen.Example("TextDirection") { TextDirection() },
    Screen.Example("FontFamilies") { FontFamilies() },
    Screen.Example("LottieAnimation") { LottieAnimation() },
    Screen.FullscreenExample("ApplicationLayouts") { ApplicationLayouts(it) },
    Screen.Example("GraphicsLayerSettings") { GraphicsLayerSettings() },
    Screen.Example("Blending") { Blending() },
    LazyLayouts,
    TextFields,
    AndroidTextFieldSamples,
)

sealed interface Screen {
    val title: String

    class Example(
        override val title: String,
        val content: @Composable () -> Unit
    ) : Screen

    class Selection(
        override val title: String,
        val screens: List<Screen>
    ) : Screen {
        constructor(title: String, vararg screens: Screen) : this(title, listOf(*screens))

        fun mergedWith(screens: List<Screen>): Selection {
            return Selection(title, this.screens + screens)
        }
    }

    class FullscreenExample(
        override val title: String,
        val content: @Composable (back: () -> Unit) -> Unit
    ) : Screen
}

class App(
    initialScreenName: String? = null,
    extraScreens: List<Screen> = listOf()
) {
    private val navigationStack: SnapshotStateList<Screen> =
        mutableStateListOf(MainScreen.mergedWith(extraScreens))

    init {
        if (initialScreenName != null) {
            var currentScreen = navigationStack.first()
            initialScreenName.split("/").forEach { target ->
                val selectionScreen = currentScreen as Screen.Selection
                currentScreen = selectionScreen.screens.find { it.title == target }!!
                navigationStack.add(currentScreen)
            }
        }
    }

    @Composable
    fun Content() {
        when (val screen = navigationStack.last()) {
            is Screen.Example -> {
                ExampleScaffold {
                    screen.content()
                }
            }

            is Screen.Selection -> {
                SelectionScaffold {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(screen.screens) {
                            Text(it.title, Modifier.clickable {
                                navigationStack.add(it)
                            }.padding(16.dp).fillMaxWidth())
                        }
                    }
                }
            }

            is Screen.FullscreenExample -> {
                screen.content { navigationStack.removeLast() }
            }
        }
    }

    @Composable
    private fun ExampleScaffold(
        content: @Composable () -> Unit
    ) {
        Scaffold(
            /*
            Without using TopAppBar, this is recommended approach to apply multiplatform window insets
            to Material2 Scaffold (otherwise there will be empty space above top app bar - as is here)
            */
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            topBar = {
                TopAppBar(
                    title = {
                        val title = navigationStack.drop(1)
                            .joinToString("/") { it.title }
                        Text(title)
                    },
                    navigationIcon = {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.clickable { navigationStack.removeLast() }
                        )
                    }
                )
            }
        ) { innerPadding ->
            Box(
                Modifier.fillMaxSize().padding(innerPadding)
            ) {
                content()
            }
        }
    }

    @Composable
    private fun SelectionScaffold(
        content: @Composable () -> Unit
    ) {
        Scaffold(
            topBar = {
                /*
                This is recommended approach of applying multiplatform window insets to Material2 Scaffold with using top app bar.
                By that way, it is possible to fill area above top app bar with its background - as it works out of box in android development or with Material3 Scaffold
                */
                TopAppBar(
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        .union(WindowInsets(left = 20.dp))
                        .asPaddingValues(),
                    content = {
                        CompositionLocalProvider(
                            LocalContentAlpha provides ContentAlpha.high
                        ) {
                            Row(
                                Modifier.fillMaxHeight().weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (navigationStack.size > 1) {
                                    Icon(
                                        Icons.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.clickable { navigationStack.removeLast() }
                                    )
                                    Spacer(Modifier.width(16.dp))
                                }
                                ProvideTextStyle(value = MaterialTheme.typography.h6) {
                                    Text(navigationStack.first().title)
                                }
                            }
                        }
                    }
                )
            },
        ) { innerPadding ->
            /*
            In case of applying WindowInsets as content padding, it is strongly recommended to wrap
            content of scaffold into box with these modifiers to support proper layout when device rotated
            */
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(innerPadding)
            ) {
                content()
            }
        }
    }
}
