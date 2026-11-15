"use client";

import { withText, type LocalizedText } from "@/lib/i18n";
import { useI18n } from "./I18n";

interface Props {
  value: LocalizedText;
  onChange: (next: LocalizedText) => void;
  ariaLabel?: string;
  /** Renders a textarea with this many rows instead of an input. */
  rows?: number;
}

/**
 * Edits a LocalizedText in the console's current language. If that language
 * has no text yet, the fallback (English) is shown as a placeholder.
 */
export default function LocalizedInput({ value, onChange, ariaLabel, rows }: Props) {
  const { locale, tx, t } = useI18n();
  const own = value[locale] ?? "";
  const missing = own === "" && tx(value) !== "";
  const common = {
    value: own,
    "aria-label": ariaLabel,
    placeholder: missing ? tx(value) : undefined,
    title: missing ? t.common.fallbackHint : undefined,
    className: missing ? "missing-translation" : undefined,
  };
  return rows ? (
    <textarea rows={rows} {...common} onChange={(e) => onChange(withText(value, locale, e.target.value))} />
  ) : (
    <input {...common} onChange={(e) => onChange(withText(value, locale, e.target.value))} />
  );
}
