package com.codezmr.nullflow.data

/**
 * A focus profile paired with the number of apps blocked in it.
 * Used by the Quick Settings tile panel to render each mode row
 * ("Deep Work · 3 apps shielded").
 */
data class ProfileWithCount(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val appCount: Int
)
