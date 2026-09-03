"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarOff, Clock, Plus, X } from "lucide-react";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { CalendarioSemanal } from "@/components/CalendarioSemanal";
import { Bloque, Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { ExceptionResponse, RuleResponse } from "@/lib/api/types";
import { fechaLarga, hora12, rangoCompacto } from "@/lib/format";

/** ISO 1–7, igual que el backend: 1 = lunes … 7 = domingo. */
const DIAS = [
  { valor: 1, nombre: "Lunes", corto: "Lun" },
  { valor: 2, nombre: "Martes", corto: "Mar" },
  { valor: 3, nombre: "Miércoles", corto: "Mié" },
  { valor: 4, nombre: "Jueves", corto: "Jue" },
  { valor: 5, nombre: "Viernes", corto: "Vie" },
  { valor: 6, nombre: "Sábado", corto: "Sáb" },
  { valor: 7, nombre: "Domingo", corto: "Dom" },
];

/** Las franjas empiezan y terminan en punto: la regla la impone el backend, aquí solo se refleja. */
/**
 * Las 24 horas en punto. El VALOR sigue siendo "18:00", que es lo que entiende el backend; lo que
 * cambia es la etiqueta que lee el profesor, en formato de 12 horas.
 */
const HORAS = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, "0")}:00`);

export default function DisponibilidadPage() {
  const reglas = useQuery({
    queryKey: ["me", "rules"],
    queryFn: () => apiFetch<RuleResponse[]>("/api/v1/me/availability/rules"),
  });

  const excepciones = useQuery({
    queryKey: ["me", "exceptions"],
    queryFn: () => apiFetch<ExceptionResponse[]>("/api/v1/me/availability/exceptions"),
  });

  const [nuevaFranja, setNuevaFranja] = useState<{ weekday: number; desde: string } | null>(null);
  const [franjaAEliminar, setFranjaAEliminar] = useState<RuleResponse | null>(null);
  const [bloqueando, setBloqueando] = useState(false);

  if (reglas.isPending || excepciones.isPending) {
    return (
      <main className="px-5 py-5">
        <Cargando filas={4} />
      </main>
    );
  }

  if (reglas.isError) {
    return (
      <main className="px-5 py-5">
        <ErrorCarga
          mensaje="No pudimos cargar tu disponibilidad."
          onReintentar={() => void reglas.refetch()}
        />
      </main>
    );
  }

  return (
    <main className="mx-auto w-full max-w-md px-5 py-5 lg:max-w-[1180px] lg:px-12 lg:py-8">
      <div>
        <h1 className="font-display text-h1 font-bold">Mi disponibilidad</h1>
        <p className="mt-1 flex items-center gap-1.5 text-[12.5px] text-text-secondary">
          <Clock size={14} strokeWidth={1.75} />
          Horario semanal recurrente · hora de Bogotá
        </p>
      </div>

      <div className="mt-4 lg:grid lg:grid-cols-[1fr_260px] lg:items-start lg:gap-6">
        {/* En escritorio, el horario se dibuja como lo que es: una rejilla de horas por días, donde
            la duración de una franja es su altura. En móvil no cabe una rejilla de siete columnas,
            así que ahí siguen las tarjetas apiladas, un día debajo de otro. */}
        <div className="hidden lg:block">
          <CalendarioSemanal
            reglas={reglas.data ?? []}
            onAnadir={(weekday, desde) => setNuevaFranja({ weekday, desde })}
            onEliminar={setFranjaAEliminar}
          />
        </div>

        <div className="grid gap-2.5 lg:hidden">
          {DIAS.map((dia) => {
            const delDia = (reglas.data ?? []).filter((regla) => regla.weekday === dia.valor);
            return (
              <div key={dia.valor} className="rounded-card bg-info-bg p-3">
                <div className="flex items-center justify-between gap-1">
                  <span className="text-[13.5px] font-bold text-info">{dia.nombre}</span>
                  <button
                    type="button"
                    aria-label={`Añadir franja el ${dia.nombre.toLowerCase()}`}
                    onClick={() => setNuevaFranja({ weekday: dia.valor, desde: "08:00" })}
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
                      <ChipFranja
                        key={regla.id}
                        regla={regla}
                        onEliminar={() => setFranjaAEliminar(regla)}
                      />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Fechas bloqueadas: al costado en desktop, debajo en móvil. */}
        <aside className="mt-3 lg:mt-0">
          <Bloque
            tono="melocoton"
            titulo="Fechas bloqueadas"
            icono={<CalendarOff size={16} strokeWidth={1.75} />}
          >
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

      {nuevaFranja && (
        <ModalNuevaFranja
          weekday={nuevaFranja.weekday}
          desde={nuevaFranja.desde}
          onCerrar={() => setNuevaFranja(null)}
        />
      )}

      {franjaAEliminar && (
        <ModalEliminarFranja
          regla={franjaAEliminar}
          onCerrar={() => setFranjaAEliminar(null)}
        />
      )}
      {bloqueando && <ModalBloquearFecha onCerrar={() => setBloqueando(false)} />}
    </main>
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
      void queryClient.invalidateQueries({ queryKey: ["me", "rules"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
  });

  const franja = `${corta(regla.startTime)}–${corta(regla.endTime)}`;

  return (
    <Modal titulo="¿Eliminar esta franja?" onCerrar={onCerrar}>
      <p className="text-[13px] text-text-secondary">
        {franja}. Los estudiantes dejarán de ver estos cupos.
      </p>
      <div className="mt-5 flex gap-2.5">
        <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
          Volver
        </Boton>
        <Boton
          variante="peligro"
          disabled={borrar.isPending}
          onClick={() => borrar.mutate()}
          className="h-11 flex-1"
        >
          Eliminar
        </Boton>
      </div>
    </Modal>
  );
}

function FilaExcepcion({ excepcion }: { excepcion: ExceptionResponse }) {
  const queryClient = useQueryClient();

  const borrar = useMutation({
    mutationFn: () =>
      apiFetch(`/api/v1/me/availability/exceptions/${excepcion.id}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "exceptions"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
    },
  });

  const cuando = excepcion.startTime
    ? `${corta(excepcion.startTime)}–${corta(excepcion.endTime)}`
    : "todo el día";

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

function ModalNuevaFranja({
  weekday,
  desde,
  onCerrar,
}: {
  weekday: number;
  desde: string;
  onCerrar: () => void;
}) {
  const queryClient = useQueryClient();
  const [inicio, setInicio] = useState(desde);
  // Una hora después de donde se pulsó: la clase mínima. Ampliarla es un clic en el select.
  const [fin, setFin] = useState(siguienteHora(desde));

  const crear = useMutation({
    mutationFn: () =>
      apiFetch<RuleResponse>("/api/v1/me/availability/rules", {
        method: "POST",
        body: { weekday, startTime: inicio, endTime: fin },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["me", "rules"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      onCerrar();
    },
  });

  // Los errores de solape los redacta el backend; aquí se muestran tal cual.
  const error = crear.error instanceof ApiError ? crear.error.message : null;
  const nombreDia = DIAS.find((dia) => dia.valor === weekday)!.nombre;

  return (
    <Modal titulo={`Nueva franja · ${nombreDia}`} onCerrar={onCerrar}>
      <div className="flex items-center gap-2.5">
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
          disabled={crear.isPending}
          onClick={() => crear.mutate()}
          className="h-11 flex-1"
        >
          {crear.isPending ? "Guardando…" : "Añadir franja"}
        </Boton>
      </div>
    </Modal>
  );
}

function ModalBloquearFecha({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [fecha, setFecha] = useState("");
  const [todoElDia, setTodoElDia] = useState(true);
  const [inicio, setInicio] = useState("09:00");
  const [fin, setFin] = useState("10:00");
  const [motivo, setMotivo] = useState("");

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

  return (
    <Modal titulo="Bloquear una fecha" onCerrar={onCerrar}>
      <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="fecha">
        Fecha
      </label>
      <Campo
        id="fecha"
        type="date"
        value={fecha}
        onChange={(event) => setFecha(event.target.value)}
        className="mt-1.5"
      />

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
              formato —que no controlamos— y además deja escribir 18:37, cuando aquí las clases
              siempre empiezan en punto. */}
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

      <label
        className="mt-4 block text-[12.5px] font-bold text-text-secondary"
        htmlFor="motivo-bloqueo"
      >
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
          disabled={!fecha || crear.isPending}
          onClick={() => crear.mutate()}
          className="h-11 flex-1"
        >
          {crear.isPending ? "Guardando…" : "Bloquear"}
        </Boton>
      </div>
    </Modal>
  );
}

/** El backend manda "18:00:00"; en pantalla sobra el segundero. */
/** "19:00" → "20:00". Tope en las 23:00, que es la última hora en la que puede empezar una clase. */
function siguienteHora(hhmm: string): string {
  const h = Math.min(23, Number(hhmm.slice(0, 2)) + 1);
  return `${String(h).padStart(2, "0")}:00`;
}

function corta(hora?: string): string {
  return (hora ?? "").slice(0, 5);
}

/** "18:00" → "6 PM"; "18:30" → "6:30 PM". Para la grilla estrecha de días en desktop. */

