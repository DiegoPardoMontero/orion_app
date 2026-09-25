"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarRange, Sparkles } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { BotonPurga } from "@/components/Purga";
import { tablaAdmin as t } from "@/components/tablaAdmin";
import { Badge, Campo } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { AdminBookingResponse, AdminUserResponse, MetricsResponse } from "@/lib/api/types";
import { etiquetaEstado } from "@/lib/estados-clase";
import { fechaYRango } from "@/lib/format";

const ESTADOS = [
  { valor: "", etiqueta: "Todas" },
  { valor: "CONFIRMED", etiqueta: "Confirmadas" },
  { valor: "CANCELLED_BY_STUDENT", etiqueta: "Canceladas" },
  { valor: "COMPLETED", etiqueta: "Completadas" },
] as const;

export default function AdminReservasPage() {
  const [estado, setEstado] = useState("");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [profesorId, setProfesorId] = useState("");

  const params = new URLSearchParams();
  if (estado) params.set("status", estado);
  if (desde) params.set("from", desde);
  if (hasta) params.set("to", hasta);
  if (profesorId) params.set("professorId", profesorId);
  const query = params.toString();

  const metricas = useQuery({
    queryKey: ["admin", "metrics"],
    queryFn: () => apiFetch<MetricsResponse>("/api/v1/admin/metrics"),
  });

  const profesores = useQuery({
    queryKey: ["admin", "users", "PROFESSOR", ""],
    queryFn: () => apiFetch<AdminUserResponse[]>("/api/v1/admin/users?role=PROFESSOR"),
  });

  const reservas = useQuery({
    queryKey: ["admin", "bookings", query],
    queryFn: () =>
      apiFetch<AdminBookingResponse[]>(`/api/v1/admin/bookings${query ? `?${query}` : ""}`),
  });

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Reservas</h1>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Metrica
          tono="lavanda"
          icono={<CalendarRange size={18} strokeWidth={2.2} />}
          cifra={metricas.data ? String(metricas.data.bookingsLast7Days) : "—"}
          etiqueta="Reservas en los últimos 7 días"
        />
        <Metrica
          tono="melocoton"
          icono={<Sparkles size={18} strokeWidth={2.2} />}
          cifra={metricas.data ? `${Math.round(metricas.data.selfServicePctAllTime ?? 0)}%` : "—"}
          etiqueta="Autoservicio (histórico)"
        />
      </div>

      <div className="mt-5 flex flex-wrap items-end gap-2">
        <label className="text-[12px] font-bold text-text-secondary">
          Desde
          <Campo
            type="date"
            value={desde}
            onChange={(event) => setDesde(event.target.value)}
            className="mt-1"
          />
        </label>
        <label className="text-[12px] font-bold text-text-secondary">
          Hasta
          <Campo
            type="date"
            value={hasta}
            onChange={(event) => setHasta(event.target.value)}
            className="mt-1"
          />
        </label>
        <label className="min-w-[180px] flex-1 text-[12px] font-bold text-text-secondary">
          Profesor
          <select
            value={profesorId}
            onChange={(event) => setProfesorId(event.target.value)}
            className="mt-1 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-4 py-3 text-[13px] font-semibold"
          >
            <option value="">Todos</option>
            {(profesores.data ?? []).map((profesor) => (
              <option key={profesor.id} value={profesor.id}>
                {profesor.fullName}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        {ESTADOS.map((opcion) => (
          <button
            key={opcion.valor || "todas"}
            type="button"
            onClick={() => setEstado(opcion.valor)}
            className={`rounded-base px-3.5 py-2 text-[12.5px] transition-colors ${
              estado === opcion.valor
                ? "bg-night font-bold text-on-primary"
                : "border-[1.5px] border-border bg-surface-raised font-semibold text-text-secondary hover:bg-surface-sunken"
            }`}
          >
            {opcion.etiqueta}
          </button>
        ))}
      </div>

      <div className="mt-5">
        {reservas.isPending && <Cargando filas={4} />}

        {reservas.isError && (
          <ErrorCarga
            mensaje="No pudimos cargar las reservas."
            onReintentar={() => void reservas.refetch()}
          />
        )}

        {reservas.data?.length === 0 && (
          <Vacio
            titulo="Sin reservas"
            texto="Cuando los estudiantes empiecen a agendar, las verás aquí."
          />
        )}

        {!!reservas.data?.length && (
          <div className={t.contenedor}>
            <table className={t.tabla}>
              <thead className={t.cabecera}>
                <tr className={t.filaCabecera}>
                  <th className={t.th}>Cuándo</th>
                  <th className={t.th}>Estudiante y profesor</th>
                  <th className={t.th}>Estado</th>
                  <th className={t.th}>Autoservicio</th>
                  <th className={`${t.th} text-right`}>Limpieza</th>
                </tr>
              </thead>
              <tbody className={t.cuerpo}>
                {reservas.data.map((reserva) => (
                  <tr key={reserva.id} className={t.fila}>
                    <td className={`${t.celda} font-semibold`}>
                      {fechaYRango(reserva.startsAt!, reserva.endsAt!)}
                    </td>
                    <td className={t.celda}>
                      <span className="block">
                        <span className="text-text-muted">Estudiante: </span>
                        {reserva.studentName}
                      </span>
                      <span className="block">
                        <span className="text-text-muted">Profesor: </span>
                        {reserva.professorName}
                      </span>
                    </td>
                    <td className={t.celda}>
                      {/* Sin columna de modalidad: desde la V30 todas las clases son virtuales. */}
                      <Badge tono={tonoEstado(reserva.status)}>{etiquetaEstado(reserva.status)}</Badge>
                    </td>
                    <td className={`${t.celda} text-text-secondary`}>
                      <span className="lg:hidden">Autoservicio: </span>
                      {reserva.selfService ? "Sí" : "No"}
                    </td>
                    <td className={t.acciones}>
                      {/* Borrado DEFINITIVO, en cualquier estado. Para limpiar datos de prueba
                          antes de abrir al público; el modal enseña qué se lleva por delante. */}
                      <BotonPurga tipo="booking" id={reserva.id!} etiqueta="Borrar" soloIcono />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Sin paginación formal en el MVP: si el tope se alcanza, se dice. */}
        {reservas.data?.length === 200 && (
          <p className="mt-2 text-[11.5px] text-text-muted">
            Mostrando las 200 más recientes. Afina los filtros para ver el resto.
          </p>
        )}
      </div>
    </main>
  );
}

function tonoEstado(estado?: string) {
  if (estado === "COMPLETED") return "menta" as const;
  if (estado === "NO_SHOW") return "melocoton" as const;
  if (estado?.startsWith("CANCELLED")) return "error" as const;
  return "lavanda" as const;
}

function Metrica({
  tono,
  icono,
  cifra,
  etiqueta,
}: {
  tono: "lavanda" | "melocoton";
  icono: ReactNode;
  cifra: string;
  etiqueta: string;
}) {
  const fondo = tono === "lavanda" ? "bg-info-bg text-info" : "bg-warning-bg text-warning";

  return (
    <div className={`flex items-center gap-4 rounded-card p-5 ${fondo}`}>
      <span className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-white">
        {icono}
      </span>
      <div>
        <p className="text-[24px] font-extrabold leading-none">{cifra}</p>
        <p className="mt-1 text-[12.5px] font-semibold">{etiqueta}</p>
      </div>
    </div>
  );
}
