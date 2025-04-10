package com.example.cashbook

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CashInBottomSheet : BottomSheetDialogFragment() {
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

        fun newInstance(transaction: Transaction? = null): CashInBottomSheet {
            val fragment = CashInBottomSheet()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.cash_in_transaction, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

            // Check for empty fields and set error messages
            var isValid = true
            if (amountStr.isEmpty()) {
                amountEditText.setError("Amount is required")
                isValid = false
            } else {
                amountEditText.setError(null) // Clear error if valid
            }
            if (details.isEmpty()) {
                detailsEditText.setError("Details are required")
                isValid = false
            } else {
                detailsEditText.setError(null) // Clear error if valid
            }

            // Proceed only if both fields are valid
            if (isValid) {
                try {
                    val amount = amountStr.toDouble()
                    val selectedDate = dateFormat.parse(dateText.text.toString()) ?: Date()
                    val transaction = Transaction(
                        id = transactionToEdit?.id ?: 0,
                        description = details,
                        amount = amount,
                        isCashIn = true,
                        date = selectedDate
                    )

                    val result = if (transactionToEdit != null) {
                        dbHelper.updateTransaction(transaction)
                    } else {
                        dbHelper.addTransaction(transaction)
                    }

                    if (result > 0) {
                        (requireActivity() as MainActivity).loadTransactions()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save transaction", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    amountEditText.setError("Invalid amount")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}