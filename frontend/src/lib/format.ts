/**
 * Fechas y horas, siempre en hora de Bogotá y en español de Colombia.
 *
 * Nunca se usa la zona del dispositivo: un estudiante que viaja a Madrid debe seguir viendo su
 * clase a las 10:00, la hora a la que su profesora la va a dar. El backend manda ISO con offset
 * (2026-07-15T10:00:00-05:00) y aquí solo se formatea — sin librerías de fechas: Intl basta.
 */

const ZONE = "America/Bogota";
const LOCALE = "es-CO";

/** "mié 15 jul" */
export function fechaCorta(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: ZONE,
    weekday: "short",
    day: "numeric",
    month: "short",
  }).format(new Date(iso));
}

/** "miércoles, 15 de julio" */
export function fechaLarga(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: ZONE,
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date(iso));
}

/** "10:00" */
export function horaBogota(iso: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone: ZONE,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(iso));
}

/** "10:00–11:00" */
export function rangoHoras(inicioIso: string, finIso: string): string {
  return `${horaBogota(inicioIso)}–${horaBogota(finIso)}`;
}

/** "Mié 15 jul · 10:00–11:00", el encabezado de una tarjeta de clase. */
export function fechaYRango(inicioIso: string, finIso: string): string {
  const fecha = fechaCorta(inicioIso);
  return `${fecha.charAt(0).toUpperCase()}${fecha.slice(1)} · ${rangoHoras(inicioIso, finIso)}`;
}

/** La fecha (YYYY-MM-DD) del instante, ya en Bogotá: sirve para agrupar cupos por día. */
export function diaBogota(iso: string): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(iso));
}

/**
 * Precio en pesos colombianos, sin decimales y con punto de miles: 45000 → "$45.000".
 * El COP no usa centavos; se redondea al peso. Formato es-CO (separador de miles con punto).
 */
export function precioCop(n: number): string {
  return `$${new Intl.NumberFormat(LOCALE).format(Math.round(n))}`;
}

/**
 * Fecha relativa breve para reseñas: "hoy", "hace 3 días", "hace 2 meses", "hace 1 año". Usa la hora
 * del dispositivo como "ahora" (una reseña siempre está en el pasado, así que no hay ambigüedad de
 * zona relevante). Sin librerías: Intl.RelativeTimeFormat basta.
 */
export function fechaRelativa(iso: string): string {
  const rtf = new Intl.RelativeTimeFormat(LOCALE, { numeric: "auto" });
  const dias = Math.round((new Date(iso).getTime() - Date.now()) / 86_400_000);
  const abs = Math.abs(dias);
  if (abs < 1) return "hoy";
  if (abs < 30) return rtf.format(dias, "day");
  if (abs < 365) return rtf.format(Math.round(dias / 30), "month");
  return rtf.format(Math.round(dias / 365), "year");
}

/** Iniciales para el avatar: "María Gómez" → "MG" */
export function iniciales(nombre: string): string {
  return nombre
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte.charAt(0).toUpperCase())
    .join("");
}
