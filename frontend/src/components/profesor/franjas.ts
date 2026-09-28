/**
 * La aritmética de las franjas semanales, sin React: pasar horas de pared a minutos, ajustar a la
 * media hora, encontrar el hueco libre de un día y contar cuántos cupos abre un horario. Vive aparte
 * para poder probarla sin montar la rejilla.
 *
 * <p>Todo se razona en minutos del día (0–1440) y en intervalos semiabiertos [inicio, fin), igual que
 * el backend: una franja de 18:00 a 19:00 y otra de 19:00 a 20:00 se tocan, no se cruzan.
 */

import { hora12 } from "@/lib/format";

/** La resolución de la rejilla y de las franjas: empiezan y terminan en punto o a la media hora. */
export const PASO = 30;

/**
 * Cada cuánto arranca un cupo dentro de una franja. Es `SlotCalculator.SLOT_CADENCE` en el backend,
 * una constante de código y no un ajuste: por eso va escrita aquí y no se lee de las cifras.
 */
export const CADENCIA_CUPOS = 30;

/** La franja más corta que tiene sentido abrir sola: con clases de 55 minutos, media hora no deja ninguna. */
export const DURACION_MINIMA = 60;

/** La última hora de pared que existe: el backend guarda `LocalTime`, que no admite las 24:00. */
export const FIN_DEL_DIA = 23 * 60 + 30;

export type Intervalo = { inicio: number; fin: number };
export type FranjaDia = Intervalo & { weekday: number };

/** "18:30" o "18:30:00" → 1110. */
export function aMinutos(hhmm: string): number {
  return Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
}

/** 1110 → "18:30", que es lo que entiende el backend. */
export function aHhmm(minutos: number): string {
  return `${String(Math.floor(minutos / 60)).padStart(2, "0")}:${String(minutos % 60).padStart(2, "0")}`;
}

/** Baja a la media hora anterior: 18:47 → 18:30. */
export function bajarAlPaso(minutos: number): number {
  return Math.floor(minutos / PASO) * PASO;
}

/** A la media hora más cercana: 18:47 → 19:00. Para mover y estirar, donde se suelta «cerca de». */
export function redondearAlPaso(minutos: number): number {
  return Math.round(minutos / PASO) * PASO;
}

/** "6:00 – 8:30 PM", y "11:00 AM – 1:00 PM" cuando cruza el mediodía. El AM/PM se dice una vez si no cambia. */
export function rangoLargo(inicio: number, fin: number): string {
  const desde = hora12(aHhmm(inicio % 1440));
  const hasta = hora12(aHhmm(fin % 1440));
  return desde.slice(-2) === hasta.slice(-2) && fin < 1440
    ? `${desde.slice(0, -3)} – ${hasta}`
    : `${desde} – ${hasta}`;
}

/** Une lo que se cruza o se toca: [18–19) y [19–20) son una sola franja de 18 a 20. */
export function unir(intervalos: Intervalo[]): Intervalo[] {
  const ordenados = [...intervalos].sort((a, b) => a.inicio - b.inicio);
  const unidos: Intervalo[] = [];
  for (const actual of ordenados) {
    const ultimo = unidos[unidos.length - 1];
    if (ultimo && actual.inicio <= ultimo.fin) {
      ultimo.fin = Math.max(ultimo.fin, actual.fin);
    } else {
      unidos.push({ ...actual });
    }
  }
  return unidos;
}

/**
 * Cuántas clases caben en un tramo continuo: arrancan cada media hora mientras la clase quepa
 * entera. 18:00–21:00 con clases de 55 da 18:00, 18:30, 19:00, 19:30 y 20:00 → 5.
 */
export function cuposDeTramo(minutos: number, duracionClase: number): number {
  if (minutos < duracionClase) return 0;
  return Math.floor((minutos - duracionClase) / CADENCIA_CUPOS) + 1;
}

/**
 * Los cupos de clase que abre un horario cada semana. Las franjas de un mismo día que se tocan se
 * cuentan unidas, que es como las lee el cálculo de cupos: dos franjas contiguas son un solo tramo.
 */
export function cuposPorSemana(franjas: FranjaDia[], duracionClase: number): number {
  let total = 0;
  for (let dia = 1; dia <= 7; dia++) {
    for (const tramo of unir(franjas.filter((f) => f.weekday === dia))) {
      total += cuposDeTramo(tramo.fin - tramo.inicio, duracionClase);
    }
  }
  return total;
}

/** Las horas abiertas a la semana, sin contar dos veces lo que se cruce. */
export function horasPorSemana(franjas: FranjaDia[]): number {
  let minutos = 0;
  for (let dia = 1; dia <= 7; dia++) {
    for (const tramo of unir(franjas.filter((f) => f.weekday === dia))) {
      minutos += tramo.fin - tramo.inicio;
    }
  }
  return minutos / 60;
}

/**
 * El hueco libre que rodea un minuto en un día: desde el fin de la franja anterior hasta el inicio
 * de la siguiente. `null` si el minuto cae dentro de una franja.
 */
export function huecoLibre(delDia: Intervalo[], minuto: number): Intervalo | null {
  let inicio = 0;
  let fin = FIN_DEL_DIA;
  for (const franja of delDia) {
    if (franja.inicio <= minuto && minuto < franja.fin) return null;
    if (franja.fin <= minuto) inicio = Math.max(inicio, franja.fin);
    if (franja.inicio > minuto) fin = Math.min(fin, franja.inicio);
  }
  return { inicio, fin };
}

/** Si el tramo se cruza con alguna franja (tocarse no es cruzarse). */
export function seCruza(delDia: Intervalo[], tramo: Intervalo): boolean {
  return delDia.some((franja) => franja.inicio < tramo.fin && tramo.inicio < franja.fin);
}

/**
 * La franja que resulta de arrastrar desde `ancla` hasta `actual` en un día (o de un clic, cuando
 * son la misma media hora). Nunca se sale del hueco libre donde empezó: toca a las vecinas pero no
 * las pisa. Y dura al menos una hora —lo que cabe de una clase— salvo que el hueco sea más corto:
 * media hora entre dos franjas sí sirve, porque las une.
 */
export function tramoArrastrado(delDia: Intervalo[], ancla: number, actual: number): Intervalo | null {
  const hueco = huecoLibre(delDia, ancla);
  if (!hueco) return null;
  let inicio = Math.max(hueco.inicio, Math.min(ancla, actual));
  let fin = Math.min(hueco.fin, Math.max(ancla, actual) + PASO);

  const minima = Math.min(DURACION_MINIMA, hueco.fin - hueco.inicio);
  if (fin - inicio < minima) {
    fin = Math.min(hueco.fin, inicio + minima);
    inicio = Math.max(hueco.inicio, fin - minima);
  }
  return fin > inicio ? { inicio, fin } : null;
}

/** La primera hora en punto libre del día a partir de las 8 AM, para el formulario de «Añadir». */
export function primeraHoraLibre(delDia: Intervalo[]): number {
  for (let hora = 8 * 60; hora + DURACION_MINIMA <= 23 * 60; hora += 60) {
    if (!seCruza(delDia, { inicio: hora, fin: hora + DURACION_MINIMA })) return hora;
  }
  return 8 * 60;
}

/**
 * Las horas que dibuja la rejilla: de 5 AM a 11 PM, ensanchada lo que haga falta para que se vean
 * todas las franjas. Una franja de las 4 AM tiene que verse y poderse borrar ahí mismo.
 */
export function ventanaDeHoras(franjas: Intervalo[]): { desde: number; hasta: number } {
  let desde = 5;
  let hasta = 23;
  for (const franja of franjas) {
    desde = Math.min(desde, Math.floor(franja.inicio / 60));
    hasta = Math.max(hasta, Math.ceil(franja.fin / 60));
  }
  return { desde, hasta: Math.min(24, hasta) };
}
