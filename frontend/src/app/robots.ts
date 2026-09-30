import type { MetadataRoute } from "next";
import { ES_PRUEBAS } from "@/lib/config";

const SITE = process.env.NEXT_PUBLIC_SITE_URL || "https://orionidiomas.com";

export default function robots(): MetadataRoute.Robots {
  // El ambiente de pruebas no se indexa nunca (29/09/2026): cerrado entero y sin sitemap.
  if (ES_PRUEBAS) {
    return { rules: { userAgent: "*", disallow: "/" } };
  }
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
