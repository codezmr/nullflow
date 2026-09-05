package com.codezmr.nullflow.data

/**
 * One row from the profile+apps join query. A profile with N blocked apps
 * yields N rows (same id/name/isActive, different package). The tile panel
 * groups these by profile id to render an icon row per mode.
 *
 * For a profile with no blocked apps, packageName/appName are null.
 */
data class ProfileWithAppsRow(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val packageName: String?,
    val appName: String?
)
