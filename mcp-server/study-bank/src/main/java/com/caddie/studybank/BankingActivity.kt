package com.caddie.studybank

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

/**
 * Banking mock activity for the study system.
 *
 * Provides a simple banking UI with stable resource IDs for automated
 * task execution. Supports transfer, confirmation, balance display,
 * and transaction history.
 *
 * Key resource IDs:
 *   - balance_amount: Current account balance
 *   - transfer_recipient: Recipient name input
 *   - transfer_amount: Amount input
 *   - btn_send: Send transfer button
 *   - btn_confirm: Confirm transfer button
 *   - confirm_status: Pending confirmation status text
 *   - btn_reset: Reset all state
 *   - transaction_list: RecyclerView for transaction history
 */
class BankingActivity : AppCompatActivity() {

    private var balance = 500.00
    private var pendingTransfer: PendingTransfer? = null
    private val transactions = mutableListOf<Transaction>()

    // Intent action to reset all state
    companion object {
        const val ACTION_RESET = "com.caddie.studybank.ACTION_RESET"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_banking)

        // Handle reset intent
        if (intent.action == ACTION_RESET) {
            resetState()
        }

        setupViews()
    }

    private fun setupViews() {
        val balanceAmount = findViewById<TextView>(R.id.balance_amount)
        val confirmStatus = findViewById<TextView>(R.id.confirm_status)
        val btnSend = findViewById<Button>(R.id.btn_send)
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)
        val btnReset = findViewById<Button>(R.id.btn_reset)
        val transferRecipient = findViewById<EditText>(R.id.transfer_recipient)
        val transferAmount = findViewById<EditText>(R.id.transfer_amount)

        updateBalanceDisplay(balanceAmount)

        btnSend.setOnClickListener {
            val recipient = transferRecipient.text.toString().trim()
            val amountText = transferAmount.text.toString().trim()

            if (recipient.isEmpty()) {
                transferRecipient.error = "Enter recipient"
                return@setOnClickListener
            }

            val amount = amountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                transferAmount.error = "Enter valid amount"
                return@setOnClickListener
            }

            if (amount > balance) {
                transferAmount.error = "Insufficient balance"
                return@setOnClickListener
            }

            pendingTransfer = PendingTransfer(recipient, amount, UUID.randomUUID().toString())
            confirmStatus.text = "Pending: €${"%.2f".format(amount)} to $recipient"
            balanceAmount.text = "€ ${"%.2f".format(balance)}"
        }

        btnConfirm.setOnClickListener {
            val transfer = pendingTransfer ?: return@setOnClickListener
            balance -= transfer.amount
            transactions.add(Transaction("Transfer to ${transfer.recipient}", transfer.amount))
            pendingTransfer = null
            confirmStatus.text = "No pending transfer"
            transferRecipient.text.clear()
            transferAmount.text.clear()
            updateBalanceDisplay(balanceAmount)
        }

        btnReset.setOnClickListener {
            resetState()
            updateBalanceDisplay(balanceAmount)
            confirmStatus.text = "No pending transfer"
        }
    }

    private fun updateBalanceDisplay(view: TextView) {
        view.text = "€ ${"%.2f".format(balance)}"
    }

    private fun resetState() {
        balance = 500.00
        pendingTransfer = null
        transactions.clear()
    }

    data class PendingTransfer(
        val recipient: String,
        val amount: Double,
        val id: String,
    )

    data class Transaction(
        val description: String,
        val amount: Double,
    )
}
