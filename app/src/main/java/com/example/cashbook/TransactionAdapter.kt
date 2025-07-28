package com.example.cashbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter(private val context: android.content.Context) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private val transactions = mutableListOf<Transaction>()
    private var onTransactionLongClickListener: OnTransactionLongClickListener? = null
    private var onTransactionClickListener: OnTransactionClickListener? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    interface OnTransactionLongClickListener {
        fun onTransactionLongClick(transaction: Transaction)
        fun onTransactionCompleted()
    }

    interface OnTransactionClickListener {
        fun onTransactionClick(transaction: Transaction)
    }

    fun setOnTransactionLongClickListener(listener: OnTransactionLongClickListener) {
        onTransactionLongClickListener = listener
    }

    fun setOnTransactionClickListener(listener: OnTransactionClickListener) {
        onTransactionClickListener = listener
    }

    fun setTransactions(newTransactions: List<Transaction>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }

    fun getTransactions(): List<Transaction> = transactions.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        holder.bind(transaction)
    }

    override fun getItemCount(): Int = transactions.size

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val amountText: TextView = itemView.findViewById(R.id.amountText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)

        fun bind(transaction: Transaction) {
            descriptionText.text = transaction.description
            amountText.text = String.format("₹%.2f", transaction.amount)
            amountText.setTextColor(if (transaction.isCashIn) context.getColor(R.color.income) else context.getColor(R.color.expense))
            dateText.text = dateFormat.format(transaction.date)

            itemView.setOnLongClickListener {
                onTransactionLongClickListener?.onTransactionLongClick(transaction)
                true
            }
            itemView.setOnClickListener {
                onTransactionClickListener?.onTransactionClick(transaction)
            }
        }
    }
}