package com.example.cashbook

import android.os.Parcel
import android.os.Parcelable
import java.util.Date

data class Transaction(
    val id: Long = 0,
    val description: String,
    val amount: Double,
    val isCashIn: Boolean,
    val date: Date,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readByte() != 0.toByte(),
        Date(parcel.readLong())
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(description)
        parcel.writeDouble(amount)
        parcel.writeByte(if (isCashIn) 1 else 0)
        parcel.writeLong(date.time)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Transaction> {
        override fun createFromParcel(parcel: Parcel): Transaction = Transaction(parcel)
        override fun newArray(size: Int): Array<Transaction?> = arrayOfNulls(size)
    }
}