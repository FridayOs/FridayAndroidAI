package com.dark.tool_neuron.service.island

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class IslandAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val coalesceRunnable = Runnable { scanAndPublish() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandPositionStore.init(applicationContext)
        IslandPositionStore.setAccessibilityActive(true)
        Log.d(TAG, "service connected")
        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                IslandPositionStore.setDodgeY(0f)
                scheduleScan()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleScan()
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        IslandPositionStore.setAccessibilityActive(false)
        handler.removeCallbacks(coalesceRunnable)
        return super.onUnbind(intent)
    }

    private fun scheduleScan() {
        handler.removeCallbacks(coalesceRunnable)
        handler.postDelayed(coalesceRunnable, COALESCE_MS)
    }

    private fun scanAndPublish() {
        val root = try { rootInActiveWindow } catch (_: SecurityException) { null }
        if (root == null) {
            Log.d(TAG, "rootInActiveWindow null")
            IslandPositionStore.setDodgeY(0f)
            return
        }

        val pkg = root.packageName?.toString().orEmpty()
        if (pkg.contains(LAUNCHER_KEYWORD, ignoreCase = true)) {
            Log.d(TAG, "pkg=$pkg is launcher, skipping dodge")
            IslandPositionStore.setDodgeY(0f)
            return
        }

        val density = resources.displayMetrics.density
        val pillRect = computeNaturalPillRect(density)
        val searchInset = (IslandGeometry.DODGE_MARGIN_DP * density).toInt()
        val searchZone = Rect(pillRect).apply { inset(-searchInset, -searchInset) }
        val obstacles = mutableListOf<Rect>()
        collectClickableRects(root, searchZone, obstacles, depthBudget = MAX_NODE_DEPTH)

        val dodgeDp = computeDownNudgeDp(pillRect, obstacles, density)
        Log.d(
            TAG,
            "pkg=$pkg pill=$pillRect obstacles=${obstacles.size} dodgeY=$dodgeDp",
        )
        IslandPositionStore.setDodgeY(dodgeDp)
    }

    private fun computeNaturalPillRect(density: Float): Rect {
        val pos = IslandPositionStore.position.value
        val screenWidthPx = resources.displayMetrics.widthPixels
        val pillWidthPx = (IslandGeometry.PILL_W_DP * density).toInt()
        val pillHeightPx = (IslandGeometry.PILL_H_DP * density).toInt()
        val pillLeftPx = (screenWidthPx - pillWidthPx) / 2
        val pillTopPx = statusBarTopInsetPx() +
            ((IslandGeometry.OUTER_PADDING_DP + pos.offsetYDp) * density).toInt()
        return Rect(pillLeftPx, pillTopPx, pillLeftPx + pillWidthPx, pillTopPx + pillHeightPx)
    }

    private fun statusBarTopInsetPx(): Int {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = getSystemService(WindowManager::class.java)
                val metrics = wm?.maximumWindowMetrics
                val insets = metrics?.windowInsets?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars()
                )
                if (insets != null) return insets.top
            }
        } catch (_: Throwable) {}
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun collectClickableRects(
        node: AccessibilityNodeInfo?,
        searchZone: Rect,
        out: MutableList<Rect>,
        depthBudget: Int,
    ) {
        if (node == null || depthBudget <= 0) return
        try {
            if (node.isVisibleToUser && (node.isClickable || node.isLongClickable)) {
                val r = Rect()
                node.getBoundsInScreen(r)
                if (!r.isEmpty && Rect.intersects(r, searchZone)) out.add(r)
            }
            val count = node.childCount
            for (i in 0 until count) {
                collectClickableRects(node.getChild(i), searchZone, out, depthBudget - 1)
            }
        } catch (_: Throwable) {
        }
    }

    private fun computeDownNudgeDp(
        pillRect: Rect,
        obstacles: List<Rect>,
        density: Float,
    ): Float {
        if (obstacles.isEmpty()) return 0f
        val marginPx = (IslandGeometry.DODGE_MARGIN_DP * density).toInt()
        var maxDownPx = 0
        var hasOverlap = false
        for (o in obstacles) {
            if (!Rect.intersects(o, pillRect)) continue
            hasOverlap = true
            val pushDown = (o.bottom + marginPx) - pillRect.top
            if (pushDown > maxDownPx) maxDownPx = pushDown
        }
        if (!hasOverlap) return 0f
        val maxDodgePx = (IslandGeometry.MAX_DODGE_DP * density).toInt()
        return maxDownPx.coerceIn(0, maxDodgePx) / density
    }

    companion object {
        private const val TAG = "IslandA11y"
        private const val COALESCE_MS = 150L
        private const val MAX_NODE_DEPTH = 96
        private const val LAUNCHER_KEYWORD = "launcher"
    }
}
