"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { CalendarClock, FileText } from "lucide-react";
import Link from "next/link";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Badge, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import { fechaCorta, fechaLarga, precioCop } from "@/lib/format";
import { estadoParaElProfe, type LiquidacionDelProfe } from "@/lib/liquidaciones";

type PorLiquidar = {
  bookingId: string;
  classAt: string;
  studentLabel: string;
  kind: "CLASS" | "LATE_CANCELLATION";
  netCop: number;
  reason: string;
};

type MisLiquidacionesData = {
  explanation: string;
  nextCutoff: string;
  nextPeriodStart: string;
  nextPeriodEnd: string;
  nextPayDate: string;
  pending: PorLiquidar[];
  payouts: LiquidacionDelProfe[];
};

const dia = (fecha: string) => fechaLarga(`${fecha}T12:00:00-05:00`);
const diaCorto = (fecha: string) => fechaCorta(`${fecha}T12:00:00-05:00`);

/**
 * Las liquidaciones del profe en «Mis ganancias» (brief de liquidaciones, paso 5): cómo funciona,
 * cuándo es el próximo corte y el pago estimado, qué clases faltan por liquidar y por qué, y el
 * historial con el comprobante de cada una.
 */
export function MisLiquidaciones() {
  const datos = useQuery({
    queryKey: ["me", "payouts"],
    queryFn: () => apiFetch<MisLiquidacionesData>("/api/v1/me/payouts"),
  });

  if (datos.isPending) return <Cargando filas={3} />;
  if (datos.isError) {
    return <ErrorCarga mensaje="No pudimos cargar tus liquidaciones." onReintentar={() => void datos.refetch()} />;
  }
  const d = datos.data;

  return (
    <section className="mt-8" aria-labelledby="liquidaciones-titulo">
      <h2 id="liquidaciones-titulo" className="font-display text-h3 font-bold">
        Tus liquidaciones
      </h2>
      <p className="mt-1 text-[13.5px] leading-relaxed text-text-secondary">{d.explanation}</p>

      <p className="mt-3 flex items-start gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[13px] text-[#5e4a8a]">
        <CalendarClock size={16} strokeWidth={1.9} className="mt-0.5 shrink-0" />
        <span>
          Próximo corte: <strong>{dia(d.nextCutoff.slice(0, 10))}</strong> (clases del {diaCorto(d.nextPeriodStart)} al{" "}
          {diaCorto(d.nextPeriodEnd)}). Pago estimado: a más tardar el <strong>{dia(d.nextPayDate)}</strong>.
        </span>
      </p>

      <h3 className="mt-6 text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">Clases por liquidar</h3>
      {d.pending.length === 0 ? (
        <p className="mt-2 text-[13.5px] text-text-muted">No tienes clases esperando: todo lo dictado ya está en una liquidación.</p>
      ) : (
        <ul className="mt-2 grid gap-2">
          {d.pending.map((c) => (
            <li key={c.bookingId}>
              <Tarjeta className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1">
                <div className="min-w-0">
                  <p className="font-semibold text-text">
                    {c.kind === "LATE_CANCELLATION" ? "Cancelación tardía" : "Clase"} del {fechaCorta(c.classAt)} ·{" "}
                    {c.studentLabel}
                  </p>
                  <p className="text-[13px] text-text-secondary">{c.reason}</p>
                </div>
                <p className="font-bold tabular-nums text-text">{precioCop(c.netCop)}</p>
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}

      <MisCertificados />

      <h3 className="mt-6 text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">Historial de liquidaciones</h3>
      {d.payouts.length === 0 ? (
        <p className="mt-2 text-[13.5px] text-text-muted">Todavía no tienes liquidaciones. La primera sale en el próximo corte.</p>
      ) : (
        <ul className="mt-2 grid gap-2">
          {d.payouts.map((l) => {
            const estado = estadoParaElProfe(l);
            return (
              <li key={l.id}>
                <Tarjeta className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-text">
                      Quincena del {diaCorto(l.periodStart)} al {diaCorto(l.periodEnd)}
                    </p>
                    <div className="mt-1">
                      <Badge tono={estado.tono}>{estado.texto}</Badge>
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <p className="font-display text-[18px] font-bold tabular-nums text-text">{precioCop(l.netCop)}</p>
                    <Link
                      href={`/comprobante/${l.id}`}
                      className="inline-flex items-center gap-1.5 text-[13px] font-bold text-primary-strong hover:underline"
                    >
                      <FileText size={14} strokeWidth={1.9} />
                      {l.status === "PAID" ? "Comprobante" : "Ver detalle"}
                    </Link>
                  </div>
                </Tarjeta>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

/**
 * El certificado anual firmado por el contador. Mientras no se haya subido ninguno, no se muestra
 * nada: una sección que promete un documento que no existe es UI muerta.
 */
function MisCertificados() {
  const certificados = useQuery({
    queryKey: ["me", "payouts", "certificados"],
    queryFn: () => apiFetch<{ year: number; uploadedAt: string }[]>("/api/v1/me/payouts/certificates"),
  });
  const descargar = useMutation({
    mutationFn: (anio: number) => apiFetch<{ url: string }>(`/api/v1/me/payouts/certificates/${anio}/url`),
    onSuccess: ({ url }) => window.open(url, "_blank", "noopener"),
  });
  if (!certificados.data || certificados.data.length === 0) return null;
  return (
    <div className="mt-6">
      <h3 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">Certificados para tu declaración</h3>
      <ul className="mt-2 flex flex-wrap gap-2">
        {certificados.data.map((c) => (
          <li key={c.year}>
            <button
              type="button"
              disabled={descargar.isPending}
              onClick={() => descargar.mutate(c.year)}
              className="inline-flex min-h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              <FileText size={15} strokeWidth={1.9} />
              Certificado {c.year}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
