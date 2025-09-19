package com.example.na3.util

object ResultText {
    const val BOLD = "<b>"
    const val ITALIC = "<i>"
    fun end(style: String) = when (style) {
        BOLD -> "</b>"
        ITALIC -> "</i>"
        else -> ""
    }
    fun red(s: String) = "[RED]$s[/RED]"
    fun blue(s: String) = "[BLUE]$s[/BLUE]"
}
