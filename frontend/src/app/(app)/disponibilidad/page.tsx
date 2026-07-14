"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { ExceptionResponse, RuleResponse } from "@/lib/api/types";
import { fechaLarga } from "@/lib/format";

/** ISO 1–7, igual que el backend: 1 = lunes … 7 = domingo. */
const DIAS = [
  { valor: 1, nombre: "Lunes" },
  { valor: 2, nombre: "Martes" },
  { valor: 3, nombre: "Miércoles" },
  { valor: 4, nombre: "Jueves" },
  { valor: 5, nombre: "Viernes" },
  { valor: 6, nombre: "Sábado" },
  { valor: 7, nombre: "Domingo" },
];

/** Las franjas empiezan y terminan en punto: la regla la impone el backend, aquí solo se refleja. */
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

  const [diaNuevaFranja, setDiaNuevaFranja] = useState<number | null>(null);
  const [bloqueando, setBloqueando] = useState(false);

  if (reglas.isPending || excepciones.isPending) {
    return (
      <main className="mx-auto max-w-md p-4">
        <Cargando filas={4} />
      </main>
    );
  }

  if (reglas.isError) {
    return (
      <main className="mx-auto max-w-md p-4">
        <ErrorCarga
          mensaje="No pudimos cargar tu disponibilidad."
          onReintentar={() => void reglas.refetch()}
        />
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-md p-4">
      <h1 className="text-xl font-semibold">Mi disponibilidad</h1>
      <p className="mt-0.5 text-xs text-ink-muted">Horario semanal recurrente · hora de Bogotá</p>

      <div className="mt-3.5">
        {DIAS.map((dia) => {
          const delDia = (reglas.data ?? []).filter((regla) => regla.weekday === dia.valor);
          return (
            <div key={dia.valor} className="border-b border-line py-2.5">
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold">{dia.nombre}</span>
                <button
                  type="button"
                  aria-label={`Añadir franja el ${dia.nombre.toLowerCase()}`}
                  onClick={() => setDiaNuevaFranja(dia.valor)}
                  className="px-2 text-ink-muted"
                >
                  +
                </button>
              </div>

              {delDia.length === 0 ? (
                <p className="mt-2 text-xs text-ink-muted">Sin franjas — toca + para añadir</p>
              ) : (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {delDia.map((regla) => (
                    <ChipFranja key={regla.id} regla={regla} />
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <p className="mt-4 text-xs font-semibold text-ink-soft">Fechas bloqueadas</p>

      {(excepciones.data ?? []).length === 0 ? (
        <p className="mt-1.5 text-xs text-ink-muted">
          Ninguna por ahora. Bloquea un día cuando no puedas dar clases.
        </p>
      ) : (
        <ul className="mt-1.5 space-y-2">
          {excepciones.data!.map((excepcion) => (
            <FilaExcepcion key={excepcion.id} excepcion={excepcion} />
          ))}
        </ul>
      )}

      <button
        type="button"
        onClick={() => setBloqueando(true)}
        className="mt-2.5 w-full rounded-orion border border-line py-2 text-sm text-ink"
      >
        Bloquear una fecha
      </button>

      {diaNuevaFranja !== null && (
        <ModalNuevaFranja weekday={diaNuevaFranja} onCerrar={() => setDiaNuevaFranja(null)} />
      )}
      {bloqueando && <ModalBloquearFecha onCerrar={() => setBloqueando(false)} />}
    </main>
  );
}

function ChipFranja({ regla }: { regla: RuleResponse }) {
  const queryClient = useQueryClient();
  const [confirmando, setConfirmando] = useState(false);

  const borrar = useMutation({
    mutationFn: () => apiFetch(`/api/v1/me/availability/rules/${regla.id}`, { method: "DELETE" }),
    onSuccess: () => {
      // Cambia lo que ven los estudiantes: hay que refrescar también los cupos.
      void queryClient.invalidateQueries({ queryKey: ["me", "rules"] });
      void queryClient.invalidateQueries({ queryKey: ["slots"] });
      setConfirmando(false);
    },
  });

  const franja = `${corta(regla.startTime)}–${corta(regla.endTime)}`;

  return (
    <>
      <span className="inline-flex items-center gap-1.5 rounded-orion bg-accent-soft px-2.5 py-1.5 text-xs font-semibold text-accent-ink">
        {franja}
        <button
          type="button"
          aria-label={`Eliminar la franja ${franja}`}
          onClick={() => setConfirmando(true)}
          className="text-accent-ink/70"
        >
          ✕
        </button>
      </span>

      {confirmando && (
        <Modal titulo="¿Eliminar esta franja?" onCerrar={() => setConfirmando(false)}>
          <p className="text-sm text-ink-soft">
            {franja}. Los estudiantes dejarán de ver estos cupos.
          </p>
          <div className="mt-4 flex gap-2">
            <button
              type="button"
              onClick={() => setConfirmando(false)}
              className="flex-1 rounded-orion border border-line py-2 text-sm text-ink-soft"
            >
              Volver
            </button>
            <button
              type="button"
              disabled={borrar.isPending}
              onClick={() => borrar.mutate()}
              className="flex-1 rounded-orion bg-accent py-2 text-sm font-semibold text-white disabled:opacity-60"
            >
              Eliminar
            </button>
          </div>
        </Modal>
      )}
    </>
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
    <li className="flex items-center justify-between rounded-orion border border-line px-2.5 py-2">
      <span className="text-xs">
        {/* La fecha llega como YYYY-MM-DD; el mediodía evita que la zona la corra un día. */}
        {fechaLarga(`${excepcion.date}T12:00:00-05:00`)} · {cuando}
        {excepcion.reason ? ` · ${excepcion.reason}` : ""}
      </span>
      <button
        type="button"
        aria-label="Eliminar bloqueo"
        disabled={borrar.isPending}
        onClick={() => borrar.mutate()}
        className="pl-2 text-ink-muted"
      >
        ✕
      </button>
    </li>
  );
}

function ModalNuevaFranja({ weekday, onCerrar }: { weekday: number; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const [inicio, setInicio] = useState("08:00");
  const [fin, setFin] = useState("11:00");

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
      <div className="flex items-center gap-2">
        <label className="flex-1 text-xs font-semibold text-ink-soft">
          Desde
          <select
            value={inicio}
            onChange={(event) => setInicio(event.target.value)}
            className="mt-1 w-full rounded-orion border border-line bg-card px-2 py-2 text-sm"
          >
            {HORAS.map((hora) => (
              <option key={hora} value={hora}>
                {hora}
              </option>
            ))}
          </select>
        </label>
        <label className="flex-1 text-xs font-semibold text-ink-soft">
          Hasta
          <select
            value={fin}
            onChange={(event) => setFin(event.target.value)}
            className="mt-1 w-full rounded-orion border border-line bg-card px-2 py-2 text-sm"
          >
            {HORAS.map((hora) => (
              <option key={hora} value={hora}>
                {hora}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && (
        <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={onCerrar}
          className="flex-1 rounded-orion border border-line py-2 text-sm text-ink-soft"
        >
          Volver
        </button>
        <button
          type="button"
          disabled={crear.isPending}
          onClick={() => crear.mutate()}
          className="flex-1 rounded-orion bg-accent py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {crear.isPending ? "Guardando…" : "Añadir"}
        </button>
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
      <label className="block text-xs font-semibold text-ink-soft" htmlFor="fecha">
        Fecha
      </label>
      <input
        id="fecha"
        type="date"
        value={fecha}
        onChange={(event) => setFecha(event.target.value)}
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm"
      />

      <label className="mt-3 flex items-center justify-between">
        <span className="text-sm">Todo el día</span>
        <input
          type="checkbox"
          checked={todoElDia}
          onChange={(event) => setTodoElDia(event.target.checked)}
          className="h-4 w-4 accent-[var(--color-accent)]"
        />
      </label>

      {!todoElDia && (
        <div className="mt-3 flex items-center gap-2">
          <label className="flex-1 text-xs font-semibold text-ink-soft">
            Desde
            <input
              type="time"
              value={inicio}
              onChange={(event) => setInicio(event.target.value)}
              className="mt-1 w-full rounded-orion border border-line bg-card px-2 py-2 text-sm"
            />
          </label>
          <label className="flex-1 text-xs font-semibold text-ink-soft">
            Hasta
            <input
              type="time"
              value={fin}
              onChange={(event) => setFin(event.target.value)}
              className="mt-1 w-full rounded-orion border border-line bg-card px-2 py-2 text-sm"
            />
          </label>
        </div>
      )}

      <label className="mt-3 block text-xs font-semibold text-ink-soft" htmlFor="motivo-bloqueo">
        Motivo (opcional)
      </label>
      <input
        id="motivo-bloqueo"
        type="text"
        maxLength={200}
        value={motivo}
        onChange={(event) => setMotivo(event.target.value)}
        className="mt-1 w-full rounded-orion border border-line bg-card px-3 py-2 text-sm"
      />

      {error && (
        <p className="mt-3 rounded-orion bg-danger-soft px-3 py-2 text-sm text-danger">{error}</p>
      )}

      <div className="mt-4 flex gap-2">
        <button
          type="button"
          onClick={onCerrar}
          className="flex-1 rounded-orion border border-line py-2 text-sm text-ink-soft"
        >
          Volver
        </button>
        <button
          type="button"
          disabled={!fecha || crear.isPending}
          onClick={() => crear.mutate()}
          className="flex-1 rounded-orion bg-accent py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          {crear.isPending ? "Guardando…" : "Bloquear"}
        </button>
      </div>
    </Modal>
  );
}

/** El backend manda "18:00:00"; en pantalla sobra el segundero. */
function corta(hora?: string): string {
  return (hora ?? "").slice(0, 5);
}
