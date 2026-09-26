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
  NotebookPen,
  Users,
  Wallet,
  XCircle,
  Sparkles,
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

      <IndicadorDelMandato />

      <FilaDelDiagnostico />
      <FilaDeLasActas />
      <FilaDeLaPractica />

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
            hora: si sigue vacío mañana, algo lo detuvo, y es el que libera los pagos.
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

/**
 * El diagnóstico, en la fila que decide si se sostiene.
 *
 * <p>Cinco números, y el último es el único que importa de verdad: <strong>conversión a reserva</strong>.
 * Una conversación preciosa que no lleva a ninguna clase es un gasto con buena prensa, y sin esta
 * cifra no hay forma de distinguir una cosa de la otra.
 */
function FilaDelDiagnostico() {
  const resumen = useQuery({
    queryKey: ["admin", "assessments"],
    queryFn: () =>
      apiFetch<{
        iniciados: number;
        completados: number;
        terminaronEnReserva: number;
        gastadoHoyCop: number;
        disponible: boolean;
      }>("/api/v1/admin/assessments"),
    staleTime: 60_000,
  });

  if (!resumen.data) return null;
  const d = resumen.data;
  const conversion = d.completados === 0
    ? "—"
    : `${Math.round((d.terminaronEnReserva / d.completados) * 100)} %`;

  return (
    <section className="mt-8">
      <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Diagnóstico de confianza
        {!d.disponible && (
          <span className="ml-2 rounded-pill bg-warning-bg px-2 py-0.5 text-[11px] normal-case text-warning">
            apagado
          </span>
        )}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Cifra
          tono="lavanda"
          icono={<Sparkles size={18} strokeWidth={2.2} />}
          valor={String(d.iniciados)}
          etiqueta="Iniciados"
          ayuda="Últimos 30 días"
        />
        <Cifra
          tono="menta"
          icono={<Sparkles size={18} strokeWidth={2.2} />}
          valor={String(d.completados)}
          etiqueta="Completados"
          ayuda="Los que llegaron a puntaje"
        />
        <Cifra
          tono="melocoton"
          icono={<Sparkles size={18} strokeWidth={2.2} />}
          valor={conversion}
          etiqueta="Terminaron en reserva"
          ayuda="Si esta cifra no sube, la función no sirve al negocio"
        />
        <Cifra
          tono="neutral"
          icono={<Wallet size={18} strokeWidth={2.2} />}
          valor={precioCop(d.gastadoHoyCop)}
          etiqueta="Gasto de hoy"
          ayuda="Contra el tope diario de Ajustes"
        />
      </div>
    </section>
  );
}

type PanelDeActas = {
  generadasHoy: number;
  publicadasHoy: number;
  clasesCerradas: number;
  clasesConActa: number;
  sinEditar: number;
  edicionMenor: number;
  reescritas: number;
  gastadoHoyCop: number;
  topeCop: number;
  iaEncendida: boolean;
  resultadosHoy: Record<string, number>;
};

/**
 * El acta de clase (Bloque 10, paso C1), en la fila que decide si se amplía o se revisa.
 *
 * <p>La cifra que manda es la de <strong>reescritas</strong>: si más de la mitad de las actas se
 * reescriben por completo, el borrador estorba en vez de ayudar y el prompt se revisa antes de
 * construir nada encima.
 */
function FilaDeLasActas() {
  const panel = useQuery({
    queryKey: ["admin", "lesson-notes"],
    queryFn: () => apiFetch<PanelDeActas>("/api/v1/admin/lesson-notes/metrics"),
    staleTime: 60_000,
  });

  if (!panel.data) return null;
  const d = panel.data;
  const conRatio = d.sinEditar + d.edicionMenor + d.reescritas;
  const porcentaje = (parte: number, total: number) =>
    total === 0 ? "—" : `${Math.round((parte / total) * 100)} %`;
  const resultados = Object.entries(d.resultadosHoy);

  return (
    <section className="mt-8">
      <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Acta de clase
        {!d.iaEncendida && (
          <span className="ml-2 rounded-pill bg-warning-bg px-2 py-0.5 text-[11px] normal-case text-warning">
            borrador con IA apagado
          </span>
        )}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Cifra
          tono="lavanda"
          icono={<NotebookPen size={18} strokeWidth={2.2} />}
          valor={String(d.publicadasHoy)}
          etiqueta="Publicadas hoy"
          ayuda={`${d.generadasHoy} empezadas hoy`}
        />
        <Cifra
          tono="menta"
          icono={<NotebookPen size={18} strokeWidth={2.2} />}
          valor={porcentaje(d.clasesConActa, d.clasesCerradas)}
          etiqueta="Clases con acta"
          ayuda={`${d.clasesConActa} de ${d.clasesCerradas} cerradas, últimos 30 días`}
        />
        <Cifra
          tono={conRatio > 0 && d.reescritas * 2 > conRatio ? "coral" : "melocoton"}
          icono={<NotebookPen size={18} strokeWidth={2.2} />}
          valor={porcentaje(d.reescritas, conRatio)}
          etiqueta="Reescritas por el profesor"
          ayuda="Si pasa de la mitad, se revisa el prompt antes de ampliar"
        />
        <Cifra
          tono="neutral"
          icono={<Wallet size={18} strokeWidth={2.2} />}
          valor={precioCop(d.gastadoHoyCop)}
          etiqueta="Gasto de hoy"
          ayuda={`De ${precioCop(d.topeCop)} de tope diario`}
        />
      </div>
      <Tarjeta className="mt-3">
        <Linea icono={<NotebookPen size={14} />} etiqueta="Sin editar" valor={d.sinEditar} />
        <Linea icono={<NotebookPen size={14} />} etiqueta="Edición menor" valor={d.edicionMenor} />
        <Linea icono={<NotebookPen size={14} />} etiqueta="Reescritas" valor={d.reescritas} />
        <p className="mt-2 text-[12px] text-text-muted">
          Llamadas al proveedor hoy:{" "}
          {resultados.length === 0
            ? "ninguna."
            : resultados.map(([resultado, n]) => `${n} ${resultado}`).join(" · ")}
        </p>
      </Tarjeta>
    </section>
  );
}

type PanelDePractica = {
  generados: number;
  completados: number;
  vencidos: number;
  fallidos: number;
  pendientes: number;
  gastadoHoyCop: number;
  topeCop: number;
  encendida: boolean;
};

/**
 * La práctica entre clases (Bloque 10, paso C1). La cifra que manda: si vencen más sets de los que
 * se completan, la práctica no engancha, y hay que mirarla antes de invertir más en ella.
 */
function FilaDeLaPractica() {
  const panel = useQuery({
    queryKey: ["admin", "practice"],
    queryFn: () => apiFetch<PanelDePractica>("/api/v1/admin/practice/metrics"),
    staleTime: 60_000,
  });

  if (!panel.data) return null;
  const d = panel.data;
  const noEngancha = d.vencidos > d.completados;

  return (
    <section className="mt-8">
      <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        Práctica entre clases
        {!d.encendida && (
          <span className="ml-2 rounded-pill bg-warning-bg px-2 py-0.5 text-[11px] normal-case text-warning">
            apagada
          </span>
        )}
      </h2>
      <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Cifra
          tono="lavanda"
          icono={<Sparkles size={18} strokeWidth={2.2} />}
          valor={String(d.generados)}
          etiqueta="Ofrecidas"
          ayuda={`Últimos 30 días · ${d.fallidos} sin ejercicios anclados`}
        />
        <Cifra
          tono="menta"
          icono={<CheckCircle2 size={18} strokeWidth={2.2} />}
          valor={String(d.completados)}
          etiqueta="Completadas"
          ayuda="Terminadas por el estudiante"
        />
        <Cifra
          tono={noEngancha ? "coral" : "melocoton"}
          icono={<Hourglass size={18} strokeWidth={2.2} />}
          valor={String(d.vencidos)}
          etiqueta="Vencidas sin hacer"
          ayuda="Si vencen más de las que se completan, la práctica no engancha"
        />
        <Cifra
          tono="neutral"
          icono={<Wallet size={18} strokeWidth={2.2} />}
          valor={precioCop(d.gastadoHoyCop)}
          etiqueta="Gasto de hoy"
          ayuda={`De ${precioCop(d.topeCop)} de tope diario`}
        />
      </div>
    </section>
  );
}

type ResumenDelAnio = {
  year: number;
  commissionCop: number;
  collectedCop: number;
  uvtCop: number;
  collectedUvt: number;
  referenceUvt: number;
  note: string;
};

/**
 * El año en curso bajo mandato (brief de liquidaciones, paso 6): lo que Orión ganó en comisiones y lo
 * que pasó por la cuenta de Pardo, también en UVT junto a la referencia de 3.500.
 */
function IndicadorDelMandato() {
  const resumen = useQuery({
    queryKey: ["admin", "payouts", "year-summary"],
    queryFn: () => apiFetch<ResumenDelAnio>("/api/v1/admin/payouts/year-summary"),
  });
  if (!resumen.data) return null;
  const r = resumen.data;
  const proporcion = Math.min(100, Math.round((r.collectedUvt / r.referenceUvt) * 100));
  return (
    <section className="mt-4 rounded-card border border-border bg-surface-raised p-5">
      <h3 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">El año {r.year} bajo mandato</h3>
      <div className="mt-3 grid gap-4 sm:grid-cols-2">
        <div>
          <p className="text-[12.5px] text-text-secondary">Comisiones acumuladas (tu ingreso)</p>
          <p className="font-display text-[22px] font-bold tabular-nums">{precioCop(r.commissionCop)}</p>
        </div>
        <div>
          <p className="text-[12.5px] text-text-secondary">Recaudo total por la pasarela</p>
          <p className="font-display text-[22px] font-bold tabular-nums">{precioCop(r.collectedCop)}</p>
          <p className="text-[12.5px] text-text-secondary">
            {r.collectedUvt.toLocaleString("es-CO")} UVT de {r.referenceUvt.toLocaleString("es-CO")} de referencia (UVT a{" "}
            {precioCop(r.uvtCop)})
          </p>
          <div className="mt-2 h-2 overflow-hidden rounded-pill bg-surface-sunken" aria-hidden="true">
            <div className="h-full bg-primary" style={{ width: `${proporcion}%` }} />
          </div>
        </div>
      </div>
      <p className="mt-3 text-[13px] text-text">{r.note}</p>
    </section>
  );
}
