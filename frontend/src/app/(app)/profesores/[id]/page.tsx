"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Calendar, CalendarX, Check, Clock, Mail, MapPin, Pencil, Video } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { HeroNoche } from "@/components/marca";
import { Bloque, BotonPrincipal, Campo, Chip, Segmento } from "@/components/ui";
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
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
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
      <main className="px-5 py-5">
        <Cargando filas={4} />
      </main>
    );
  }

  if (profesor.isError) {
    return (
      <main className="px-5 py-5">
        <ErrorCarga
          mensaje="No pudimos cargar este profesor."
          onReintentar={() => void profesor.refetch()}
        />
      </main>
    );
  }

  const detalle = profesor.data;

  return (
    <main>
      <HeroNoche className="rounded-b-sheet px-5 pb-6 pt-4">
        <Link
          href="/profesores"
          aria-label="Volver"
          className="grid h-9 w-9 place-items-center rounded-full bg-white/15 text-white"
        >
          <ArrowLeft size={18} strokeWidth={2.2} />
        </Link>

        <p className="mt-4 text-[11.5px] font-bold tracking-[0.12em] text-[#c9bff0]">
          RESERVAR CLASE
        </p>

        <div className="mt-2 flex items-center gap-3">
          <div className="relative">
            <Avatar
              nombre={detalle.fullName ?? ""}
              fotoUrl={detalle.photoUrl}
              size="lg"
              className="border-[2.5px] border-accent"
            />
            <span className="absolute bottom-0 right-0 h-3.5 w-3.5 rounded-full border-2 border-[#241e4e] bg-[#4ade80]" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-[18px] font-bold text-white">{detalle.fullName}</p>
            <p className="truncate text-[12.5px] text-[#c9bff0]">{detalle.headline}</p>
          </div>
        </div>

        {detalle.bio && (
          <p className="mt-3 text-[12.5px] leading-relaxed text-[#c9bff0]">{detalle.bio}</p>
        )}
      </HeroNoche>

      <div className="space-y-3 px-5 py-5">
        {cupos.isError ? (
          <ErrorCarga
            mensaje="No pudimos cargar la agenda."
            onReintentar={() => void cupos.refetch()}
          />
        ) : dias.length === 0 ? (
          <Vacio
            icono={<CalendarX size={24} strokeWidth={2.2} />}
            titulo="Sin cupos esta semana"
            texto="Vuelve en unos días: seguro encontramos un horario que te sirva."
          />
        ) : (
          <>
            <Bloque
              tono="melocoton"
              titulo="Elige un día"
              icono={<Calendar size={16} strokeWidth={2.2} />}
            >
              <div className="flex flex-wrap gap-2">
                {dias.map((dia) => (
                  <Chip
                    key={dia}
                    activo={dia === diaActivo}
                    tono="coral"
                    onClick={() => {
                      setDiaElegido(dia);
                      setCupoElegido(null);
                    }}
                  >
                    {fechaCorta(porDia[dia][0].startsAt!)}
                  </Chip>
                ))}
              </div>
            </Bloque>

            <Bloque
              tono="lavanda"
              titulo="Cupos disponibles"
              icono={<Clock size={16} strokeWidth={2.2} />}
              extra={
                <span className="text-[11.5px] font-bold text-info">
                  {cuposDelDia.length} {cuposDelDia.length === 1 ? "libre" : "libres"}
                </span>
              }
            >
              <div className="grid grid-cols-3 gap-2">
                {cuposDelDia.map((cupo) => (
                  <Chip
                    key={cupo.startsAt}
                    activo={cupo.startsAt === cupoElegido}
                    tono="tinta"
                    onClick={() => setCupoElegido(cupo.startsAt!)}
                  >
                    {horaBogota(cupo.startsAt!)}
                  </Chip>
                ))}
              </div>
            </Bloque>

            <Bloque
              tono="menta"
              titulo="Modalidad"
              icono={<Video size={16} strokeWidth={2.2} />}
            >
              <Segmento<Modality>
                valor={modalidad}
                onCambio={setModalidad}
                opciones={[
                  {
                    valor: "VIRTUAL",
                    etiqueta: (
                      <>
                        <Video size={15} strokeWidth={2.2} /> Virtual
                      </>
                    ),
                  },
                  {
                    valor: "IN_PERSON",
                    etiqueta: (
                      <>
                        <MapPin size={15} strokeWidth={2.2} /> Presencial
                      </>
                    ),
                  },
                ]}
              />
            </Bloque>

            <Campo
              type="text"
              value={nota}
              onChange={(event) => setNota(event.target.value)}
              maxLength={300}
              icono={<Pencil size={16} strokeWidth={2.2} />}
              placeholder={
                modalidad === "VIRTUAL"
                  ? "Link de la videollamada (opcional)"
                  : "Lugar del encuentro (opcional)"
              }
            />

            {errorReserva && <AvisoError mensaje={errorReserva} />}

            <BotonPrincipal
              disabled={!cupoElegido || reservar.isPending}
              onClick={() => cupoElegido && reservar.mutate(cupoElegido)}
            >
              {reservar.isPending ? "Reservando…" : "Confirmar reserva"}
              {!reservar.isPending && <Check size={18} strokeWidth={2.2} />}
            </BotonPrincipal>

            <p className="flex items-center justify-center gap-1.5 text-[11.5px] text-text-muted">
              <Mail size={13} strokeWidth={2.2} />
              Recibirás confirmación por correo con invitación al calendario
            </p>
          </>
        )}
      </div>
    </main>
  );
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
