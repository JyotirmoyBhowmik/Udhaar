/**
 * Udhaar Client-Side Calendar Reminder Service (Spark Mode)
 * (webapp/src/features/reminders/calendarReminderService.js)
 * 
 * In Spark Mode, server push notifications and background cron schedulers are disabled.
 * The Web app provides client-side calendar reminders:
 * 1. "Add to Google Calendar" instant template link
 * 2. Standard ".ics" iCalendar file download (Apple Calendar, Outlook, etc.)
 */

function formatIcsDateTime(date) {
  const d = new Date(date);
  const pad = (n) => String(n).padStart(2, "0");
  return (
    d.getUTCFullYear() +
    pad(d.getUTCMonth() + 1) +
    pad(d.getUTCDate()) +
    "T" +
    pad(d.getUTCHours()) +
    pad(d.getUTCMinutes()) +
    pad(d.getUTCSeconds()) +
    "Z"
  );
}

function buildGoogleCalendarUrl({ title, description, dueDateMs }) {
  if (!dueDateMs) return "";
  const startDate = new Date(dueDateMs);
  startDate.setHours(10, 0, 0, 0); // 10:00 AM default reminder time
  const endDate = new Date(startDate.getTime() + 60 * 60 * 1000); // 1 hour event

  const startIso = formatIcsDateTime(startDate);
  const endIso = formatIcsDateTime(endDate);

  const params = new URLSearchParams({
    action: "TEMPLATE",
    text: title || "Udhaar Repayment Reminder",
    details: description || "Repayment scheduled on Udhaar.",
    dates: `${startIso}/${endIso}`
  });

  return `https://calendar.google.com/calendar/render?${params.toString()}`;
}

function generateIcsContent({ title, description, dueDateMs, uid = "udhaar" }) {
  const startDate = dueDateMs ? new Date(dueDateMs) : new Date();
  startDate.setHours(10, 0, 0, 0);
  const endDate = new Date(startDate.getTime() + 60 * 60 * 1000);

  const startIso = formatIcsDateTime(startDate);
  const endIso = formatIcsDateTime(endDate);
  const nowIso = formatIcsDateTime(new Date());

  return [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//Udhaar//NONSGML v24.0//EN",
    "CALSCALE:GREGORIAN",
    "BEGIN:VEVENT",
    `UID:reminder-${uid}-${Date.now()}@udhaar.jyotirmoyb.com`,
    `DTSTAMP:${nowIso}`,
    `DTSTART:${startIso}`,
    `DTEND:${endIso}`,
    `SUMMARY:${title || "Udhaar Repayment Reminder"}`,
    `DESCRIPTION:${(description || "").replace(/\n/g, "\\n")}`,
    "STATUS:CONFIRMED",
    "BEGIN:VALARM",
    "TRIGGER:-PT15M",
    "ACTION:DISPLAY",
    "DESCRIPTION:Reminder",
    "END:VALARM",
    "END:VEVENT",
    "END:VCALENDAR"
  ].join("\r\n");
}

function downloadIcsFile({ title, description, dueDateMs, fileName = "udhaar-reminder.ics" }) {
  if (typeof window === "undefined" || typeof document === "undefined") return;

  const content = generateIcsContent({ title, description, dueDateMs });
  const blob = new Blob([content], { type: "text/calendar;charset=utf-8" });
  const url = URL.createObjectURL(blob);

  const link = document.createElement("a");
  link.href = url;
  link.setAttribute("download", fileName);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    formatIcsDateTime,
    buildGoogleCalendarUrl,
    generateIcsContent,
    downloadIcsFile
  };
}
