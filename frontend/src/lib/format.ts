const missing = "–";

export function formatAmount(
  value: number | bigint | null | undefined,
): string {
  if (value == null) return missing;
  return value
    .toLocaleString("ko-KR", { maximumFractionDigits: 0 })
    .replace("-", "−");
}

/** Input is already a percentage, not a fractional ratio. */
export function formatRate(value: number | null | undefined): string {
  if (value == null) return missing;
  return `${value.toFixed(1).replace("-", "−")}%`;
}
export const formatPercent = formatRate;

type DateValue = string | Date | null | undefined;
function localParts(value: DateValue): string | null {
  if (value == null || value === "") return null;
  if (typeof value === "string" && !/[zZ]|[+-]\d{2}:?\d{2}$/.test(value)) {
    // PostgreSQL TIMESTAMP and date-only values are already Seoul wall time.
    return value.replace("T", " ");
  }
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const part = (type: string) =>
    parts.find((item) => item.type === type)?.value ?? "";
  return `${part("year")}-${part("month")}-${part("day")} ${part("hour")}:${part("minute")}`;
}
export function formatDate(value: DateValue): string {
  return localParts(value)?.slice(0, 10) ?? missing;
}
export function formatMonth(value: DateValue): string {
  return localParts(value)?.slice(0, 7) ?? missing;
}
export function formatDateTime(value: DateValue): string {
  return localParts(value)?.slice(0, 16) ?? missing;
}
