package com.jyotirmoyb.udhaar.data

data class FullLedgerSnapshot(
    val parties: List<PartyEntity>,
    val transactions: List<UdhaarTransaction>,
    val partialPayments: List<PartialPayment>,
    val chatThreads: List<ChatThreadMetadata>,
    val chatMessages: List<ChatMessageEntity>,
    val attachments: List<Attachment> = emptyList(),
    val installmentPlans: List<InstallmentPlan> = emptyList(),
    val installments: List<Installment> = emptyList()
)
