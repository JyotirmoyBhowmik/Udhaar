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
                runCatching {
                    val id = if (idIdx >= 0) runCatching { c.getLong(idIdx) }.getOrDefault(0L) else 0L
                    val rawLookupKey = if (lookupIdx >= 0) runCatching { c.getString(lookupIdx) }.getOrNull().orEmpty() else "$id"
                    val name = if (nameIdx >= 0) runCatching { c.getString(nameIdx) }.getOrNull().orEmpty().trim() else ""
                    val rawNumber = if (numIdx >= 0) runCatching { c.getString(numIdx) }.getOrNull().orEmpty().trim() else ""
                    val thumbUri = if (thumbIdx >= 0) runCatching { c.getString(thumbIdx) }.getOrNull().orEmpty() else ""
                    val photoUri = if (photoIdx >= 0) runCatching { c.getString(photoIdx) }.getOrNull().orEmpty() else thumbUri

                    if (name.isBlank() && rawNumber.isBlank()) return@runCatching

                    val normalized = InterestCalculator.normalizeToE164(rawNumber, currency)
                    val dedupKey = if (normalized.isNotBlank()) normalized else "${name.lowercase()}_${rawNumber}"

                    if (seenKeys.add(dedupKey)) {
                        val matchingParty = existingParties.find { party ->
                            InterestCalculator.arePhonesEquivalent(party.phoneNumber, rawNumber, currency) ||
                                (party.alternatePhoneNumber.isNotBlank() && InterestCalculator.arePhonesEquivalent(party.alternatePhoneNumber, rawNumber, currency)) ||
                                party.name.trim().equals(name, ignoreCase = true)
                        }

                        val isInLedger = matchingParty != null
                        val hasUdhaarApp = matchingParty?.linkedUserId?.isNotBlank() == true
                        val baseLookup = rawLookupKey.ifBlank { id.toString() }
                        val uniqueLookupKey = "${baseLookup}_${dedupKey}_${contactList.size}"

                        contactList.add(
                            DeviceContact(
                                contactId = id,
                                lookupKey = uniqueLookupKey,
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
        }

        contactList.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Parses contacts from a standard vCard (.vcf) text payload so users importing a .vcf
     * contacts file from Settings can import their contacts directly into People Directory.
     */
    fun parseVCardContacts(
        vcfContent: String,
        currency: CurrencyType = CurrencyType.INR
    ): List<DeviceContact> {
        val results = mutableListOf<DeviceContact>()
        val seen = mutableSetOf<String>()
        var currentName = ""
        val currentPhones = mutableListOf<String>()

        fun flushCard() {
            val cleanName = currentName.trim()
            if (cleanName.isNotBlank() || currentPhones.isNotEmpty()) {
                val phonesToUse = if (currentPhones.isEmpty()) listOf("") else currentPhones
                for (rawPhone in phonesToUse) {
                    val normalized = InterestCalculator.normalizeToE164(rawPhone, currency)
                    val dedup = if (normalized.isNotBlank()) normalized else cleanName.lowercase()
                    if (dedup.isNotBlank() && seen.add(dedup)) {
                        results.add(
                            DeviceContact(
                                contactId = results.size.toLong() + 1L,
                                lookupKey = "vcf_${dedup}_${results.size}",
                                displayName = cleanName.ifBlank { rawPhone },
                                rawPhoneNumber = rawPhone,
                                normalizedPhoneNumber = normalized
                            )
                        )
                    }
                }
            }
            currentName = ""
            currentPhones.clear()
        }

        vcfContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    currentName = ""
                    currentPhones.clear()
                }
                line.equals("END:VCARD", ignoreCase = true) -> {
                    flushCard()
                }
                line.startsWith("FN:", ignoreCase = true) || line.startsWith("FN;", ignoreCase = true) -> {
                    val value = line.substringAfter(":", "").trim()
                    if (value.isNotBlank()) currentName = value
                }
                currentName.isBlank() && (line.startsWith("N:", ignoreCase = true) || line.startsWith("N;", ignoreCase = true)) -> {
                    val parts = line.substringAfter(":", "").split(";").map { it.trim() }.filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        currentName = if (parts.size >= 2) "${parts[1]} ${parts[0]}".trim() else parts[0]
                    }
                }
                line.startsWith("TEL:", ignoreCase = true) || line.startsWith("TEL;", ignoreCase = true) -> {
                    val phoneVal = line.substringAfter(":", "").trim()
                    if (phoneVal.isNotBlank()) {
                        currentPhones.add(phoneVal)
                    }
                }
            }
        }
        return results
    }
}
