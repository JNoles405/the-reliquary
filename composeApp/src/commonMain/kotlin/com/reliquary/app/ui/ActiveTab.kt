package com.reliquary.app.ui

import com.reliquary.app.domain.CustomTab
import com.reliquary.app.domain.MediaType

/** Which tab is selected — a built-in media type or a user-defined custom tab. */
sealed interface ActiveTab {
    val title: String
    val supportsBarcode: Boolean

    /** media_type column value stored on items created under this tab. */
    val mediaTypeName: String

    /** custom_tab_id for items under this tab (null for built-ins). */
    val customTabId: String?

    data class Builtin(val type: MediaType) : ActiveTab {
        override val title get() = type.displayName
        override val supportsBarcode get() = type.supportsBarcode
        override val mediaTypeName get() = type.name
        override val customTabId get() = null
    }

    data class Custom(val tab: CustomTab) : ActiveTab {
        override val title get() = tab.name
        override val supportsBarcode get() = tab.supportsBarcode
        override val mediaTypeName get() = "CUSTOM"
        override val customTabId get() = tab.id
    }
}
