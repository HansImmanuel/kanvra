import { NextRequest, NextResponse } from "next/server";

/**
 * Injects the CSRF double-submit header for same-origin proxied API calls.
 *
 * The csrf_token cookie is a browser-session cookie the JS client can't read
 * (there is no document.cookie API for non-httpOnly cookies either). Instead of
 * relying on client JS to mirror it, the middleware reads it server-side and
 * adds the X-CSRF-Token header before the /api/* rewrite reaches Spring Boot —
 * satisfying the double-submit check (SPEC §3.1) without exposing the csrf value
 * to scripts.
 */
export function proxy(request: NextRequest) {
  if (!request.nextUrl.pathname.startsWith("/api/")) {
    return NextResponse.next();
  }
  const csrf = request.cookies.get("csrf_token")?.value;
  if (!csrf) {
    return NextResponse.next();
  }
  const headers = new Headers(request.headers);
  if (!headers.has("x-csrf-token")) {
    headers.set("x-csrf-token", csrf);
  }
  return NextResponse.next({ request: { headers } });
}

export const config = {
  matcher: ["/api/:path*"]
};