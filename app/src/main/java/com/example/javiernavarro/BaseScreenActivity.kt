package com.example.javiernavarro

import android.graphics.Color
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

/** Shared window handling: keeps forms and controls clear of system bars and keyboard. */
abstract class BaseScreenActivity : AppCompatActivity() {
    protected fun showScreen(layoutId: Int) {
        enableEdgeToEdge()
        setContentView(layoutId)
        window.decorView.setBackgroundColor(Color.rgb(244, 247, 243))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, keyboard.bottom))
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
