package com.comunidapp.app.ui.media

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.view.GestureCropImageView
import com.yalantis.ucrop.view.UCropView

/**
 * Same uCrop engine and crop math.
 * Tool chrome only. Framing is kept after drag/pinch; Restablecer is user-only.
 */
class LeoVerUCropActivity : UCropActivity() {
    private var cropTabId: Int = 0
    private var rotateTabId: Int = 0
    private var wrapperControls: View? = null
    private var controlsShadow: View? = null
    private var rotateWheel: View? = null
    private var scaleWheel: View? = null
    private var cropView: GestureCropImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val content = findViewById<View>(android.R.id.content)
        val root = (content as? android.view.ViewGroup)?.getChildAt(0) ?: content
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        wireToolChrome()
        preventAutoRecenterOnRelease()
    }

    private fun wireToolChrome() {
        cropTabId = viewId("state_aspect_ratio")
        rotateTabId = viewId("state_rotate")
        val scaleTabId = viewId("state_scale")
        val cropTab = findView(cropTabId)
        val rotateTab = findView(rotateTabId)
        val scaleTab = findView(scaleTabId)
        wrapperControls = findView(viewId("wrapper_controls"))
        controlsShadow = findView(viewId("controls_shadow"))
        rotateWheel = findView(viewId("layout_rotate_wheel"))
        scaleWheel = findView(viewId("layout_scale_wheel"))
        cropView = findViewById<UCropView>(viewId("ucrop"))?.cropImageView

        cropTab?.visibility = View.VISIBLE
        rotateTab?.visibility = View.VISIBLE
        scaleTab?.visibility = View.VISIBLE
        setTabLabel(cropTab, "Ajustar")
        setTabLabel(rotateTab, "Rotar")
        setTabLabel(scaleTab, "Restablecer")
        scaleWheel?.visibility = View.GONE

        cropTab?.setOnClickListener { enterNormalMode() }
        rotateTab?.setOnClickListener { enterRotateMode() }
        scaleTab?.setOnClickListener { resetFramingToInitial() }

        enterNormalMode()
    }

    /**
     * Stock uCrop calls setImageToWrapCropBounds() on ACTION_UP, which animates
     * the image back toward the crop center. Cancel that snap-back and apply
     * only a non-animated indent if the crop would otherwise show empty area.
     */
    private fun preventAutoRecenterOnRelease() {
        val view = cropView ?: return
        view.setOnTouchListener { v, event ->
            val crop = v as GestureCropImageView
            val handled = crop.onTouchEvent(event)
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                crop.cancelAllAnimations()
            }
            handled
        }
    }

    private fun enterNormalMode() {
        scaleWheel?.visibility = View.GONE
        rotateWheel?.visibility = View.GONE
        wrapperControls?.visibility = View.GONE
        controlsShadow?.visibility = View.GONE
        findView(cropTabId)?.isSelected = true
        findView(rotateTabId)?.isSelected = false
        nativeAllowedGestures(2)
    }

    private fun enterRotateMode() {
        wrapperControls?.visibility = View.VISIBLE
        controlsShadow?.visibility = View.VISIBLE
        scaleWheel?.visibility = View.GONE
        rotateWheel?.visibility = View.VISIBLE
        findView(cropTabId)?.isSelected = false
        findView(rotateTabId)?.isSelected = true
        nativeAllowedGestures(1)
    }

    private fun resetFramingToInitial() {
        val view = cropView ?: return
        val minScale = view.minScale
        if (minScale <= 0f) return
        view.cancelAllAnimations()
        view.zoomOutImage(minScale)
        view.setImageToWrapCropBounds(false)
    }

    private fun nativeAllowedGestures(tab: Int) {
        runCatching {
            val method = UCropActivity::class.java.getDeclaredMethod(
                "setAllowedGestures",
                Integer.TYPE
            )
            method.isAccessible = true
            method.invoke(this, tab)
        }
    }

    private fun setTabLabel(tab: View?, label: String) {
        val group = tab as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView) child.text = label
        }
    }

    private fun viewId(name: String): Int = resources.getIdentifier(name, "id", packageName)

    private fun findView(id: Int): View? = if (id == 0) null else findViewById(id)
}
