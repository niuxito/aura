import type { Metadata } from "next";
import { DM_Mono, Public_Sans } from "next/font/google";
import "./globals.css";

const sans = Public_Sans({
  variable: "--font-sans",
  subsets: ["latin"],
});

const mono = DM_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "AURA · Control de misión",
  description: "Plan de misión y plan de prevención para la tripulación",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es" className={`${sans.variable} ${mono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
