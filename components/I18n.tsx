"use client";

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import {
  DICTIONARIES,
  INTL_LOCALE,
  LOCALE_COOKIE,
  tr,
  type Locale,
  type LocalizedText,
  type Messages,
} from "@/lib/i18n";

interface I18nValue {
  locale: Locale;
  t: Messages;
  /** Localized data text in the current language. */
  tx: (text: LocalizedText | null | undefined) => string;
  setLocale: (locale: Locale) => void;
  formatDate: (iso: string, options: Intl.DateTimeFormatOptions) => string;
}

const I18nContext = createContext<I18nValue | null>(null);

/**
 * Holds the console language. The route (/en, /es) sets the initial one;
 * switching happens in place (no navigation, so pending saves are not lost)
 * and the URL and the cookie are updated to match.
 */
export function I18nProvider({ initialLocale, children }: { initialLocale: Locale; children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(initialLocale);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    document.cookie = `${LOCALE_COOKIE}=${next}; path=/; max-age=31536000; samesite=lax`;
    document.documentElement.lang = next;
    document.title = DICTIONARIES[next].app.title;
    const url = new URL(window.location.href);
    url.pathname = url.pathname.replace(/^\/[^/]+/, `/${next}`);
    window.history.replaceState(window.history.state, "", url);
  }, []);

  const value = useMemo<I18nValue>(
    () => ({
      locale,
      t: DICTIONARIES[locale],
      tx: (text) => tr(text, locale),
      setLocale,
      formatDate: (iso, options) =>
        new Intl.DateTimeFormat(INTL_LOCALE[locale], options).format(new Date(iso)),
    }),
    [locale, setLocale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const value = useContext(I18nContext);
  if (!value) throw new Error("useI18n must be used inside I18nProvider");
  return value;
}
