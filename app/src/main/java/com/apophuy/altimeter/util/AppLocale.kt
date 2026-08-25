package com.apophuy.altimeter.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

fun currentAppLocale(): Locale =
    AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
