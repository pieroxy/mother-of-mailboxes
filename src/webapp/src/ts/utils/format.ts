/** Byte size with the largest unit that keeps the value readable, e.g. 47852014 -> "45.6 MB". */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex++;
  }
  return value.toFixed(1) + " " + units[unitIndex];
}

/** Entry count with the largest unit that keeps the value readable, e.g. 47852014 -> "47.9M". */
export function formatCount(count: number): string {
  if (count < 1000) return String(count);
  const units = ["K", "M", "B"];
  let value = count / 1000;
  let unitIndex = 0;
  while (value >= 1000 && unitIndex < units.length - 1) {
    value /= 1000;
    unitIndex++;
  }
  return value.toFixed(1) + units[unitIndex];
}

/**
 * A duration in seconds, as its two most significant units, e.g. 71460 -> "19h51m"
 * Below a minute, just seconds: "45s".
 */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.round(Math.abs(totalSeconds));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor(seconds / 3600) % 24;
  const minutes = Math.floor(seconds / 60) % 60;
  const secs = seconds % 60;

  if (days > 0) return days + "d" + hours + "h";
  if (hours > 0) return hours + "h" + minutes + "m";
  if (minutes > 0) return minutes + "m" + secs + "s";
  return secs + "s";
}
