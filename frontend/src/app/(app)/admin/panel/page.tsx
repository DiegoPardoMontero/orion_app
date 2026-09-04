"use client";

import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  Banknote,
  CalendarDays,
  CheckCircle2,
  Coins,
  GraduationCap,
  Hourglass,
  Landmark,
  Users,
  Wallet,
  XCircle,
} from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { DashboardResponse } from "@/lib/api/types";
import { etiquetaEstado } from "@/lib/estados-clase";
import { fechaCorta, horaBogota, precioCop } from "@/lib/format";

/**
 * El pulso de Orión. Se ordena por urgencia y no por tema: primero lo que espera una decisión tuya,
 * después el dinero, después la actividad. Un tablero cuya primera fila no exige nada es un tablero
 * que se puede cerrar tranquilo.
 */
export default function AdminPanelPage() {
  const panel = useQuery({
    queryKey: ["admin", "dashboard"],
    queryFn: () => apiFetch<DashboardResponse>("/api/v1/admin/dashboard"),
    refetchInterval: 60_000,
  });

  if (panel.isPending) {
    return (
      <main className="mx-auto max-w-5xl px-6 py-6">
        <Cargando filas={5} />
      </main>
    );
  }
  if (panel.isError) {
    return (
      <main className="mx-auto max-w-5xl px-6 py-6">
        <ErrorCarga mensaje="No pudimos cargar el panel." onReintentar={() => void panel.refetch()} />
      </main>
    );
  }

  const d = panel.data;
  const pendientes =
    d.attention.openDisputes +
    d.attention.paymentsNeedingReview +
    d.attention.proposedSanctions +
    d.attention.reportedReviews;

  const clasesVivas = Object.entries(d.lessons.byStatus).filter(([, n]) => n > 0);

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Panel</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Todo lo que ves aquí son cifras reales, consultadas ahora mismo.
      </p>

      {/* 1. Lo que espera una decisión tuya */}
      <section className="mt-5">
        {pendientes === 0 ? (
          <div className="flex items-center gap-3 rounded-card bg-success-bg px-5 py-4 text-success">
            <CheckCircle2 size={20} strokeWidth={2} />
            <p className="text-[14px] font-semibold">
              No hay nada esperando tu decisión. Todo está al día.
            </p>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Pendiente
              n={d.attention.openDisputes}
              etiqueta="Reclamos abiertos"
              ayuda="Dinero congelado hasta que resuelvas"
              href="/admin/reclamos"
            />
            <Pendiente
              n={d.attention.paymentsNeedingReview}
              etiqueta="Pagos por decidir"
              ayuda="Cobrados sin clase detrás"
              href="/admin/pagos"
            />
            <Pendiente
              n={d.attention.proposedSanctions}
              etiqueta="Sanciones propuestas"
              ayuda="El sistema las calculó; tú confirmas"
              href="/admin/reclamos"
            />
            <Pendiente
              n={d.attention.reportedReviews}
              etiqueta="Reseñas reportadas"
              ayuda="Un profesor pidió revisarlas"
              href="/admin/resenas"
            />
          </div>
        )}
      </section>

      {/* 2. El dinero */}
      <h2 className="mt-8 text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Dinero
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Cifra
          tono="melocoton"
          icono={<Hourglass size={18} strokeWidth={2.2} />}
          valor={precioCop(d.money.heldCop)}
          etiqueta="Retenido"
          ayuda="Clases pagadas que aún no se dictan"
        />
        <Cifra
          tono="menta"
          icono={<Banknote size={18} strokeWidth={2.2} />}
          valor={precioCop(d.money.payableCop)}
          etiqueta="Por transferir"
          ayuda="Ya se lo ganaron los profesores"
        />
        <Cifra
          tono="lavanda"
          icono={<Landmark size={18} strokeWidth={2.2} />}
          valor={precioCop(d.money.transferredCop)}
          etiqueta="Transferido"
          ayuda="Ya salió hacia sus cuentas"
        />
        <Cifra
          tono="coral"
          icono={<Coins size={18} strokeWidth={2.2} />}
          valor={precioCop(d.money.commissionEarnedCop)}
          etiqueta="Comisión de Orión"
          ayuda="Sobre clases efectivamente cobradas"
        />
        <Cifra
          tono="neutral"
          icono={<Wallet size={18} strokeWidth={2.2} />}
          valor={precioCop(d.money.outstandingCreditCop)}
          etiqueta="Saldo a favor vigente"
          ayuda="Lo que Orión le debe a estudiantes"
        />
      </div>

      {/* 3. Personas y clases */}
      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <section>
          <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Personas
          </h2>
          <Tarjeta className="mt-3">
            <Linea icono={<Users size={16} strokeWidth={1.9} />} etiqueta="Estudiantes" valor={d.people.students} />
            <Linea
              icono={<GraduationCap size={16} strokeWidth={1.9} />}
              etiqueta="Profesores"
              valor={`${d.people.professorsPublished} publicados de ${d.people.professors}`}
            />
            <Linea
              icono={<CalendarDays size={16} strokeWidth={1.9} />}
              etiqueta="Postulaciones por revisar"
              valor={d.people.applicationsPending}
              href={d.people.applicationsPending > 0 ? "/admin/aplicaciones" : undefined}
            />
          </Tarjeta>
        </section>

        <section>
          <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Clases
          </h2>
          <Tarjeta className="mt-3">
            <Linea
              icono={<CalendarDays size={16} strokeWidth={1.9} />}
              etiqueta="Reservadas en 7 días"
              valor={d.lessons.bookedLast7Days}
            />
            <Linea
              icono={<CheckCircle2 size={16} strokeWidth={1.9} />}
              etiqueta="Autoservicio"
              valor={`${d.lessons.selfServicePercentage.toFixed(0)} %`}
            />
            <div className="mt-3 border-t border-border pt-3">
              {clasesVivas.length === 0 ? (
                <p className="text-[13px] text-text-muted">Todavía no hay clases.</p>
              ) : (
                clasesVivas.map(([estado, n]) => (
                  <div key={estado} className="flex items-baseline justify-between py-1 text-[13px]">
                    <span className="text-text-secondary">{etiquetaEstado(estado)}</span>
                    <span className="font-semibold tabular-nums text-text">{n}</span>
                  </div>
                ))
              )}
            </div>
          </Tarjeta>
        </section>
      </div>

      {/* 4. Los jobs, que nadie mira hasta que fallan */}
      <h2 className="mt-8 text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Procesos automáticos
      </h2>
      <Tarjeta className="mt-3">
        {d.jobs.length === 0 ? (
          <p className="text-[13px] text-text-muted">
            Ninguno ha corrido todavía desde el último reinicio. El de cierre de clases corre cada
            hora: si sigue vacío mañana, algo lo detuvo — y es el que libera los pagos.
          </p>
        ) : (
          d.jobs.map((job) => (
            <div key={job.job} className="flex items-start gap-2.5 py-1.5">
              {job.ok ? (
                <CheckCircle2 size={16} strokeWidth={2} className="mt-0.5 shrink-0 text-success" />
              ) : (
                <XCircle size={16} strokeWidth={2} className="mt-0.5 shrink-0 text-error" />
              )}
              <div className="min-w-0">
                <p className="text-[13px] font-semibold text-text">{job.job}</p>
                <p className="text-[12px] text-text-muted">
                  {/* Con las mismas funciones que el resto: `toLocaleString` devolvía «6:30:35 a. m.»,
                      con segundos y con el meridiano en minúsculas y partido, que no es como Orión
                      escribe una hora en ninguna otra pantalla. */}
                  {fechaCorta(job.lastRunAt)}, {horaBogota(job.lastRunAt)} ·{" "}
                  {job.detail}
                </p>
              </div>
            </div>
          ))
        )}
      </Tarjeta>
    </main>
  );
}

function Pendiente({
  n,
  etiqueta,
  ayuda,
  href,
}: {
  n: number;
  etiqueta: string;
  ayuda: string;
  href: string;
}) {
  const urgente = n > 0;
  const contenido = (
    <div
      className={`rounded-card p-4 shadow-sm transition-colors ${
        urgente ? "bg-warning-bg hover:bg-[#fbe4cf]" : "bg-surface-raised"
      }`}
    >
      <div className="flex items-center gap-2">
        {urgente && <AlertTriangle size={16} strokeWidth={2.2} className="text-warning" />}
        <p
          className={`font-display text-h2 font-bold tabular-nums ${
            urgente ? "text-warning" : "text-text-muted"
          }`}
        >
          {n}
        </p>
      </div>
      <p className="mt-1 text-[13px] font-semibold text-text">{etiqueta}</p>
      <p className="mt-0.5 text-[11.5px] text-text-muted">{ayuda}</p>
    </div>
  );
  return urgente ? <Link href={href}>{contenido}</Link> : contenido;
}

function Cifra({
  tono,
  icono,
  valor,
  etiqueta,
  ayuda,
}: {
  tono: "menta" | "melocoton" | "lavanda" | "coral" | "neutral";
  icono: ReactNode;
  valor: string;
  etiqueta: string;
  ayuda: string;
}) {
  const TONOS = {
    menta: "bg-success-bg text-success",
    melocoton: "bg-warning-bg text-warning",
    lavanda: "bg-info-bg text-info",
    coral: "bg-primary-soft text-primary-strong",
    neutral: "bg-surface-sunken text-text-secondary",
  } as const;

  return (
    <div className="rounded-card bg-surface-raised p-4 shadow-sm">
      <span aria-hidden="true" className={`inline-grid h-9 w-9 place-items-center rounded-full ${TONOS[tono]}`}>
        {icono}
      </span>
      <p className="mt-3 font-display text-h3 font-bold tabular-nums text-text">{valor}</p>
      <p className="text-[13px] font-semibold text-text-secondary">{etiqueta}</p>
      <p className="mt-0.5 text-[11.5px] text-text-muted">{ayuda}</p>
    </div>
  );
}

function Linea({
  icono,
  etiqueta,
  valor,
  href,
}: {
  icono: ReactNode;
  etiqueta: string;
  valor: string | number;
  href?: string;
}) {
  const fila = (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <span className="flex items-center gap-2 text-[13.5px] text-text-secondary">
        <span className="text-text-muted">{icono}</span>
        {etiqueta}
      </span>
      <span className="font-semibold tabular-nums text-text">{valor}</span>
    </div>
  );
  return href ? (
    <Link href={href} className="block rounded-base hover:bg-surface-sunken">
      {fila}
    </Link>
  ) : (
    fila
  );
}
