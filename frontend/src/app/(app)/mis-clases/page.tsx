"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, Calendar, Check, Clock, MapPin, Star, Video, X } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import { Avatar } from "@/components/Avatar";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { SelectorEstrellas } from "@/components/Rating";
import { Rigel } from "@/components/Rigel";
import { Badge, Bloque, Boton, Chip, Segmento, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { MyBookingResponse, SlotsResponse, SlotView } from "@/lib/api/types";
import { useMe } from "@/lib/auth/session";
import { esperaPago, etiquetaEstado } from "@/lib/estados-clase";
import { diaBogota, fechaCorta, fechaYRango, horaBogota, rangoHoras } from "@/lib/format";

type Scope = "upcoming" | "past";

export default function MisClasesPage() {
  const [scope, setScope] = useState<Scope>("upcoming");
  const { data: me } = useMe();

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ["me", "bookings", scope],
    queryFn: () => apiFetch<MyBookingResponse[]>(`/api/v1/me/bookings?scope=${scope}`),
  });

  const esProfesor = me?.role === "PROFESSOR";

  return (
    <main className="mx-auto w-full max-w-md px-5 py-6 lg:max-w-3xl lg:px-12 lg:py-8">
      <Suspense fallback={null}>
        <BannerReserva />
      </Suspense>

      <h1 className="font-display text-h1 font-bold">Mis clases</h1>

      <div className="mt-4 lg:max-w-sm">
        <Segmento<Scope>
          valor={scope}
          onCambio={setScope}
          opciones={[
            { valor: "upcoming", etiqueta: "Próximas" },
            { valor: "past", etiqueta: "Pasadas" },
          ]}
        />
      </div>

      <div className="mt-4">
        {isPending && <Cargando />}

        {isError && (
          <ErrorCarga mensaje="No pudimos cargar tus clases." onReintentar={() => void refetch()} />
        )}

        {data?.length === 0 &&
          (scope === "upcoming" ? (
            <Vacio
              mascota
              titulo="Aún no tienes clases"
              texto={
                esProfesor
                  ? "En cuanto un estudiante reserve contigo, la verás aquí."
                  : "Explora los profesores y reserva la primera. Cada proceso es diferente; lo importante es empezar."
              }
              accion={
                esProfesor ? undefined : (
                  <Link
                    href="/profesores"
                    className="inline-flex min-h-11 items-center rounded-pill bg-primary px-6 text-[15px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
                  >
                    Ver profesores
                  </Link>
                )
              }
            />
          ) : (
            <Vacio
              mascota
              titulo="Nada por aquí todavía"
              texto="Tus clases pasadas aparecerán en esta pestaña."
            />
          ))}

        {/*
          Una sola columna con las clases agrupadas por día y colgando de una línea de tiempo.
          Antes era una retícula de dos columnas, y esa era la razón de que no se entendiera el
          orden: en dos columnas la lectura zigzaguea y la segunda clase queda a la derecha de la
          primera, no debajo. Aquí el orden se lee bajando, que es como se lee una agenda.
        */}
        <Agenda clases={data ?? []} scope={scope} esProfesor={esProfesor} />
      </div>
    </main>
  );
}

/**
 * Las clases de una pestaña, agrupadas por día y colgadas de una línea vertical.
 *
 * El backend ya las devuelve ordenadas —las próximas de la más cercana a la más lejana, las pasadas
 * de la más reciente a la más antigua—, así que aquí no se reordena nada: se hace visible el orden
 * que ya traen. El encabezado de cada día dice de qué día se trata en palabras ("Hoy", "Mañana",
 * "Hace 3 días") porque una fecha suelta obliga a hacer la cuenta mental.
 */
function Agenda({
  clases,
  scope,
  esProfesor,
}: {
  clases: MyBookingResponse[];
  scope: Scope;
  esProfesor: boolean;
}) {
  const dias = useMemo(() => {
    const grupos: { dia: string; clases: MyBookingResponse[] }[] = [];
    for (const clase of clases) {
      if (!clase.startsAt) continue;
      const dia = diaBogota(clase.startsAt);
      const ultimo = grupos.at(-1);
      if (ultimo?.dia === dia) ultimo.clases.push(clase);
      else grupos.push({ dia, clases: [clase] });
    }
    return grupos;
  }, [clases]);

  if (dias.length === 0) return null;

  return (
    <div className="flex flex-col gap-6">
      {dias.map((grupo) => (
        <section key={grupo.dia}>
          <h2 className="mb-2.5 flex items-baseline gap-2 text-[13px] font-bold text-text">
            {etiquetaDia(grupo.dia)}
            <span className="text-[12px] font-semibold text-text-muted">
              {grupo.clases.length === 1 ? "1 clase" : `${grupo.clases.length} clases`}
            </span>
          </h2>

          {/* La línea de tiempo: un filete vertical del que cuelga cada clase por su hora. */}
          <ul className="flex flex-col gap-2.5 border-l-2 border-border pl-4">
            {grupo.clases.map((clase, i) => (
              <li key={clase.id} className="relative">
                <span
                  aria-hidden="true"
                  className={`absolute -left-[21px] top-5 h-2.5 w-2.5 rounded-full ring-4 ring-surface ${
                    scope === "upcoming" && grupo === dias[0] && i === 0
                      ? "bg-primary"
                      : "bg-border-strong"
                  }`}
                />
                <TarjetaClase
                  clase={clase}
                  scope={scope}
                  esProfesor={esProfesor}
                  esLaSiguiente={scope === "upcoming" && grupo === dias[0] && i === 0}
                />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** "Hoy", "Mañana", "Ayer", "Hace 3 días" o la fecha larga. Una fecha suelta obliga a calcular. */
function etiquetaDia(dia: string): string {
  const hoy = diaBogota(new Date().toISOString());
  const dias = Math.round(
    (Date.parse(`${dia}T12:00:00-05:00`) - Date.parse(`${hoy}T12:00:00-05:00`)) / 86_400_000,
  );
  const fecha = fechaLargaDia(dia);

  if (dias === 0) return `Hoy · ${fecha}`;
  if (dias === 1) return `Mañana · ${fecha}`;
  if (dias === -1) return `Ayer · ${fecha}`;
  if (dias < -1 && dias >= -7) return `Hace ${Math.abs(dias)} días · ${fecha}`;
  return fecha.charAt(0).toUpperCase() + fecha.slice(1);
}

/** El día "2026-07-15" escrito en palabras, sin arrastrar la zona del navegador. */
function fechaLargaDia(dia: string): string {
  return new Intl.DateTimeFormat("es-CO", {
    timeZone: "America/Bogota",
    weekday: "long",
    day: "numeric",
    month: "long",
  }).format(new Date(`${dia}T12:00:00-05:00`));
}

function TarjetaClase({
  clase,
  scope,
  esProfesor,
  esLaSiguiente = false,
}: {
  clase: MyBookingResponse;
  scope: Scope;
  esProfesor: boolean;
  esLaSiguiente?: boolean;
}) {
  const [cancelando, setCancelando] = useState(false);
  const [reprogramando, setReprogramando] = useState(false);
  const [reportando, setReportando] = useState(false);
  const [registrando, setRegistrando] = useState(false);
  const [calificando, setCalificando] = useState(false);
  // Sin flag de "ya reseñada" en el DTO: se recuerda localmente al calificar (o al chocar con el 409
  // de "ya reseñaste"), para ocultar el botón y agradecer sin recargar la lista.
  const [resenaHecha, setResenaHecha] = useState(false);

  const contraparte = clase.counterpart;
  const nombreContraparte = contraparte?.fullName ?? "";
  const virtual = clase.modality === "VIRTUAL";

  // Una clase futura y confirmada que NO se puede cancelar solo puede ser por la regla de 24 h.
  const dentroDeLas24 = scope === "upcoming" && clase.status === "CONFIRMED" && !clase.canCancel;

  // El profesor registra asistencia de lo que ya ocurrió y sigue confirmado.
  const puedeRegistrar = esProfesor && scope === "past" && clase.status === "CONFIRMED";

  // El estudiante califica una clase pasada que se dio (confirmada o completada). El backend arbitra
  // el plazo/estado real (422) y el duplicado (409); aquí basta con ofrecer el botón en ese rango.
  // Reportar un problema: del estudiante, sobre una clase pasada que todavía no se cerró. El
  // backend arbitra la ventana real (desde 15 min después de empezar y hasta 24 h después de
  // terminar); aquí basta con ofrecer el botón en ese rango.
  const puedeReportar = !esProfesor && scope === "past" && clase.status === "CONFIRMED";

  const puedeCalificar =
    !esProfesor &&
    scope === "past" &&
    (clase.status === "CONFIRMED" || clase.status === "COMPLETED");

  return (
    <>
      <Tarjeta>
        <div className="flex items-center justify-between gap-2">
          <span className="flex items-center gap-2 text-[13.5px] font-bold">
            <Clock size={15} strokeWidth={1.9} className="text-primary" />
            {rangoHoras(clase.startsAt!, clase.endsAt!)}
            {esLaSiguiente && (
              <span className="rounded-pill bg-primary-soft px-2 py-0.5 text-[11px] font-bold uppercase tracking-[0.06em] text-primary-strong">
                La siguiente
              </span>
            )}
          </span>
          <Badge tono={virtual ? "menta" : "melocoton"}>
            {virtual ? <Video size={12} strokeWidth={2.4} /> : <MapPin size={12} strokeWidth={2.4} />}
            {virtual ? "Virtual" : "Presencial"}
          </Badge>
        </div>

        <div className="mt-3 flex items-center gap-2.5">
          <Avatar nombre={nombreContraparte} fotoUrl={contraparte?.photoUrl} size="sm" />
          <div className="min-w-0">
            <span className="block text-[13.5px] font-semibold">
              {esProfesor ? nombreContraparte : `Prof. ${nombreContraparte}`}
            </span>
            {contraparte?.headline && (
              <span className="block truncate text-[12px] text-text-muted">{contraparte.headline}</span>
            )}
          </div>
        </div>

        {clase.locationNote && (
          <p className="ml-11 mt-1 text-[12px] text-text-muted">Lugar: {clase.locationNote}</p>
        )}

        {scope === "past" && clase.status !== "CONFIRMED" && !esperaPago(clase.status) && (
          <div className="mt-3">
            <Badge
              tono={
                clase.status === "COMPLETED"
                  ? "menta"
                  : clase.status === "NO_SHOW"
                    ? "melocoton"
                    : "error"
              }
            >
              {clase.status === "COMPLETED" ? (
                <Check size={12} strokeWidth={2.4} />
              ) : (
                <X size={12} strokeWidth={2.4} />
              )}
              {etiquetaEstado(clase.status)}
            </Badge>
          </div>
        )}

        {/*
          Una reserva sin pagar aparece entre las próximas porque el cupo está apartado, pero
          todavía no es una clase: no hay sala ni confirmación. Lo que necesita es una salida al
          pago, no los botones de una clase que existe.
        */}
        {esperaPago(clase.status) && (
          <div className="mt-3.5 rounded-base bg-warning-bg px-4 py-3">
            <p className="flex items-center gap-1.5 text-[13px] font-bold text-warning">
              <Clock size={15} strokeWidth={2.2} />
              {esProfesor ? "Reservada, a la espera del pago" : "Te guardamos el cupo mientras pagas"}
            </p>
            <p className="mt-1 text-[12.5px] text-warning">
              {esProfesor
                ? "Tu horario está apartado. Te confirmamos la clase en cuanto entre el pago; si no entra, el cupo se libera solo."
                : "Si no completas el pago a tiempo, el horario vuelve a quedar libre."}
            </p>
            {!esProfesor && (
              <div className="mt-3 flex flex-wrap gap-2 sm:justify-end">
                <Link href={`/pago/${clase.id}`} className="min-w-[140px] flex-1 sm:flex-none">
                  <Boton className="h-10 w-full">Completar el pago</Boton>
                </Link>
                {/* Arrepentirse antes de pagar se puede siempre: no hay clase que proteger, y
                    esperar 20 minutos a que venza no es una respuesta. */}
                <Boton
                  variante="contorno"
                  onClick={() => setCancelando(true)}
                  className="h-10 min-w-[120px] flex-1 sm:flex-none"
                >
                  Soltar el cupo
                </Boton>
              </div>
            )}
          </div>
        )}

        {/* En móvil los controles ocupan el ancho (son el objetivo del pulgar); de `sm` en adelante
            se encogen a su contenido y la fila se alinea a la derecha, para que la acción no se
            convierta en una barra que atraviesa la tarjeta. */}
        <div className="mt-3.5 flex flex-wrap gap-2 sm:justify-end">
          {scope === "upcoming" && clase.status === "CONFIRMED" && virtual && clase.meetingLink && (
            <a
              href={clase.meetingLink}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex h-10 min-h-11 w-full items-center justify-center gap-2 rounded-pill bg-primary px-4 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus sm:order-last sm:min-h-0 sm:w-auto"
            >
              <Video size={16} strokeWidth={1.75} />
              Unirse a la clase
            </a>
          )}
          {scope === "upcoming" && clase.status === "CONFIRMED" && (
            <>
              {/* Los dos lados pueden proponer, y a cualquier hora: es justamente la salida de
                  quien ya no puede cancelar. Lo que protege al otro no es el plazo, es que tiene
                  que aceptar. */}
              <Boton
                variante="secundario"
                onClick={() => setReprogramando(true)}
                className="h-10 flex-1 basis-[180px] sm:flex-none sm:basis-auto"
              >
                Proponer otro horario
              </Boton>
              <Boton
                variante="contorno"
                disabled={!clase.canCancel}
                onClick={() => setCancelando(true)}
                className="h-10 min-w-[110px] flex-1 sm:flex-none"
              >
                Cancelar
              </Boton>
            </>
          )}

          {puedeReportar && (
            <Boton
              variante="peligro"
              onClick={() => setReportando(true)}
              className="h-10 flex-1 basis-[190px] sm:flex-none sm:basis-auto"
            >
              <AlertCircle size={15} strokeWidth={1.75} />
              Reportar un problema
            </Boton>
          )}

          {puedeRegistrar && (
            <Boton variante="tinta" onClick={() => setRegistrando(true)} className="h-10 flex-1 sm:flex-none">
              Registrar asistencia
            </Boton>
          )}

          {puedeCalificar && !resenaHecha && (
            <Boton
              variante="secundario"
              onClick={() => setCalificando(true)}
              className="h-10 min-w-[110px] flex-1 sm:flex-none"
            >
              <Star size={15} strokeWidth={1.9} />
              Calificar
            </Boton>
          )}
        </div>

        {puedeCalificar && resenaHecha && (
          <p className="mt-2.5 flex items-center justify-center gap-1.5 text-center text-[12px] font-semibold text-success">
            <Check size={13} strokeWidth={2.2} />
            ¡Gracias! Ya calificaste esta clase.
          </p>
        )}

        {/* El servidor decide con canCancel; aquí solo se explica por qué está bloqueado. */}
        {dentroDeLas24 && (
          <p className="mt-2.5 flex items-center justify-center gap-1.5 text-center text-[11.5px] text-text-muted">
            <AlertCircle size={13} strokeWidth={2.2} />
            Faltan menos de 24 h — la clase se considera impartida
          </p>
        )}
      </Tarjeta>

      {cancelando && <ModalCancelar clase={clase} onCerrar={() => setCancelando(false)} />}
      {reprogramando && <ModalReprogramar clase={clase} onCerrar={() => setReprogramando(false)} />}
      {reportando && <ModalReportar clase={clase} onCerrar={() => setReportando(false)} />}
      {registrando && <ModalAsistencia clase={clase} onCerrar={() => setRegistrando(false)} />}
      {calificando && (
        <ModalCalificar
          clase={clase}
          onCerrar={() => setCalificando(false)}
          onResenada={() => setResenaHecha(true)}
        />
      )}
    </>
  );
}

/**
 * Calificar una clase pasada (estudiante): estrellas 1..5 + comentario opcional. El backend arbitra
 * el estado real (422 "aún no puedes calificar" / plazo vencido) y el duplicado (409 "ya reseñaste").
 * En el 409 ocultamos el botón con gracia (ya está reseñada); en el 422 mostramos el mensaje y
 * dejamos el modal abierto. Al éxito agradecemos, invalidamos la lista y ocultamos el botón.
 */
function ModalCalificar({
  clase,
  onCerrar,
  onResenada,
}: {
  clase: MyBookingResponse;
  onCerrar: () => void;
  onResenada: () => void;
}) {
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(0);
  const [comentario, setComentario] = useState("");

  const calificar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/bookings/${clase.id}/review`, {
        method: "POST",
        body: { rating, comment: comentario.trim() || undefined },
      }),
    onSuccess: () => {
      onResenada();
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      onCerrar();
    },
    onError: (err) => {
      // 409: la clase ya estaba reseñada. No es un error para el usuario: la damos por hecha.
      if (err instanceof ApiError && err.status === 409) {
        onResenada();
        onCerrar();
      }
    },
  });

  // El 422 (aún no puedes / plazo vencido) sí se muestra; el 409 ya se resolvió cerrando el modal.
  const error =
    calificar.error instanceof ApiError && calificar.error.status !== 409
      ? calificar.error.message
      : null;

  return (
    <Modal
      titulo={`¿Cómo estuvo tu clase con ${clase.counterpart?.fullName?.split(" ")[0] ?? "tu profesor"}?`}
      onCerrar={onCerrar}
    >
      <p className="text-[13px] text-text-secondary">
        Tu reseña ayuda a otros estudiantes a elegir. Elige de 1 a 5 estrellas.
      </p>

      <div className="mt-4 flex justify-center">
        <SelectorEstrellas valor={rating} onCambio={setRating} />
      </div>

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="comentario">
        Comentario (opcional)
      </label>
      <textarea
        id="comentario"
        rows={3}
        maxLength={1000}
        value={comentario}
        onChange={(event) => setComentario(event.target.value)}
        placeholder="¿Qué destacarías de la clase?"
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm focus:border-primary focus:shadow-focus focus:outline-none"
      />

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Ahora no
        </Boton>
        <Boton
          variante="primario"
          disabled={rating === 0 || calificar.isPending}
          onClick={() => calificar.mutate()}
          className="h-11 flex-1"
        >
          {calificar.isPending ? "Enviando…" : "Enviar reseña"}
        </Boton>
      </div>
    </Modal>
  );
}

function ModalCancelar({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState("");

  const cancelar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/bookings/${clase.id}/cancel`, {
        method: "POST",
        body: { reason: motivo.trim() || undefined },
      }),
    onSuccess: () => {
      // La clase cambia de pestaña y el cupo vuelve a la agenda del profesor: refrescamos ambos.
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
  });

  const error = cancelar.error instanceof ApiError ? cancelar.error.message : null;

  // Soltar un cupo sin pagar no es lo mismo que cancelar una clase: nadie contaba con esa hora
  // todavía, y el saldo que se hubiera aplicado vuelve intacto.
  const sinPagar = esperaPago(clase.status);

  return (
    <Modal titulo={sinPagar ? "¿Soltar este cupo?" : "¿Cancelar esta clase?"} onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {fechaYRango(clase.startsAt!, clase.endsAt!)} con {clase.counterpart?.fullName}.{" "}
        {sinPagar
          ? "No se te ha cobrado nada y el horario vuelve a quedar libre."
          : "Puedes agendar otra cuando quieras."}
      </p>

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="motivo">
        Motivo (opcional)
      </label>
      <textarea
        id="motivo"
        rows={2}
        maxLength={300}
        value={motivo}
        onChange={(event) => setMotivo(event.target.value)}
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm focus:border-primary focus:shadow-focus focus:outline-none"
      />

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          {sinPagar ? "Conservar cupo" : "Mantener clase"}
        </Boton>
        <Boton
          variante="peligro"
          disabled={cancelar.isPending}
          onClick={() => cancelar.mutate()}
          className="h-11 flex-1"
        >
          {cancelar.isPending ? "Cancelando…" : sinPagar ? "Sí, soltarlo" : "Sí, cancelar"}
        </Boton>
      </div>
    </Modal>
  );
}

/** Motivos de reclamo, en el idioma del estudiante. Los códigos son los del backend. */
const MOTIVOS_RECLAMO = [
  { codigo: "PROFESSOR_NO_SHOW", etiqueta: "El profesor no se presentó" },
  { codigo: "PROFESSOR_LATE", etiqueta: "Llegó demasiado tarde" },
  { codigo: "TECHNICAL_PROBLEM", etiqueta: "Hubo un problema técnico" },
  { codigo: "LESSON_NOT_HELD", etiqueta: "La clase no se dio" },
  { codigo: "OTHER", etiqueta: "Otra cosa" },
];

/**
 * El reclamo del estudiante. Es su única vía para que una clase que no ocurrió no se dé por dictada
 * y su dinero no se libere solo: mientras el reclamo está abierto, esa plata no se mueve.
 */
function ModalReportar({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState(MOTIVOS_RECLAMO[0].codigo);
  const [descripcion, setDescripcion] = useState("");

  const reportar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/bookings/${clase.id}/report-problem`, {
        method: "POST",
        body: { reason: motivo, description: descripcion.trim() || undefined },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      onCerrar();
    },
  });

  const error = reportar.error instanceof ApiError ? reportar.error.message : null;

  return (
    <Modal titulo="Reportar un problema" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {fechaYRango(clase.startsAt!, clase.endsAt!)} con {clase.counterpart?.fullName}. Vamos a
        revisarlo y mientras tanto tu pago queda retenido.
      </p>

      <fieldset className="mt-4">
        <legend className="text-[12.5px] font-bold text-text-secondary">¿Qué pasó?</legend>
        <div className="mt-2 space-y-1.5">
          {MOTIVOS_RECLAMO.map((opcion) => (
            <label key={opcion.codigo} className="flex items-center gap-2.5 text-[13.5px]">
              <input
                type="radio"
                name="motivo-reclamo"
                value={opcion.codigo}
                checked={motivo === opcion.codigo}
                onChange={() => setMotivo(opcion.codigo)}
                className="h-4 w-4 accent-[var(--color-primary,#e8503a)]"
              />
              {opcion.etiqueta}
            </label>
          ))}
        </div>
      </fieldset>

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="descripcion-reclamo">
        Cuéntanos qué pasó (opcional)
      </label>
      <textarea
        id="descripcion-reclamo"
        rows={3}
        maxLength={1000}
        value={descripcion}
        onChange={(event) => setDescripcion(event.target.value)}
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm focus:border-primary focus:shadow-focus focus:outline-none"
      />

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Cancelar
        </Boton>
        <Boton
          disabled={reportar.isPending}
          onClick={() => reportar.mutate()}
          className="h-11 flex-1"
        >
          {reportar.isPending ? "Enviando…" : "Enviar reporte"}
        </Boton>
      </div>
    </Modal>
  );
}

function ModalReprogramar({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const profesorId = clase.counterpart?.id;
  const [diaElegido, setDiaElegido] = useState<string | null>(null);
  const [cupoElegido, setCupoElegido] = useState<string | null>(null);

  // La agenda del mismo profesor. Su cupo actual no aparece (ya está tomado por esta reserva).
  const cupos = useQuery({
    queryKey: ["slots", profesorId],
    queryFn: () => apiFetch<SlotsResponse>(`/api/v1/professors/${profesorId}/slots`),
    enabled: !!profesorId,
  });

  const porDia = useMemo(() => {
    const grupos: Record<string, SlotView[]> = {};
    for (const slot of cupos.data?.slots ?? []) {
      if (!slot.startsAt) continue;
      (grupos[diaBogota(slot.startsAt)] ??= []).push(slot);
    }
    return grupos;
  }, [cupos.data]);
  const dias = Object.keys(porDia);
  const diaActivo = diaElegido && porDia[diaElegido] ? diaElegido : (dias[0] ?? null);
  const cuposDelDia = diaActivo ? porDia[diaActivo] : [];

  const reprogramar = useMutation({
    mutationFn: (startsAt: string) =>
      apiFetch(`/api/v1/bookings/${clase.id}/reschedule-requests`, {
        method: "POST",
        body: { startsAt },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
    onError: () => {
      // 422/409: el cupo se ocupó o la agenda cambió; refrescamos para ver la realidad.
      setCupoElegido(null);
      void queryClient.invalidateQueries({ queryKey: ["slots", profesorId] });
    },
  });

  const error = reprogramar.error instanceof ApiError ? reprogramar.error.message : null;

  return (
    <Modal titulo="Proponer otro horario" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        Elige un horario libre de {clase.counterpart?.fullName}. La clase se mueve cuando la otra
        persona acepte; hasta entonces sigue en su hora original.
      </p>

      <div className="mt-4 space-y-3">
        {cupos.isPending && <Cargando filas={2} />}
        {cupos.isError && (
          <ErrorCarga mensaje="No pudimos cargar la agenda." onReintentar={() => void cupos.refetch()} />
        )}
        {cupos.data && dias.length === 0 && (
          <p className="rounded-base bg-surface-sunken px-4 py-3 text-[13px] text-text-secondary">
            No hay otros cupos disponibles esta semana. Vuelve más adelante o escríbele por WhatsApp.
          </p>
        )}

        {dias.length > 0 && (
          <>
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
            <Bloque tono="lavanda" titulo="Nuevo horario" icono={<Clock size={16} strokeWidth={1.75} />}>
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
            </Bloque>
          </>
        )}
      </div>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Volver
        </Boton>
        <Boton
          variante="primario"
          disabled={!cupoElegido || reprogramar.isPending}
          onClick={() => cupoElegido && reprogramar.mutate(cupoElegido)}
          className="h-11 flex-1"
        >
          {reprogramar.isPending ? "Guardando…" : "Confirmar cambio"}
        </Boton>
      </div>
    </Modal>
  );
}

function ModalAsistencia({ clase, onCerrar }: { clase: MyBookingResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [notas, setNotas] = useState("");
  const [asistio, setAsistio] = useState<boolean | null>(null);

  const registrar = useMutation({
    mutationFn: (present: boolean) =>
      apiFetch(`/api/v1/bookings/${clase.id}/attendance`, {
        method: "POST",
        body: { present, notes: notas.trim() || undefined },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
      onCerrar();
    },
  });

  const error = registrar.error instanceof ApiError ? registrar.error.message : null;

  return (
    <Modal
      titulo={`¿Cómo fue la clase con ${clase.counterpart?.fullName?.split(" ")[0] ?? ""}?`}
      onCerrar={onCerrar}
    >
      <div className="flex gap-2">
        <Boton
          variante={asistio === true ? "tinta" : "contorno"}
          onClick={() => setAsistio(true)}
          className="h-11 flex-1"
        >
          Asistió
        </Boton>
        <Boton
          variante={asistio === false ? "tinta" : "contorno"}
          onClick={() => setAsistio(false)}
          className="h-11 flex-1"
        >
          No asistió
        </Boton>
      </div>

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="notas">
        Notas (opcional)
      </label>
      <textarea
        id="notas"
        rows={3}
        maxLength={500}
        value={notas}
        onChange={(event) => setNotas(event.target.value)}
        className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-sm focus:border-primary focus:shadow-focus focus:outline-none"
      />

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Ahora no
        </Boton>
        <Boton
          variante="primario"
          disabled={asistio === null || registrar.isPending}
          onClick={() => asistio !== null && registrar.mutate(asistio)}
          className="h-11 flex-1"
        >
          {registrar.isPending ? "Guardando…" : "Guardar"}
        </Boton>
      </div>
    </Modal>
  );
}

function BannerReserva() {
  const params = useSearchParams();
  if (params.get("reservada") !== "1") return null;

  // El momento de deleite: Rigel celebra la reserva recién confirmada.
  return (
    <div className="anim-rise mb-4 flex items-center gap-3 rounded-card bg-success-bg p-4">
      <Rigel pose="celebracion" decorativo className="h-[118px] w-auto shrink-0" />
      <div>
        <p className="font-display text-[17px] font-bold text-success">¡Clase reservada!</p>
        <p className="mt-1 text-[13px] leading-relaxed text-text-secondary">
          Te enviamos la confirmación al correo, con la invitación al calendario.
        </p>
      </div>
    </div>
  );
}

/** El logo de WhatsApp: SVG inline, como todo en este diseño. */
