/**
 * Número de soporte por WhatsApp. Es público (va al bundle del cliente), no un secreto. Se puede
 * sobrescribir por entorno en el despliegue; el valor por defecto deja el botón funcionando ya.
 */
export const SUPPORT_WHATSAPP = process.env.NEXT_PUBLIC_SUPPORT_WHATSAPP || "573023063447";

/**
 * Origen público del sitio. Lo usan el sitemap, robots y los JSON-LD de las páginas de marketing
 * para construir URLs absolutas (canonical, @id). Se sobrescribe por entorno en el despliegue.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || "https://orionidiomas.com";

/** Link wa.me con mensaje opcional. null si no hay número configurado (el botón entonces no se pinta). */
export function whatsappSoporte(mensaje?: string): string | null {
  const digitos = SUPPORT_WHATSAPP.replace(/\D/g, "");
  if (!digitos) return null;
  return `https://wa.me/${digitos}${mensaje ? `?text=${encodeURIComponent(mensaje)}` : ""}`;
}

/**
 * Las cuentas de Orión en redes (Pardo, 23/09/2026). Van al pie de la portada y al `sameAs` del
 * JSON-LD, que es como Google une el sitio con sus perfiles.
 */
export const REDES_SOCIALES = [
  { nombre: "Instagram", url: "https://www.instagram.com/orionidiomascom/" },
  { nombre: "TikTok", url: "https://www.tiktok.com/@orion.idiomas.com" },
  { nombre: "LinkedIn", url: "https://www.linkedin.com/company/orion-idiomas/" },
] as const;
