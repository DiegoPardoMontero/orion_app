"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, Clock, Star, X } from "lucide-react";
import { apiFetch, ApiError } from "@/lib/api/fetch";
import { AvisoError } from "@/components/estados";
import { Boton } from "@/components/ui";
import { momentosDeAsistencia, useAhoraHasta } from "@/components/aula/asistencia";
import type { ClassroomResponse } from "@/lib/api/aula";
import { useCifras } from "@/lib/cifras";
import { horaBogota, rangoHoras } from "@/lib/format";

const VALOR: Record<number, string> = {
  1: "Mejorable",
  2: "Regular",
  3: "Buena",
  4: "Muy buena",
  5: "Excelente",
};

/**
 * El cierre: la hoja que sube cuando la clase termina, sin recargar.
 *
 * <p>Dos versiones porque las dos personas acaban de hacer cosas distintas. El estudiante acaba de
 * hablar en otro idioma durante casi una hora y lo que necesita es que alguien se lo reconozca —de
 * ahí el titular con los minutos, que es un logro y no una métrica—. El profesor acaba de trabajar
 * y lo que necesita es cerrar: registrar la asistencia y pasar a lo siguiente.
 *
 * <p><strong>Calificar es opcional de verdad.</strong> «Ahora no» tiene el mismo tamaño que
 * «Enviar», y saltárselo no deja aviso ni recordatorio. Una calificación arrancada a presión no
 * mide nada y además enseña a la gente a mentir para salir de la pantalla.
 */
export function HojaDeCierre({
  datos,
  bookingId,
  minutos,
  onCerrar,
  onIr,
  onVolverALaClase,
}: {
  datos: ClassroomResponse;
  bookingId: string;
  minutos: number;
  /** Cierra la hoja y vuelve a Mis clases. */
  onCerrar: () => void;
  /**
   * Cierra la hoja y va a otra pantalla. Toda salida pasa por aquí o por `onCerrar`: navegar con la
   * hoja abierta deja la llamada en «cierre», y `ClaseEnCurso` devuelve al aula a quien lo intente.
   */
  onIr: (ruta: string) => void;
  /** Vuelve a entrar a la sala, mientras siga abierta. */
  onVolverALaClase: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-night/45 sm:items-center">
      <div
        className="max-h-dvh w-full max-w-lg overflow-y-auto bg-surface shadow-[0_-18px_44px_rgba(51,32,59,.18)] animate-[sheet-up_380ms_cubic-bezier(.22,1,.36,1)]"
        style={{ borderRadius: "var(--sheet-radius) var(--sheet-radius) 0 0" }}
        role="dialog"
        aria-modal="true"
        aria-label="La clase terminó"
      >
        {datos.moderator ? (
          <CierreProfesor
            datos={datos}
            bookingId={bookingId}
            minutos={minutos}
            onCerrar={onCerrar}
            onIr={onIr}
            onVolverALaClase={onVolverALaClase}
          />
        ) : (
          <CierreEstudiante datos={datos} bookingId={bookingId} minutos={minutos} onCerrar={onCerrar} onIr={onIr} />
        )}
      </div>
    </div>
  );
}

function CierreEstudiante({
  datos,
  bookingId,
  minutos,
  onCerrar,
  onIr,
}: {
  datos: ClassroomResponse;
  bookingId: string;
  minutos: number;
  onCerrar: () => void;
  onIr: (ruta: string) => void;
}) {
  const nombre = datos.counterpart?.firstName ?? "tu profesor";
  const [estrellas, setEstrellas] = useState(0);
  const [comentario, setComentario] = useState("");

  const enviar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/bookings/${bookingId}/review`, {
        method: "POST",
        body: { rating: estrellas, comment: comentario.trim() || undefined },
      }),
    onSuccess: onCerrar,
  });

  return (
    <>
      <div className="bg-[linear-gradient(135deg,#2e1e4e_0%,#7a4a8c_55%,#e8764f_100%)] px-6 py-7 text-text-on-night">
        <p className="font-display text-h2 font-bold">
          Hablaste {minutos} {minutos === 1 ? "minuto" : "minutos"} en inglés.
        </p>
        <p className="mt-1 text-[13px] opacity-85">{rangoHoras(datos.startsAt, datos.endsAt)}</p>
      </div>

      <div className="px-6 py-6">
        <h2 className="text-[15px] font-bold text-text">¿Cómo te fue con {nombre}?</h2>

        <div role="radiogroup" aria-label="Calificación" className="mt-3 flex gap-1.5">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              role="radio"
              aria-checked={estrellas === n}
              aria-label={`${n}: ${VALOR[n]}`}
              onClick={() => setEstrellas(n)}
              className="rounded-base p-1 focus-visible:shadow-focus"
            >
              <Star
                size={40}
                strokeWidth={1.75}
                className={n <= estrellas ? "fill-accent-peach text-accent-peach" : "text-border-strong"}
              />
            </button>
          ))}
        </div>
        <p className="mt-1.5 h-5 text-[13px] font-semibold text-text-secondary">
          {estrellas ? VALOR[estrellas] : ""}
        </p>

        <textarea
          rows={3}
          value={comentario}
          onChange={(e) => setComentario(e.target.value)}
          maxLength={1000}
          placeholder="¿Qué destacarías de la clase? (opcional)"
          aria-label="Comentario opcional"
          className="mt-3 w-full rounded-base border-[1.5px] border-border bg-surface-raised p-3 text-[14px] text-text focus:border-primary focus:shadow-focus focus:outline-none"
        />

        {enviar.error instanceof ApiError && (
          <div className="mt-2">
            <AvisoError mensaje={enviar.error.message} />
          </div>
        )}

        <div className="mt-4 grid grid-cols-2 gap-2.5">
          <Boton variante="contorno" onClick={onCerrar}>
            Ahora no
          </Boton>
          <Boton
            variante="primario"
            disabled={estrellas === 0 || enviar.isPending}
            onClick={() => enviar.mutate()}
          >
            Enviar
          </Boton>
        </div>

        {/* El aula no trae el id del profesor: el enlace va al catálogo y el texto no promete más. */}
        <Link
          href="/profesores"
          onClick={(e) => {
            e.preventDefault();
            onIr("/profesores");
          }}
          className="mt-4 block text-center text-[13px] font-semibold text-primary-strong hover:underline"
        >
          Reservar otra clase
        </Link>
      </div>
    </>
  );
}

function CierreProfesor({
  datos,
  bookingId,
  minutos,
  onCerrar,
  onIr,
  onVolverALaClase,
}: {
  datos: ClassroomResponse;
  bookingId: string;
  minutos: number;
  onCerrar: () => void;
  onIr: (ruta: string) => void;
  onVolverALaClase: () => void;
}) {
  const nombre = datos.counterpart?.firstName ?? "tu estudiante";
  const cifras = useCifras();
  const queryClient = useQueryClient();

  // Colgar antes de la hora es normal —se cae la conexión, se acaba el tema—, y el servidor no deja
  // dar la clase por dictada antes de que termine. Por eso cada respuesta se habilita en su momento,
  // la sala se puede retomar mientras siga abierta y registrar puede quedar para después.
  const desde = momentosDeAsistencia(datos.startsAt, datos.endsAt, cifras.noShowReportMinutes);
  const salaHasta = Date.parse(datos.expiresAt);
  const ahora = useAhoraHasta([desde.asistio, desde.noAsistio, salaHasta]);
  const puedeAsistio = ahora >= desde.asistio;
  const puedeNoAsistio = ahora >= desde.noAsistio;
  const salaAbierta = !!datos.token && ahora < salaHasta;

  // Si nunca entró, la respuesta honesta viene preseleccionada; si entró y se cayó, asistió. Lo que
  // todavía no se puede registrar no cuenta como elegido: aparece solo cuando se abre.
  const [asistio, setAsistio] = useState<boolean | null>(
    datos.counterpartPresent ? true : null,
  );
  const eleccion =
    (asistio === true && !puedeAsistio) || (asistio === false && !puedeNoAsistio) ? null : asistio;

  // Si la clase se dio, lo siguiente natural es contarla: el acta se ofrece aquí mismo, mientras
  // la clase está fresca («Ahora no» devuelve a Mis clases). Si no se presentó, no hay nada que
  // contar y la hoja se cierra como siempre.
  const cerrar = useMutation({
    mutationFn: (present: boolean) =>
      apiFetch(`/api/v1/bookings/${bookingId}/attendance`, {
        method: "POST",
        body: { present },
      }),
    onSuccess: (_, present) => {
      refrescarClases();
      if (present) {
        onIr(`/mis-clases/${bookingId}/acta`);
      } else {
        onCerrar();
      }
    },
    onError: (error) => {
      // 409: la clase ya no está confirmada —ya se registró, se canceló o el estudiante reportó un
      // problema—. Quedarse en la hoja no arregla nada: se va a la clase, que dice en qué quedó.
      if (error instanceof ApiError && error.status === 409) {
        refrescarClases();
        onIr(`/mis-clases?scope=past&clase=${bookingId}&aviso=asistencia-cerrada`);
      }
    },
  });

  // La tarjeta pasa a ofrecer el acta y «Actas por escribir» la cuenta: las tres consultas cambian.
  function refrescarClases() {
    void queryClient.invalidateQueries({ queryKey: ["me", "bookings"] });
    void queryClient.invalidateQueries({ queryKey: ["lesson-notes-summary"] });
    void queryClient.invalidateQueries({ queryKey: ["lesson-notes-index"] });
  }

  return (
    <div className="px-6 py-6">
      <span className="inline-flex items-center gap-1.5 rounded-pill bg-surface-sunken px-2.5 py-1 text-[11.5px] font-bold text-text-secondary">
        <X size={12} strokeWidth={2.4} />
        Sala cerrada
      </span>

      <h2 className="mt-3 font-display text-h2 font-bold">
        {datos.counterpartPresent
          ? `La clase duró ${minutos} min`
          : `Estuviste ${minutos} min en la sala`}
      </h2>
      <p className="mt-1 text-[13px] text-text-muted">{rangoHoras(datos.startsAt, datos.endsAt)}</p>

      <p className="mt-5 text-[13.5px] font-bold text-text">¿{nombre} asistió?</p>
      <div role="radiogroup" aria-label="Asistencia" className="mt-2.5 grid gap-2.5">
        <TarjetaAsistencia
          seleccionada={eleccion === true}
          onClick={() => setAsistio(true)}
          titulo={`${nombre} asistió`}
          disponibleDesde={puedeAsistio ? null : `Podrás registrarla a las ${horaBogota(datos.endsAt)}, cuando termine la clase`}
        />
        <TarjetaAsistencia
          seleccionada={eleccion === false}
          onClick={() => setAsistio(false)}
          titulo={`${nombre} no se presentó`}
          disponibleDesde={
            puedeNoAsistio
              ? null
              : `Podrás marcarlo a las ${horaBogota(new Date(desde.noAsistio).toISOString())}, ${cifras.noShowReportMinutes} min después del inicio`
          }
        />
      </div>

      {eleccion === false && (
        <div className="mt-3 rounded-card bg-accent-peach-soft p-3.5 text-[12.5px] leading-relaxed text-[#8a5a33]">
          La clase se registra como no asistida por {nombre}. Tu tiempo cuenta como impartido.
          {" "}
          {nombre} recibe un aviso amable, no un regaño.
        </div>
      )}

      {/* La «nota privada para la próxima clase» se quitó (Pardo, 29/09/2026): se guardaba y ninguna
          pantalla la volvía a mostrar. Lo que importa de la clase va en el acta. */}
      {cerrar.error instanceof ApiError && cerrar.error.status !== 409 && (
        <div className="mt-2">
          <AvisoError mensaje={cerrar.error.message} />
        </div>
      )}

      <div className="mt-4 flex flex-col gap-2.5">
        <Boton
          variante="primario"
          disabled={eleccion === null || cerrar.isPending}
          onClick={() => eleccion !== null && cerrar.mutate(eleccion)}
        >
          Cerrar la clase
        </Boton>
        {salaAbierta && (
          <Boton variante="contorno" onClick={onVolverALaClase} disabled={cerrar.isPending}>
            Volver a la clase
          </Boton>
        )}
        <Boton variante="fantasma" onClick={onCerrar} disabled={cerrar.isPending}>
          Ahora no, lo registro después
        </Boton>
        {eleccion === false && (
          <Link
            href="/mensajes"
            onClick={(e) => {
              e.preventDefault();
              onIr("/mensajes");
            }}
            className="text-center text-[13px] font-semibold text-primary-strong hover:underline"
          >
            Escribirle a {nombre}
          </Link>
        )}
      </div>
      <p className="mt-3 text-center text-[12px] leading-relaxed text-text-muted">
        Si lo dejas para después, lo registras desde Mis clases.
      </p>
    </div>
  );
}

function TarjetaAsistencia({
  seleccionada,
  onClick,
  titulo,
  disponibleDesde,
}: {
  seleccionada: boolean;
  onClick: () => void;
  titulo: string;
  /** Si todavía no se puede elegir, cuándo sí. */
  disponibleDesde: string | null;
}) {
  const cerrada = disponibleDesde !== null;
  return (
    <button
      type="button"
      role="radio"
      aria-checked={seleccionada}
      disabled={cerrada}
      onClick={onClick}
      className={`flex items-center gap-3 rounded-card border-2 p-3.5 text-left focus-visible:shadow-focus disabled:cursor-default ${
        seleccionada
          ? "border-primary bg-primary-soft"
          : cerrada
            ? "border-dashed border-border bg-surface"
            : "border-border bg-surface-raised"
      }`}
    >
      <span
        className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${
          seleccionada ? "border-primary" : "border-border-strong"
        } ${cerrada ? "opacity-50" : ""}`}
      >
        {seleccionada && <span className="h-2.5 w-2.5 rounded-full bg-primary" />}
      </span>
      <span className="min-w-0">
        <span className={`block text-[14px] font-semibold ${cerrada ? "text-text-muted" : "text-text"}`}>
          {titulo}
        </span>
        {cerrada && (
          <span className="mt-0.5 flex items-center gap-1 text-[12.5px] text-text-secondary">
            <Clock size={12} strokeWidth={2.2} className="shrink-0" />
            {disponibleDesde}
          </span>
        )}
      </span>
      {seleccionada && <Check size={16} strokeWidth={2.4} className="ml-auto text-primary" />}
    </button>
  );
}
