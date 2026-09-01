import type { MetadataRoute } from "next";

const SITE = process.env.NEXT_PUBLIC_SITE_URL || "https://orionidiomas.com";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // Las zonas con sesión no se indexan: ni aportan SEO ni deben salir en buscadores.
      disallow: ["/mis-clases", "/profesores", "/disponibilidad", "/cuenta", "/perfil", "/admin"],
    },
    sitemap: `${SITE}/sitemap.xml`,
  };
}
