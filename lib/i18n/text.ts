// Languages and localized text. Shared by the UI and by the data: every
// human-readable text in the mission state and in the mission package is a
// LocalizedText, so the astronaut interface can show it in its own language.

export const LOCALES = ["en", "es"] as const;
export type Locale = (typeof LOCALES)[number];

/** Reference language: NASA works in English, so every text should exist in it. */
export const DEFAULT_LOCALE: Locale = "en";

export const LOCALE_NAMES: Record<Locale, string> = {
  en: "English",
  es: "Español",
};

/** Cookie that remembers the console language (read by proxy.ts). */
export const LOCALE_COOKIE = "aura-locale";

/** BCP 47 tag used for dates and numbers. */
export const INTL_LOCALE: Record<Locale, string> = {
  en: "en-US",
  es: "es-ES",
};

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (LOCALES as readonly string[]).includes(value);
}

/**
 * A text in several languages, e.g. { en: "Sleep", es: "Sueño" }.
 * Missing languages fall back to English, then to any available language.
 */
export type LocalizedText = Partial<Record<Locale, string>>;

/** Builds a LocalizedText from its English and (optional) Spanish versions. */
export function L(en: string, es?: string): LocalizedText {
  return es === undefined ? { en } : { en, es };
}

/** The text in the requested language, with fallback. */
export function tr(text: LocalizedText | null | undefined, locale: Locale): string {
  if (!text) return "";
  return (
    text[locale] ||
    text[DEFAULT_LOCALE] ||
    LOCALES.map((l) => text[l]).find((t) => t) ||
    ""
  );
}

/** Returns a copy of the text with one language changed (empty removes it). */
export function withText(
  text: LocalizedText | null | undefined,
  locale: Locale,
  value: string,
): LocalizedText {
  const next: LocalizedText = { ...(text ?? {}) };
  if (value === "") delete next[locale];
  else next[locale] = value;
  return next;
}

/** Builds a LocalizedText by evaluating a function once per language. */
export function localized(fn: (locale: Locale) => string): LocalizedText {
  const out: LocalizedText = {};
  for (const locale of LOCALES) out[locale] = fn(locale);
  return out;
}

/** true if the value looks like a LocalizedText (only language keys, string values). */
export function isLocalizedText(value: unknown): value is LocalizedText {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const entries = Object.entries(value);
  return (
    entries.length > 0 &&
    entries.every(([k, v]) => isLocale(k) && typeof v === "string")
  );
}

/** Every LocalizedText inside a value, found by walking it. */
export function collectTexts(value: unknown, out: LocalizedText[] = []): LocalizedText[] {
  if (isLocalizedText(value)) {
    out.push(value);
  } else if (Array.isArray(value)) {
    for (const v of value) collectTexts(v, out);
  } else if (value && typeof value === "object") {
    for (const v of Object.values(value)) collectTexts(v, out);
  }
  return out;
}
