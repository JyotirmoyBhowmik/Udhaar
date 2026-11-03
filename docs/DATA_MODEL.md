# Udhaar v13.0 — Complete Data Model Reference (Room Schema v7 & Cloud Firestore)

## 1. Local SQLCipher Room Database (`udhaar_ledger.db`, Version 7)

All on-device tables are encrypted at rest with a 256-bit SQLCipher passphrase wrapped by Android Keystore (`AES256_GCM`).

### Table 1: `parties` (`PartyEntity`)
Stores contacts/counterparties in the People Directory.
| Column | Type | Constraints / Notes |
|---|---|---|
| `partyId` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `name` | `TEXT` | Counterparty display name |
| `phoneNumber` | `TEXT` | Sanitized E.164 phone (`+91...` / `+977...`), indexed |
| `upiId` | `TEXT` | Counterparty VPA (`user@upi`) |
| `email` | `TEXT` | Counterparty email address |
| `tagOrNotes` | `TEXT` | Relationship tag (`Friend`, `Client`, `Supplier`, `Family`, `Staff`) |
| `preferredCurrency` | `TEXT` (`CurrencyType`) | `INR`, `NPR`, `USD`, `EUR`, `GBP`, `BDT` |
| `createdAtMillis` | `INTEGER` (`Long`) | Creation epoch ms |
| `linkedCloudUid` | `TEXT?` | Optional Firebase Auth UID for verified counterparties |
| `linkedIdentityKey` | `TEXT?` | Normalized phone/email/UID identity key, indexed |
| `customReminderTemplate` | `TEXT` | Optional per-party WhatsApp reminder template with placeholders |

### Table 2: `udhaar_transactions` (`UdhaarTransaction`)
Stores all Given (`GIVEN` / Receivable) and Taken (`TAKEN` / Payable) ledger entries.
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `partyId` | `INTEGER?` (`Long?`) | Optional link to `parties.partyId`, indexed |
| `transactionType` | `TEXT` (`TransactionType`) | `GIVEN` or `TAKEN`, indexed |
| `partyName` | `TEXT` | Counterparty name snapshot |
| `phoneNumber` | `TEXT` | Counterparty phone snapshot |
| `currency` | `TEXT` (`CurrencyType`) | `INR`, `NPR`, `USD`, `EUR`, `GBP`, `BDT`, indexed |
| `principalAmount` | `REAL` (`Double`) | Original principal amount |
| `amountPaid` | `REAL` (`Double`) | Cumulative partial payments recorded |
| `paymentMode` | `TEXT` (`PaymentMode`) | `CASH`, `ONLINE`, `UPI`, `PARTY_ACCOUNT` |
| `isInterestEnabled` | `INTEGER` (`Boolean`) | Whether interest accrues |
| `interestType` | `TEXT` (`InterestType`) | `SIMPLE` or `COMPOUND` |
| `interestRate` | `REAL` (`Double`) | Numeric rate (`%`) |
| `interestRatePeriod` | `TEXT` (`InterestRatePeriod`) | `PER_MONTH` or `PER_ANNUM` |
| `compoundFrequency` | `TEXT` (`CompoundFrequency`) | `MONTHLY`, `QUARTERLY`, `YEARLY` |
| `startDateMillis` | `INTEGER` (`Long`) | Loan start timestamp |
| `expiryDateMillis` | `INTEGER?` (`Long?`) | Optional due date timestamp, indexed |
| `reminderDateMillis` | `INTEGER?` (`Long?`) | Scheduled local notification timestamp |
| `notes` | `TEXT` | Free-form memo |
| `isSettled` | `INTEGER` (`Boolean`) | Settlement flag, indexed |
| `settledAtMillis` | `INTEGER?` (`Long?`) | Settlement timestamp |
| `createdAtMillis` | `INTEGER` (`Long`) | Creation timestamp |
| `verificationHash` | `TEXT` | Canonical SHA-256 verification digest |
| `verificationToken` | `TEXT` | Signed URL token for `https://udhaar.jyotirmoyb.com/u/<token>` |
| `verificationStatus` | `TEXT` (`VerificationStatus`) | `UNVERIFIED`, `PENDING_REMOTE`, `CONFIRMED`, `DISPUTED`, indexed |
| `verifiedAtMillis` | `INTEGER?` | Timestamp of counterparty confirmation |
| `verifiedByIdentity` | `TEXT` | Counterparty identifier that confirmed/disputed |
| `disputeNote` | `TEXT` | Counterparty dispute reason if `DISPUTED` |
| `cloudRecordId` | `TEXT?` | Shared Firestore document ID (`shared_records/{recordId}`), indexed |
| `isDeleted` | `INTEGER` (`Boolean`) | Soft-delete tombstone flag (`0` active, `1` trashed), indexed |
| `deletedAtMillis` | `INTEGER?` (`Long?`) | Soft-delete timestamp |
| `recurrenceFrequency` | `TEXT` (`RecurrenceFrequency`) | `NONE`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `lastRecurrenceGeneratedMillis` | `INTEGER?` | Last auto-generated recurring entry timestamp |

### Table 3: `partial_payments` (`PartialPayment`)
Stores installment repayments against a transaction.
| Column | Type | Constraints / Notes |
|---|---|---|
| `id` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `transactionId` | `INTEGER` (`Long`) | `FOREIGN KEY -> udhaar_transactions(id) ON DELETE CASCADE`, indexed |
| `amount` | `REAL` (`Double`) | Installment payment amount |
| `paidAtMillis` | `INTEGER` (`Long`) | Payment timestamp |
| `note` | `TEXT` | Installment note |

### Table 4: `audit_trail` (`AuditTrailEntry`)
Tamper-evident hash-chained log of every ledger mutation.
| Column | Type | Constraints / Notes |
|---|---|---|
| `auditId` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `transactionId` | `INTEGER` (`Long`) | Associated transaction ID, indexed |
| `partyId` | `INTEGER?` (`Long?`) | Associated party ID, indexed |
| `actionType` | `TEXT` (`AuditActionType`) | `CREATED`, `EDITED`, `PARTIAL_PAYMENT_ADDED`, `PARTIAL_PAYMENT_DELETED`, `SETTLED`, `REOPENED`, `VERIFICATION_SHARED`, `VERIFICATION_CONFIRMED`, `VERIFICATION_DISPUTED`, `IMPORTED_FROM_LINK` |
| `actorIdentity` | `TEXT` | Local user or counterparty label |
| `summary` | `TEXT` | Human-readable change description |
| `previousValuesJson` | `TEXT` | Snapshot before mutation |
| `newValuesJson` | `TEXT` | Snapshot after mutation |
| `entryHash` | `TEXT` | `SHA-256(previousEntryHash + canonical payload)` |
| `previousEntryHash` | `TEXT` | Previous audit entry hash (`GENESIS` for first entry) |
| `createdAtMillis` | `INTEGER` (`Long`) | Mutation timestamp, indexed |

### Table 5: `chat_threads` (`ChatThreadMetadata`)
Per-party chat thread metadata cached locally.
| Column | Type | Constraints / Notes |
|---|---|---|
| `threadId` | `TEXT` | `PRIMARY KEY` |
| `partyId` | `INTEGER?` (`Long?`) | Linked `parties.partyId`, indexed |
| `partyName` | `TEXT` | Counterparty display name |
| `counterpartyIdentityKey` | `TEXT` | Counterparty identity key, indexed |
| `lastMessagePreview` | `TEXT` | Last message snippet |
| `lastMessageAtMillis` | `INTEGER` (`Long`) | Last message timestamp, indexed |
| `unreadCount` | `INTEGER` (`Int`) | Unread badge count |
| `isCloudSynced` | `INTEGER` (`Boolean`) | Whether synced to Firestore |

### Table 6: `chat_messages` (`ChatMessageEntity`)
Individual messages and ledger event bubbles inside a thread.
| Column | Type | Constraints / Notes |
|---|---|---|
| `messageId` | `TEXT` | `PRIMARY KEY` |
| `threadId` | `TEXT` | `FOREIGN KEY -> chat_threads(threadId) ON DELETE CASCADE`, indexed |
| `partyId` | `INTEGER?` (`Long?`) | Linked `parties.partyId`, indexed |
| `linkedTransactionId` | `INTEGER?` (`Long?`) | Optional linked transaction ID, indexed |
| `senderIdentity` | `TEXT` | Sender UID or identity key |
| `senderName` | `TEXT` | Sender display name |
| `isOutgoing` | `INTEGER` (`Boolean`) | `true` if sent by local user |
| `messageType` | `TEXT` (`ChatMessageType`) | `TEXT`, `RECORD_CARD`, `VERIFICATION_EVENT`, `PAYMENT_CONFIRMATION` |
| `body` | `TEXT` | Message text |
| `recordHash` | `TEXT` | Linked verification hash |
| `createdAtMillis` | `INTEGER` (`Long`) | Message timestamp, indexed |
| `deliveryStatus` | `TEXT` (`ChatDeliveryStatus`) | `LOCAL_ONLY`, `SYNCED`, `DELIVERED`, `READ` |

### Table 7: `attachments` (`Attachment`)
Local receipt photos, PDFs, and payment proofs linked to transactions or partial payments.
| Column | Type | Constraints / Notes |
|---|---|---|
| `attachmentId` | `INTEGER` (`Long`) | `PRIMARY KEY AUTOINCREMENT` |
| `transactionId` | `INTEGER` (`Long`) | `FOREIGN KEY -> udhaar_transactions(id) ON DELETE CASCADE`, indexed |
| `paymentId` | `INTEGER?` (`Long?`) | Optional `partial_payments.id`, indexed |
| `localPath` | `TEXT` | Relative path inside private `files/attachments/` |
| `mimeType` | `TEXT` | `image/jpeg`, `application/pdf`, etc. |
| `fileName` | `TEXT` | Sanitized display filename |
| `sizeBytes` | `INTEGER` (`Long`) | Compressed file size in bytes |
| `sha256Hash` | `TEXT` | SHA-256 digest of file bytes |
| `driveFileId` | `TEXT?` | Optional Google Drive `appDataFolder` file ID |
| `createdAtMillis` | `INTEGER` (`Long`) | Creation timestamp |

---

## 2. Cloud Firestore Collections (Named DB: `ai-studio-android-udhaar-42622035-52d2-4bfb-8070-8962975a82cf`)

1. **`/user_profiles/{uid}`**
   - Stores authenticated user's public identity mapping (`uid`, `displayName`, `email`, `phoneNumber`, `upiId`, `identityKeys`, `fcmToken`, `updatedAtMillis`).
2. **`/shared_records/{recordId}`**
   - Stores mutually verifiable transaction snapshots (`recordId`, `creatorUid`, `participantUids`, `participantIdentities`, `verificationStatus`, `verificationHash`, `verificationToken`, `disputeNote`, `updatedAtMillis`).
3. **`/chat_threads/{threadId}` & `/chat_threads/{threadId}/messages/{messageId}`**
   - Stores real-time counterparty chat metadata and messages restricted by `participantUids` in `firestore.rules`.
