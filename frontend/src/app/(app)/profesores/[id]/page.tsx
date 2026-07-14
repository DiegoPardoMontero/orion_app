"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type {
  BookingResponse,
  Modality,
  ProfessorDetail,
  SlotView,
  SlotsResponse,
} from "@/lib/api/types";
import { diaBogota, fechaCorta, horaBogota } from "@/lib/format";

export default function AgendaProfesorPage() {
  // En Next 16 los params de página son una Promise; en un client component se leen con este hook.
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const [diaElegido, setDiaElegido] = useState<string | null>(null);
  const [cupoElegido, setCupoElegido] = useState<string | null>(null);
  const [modalidad, setModalidad] = useState<Modality>("VIRTUAL");
  const [nota, setNota] = useState("");

  const profesor = useQuery({
    queryKey: ["professor", id],
    queryFn: () => apiFetch<ProfessorDetail>(`/api/v1/professors/${id}`),
  });

  // Una sola consulta: el API devuelve los próximos 7 días y agrupamos por fecha aquí.
  const cupos = useQuery({
    queryKey: ["slots", id],
    queryFn: () => apiFetch<SlotsResponse>(`/api/v1/professors/${id}/slots`),
  });

  const porDia = useMemo(() => agruparPorDia(cupos.data?.slots ?? []), [cupos.data]);
  const dias = Object.keys(porDia);
  const diaActivo = diaElegido && porDia[diaElegido] ? diaElegido : (dias[0] ?? null);
  const cuposDelDia = diaActivo ? porDia[diaActivo] : [];

  const reservar = useMutation({
    mutationFn: (startsAt: string) =>
      apiFetch<BookingResponse>("/api/v1/bookings", {
        method: "POST",
        body: {
          professorId: id,
          startsAt,
          modality: modalidad,
          locationNote: nota.trim() || undefined,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      router.push("/mis-clases?reservada=1");
    },
    onError: () => {
      // 422 (alguien lo tomó) o 409 (carrera): la agenda que ve el usuario está desfasada.
      // Invalidamos los cupos para que vuelva a pedirlos y vea la realidad.
      setCupoElegido(null);
      void queryClient.invalidateQueries({ queryKey: ["slots", id] });
    },
  });

  const errorReserva = reservar.error instanceof ApiError ? reservar.error.message : null;

  if (profesor.isPending || cupos.isPending) {
    return (
      <main className="mx-auto max-w-md p-4">
        <Cargando filas={4} />
      </main>
    );
  }

  if (profesor.isError) {
    return (
      <main className="mx-auto max-w-md p-4">
        <ErrorCarga
          mensaje="No pudimos cargar este profesor."
          onReintentar={() => void profesor.refetch()}
        />
      </main>
    );
  }

  const detalle = profesor.data;

  return (
    <main className="mx-auto max-w-md p-4">
      <div className="flex items-center gap-2.5">
        <Link href="/profesores" aria-label="Volver" className="text-ink-muted">
          ←
        </Link>
        <Avatar nombre={detalle.fullName ?? ""} fotoUrl={detalle.photoUrl} />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{detalle.fullName}</p>
          <p className="truncate text-xs text-ink-soft">{detalle.headline}</p>
        </div>
      </div>

      {detalle.bio && <p className="mt-3.5 text-xs leading-relaxed text-ink-muted">{detalle.bio}</p>}

      {cupos.isError ? (
        <div className="mt-4">
          <ErrorCarga
            mensaje="No pudimos cargar la agenda."
            onReintentar={() => void cupos.refetch()}
          />
        </div>
      ) : dias.length === 0 ? (
        <div className="mt-4">
          <Vacio
            titulo="Sin cupos esta semana"
            texto="Vuelve en unos días o escribe a otro profesor: seguro encontramos un horario que te sirva."
          />
        </div>
      ) : (
        <>
          <p className="mt-4 text-xs font-semibold text-ink-soft">Elige un día</p>
          <div className="mt-1.5 flex flex-wrap gap-1.5">
            {dias.map((dia) => (
              <button
                key={dia}
                type="button"
                onClick={() => {
                  setDiaElegido(dia);
                  setCupoElegido(null);
                }}
                className={chipClass(dia === diaActivo)}
              >
                {fechaCorta(porDia[dia][0].startsAt!)}
              </button>
            ))}
          </div>

          <p className="mt-3 text-xs font-semibold text-ink-soft">Cupos disponibles</p>
          <div className="mt-1.5 grid grid-cols-3 gap-1.5">
            {cuposDelDia.map((cupo) => (
              <button
                key={cupo.startsAt}
                type="button"
                onClick={() => setCupoElegido(cupo.startsAt!)}
                className={`${chipClass(cupo.startsAt === cupoElegido)} justify-center`}
              >
                {horaBogota(cupo.startsAt!)}
              </button>
            ))}
          </div>

          <p className="mt-3.5 text-xs font-semibold text-ink-soft">Modalidad</p>
          <div className="mt-1.5 flex overflow-hidden rounded-orion border border-line">
            {(["VIRTUAL", "IN_PERSON"] as const).map((valor) => (
              <button
                key={valor}
                type="button"
                onClick={() => setModalidad(valor)}
                className={`flex-1 py-1.5 text-xs ${
                  modalidad === valor
                    ? "bg-accent-soft font-semibold text-accent-ink"
                    : "text-ink-soft"
                }`}
              >
                {valor === "VIRTUAL" ? "Virtual" : "Presencial"}
              </button>
            ))}
          </div>

          <input
            type="text"
            value={nota}
            onChange={(event) => setNota(event.target.value)}
            maxLength={300}
            placeholder={
              modalidad === "VIRTUAL"
                ? "Link de la videollamada (opcional)"
                : "Lugar del encuentro (opcional)"
            }
            className="mt-3 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm outline-none focus:border-accent"
          />

          {errorReserva && (
            <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">
              {errorReserva}
            </p>
          )}

          <button
            type="button"
            disabled={!cupoElegido || reservar.isPending}
            onClick={() => cupoElegido && reservar.mutate(cupoElegido)}
            className="mt-3.5 w-full rounded-orion bg-accent py-2.5 text-sm font-semibold text-white disabled:opacity-50"
          >
            {reservar.isPending ? "Reservando…" : "Confirmar reserva"}
          </button>
          <p className="mt-2 text-center text-[11px] text-ink-muted">
            Recibirás confirmación por correo con invitación al calendario
          </p>
        </>
      )}
    </main>
  );
}

function chipClass(activo: boolean): string {
  return `inline-flex items-center rounded-orion border px-2.5 py-1.5 text-xs ${
    activo
      ? "border-transparent bg-accent-soft font-semibold text-accent-ink"
      : "border-line text-ink-soft"
  }`;
}

/** Los cupos vienen planos; la agenda los necesita por día (en fecha de Bogotá, no del navegador). */
function agruparPorDia(slots: SlotView[]): Record<string, SlotView[]> {
  const grupos: Record<string, SlotView[]> = {};
  for (const slot of slots) {
    if (!slot.startsAt) continue;
    const dia = diaBogota(slot.startsAt);
    (grupos[dia] ??= []).push(slot);
  }
  return grupos;
}
