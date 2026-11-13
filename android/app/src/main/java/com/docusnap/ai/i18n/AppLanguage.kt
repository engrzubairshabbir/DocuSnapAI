package com.docusnap.ai.i18n

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val flag: String,
    val isRtl: Boolean = false
) {
    ENGLISH("en", "English", "🇺🇸", false),
    JAPANESE("ja", "日本語", "🇯🇵", false),
    KOREAN("ko", "한국어", "🇰🇷", false),
    CHINESE("zh", "中文", "🇨🇳", false),
    SPANISH("es", "Español", "🇪🇸", false),
    FRENCH("fr", "Français", "🇫🇷", false),
    GERMAN("de", "Deutsch", "🇩🇪", false),
    ARABIC("ar", "العربية", "🇸🇦", true),
    HINDI("hi", "हिंदी", "🇮🇳", false),
    URDU("ur", "اردو", "🇵🇰", true);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
