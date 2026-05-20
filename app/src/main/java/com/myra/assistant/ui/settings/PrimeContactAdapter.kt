package com.myra.assistant.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.model.PrimeContact

class PrimeContactAdapter(
    private var contacts: List<PrimeContact>,
    private val onDelete: (PrimeContact) -> Unit
) : RecyclerView.Adapter<PrimeContactAdapter.ViewHolder>() {

    fun updateContacts(newContacts: List<PrimeContact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_prime_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.bind(contact)
    }

    override fun getItemCount(): Int = contacts.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.contactName)
        private val numberText: TextView = itemView.findViewById(R.id.contactNumber)
        private val deleteBtn: ImageButton = itemView.findViewById(R.id.deleteBtn)

        fun bind(contact: PrimeContact) {
            nameText.text = contact.name
            numberText.text = contact.number
            deleteBtn.setOnClickListener { onDelete(contact) }
        }
    }
}