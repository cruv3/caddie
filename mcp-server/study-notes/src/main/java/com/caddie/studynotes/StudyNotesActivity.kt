package com.caddie.studynotes

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class StudyNotesActivity : AppCompatActivity() {
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_notes)
        findViewById<View>(R.id.create_note).setOnClickListener { showEditor() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editing) saveAndShowList() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        showList()
    }

    private fun showEditor() {
        editing = true
        findViewById<View>(R.id.notes_list).visibility = View.GONE
        findViewById<View>(R.id.note_editor).visibility = View.VISIBLE
        findViewById<EditText>(R.id.note_text).apply { setText(""); requestFocus() }
    }

    private fun saveAndShowList() {
        val text = findViewById<EditText>(R.id.note_text).text.toString().trim()
        if (text.isNotEmpty()) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_NOTE, text).commit()
        showList()
    }

    private fun showList() {
        editing = false
        findViewById<View>(R.id.note_editor).visibility = View.GONE
        findViewById<View>(R.id.notes_list).visibility = View.VISIBLE
        val note = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_NOTE, null)
        findViewById<TextView>(R.id.saved_note).apply {
            text = note.orEmpty()
            visibility = if (note.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        const val ACTION_RESET = "com.caddie.studynotes.ACTION_RESET"
        const val PREFS = "study_notes_state"
        const val KEY_NOTE = "saved_note"
    }
}
