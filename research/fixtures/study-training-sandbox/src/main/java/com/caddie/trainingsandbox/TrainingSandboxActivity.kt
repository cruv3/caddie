package com.caddie.trainingsandbox

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class TrainingSandboxActivity : AppCompatActivity() {
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_training_sandbox)

        findViewById<View>(R.id.new_list).setOnClickListener {
            showEditor("", "")
        }
        findViewById<View>(R.id.edit_saved_list).setOnClickListener {
            showEditor(savedTitle(), savedItems())
        }
        findViewById<View>(R.id.save_list).setOnClickListener {
            saveDraft()
        }
        findViewById<View>(R.id.discard_draft).setOnClickListener {
            showStableState()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (editing) {
                    showStableState()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        showStableState()
    }

    private fun showStableState() {
        editing = false
        findViewById<View>(R.id.editor).visibility = View.GONE
        findViewById<View>(R.id.new_list).visibility = View.VISIBLE
        if (hasSavedList()) {
            showSaved()
        } else {
            findViewById<View>(R.id.empty_state).visibility = View.VISIBLE
            findViewById<View>(R.id.saved_state).visibility = View.GONE
        }
    }

    private fun showSaved() {
        findViewById<View>(R.id.empty_state).visibility = View.GONE
        findViewById<View>(R.id.saved_state).visibility = View.VISIBLE
        findViewById<TextView>(R.id.saved_title).text = savedTitle()
        findViewById<TextView>(R.id.saved_items).text = savedItems()
    }

    private fun showEditor(title: String, items: String) {
        editing = true
        findViewById<View>(R.id.empty_state).visibility = View.GONE
        findViewById<View>(R.id.saved_state).visibility = View.GONE
        findViewById<View>(R.id.new_list).visibility = View.GONE
        findViewById<View>(R.id.editor).visibility = View.VISIBLE
        findViewById<EditText>(R.id.list_title).apply {
            setText(title)
            setSelection(text.length)
        }
        findViewById<EditText>(R.id.list_items).apply {
            setText(items)
            setSelection(text.length)
            requestFocus()
        }
    }

    private fun saveDraft() {
        val titleInput = findViewById<EditText>(R.id.list_title)
        val itemsInput = findViewById<EditText>(R.id.list_items)
        val title = titleInput.text.toString().trim()
        val items = itemsInput.text.toString()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")

        var valid = true
        if (title.isEmpty()) {
            titleInput.error = getString(R.string.title_required)
            valid = false
        }
        if (items.isEmpty()) {
            itemsInput.error = getString(R.string.items_required)
            valid = false
        }
        if (!valid) return

        val stored = getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_ITEMS, items)
            .commit()
        if (!stored) {
            itemsInput.error = getString(R.string.save_failed)
            return
        }
        showStableState()
    }

    private fun hasSavedList(): Boolean = savedTitle().isNotEmpty() && savedItems().isNotEmpty()

    private fun savedTitle(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TITLE, "").orEmpty()

    private fun savedItems(): String =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ITEMS, "").orEmpty()

    companion object {
        const val ACTION_RESET = "com.caddie.trainingsandbox.ACTION_RESET"
        const val PREFS = "training_sandbox_state"
        const val KEY_TITLE = "saved_title"
        const val KEY_ITEMS = "saved_items"
    }
}
