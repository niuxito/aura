"use client";

import { useState } from "react";
import { formatMet, parseMet } from "@/lib/met";

interface Props {
  value: number;
  onCommit: (met: number) => void;
  ariaLabel: string;
}

/**
 * Campo de texto para un MET (DD/HH:MM[:SS]). Confirma al salir o con Intro;
 * si el texto no es un MET válido, vuelve al valor anterior.
 * Úsalo con key={value} para que refleje cambios hechos desde fuera.
 */
export default function MetInput({ value, onCommit, ariaLabel }: Props) {
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
      title="MET · DD/HH:MM o DD/HH:MM:SS"
      onChange={(e) => setText(e.target.value)}
      onBlur={commit}
      onKeyDown={(e) => {
        if (e.key === "Enter") e.currentTarget.blur();
      }}
    />
  );
}
