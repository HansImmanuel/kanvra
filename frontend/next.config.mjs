/**
 * Next.js configuration.
 *
 * /api/* is reversed to the Spring Boot backend on :8080 (overridable via
 * KANVRA_BACKEND_URL). Keeping API calls same-origin to the frontend means the
 * cookie-based session (SameSite=Lax/Strict, httpOnly) behaves exactly as the
 * backend security model expects: the browser sends cookies to this origin and
 * Next proxies them server-side, so no CORS/cross-site cookie loss occurs.
 */
const BACKEND = process.env.KANVRA_BACKEND_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND}/api/:path*`
      }
    ];
  }
};

export default nextConfig;