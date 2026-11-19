package com.kyuusanq3.mixauto.ui.settings

enum class AppDrawerPagingMode(val label: String) {
    SCROLL("Scroll"),
    SWIPE_HORIZONTAL("Swipe horizontal"),
    SWIPE_VERTICAL("Swipe vertical"),
    ;

    companion object {
        fun fromStored(raw: String?): AppDrawerPagingMode =
            entries.firstOrNull { it.name == raw } ?: SCROLL
    }
}
