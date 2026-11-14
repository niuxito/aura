import { NextResponse, type NextRequest } from "next/server";
import { DEFAULT_LOCALE, LOCALE_COOKIE, isLocale, type Locale } from "@/lib/i18n/text";

// The console lives under /en and /es. A request to / goes to the language
// chosen earlier (cookie) or, the first time, to the browser's preferred one;
// English otherwise. Query parameters (?tab=, ?fd=) are kept.

function preferredLocale(request: NextRequest): Locale {
  const saved = request.cookies.get(LOCALE_COOKIE)?.value;
  if (isLocale(saved)) return saved;
  const accepted = (request.headers.get("accept-language") ?? "")
    .split(",")
    .map((part) => {
      const [tag, q] = part.trim().split(";q=");
      return { lang: tag.toLowerCase().split("-")[0], q: q === undefined ? 1 : Number(q) };
    })
    .sort((a, b) => b.q - a.q);
  return accepted.map((a) => a.lang).find((l): l is Locale => isLocale(l)) ?? DEFAULT_LOCALE;
}

export function proxy(request: NextRequest) {
  const url = request.nextUrl.clone();
  url.pathname = `/${preferredLocale(request)}`;
  return NextResponse.redirect(url);
}

export const config = {
  matcher: "/",
};
