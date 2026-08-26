package com.caddie.studymail

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class StudyMailActivity : AppCompatActivity() {

    private enum class MessageType { INVOICE, MEETING_CHANGE }

    private data class RowSeed(
        val viewId: Int,
        val sender: String,
        val subject: String,
        val preview: String,
        val time: String,
        val unread: Boolean,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_study_mail)
        configureSystemBarInsets()
        window.statusBarColor = getColor(R.color.mail_background)
        window.navigationBarColor = getColor(R.color.mail_background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        findViewById<View>(R.id.mail_invoice).setOnClickListener { openMessage(MessageType.INVOICE) }
        findViewById<View>(R.id.mail_meeting_change).setOnClickListener { openMessage(MessageType.MEETING_CHANGE) }
        findViewById<View>(R.id.btn_back_to_inbox).setOnClickListener { showInbox() }
        showInbox()
    }

    private fun configureSystemBarInsets() {
        val root = findViewById<View>(R.id.mail_root)
        val inbox = findViewById<View>(R.id.mail_inbox)
        val detail = findViewById<View>(R.id.mail_detail)
        val inboxInitialPadding = Padding(inbox)
        val detailInitialPadding = Padding(detail)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            inbox.setPadding(
                inboxInitialPadding.left + systemBars.left,
                inboxInitialPadding.top + systemBars.top,
                inboxInitialPadding.right + systemBars.right,
                inboxInitialPadding.bottom + systemBars.bottom,
            )
            detail.setPadding(
                detailInitialPadding.left + systemBars.left,
                detailInitialPadding.top + systemBars.top,
                detailInitialPadding.right + systemBars.right,
                detailInitialPadding.bottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        constructor(view: View) : this(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom,
        )
    }

    private fun renderInbox() {
        val preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val invoiceUnread = !preferences.getBoolean(KEY_INVOICE_READ, false)
        val meetingUnread = !preferences.getBoolean(KEY_MEETING_READ, false)
        val rows = listOf(
            RowSeed(R.id.mail_invoice, getString(R.string.invoice_sender), getString(R.string.invoice_subject), getString(R.string.invoice_preview), "20:31", invoiceUnread),
            RowSeed(R.id.mail_meeting_change, getString(R.string.meeting_sender), getString(R.string.meeting_subject), getString(R.string.meeting_preview), "18:12", meetingUnread),
            RowSeed(R.id.mail_mensa, "Mensa Campus", "Speiseplan für diese Woche", "Neue vegetarische Gerichte im Wochenplan", "Fr", false),
            RowSeed(R.id.mail_sport, "Hochschulsport", "Kursbestätigung", "Deine Anmeldung wurde bestätigt", "Do", false),
            RowSeed(R.id.mail_library, "Bibliothek", "Erinnerung an die Rückgabefrist", "Zwei Medien werden nächste Woche fällig", "Mi", false),
            RowSeed(R.id.mail_it, "Campus IT", "Wartungsarbeiten am WLAN", "Kurze Unterbrechung am Samstagmorgen", "Di", false),
            RowSeed(R.id.mail_fee, "Studierendenwerk", "Information zum Semesterbeitrag", "Hinweise zur kommenden Rückmeldung", "Mo", false),
            RowSeed(R.id.mail_project, "Projektteam", "Protokoll der letzten Sitzung", "Beschlüsse und nächste Schritte", "So", false),
        )
        rows.forEach(::bindRow)
        val unreadCount = listOf(invoiceUnread, meetingUnread).count { it }
        findViewById<TextView>(R.id.unread_count).text = "$unreadCount ungelesen"
    }

    private fun bindRow(seed: RowSeed) {
        val row = findViewById<View>(seed.viewId)
        row.isSelected = seed.unread
        row.findViewById<TextView>(R.id.mail_row_sender).apply {
            text = seed.sender
            setTypeface(typeface, if (seed.unread) Typeface.BOLD else Typeface.NORMAL)
        }
        row.findViewById<TextView>(R.id.mail_row_subject).apply {
            text = seed.subject
            setTypeface(typeface, if (seed.unread) Typeface.BOLD else Typeface.NORMAL)
        }
        row.findViewById<TextView>(R.id.mail_row_preview).text = seed.preview
        row.findViewById<TextView>(R.id.mail_row_time).apply {
            text = seed.time
            setTypeface(typeface, if (seed.unread) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun openMessage(type: MessageType) {
        val isInvoice = type == MessageType.INVOICE
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(if (isInvoice) KEY_INVOICE_READ else KEY_MEETING_READ, true)
            .apply()
        findViewById<View>(if (isInvoice) R.id.mail_invoice else R.id.mail_meeting_change).isSelected = false
        findViewById<TextView>(R.id.mail_subject).text = getString(
            if (isInvoice) R.string.invoice_subject else R.string.meeting_subject
        )
        findViewById<TextView>(R.id.mail_sender).text = getString(
            if (isInvoice) R.string.invoice_sender else R.string.meeting_sender
        )
        findViewById<TextView>(R.id.mail_body).text = getString(
            if (isInvoice) R.string.invoice_body else R.string.meeting_body
        )
        findViewById<View>(R.id.mail_inbox).visibility = View.GONE
        findViewById<View>(R.id.mail_detail).visibility = View.VISIBLE
    }

    private fun showInbox() {
        findViewById<View>(R.id.mail_detail).visibility = View.GONE
        findViewById<View>(R.id.mail_inbox).visibility = View.VISIBLE
        renderInbox()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (findViewById<View>(R.id.mail_detail).visibility == View.VISIBLE) {
            showInbox()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val ACTION_RESET = "com.caddie.studymail.ACTION_RESET"
        const val PREFS = "study_mail_state"
        const val KEY_INVOICE_READ = "invoice_read"
        const val KEY_MEETING_READ = "meeting_read"
    }
}
