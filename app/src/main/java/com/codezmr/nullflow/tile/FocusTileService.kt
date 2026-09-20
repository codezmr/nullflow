package com.codezmr.nullflow.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.codezmr.nullflow.AppLog
import com.codezmr.nullflow.MainActivity
import com.codezmr.nullflow.R
import com.codezmr.nullflow.data.FocusDatabase
import com.codezmr.nullflow.data.Settings
import com.codezmr.nullflow.ui.tile.TileFocusPanel
import com.codezmr.nullflow.vpn.FocusVpnService
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "GhostShield" - the NullFlow Quick Settings tile.
 *
 * Lives in the swipe-down notification shade. Tapping it opens a native,
 * Compose-powered Focus Panel (BottomSheetDialog) directly over the current
 * app, so the user can flip the shield and switch modes in ~0.5s without
 * opening the main app.
 *
 * The tile reflects shield state: blue glow (STATE_ACTIVE) when a shield
 * session is running, grey (STATE_INACTIVE) otherwise.
 *
 * LIFECYCLE (per verified best practice):
 *  - We do NOT implement LifecycleOwner/ViewModelStoreOwner on the service.
 *    Instead the ComposeView delegates to the BottomSheetDialog's OWN built-in
 *    lifecycle owners (setViewTree*Owner(dialog)). The dialog reaches RESUMED
 *    when shown and DESTROYED when dismissed, so Compose state/flows run and
 *    tear down automatically.
 *  - ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed guarantees the
 *    Composition is disposed when the dialog is destroyed (no leaks).
 *  - We keep a nullable dialog ref and dismiss it in onDestroy() to avoid a
 *    Window leak if the OS kills the service while the panel is open.
 */
class FocusTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserver: Job? = null
    private var tileDialog: BottomSheetDialog? = null
    private var panelOwner: PanelOwner? = null

    override fun onStartListening() {
        super.onStartListening()
        AppLog.d("FocusTileService.onStartListening")
        // Reflect the current shield state on the tile immediately, then keep
        // it in sync reactively as Room changes.
        updateTileState()
        observeShieldState()
    }

    override fun onStopListening() {
        super.onStopListening()
        AppLog.d("FocusTileService.onStopListening")
        stateObserver?.cancel()
        stateObserver = null
    }

    override fun onClick() {
        super.onClick()
        AppLog.d("FocusTileService.onClick")

        // Onboarding gate: the shield must NOT be usable before the user has
        // completed the Welcome/Consent screen (which is where the VPN consent
        // is granted). If not onboarded, skip the panel entirely and force the
        // main app (which shows Welcome) - collapsing the shade.
        if (!Settings.get(this).hasOnboarded) {
            AppLog.d("not onboarded → forcing MainActivity (Welcome screen)")
            openMainActivity()
            return
        }

        // If the device is locked, we must unlock before showing a dialog.
        if (isLocked) {
            AppLog.d("device locked → unlockAndRun")
            unlockAndRun { showFocusPanel() }
        } else {
            showFocusPanel()
        }
    }

    /**
     * Show the Compose Focus Panel as a native system overlay via
     * TileService.showDialog(). The dialog is a Material BottomSheetDialog
     * whose content is a ComposeView rendering TileFocusPanel.
     */
    private fun showFocusPanel() {
        // Avoid stacking multiple panels.
        tileDialog?.dismiss()

        val dialog = BottomSheetDialog(this, R.style.NullFlow_BottomSheet_Dialog)
        tileDialog = dialog

        // Material 1.11.0's BottomSheetDialog is NOT a ViewModelStoreOwner
        // (it's Dialog-based, not ComponentDialog), so we drive the ComposeView's
        // lifecycle with a lightweight PanelOwner and destroy it on dismiss.
        val owner = PanelOwner()
        panelOwner = owner
        owner.start()

        val composeView = ComposeView(this).apply {
            // Dispose the Composition when the owner's lifecycle is destroyed.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TileFocusPanel(
                    onOpenApp = {
                        // Dismiss the panel and open the main app.
                        dialog.dismiss()
                        openMainActivity()
                    }
                )
            }
        }

        // Must set content BEFORE attaching the tags (so the internal container
        // is built).
        dialog.setContentView(composeView)

        // CRITICAL FIX (API 34/35 TileService window bug): TileService.showDialog()
        // uses a TYPE_QS_DIALOG window that strips/doesn't propagate lifecycle
        // tags to children. Compose's WindowRecomposer searches up to the window
        // root (decorView) and throws "ViewTreeLifecycleOwner not found" if the
        // tag isn't there. So we attach the owner to the DECORVIEW (the root),
        // NOT to the ComposeView.
        dialog.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(owner)
            decorView.setViewTreeViewModelStoreOwner(owner)
            decorView.setViewTreeSavedStateRegistryOwner(owner)
        }

        // Quick Settings overlay fix: force the sheet fully expanded so the
        // Compose view isn't clipped to a half-height peek.
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        dialog.setOnDismissListener {
            AppLog.d("FocusPanel dismissed → destroying owner")
            owner.destroy()
            tileDialog = null
            panelOwner = null
        }

        AppLog.d("showDialog(FocusPanel)")
        showDialog(dialog)
    }

    private fun openMainActivity() {
        // startActivityAndCollapse collapses the shade and launches the app.
        // On Android 15 the Intent overload is disallowed - it requires a
        // PendingIntent. FLAG_ACTIVITY_NEW_TASK is required from a Service.
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            startActivityAndCollapse(pendingIntent)
            AppLog.d("startActivityAndCollapse(PendingIntent) → MainActivity")
        } catch (e: Exception) {
            AppLog.e("startActivityAndCollapse FAILED, falling back to startActivity", e)
            startActivity(intent)
        }
    }

    /** Set the tile's visual state from the live shield state. */
    private fun updateTileState() {
        val tile = qsTile ?: return
        val active = FocusVpnService.isShieldRunning
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // When a shield is active, show the active mode name so the user sees
        // which mode is engaged at a glance (e.g. "NullFlow · Study"). When
        // off, just the app name.
        tile.label = if (active) activeTileLabel() else getString(R.string.tile_label)
        tile.icon = tileIcon(active)
        tile.updateTile()
        AppLog.d("updateTileState: state=${if (active) "ACTIVE" else "INACTIVE"} label='${tile.label}'")
    }

    /** "AppName · ModeName" when a shield is active, else just the app name. */
    private fun activeTileLabel(): String {
        val appName = getString(R.string.tile_label)
        return try {
            val dao = FocusDatabase.get(this).focusDao()
            val active = kotlinx.coroutines.runBlocking { dao.observeActiveProfile().first() }
            if (active != null && active.name.isNotBlank()) "$appName · ${active.name}" else appName
        } catch (e: Exception) {
            appName
        }
    }

    /** Tinted icon: blue when active, neutral when inactive. */
    private fun tileIcon(active: Boolean): Icon {
        val base = ContextCompat.getDrawable(this, R.drawable.ic_hero_toggle)
        if (base == null) {
            return Icon.createWithResource(this, R.drawable.ic_hero_toggle)
        }
        val color = if (active)
            ContextCompat.getColor(this, R.color.tile_active)
        else
            ContextCompat.getColor(this, R.color.tile_inactive)
        // Mutate() so we don't mutate the shared cached drawable, then tint it.
        val drawable = base.mutate()
        drawable.setTint(color)
        // Render the tinted drawable into a bitmap for the Icon.
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return Icon.createWithBitmap(bitmap)
    }

    /** Keep the tile in sync with Room (active profile + running session). */
    private fun observeShieldState() {
        stateObserver?.cancel()
        stateObserver = tileScope.launch {
            val dao = FocusDatabase.get(this@FocusTileService).focusDao()
            // Combine active-profile + running-session into a single "is on" signal.
            combine(
                dao.observeActiveProfile(),
                dao.observeRunningSession()
            ) { active, session -> active != null && session != null }
                .collect { isOn ->
                    if (isOn != FocusVpnService.isShieldRunning) {
                        updateTileState()
                    }
                }
        }
    }

    override fun onDestroy() {
        AppLog.d("FocusTileService.onDestroy")
        stateObserver?.cancel()
        tileScope.cancel()
        // Prevent a Window leak if the system kills the service mid-panel.
        tileDialog?.dismiss()
        tileDialog = null
        panelOwner?.destroy()
        panelOwner = null
        super.onDestroy()
    }
}

/**
 * A minimal, self-contained owner that drives a ComposeView's lifecycle.
 *
 * Material 1.11.0's BottomSheetDialog is NOT a ComponentDialog, so it can't be
 * used directly as a ViewModelStoreOwner / SavedStateRegistryOwner. This class
 * implements all three owner interfaces and moves its lifecycle to RESUMED on
 * start() (so Compose state/flows run) and DESTROYED on destroy() (so the
 * Composition is disposed and no references leak).
 *
 * It is attached to the dialog's decorView (the window root) via
 * setViewTree*Owner - NOT to the ComposeView - to work around the API 34/35
 * TileService TYPE_QS_DIALOG window bug that strips child lifecycle tags.
 */
private class PanelOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInstance = ViewModelStore()
    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStoreInstance.clear()
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreInstance

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
