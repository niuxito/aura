import { notFound } from "next/navigation";
import App from "@/components/App";
import { isLocale } from "@/lib/i18n";

export default async function Home({ params }: PageProps<"/[lang]">) {
  const { lang } = await params;
  if (!isLocale(lang)) notFound();
  return <App locale={lang} />;
}
