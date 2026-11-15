// MET (Mission Elapsed Time): time elapsed since launch.
// Stored in seconds and written as DD/HH:MM or DD/HH:MM:SS, as in NASA
// timelines (for example, TLI at 01/01:08:42).

export const MINUTE = 60;
export const HOUR = 3600;
export const DAY = 86400;

const pad = (n: number) => String(n).padStart(2, "0");

export function formatMet(seconds: number): string {
  const sign = seconds < 0 ? "-" : "";
  const abs = Math.abs(Math.round(seconds));
  const d = Math.floor(abs / DAY);
  const h = Math.floor((abs % DAY) / HOUR);
  const m = Math.floor((abs % HOUR) / MINUTE);
  const s = abs % MINUTE;
  return `${sign}${pad(d)}/${pad(h)}:${pad(m)}${s ? `:${pad(s)}` : ""}`;
}

/** Accepts D/HH:MM and D/HH:MM:SS, with an optional sign. Returns null if invalid. */
export function parseMet(text: string): number | null {
  const match = /^(-)?(\d{1,3})\/(\d{1,2}):(\d{2})(?::(\d{2}))?$/.exec(text.trim());
  if (!match) return null;
  const [, sign, d, h, m, s] = match;
  const hours = Number(h);
  const minutes = Number(m);
  const secs = Number(s ?? 0);
  if (hours > 23 || minutes > 59 || secs > 59) return null;
  const total = Number(d) * DAY + hours * HOUR + minutes * MINUTE + secs;
  return sign ? -total : total;
}

/** For data written by hand in code; throws if the text is not a MET. */
export function met(text: string): number {
  const value = parseMet(text);
  if (value === null) throw new Error(`Invalid MET: ${text}`);
  return value;
}

export function formatFlightDay(day: number): string {
  return `FD${pad(day)}`;
}
