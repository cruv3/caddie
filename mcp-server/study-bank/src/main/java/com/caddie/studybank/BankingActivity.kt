package com.caddie.studybank

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Offline banking mock with deterministic study states and stable accessibility IDs. */
class BankingActivity : AppCompatActivity() {

    private enum class Screen { HOME, FORM, REVIEW }

    private var balance = INITIAL_BALANCE
    private var pendingTransfer: PendingTransfer? = null
    private var completedTransfer: CompletedTransfer? = null
    private var currentScreen = Screen.HOME

    private lateinit var homeScreen: View
    private lateinit var transferScreen: View
    private lateinit var reviewScreen: View
    private lateinit var balanceAmount: TextView
    private lateinit var emptyTransactionLabel: TextView
    private lateinit var newTransactionRow: View
    private lateinit var newTransactionRecipient: TextView
    private lateinit var newTransactionPurpose: TextView
    private lateinit var newTransactionAmount: TextView
    private lateinit var transferRecipient: EditText
    private lateinit var transferIban: EditText
    private lateinit var transferAmount: EditText
    private lateinit var purposeText: EditText
    private lateinit var confirmDetails: TextView
    private val resetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RESET) resetForStudy()
        }
    }

    companion object {
        const val ACTION_RESET = "com.caddie.studybank.ACTION_RESET"
        private const val INITIAL_BALANCE = 40.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_banking)
        window.statusBarColor = getColor(R.color.card_background)
        window.navigationBarColor = getColor(R.color.page_background)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        bindViews()
        applySystemBarInsets()
        setupActions()
        if (intent.action == ACTION_RESET) resetForStudy()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_RESET) resetForStudy()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            resetReceiver,
            IntentFilter(ACTION_RESET),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(resetReceiver)
        super.onStop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentScreen) {
            Screen.REVIEW -> show(Screen.FORM)
            Screen.FORM -> show(Screen.HOME)
            Screen.HOME -> super.onBackPressed()
        }
    }

    private fun bindViews() {
        homeScreen = findViewById(R.id.home_screen)
        transferScreen = findViewById(R.id.transfer_screen)
        reviewScreen = findViewById(R.id.review_screen)
        balanceAmount = findViewById(R.id.balance_amount)
        emptyTransactionLabel = findViewById(R.id.empty_transaction_label)
        newTransactionRow = findViewById(R.id.new_transaction_row)
        newTransactionRecipient = findViewById(R.id.new_transaction_recipient)
        newTransactionPurpose = findViewById(R.id.new_transaction_purpose)
        newTransactionAmount = findViewById(R.id.new_transaction_amount)
        transferRecipient = findViewById(R.id.transfer_recipient)
        transferIban = findViewById(R.id.transfer_iban)
        transferAmount = findViewById(R.id.transfer_amount)
        purposeText = findViewById(R.id.purpose_text)
        confirmDetails = findViewById(R.id.confirm_details)
    }

    private fun applySystemBarInsets() {
        val screens = listOf(homeScreen, transferScreen, reviewScreen)
        val initialTop = screens.associateWith { it.paddingTop }
        val initialBottom = screens.associateWith { it.paddingBottom }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.banking_root)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            screens.forEach { screen ->
                screen.setPadding(
                    screen.paddingLeft,
                    (initialTop[screen] ?: 0) + bars.top,
                    screen.paddingRight,
                    (initialBottom[screen] ?: 0) + bars.bottom,
                )
            }
            insets
        }
    }

    private fun setupActions() {
        findViewById<Button>(R.id.btn_open_transfer).setOnClickListener { show(Screen.FORM) }
        findViewById<Button>(R.id.btn_back_form).setOnClickListener { show(Screen.HOME) }
        findViewById<Button>(R.id.btn_back_review).setOnClickListener { show(Screen.FORM) }
        findViewById<Button>(R.id.btn_send).setOnClickListener { reviewTransfer() }
        findViewById<Button>(R.id.btn_confirm).setOnClickListener { completeTransfer() }
    }

    private fun reviewTransfer() {
        val recipient = transferRecipient.text.toString().trim()
        val iban = transferIban.text.toString().replace(" ", "").trim()
        val amount = parseAmount(transferAmount.text.toString())
        val purpose = purposeText.text.toString().trim()

        if (recipient.isEmpty()) {
            transferRecipient.error = "Empfänger eingeben"
            return
        }
        if (iban.isEmpty()) {
            transferIban.error = "IBAN eingeben"
            return
        }
        if (amount == null || amount <= 0.0) {
            transferAmount.error = "Gültigen Betrag eingeben"
            return
        }
        if (purpose.isEmpty()) {
            purposeText.error = "Verwendungszweck eingeben"
            return
        }

        pendingTransfer = PendingTransfer(
            recipient = recipient,
            iban = iban,
            amount = amount,
            purpose = purpose,
            id = UUID.randomUUID().toString(),
        )
        confirmDetails.text = buildReviewText(pendingTransfer!!)
        show(Screen.REVIEW)
    }

    private fun completeTransfer() {
        val transfer = pendingTransfer ?: return
        balance -= transfer.amount
        completedTransfer = CompletedTransfer(
            recipient = transfer.recipient,
            amount = transfer.amount,
            purpose = transfer.purpose,
            id = transfer.id,
        )
        pendingTransfer = null
        clearForm()
        show(Screen.HOME)
    }

    private fun show(screen: Screen) {
        currentScreen = screen
        render()
    }

    private fun render() {
        homeScreen.visibility = if (currentScreen == Screen.HOME) View.VISIBLE else View.GONE
        transferScreen.visibility = if (currentScreen == Screen.FORM) View.VISIBLE else View.GONE
        reviewScreen.visibility = if (currentScreen == Screen.REVIEW) View.VISIBLE else View.GONE
        if (currentScreen == Screen.HOME) renderHome()
    }

    private fun renderHome() {
        balanceAmount.text = formatMoney(balance)
        balanceAmount.setTextColor(
            getColor(if (balance < 0.0) R.color.sparkasse_red else R.color.text_primary)
        )

        val transfer = completedTransfer
        emptyTransactionLabel.visibility = if (transfer == null) View.VISIBLE else View.GONE
        newTransactionRow.visibility = if (transfer == null) View.GONE else View.VISIBLE
        if (transfer != null) {
            newTransactionRecipient.text = transfer.recipient
            newTransactionPurpose.text = transfer.purpose
            newTransactionAmount.text = formatMoney(-transfer.amount)
            newTransactionAmount.setTextColor(
                getColor(if (balance < 0.0) R.color.sparkasse_red else R.color.text_primary)
            )
        }
    }

    private fun buildReviewText(transfer: PendingTransfer): String = """
        Empfänger
        ${transfer.recipient}

        IBAN
        ${formatIban(transfer.iban)}

        Betrag
        ${formatMoney(transfer.amount)}

        Verwendungszweck
        ${transfer.purpose}
    """.trimIndent()

    private fun resetState() {
        balance = INITIAL_BALANCE
        pendingTransfer = null
        completedTransfer = null
        currentScreen = Screen.HOME
        clearForm()
        confirmDetails.text = ""
    }

    internal fun resetForStudy() {
        resetState()
        render()
    }

    private fun clearForm() {
        transferRecipient.text.clear()
        transferIban.text.clear()
        transferAmount.text.clear()
        purposeText.text.clear()
    }

    private fun parseAmount(raw: String): Double? {
        val compact = raw.replace("€", "").replace(" ", "").trim()
        val normalized = if (compact.contains(',')) {
            compact.replace(".", "").replace(',', '.')
        } else compact
        return normalized.toDoubleOrNull()
    }

    private fun formatMoney(value: Double): String {
        val number = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(abs(value))
        return if (value < 0.0) "−$number €" else "$number €"
    }

    private fun formatIban(iban: String): String = iban.chunked(4).joinToString(" ")

    data class PendingTransfer(
        val recipient: String,
        val iban: String,
        val amount: Double,
        val purpose: String,
        val id: String,
    )

    data class CompletedTransfer(
        val recipient: String,
        val amount: Double,
        val purpose: String,
        val id: String,
    )
}
