"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Calendar, Check, Clock, Mail, MapPin, Pencil, Video } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Bloque, BotonPrincipal, Campo, Chip, Segmento, Spinner } from "@/components/ui";
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
      <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
        <Cargando filas={4} />
      </main>
    );
  }

  if (profesor.isError) {
    return (
      <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
        <ErrorCarga
          mensaje="No pudimos cargar este profesor."
          onReintentar={() => void profesor.refetch()}
        />
      </main>
    );
  }

  const detalle = profesor.data;

  // Resumen humano de lo elegido, para el pie de confirmación.
  const resumen =
    diaActivo && cupoElegido
      ? `${fechaCorta(cupoElegido)} · ${horaBogota(cupoElegido)} · ${modalidad === "VIRTUAL" ? "Virtual" : "Presencial"}`
      : null;

  return (
    <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
      <Link
        href="/profesores"
        aria-label="Volver a profesores"
        className="grid h-11 w-11 place-items-center rounded-full bg-surface-sunken text-text transition-colors hover:bg-border focus-visible:shadow-focus"
      >
        <ArrowLeft size={18} strokeWidth={1.75} />
      </Link>

      <div className="lg:grid lg:grid-cols-[420px_1fr] lg:gap-12">
        {/* Columna de perfil */}
        <section className="mt-5 lg:mt-6">
          <div className="flex items-center gap-4">
            <Avatar
              nombre={detalle.fullName ?? ""}
              fotoUrl={detalle.photoUrl}
              size="lg"
              className="lg:hidden"
            />
            <Avatar
              nombre={detalle.fullName ?? ""}
              fotoUrl={detalle.photoUrl}
              size="xl"
              className="hidden lg:block"
            />
            <div className="min-w-0">
              <h1 className="truncate font-display text-[20px] font-bold lg:text-[32px]">
                {detalle.fullName}
              </h1>
              <p className="truncate text-[13px] text-text-secondary lg:text-[15px]">
                {detalle.headline}
              </p>
            </div>
          </div>

          <div className="mt-3 hidden flex-wrap gap-2 lg:flex">
            <Badge tono="neutral">Virtual y presencial</Badge>
          </div>

          {detalle.bio && (
            <div className="mt-4 rounded-base bg-surface-raised p-4 shadow-sm lg:mt-5 lg:bg-transparent lg:p-0 lg:shadow-none">
              <p className="text-[13.5px] leading-relaxed text-text-secondary lg:text-[15px] lg:leading-[1.7]">
                {detalle.bio}
              </p>
            </div>
          )}
        </section>

        {/* Columna de agenda */}
        <section className="mt-5 lg:mt-6 lg:rounded-card lg:bg-surface-raised lg:p-9 lg:shadow-lg">
          {cupos.isError ? (
            <ErrorCarga
              mensaje="No pudimos cargar la agenda."
              onReintentar={() => void cupos.refetch()}
            />
          ) : dias.length === 0 ? (
            <Vacio
              mascota
              titulo="Sin cupos esta semana"
              texto="Vuelve en unos días: seguro encontramos un horario que te sirva."
            />
          ) : (
            <div className="space-y-3">
              <Bloque
                tono="melocoton"
                titulo="Elige un día"
                icono={<Calendar size={16} strokeWidth={1.75} />}
              >
                <div className="flex flex-wrap gap-2">
                  {dias.map((dia) => (
                    <Chip
                      key={dia}
                      familia="fecha"
                      activo={dia === diaActivo}
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
                icono={<Clock size={16} strokeWidth={1.75} />}
                extra={
                  <span className="text-[12px] font-bold normal-case text-[#5e4a8a]">
                    {cuposDelDia.length} {cuposDelDia.length === 1 ? "libre" : "libres"}
                  </span>
                }
              >
                {cuposDelDia.length === 0 ? (
                  <p className="text-[13px] text-[#5e4a8a]">Elige un día para ver sus horarios.</p>
                ) : (
                  <div className="grid grid-cols-3 gap-2.5">
                    {cuposDelDia.map((cupo) => (
                      <Chip
                        key={cupo.startsAt}
                        familia="hora"
                        activo={cupo.startsAt === cupoElegido}
                        onClick={() => setCupoElegido(cupo.startsAt!)}
                      >
                        {horaBogota(cupo.startsAt!)}
                      </Chip>
                    ))}
                  </div>
                )}
              </Bloque>

              <Bloque
                tono="menta"
                titulo="Modalidad"
                icono={<Video size={16} strokeWidth={1.75} />}
              >
                <Segmento<Modality>
                  valor={modalidad}
                  onCambio={setModalidad}
                  opciones={[
                    {
                      valor: "VIRTUAL",
                      etiqueta: (
                        <>
                          <Video size={15} strokeWidth={1.75} /> Virtual
                        </>
                      ),
                    },
                    {
                      valor: "IN_PERSON",
                      etiqueta: (
                        <>
                          <MapPin size={15} strokeWidth={1.75} /> Presencial
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
                icono={<Pencil size={16} strokeWidth={1.75} />}
                placeholder={
                  modalidad === "VIRTUAL"
                    ? "Link de la videollamada (opcional)"
                    : "Lugar del encuentro (opcional)"
                }
              />

              {errorReserva && <AvisoError mensaje={errorReserva} />}

              {resumen && (
                <p className="text-center text-[13px] font-semibold text-text">{resumen}</p>
              )}

              <BotonPrincipal
                disabled={!cupoElegido || reservar.isPending}
                onClick={() => cupoElegido && reservar.mutate(cupoElegido)}
              >
                {reservar.isPending ? (
                  <>
                    <Spinner />
                    Reservando…
                  </>
                ) : (
                  <>
                    Confirmar reserva
                    <Check size={18} strokeWidth={1.75} />
                  </>
                )}
              </BotonPrincipal>

              <p className="flex items-center justify-center gap-1.5 text-[11.5px] text-text-muted">
                <Mail size={13} strokeWidth={1.75} />
                Recibirás confirmación por correo con invitación al calendario
              </p>
            </div>
          )}
        </section>
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
