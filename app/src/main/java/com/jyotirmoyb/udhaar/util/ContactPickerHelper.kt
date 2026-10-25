package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

object ContactPickerHelper {

    /**
     * Creates an Intent to pick a contact with phone number.
     * Uses Phone.CONTENT_TYPE which is widely supported by Google Contacts and OEM pickers.
     */
    fun createPickContactIntent(): Intent {
        return Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
    }

    /**
     * Fallback Intent to pick a general contact if Phone.CONTENT_TYPE is not supported.
     */
    fun createFallbackPickContactIntent(): Intent {
        return Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
    }

    /**
     * Robustly extracts the Contact Display Name and Phone Number from any Uri
     * returned by Android contact pickers across various device OEMs and Google Contacts.
     */
    fun extractContactInfo(context: Context, contactUri: Uri): Pair<String, String>? {
        var name = ""
        var phone = ""

        // Strategy 1: Direct query against the picked URI with Phone projection
        runCatching {
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            )
            context.contentResolver.query(contactUri, phoneProjection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx).orEmpty()
                    }
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numIdx >= 0) {
                        phone = cursor.getString(numIdx).orEmpty()
                    }
                }
            }
        }

        // Strategy 2: If phone is still empty, the URI might be a Contacts or Lookup URI
        if (phone.isBlank()) {
            runCatching {
                var contactId: String? = null
                val contactProjection = arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                )
                context.contentResolver.query(contactUri, contactProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        if (idIdx >= 0) {
                            contactId = cursor.getString(idIdx)
                        }
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (nameIdx >= 0 && name.isBlank()) {
                            name = cursor.getString(nameIdx).orEmpty()
                        }
                    }
                }

                if (!contactId.isNullOrBlank()) {
                    val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val phoneProjection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                    context.contentResolver.query(
                        phoneUri,
                        phoneProjection,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numIdx >= 0) {
                                phone = phoneCursor.getString(numIdx).orEmpty()
                            }
                            val nameIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            if (nameIdx >= 0 && name.isBlank()) {
                                name = phoneCursor.getString(nameIdx).orEmpty()
                            }
                        }
                    }
                }
            }
        }

        // Strategy 3: Dynamic fallback across all available cursor columns
        if (name.isBlank() || phone.isBlank()) {
            runCatching {
                context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        for (i in 0 until cursor.columnCount) {
                            val col = cursor.getColumnName(i).lowercase()
                            val valStr = cursor.getString(i).orEmpty().trim()
                            if (name.isBlank() && (col.contains("display_name") || col == "name") && valStr.isNotBlank() && !valStr.contains("@")) {
                                name = valStr
                            }
                            if (phone.isBlank() && (col.contains("data1") || col.contains("number") || col.contains("phone")) && valStr.any { it.isDigit() }) {
                                phone = valStr
                            }
                        }
                    }
                }
            }
        }

        // Clean up formatting
        name = name.trim()
        phone = cleanPhoneNumber(phone)

        return if (name.isNotBlank() || phone.isNotBlank()) {
            Pair(name, phone)
        } else {
            null
        }
    }

    /**
     * Cleans spaces, dashes, and parentheses while preserving leading '+' country code
     */
    fun cleanPhoneNumber(rawPhone: String): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }
}
