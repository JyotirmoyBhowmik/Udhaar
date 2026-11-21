package com.jyotirmoyb.udhaar.ui.icons

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder

internal fun udhaarVectorIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    materialIcon(name = name) {
        materialPath(pathBuilder = block)
    }

/**
 * App-owned Material vector icons used across Udhaar UI without pulling in the
 * multi-megabyte `material-icons-extended` artifact or squatting in `androidx.*` packages.
 */
object UdhaarIcons {
    val AccountBalanceWallet: ImageVector by lazy { buildAccountBalanceWalletIcon() }
    val AttachFile: ImageVector by lazy { buildAttachFileIcon() }
    val Block: ImageVector by lazy { buildBlockIcon() }
    val CalendarMonth: ImageVector by lazy { buildCalendarMonthIcon() }
    val CalendarToday: ImageVector by lazy { CalendarMonth }
    val CameraAlt: ImageVector by lazy { buildCameraAltIcon() }
    val ChatBubbleOutline: ImageVector by lazy { buildChatBubbleOutlineIcon() }
    val CloudDone: ImageVector by lazy { buildCloudDoneIcon() }
    val CloudUpload: ImageVector by lazy { buildCloudUploadIcon() }
    val Contacts: ImageVector by lazy { buildContactsIcon() }
    val ContentCopy: ImageVector by lazy { buildContentCopyIcon() }
    val DeleteOutline: ImageVector by lazy { buildDeleteOutlineIcon() }
    val Description: ImageVector by lazy { buildDescriptionIcon() }
    val DoneAll: ImageVector by lazy { buildDoneAllIcon() }
    val Download: ImageVector by lazy { buildDownloadIcon() }
    val ErrorOutline: ImageVector by lazy { buildErrorOutlineIcon() }
    val ExpandLess: ImageVector by lazy { buildExpandLessIcon() }
    val ExpandMore: ImageVector by lazy { buildExpandMoreIcon() }
    val Fingerprint: ImageVector by lazy { buildFingerprintIcon() }
    val Flag: ImageVector by lazy { buildFlagIcon() }
    val History: ImageVector by lazy { buildHistoryIcon() }
    val Image: ImageVector by lazy { buildImageIcon() }
    val Link: ImageVector by lazy { buildLinkIcon() }
    val LockOpen: ImageVector by lazy { buildLockOpenIcon() }
    val Merge: ImageVector by lazy { buildMergeIcon() }
    val Message: ImageVector by lazy { buildMessageIcon() }
    val NotificationsActive: ImageVector by lazy { buildNotificationsActiveIcon() }
    val NotificationsOff: ImageVector by lazy { buildNotificationsOffIcon() }
    val Palette: ImageVector by lazy { buildPaletteIcon() }
    val Payments: ImageVector by lazy { buildPaymentsIcon() }
    val PersonAdd: ImageVector by lazy { buildPersonAddIcon() }
    val Pin: ImageVector by lazy { buildPinIcon() }
    val QrCode2: ImageVector by lazy { buildQrCode2Icon() }
    val ReceiptLong: ImageVector by lazy { buildReceiptLongIcon() }
    val ReportProblem: ImageVector by lazy { buildReportProblemIcon() }
    val Schedule: ImageVector by lazy { buildScheduleIcon() }
    val Sort: ImageVector by lazy { buildSortIcon() }
    val StarBorder: ImageVector by lazy { buildStarBorderIcon() }
    val SupportAgent: ImageVector by lazy { buildSupportAgentIcon() }
    val Sync: ImageVector by lazy { buildSyncIcon() }
    val Upload: ImageVector by lazy { buildUploadIcon() }
    val Verified: ImageVector by lazy { buildVerifiedIcon() }
    val VerifiedUser: ImageVector by lazy { buildVerifiedUserIcon() }
    val Visibility: ImageVector by lazy { buildVisibilityIcon() }
    val VisibilityOff: ImageVector by lazy { buildVisibilityOffIcon() }
    val ZoomIn: ImageVector by lazy { buildZoomInIcon() }
}
