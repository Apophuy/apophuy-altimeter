package com.apophuy.altimeter

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.junit4.createComposeRule
import com.apophuy.altimeter.model.ThemeMode
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeModeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun automaticThemeFollowsSystemAndManualSelectionOverridesIt() {
        val systemDark = mutableStateOf(false)
        val mode = mutableStateOf(ThemeMode.SYSTEM)
        var renderedDark = false
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(systemDark.value)) {
                AltimeterTheme(mode.value) {
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    SideEffect { renderedDark = dark }
                    Box { }
                }
            }
        }
        compose.runOnIdle { assertFalse(renderedDark); systemDark.value = true }
        compose.runOnIdle { assertTrue(renderedDark); mode.value = ThemeMode.LIGHT }
        compose.runOnIdle { assertFalse(renderedDark); mode.value = ThemeMode.DARK; systemDark.value = false }
        compose.runOnIdle { assertTrue(renderedDark); mode.value = ThemeMode.SYSTEM }
        compose.runOnIdle { assertFalse(renderedDark) }
    }
}
