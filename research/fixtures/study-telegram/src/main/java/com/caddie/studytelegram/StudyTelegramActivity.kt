package com.caddie.studytelegram

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudyTelegramActivity : AppCompatActivity() {

    private var activeChat = ChatId.ANNA
    private lateinit var chatMessages: RecyclerView
    private val messageAdapter = ChatMessageAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_telegram)
        window.statusBarColor = getColor(R.color.telegram_blue)
        window.navigationBarColor = getColor(R.color.telegram_background)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        chatMessages = findViewById<RecyclerView>(R.id.chat_messages).apply {
            layoutManager = LinearLayoutManager(this@StudyTelegramActivity)
            adapter = messageAdapter
        }
        applySystemBarInsets()
        findViewById<View>(R.id.chat_anna).setOnClickListener { showChat(ChatId.ANNA) }
        findViewById<View>(R.id.chat_lena).setOnClickListener { showChat(ChatId.LENA) }
        findViewById<View>(R.id.chat_anne).setOnClickListener { showChat(ChatId.ANNE) }
        findViewById<View>(R.id.chat_anni).setOnClickListener { showChat(ChatId.ANNI) }
        findViewById<View>(R.id.chat_project).setOnClickListener { showChat(ChatId.PROJECT) }
        findViewById<View>(R.id.chat_mila).setOnClickListener { showChat(ChatId.MILA) }
        findViewById<View>(R.id.chat_jonas).setOnClickListener { showChat(ChatId.JONAS) }
        findViewById<Button>(R.id.btn_back_to_chats).setOnClickListener { showChats() }
        findViewById<Button>(R.id.btn_send).setOnClickListener { sendCurrentMessage() }
        activeChat = savedInstanceState?.getString(STATE_ACTIVE_CHAT)
            ?.let(ChatId::valueOf)
            ?: ChatId.ANNA
        if (savedInstanceState?.getBoolean(STATE_SHOWING_CHAT) == true) {
            showChat(activeChat, requestAnnaFocus = false)
        } else {
            showChats()
        }
    }

    private fun applySystemBarInsets() {
        val chatList = findViewById<View>(R.id.screen_chats)
        val chatScreen = findViewById<View>(R.id.screen_chat)
        val inputBar = findViewById<View>(R.id.chat_input_bar)
        val inputBarBottom = inputBar.paddingBottom
        val chatScreenBottom = chatScreen.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.telegram_root)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardOnlyBottom = (ime.bottom - bars.bottom).coerceAtLeast(0)
            chatList.setPadding(chatList.paddingLeft, bars.top, chatList.paddingRight, chatList.paddingBottom)
            chatScreen.setPadding(
                chatScreen.paddingLeft,
                bars.top,
                chatScreen.paddingRight,
                chatScreenBottom + keyboardOnlyBottom,
            )
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
        renderChatList()
    }

    private fun showChat(chatId: ChatId, requestAnnaFocus: Boolean = true) {
        activeChat = chatId
        findViewById<View>(R.id.screen_chats).visibility = View.GONE
        findViewById<View>(R.id.screen_chat).visibility = View.VISIBLE
        renderChat()
        chatMessages.post {
            if (messageAdapter.itemCount > 0) {
                chatMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }
        if (activeChat == ChatId.ANNA && requestAnnaFocus) {
            findViewById<EditText>(R.id.message_input).requestFocus()
        }
    }

    private fun sendCurrentMessage() {
        if (activeChat != ChatId.ANNA) return
        val input = findViewById<EditText>(R.id.message_input)
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        val sentAt = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date())
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_MESSAGE, message)
            .putString(KEY_LAST_MESSAGE_TIME, sentAt)
            .apply()
        input.setText("")
        renderChat()
        chatMessages.post {
            if (messageAdapter.itemCount > 0) {
                chatMessages.scrollToPosition(messageAdapter.itemCount - 1)
            }
        }
    }

    private fun renderChatList() {
        val sent = savedAnnaMessage()
        findViewById<TextView>(R.id.chat_anna_preview).text = sent ?: ChatSeeds.chat(ChatId.ANNA).preview
        listOf(
            ChatId.LENA to R.id.chat_lena_preview,
            ChatId.ANNE to R.id.chat_anne_preview,
            ChatId.ANNI to R.id.chat_anni_preview,
            ChatId.PROJECT to R.id.chat_project_preview,
            ChatId.MILA to R.id.chat_mila_preview,
            ChatId.JONAS to R.id.chat_jonas_preview,
        ).forEach { (chatId, previewId) ->
            findViewById<TextView>(previewId).text = ChatSeeds.chat(chatId).preview
        }
    }

    private fun renderChat() {
        val summary = ChatSeeds.chat(activeChat)
        findViewById<TextView>(R.id.chat_header_avatar).text = summary.avatar
        findViewById<TextView>(R.id.chat_header_name).text = summary.name
        findViewById<View>(R.id.chat_input_bar).visibility =
            if (activeChat == ChatId.ANNA) View.VISIBLE else View.GONE
        messageAdapter.submitRows(rows(messagesForActiveChat()))
    }

    private fun messagesForActiveChat(): List<ChatMessage> {
        val seedHistory = ChatSeeds.messages(activeChat)
        val sent = savedAnnaMessage()
        return if (activeChat == ChatId.ANNA && !sent.isNullOrBlank()) {
            seedHistory + ChatMessage(
                text = sent,
                incoming = false,
                dateLabel = "Heute",
                time = savedAnnaTimestamp().orEmpty(),
            )
        } else {
            seedHistory
        }
    }

    private fun savedAnnaMessage(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_MESSAGE, null)

    private fun savedAnnaTimestamp(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_MESSAGE_TIME, null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTIVE_CHAT, activeChat.name)
        outState.putBoolean(
            STATE_SHOWING_CHAT,
            findViewById<View>(R.id.screen_chat).visibility == View.VISIBLE,
        )
    }

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
        const val KEY_LAST_MESSAGE_TIME = "last_message_time"
        private const val STATE_ACTIVE_CHAT = "active_chat"
        private const val STATE_SHOWING_CHAT = "showing_chat"
    }
}
