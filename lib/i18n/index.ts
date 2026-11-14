// Internationalization: languages, localized text and UI dictionaries.
// Adding a language = adding it to LOCALES (text.ts) and a dictionary here.

import { en, type Messages } from "./en";
import { es } from "./es";
import { LOCALES, localized, type Locale, type LocalizedText } from "./text";

export * from "./text";
export type { Messages } from "./en";

export const DICTIONARIES: Record<Locale, Messages> = { en, es };

/** Builds a LocalizedText from a dictionary entry, evaluated in every language. */
export function msg(fn: (m: Messages, locale: Locale) => string): LocalizedText {
  return localized((locale) => fn(DICTIONARIES[locale], locale));
}

/** Language list as a sentence in the given locale ("English and Spanish"). */
export function languageList(locale: Locale): string {
  const names = LOCALES.map((l) => DICTIONARIES[locale].languageName[l]);
  const and = locale === "es" ? " y " : " and ";
  return names.length <= 1 ? names.join("") : `${names.slice(0, -1).join(", ")}${and}${names.at(-1)}`;
}
