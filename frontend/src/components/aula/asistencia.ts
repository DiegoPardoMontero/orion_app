import { useEffect, useState } from "react";

/**
 * Desde cuándo se puede registrar cada respuesta de asistencia, en milisegundos.
 *
 * <p>Es la regla de `AttendanceService` repetida solo para pintar: «asistió» desde que la clase
 * termina —darla por dictada antes liberaría el dinero de algo que aún no ocurrió— y «no se
 * presentó» desde el inicio más la espera de `no_show_report_minutes`. Ofrecer lo que el servidor va
 * a rechazar con un 422 era dejar al profesor pulsando un botón que no sirve; el servidor sigue
 * siendo quien decide si el reloj de aquí se equivoca.
 */
export function momentosDeAsistencia(
  inicioIso: string,
  finIso: string,
  esperaMinutos: number,
): { asistio: number; noAsistio: number } {
  return {
    asistio: Date.parse(finIso),
    noAsistio: Date.parse(inicioIso) + esperaMinutos * 60_000,
  };
}

/** Un día: lo más lejos que se programa un temporizador (más allá de 2³¹ ms se dispara al instante). */
const ESPERA_MAXIMA = 24 * 60 * 60_000;

/**
 * La hora, que se actualiza sola justo al cruzar cada uno de los momentos dados: así un botón se
 * habilita a las 5:55 sin recargar y sin un reloj que despierte cada segundo en cada tarjeta.
 */
export function useAhoraHasta(momentos: number[]): number {
  const [ahora, setAhora] = useState(() => Date.now());
  const siguiente = momentos.filter((m) => m > ahora).sort((a, b) => a - b)[0];

  useEffect(() => {
    if (siguiente === undefined) return;
    const espera = Math.min(Math.max(0, siguiente - Date.now()) + 250, ESPERA_MAXIMA);
    const t = setTimeout(() => setAhora(Date.now()), espera);
    return () => clearTimeout(t);
    // `ahora` también: si el momento está a más de un día, el temporizador despierta, se reprograma
    // y sigue esperando.
  }, [siguiente, ahora]);

  return ahora;
}
