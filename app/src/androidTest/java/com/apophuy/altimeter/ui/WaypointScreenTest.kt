package com.apophuy.altimeter.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.apophuy.altimeter.R
import com.apophuy.altimeter.data.local.WaypointEntity
import com.apophuy.altimeter.model.Coordinates
import com.apophuy.altimeter.model.LiveReading
import com.apophuy.altimeter.model.PositionSource
import com.apophuy.altimeter.ui.theme.AltimeterTheme
import org.junit.Rule
import org.junit.Test

class WaypointScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun waypointActionsAreAccessibleIconButtons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = System.currentTimeMillis()
        val waypoint = WaypointEntity(
            id = 1L,
            name = "Camp",
            latitude = 55.75,
            longitude = 37.61,
            horizontalAccuracyMeters = 5f,
            createdAtMillis = now,
        )
        compose.setContent {
            AltimeterTheme {
                WaypointScreen(
                    state = AppUiState(
                        live = LiveReading(
                            coordinates = Coordinates(
                                latitude = 55.751,
                                longitude = 37.611,
                                horizontalAccuracyMeters = 5f,
                                timestampMillis = now,
                                source = PositionSource.GNSS,
                            ),
                        ),
                        waypoints = listOf(waypoint),
                    ),
                    onSave = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onSelect = {},
                    onShare = {},
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.rename)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.share)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.delete))
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.delete_waypoint_title)).assertIsDisplayed()
    }
}
