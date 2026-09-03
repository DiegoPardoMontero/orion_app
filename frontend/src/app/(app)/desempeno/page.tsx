"use client";

import { useQuery } from "@tanstack/react-query";
import { AlertTriangle, BarChart3, CalendarCheck, Star, TrendingUp, Users } from "lucide-react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Badge, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { PerformanceResponse } from "@/lib/api/types";
import { fechaCorta } from "@/lib/format";

const SANCION: Record<string, string> = {
  WARNING: "Aviso",
  VISIBILITY_REDUCED: "Menos visibilidad",
  BOOKINGS_SUSPENDED: "Sin reservas nuevas",
  PROFILE_HIDDEN: "Perfil oculto",
  ACCOUNT_SUSPENDED: "Cuenta suspendida",
};

/**
 * "Mi desempeño". Existe para que ninguna sanción sea invisible: una caída de ingresos sin
 * explicación no corrige a nadie, solo hace que la persona se vaya sin entender por qué.
 */
export default function DesempenoPage() {
  const datos = useQuery({
    queryKey: ["me", "performance"],
    queryFn: () => apiFetch<PerformanceResponse>("/api/v1/me/performance"),
  });

  if (datos.isPending) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-6">
        <Cargando filas={4} />
      </main>
    );
  }
  if (datos.isError) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-6">
        <ErrorCarga mensaje="No pudimos cargar tu desempeño." onReintentar={() => void datos.refetch()} />
      </main>
    );
  }

  const d = datos.data;
  const activas = d.sanctions.filter((s) => s.state === "ACTIVE");
  const propuestas = d.sanctions.filter((s) => s.state === "PROPOSED");
  const sinDatos = d.lessonsCompleted === 0 && d.ratingCount === 0;

  return (
    <main className="mx-auto max-w-3xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Mi desempeño</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Sobre tus últimos {d.windowDays} días. Se recalcula cada noche.
      </p>

      {activas.length > 0 && (
        <div className="mt-4 rounded-card bg-warning-bg px-5 py-4">
          <p className="flex items-center gap-2 font-bold text-warning">
            <AlertTriangle size={18} strokeWidth={2.2} />
            {activas.length === 1 ? "Tienes una restricción activa" : "Tienes restricciones activas"}
          </p>
          {activas.map((sancion) => (
            <div key={sancion.id} className="mt-2 text-[13px] text-warning">
              <p className="font-semibold">{SANCION[sancion.type] ?? sancion.type}</p>
              <p>{sancion.reason}</p>
              {sancion.endsAt && <p className="mt-0.5">Hasta el {fechaCorta(sancion.endsAt)}.</p>}
            </div>
          ))}
        </div>
      )}

      {propuestas.length > 0 && (
        <div className="mt-4 rounded-card bg-info-bg px-5 py-4 text-[13px] text-info">
          <p className="font-bold">Hay algo pendiente de revisión</p>
          <p className="mt-1">
            Se registraron ausencias en tus clases y el equipo de Orión las está revisando. Todavía
            no afectan tu visibilidad ni tus reservas.
          </p>
        </div>
      )}

      {sinDatos ? (
        <Tarjeta className="mt-5">
          <p className="text-[14px] font-semibold text-text">Aún no hay nada que medir</p>
          <p className="mt-1 text-[13px] text-text-secondary">
            Tus indicadores aparecen cuando empieces a dar clases. Mientras tanto, en el buscador
            apareces con una posición neutra: nadie te penaliza por ser nuevo.
          </p>
        </Tarjeta>
      ) : (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Metrica
              icono={<Star size={18} strokeWidth={2.2} />}
              valor={d.ratingCount >= 3 && d.ratingAvg ? d.ratingAvg.toFixed(1) : "—"}
              etiqueta="Calificación"
              ayuda={d.ratingCount < 3 ? "Se muestra desde 3 reseñas" : `${d.ratingCount} reseñas`}
            />
            <Metrica
              icono={<CalendarCheck size={18} strokeWidth={2.2} />}
              valor={String(d.lessonsCompleted)}
              etiqueta="Clases dictadas"
              ayuda={`En ${d.windowDays} días`}
            />
            <Metrica
              icono={<TrendingUp size={18} strokeWidth={2.2} />}
              valor={d.attendanceRate != null ? `${d.attendanceRate.toFixed(0)} %` : "—"}
              etiqueta="Cumplimiento"
              ayuda="Clases dictadas sobre las que te tocaban"
            />
            <Metrica
              icono={<Users size={18} strokeWidth={2.2} />}
              valor={String(d.activeStudents)}
              etiqueta="Estudiantes"
              ayuda="Distintos, con clase cerrada"
            />
          </div>

          <Tarjeta className="mt-4">
            <p className="flex items-center gap-2 text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
              <BarChart3 size={15} strokeWidth={2.2} />
              Cómo apareces en el buscador
            </p>
            <p className="mt-2 text-[13.5px] text-text">
              {d.rankingScore != null
                ? `Tu posición se calcula con un puntaje de ${d.rankingScore.toFixed(0)} sobre 100.`
                : "Tu puntaje se calcula esta noche."}
            </p>
            <p className="mt-1 text-[12.5px] text-text-muted">
              Pesan tu calificación, tu cumplimiento, cuántas clases llevas, qué tan completo está tu
              perfil y cuántos estudiantes repiten contigo. Completar tu perfil es lo más rápido que
              puedes hacer para subir.
            </p>
            {d.profileCompleteness != null && (
              <div className="mt-3">
                <div className="flex items-baseline justify-between text-[12.5px]">
                  <span className="text-text-secondary">Perfil completo</span>
                  <span className="font-semibold text-text">{d.profileCompleteness} %</span>
                </div>
                <div className="mt-1.5 h-2 overflow-hidden rounded-pill bg-surface-sunken">
                  <div
                    className="h-full rounded-pill bg-primary transition-all"
                    style={{ width: `${d.profileCompleteness}%` }}
                  />
                </div>
              </div>
            )}
          </Tarjeta>
        </>
      )}

      {d.sanctions.length > 0 && (
        <section className="mt-6">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Historial
          </h2>
          <Tarjeta className="mt-3">
            {d.sanctions.map((sancion) => (
              <div key={sancion.id} className="flex items-start justify-between gap-3 py-2">
                <div>
                  <p className="text-[13.5px] font-semibold text-text">
                    {SANCION[sancion.type] ?? sancion.type}
                  </p>
                  <p className="text-[12.5px] text-text-muted">{sancion.reason}</p>
                </div>
                <Badge
                  tono={
                    sancion.state === "ACTIVE"
                      ? "melocoton"
                      : sancion.state === "PROPOSED"
                        ? "lavanda"
                        : "neutral"
                  }
                >
                  {sancion.state === "ACTIVE"
                    ? "Activa"
                    : sancion.state === "PROPOSED"
                      ? "En revisión"
                      : "Levantada"}
                </Badge>
              </div>
            ))}
          </Tarjeta>
        </section>
      )}
    </main>
  );
}

function Metrica({
  icono,
  valor,
  etiqueta,
  ayuda,
}: {
  icono: React.ReactNode;
  valor: string;
  etiqueta: string;
  ayuda: string;
}) {
  return (
    <div className="rounded-card bg-surface-raised p-4 shadow-sm">
      <span aria-hidden="true" className="inline-grid h-9 w-9 place-items-center rounded-full bg-primary-soft text-primary-strong">
        {icono}
      </span>
      <p className="mt-3 font-display text-h2 font-bold tabular-nums text-text">{valor}</p>
      <p className="text-[13px] font-semibold text-text-secondary">{etiqueta}</p>
      <p className="mt-0.5 text-[11.5px] text-text-muted">{ayuda}</p>
    </div>
  );
}
