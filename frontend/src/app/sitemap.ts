import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/config";

/**
 * Solo páginas PÚBLICAS e indexables. Las de sesión se excluyen (sin valor SEO y privadas).
 *
 * Nota de alcance (Bloque 7): el marketplace `/profesores` y los perfiles `/profesores/[id]` viven
 * en la zona autenticada —un anónimo cae en /login—, así que NO se listan aquí (los desautoriza
 * `robots.ts` y no aportarían SEO). Lo que sí es público: la portada, las landings por idioma y
 * "Enseña en Orión". Los tres idiomas del catálogo tienen landing propia (/idiomas/{code}).
 */
const IDIOMAS_PUBLICOS = ["EN", "FR", "ES"];

export default function sitemap(): MetadataRoute.Sitemap {
  const idiomas: MetadataRoute.Sitemap = IDIOMAS_PUBLICOS.map((code) => ({
    url: `${SITE_URL}/idiomas/${code}`,
    changeFrequency: "weekly",
    priority: 0.7,
  }));

  return [
    { url: `${SITE_URL}/`, changeFrequency: "weekly", priority: 1 },
    { url: `${SITE_URL}/ensena-con-orion`, changeFrequency: "monthly", priority: 0.8 },
    ...idiomas,
    { url: `${SITE_URL}/registro`, changeFrequency: "yearly", priority: 0.6 },
    { url: `${SITE_URL}/login`, changeFrequency: "yearly", priority: 0.4 },
  ];
}
