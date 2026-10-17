package koodipardik.nordprice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import koodipardik.nordprice.AppContainer
import koodipardik.nordprice.settings.Settings

val LocalContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

val LocalSettings: ProvidableCompositionLocal<Settings> = compositionLocalOf { Settings() }

@Composable
fun AppProviders(container: AppContainer, content: @Composable () -> Unit) {
    val settings by container.settings.state.collectAsStateWithLifecycle()
    CompositionLocalProvider(
        LocalContainer provides container,
        LocalSettings provides settings,
        content = content,
    )
}

/** Settings of the running app; every screen reads through this. */
@Composable
fun rememberSettings(): Settings = LocalSettings.current

/** BCP-47 tag of the in-app language, used to build date formatters. */
@Composable
fun currentLanguageTag(): String = LocalSettings.current.language.code

@Composable
fun rememberContainer(): AppContainer = LocalContainer.current
