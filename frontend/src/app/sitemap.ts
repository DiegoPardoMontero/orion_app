import type { MetadataRoute } from "next";

const SITE = process.env.NEXT_PUBLIC_SITE_URL || "https://orionidiomas.com";

/** Solo las páginas públicas; las de sesión se excluyen (sin valor SEO y son privadas). */
export default function sitemap(): MetadataRoute.Sitemap {
  return [
    { url: `${SITE}/`, changeFrequency: "monthly", priority: 1 },
    { url: `${SITE}/registro`, changeFrequency: "yearly", priority: 0.8 },
    { url: `${SITE}/login`, changeFrequency: "yearly", priority: 0.5 },
  ];
}
