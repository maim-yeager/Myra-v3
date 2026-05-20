package com.myra.assistant.ui.main

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.model.ChatMessage

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(message: ChatMessage) {
        val lastMyra = messages.lastOrNull { !it.isUser }
        if (lastMyra?.text == message.text && !message.isUser) {
            return
        }
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun lastMyraText(): String? {
        return messages.lastOrNull { !it.isUser }?.text
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val avatarDot: View = itemView.findViewById(R.id.avatarDot)

        fun bind(message: ChatMessage) {
            messageText.text = message.text

            if (message.isUser) {
                messageText.setBackgroundResource(R.drawable.chat_bubble_user)
                (messageText.layoutParams as? LinearLayout.LayoutParams)?.gravity = Gravity.END
                avatarDot.visibility = View.GONE
            } else {
                messageText.setBackgroundResource(R.drawable.chat_bubble_myra)
                (messageText.layoutParams as? LinearLayout.LayoutParams)?.gravity = Gravity.START
                avatarDot.visibility = View.VISIBLE
            }
        }
    }
}