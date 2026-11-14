"use client";

import { useState } from "react";
import { formatMet, parseMet } from "@/lib/met";
import { useI18n } from "./I18n";

interface Props {
  value: number;
  onCommit: (met: number) => void;
  ariaLabel: string;
}

/**
 * Text field for a MET (DD/HH:MM[:SS]). Commits on blur or Enter; if the text
 * is not a valid MET, it reverts to the previous value.
 * Use it with key={value} so it reflects changes made from outside.
 */
export default function MetInput({ value, onCommit, ariaLabel }: Props) {
  const { t } = useI18n();
  const [text, setText] = useState(formatMet(value));

  const commit = () => {
    const parsed = parseMet(text);
    if (parsed === null) setText(formatMet(value));
    else if (parsed !== value) onCommit(parsed);
    else setText(formatMet(value));
  };

  return (
    <input
      className="met-input"
      value={text}
      aria-label={ariaLabel}
      title={t.metInput.title}
      onChange={(e) => setText(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") e.currentTarget.blur();
      }}
    />
  );
}
