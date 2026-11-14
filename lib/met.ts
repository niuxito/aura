// MET (Mission Elapsed Time): tiempo transcurrido desde el despegue.
// Se guarda en segundos y se escribe como DD/HH:MM o DD/HH:MM:SS, igual que
// en los cronogramas de NASA (por ejemplo, TLI en 01/01:08:42).

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

/** Acepta D/HH:MM y D/HH:MM:SS, con signo opcional. Devuelve null si no es válido. */
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

/** Para datos escritos a mano en el código; falla si el texto no es un MET. */
export function met(text: string): number {
  const value = parseMet(text);
  if (value === null) throw new Error(`MET no válido: ${text}`);
  return value;
}

export function formatFlightDay(day: number): string {
  return `FD${pad(day)}`;
}
