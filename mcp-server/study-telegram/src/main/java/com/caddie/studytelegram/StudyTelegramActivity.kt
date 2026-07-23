package com.caddie.studytelegram

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class StudyTelegramActivity : AppCompatActivity() {

    private enum class Chat { ANNA, LENA, ANNE, ANNI }

    private var activeChat = Chat.ANNA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_telegram)
        window.statusBarColor = getColor(R.color.telegram_blue)
        window.navigationBarColor = getColor(R.color.telegram_background)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        applySystemBarInsets()
        findViewById<View>(R.id.chat_anna).setOnClickListener { showChat(Chat.ANNA) }
        findViewById<View>(R.id.chat_lena).setOnClickListener { showChat(Chat.LENA) }
        findViewById<View>(R.id.chat_anne).setOnClickListener { showChat(Chat.ANNE) }
        findViewById<View>(R.id.chat_anni).setOnClickListener { showChat(Chat.ANNI) }
        findViewById<Button>(R.id.btn_back_to_chats).setOnClickListener { showChats() }
        findViewById<Button>(R.id.btn_send).setOnClickListener { sendCurrentMessage() }
        showChats()
    }

    private fun applySystemBarInsets() {
        val chatList = findViewById<View>(R.id.screen_chats)
        val chatScreen = findViewById<View>(R.id.screen_chat)
        val inputBar = findViewById<View>(R.id.chat_input_bar)
        val inputBarBottom = inputBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.telegram_root)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            chatList.setPadding(chatList.paddingLeft, bars.top, chatList.paddingRight, chatList.paddingBottom)
            chatScreen.setPadding(chatScreen.paddingLeft, bars.top, chatScreen.paddingRight, chatScreen.paddingBottom)
            inputBar.setPadding(
                inputBar.paddingLeft,
                inputBar.paddingTop,
                inputBar.paddingRight,
                inputBarBottom + bars.bottom,
            )
            insets
        }
    }

    private fun showChats() {
        findViewById<View>(R.id.screen_chat).visibility = View.GONE
        findViewById<View>(R.id.screen_chats).visibility = View.VISIBLE
        renderState()
    }

    private fun showChat(chat: Chat) {
        activeChat = chat
        findViewById<View>(R.id.screen_chats).visibility = View.GONE
        findViewById<View>(R.id.screen_chat).visibility = View.VISIBLE
        renderState()
        if (activeChat == Chat.ANNA) {
            findViewById<EditText>(R.id.message_input).requestFocus()
        }
    }

    private fun sendCurrentMessage() {
        if (activeChat != Chat.ANNA) return
        val input = findViewById<EditText>(R.id.message_input)
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_MESSAGE, message)
            .apply()
        input.setText("")
        renderState()
    }

    private fun renderState() {
        val sent = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_MESSAGE, null)
        findViewById<TextView>(R.id.chat_anna_preview).text =
            sent ?: getString(R.string.anna_seed_preview)
        findViewById<TextView>(R.id.chat_header_avatar).text = when (activeChat) {
            Chat.ANNA, Chat.ANNE, Chat.ANNI -> "A"
            Chat.LENA -> "L"
        }
        findViewById<TextView>(R.id.chat_header_name).text = getString(
            when (activeChat) {
                Chat.ANNA -> R.string.anna
                Chat.LENA -> R.string.lena
                Chat.ANNE -> R.string.anne
                Chat.ANNI -> R.string.anni
            }
        )
        findViewById<View>(R.id.anna_messages).visibility = visibleIf(activeChat == Chat.ANNA)
        findViewById<View>(R.id.lena_messages).visibility = visibleIf(activeChat == Chat.LENA)
        findViewById<View>(R.id.anne_messages).visibility = visibleIf(activeChat == Chat.ANNE)
        findViewById<View>(R.id.anni_messages).visibility = visibleIf(activeChat == Chat.ANNI)
        findViewById<TextView>(R.id.sent_message).apply {
            text = sent.orEmpty()
            visibility = if (activeChat == Chat.ANNA && !sent.isNullOrBlank()) View.VISIBLE else View.GONE
        }
    }

    private fun visibleIf(condition: Boolean): Int = if (condition) View.VISIBLE else View.GONE

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (findViewById<View>(R.id.screen_chat).visibility == View.VISIBLE) {
            showChats()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val ACTION_RESET = "com.caddie.studytelegram.ACTION_RESET"
        const val PREFS = "study_telegram_state"
        const val KEY_LAST_MESSAGE = "last_message"
    }
}
