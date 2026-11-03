package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject
import javax.inject.Singleton

open class PartyRepository(
    private val dao: UdhaarDao
) {
    val allParties: Flow<List<PartyEntity>> = dao.getAllParties().catch { e ->
        SafeLogger.e("PartyRepository", "Failed reading parties flow", e)
        emit(emptyList())
    }

    suspend fun saveParty(party: PartyEntity): Long {
        val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(
            party.phoneNumber,
            party.preferredCurrency
        )
        val formattedAltPhone = if (party.alternatePhoneNumber.isNotBlank()) {
            InterestCalculator.formatPhoneWithCountryCode(
                party.alternatePhoneNumber,
                party.preferredCurrency
            )
        } else {
            ""
        }
        return dao.insertParty(
            party.copy(
                name = party.name.trim(),
                phoneNumber = formattedPhone,
                alternatePhoneNumber = formattedAltPhone,
                upiId = party.upiId.trim(),
                email = party.email.trim(),
                tagOrNotes = party.tagOrNotes.trim(),
                notes = party.notes.trim(),
                photoUri = party.photoUri.trim(),
                linkedUserId = party.linkedUserId.trim(),
                isFavorite = party.isFavorite,
                lastInteractionMillis = if (party.lastInteractionMillis > 0L) party.lastInteractionMillis else System.currentTimeMillis()
            )
        )
    }

    suspend fun saveParties(parties: List<PartyEntity>): List<Long> {
        val mapped = parties.map { p ->
            val formattedPhone = InterestCalculator.formatPhoneWithCountryCode(p.phoneNumber, p.preferredCurrency)
            val formattedAltPhone = if (p.alternatePhoneNumber.isNotBlank()) {
                InterestCalculator.formatPhoneWithCountryCode(p.alternatePhoneNumber, p.preferredCurrency)
            } else ""
            p.copy(
                name = p.name.trim(),
                phoneNumber = formattedPhone,
                alternatePhoneNumber = formattedAltPhone,
                upiId = p.upiId.trim(),
                email = p.email.trim(),
                tagOrNotes = p.tagOrNotes.trim(),
                notes = p.notes.trim(),
                photoUri = p.photoUri.trim(),
                linkedUserId = p.linkedUserId.trim()
            )
        }
        return dao.insertParties(mapped)
    }

    suspend fun togglePartyFavorite(partyId: Long, isFavorite: Boolean) {
        dao.setPartyFavorite(partyId, isFavorite)
    }

    suspend fun updatePartyLastInteraction(partyId: Long, millis: Long = System.currentTimeMillis()) {
        dao.updatePartyLastInteraction(partyId, millis)
    }

    suspend fun updatePartyLanguage(partyId: Long, language: String) {
        dao.updatePartyLanguage(partyId, language)
    }

    suspend fun updatePartyQuietHoursOverride(partyId: Long, override: Boolean) {
        dao.updatePartyQuietHoursOverride(partyId, override)
    }

    suspend fun mergeParties(primaryParty: PartyEntity, secondaryParty: PartyEntity): PartyEntity {
        val merged = primaryParty.copy(
            phoneNumber = primaryParty.phoneNumber.ifBlank { secondaryParty.phoneNumber },
            alternatePhoneNumber = primaryParty.alternatePhoneNumber.ifBlank {
                if (secondaryParty.phoneNumber != primaryParty.phoneNumber) secondaryParty.phoneNumber
                else secondaryParty.alternatePhoneNumber
            },
            upiId = primaryParty.upiId.ifBlank { secondaryParty.upiId },
            email = primaryParty.email.ifBlank { secondaryParty.email },
            tagOrNotes = primaryParty.tagOrNotes.ifBlank { secondaryParty.tagOrNotes },
            notes = if (primaryParty.notes.isNotBlank() && secondaryParty.notes.isNotBlank() && primaryParty.notes != secondaryParty.notes) {
                "${primaryParty.notes}\n[Merged]: ${secondaryParty.notes}"
            } else {
                primaryParty.notes.ifBlank { secondaryParty.notes }
            },
            photoUri = primaryParty.photoUri.ifBlank { secondaryParty.photoUri },
            linkedUserId = primaryParty.linkedUserId.ifBlank { secondaryParty.linkedUserId },
            isFavorite = primaryParty.isFavorite || secondaryParty.isFavorite,
            lastInteractionMillis = maxOf(primaryParty.lastInteractionMillis, secondaryParty.lastInteractionMillis)
        )
        dao.insertParty(merged)

        if (secondaryParty.partyId > 0L && primaryParty.partyId > 0L) {
            dao.relinkTransactionsForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.relinkChatMessagesForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.relinkChatThreadsForPartyId(secondaryParty.partyId, primaryParty.partyId, primaryParty.name)
            dao.deletePartyById(secondaryParty.partyId)
        }
        return merged
    }

    suspend fun deleteParty(party: PartyEntity) {
        dao.deleteParty(party)
    }

    suspend fun deletePartyById(partyId: Long) {
        dao.deletePartyById(partyId)
    }

    suspend fun getPartyById(partyId: Long): PartyEntity? = dao.getPartyById(partyId)

    suspend fun getPartyByName(name: String): PartyEntity? = dao.getPartyByName(name)
}
