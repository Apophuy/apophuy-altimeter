package com.apophuy.altimeter.util

import com.apophuy.altimeter.model.AppLanguage

fun AppLanguage.toLanguageTags(): String = when (this) {
    AppLanguage.SYSTEM -> ""
    AppLanguage.ENGLISH -> "en"
    AppLanguage.RUSSIAN -> "ru"
    AppLanguage.CHINESE_SIMPLIFIED -> "zh-Hans"
    AppLanguage.HINDI -> "hi"
    AppLanguage.SPANISH -> "es"
    AppLanguage.ARABIC -> "ar"
    AppLanguage.FRENCH -> "fr"
    AppLanguage.BENGALI -> "bn"
    AppLanguage.PORTUGUESE_BRAZIL -> "pt-BR"
    AppLanguage.INDONESIAN -> "id"
    AppLanguage.URDU -> "ur"
    AppLanguage.GERMAN -> "de"
}

fun appLanguageFromTag(tag: String): AppLanguage? = when {
    tag.startsWith("zh", ignoreCase = true) -> AppLanguage.CHINESE_SIMPLIFIED
    tag.startsWith("pt-BR", ignoreCase = true) -> AppLanguage.PORTUGUESE_BRAZIL
    tag.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
    tag.startsWith("ru", ignoreCase = true) -> AppLanguage.RUSSIAN
    tag.startsWith("hi", ignoreCase = true) -> AppLanguage.HINDI
    tag.startsWith("es", ignoreCase = true) -> AppLanguage.SPANISH
    tag.startsWith("ar", ignoreCase = true) -> AppLanguage.ARABIC
    tag.startsWith("fr", ignoreCase = true) -> AppLanguage.FRENCH
    tag.startsWith("bn", ignoreCase = true) -> AppLanguage.BENGALI
    tag.startsWith("id", ignoreCase = true) -> AppLanguage.INDONESIAN
    tag.startsWith("ur", ignoreCase = true) -> AppLanguage.URDU
    tag.startsWith("de", ignoreCase = true) -> AppLanguage.GERMAN
    else -> null
}
