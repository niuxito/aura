import type { Metadata } from "next";
import { DM_Mono, Public_Sans } from "next/font/google";
import { notFound } from "next/navigation";
import { DICTIONARIES, LOCALES, isLocale } from "@/lib/i18n";
import "../globals.css";

const sans = Public_Sans({
  variable: "--font-sans",
  subsets: ["latin"],
});

const mono = DM_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

/** /en and /es are prerendered; any other language is a 404. */
export async function generateStaticParams() {
  return LOCALES.map((lang) => ({ lang }));
}

export async function generateMetadata({ params }: LayoutProps<"/[lang]">): Promise<Metadata> {
  const { lang } = await params;
  const t = DICTIONARIES[isLocale(lang) ? lang : "en"];
  return {
    title: t.app.title,
    description: t.app.description,
    alternates: { languages: Object.fromEntries(LOCALES.map((l) => [l, `/${l}`])) },
  };
}

export default async function RootLayout({ children, params }: LayoutProps<"/[lang]">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  return (
    <html lang={lang} className={`${sans.variable} ${mono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
