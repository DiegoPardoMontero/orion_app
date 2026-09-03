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

/**
 * "10:00 AM". Formato de 12 horas porque es como se lee la hora en Colombia: nadie queda de verse
 * "a las 18". Se compone a mano en vez de dejárselo a Intl porque es-CO devuelve "6:00 p. m.", con
 * espacios y puntos que en un chip estrecho se parten en dos líneas.
 */
export function horaBogota(iso: string): string {
  const partes = new Intl.DateTimeFormat("en-US", {
    timeZone: ZONE,
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  }).formatToParts(new Date(iso));

  const valor = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? "";
  return `${valor("hour")}:${valor("minute")} ${valor("dayPeriod").toUpperCase()}`;
}

/**
 * "10:00 – 11:00 AM", y "11:00 AM – 12:00 PM" cuando cruzan el mediodía. El meridiano se dice una
 * sola vez si es el mismo en los dos extremos: repetirlo no añade información y alarga el renglón.
 */
export function rangoHoras(inicioIso: string, finIso: string): string {
  const desde = horaBogota(inicioIso);
  const hasta = horaBogota(finIso);
  const meridianoDesde = desde.slice(-2);

  return meridianoDesde === hasta.slice(-2)
    ? `${desde.slice(0, -3)} – ${hasta}`
    : `${desde} – ${hasta}`;
}

/**
 * Una hora de pared ("18:00", como las guarda la disponibilidad) en formato de 12 horas: "6:00 PM".
 * Sin fecha de por medio: aquí no hay instante ni zona, solo la hora que el profesor escribió.
 */
export function hora12(hhmm: string): string {
  const [h, m = "00"] = hhmm.split(":");
  const hora = Number(h);
  const meridiano = hora < 12 ? "AM" : "PM";
  const doce = hora % 12 === 0 ? 12 : hora % 12;
  return `${doce}:${m} ${meridiano}`;
}

/** "6 PM" cuando está en punto, "6:30 PM" si no. Para retículas estrechas. */
export function hora12Compacta(hhmm: string): string {
  const completa = hora12(hhmm);
  return completa.replace(":00 ", " ");
}

/**
 * Un rango de horas de pared, compacto: "6–9 PM" cuando ambos extremos comparten meridiano y
 * "11 AM–1 PM" cuando no. Repetir el AM/PM en los dos lados alarga la etiqueta justo donde menos
 * espacio hay —las columnas de la grilla semanal— y no aporta nada: si el rango no cruza el
 * mediodía, decirlo una vez basta.
 */
export function rangoCompacto(inicioHhmm: string, finHhmm: string): string {
  const inicio = hora12Compacta(inicioHhmm);
  const fin = hora12Compacta(finHhmm);
  const meridiano = inicio.slice(-2);
  return meridiano === fin.slice(-2) ? `${inicio.slice(0, -3)}–${fin}` : `${inicio}–${fin}`;
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
 * La tarifa como la lee un estudiante. Distingue tres estados que un `rate ? … : …` confunde en
 * dos, porque 0 es falsy: sin fijar (null), gratuita (0) y con precio.
 */
export function tarifaClase(rate: number | null | undefined): string | null {
  if (rate === null || rate === undefined) return null;
  return rate === 0 ? "Gratis" : precioCop(rate);
}

/** true solo para la tarifa gratuita — no para la que aún no se ha fijado. */
export function esGratis(rate: number | null | undefined): boolean {
  return rate === 0;
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
