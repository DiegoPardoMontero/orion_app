"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertCircle, CalendarOff, Check, Clock, MousePointerClick, Plus, TriangleAlert, X } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { CalendarioSemanal, DIAS_SEMANA as DIAS, type Propuesta } from "@/components/CalendarioSemanal";
import { RigelDeHorarios } from "@/components/profesor/RigelDeHorarios";
import {
  aHhmm,
  aMinutos,
  cuposDeTramo,
  cuposPorSemana,
  FIN_DEL_DIA,
  primeraHoraLibre,
  rangoLargo,
  type FranjaDia,
} from "@/components/profesor/franjas";
import { Bloque, Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { ExceptionResponse, MyBookingResponse, RuleResponse } from "@/lib/api/types";
import { useCifras } from "@/lib/cifras";
import { diaBogota, fechaLarga, finDeClase, hora12, minutoDelDiaBogota, rangoCompacto, rangoHoras } from "@/lib/format";

/**
 * Cada media hora del día. El VALOR sigue siendo "18:30", que es lo que entiende el backend; lo que
 * cambia es la etiqueta que lee el profesor, en formato de 12 horas.
 *
 * <p>Antes eran solo las horas en punto. No lo exigía ni el backend ni la base —era convención de
 * este formulario— y dejaba fuera al profesor que empieza a y media. Ahora que los cupos arrancan
 * cada media hora, la franja también tiene que poder hacerlo: si no, abrir de 17:30 a 20:00 era
 * imposible de decir.
 */
const HORAS = Array.from({ length: 48 }, (_, i) =>
  `${String(Math.floor(i / 2)).padStart(2, "0")}:${i % 2 === 0 ? "00" : "30"}`,
);

const CLAVE_REGLAS = ["me", "rules"] as const;

/** Un cambio sobre el horario hecho desde la rejilla: se guarda al soltar y se puede deshacer. */
type Accion =
  | { tipo: "crear"; propuesta: Propuesta }
  | { tipo: "cambiar"; regla: RuleResponse; propuesta: Propuesta }
  | { tipo: "quitar"; regla: RuleResponse };

type Aviso = { texto: string; error?: boolean; deshacer?: Accion };

/**
 * Los horarios del profesor: sus franjas de cada semana y las fechas bloqueadas. Vive dentro de «Mi
 * perfil» desde el 24/09/2026 («fusiona disponibilidad con lo demás del profesor»); la ruta vieja
 * /disponibilidad lleva aquí.
 *
 * <p>En el computador la semana es una rejilla que se maneja arrastrando (27/09/2026), con Rigel a
 * la derecha contando lo que abre; en el celular sigue la lista por días con su formulario, que
 * también es el camino del teclado en el computador.
 */
export function MisHorarios() {
  const queryClient = useQueryClient();
  const { classMinutes } = useCifras();

  const reglas = useQuery({
    queryKey: CLAVE_REGLAS,
    queryFn: () => apiFetch<RuleResponse[]>("/api/v1/me/availability/rules"),
  });

  const excepciones = useQuery({
    queryKey: ["me", "exceptions"],
    queryFn: () => apiFetch<ExceptionResponse[]>("/api/v1/me/availability/exceptions"),
  });

  const [formulario, setFormulario] = useState<{ weekday: number; regla?: RuleResponse } | null>(null);
  const [franjaAEliminar, setFranjaAEliminar] = useState<RuleResponse | null>(null);
  const [bloqueando, setBloqueando] = useState(false);
  const [aviso, setAviso] = useState<Aviso | null>(null);
  const [celebrando, setCelebrando] = useState(false);
  // Cierres estables: el Modal vuelve a enfocarse cada vez que cambia su `onCerrar`, y esta pantalla
  // se vuelve a pintar sola (el aviso que se va, Rigel que deja de celebrar) con un diálogo abierto.
  const cerrarFormulario = useCallback(() => setFormulario(null), []);
  const cerrarEliminar = useCallback(() => setFranjaAEliminar(null), []);
  const cerrarBloqueo = useCallback(() => setBloqueando(false), []);
  const temporizadorAviso = useRef<ReturnType<typeof setTimeout>>(undefined);
  const temporizadorFiesta = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(
    () => () => {
      clearTimeout(temporizadorAviso.current);
      clearTimeout(temporizadorFiesta.current);
    },
    [],
  );

  const mostrarAviso = (siguiente: Aviso | null) => {
    setAviso(siguiente);
    clearTimeout(temporizadorAviso.current);
    if (siguiente) temporizadorAviso.current = setTimeout(() => setAviso(null), siguiente.error ? 10_000 : 7_000);
  };

  // Rigel celebra unos segundos cada franja nueva.
  const celebrar = () => {
    setCelebrando(true);
    clearTimeout(temporizadorFiesta.current);
    temporizadorFiesta.current = setTimeout(() => setCelebrando(false), 3_500);
  };

  const cambio = useMutation({
    mutationFn: async (accion: Accion): Promise<{ antes: RuleResponse | null; despues: RuleResponse | null; aviso: Aviso }> => {
      const actuales = queryClient.getQueryData<RuleResponse[]>(CLAVE_REGLAS) ?? [];
      if (accion.tipo === "crear") {
        const mas = cuposPorSemana([...aFranjas(actuales), accion.propuesta], classMinutes) - cuposPorSemana(aFranjas(actuales), classMinutes);
        const nueva = await crearRegla(accion.propuesta);
        return {
          antes: null,
          despues: nueva,
          aviso: {
            texto: `Abriste el ${diaYRango(accion.propuesta)}${mas > 0 ? ` · +${mas} ${mas === 1 ? "cupo" : "cupos"} por semana` : ""}`,
            deshacer: { tipo: "quitar", regla: nueva },
          },
        };
      }
      if (accion.tipo === "quitar") {
        await apiFetch(`/api/v1/me/availability/rules/${accion.regla.id}`, { method: "DELETE" });
        return {
          antes: accion.regla,
          despues: null,
          aviso: { texto: `Quitaste la franja del ${diaYRango(propuestaDe(accion.regla))}`, deshacer: { tipo: "crear", propuesta: propuestaDe(accion.regla) } },
        };
      }
      const nueva = await reemplazarRegla(accion.regla, accion.propuesta);
      return {
        antes: accion.regla,
        despues: nueva,
        aviso: {
          texto: `Franja cambiada: ${diaYRango(accion.propuesta)}`,
          deshacer: { tipo: "cambiar", regla: nueva, propuesta: propuestaDe(accion.regla) },
        },
      };
    },
    onSuccess: ({ antes, despues, aviso: siguiente }, accion) => {
      // La rejilla pinta la franja nueva ya, sin esperar a la recarga: si no, parpadeaba vacía.
      queryClient.setQueryData<RuleResponse[]>(CLAVE_REGLAS, (previas = []) => [
        ...previas.filter((regla) => regla.id !== antes?.id),
        ...(despues ? [despues] : []),
      ]);
      mostrarAviso(siguiente);
      if (accion.tipo === "crear") celebrar();
    },
    onError: (error) => mostrarAviso({ texto: mensajeDe(error), error: true }),
    onSettled: () => refrescarHorario(queryClient),
  });

  const variables = cambio.isPending ? cambio.variables : undefined;
  const guardando = variables && variables.tipo !== "quitar" ? { ...variables.propuesta } : null;
  const reemplazando = variables && variables.tipo !== "crear" ? (variables.regla.id ?? null) : null;

  if (reglas.isPending || excepciones.isPending) {
    return <Cargando filas={4} />;
  }

  if (reglas.isError) {
    return <ErrorCarga mensaje="No pudimos cargar tus horarios." onReintentar={() => void reglas.refetch()} />;
  }

  const todas = reglas.data ?? [];

  const hacer = (accion: Accion) => {
    mostrarAviso(null);
    cambio.mutate(accion);
  };

  return (
    <section aria-labelledby="titulo-horarios" className="mt-5">
      <div>
        <h2 id="titulo-horarios" className="font-display text-[19px] font-bold">
          Mis horarios
        </h2>
        <p className="mt-1 flex items-center gap-1.5 text-[12.5px] text-text-secondary">
          <Clock size={14} strokeWidth={1.75} />
          Horario semanal recurrente · hora de Colombia
        </p>
        <p className="mt-1 hidden items-center gap-1.5 text-[12.5px] text-text-secondary lg:flex">
          <MousePointerClick size={14} strokeWidth={1.75} />
          Arrastra sobre un día para abrir una franja. Arrastra una franja para moverla, o estírala desde su borde.
        </p>
      </div>

      <div className="mt-4 flex flex-col gap-4 xl:grid xl:grid-cols-[minmax(0,1fr)_300px] xl:items-start xl:gap-6">
        <div data-tour="horarios-semana" className="order-2 rounded-card xl:order-none">
          {/* En el computador, el horario se dibuja como lo que es: una rejilla de horas por días,
              donde la duración de una franja es su altura. En el celular no cabe una rejilla de siete
              columnas, así que ahí siguen las tarjetas apiladas, un día debajo de otro. */}
          <div className="hidden lg:block">
            <CalendarioSemanal
              reglas={todas}
              duracionClase={classMinutes}
              guardando={guardando}
              reemplazando={reemplazando}
              bloqueado={cambio.isPending}
              onCrear={(propuesta) => hacer({ tipo: "crear", propuesta })}
              onCambiar={(regla, propuesta) => hacer({ tipo: "cambiar", regla, propuesta })}
              onEditar={(regla) => setFormulario({ weekday: regla.weekday!, regla })}
              onEliminar={setFranjaAEliminar}
              onAnadir={(weekday) => setFormulario({ weekday })}
            />
          </div>

          <div className="grid gap-2.5 lg:hidden">
            {DIAS.map((dia) => {
              const delDia = todas
                .filter((regla) => regla.weekday === dia.valor)
                .sort((a, b) => aMinutos(a.startTime!) - aMinutos(b.startTime!));
              return (
                <div key={dia.valor} className="rounded-card bg-info-bg p-3">
                  <div className="flex items-center justify-between gap-1">
                    <span className="text-[13.5px] font-bold text-info">{dia.nombre}</span>
                    <button
                      type="button"
                      aria-label={`Añadir franja el ${dia.nombre.toLowerCase()}`}
                      onClick={() => setFormulario({ weekday: dia.valor })}
                      className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-white text-info transition-colors hover:bg-primary hover:text-on-primary"
                    >
                      <Plus size={15} strokeWidth={2.4} />
                    </button>
                  </div>

                  {delDia.length === 0 ? (
                    <p className="mt-2 text-[11.5px] text-info/70">Sin franjas</p>
                  ) : (
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {delDia.map((regla) => (
                        <ChipFranja key={regla.id} regla={regla} onEliminar={() => setFranjaAEliminar(regla)} />
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {/* Rigel y las fechas bloqueadas: a la derecha en el computador ancho; en el celular Rigel va
            arriba y las fechas al final (el contenedor se «disuelve» y cada uno toma su lugar). */}
        <div className="contents xl:sticky xl:top-6 xl:flex xl:flex-col xl:gap-4">
          <div className="order-1 xl:order-none">
            <RigelDeHorarios reglas={todas} duracionClase={classMinutes} celebrando={celebrando} />
          </div>

          <aside className="order-3 xl:order-none">
            <Bloque tono="melocoton" titulo="Fechas bloqueadas" icono={<CalendarOff size={16} strokeWidth={1.75} />}>
              {(excepciones.data ?? []).length === 0 ? (
                <p className="text-[12.5px] text-warning">
                  Ninguna por ahora. Bloquea un día cuando no puedas dar clases.
                </p>
              ) : (
                <ul className="space-y-2">
                  {excepciones.data!.map((excepcion) => (
                    <FilaExcepcion key={excepcion.id} excepcion={excepcion} />
                  ))}
                </ul>
              )}

              <button
                type="button"
                onClick={() => setBloqueando(true)}
                className="mt-3 w-full rounded-base border-[1.5px] border-dashed border-warning py-2.5 text-[13px] font-bold text-warning hover:bg-white/60"
              >
                Bloquear una fecha
              </button>
            </Bloque>
          </aside>
        </div>
      </div>

      {formulario && (
        <ModalFranja
          weekday={formulario.weekday}
          regla={formulario.regla}
          otras={todas.filter((regla) => regla.weekday === formulario.weekday && regla.id !== formulario.regla?.id)}
          duracionClase={classMinutes}
          onGuardada={(nueva) => {
            if (nueva) celebrar();
          }}
          onEliminar={
            formulario.regla
              ? () => {
                  setFranjaAEliminar(formulario.regla!);
                  setFormulario(null);
                }
              : undefined
          }
          onCerrar={cerrarFormulario}
        />
      )}

      {franjaAEliminar && <ModalEliminarFranja regla={franjaAEliminar} onCerrar={cerrarEliminar} />}
      {bloqueando && <ModalBloquearFecha onCerrar={cerrarBloqueo} />}

      {/* Lo que pasó con el último arrastre, con su «Deshacer», como en los calendarios. */}
      {aviso && (
        <div className="pointer-events-none fixed inset-x-0 bottom-24 z-40 flex justify-center px-4 lg:bottom-8">
          <div
            role="status"
            className={`anim-toast pointer-events-auto flex max-w-[600px] items-center gap-2 rounded-pill py-1.5 pl-4 pr-1.5 text-[13.5px] font-semibold text-on-primary shadow-lg ${
              aviso.error ? "bg-error" : "bg-night"
            }`}
          >
            {aviso.error ? (
              <AlertCircle size={16} strokeWidth={2} className="shrink-0" />
            ) : (
              <Check size={16} strokeWidth={2.4} className="shrink-0 text-accent-peach" />
            )}
            <span className="py-1.5">{aviso.texto}</span>
            {aviso.deshacer && (
              <button
                type="button"
                onClick={() => hacer(aviso.deshacer!)}
                className="shrink-0 rounded-pill px-3 py-1.5 font-bold text-accent-peach transition-colors hover:bg-white/10 focus-visible:shadow-focus"
              >
                Deshacer
              </button>
            )}
            <button
              type="button"
              aria-label="Cerrar el aviso"
              onClick={() => mostrarAviso(null)}
              className="grid h-8 w-8 shrink-0 place-items-center rounded-full transition-colors hover:bg-white/10 focus-visible:shadow-focus"
            >
              <X size={15} strokeWidth={2.2} />
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function ChipFranja({ regla, onEliminar }: { regla: RuleResponse; onEliminar: () => void }) {
  const franja = rangoCompacto(corta(regla.startTime), corta(regla.endTime));

  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-pill bg-night py-1.5 pl-2.5 pr-1.5 text-[12px] font-bold text-on-primary">
      {franja}
      <button
        type="button"
        aria-label={`Eliminar la franja ${franja}`}
        onClick={onEliminar}
        className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-white/20 transition-colors hover:bg-primary"
      >
        <X size={12} strokeWidth={2.6} />
      </button>
    </span>
  );
}

function ModalEliminarFranja({ regla, onCerrar }: { regla: RuleResponse; onCerrar: () => void }) {
  const queryClient = useQueryClient();

  const borrar = useMutation({
    mutationFn: () => apiFetch(`/api/v1/me/availability/rules/${regla.id}`, { method: "DELETE" }),
    onSuccess: () => {
      // Cambia lo que ven los estudiantes: hay que refrescar también los cupos.
      refrescarHorario(queryClient);
      onCerrar();
    },
  });

  const franja = rangoCompacto(corta(regla.startTime), corta(regla.endTime));
  const error = borrar.error ? mensajeDe(borrar.error) : null;

  return (
    <Modal titulo="¿Eliminar esta franja?" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {nombreDelDia(regla.weekday!)}, {franja}. Los estudiantes dejarán de ver estos cupos; las clases que ya te
        reservaron siguen en pie.
      </p>
      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}
      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Volver
        </Boton>
        <Boton variante="peligro" disabled={borrar.isPending} onClick={() => borrar.mutate()} className="h-11 flex-1">
          Eliminar
        </Boton>
      </div>
    </Modal>
  );
}

function FilaExcepcion({ excepcion }: { excepcion: ExceptionResponse }) {
  const queryClient = useQueryClient();

  const borrar = useMutation({
    mutationFn: () => apiFetch(`/api/v1/me/availability/exceptions/${excepcion.id}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "exceptions"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
    },
  });

  const cuando = excepcion.startTime ? rangoCompacto(corta(excepcion.startTime), corta(excepcion.endTime)) : "todo el día";

  return (
    <li className="flex items-center justify-between gap-2 rounded-base bg-surface-raised px-3.5 py-2.5">
      <span className="text-[12px] font-semibold text-text">
        {/* La fecha llega como YYYY-MM-DD; el mediodía evita que la zona la corra un día. */}
        {fechaLarga(`${excepcion.date}T12:00:00-05:00`)} · {cuando}
        {excepcion.reason ? ` · ${excepcion.reason}` : ""}
      </span>
      <button
        type="button"
        aria-label="Eliminar bloqueo"
        disabled={borrar.isPending}
        onClick={() => borrar.mutate()}
        className="grid h-6 w-6 shrink-0 place-items-center rounded-full text-text-muted hover:bg-error-bg hover:text-error"
      >
        <X size={13} strokeWidth={2.4} />
      </button>
    </li>
  );
}

/**
 * El formulario de una franja: para añadirla (el «+» de cada día, que es también el camino del
 * teclado) o para cambiarle las horas a una que ya existe (pulsándola en la rejilla).
 */
function ModalFranja({
  weekday,
  regla,
  otras,
  duracionClase,
  onGuardada,
  onEliminar,
  onCerrar,
}: {
  weekday: number;
  regla?: RuleResponse;
  /** Las demás franjas de ese día, para proponer una hora libre. */
  otras: RuleResponse[];
  duracionClase: number;
  onGuardada: (nueva: boolean) => void;
  onEliminar?: () => void;
  onCerrar: () => void;
}) {
  const queryClient = useQueryClient();
  const [inicio, setInicio] = useState(() =>
    regla ? corta(regla.startTime) : aHhmm(primeraHoraLibre(aFranjas(otras))),
  );
  // Una hora después: la clase mínima. Ampliarla es un clic en el select.
  const [fin, setFin] = useState(() => (regla ? corta(regla.endTime) : siguienteHora(inicio)));

  const guardar = useMutation({
    mutationFn: () => {
      const propuesta = { weekday, inicio: aMinutos(inicio), fin: aMinutos(fin) };
      return regla ? reemplazarRegla(regla, propuesta) : crearRegla(propuesta);
    },
    onSuccess: () => {
      refrescarHorario(queryClient);
      onGuardada(!regla);
      onCerrar();
    },
  });

  // Los errores de solape los redacta el backend; aquí se muestran tal cual.
  const error = guardar.error ? mensajeDe(guardar.error) : null;
  const minutos = aMinutos(fin) - aMinutos(inicio);
  const cupos = cuposDeTramo(minutos, duracionClase);

  return (
    <Modal titulo={`${regla ? "Editar franja" : "Nueva franja"} · ${nombreDelDia(weekday)}`} onCerrar={onCerrar}>
      <div className="flex items-center gap-2.5">
        <label className="flex-1 text-[12.5px] font-bold text-text-secondary">
          Desde
          <select
            value={inicio}
            onChange={(event) => {
              setInicio(event.target.value);
              // Como en un calendario: si el fin queda antes del nuevo inicio, se corre con él.
              if (aMinutos(fin) <= aMinutos(event.target.value)) setFin(siguienteHora(event.target.value));
            }}
            className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 py-3 text-sm font-semibold text-text"
          >
            {HORAS.map((hora) => (
              <option key={hora} value={hora}>
                {hora12(hora)}
              </option>
            ))}
          </select>
        </label>
        <label className="flex-1 text-[12.5px] font-bold text-text-secondary">
          Hasta
          <select
            value={fin}
            onChange={(event) => setFin(event.target.value)}
            className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 py-3 text-sm font-semibold text-text"
          >
            {HORAS.map((hora) => (
              <option key={hora} value={hora}>
                {hora12(hora)}
              </option>
            ))}
          </select>
        </label>
      </div>

      <p className="mt-3 text-[12.5px] text-text-secondary">
        {minutos <= 0
          ? "La hora de fin tiene que ser después de la de inicio."
          : cupos === 0
            ? `Una clase dura ${duracionClase} minutos: esta franja todavía no alcanza para ninguna.`
            : `Abre ${cupos} ${cupos === 1 ? "cupo" : "cupos"} de clase cada ${nombreDelDia(weekday).toLowerCase()}: empiezan cada media hora.`}
      </p>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}

      {onEliminar && (
        <button
          type="button"
          onClick={onEliminar}
          className="mt-4 text-[13px] font-bold text-error underline-offset-2 hover:underline"
        >
          Eliminar esta franja
        </button>
      )}

      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Cancelar
        </Boton>
        <Boton
          variante="primario"
          disabled={guardar.isPending || minutos <= 0}
          onClick={() => guardar.mutate()}
          className="h-11 flex-1"
        >
          {guardar.isPending ? "Guardando…" : regla ? "Guardar cambios" : "Añadir franja"}
        </Boton>
      </div>
    </Modal>
  );
}

function ModalBloquearFecha({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const { classMinutes } = useCifras();
  // Hoy en Bogotá, no en la zona del computador: es el primer día que se puede bloquear.
  const [hoy] = useState(() => diaBogota(new Date().toISOString()));
  const [fecha, setFecha] = useState("");
  const [todoElDia, setTodoElDia] = useState(true);
  const [inicio, setInicio] = useState("09:00");
  const [fin, setFin] = useState("10:00");
  const [motivo, setMotivo] = useState("");

  // La misma consulta que «Mis clases» (misma clave): si ya se abrió, no se vuelve a pedir.
  const clases = useQuery({
    queryKey: ["me", "bookings", "upcoming"],
    queryFn: () => apiFetch<MyBookingResponse[]>("/api/v1/me/bookings?scope=upcoming"),
  });

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<ExceptionResponse>("/api/v1/me/availability/exceptions", {
        method: "POST",
        body: {
          date: fecha,
          startTime: todoElDia ? undefined : inicio,
          endTime: todoElDia ? undefined : fin,
          reason: motivo.trim() || undefined,
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "exceptions"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
  });

  const error = crear.error instanceof ApiError ? crear.error.message : null;
  const pasada = fecha !== "" && fecha < hoy;

  // Las clases confirmadas que caen en lo que se va a bloquear. Bloquear no las cancela: solo deja
  // de ofrecer cupos nuevos. Si el profesor no puede darlas, tiene que cancelarlas él.
  const afectadas = (clases.data ?? []).filter((clase) => {
    if (clase.status !== "CONFIRMED" || !clase.startsAt || diaBogota(clase.startsAt) !== fecha) return false;
    if (todoElDia) return true;
    const empieza = minutoDelDiaBogota(clase.startsAt);
    const termina = empieza + classMinutes;
    return empieza < aMinutos(fin) && aMinutos(inicio) < termina;
  });

  return (
    <Modal titulo="Bloquear una fecha" onCerrar={onCerrar}>
      <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="fecha">
        Fecha
      </label>
      <Campo
        id="fecha"
        type="date"
        min={hoy}
        value={fecha}
        onChange={(event) => setFecha(event.target.value)}
        aria-invalid={pasada || undefined}
        aria-describedby={pasada ? "fecha-pasada" : undefined}
        className="mt-1.5"
      />
      {pasada && (
        <p id="fecha-pasada" className="mt-1.5 text-[12.5px] font-semibold text-error">
          Esa fecha ya pasó: elige hoy o un día que venga.
        </p>
      )}

      <label className="mt-4 flex items-center justify-between">
        <span className="text-[13.5px] font-semibold">Todo el día</span>
        <input
          type="checkbox"
          checked={todoElDia}
          onChange={(event) => setTodoElDia(event.target.checked)}
          className="h-5 w-5 accent-[var(--color-accent)]"
        />
      </label>

      {!todoElDia && (
        <div className="mt-3 flex items-center gap-2.5">
          {/* Selects y no <input type="time">: ese control lo pinta el sistema operativo con SU
              formato —que no controlamos— y además deja escribir 18:37, cuando aquí todo va en punto
              o a la media hora. */}
          <label className="flex-1 text-[12.5px] font-bold text-text-secondary">
            Desde
            <select
              value={inicio}
              onChange={(event) => setInicio(event.target.value)}
              className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 py-3 text-sm font-semibold text-text"
            >
              {HORAS.map((hora) => (
                <option key={hora} value={hora}>
                  {hora12(hora)}
                </option>
              ))}
            </select>
          </label>
          <label className="flex-1 text-[12.5px] font-bold text-text-secondary">
            Hasta
            <select
              value={fin}
              onChange={(event) => setFin(event.target.value)}
              className="mt-1.5 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 py-3 text-sm font-semibold text-text"
            >
              {HORAS.map((hora) => (
                <option key={hora} value={hora}>
                  {hora12(hora)}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}

      {afectadas.length > 0 && (
        <div role="status" className="mt-4 rounded-base bg-warning-bg px-4 py-3 text-[13px] text-warning">
          <p className="flex items-start gap-2 font-bold">
            <TriangleAlert size={16} strokeWidth={2} className="mt-px shrink-0" />
            {afectadas.length === 1
              ? `${todoElDia ? "Ese día" : "En esas horas"} tienes una clase confirmada`
              : `${todoElDia ? "Ese día" : "En esas horas"} tienes ${afectadas.length} clases confirmadas`}
          </p>
          <ul className="mt-2 space-y-1 pl-6 text-[12.5px] font-semibold text-text">
            {afectadas.map((clase) => (
              <li key={clase.id}>
                {rangoHoras(clase.startsAt!, clase.endsAt ?? finDeClase(clase.startsAt!, classMinutes))}
                {clase.counterpart?.fullName ? ` · ${clase.counterpart.fullName}` : ""}
              </li>
            ))}
          </ul>
          <p className="mt-2 pl-6 text-[12.5px]">
            {afectadas.length === 1
              ? "Esta clase sigue en pie: si no puedes darla, cancélala desde "
              : "Estas clases siguen en pie: si no puedes darlas, cancélalas desde "}
            <Link href="/mis-clases" className="font-bold underline underline-offset-2">
              Mis clases
            </Link>
            .
          </p>
        </div>
      )}

      <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="motivo-bloqueo">
        Motivo (opcional)
      </label>
      <Campo
        id="motivo-bloqueo"
        type="text"
        maxLength={200}
        value={motivo}
        onChange={(event) => setMotivo(event.target.value)}
        className="mt-1.5"
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
          variante="primario"
          disabled={!fecha || pasada || crear.isPending}
          onClick={() => crear.mutate()}
          className="h-11 flex-1"
        >
          {crear.isPending ? "Guardando…" : "Bloquear"}
        </Boton>
      </div>
    </Modal>
  );
}

/* ---- Llamadas y utilidades ---- */

function crearRegla(propuesta: Propuesta): Promise<RuleResponse> {
  return apiFetch<RuleResponse>("/api/v1/me/availability/rules", {
    method: "POST",
    body: { weekday: propuesta.weekday, startTime: aHhmm(propuesta.inicio), endTime: aHhmm(propuesta.fin) },
  });
}

/**
 * Cambiar una franja es borrarla y crearla de nuevo: el backend no tiene un «editar». Tiene que ser
 * en ese orden —creando primero, la nueva se cruzaría con la vieja—, así que si la nueva falla se
 * vuelve a crear la de antes, para que un error no le cueste al profesor la franja que tenía.
 */
async function reemplazarRegla(regla: RuleResponse, propuesta: Propuesta): Promise<RuleResponse> {
  await apiFetch(`/api/v1/me/availability/rules/${regla.id}`, { method: "DELETE" });
  try {
    return await crearRegla(propuesta);
  } catch (error) {
    try {
      await crearRegla(propuestaDe(regla));
    } catch {
      throw new Error(
        `No pudimos cambiar la franja y tampoco dejarla como estaba (${diaYRango(propuestaDe(regla))}). Revisa tu horario y vuelve a abrirla.`,
      );
    }
    throw error;
  }
}

/** Cambia lo que ven los estudiantes: además de las franjas, se refrescan los cupos y lo que falta del perfil. */
function refrescarHorario(queryClient: ReturnType<typeof useQueryClient>) {
  void queryClient.invalidateQueries({ queryKey: CLAVE_REGLAS });
  void queryClient.invalidateQueries({ queryKey: ["me", "profile", "pending"] });
  void queryClient.invalidateQueries({ queryKey: ["slots"] });
}

function aFranjas(reglas: RuleResponse[]): FranjaDia[] {
  return reglas.map(propuestaDe);
}

function propuestaDe(regla: RuleResponse): Propuesta {
  return { weekday: regla.weekday!, inicio: aMinutos(regla.startTime!), fin: aMinutos(regla.endTime!) };
}

/** «martes, 6:00 – 8:30 PM». */
function diaYRango(propuesta: Propuesta): string {
  return `${nombreDelDia(propuesta.weekday).toLowerCase()}, ${rangoLargo(propuesta.inicio, propuesta.fin)}`;
}

function nombreDelDia(weekday: number): string {
  return DIAS.find((dia) => dia.valor === weekday)!.nombre;
}

function mensajeDe(error: unknown): string {
  if (error instanceof ApiError || error instanceof Error) return error.message;
  return "No pudimos guardar el cambio. Inténtalo de nuevo.";
}

/**
 * Una hora después, conservando los minutos. Es el fin por defecto de una franja nueva, y una hora
 * es el mínimo que sirve: la clase dura 55, así que media hora no deja caber ninguna. Truncar los
 * minutos —que es lo que hacía antes— convertía «desde las 17:30» en una franja de 30 minutos que
 * no producía un solo cupo.
 */
function siguienteHora(hhmm: string): string {
  return aHhmm(Math.min(FIN_DEL_DIA, aMinutos(hhmm) + 60));
}

/** El backend manda "18:00:00"; en pantalla sobra el segundero. */
function corta(hora?: string): string {
  return (hora ?? "").slice(0, 5);
}
