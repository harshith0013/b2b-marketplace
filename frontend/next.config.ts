import type { NextConfig } from "next";

/**
 * Static Content-Security-Policy. Scripts must still allow 'unsafe-inline' for Next.js's inline
 * bootstrap scripts; a nonce-based policy would make every page dynamic, so it is deferred to
 * Phase 12. Everything else is locked to this site, except Razorpay's checkout (its script, its
 * payment window and its logging), which buyers use to pay online. React needs 'unsafe-eval' only
 * in development.
 */
const RAZORPAY_CHECKOUT = "https://checkout.razorpay.com";
const RAZORPAY_API = "https://api.razorpay.com";
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline' ${RAZORPAY_CHECKOUT}${process.env.NODE_ENV === "development" ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https://*.razorpay.com",
  "font-src 'self'",
  `connect-src 'self' ${RAZORPAY_API} https://lumberjack.razorpay.com`,
  `frame-src ${RAZORPAY_CHECKOUT} ${RAZORPAY_API}`,
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=()",
  },
  // Not in search engines until the pilot: also covers photos and other non-HTML responses.
  ...(process.env.SITE_INDEXABLE === "true"
    ? []
    : [{ key: "X-Robots-Tag", value: "noindex, nofollow" }]),
];

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  cacheComponents: true,
  experimental: {
    // Photo uploads go through a Server Action: 5 MB per photo plus form overhead.
    serverActions: { bodySizeLimit: "6mb" },
  },
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
