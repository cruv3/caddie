package com.caddie.studygallery

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class StudyGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_gallery)
        findViewById<View>(R.id.whiteboard_photo).setOnClickListener { showDetail() }
        findViewById<View>(R.id.back_to_gallery).setOnClickListener { showGrid() }
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DETAIL, false)) showDetail() else showGrid()
    }

    private fun showDetail() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DETAIL, true).apply()
        window.statusBarColor = getColor(R.color.gallery_detail_background)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        findViewById<View>(R.id.gallery_grid).visibility = View.GONE
        findViewById<View>(R.id.gallery_detail).visibility = View.VISIBLE
    }

    private fun showGrid() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_DETAIL, false).apply()
        window.statusBarColor = getColor(R.color.gallery_background)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        findViewById<View>(R.id.gallery_detail).visibility = View.GONE
        findViewById<View>(R.id.gallery_grid).visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (findViewById<View>(R.id.gallery_detail).visibility == View.VISIBLE) showGrid() else super.onBackPressed()
    }

    companion object {
        const val ACTION_RESET = "com.caddie.studygallery.ACTION_RESET"
        const val PREFS = "study_gallery_state"
        const val KEY_DETAIL = "detail_open"
    }
}
