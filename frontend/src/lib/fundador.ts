import { precioCop } from "@/lib/format";

/**
 * El profe fundador en palabras (brief del profe fundador, 25/09/2026): la ayuda que acompaña la
 * tarifa del profe y el estado que ve el admin. Los números salen de la API —la comisión base y la
 * promesa de cada profe—; aquí solo se escriben.
 */

export type Fundador = {
  rateBps?: number;
  periodMonths?: number;
  startedAt?: string | null;
  until?: string | null;
  status?: string;
};

/**
 * Lo que recibe el profe por una clase: la comisión se redondea HACIA ABAJO al peso, exactamente
 * como `RateBreakdown` en el backend, así que la diferencia queda a su favor.
 */
export function recibe(tarifaCop: number, comisionBps: number): number {
  return tarifaCop - Math.floor((tarifaCop * comisionBps) / 10000);
}

export const porcentaje = (bps: number) => `${bps / 100} %`;

/** «12 de enero de 2027», en Bogotá. */
export function fechaDeFin(iso: string): string {
  return new Intl.DateTimeFormat("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "America/Bogota",
  }).format(new Date(iso));
}

const meses = (n: number) => (n === 1 ? "tu primer mes" : `tus primeros ${n} meses`);

/**
 * La ayuda debajo de la tarifa, que se recalcula mientras el profe escribe. Cuatro estados: tarifa
 * gratis, fundador sin empezar, fundador activo y el resto (no fundador o ya terminó).
 */
export function ayudaDeTarifa(tarifaCop: number, baseBps: number, fundador: Fundador | null | undefined): string {
  if (tarifaCop === 0) return "Clase gratis: no pagas comisión.";
  const conLaBase = recibe(tarifaCop, baseBps);
  const vigente = fundador?.rateBps != null && fundador.status !== "ENDED";
  if (!vigente) {
    return `Recibes ${precioCop(conLaBase)} por clase (comisión de Orión: ${porcentaje(baseBps)}).`;
  }
  const conLaDeFundador = recibe(tarifaCop, fundador.rateBps!);
  const hasta =
    fundador.status === "ACTIVE" && fundador.until
      ? `hasta el ${fechaDeFin(fundador.until)}`
      : `durante ${meses(fundador.periodMonths ?? 3)} de clases`;
  return (
    `Recibes ${precioCop(conLaDeFundador)} por clase: ${porcentaje(fundador.rateBps!)} de comisión como profe ` +
    `fundador ${hasta}. Después recibirás ${precioCop(conLaBase)} (${porcentaje(baseBps)}).`
  );
}

/** El estado para el admin, en Usuarios. */
export function estadoDeFundador(fundador: Fundador | null | undefined): string {
  if (fundador?.rateBps == null) return "Sin beneficio de fundador";
  const tasa = porcentaje(fundador.rateBps);
  if (fundador.status === "ENDED" && fundador.until) return `Fundador · terminó el ${fechaDeFin(fundador.until)}`;
  if (fundador.status === "ACTIVE" && fundador.until) return `Fundador · ${tasa} hasta el ${fechaDeFin(fundador.until)}`;
  return `Fundador · ${tasa} · sin empezar`;
}
