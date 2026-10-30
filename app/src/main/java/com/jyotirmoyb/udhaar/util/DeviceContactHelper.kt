package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(
    val contactId: Long = 0L,
    val lookupKey: String = "",
    val displayName: String = "",
    val rawPhoneNumber: String = "",
    val normalizedPhoneNumber: String = "",
    val photoThumbnailUri: String? = null,
    val photoUri: String = "",
    val email: String = "",
    val isInLedger: Boolean = false,
    val hasUdhaarApp: Boolean = false
) {
    val name: String get() = displayName.ifBlank { rawPhoneNumber }
}

object DeviceContactHelper {

    /**
     * Reads all contacts from ContactsContract with phone numbers.
     * Normalizes phone numbers to standard E.164 based on preferred currency.
     * Computes "In Ledger" and "On Udhaar" badges against existing parties.
     */
    suspend fun fetchDeviceContacts(
        context: Context,
        currency: CurrencyType = CurrencyType.INR,
        existingParties: List<PartyEntity> = emptyList()
    ): List<DeviceContact> = withContext(Dispatchers.IO) {
        val contactList = mutableListOf<DeviceContact>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val cursor: Cursor? = runCatching {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )
        }.getOrNull()

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val thumbIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            val seenKeys = mutableSetOf<String>()

            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getLong(idIdx) else 0L
                val lookupKey = if (lookupIdx >= 0) c.getString(lookupIdx).orEmpty() else "$id"
                val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty().trim() else ""
                val rawNumber = if (numIdx >= 0) c.getString(numIdx).orEmpty().trim() else ""
                val thumbUri = if (thumbIdx >= 0) c.getString(thumbIdx).orEmpty() else ""
                val photoUri = if (photoIdx >= 0) c.getString(photoIdx).orEmpty() else thumbUri

                if (name.isBlank() && rawNumber.isBlank()) continue

                val normalized = InterestCalculator.normalizeToE164(rawNumber, currency)
                val dedupKey = if (normalized.isNotBlank()) normalized else name.lowercase()

                if (seenKeys.add(dedupKey)) {
                    val matchingParty = existingParties.find { party ->
                        InterestCalculator.arePhonesEquivalent(party.phoneNumber, rawNumber, currency) ||
                            (party.alternatePhoneNumber.isNotBlank() && InterestCalculator.arePhonesEquivalent(party.alternatePhoneNumber, rawNumber, currency)) ||
                            party.name.trim().equals(name, ignoreCase = true)
                    }

                    val isInLedger = matchingParty != null
                    val hasUdhaarApp = matchingParty?.linkedUserId?.isNotBlank() == true

                    contactList.add(
                        DeviceContact(
                            contactId = id,
                            lookupKey = if (lookupKey.isNotBlank()) lookupKey else dedupKey,
                            displayName = name.ifBlank { rawNumber },
                            rawPhoneNumber = rawNumber,
                            normalizedPhoneNumber = normalized,
                            photoThumbnailUri = thumbUri.ifBlank { null },
                            photoUri = photoUri,
                            isInLedger = isInLedger,
                            hasUdhaarApp = hasUdhaarApp
                        )
                    )
                }
            }
        }

        contactList.sortedBy { it.displayName.lowercase() }
    }
}
