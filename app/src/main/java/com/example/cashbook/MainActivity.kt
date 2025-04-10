package com.example.cashbook

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity(), TransactionAdapter.OnTransactionLongClickListener, TransactionAdapter.OnTransactionClickListener {
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var totalBalanceText: TextView
    private lateinit var incomeText: TextView
    private lateinit var expenseText: TextView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var btnCashout: Button
    private lateinit var btnCashin: Button
    private lateinit var animationView: LottieAnimationView
    private lateinit var monthYearText: TextView
    private lateinit var monthDropdown: ImageView
    private lateinit var reportButton: ImageView

    private val CREATE_DOCUMENT_REQUEST_CODE = 101
    private val NOTIFICATION_PERMISSION_CODE = 102
    private val NOTIFICATION_CHANNEL_ID = "CashBookReports"
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMMM - yyyy", Locale.getDefault())
    private val transactionDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val numberFormat = DecimalFormat("#,##0.##")

    private var transactionsToSave: List<Transaction>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        initViews()
        setupListeners()
        updateMonthYearDisplay()
        loadTransactions()
        createNotificationChannel()
        checkNotificationPermission() // Check for notification permission on start
    }

    private fun initViews() {
        transactionsRecyclerView = findViewById(R.id.transactionsRecyclerView)
        totalBalanceText = findViewById(R.id.totalBalanceText)
        incomeText = findViewById(R.id.incomeText)
        expenseText = findViewById(R.id.expenseText)
        btnCashout = findViewById(R.id.btn_cashout)
        btnCashin = findViewById(R.id.btn_cashin)
        animationView = findViewById(R.id.animationView)
        monthYearText = findViewById(R.id.monthYearText)
        monthDropdown = findViewById(R.id.monthDropdown)
        reportButton = findViewById(R.id.report)

        transactionAdapter = TransactionAdapter(this).apply {
            setOnTransactionLongClickListener(this@MainActivity)
            setOnTransactionClickListener(this@MainActivity)
        }
        transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = transactionAdapter
        }
    }

    private fun setupListeners() {
        btnCashin.setOnClickListener {
            CashInBottomSheet.newInstance().show(supportFragmentManager, "CashInBottomSheet")
        }
        btnCashout.setOnClickListener {
            CashOutBottomSheet.newInstance().show(supportFragmentManager, "CashOutBottomSheet")
        }
        monthDropdown.setOnClickListener {
            showMonthPicker()
        }
        reportButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Generate Report")
                .setMessage("Do you want to generate the PDF report for this month?")
                .setPositiveButton("Yes") { _, _ -> generateReport() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateMonthYearDisplay() {
        monthYearText.text = dateFormat.format(calendar.time)
    }

    private fun showMonthPicker() {
        val months = (1..12).map { Calendar.getInstance().apply { set(Calendar.MONTH, it - 1) }.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) }
        val years = (2020..2030).toList()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        AlertDialog.Builder(this)
            .setTitle("Select Month and Year")
            .setSingleChoiceItems(months.toTypedArray(), currentMonth) { _, monthIndex ->
                calendar.set(Calendar.MONTH, monthIndex)
            }
            .setPositiveButton("OK") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Select Year")
                    .setSingleChoiceItems(years.map { it.toString() }.toTypedArray(), years.indexOf(currentYear)) { _, yearIndex ->
                        calendar.set(Calendar.YEAR, years[yearIndex])
                    }
                    .setPositiveButton("OK") { _, _ ->
                        updateMonthYearDisplay()
                        loadTransactions()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onTransactionLongClick(transaction: Transaction) {
        showDeleteConfirmationDialog(transaction)
    }

    override fun onTransactionClick(transaction: Transaction) {
        val fragment = if (transaction.isCashIn) CashInBottomSheet.newInstance(transaction) else CashOutBottomSheet.newInstance(transaction)
        fragment.show(supportFragmentManager, if (transaction.isCashIn) "CashInBottomSheet" else "CashOutBottomSheet")
    }

    private fun showDeleteConfirmationDialog(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                if (dbHelper.deleteTransaction(transaction.id) > 0) {
                    loadTransactions()
                    Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun loadTransactions() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val transactions = dbHelper.getTransactionsByMonth(year, month)
        transactionAdapter.setTransactions(transactions)
        updateSummary()

        if (transactions.isEmpty()) {
            transactionsRecyclerView.visibility = View.GONE
            animationView.visibility = View.VISIBLE
            animationView.playAnimation()
        } else {
            transactionsRecyclerView.visibility = View.VISIBLE
            animationView.visibility = View.GONE
            animationView.cancelAnimation()
        }
    }

    private fun updateSummary() {
        val transactions = transactionAdapter.getTransactions()
        val totalIncome = transactions.filter { it.isCashIn }.sumOf { it.amount }
        val totalExpense = transactions.filter { !it.isCashIn }.sumOf { it.amount }
        val balance = totalIncome - totalExpense

        totalBalanceText.text = String.format("₹%.2f", balance)
        incomeText.text = String.format("₹%.2f", totalIncome)
        expenseText.text = String.format("₹%.2f", totalExpense)
    }

    private fun generateReport() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val transactions = dbHelper.getTransactionsByMonth(year, month)

        if (transactions.isEmpty()) {
            Toast.makeText(this, "No transactions found for ${dateFormat.format(calendar.time)}", Toast.LENGTH_SHORT).show()
            return
        }

        transactionsToSave = transactions
        val fileName = "Cash_Diary_${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.MONTH) + 1}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents"))
        }
        startActivityForResult(intent, CREATE_DOCUMENT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val transactions = transactionsToSave ?: return
                    val totalIncome = transactions.filter { it.isCashIn }.sumOf { it.amount }
                    val totalExpense = transactions.filter { !it.isCashIn }.sumOf { it.amount }
                    val balance = totalIncome - totalExpense

                    val pdfDocument = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    val paint = Paint()

                    drawTextCentered(canvas, "Cash Book", 24f, 50f, paint)
                    drawTextCentered(canvas, dateFormat.format(calendar.time), 14f, 80f, paint)
                    drawTableHeaders(canvas, paint)

                    var yPosition = 145f
                    runningBalance = 0.0
                    transactions.forEach { transaction ->
                        drawTransactionRow(canvas, paint, transaction, yPosition)
                        yPosition += 25f
                    }

                    drawSummaryTable(canvas, paint, totalIncome, totalExpense, balance, yPosition + 10f)
                    pdfDocument.finishPage(page)

                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                        outputStream.close()
                        val fileName = "Cash_Diary_${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.MONTH) + 1}.pdf"
                        showNotification(fileName, uri)

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                        }
                    }
                    pdfDocument.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to save PDF: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    transactionsToSave = null
                }
            } ?: run {
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_CODE)
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Cash Book Reports"
            val descriptionText = "Notifications for generated reports"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(fileName: String, uri: Uri) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rupees)
            .setContentTitle("Cash Dairy")
            .setContentText("$fileName saved in Documents")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(1, notification) // Use a fixed ID for testing
            } else {
                Toast.makeText(this, "Notification not shown due to missing permission", Toast.LENGTH_SHORT).show()
            }
        } else {
            notificationManager.notify(1, notification) // Use a fixed ID for testing
        }
    }

    private fun drawTextCentered(canvas: Canvas, text: String, textSize: Float, yPosition: Float, paint: Paint) {
        paint.textSize = textSize
        paint.color = Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val textWidth = paint.measureText(text)
        canvas.drawText(text, (595 - textWidth) / 2, yPosition, paint)
    }

    private fun drawTableHeaders(canvas: Canvas, paint: Paint) {
        val headers = listOf("Date", "Notes", "Cash In", "Cash Out", "Balance")
        val columnWidths = listOf(100f, 160f, 80f, 80f, 80f)
        val startX = 50f
        val startY = 120f
        val rowHeight = 25f

        paint.textSize = 14f
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        canvas.drawRect(startX, startY, startX + columnWidths.sum(), startY + rowHeight, paint)

        paint.style = Paint.Style.FILL
        var x = startX
        headers.forEachIndexed { index, header ->
            canvas.drawText(header, x + 5f, startY + 18f, paint)
            x += columnWidths[index]
            if (index != headers.lastIndex) {
                canvas.drawLine(x, startY, x, startY + rowHeight, paint)
            }
        }
    }

    private var runningBalance = 0.0
    private fun drawTransactionRow(canvas: Canvas, paint: Paint, transaction: Transaction, yPosition: Float) {
        val cashIn = if (transaction.isCashIn) transaction.amount else 0.0
        val cashOut = if (!transaction.isCashIn) transaction.amount else 0.0
        runningBalance += cashIn - cashOut

        val formattedCashIn = numberFormat.format(cashIn)
        val formattedCashOut = numberFormat.format(cashOut)
        val formattedBalance = numberFormat.format(runningBalance)

        val values = listOf(transactionDateFormat.format(transaction.date), transaction.description, formattedCashIn, formattedCashOut, formattedBalance)
        val columnWidths = listOf(100f, 160f, 80f, 80f, 80f)
        val startX = 50f
        val rowHeight = 25f

        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(startX, yPosition, startX + columnWidths.sum(), yPosition + rowHeight, paint)

        paint.style = Paint.Style.FILL
        var x = startX
        values.forEachIndexed { index, value ->
            canvas.drawText(value, x + 5f, yPosition + 18f, paint)
            x += columnWidths[index]
            if (index != values.lastIndex) {
                canvas.drawLine(x, yPosition, x, yPosition + rowHeight, paint)
            }
        }
    }

    private fun drawSummaryTable(canvas: Canvas, paint: Paint, totalIncome: Double, totalExpense: Double, balance: Double, yPosition: Float) {
        val summaryData = listOf("Total Cash In" to numberFormat.format(totalIncome), "Total Cash Out" to numberFormat.format(totalExpense), "Balance" to numberFormat.format(balance))
        paint.textSize = 14f
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL

        val tableWidth = 400f
        val tableHeight = summaryData.size * 30f
        val startX = (595 - tableWidth) / 2f
        val startY = yPosition
        val midX = startX + tableWidth / 2

        paint.style = Paint.Style.STROKE
        canvas.drawRect(startX, startY, startX + tableWidth, startY + tableHeight, paint)

        summaryData.forEachIndexed { i, (label, value) ->
            val topY = startY + i * 30f
            val bottomY = topY + 30f
            canvas.drawLine(startX, bottomY, startX + tableWidth, bottomY, paint)
            canvas.drawLine(midX, startY, midX, startY + tableHeight, paint)
            paint.style = Paint.Style.FILL
            canvas.drawText(label, startX + 20f, topY + 20f, paint)
            canvas.drawText(value, midX + 20f, topY + 20f, paint)
            paint.style = Paint.Style.STROKE
        }
        paint.style = Paint.Style.FILL
    }
}