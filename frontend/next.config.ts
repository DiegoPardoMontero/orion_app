import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Empaqueta el servidor y solo sus dependencias en .next/standalone → imagen pequeña, sin
  // arrastrar todo node_modules. El Dockerfile copia esa carpeta y arranca con `node server.js`.
  output: "standalone",

  /**
   * El navegador solo habla con :3000. Next reescribe /api/* hacia el backend, así que para el
   * navegador existe un único origen: las cookies (ORION_SESSION, XSRF-TOKEN) fluyen sin CORS,
   * sin preflights y sin problemas de SameSite.
   *
   * API_URL se lee al ARRANCAR el servidor, no al compilar: la misma imagen sirve en local
   * (localhost:8080) y en Railway (backend.railway.internal:8080) sin recompilar.
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
