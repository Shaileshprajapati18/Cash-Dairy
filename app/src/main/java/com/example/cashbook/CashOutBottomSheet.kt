package com.example.cashbook

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

    class CashOutDialog : DialogFragment() {
    private lateinit var amountEditText: EditText
    private lateinit var detailsEditText: EditText
    private lateinit var dateText: TextView
    private lateinit var saveButton: Button
    private lateinit var datePickerButton: LinearLayout
    private lateinit var dbHelper: DatabaseHelper
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private var transactionToEdit: Transaction? = null

    companion object {
        private const val ARG_TRANSACTION = "transaction"

        fun newInstance(transaction: Transaction? = null): CashOutDialog {
            val fragment = CashOutDialog()
            transaction?.let {
                val args = Bundle().apply {
                    putParcelable(ARG_TRANSACTION, it)
                }
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transactionToEdit = arguments?.getParcelable(ARG_TRANSACTION)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.cash_out_transaction, null)

        dbHelper = DatabaseHelper(requireContext())
        amountEditText = view.findViewById(R.id.amountEditText)
        detailsEditText = view.findViewById(R.id.detailsEditText)
        dateText = view.findViewById(R.id.dateText)
        saveButton = view.findViewById(R.id.saveButton)
        datePickerButton = view.findViewById(R.id.datePickerButton)

        if (transactionToEdit != null) {
            prefillFields(transactionToEdit!!)
            saveButton.text = "Update"
        } else {
            setInitialDate()
        }

        setupDatePicker()
        setupTextWatcher()
        setupSaveButton()

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setCancelable(true)
            .create()
    }

    private fun setInitialDate() {
        val currentDate = Date()
        dateText.text = dateFormat.format(currentDate)
    }

    private fun prefillFields(transaction: Transaction) {
        amountEditText.setText(transaction.amount.toString())
        detailsEditText.setText(transaction.description)
        dateText.text = dateFormat.format(transaction.date)
        saveButton.isEnabled = true
    }

    private fun setupDatePicker() {
        datePickerButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            transactionToEdit?.date?.let { calendar.time = it }
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    TimePickerDialog(
                        requireContext(),
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)
                            dateText.text = dateFormat.format(calendar.time)
                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupTextWatcher() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                saveButton.isEnabled = amountEditText.text.toString().trim().isNotEmpty() &&
                        detailsEditText.text.toString().trim().isNotEmpty()
            }
        }
        amountEditText.addTextChangedListener(textWatcher)
        detailsEditText.addTextChangedListener(textWatcher)
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            val amountStr = amountEditText.text.toString().trim()
            val details = detailsEditText.text.toString().trim()

            var isValid = true
            if (amountStr.isEmpty()) {
                amountEditText.error = "Amount is required"
                isValid = false
            } else {
                amountEditText.error = null
            }
            if (details.isEmpty()) {
                detailsEditText.error = "Details are required"
                isValid = false
            } else {
                detailsEditText.error = null
            }
            if (isValid) {
                try {
                    val amount = amountStr.toDouble()
                    val selectedDate = dateFormat.parse(dateText.text.toString()) ?: Date()
                    val transaction = Transaction(
                        id = transactionToEdit?.id ?: 0,
                        description = details,
                        amount = amount,
                        isCashIn = false,
                        date = selectedDate
                    )

                    val result = if (transactionToEdit != null) {
                        dbHelper.updateTransaction(transaction)
                    } else {
                        dbHelper.addTransaction(transaction)
                    }

                    if (result > 0) {
                        (requireActivity() as MainActivity).loadTransactions()
                        (requireActivity() as MainActivity).onTransactionCompleted()  // Show ad if needed

                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save transaction", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    amountEditText.error = "Invalid amount"
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}