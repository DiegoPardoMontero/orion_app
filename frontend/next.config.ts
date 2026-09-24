import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Empaqueta el servidor y solo sus dependencias en .next/standalone → imagen pequeña, sin
  // arrastrar todo node_modules. El Dockerfile copia esa carpeta y arranca con `node server.js`.
  output: "standalone",

  // No anunciar el framework: es información gratis para quien busca versiones vulnerables.
  poweredByHeader: false,

  /**
   * Cabeceras de seguridad de las páginas. La API ya las lleva (las pone Spring Security); las
   * páginas que sirve Next no llevaban ninguna.
   *
   * <p>Solo las que no pueden romper nada: que nadie meta Orión dentro de un iframe ajeno
   * (clickjacking), que el navegador no adivine tipos de archivo, y que al salir a otro sitio no
   * viaje la URL completa. Una CSP y un Permissions-Policy quedan pendientes a propósito: el aula
   * embebe 8x8 con cámara y micrófono, y el diagnóstico habla con OpenAI por WebRTC, y restringirlos
   * sin probar una clase real podría dejar la cámara apagada en producción.
   */
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
        ],
      },
    ];
  },

  /**
   * El navegador solo habla con :3000. Next reescribe /api/* hacia el backend, así que para el
   * navegador existe un único origen: las cookies (ORION_SESSION, XSRF-TOKEN) fluyen sin CORS,
   * sin preflights y sin problemas de SameSite.
   *
   * API_URL se lee al ARRANCAR el servidor, no al compilar: la misma imagen sirve en local
   * (localhost:8080) y en Railway (backend.railway.internal:8080) sin recompilar.
   */
  /**
   * Un solo dominio: quien entra por www. se va al dominio sin www. antes de nada. La vuelta de Google
   * llega siempre al dominio de ORION_APP_BASE_URL, y la cookie que la espera es de un solo host:
   * quien empezaba en www. volvía sin ella y veía «No pudimos entrar» (24/09/2026).
   */
  async redirects() {
    return [
      {
        source: "/:path*",
        has: [{ type: "host", value: "www\\.(?<dominio>.+)" }],
        destination: "https://:dominio/:path*",
        permanent: true,
      },
    ];
  },

  async rewrites() {
    const api = process.env.API_URL ?? "http://localhost:8080";
    return [
      { source: "/api/:path*", destination: `${api}/api/:path*` },
      { source: "/actuator/:path*", destination: `${api}/actuator/:path*` },
      // Entrar con Google, Apple o Facebook: la ida (/oauth2/authorization/google) y la vuelta
      // (/login/oauth2/code/google) las atiende el backend, pero bajo el dominio público, que es
      // el único que conoce el navegador y el que se da de alta en la consola de cada proveedor.
      // La página /login sigue siendo de Next: solo se reenvía lo que cuelga de /login/oauth2.
      { source: "/oauth2/:path*", destination: `${api}/oauth2/:path*` },
      { source: "/login/oauth2/:path*", destination: `${api}/login/oauth2/:path*` },
    ];
  },
};

export default nextConfig;
