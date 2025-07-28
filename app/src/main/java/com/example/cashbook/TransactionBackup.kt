package com.example.cashbook

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.Date

data class TransactionBackup(val transactions: List<Transaction>)

object BackupRestoreHelper {
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, object : TypeAdapter<Date>() {
            override fun write(out: JsonWriter, value: Date?) {
                out.value(value?.time)
            }
            override fun read(`in`: JsonReader): Date {
                return Date(`in`.nextLong())
            }
        })
        .create()

    fun createBackupFile(context: Context, transactions: List<Transaction>): java.io.File {
        val backupData = TransactionBackup(transactions)
        val json = gson.toJson(backupData)
        val file = java.io.File(context.cacheDir, "cashbook_backup.json")
        FileWriter(file).use { it.write(json) }
        return file
    }

    fun restoreTransactions(context: Context, inputStream: InputStreamReader): List<Transaction> {
        val type = object : TypeToken<TransactionBackup>() {}.type
        val backupData: TransactionBackup = gson.fromJson(inputStream, type)
        return backupData.transactions
    }

    fun clearDatabase(dbHelper: DatabaseHelper) {
        dbHelper.clearAllTransactions()
    }

    fun insertTransactions(dbHelper: DatabaseHelper, transactions: List<Transaction>) {
        transactions.forEach { transaction ->
            dbHelper.insertTransaction(
                transaction.description,
                transaction.amount,
                transaction.date.time,
                transaction.isCashIn
            )
        }
    }

    object DriveServiceHelper {
        fun getDriveService(context: Context): Drive? {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = account.account
            return try {
                Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName(context.getString(R.string.app_name))
                    .build()
                    .also { Log.i("Drive", "Drive service initialized: ${it != null}") }
            } catch (e: Exception) {
                Log.e("Permission", "Drive service failed: ${e.message}")
                null
            }
        }

        fun backupToDrive(
            context: Context,
            backupFile: java.io.File,
            onSuccess: () -> Unit,
            onFailure: (Exception) -> Unit,
            onAuthNeeded: (UserRecoverableAuthIOException) -> Unit
        ) {
            val driveService = getDriveService(context) ?: return onFailure(Exception("No signed-in account"))
            Thread {
                try {
                    val files = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='cashbook_backup.json'")
                        .setFields("files(id)")
                        .execute()
                    Log.i("Drive", "Files found: ${files.files.size}")
                    if (!files.files.isEmpty()) {
                        driveService.files().delete(files.files[0].id).execute()
                    }

                    val metadata = File().apply {
                        name = "cashbook_backup.json"
                        parents = listOf("appDataFolder")
                        mimeType = "application/json"
                    }
                    val content = ByteArrayOutputStream().use { output ->
                        backupFile.inputStream().use { input -> input.copyTo(output) }
                        output.toByteArray()
                    }
                    val fileContent = com.google.api.client.http.ByteArrayContent("application/json", content)
                    driveService.files().create(metadata, fileContent)
                        .setFields("id")
                        .execute()
                    onSuccess()
                } catch (e: UserRecoverableAuthIOException) {
                    Log.e("Drive", "User recoverable auth error: ${e.message}", e)
                    onAuthNeeded(e)
                } catch (e: Exception) {
                    Log.e("Drive", "Backup failed: ${e.message}", e)
                    onFailure(e)
                }
            }.start()
        }

        fun restoreFromDrive(
            context: Context,
            onSuccess: (InputStreamReader) -> Unit,
            onFailure: (Exception) -> Unit,
            onAuthNeeded: (UserRecoverableAuthIOException) -> Unit
        ) {
            val driveService = getDriveService(context) ?: return onFailure(Exception("No signed-in account"))
            Thread {
                try {
                    val files = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name='cashbook_backup.json'")
                        .setFields("files(id)")
                        .execute()
                    Log.i("Drive", "Files found: ${files.files.size}")
                    if (files.files.isEmpty()) {
                        throw Exception("No backup file found")
                    }
                    val fileId = files.files[0].id
                    val inputStream = driveService.files().get(fileId).executeMediaAsInputStream()
                    onSuccess(InputStreamReader(inputStream))
                } catch (e: UserRecoverableAuthIOException) {
                    Log.e("Drive", "User recoverable auth error: ${e.message}", e)
                    onAuthNeeded(e)
                } catch (e: Exception) {
                    Log.e("Drive", "Restore failed: ${e.message}", e)
                    onFailure(e)
                }
            }.start()
        }
    }
}