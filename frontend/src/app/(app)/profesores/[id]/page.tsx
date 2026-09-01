"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  Calendar,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock,
  Mail,
  MapPin,
  Video,
} from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Fragment, useMemo, useState, type ReactNode } from "react";
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
import { useMediaQuery } from "@/lib/useMediaQuery";

export default function AgendaProfesorPage() {
  // En Next 16 los params de página son una Promise; en un client component se leen con este hook.
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const esDesktop = useMediaQuery("(min-width: 1024px)");

  const [diaElegido, setDiaElegido] = useState<string | null>(null);
  const [cupoElegido, setCupoElegido] = useState<string | null>(null);
  const [modalidad, setModalidad] = useState<Modality>("VIRTUAL");
  const [nota, setNota] = useState("");

  const profesor = useQuery({
    queryKey: ["professor", id],
    queryFn: () => apiFetch<ProfessorDetail>(`/api/v1/professors/${id}`),
  });

  // Los próximos 7 días alimentan los chips de móvil (y el estado de carga/vacío inicial).
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
          locationNote: modalidad === "IN_PERSON" ? nota.trim() || undefined : undefined,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      router.push("/mis-clases?reservada=1");
    },
    onError: () => {
      // 422/409: la agenda que ve el usuario está desfasada. Invalidamos todos los cupos del
      // profesor (chips y semana) para que vuelva a pedirlos y vea la realidad.
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

  const resumen =
    cupoElegido
      ? `${fechaCorta(cupoElegido)} · ${horaBogota(cupoElegido)} · ${modalidad === "VIRTUAL" ? "Virtual" : "Presencial"}`
      : null;

  // Controles compartidos entre móvil y desktop: modalidad, nota, confirmación.
  const controles: ReactNode = (
    <>
      <Bloque tono="menta" titulo="Modalidad" icono={<Video size={16} strokeWidth={1.75} />}>
        <Segmento<Modality>
          valor={modalidad}
          onCambio={setModalidad}
          opciones={[
            { valor: "VIRTUAL", etiqueta: (<><Video size={15} strokeWidth={1.75} /> Virtual</>) },
            { valor: "IN_PERSON", etiqueta: (<><MapPin size={15} strokeWidth={1.75} /> Presencial</>) },
          ]}
        />
      </Bloque>

      {modalidad === "IN_PERSON" ? (
        <Campo
          type="text"
          value={nota}
          onChange={(event) => setNota(event.target.value)}
          maxLength={300}
          icono={<MapPin size={16} strokeWidth={1.75} />}
          placeholder="¿Dónde se encontrarán? (opcional)"
        />
      ) : (
        <p className="flex items-center gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[12.5px] text-[#5e4a8a]">
          <Video size={16} strokeWidth={1.75} className="shrink-0" />
          Al confirmar creamos una sala de videollamada; el enlace llega a tu correo y a Mis clases.
        </p>
      )}

      {errorReserva && <AvisoError mensaje={errorReserva} />}
      {resumen && <p className="text-center text-[13px] font-semibold text-text">{resumen}</p>}

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
    </>
  );

  return (
    <main className="mx-auto w-full max-w-md px-7 py-6 lg:max-w-[1180px] lg:px-12 lg:py-8">
      <Link
        href="/profesores"
        aria-label="Volver a profesores"
        className="grid h-11 w-11 place-items-center rounded-full bg-surface-sunken text-text transition-colors hover:bg-border focus-visible:shadow-focus"
      >
        <ArrowLeft size={18} strokeWidth={1.75} />
      </Link>

      <div className="lg:grid lg:grid-cols-[340px_minmax(0,1fr)] lg:gap-10">
        {/* Columna de perfil (compacta) */}
        <section className="mt-5 lg:mt-6">
          <div className="flex items-center gap-4">
            <Avatar nombre={detalle.fullName ?? ""} fotoUrl={detalle.photoUrl} size="lg" className="lg:hidden" />
            <Avatar nombre={detalle.fullName ?? ""} fotoUrl={detalle.photoUrl} size="xl" className="hidden lg:block" />
            <div className="min-w-0">
              <h1 className="truncate font-display text-[20px] font-bold lg:text-[30px]">
                {detalle.fullName}
              </h1>
              <p className="truncate text-[13px] text-text-secondary lg:text-[15px]">
                {detalle.headline}
              </p>
            </div>
          </div>

          <div className="mt-3 hidden flex-wrap gap-2 lg:flex">
            <Badge tono="lavanda">
              <Video size={12} strokeWidth={2.4} /> Virtual
            </Badge>
            <Badge tono="melocoton">
              <MapPin size={12} strokeWidth={2.4} /> Presencial
            </Badge>
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
            <ErrorCarga mensaje="No pudimos cargar la agenda." onReintentar={() => void cupos.refetch()} />
          ) : esDesktop ? (
            <div className="space-y-5">
              <AgendaSemanal profesorId={id} cupoElegido={cupoElegido} onElegir={setCupoElegido} />
              {controles}
            </div>
          ) : dias.length === 0 ? (
            <Vacio
              mascota
              titulo="Sin cupos esta semana"
              texto="Vuelve en unos días: seguro encontramos un horario que te sirva."
            />
          ) : (
            <div className="space-y-3">
              <Bloque tono="melocoton" titulo="Elige un día" icono={<Calendar size={16} strokeWidth={1.75} />}>
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

              {controles}
            </div>
          )}
        </section>
      </div>
    </main>
  );
}

/** Vista semanal navegable (desktop): 7 columnas de días × filas de horas. */
function AgendaSemanal({
  profesorId,
  cupoElegido,
  onElegir,
}: {
  profesorId: string;
  cupoElegido: string | null;
  onElegir: (startsAt: string) => void;
}) {
  const [offset, setOffset] = useState(0);
  const hoy = diaBogota(new Date().toISOString());
  const from = addDays(hoy, offset * 7);
  const dias7 = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(from, i)), [from]);
  const to = dias7[6];

  const cupos = useQuery({
    queryKey: ["slots", profesorId, "semana", from],
    queryFn: () =>
      apiFetch<SlotsResponse>(`/api/v1/professors/${profesorId}/slots?from=${from}&to=${to}`),
  });

  const { porCelda, horas } = useMemo(() => {
    const celda: Record<string, Record<string, SlotView>> = {};
    const set = new Set<string>();
    for (const slot of cupos.data?.slots ?? []) {
      if (!slot.startsAt) continue;
      const dia = diaBogota(slot.startsAt);
      const hora = horaBogota(slot.startsAt);
      (celda[dia] ??= {})[hora] = slot;
      set.add(hora);
    }
    return { porCelda: celda, horas: [...set].sort() };
  }, [cupos.data]);

  return (
    <div>
      <div className="flex items-center justify-between">
        <button
          type="button"
          aria-label="Semana anterior"
          disabled={offset === 0}
          onClick={() => setOffset((o) => Math.max(0, o - 1))}
          className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
        >
          <ChevronLeft size={18} strokeWidth={1.75} />
        </button>
        <p className="font-display text-[15px] font-bold">
          {etiquetaSemana(from)} – {etiquetaSemana(to)}
        </p>
        <button
          type="button"
          aria-label="Semana siguiente"
          disabled={offset >= 5}
          onClick={() => setOffset((o) => Math.min(5, o + 1))}
          className="grid h-10 w-10 place-items-center rounded-full border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus disabled:opacity-40"
        >
          <ChevronRight size={18} strokeWidth={1.75} />
        </button>
      </div>

      {cupos.isPending ? (
        <div className="mt-4">
          <Cargando filas={3} />
        </div>
      ) : cupos.isError ? (
        <div className="mt-4">
          <ErrorCarga mensaje="No pudimos cargar la semana." onReintentar={() => void cupos.refetch()} />
        </div>
      ) : horas.length === 0 ? (
        <p className="mt-6 rounded-base bg-surface-sunken px-4 py-8 text-center text-[14px] text-text-secondary">
          Sin cupos esta semana. Prueba con la siguiente →
        </p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <div
            className="grid min-w-[440px] gap-1"
            style={{ gridTemplateColumns: "44px repeat(7, minmax(52px, 1fr))" }}
          >
            <div aria-hidden="true" />
            {dias7.map((dia) => (
              <div key={dia} className="pb-1 text-center">
                <p className="text-[11px] font-bold uppercase tracking-[0.04em] text-text-muted">
                  {diaSemana(dia)}
                </p>
                <p className="text-[15px] font-bold text-text">{diaNumero(dia)}</p>
              </div>
            ))}

            {horas.map((hora) => (
              <Fragment key={hora}>
                <div className="flex items-center justify-end pr-1 text-[12px] font-semibold text-text-muted">
                  {hora}
                </div>
                {dias7.map((dia) => {
                  const slot = porCelda[dia]?.[hora];
                  if (!slot?.startsAt) {
                    return <div key={dia} className="min-h-11 rounded-base bg-surface-sunken/40" />;
                  }
                  const activo = slot.startsAt === cupoElegido;
                  return (
                    <button
                      key={dia}
                      type="button"
                      onClick={() => onElegir(slot.startsAt!)}
                      className={`min-h-11 rounded-base text-[12px] font-semibold transition-colors focus-visible:shadow-focus ${
                        activo
                          ? "bg-primary text-on-primary hora-elegida"
                          : "bg-accent-lavender-soft text-[#5e4a8a] hover:bg-[#e2d7f4]"
                      }`}
                    >
                      {hora}
                    </button>
                  );
                })}
              </Fragment>
            ))}
          </div>
        </div>
      )}
    </div>
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

/** Aritmética de calendario pura (UTC), sin deriva de zona: "YYYY-MM-DD" + días → "YYYY-MM-DD". */
function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

// Formateo del encabezado a partir de la fecha (mediodía Bogotá evita que la zona la corra un día).
const bogotaNoon = (dateStr: string) => new Date(`${dateStr}T12:00:00-05:00`);
const diaSemana = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", weekday: "short" }).format(bogotaNoon(dateStr));
const diaNumero = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", day: "numeric" }).format(bogotaNoon(dateStr));
const etiquetaSemana = (dateStr: string) =>
  new Intl.DateTimeFormat("es-CO", { timeZone: "America/Bogota", day: "numeric", month: "short" }).format(
    bogotaNoon(dateStr),
  );
