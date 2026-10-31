package com.aiagent.assistant.service.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * অন্য অ্যাপের উপরে ভাসমান assistant বাবল। টেনে সরানো যায়, ট্যাপ করলে ছোট মেনু খোলে।
 * Compose-এর বদলে সাধারণ View ব্যবহার করা হয়েছে, কারণ system overlay window-এ সেটা সহজ ও নিরাপদ।
 * "Display over other apps" অনুমতি লাগে।
 */
class BubbleOverlay(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onTalk()
        fun onOpenChat()
        fun onStopAgent()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null

    private fun dp(value: Int): Int = (value * density).toInt()

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    val isShowing: Boolean get() = bubble != null

    fun show(letter: String) {
        if (bubble != null) {
            bubble?.text = letter
            return
        }
        if (!Settings.canDrawOverlays(context)) return
        val size = dp(56)

        val view = TextView(context).apply {
            text = letter
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF7C4DFF.toInt(), 0xFF2F80FF.toInt())
            ).apply { shape = GradientDrawable.OVAL }
            elevation = dp(6).toFloat()
            contentDescription = "Assistant bubble"
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - size - dp(8)
            y = (screenHeight * 0.4f).toInt()
        }
        view.setOnTouchListener(DragListener(params))
        try {
            windowManager.addView(view, params)
            bubble = view
            bubbleParams = params
        } catch (e: Exception) {
            // অনুমতি বা window সমস্যা: বাবল দেখানো যাবে না
        }
    }

    fun hide() {
        hidePanel()
        bubble?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // আগেই সরানো
            }
        }
        bubble = null
        bubbleParams = null
    }

    // ───────────── drag + tap ─────────────

    private inner class DragListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downX = 0f
        private var downY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        hidePanel()
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (e: Exception) {
                            // view সরে গেছে
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        snapToEdge(view, params)
                    } else {
                        view.performClick()
                        if (panel == null) showPanel() else hidePanel()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val size = params.width
        val target = if (params.x + size / 2 < screenWidth / 2) dp(8) else screenWidth - size - dp(8)
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 180
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    cancel()
                }
            }
            start()
        }
    }

    // ───────────── quick action panel ─────────────

    private fun showPanel() {
        val anchor = bubbleParams ?: return
        val width = dp(220)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF12122A.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0x667C4DFF)
            }
            elevation = dp(8).toFloat()
        }
        container.addView(actionRow("🎙  Talk · কথা বলুন") {
            hidePanel()
            callbacks.onTalk()
        })
        container.addView(actionRow("💬  Chat · চ্যাট") {
            hidePanel()
            callbacks.onOpenChat()
        })
        container.addView(actionRow("⏹  Stop agent · বন্ধ করুন") {
            hidePanel()
            callbacks.onStopAgent()
        })
        container.addView(actionRow("✕  Close · বন্ধ") { hidePanel() })

        val onLeftHalf = anchor.x + anchor.width / 2 < screenWidth / 2
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (onLeftHalf) anchor.x + anchor.width + dp(8) else anchor.x - width - dp(8)
            y = anchor.y
        }
        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) hidePanel()
            false
        }
        try {
            windowManager.addView(container, params)
            panel = container
        } catch (e: Exception) {
            // দেখানো গেল না
        }
    }

    private fun actionRow(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun hidePanel() {
        panel?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // আগেই সরানো
            }
        }
        panel = null
    }
}
