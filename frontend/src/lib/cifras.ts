import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";
import type { PublicFigures } from "@/lib/api/types";

/**
 * Los números de negocio, leídos del servidor en vez de escritos en la pantalla.
 *
 * <p>Regla de Pardo: lo que se cambia desde Ajustes cambia en todas partes. Un «12 horas» tecleado
 * en un componente es una segunda verdad que nadie recuerda actualizar, y el día que se separan, la
 * que ve el usuario es la falsa. Ya nos pasó: dos pantallas anunciaban clases de 60 minutos cuando
 * duran 55 desde hace dos bloques.
 *
 * <p>Son públicas —se anuncian en la portada y en los Términos— y cambian poco, así que se cachean
 * una hora y se comparten entre todas las pantallas.
 */
export const cifrasQueryKey = ["catalog", "figures"] as const;

/** Lo que se muestra mientras llega la respuesta. Coincide con los valores sembrados. */
const MIENTRAS_CARGA: PublicFigures = {
  commissionPercent: 15,
  classMinutes: 55,
  paymentHoldMinutes: 20,
  studentCancelHours: 12,
  professorCancelHours: 12,
  bookingMinLeadHours: 6,
  noShowReportMinutes: 15,
  disputeReportWindowHours: 24,
  autoCompleteHours: 24,
  applicationReviewBusinessDays: 3,
};

export function useCifras(): PublicFigures {
  const { data } = useQuery({
    queryKey: cifrasQueryKey,
    queryFn: () => apiFetch<PublicFigures>("/api/v1/catalog/figures"),
    staleTime: 60 * 60_000,
  });
  return data ?? MIENTRAS_CARGA;
}

/** «12 horas», «1 hora». El plural resuelto, que concatenar no vale en español. */
export const horas = (n: number) => `${n} ${n === 1 ? "hora" : "horas"}`;
export const minutos = (n: number) => `${n} ${n === 1 ? "minuto" : "minutos"}`;
export const diasHabiles = (n: number) => `${n} ${n === 1 ? "día hábil" : "días hábiles"}`;
