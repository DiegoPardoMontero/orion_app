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
