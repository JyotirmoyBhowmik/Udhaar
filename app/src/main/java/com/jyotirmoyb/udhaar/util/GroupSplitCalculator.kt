package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.CurrencyType
import kotlin.math.abs
import kotlin.math.roundToLong

enum class GroupSplitMode {
    EQUAL,
    CUSTOM,
    PERCENTAGE
}

enum class GroupPayerMode {
    I_PAID,
    OTHER_PAID
}

data class GroupMemberDraft(
    val id: Long,
    val name: String,
    val phoneNumber: String = "",
    val partyId: Long? = null,
    val customAmountInput: String = "",
    val percentageInput: String = ""
)

data class GroupMemberShare(
    val member: GroupMemberDraft,
    val amountMinor: Long,
    val amountDouble: Double
)

data class GroupSplitCalculationResult(
    val totalBillMinor: Long,
    val totalBillDouble: Double,
    val myShareMinor: Long,
    val myShareDouble: Double,
    val memberShares: List<GroupMemberShare>,
    val allocatedTotalMinor: Long,
    val allocatedTotalDouble: Double,
    val isTotalMatching: Boolean
)

data class ActiveGroupSplitSummary(
    val groupName: String,
    val currency: CurrencyType,
    val records: List<CalculatedUdhaarSummary>,
    val totalMembersCount: Int,
    val settledMembersCount: Int,
    val totalAmount: Double,
    val remainingDue: Double
)

object GroupSplitCalculator {

    private const val GROUP_TAG_PREFIX = "[Group: "
    private const val GROUP_TAG_SUFFIX = "]"

    fun formatGroupNote(groupName: String, extraNote: String = ""): String {
        val cleanGroup = groupName.trim().replace("]", "")
        val tag = "$GROUP_TAG_PREFIX$cleanGroup$GROUP_TAG_SUFFIX"
        return if (extraNote.isNotBlank()) {
            "$tag ${extraNote.trim()}"
        } else {
            tag
        }
    }

    fun extractGroupName(notes: String): String? {
        val startIdx = notes.indexOf(GROUP_TAG_PREFIX)
        if (startIdx < 0) return null
        val contentStart = startIdx + GROUP_TAG_PREFIX.length
        val endIdx = notes.indexOf(GROUP_TAG_SUFFIX, contentStart)
        if (endIdx <= contentStart) return null
        return notes.substring(contentStart, endIdx).trim().takeIf { it.isNotEmpty() }
    }

    fun stripGroupTag(notes: String): String {
        val startIdx = notes.indexOf(GROUP_TAG_PREFIX)
        if (startIdx < 0) return notes
        val endIdx = notes.indexOf(GROUP_TAG_SUFFIX, startIdx + GROUP_TAG_PREFIX.length)
        if (endIdx < 0) return notes
        return (notes.substring(0, startIdx) + notes.substring(endIdx + 1)).trim()
    }

    fun calculateSplit(
        totalBillAmount: Double,
        splitMode: GroupSplitMode,
        includeMyself: Boolean,
        members: List<GroupMemberDraft>
    ): GroupSplitCalculationResult {
        val totalMinor = (totalBillAmount.coerceAtLeast(0.0) * 100.0).roundToLong()
        if (members.isEmpty() || totalMinor <= 0L) {
            return GroupSplitCalculationResult(
                totalBillMinor = totalMinor,
                totalBillDouble = totalMinor / 100.0,
                myShareMinor = 0L,
                myShareDouble = 0.0,
                memberShares = members.map { GroupMemberShare(it, 0L, 0.0) },
                allocatedTotalMinor = 0L,
                allocatedTotalDouble = 0.0,
                isTotalMatching = totalMinor == 0L
            )
        }

        return when (splitMode) {
            GroupSplitMode.EQUAL -> {
                val participantCount = members.size + (if (includeMyself) 1 else 0)
                val baseShareMinor = totalMinor / participantCount
                val remainderMinor = totalMinor - (baseShareMinor * participantCount)
                val myShareMinor = if (includeMyself) baseShareMinor else 0L

                val shares = members.mapIndexed { index, member ->
                    val extra = if (index == members.lastIndex) remainderMinor else 0L
                    val memberMinor = baseShareMinor + extra
                    GroupMemberShare(
                        member = member,
                        amountMinor = memberMinor,
                        amountDouble = memberMinor / 100.0
                    )
                }
                val allocatedMinor = myShareMinor + shares.sumOf { it.amountMinor }
                GroupSplitCalculationResult(
                    totalBillMinor = totalMinor,
                    totalBillDouble = totalMinor / 100.0,
                    myShareMinor = myShareMinor,
                    myShareDouble = myShareMinor / 100.0,
                    memberShares = shares,
                    allocatedTotalMinor = allocatedMinor,
                    allocatedTotalDouble = allocatedMinor / 100.0,
                    isTotalMatching = allocatedMinor == totalMinor
                )
            }

            GroupSplitMode.CUSTOM -> {
                val shares = members.map { member ->
                    val amt = member.customAmountInput.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val minor = (amt * 100.0).roundToLong()
                    GroupMemberShare(
                        member = member,
                        amountMinor = minor,
                        amountDouble = minor / 100.0
                    )
                }
                val membersSumMinor = shares.sumOf { it.amountMinor }
                val myShareMinor = if (includeMyself) {
                    (totalMinor - membersSumMinor).coerceAtLeast(0L)
                } else {
                    0L
                }
                val allocatedMinor = membersSumMinor + myShareMinor
                GroupSplitCalculationResult(
                    totalBillMinor = totalMinor,
                    totalBillDouble = totalMinor / 100.0,
                    myShareMinor = myShareMinor,
                    myShareDouble = myShareMinor / 100.0,
                    memberShares = shares,
                    allocatedTotalMinor = allocatedMinor,
                    allocatedTotalDouble = allocatedMinor / 100.0,
                    isTotalMatching = abs(allocatedMinor - totalMinor) <= 1L
                )
            }

            GroupSplitMode.PERCENTAGE -> {
                val shares = members.map { member ->
                    val pct = member.percentageInput.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0
                    val minor = ((totalMinor.toDouble() * pct) / 100.0).roundToLong()
                    GroupMemberShare(
                        member = member,
                        amountMinor = minor,
                        amountDouble = minor / 100.0
                    )
                }
                val membersSumMinor = shares.sumOf { it.amountMinor }
                val myShareMinor = if (includeMyself) {
                    (totalMinor - membersSumMinor).coerceAtLeast(0L)
                } else {
                    0L
                }
                val allocatedMinor = membersSumMinor + myShareMinor
                GroupSplitCalculationResult(
                    totalBillMinor = totalMinor,
                    totalBillDouble = totalMinor / 100.0,
                    myShareMinor = myShareMinor,
                    myShareDouble = myShareMinor / 100.0,
                    memberShares = shares,
                    allocatedTotalMinor = allocatedMinor,
                    allocatedTotalDouble = allocatedMinor / 100.0,
                    isTotalMatching = abs(allocatedMinor - totalMinor) <= 5L
                )
            }
        }
    }

    fun computeActiveGroups(items: List<CalculatedUdhaarSummary>): List<ActiveGroupSplitSummary> {
        val grouped = items.mapNotNull { summary ->
            val groupName = extractGroupName(summary.transaction.notes) ?: return@mapNotNull null
            groupName to summary
        }.groupBy({ it.first }, { it.second })

        return grouped.map { (groupName, groupItems) ->
            val currency = groupItems.first().transaction.currency
            val settledCount = groupItems.count { it.transaction.isSettled || it.netBalanceDue <= 0.005 }
            val totalAmt = groupItems.sumOf { it.grossTotal }
            val remaining = groupItems.filter { !it.transaction.isSettled }.sumOf { it.netBalanceDue }
            ActiveGroupSplitSummary(
                groupName = groupName,
                currency = currency,
                records = groupItems,
                totalMembersCount = groupItems.size,
                settledMembersCount = settledCount,
                totalAmount = totalAmt,
                remainingDue = remaining
            )
        }.sortedByDescending { it.remainingDue }
    }

    fun buildWhatsAppGroupSplitMessage(
        groupName: String,
        currency: CurrencyType,
        calcResult: GroupSplitCalculationResult,
        payeeName: String,
        upiId: String
    ): String {
        val title = groupName.trim().ifBlank { "Group Split" }
        val totalFmt = InterestCalculator.formatCurrency(calcResult.totalBillDouble, currency)
        return buildString {
            append("*Udhaar — Group Split: $title*\n")
            append("Total Bill: *$totalFmt*\n")
            if (calcResult.myShareMinor > 0L) {
                val myFmt = InterestCalculator.formatCurrency(calcResult.myShareDouble, currency)
                append("${payeeName.ifBlank { "My" }} Share: $myFmt\n")
            }
            append("\n*Member Shares:*\n")
            calcResult.memberShares.forEachIndexed { idx, share ->
                val shareFmt = InterestCalculator.formatCurrency(share.amountDouble, currency)
                append("${idx + 1}. ${share.member.name}: *$shareFmt*\n")
            }
            if (currency == CurrencyType.INR && upiId.isNotBlank()) {
                append("\nPay via UPI ID: *$upiId*")
            }
        }
    }
}
