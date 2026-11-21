package com.kyuusanq3.mixauto.ui.settings

enum class AppDrawerPagingMode(val label: String) {
    SCROLL_VERTICAL("Scroll vertical"),
    SCROLL_HORIZONTAL("Scroll horizontal"),
    SWIPE_HORIZONTAL("Swipe horizontal"),
    SWIPE_VERTICAL("Swipe vertical"),
    ;

    val isSwipePaged: Boolean
        get() = this == SWIPE_HORIZONTAL || this == SWIPE_VERTICAL

    companion object {
        fun fromStored(raw: String?): AppDrawerPagingMode = when (raw) {
            null, "SCROLL", SCROLL_VERTICAL.name -> SCROLL_VERTICAL
            else -> entries.firstOrNull { it.name == raw } ?: SCROLL_VERTICAL
        }
    }
}
