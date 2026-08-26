package com.caddie.studytelegram

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView

sealed interface ChatRow {
    data class Date(val label: String) : ChatRow
    data class Message(val value: ChatMessage) : ChatRow
}

fun rows(messages: List<ChatMessage>): List<ChatRow> = buildList {
    var currentDate: String? = null
    messages.forEach { message ->
        if (message.dateLabel != currentDate) {
            add(ChatRow.Date(message.dateLabel))
            currentDate = message.dateLabel
        }
        add(ChatRow.Message(message))
    }
}

class ChatMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var renderedRows: List<ChatRow> = emptyList()

    fun submitRows(rows: List<ChatRow>) {
        renderedRows = rows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (renderedRows[position]) {
        is ChatRow.Date -> DATE_VIEW_TYPE
        is ChatRow.Message -> MESSAGE_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        DATE_VIEW_TYPE -> DateViewHolder(inflate(parent, R.layout.item_chat_date))
        else -> MessageViewHolder(inflate(parent, R.layout.item_chat_message))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = renderedRows[position]) {
            is ChatRow.Date -> (holder as DateViewHolder).bind(row)
            is ChatRow.Message -> (holder as MessageViewHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = renderedRows.size

    private fun inflate(parent: ViewGroup, @LayoutRes layout: Int): View =
        LayoutInflater.from(parent.context).inflate(layout, parent, false)

    private class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label = view.findViewById<TextView>(R.id.date_label)

        fun bind(row: ChatRow.Date) {
            label.text = row.label
        }
    }

    private class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble = view.findViewById<LinearLayout>(R.id.message_bubble)
        private val text = view.findViewById<TextView>(R.id.message_text)
        private val time = view.findViewById<TextView>(R.id.message_time)

        fun bind(row: ChatRow.Message) {
            val message = row.value
            (bubble.layoutParams as FrameLayout.LayoutParams).gravity =
                if (message.incoming) Gravity.START else Gravity.END
            bubble.setBackgroundResource(
                if (message.incoming) R.color.telegram_incoming else R.color.telegram_outgoing
            )
            text.text = message.text
            time.text = message.time
        }
    }

    private companion object {
        const val DATE_VIEW_TYPE = 0
        const val MESSAGE_VIEW_TYPE = 1
    }
}
