import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /**
   * El navegador solo habla con :3000. Next reescribe /api/* hacia el backend, así que para el
   * navegador existe un único origen: las cookies (ORION_SESSION, XSRF-TOKEN) fluyen sin CORS,
   * sin preflights y sin problemas de SameSite.
   */
  async rewrites() {
    const api = process.env.API_URL ?? "http://localhost:8080";
    return [
      { source: "/api/:path*", destination: `${api}/api/:path*` },
      { source: "/actuator/:path*", destination: `${api}/actuator/:path*` },
    ];
  },
};

export default nextConfig;
