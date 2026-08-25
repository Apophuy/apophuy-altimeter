package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageMappingsTest {
    @Test
    fun `every explicit language round trips through its BCP 47 tag`() {
        AppLanguage.entries.filterNot { it == AppLanguage.SYSTEM }.forEach { language ->
            assertEquals(language, appLanguageFromTag(language.toLanguageTags()))
        }
    }

    @Test
    fun `system and unsupported tags are handled explicitly`() {
        assertEquals("", AppLanguage.SYSTEM.toLanguageTags())
        assertNull(appLanguageFromTag("ja"))
    }
}
