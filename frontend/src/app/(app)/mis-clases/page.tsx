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
import { etiquetaEstado } from "@/lib/estados-clase";
import { diaBogota, fechaCorta, fechaYRango, horaBogota } from "@/lib/format";
import { linkWhatsapp } from "@/lib/whatsapp";

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

        <ul className="grid gap-3 lg:grid-cols-2">
          {data?.map((clase) => (
            <TarjetaClase
              key={clase.id}
              clase={clase}
              scope={scope}
              esProfesor={esProfesor}
              miNombre={me?.fullName ?? ""}
            />
          ))}
        </ul>
      </div>
    </main>
  );
}

function TarjetaClase({
  clase,
  scope,
  esProfesor,
  miNombre,
}: {
  clase: MyBookingResponse;
  scope: Scope;
  esProfesor: boolean;
  miNombre: string;
}) {
  const [cancelando, setCancelando] = useState(false);
  const [reprogramando, setReprogramando] = useState(false);
  const [registrando, setRegistrando] = useState(false);
  const [calificando, setCalificando] = useState(false);
  // Sin flag de "ya reseñada" en el DTO: se recuerda localmente al calificar (o al chocar con el 409
  // de "ya reseñaste"), para ocultar el botón y agradecer sin recargar la lista.
  const [resenaHecha, setResenaHecha] = useState(false);

  const contraparte = clase.counterpart;
  const nombreContraparte = contraparte?.fullName ?? "";
  const virtual = clase.modality === "VIRTUAL";

  const whatsapp = linkWhatsapp({
    telefono: contraparte?.whatsappPhone,
    contraparte: nombreContraparte,
    yo: miNombre,
    inicioIso: clase.startsAt!,
  });

  // Una clase futura y confirmada que NO se puede cancelar solo puede ser por la regla de 24 h.
  const dentroDeLas24 = scope === "upcoming" && clase.status === "CONFIRMED" && !clase.canCancel;

  // El profesor registra asistencia de lo que ya ocurrió y sigue confirmado.
  const puedeRegistrar = esProfesor && scope === "past" && clase.status === "CONFIRMED";

  // El estudiante califica una clase pasada que se dio (confirmada o completada). El backend arbitra
  // el plazo/estado real (422) y el duplicado (409); aquí basta con ofrecer el botón en ese rango.
  const puedeCalificar =
    !esProfesor &&
    scope === "past" &&
    (clase.status === "CONFIRMED" || clase.status === "COMPLETED");

  return (
    <li>
      <Tarjeta>
        <div className="flex items-center justify-between gap-2">
          <span className="flex items-center gap-1.5 text-[13.5px] font-bold">
            <Calendar size={15} strokeWidth={1.75} className="text-primary" />
            {fechaYRango(clase.startsAt!, clase.endsAt!)}
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

        {scope === "past" && clase.status !== "CONFIRMED" && (
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

        {scope === "upcoming" && clase.status === "CONFIRMED" && virtual && clase.meetingLink && (
          <a
            href={clase.meetingLink}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-3.5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-pill bg-primary px-5 text-[14px] font-bold text-on-primary shadow-primary transition-colors hover:bg-primary-strong focus-visible:shadow-focus"
          >
            <Video size={16} strokeWidth={1.75} />
            Unirse a la clase
          </a>
        )}

        <div className="mt-3.5 flex flex-wrap gap-2">
          {whatsapp && (
            <a
              href={whatsapp}
              target="_blank"
              rel="noopener noreferrer"
              className="flex min-w-[120px] flex-1 items-center justify-center gap-1.5 rounded-pill border-[1.5px] border-success py-2.5 text-[13px] font-bold text-success transition-colors hover:bg-success-bg focus-visible:shadow-focus"
            >
              <LogoWhatsapp />
              WhatsApp
            </a>
          )}

          {scope === "upcoming" && clase.status === "CONFIRMED" && (
            <>
              {/* Reprogramar es del estudiante; el profesor cancela o escribe por WhatsApp. */}
              {!esProfesor && (
                <Boton
                  variante="secundario"
                  disabled={!clase.canCancel}
                  onClick={() => setReprogramando(true)}
                  className="h-10 min-w-[120px] flex-1"
                >
                  Reprogramar
                </Boton>
              )}
              <Boton
                variante="contorno"
                disabled={!clase.canCancel}
                onClick={() => setCancelando(true)}
                className="h-10 min-w-[110px] flex-1"
              >
                Cancelar
              </Boton>
            </>
          )}

          {puedeRegistrar && (
            <Boton variante="tinta" onClick={() => setRegistrando(true)} className="h-10 flex-1">
              Registrar asistencia
            </Boton>
          )}

          {puedeCalificar && !resenaHecha && (
            <Boton
              variante="secundario"
              onClick={() => setCalificando(true)}
              className="h-10 min-w-[110px] flex-1"
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
      {registrando && <ModalAsistencia clase={clase} onCerrar={() => setRegistrando(false)} />}
      {calificando && (
        <ModalCalificar
          clase={clase}
          onCerrar={() => setCalificando(false)}
          onResenada={() => setResenaHecha(true)}
        />
      )}
    </li>
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
        <Boton variante="contorno" onClick={onCerrar} className="h-12 flex-1">
          Ahora no
        </Boton>
        <Boton
          variante="primario"
          disabled={rating === 0 || calificar.isPending}
          onClick={() => calificar.mutate()}
          className="h-12 flex-1"
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

  return (
    <Modal titulo="¿Cancelar esta clase?" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {fechaYRango(clase.startsAt!, clase.endsAt!)} con {clase.counterpart?.fullName}. Puedes
        agendar otra cuando quieras.
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
        <Boton variante="contorno" onClick={onCerrar} className="h-12 flex-1">
          Mantener clase
        </Boton>
        <Boton
          variante="peligro"
          disabled={cancelar.isPending}
          onClick={() => cancelar.mutate()}
          className="h-12 flex-1"
        >
          {cancelar.isPending ? "Cancelando…" : "Sí, cancelar"}
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
      apiFetch(`/api/v1/bookings/${clase.id}/reschedule`, { method: "POST", body: { startsAt } }),
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
    <Modal titulo="Reprogramar clase" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        Elige un nuevo horario con {clase.counterpart?.fullName}. Tu cupo actual se libera.
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
        <Boton variante="contorno" onClick={onCerrar} className="h-12 flex-1">
          Volver
        </Boton>
        <Boton
          variante="primario"
          disabled={!cupoElegido || reprogramar.isPending}
          onClick={() => cupoElegido && reprogramar.mutate(cupoElegido)}
          className="h-12 flex-1"
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
        <Boton variante="contorno" onClick={onCerrar} className="h-12 flex-1">
          Ahora no
        </Boton>
        <Boton
          variante="primario"
          disabled={asistio === null || registrar.isPending}
          onClick={() => asistio !== null && registrar.mutate(asistio)}
          className="h-12 flex-1"
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
function LogoWhatsapp() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="currentColor" aria-hidden="true">
      <path d="M17.5 14.4c-.3-.2-1.7-.9-2-1-.3-.1-.5-.2-.7.1s-.8 1-.9 1.2c-.2.2-.3.2-.6.1-.3-.2-1.2-.5-2.3-1.4-.9-.8-1.4-1.7-1.6-2-.2-.3 0-.5.1-.6l.5-.5c.1-.2.2-.3.3-.5 0-.2 0-.4-.1-.5l-.9-2.2c-.2-.5-.5-.5-.7-.5h-.6c-.2 0-.5.1-.8.4-.3.3-1 1-1 2.5s1.1 2.9 1.2 3.1c.1.2 2.1 3.2 5 4.4.7.3 1.2.5 1.7.6.7.2 1.3.2 1.8.1.6-.1 1.7-.7 1.9-1.4.2-.7.2-1.2.2-1.4-.1-.1-.3-.2-.5-.3z" />
      <path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2z" />
    </svg>
  );
}
