package com.example.cashbook

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
    private lateinit var toolbarIcon: ImageView
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var usernameText: TextView
    private lateinit var emailText: TextView
    private lateinit var profileImage: ImageView
    private lateinit var signInButton: Button
    private val CREATE_DOCUMENT_REQUEST_CODE = 101
    private val NOTIFICATION_PERMISSION_CODE = 102
    private val GOOGLE_SIGN_IN_REQUEST_CODE = 103
    private val GOOGLE_SIGNIN_BACKUP_REQUEST_CODE = 104
    private val GOOGLE_SIGNIN_RESTORE_REQUEST_CODE = 105
    private val GOOGLE_AUTH_CONSENT_REQUEST_CODE = 106
    private val NOTIFICATION_CHANNEL_ID = "CashBookReports"
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("MMMM - yyyy", Locale.getDefault())
    private val transactionDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val numberFormat = DecimalFormat("#,##0.##")
    private var adView: AdView? = null
    private val currentYear = calendar.get(Calendar.YEAR)
    private val years = (2025..2035).toList()
    private var transactionsToSave: List<Transaction>? = null
    private var runningBalance = 0.0
    private lateinit var sharedPreferences: SharedPreferences
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false
    private var adLoadRetryCount = 0
    private val MAX_AD_LOAD_RETRIES = 3
    private val ACTION_THRESHOLD = 3
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval: Long = 30000
    private var adsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        window.statusBarColor = ContextCompat.getColor(this, R.color.black)

        sharedPreferences = getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        dbHelper = DatabaseHelper(this)
        initGoogleSignIn()
        initViews()
        setupListeners()
        updateMonthYearDisplay()
        loadTransactions()
        createNotificationChannel()
        checkNotificationPermission()
        updateNavHeader()
    }

    private fun initializeAds() {
        if (!adsInitialized) {
            MobileAds.initialize(this) {}
            adsInitialized = true
            loadBannerAd()
            startAdRefreshLoop()
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }
            })
    }

    override fun onTransactionCompleted() {
        if (!adsInitialized) {
            initializeAds()
            loadInterstitialAd()
        }

        val count = sharedPreferences.getInt("action_count", 0) + 1
        sharedPreferences.edit().putInt("action_count", count).apply()

        if (count >= ACTION_THRESHOLD && interstitialAd != null) {
            interstitialAd?.show(this)
            sharedPreferences.edit().putInt("action_count", 0).apply()
            loadInterstitialAd()
        }
    }

    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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
        toolbarIcon = findViewById(R.id.toolbar_icon)
        mDrawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        adView = findViewById(R.id.adView)

        val headerView = navigationView.getHeaderView(0)
        usernameText = headerView.findViewById(R.id.username)
        emailText = headerView.findViewById(R.id.email)
        profileImage = headerView.findViewById(R.id.profile_image)
        signInButton = headerView.findViewById(R.id.btnGoogleSignIn)

        transactionAdapter = TransactionAdapter(this).apply {
            setOnTransactionLongClickListener(this@MainActivity)
            setOnTransactionClickListener(this@MainActivity)
        }
        transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = transactionAdapter
        }

        val toggle = ActionBarDrawerToggle(
            this, mDrawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        mDrawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun setupListeners() {
        btnCashin.setOnClickListener {
            CashInDialog.newInstance().show(supportFragmentManager, "CashInDialog")
        }
        btnCashout.setOnClickListener {
            CashOutDialog.newInstance().show(supportFragmentManager, "CashOutDialog")
        }
        monthDropdown.setOnClickListener {
            showMonthPicker()
        }
        toolbarIcon.setOnClickListener {
            mDrawerLayout.openDrawer(GravityCompat.START)
        }
        reportButton.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_generate_report, null)
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            dialog.show()

            val btnYes = dialogView.findViewById<Button>(R.id.btn_yes)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

            btnYes.setOnClickListener {
                dialog.dismiss()
                if (!adsInitialized) {
                    initializeAds()
                }
                showRewardedAdForReport()
            }
            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
        }
        signInButton.setOnClickListener {
            if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
            } else {
                googleSignInClient.signOut().addOnCompleteListener {
                    FirebaseAuth.getInstance().signOut()
                    updateNavHeader()
                    Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_sign_out -> {
                    val dialogView =
                        LayoutInflater.from(this).inflate(R.layout.custom_logout_dialog, null)
                    val alertDialog = AlertDialog.Builder(this)
                        .setView(dialogView)
                        .create()

                    val btnYes = dialogView.findViewById<Button>(R.id.btn_yes)
                    val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

                    btnYes.setOnClickListener {
                        googleSignInClient.signOut().addOnCompleteListener {
                            FirebaseAuth.getInstance().signOut()
                            updateNavHeader()
                            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT)
                                .show()
                        }
                        mDrawerLayout.closeDrawer(GravityCompat.START)
                        alertDialog.dismiss()
                    }

                    btnCancel.setOnClickListener {
                        alertDialog.dismiss()
                    }

                    alertDialog.show()
                    true
                }

                R.id.nav_backup -> {
                    if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                        startActivityForResult(
                            googleSignInClient.signInIntent,
                            GOOGLE_SIGNIN_BACKUP_REQUEST_CODE
                        )
                    } else {
                        backupToGoogleDrive()
                    }
                    mDrawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                R.id.nav_restore -> {
                    if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                        startActivityForResult(
                            googleSignInClient.signInIntent,
                            GOOGLE_SIGNIN_RESTORE_REQUEST_CODE
                        )
                    } else {
                        showRestoreConfirmationDialog()
                    }
                    mDrawerLayout.closeDrawer(GravityCompat.START)
                    true
                }

                else -> false
            }
        }
    }

    private fun updateNavHeader() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val signOutMenuItem = navigationView.menu.findItem(R.id.nav_sign_out)
        val backupMenuItem = navigationView.menu.findItem(R.id.nav_backup)
        val restoreMenuItem = navigationView.menu.findItem(R.id.nav_restore)
        if (account != null) {
            usernameText.text = account.displayName ?: "User"
            emailText.text = account.email ?: "No email"
            signInButton.visibility = View.GONE
            signOutMenuItem.isVisible = true
            backupMenuItem?.isVisible = true
            restoreMenuItem?.isVisible = true
            Glide.with(this)
                .load(account.photoUrl)
                .placeholder(R.drawable.profile_pic)
                .error(R.drawable.profile_pic)
                .into(profileImage)
        } else {
            usernameText.text = "Guest"
            emailText.text = "Not signed in"
            signInButton.visibility = View.VISIBLE
            signOutMenuItem.isVisible = false
            backupMenuItem?.isVisible = false
            restoreMenuItem?.isVisible = false
            profileImage.setImageResource(R.drawable.profile_pic)
        }
    }

    private fun backupToGoogleDrive() {
        val transactions = dbHelper.getAllTransactions()
        if (transactions.isEmpty()) {
            Toast.makeText(this, "No transactions to backup", Toast.LENGTH_SHORT).show()
            return
        }

        val backupFile = BackupRestoreHelper.createBackupFile(this, transactions)
        BackupRestoreHelper.DriveServiceHelper.backupToDrive(
            context = this,
            backupFile = backupFile,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Backup successful", Toast.LENGTH_SHORT).show()
                    backupFile.delete()
                }
            },
            onFailure = { e ->
                runOnUiThread {
                    Log.e("Drive", "Backup failed: ${e.message}", e)
                    Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    backupFile.delete()
                }
            },
            onAuthNeeded = { e ->
                runOnUiThread {
                    Log.i("Drive", "Consent required for backup")
                    startActivityForResult(e.intent, GOOGLE_AUTH_CONSENT_REQUEST_CODE)
                }
            }
        )
    }

    private fun restoreFromGoogleDrive() {
        BackupRestoreHelper.DriveServiceHelper.restoreFromDrive(
            context = this,
            onSuccess = { inputStreamReader ->
                val transactions = BackupRestoreHelper.restoreTransactions(this, inputStreamReader)
                BackupRestoreHelper.clearDatabase(dbHelper)
                BackupRestoreHelper.insertTransactions(dbHelper, transactions)
                runOnUiThread {
                    loadTransactions()
                    Toast.makeText(this, "Restore successful", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = { e ->
                runOnUiThread {
                    Log.e("Drive", "Restore failed: ${e.message}", e)
                    Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            onAuthNeeded = { e ->
                runOnUiThread {
                    Log.i("Drive", "Consent required for restore")
                    startActivityForResult(e.intent, GOOGLE_AUTH_CONSENT_REQUEST_CODE)
                }
            }
        )
    }

    private fun showRestoreConfirmationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.custom_restore_dialog, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val btnYes = dialogView.findViewById<Button>(R.id.btnYes)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)

        btnYes.setOnClickListener {
            restoreFromGoogleDrive()
            alertDialog.dismiss()
        }

        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun updateMonthYearDisplay() {
        monthYearText.text = dateFormat.format(calendar.time)
    }

    private fun showMonthPicker() {
        val months = (1..12).map {
            Calendar.getInstance().apply { set(Calendar.MONTH, it - 1) }
                .getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
        }
        val currentMonth = calendar.get(Calendar.MONTH)

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_month, null)
        val listView = dialogView.findViewById<ListView>(R.id.month_list)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, months)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(currentMonth, true)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        listView.setOnItemClickListener { _, _, position, _ ->
            calendar.set(Calendar.MONTH, position)
            dialog.dismiss()
            showYearDialog()
        }
    }

    private fun showYearDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_year, null)
        val listView = dialogView.findViewById<ListView>(R.id.year_list)

        val yearStrings = years.map { it.toString() }
        val adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, yearStrings)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setItemChecked(years.indexOf(currentYear), true)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        listView.setOnItemClickListener { _, _, position, _ ->
            calendar.set(Calendar.YEAR, years[position])
            dialog.dismiss()
            updateMonthYearDisplay()
            loadTransactions()
        }
    }

    override fun onTransactionLongClick(transaction: Transaction) {
        showDeleteConfirmationDialog(transaction)
    }

    override fun onTransactionClick(transaction: Transaction) {
        val fragment = if (transaction.isCashIn) {
            CashInDialog.newInstance(transaction)
        } else {
            CashOutDialog.newInstance(transaction)
        }
        fragment.show(
            supportFragmentManager,
            if (transaction.isCashIn) "CashInDialog" else "CashOutDialog"
        )
    }

    private fun showDeleteConfirmationDialog(transaction: Transaction) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.custom_delete_dialog, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val deleteButton = dialogView.findViewById<Button>(R.id.deleteButton)

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        deleteButton.setOnClickListener {
            if (dbHelper.deleteTransaction(transaction.id) > 0) {
                loadTransactions()
                Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
            }
            alertDialog.dismiss()
        }

        alertDialog.show()
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
            Toast.makeText(
                this,
                "No transactions found for ${dateFormat.format(calendar.time)}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        transactionsToSave = transactions
        val fileName =
            "Cash_Diary_${calendar.get(Calendar.YEAR)}_${calendar.get(Calendar.MONTH) + 1}.pdf"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                Uri.parse("content://com.android.externalstorage.documents/document/primary:Documents")
            )
        }
        startActivityForResult(intent, CREATE_DOCUMENT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GOOGLE_SIGN_IN_REQUEST_CODE -> {
                try {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                    val account = task.getResult(ApiException::class.java)
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                updateNavHeader()
                                Toast.makeText(
                                    this,
                                    "Signed in as ${account.displayName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Firebase sign-in failed: ${authTask.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                                updateNavHeader()
                            }
                        }
                } catch (e: ApiException) {
                    Toast.makeText(
                        this,
                        "Google Sign-In failed: ${e.statusCode}",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateNavHeader()
                }
            }

            GOOGLE_SIGNIN_BACKUP_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                        task.getResult(ApiException::class.java)
                        backupToGoogleDrive()
                    } catch (e: ApiException) {
                        Toast.makeText(
                            this,
                            "Backup Sign-In failed: ${e.statusCode}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            GOOGLE_SIGNIN_RESTORE_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    try {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                        task.getResult(ApiException::class.java)
                        showRestoreConfirmationDialog()
                    } catch (e: ApiException) {
                        Toast.makeText(
                            this,
                            "Restore Sign-In failed: ${e.statusCode}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            GOOGLE_AUTH_CONSENT_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Consent granted, retrying operation", Toast.LENGTH_SHORT)
                        .show()
                    backupToGoogleDrive()
                } else {
                    Toast.makeText(
                        this,
                        "Consent denied, cannot proceed with Drive operation",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            CREATE_DOCUMENT_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        try {
                            val transactions = transactionsToSave ?: return
                            val totalIncome =
                                transactions.filter { it.isCashIn }.sumOf { it.amount }
                            val totalExpense =
                                transactions.filter { !it.isCashIn }.sumOf { it.amount }
                            val balance = totalIncome - totalExpense

                            val pdfDocument = PdfDocument()
                            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                            val page = pdfDocument.startPage(pageInfo)
                            val canvas = page.canvas
                            val paint = Paint()

                            drawTextCentered(canvas, "Cash Diary", 24f, 50f, paint)
                            drawTextCentered(
                                canvas,
                                dateFormat.format(calendar.time),
                                14f,
                                80f,
                                paint
                            )
                            drawTableHeaders(canvas, paint)

                            var yPosition = 145f
                            runningBalance = 0.0
                            transactions.forEach { transaction ->
                                drawTransactionRow(canvas, paint, transaction, yPosition)
                                yPosition += 25f
                            }

                            drawSummaryTable(
                                canvas,
                                paint,
                                totalIncome,
                                totalExpense,
                                balance,
                                yPosition + 10f
                            )
                            pdfDocument.finishPage(page)

                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                pdfDocument.writeTo(outputStream)
                                outputStream.close()
                                val fileName = "Cash_Diary_${calendar.get(Calendar.YEAR)}_${
                                    calendar.get(Calendar.MONTH) + 1
                                }.pdf"
                                showNotification(fileName, uri)

                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "No PDF viewer found", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                            pdfDocument.close()
                        } catch (e: Exception) {
                            Log.e("PDF", "Failed to save PDF: ${e.message}", e)
                            Toast.makeText(
                                this,
                                "Failed to save PDF: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            transactionsToSave = null
                        }
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
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
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(fileName: String, uri: Uri) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            .setContentTitle("Cash Diary")
            .setContentText("$fileName saved in Documents")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(1, notification)
            } else {
                Toast.makeText(
                    this,
                    "Notification not shown due to missing permission",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            notificationManager.notify(1, notification)
        }
    }

    private fun drawTextCentered(
        canvas: Canvas,
        text: String,
        textSize: Float,
        yPosition: Float,
        paint: Paint
    ) {
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

    private fun drawTransactionRow(
        canvas: Canvas,
        paint: Paint,
        transaction: Transaction,
        yPosition: Float
    ) {
        val cashIn = if (transaction.isCashIn) transaction.amount else 0.0
        val cashOut = if (!transaction.isCashIn) transaction.amount else 0.0
        runningBalance += cashIn - cashOut

        val formattedCashIn = numberFormat.format(cashIn)
        val formattedCashOut = numberFormat.format(cashOut)
        val formattedBalance = numberFormat.format(runningBalance)

        val values = listOf(
            transactionDateFormat.format(transaction.date),
            transaction.description,
            formattedCashIn,
            formattedCashOut,
            formattedBalance
        )
        val columnWidths = listOf(100f, 160f, 80f, 80f, 80f)
        val startX = 50f
        val rowHeight = 25f

        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(
            startX,
            yPosition,
            startX + columnWidths.sum(),
            yPosition + rowHeight,
            paint
        )

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

    private fun drawSummaryTable(
        canvas: Canvas,
        paint: Paint,
        totalIncome: Double,
        totalExpense: Double,
        balance: Double,
        yPosition: Float
    ) {
        val summaryData = listOf(
            "Total Cash In" to numberFormat.format(totalIncome),
            "Total Cash Out" to numberFormat.format(totalExpense),
            "Balance" to numberFormat.format(balance)
        )
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

    private fun loadBannerAd() {
        adView?.let {
            val adRequest = AdRequest.Builder().build()
            it.loadAd(adRequest)
        } ?: run {
            Log.e("MainActivity", "adView is null, cannot load banner ad")
        }
    }

    private fun startAdRefreshLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (adView != null) {
                    loadBannerAd()
                    handler.postDelayed(this, refreshInterval)
                }
            }
        }, refreshInterval)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        adView?.destroy()
        super.onDestroy()
    }

    private fun showRewardedAdForReport() {
        if (isAdLoading) {
            return
        }

        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    Snackbar.make(
                        findViewById(R.id.drawer_layout),
                        "Failed to show ad. Generating report anyway.",
                        Snackbar.LENGTH_LONG
                    ).show()
                    generateReport()
                    loadRewardedAd()
                }

                override fun onAdShowedFullScreenContent() {
                    // No action needed
                }
            }

            rewardedAd?.show(this) { rewardItem ->
                Snackbar.make(
                    findViewById(R.id.drawer_layout),
                    "Report unlocked! Generating PDF...",
                    Snackbar.LENGTH_SHORT
                ).show()
                generateReport()
            }
        } else {
            loadRewardedAd()
        }
    }

    private fun loadRewardedAd() {
        if (isAdLoading || adLoadRetryCount >= MAX_AD_LOAD_RETRIES) {
            if (adLoadRetryCount >= MAX_AD_LOAD_RETRIES) {
                Snackbar.make(
                    findViewById(R.id.drawer_layout),
                    "Unable to load ad. Generating report anyway.",
                    Snackbar.LENGTH_LONG
                ).show()
                generateReport()
            }
            return
        }

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            this,
            "ca-app-pub-3940256099942544/5224354917",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isAdLoading = false
                    adLoadRetryCount = 0
                    rewardedAd = ad
                    showRewardedAdForReport() // Show ad immediately after loading
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    isAdLoading = false
                    adLoadRetryCount++
                    rewardedAd = null

                    if (adLoadRetryCount < MAX_AD_LOAD_RETRIES) {
                        handler.postDelayed({ loadRewardedAd() }, 2000)
                    } else {
                        Snackbar.make(
                            findViewById(R.id.drawer_layout),
                            "Unable to load ad after $MAX_AD_LOAD_RETRIES attempts. Generating report anyway.",
                            Snackbar.LENGTH_LONG
                        ).show()
                        generateReport()
                    }
                }
            })
    }
}