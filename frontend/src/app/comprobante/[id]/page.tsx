"use client";

import { useQuery } from "@tanstack/react-query";
import { Printer } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Wordmark } from "@/components/marca";
import { Cargando, ErrorCarga } from "@/components/estados";
import { Boton } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import { useMe } from "@/lib/auth/session";
import { entrarYVolver } from "@/lib/auth/volver";
import { fechaCorta, fechaLarga, precioCop } from "@/lib/format";

type Linea = {
  kind: string;
  classAt: string | null;
  studentLabel: string | null;
  grossCop: number;
  commissionRateBps: number | null;
  commissionCop: number;
  netCop: number;
  description: string;
};

type Comprobante = {
  id: string;
  status: string;
  statusLabel: string;
  periodStart: string;
  periodEnd: string;
  committedPayDate: string;
  mandataryName: string;
  mandataryDocument: string | null;
  professorName: string | null;
  professorDocument: string | null;
  lines: Linea[];
  grossCop: number;
  commissionCop: number;
  adjustmentsCop: number;
  netCop: number;
  paidOn: string | null;
  reference: string | null;
  payeeKeyTypeLabel: string | null;
  payeeMaskedKey: string | null;
  payeeHolder: string | null;
};

const dia = (fecha: string) => fechaLarga(`${fecha}T12:00:00-05:00`);
const diaCorto = (fecha: string) => fechaCorta(`${fecha}T12:00:00-05:00`);

/**
 * El comprobante de una liquidación (brief de liquidaciones, paso 5): una página imprimible, sin menú,
 * para guardarla como PDF desde el navegador. La ven el profe dueño y el admin; el contenido es el
 * mismo que le llega al profe por correo al pagarle.
 */
export default function ComprobantePage() {
  const { id } = useParams<{ id: string }>();
  const { data: me, isPending: cargandoSesion, isError: sinSesion } = useMe();
  const esAdmin = me?.role === "ADMIN";

  const comprobante = useQuery({
    queryKey: ["comprobante", id, esAdmin],
    queryFn: () =>
      apiFetch<Comprobante>(esAdmin ? `/api/v1/admin/payouts/${id}/receipt` : `/api/v1/me/payouts/${id}/receipt`),
    enabled: !!me,
  });

  if (sinSesion) {
    return (
      <main className="mx-auto max-w-md px-6 py-16 text-center">
        <p className="text-[15px] text-text-secondary">Entra a Orión para ver este comprobante.</p>
        <Link href={entrarYVolver("/login", `/comprobante/${id}`)} className="mt-4 inline-block font-bold text-primary-strong hover:underline">
          Entrar
        </Link>
      </main>
    );
  }
  if (cargandoSesion || comprobante.isPending) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-10">
        <Cargando filas={5} />
      </main>
    );
  }
  if (comprobante.isError) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-10">
        <ErrorCarga mensaje="No encontramos esta liquidación." onReintentar={() => void comprobante.refetch()} />
      </main>
    );
  }

  const c = comprobante.data;
  const pagada = c.status === "PAID";
  return (
    <main className="mx-auto max-w-3xl bg-surface px-6 py-8 text-text print:max-w-none print:bg-white print:px-0 print:py-0">
      <div className="flex items-center justify-between gap-4 print:hidden">
        <Link href={esAdmin ? "/admin/pagos" : "/ganancias"} className="text-[13px] font-bold text-primary-strong hover:underline">
          ← Volver
        </Link>
        <Boton variante="contorno" onClick={() => window.print()} className="h-11">
          <Printer size={16} strokeWidth={1.75} />
          Imprimir o guardar como PDF
        </Boton>
      </div>

      <article className="mt-6 rounded-card border border-border bg-surface-raised p-7 print:mt-0 print:border-0 print:p-0">
        <header className="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-5">
          <div>
            <Wordmark className="text-[18px] text-primary" />
            <h1 className="mt-3 font-display text-[24px] font-bold leading-tight">
              {pagada ? "Comprobante de liquidación" : "Liquidación (todavía sin pagar)"}
            </h1>
            <p className="mt-1 text-[13.5px] text-text-secondary">
              Quincena del {diaCorto(c.periodStart)} al {diaCorto(c.periodEnd)}
            </p>
          </div>
          <p className="text-[12px] text-text-muted">N.º {c.id.slice(0, 8).toUpperCase()}</p>
        </header>

        <section className="mt-5 grid gap-4 text-[13.5px] sm:grid-cols-2">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">Mandatario (recibe y entrega)</p>
            <p className="mt-1 font-semibold">{c.mandataryName}</p>
            {c.mandataryDocument && <p className="text-text-secondary">{c.mandataryDocument}</p>}
          </div>
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-text-muted">Profe (mandante)</p>
            <p className="mt-1 font-semibold">{c.professorName ?? "—"}</p>
            {c.professorDocument && <p className="text-text-secondary">{c.professorDocument}</p>}
          </div>
        </section>

        <table className="mt-6 w-full border-collapse text-[13px]">
          <thead>
            <tr className="border-b border-border text-left text-[11px] font-bold uppercase tracking-[0.06em] text-text-muted">
              <th className="py-2 pr-2">Concepto</th>
              <th className="py-2 pr-2 text-right">Valor</th>
              <th className="py-2 pr-2 text-right">Comisión</th>
              <th className="py-2 text-right">Neto</th>
            </tr>
          </thead>
          <tbody>
            {c.lines.map((l, i) => (
              <tr key={i} className="border-b border-border/60 align-top">
                <td className="py-2 pr-2">
                  {l.description}
                  {l.studentLabel && <span className="block text-text-muted">{l.studentLabel}</span>}
                </td>
                <td className="py-2 pr-2 text-right tabular-nums">{l.commissionRateBps !== null ? precioCop(l.grossCop) : ""}</td>
                <td className="py-2 pr-2 text-right tabular-nums">
                  {l.commissionRateBps !== null ? `${l.commissionRateBps / 100} % · ${precioCop(l.commissionCop)}` : ""}
                </td>
                <td className="py-2 text-right font-semibold tabular-nums">{precioCop(l.netCop)}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <dl className="ml-auto mt-4 grid max-w-sm gap-1 text-[13.5px]">
          <Fila etiqueta="Recibido en tu nombre" valor={precioCop(c.grossCop)} />
          <Fila etiqueta="Comisión de Orión" valor={`− ${precioCop(c.commissionCop)}`} />
          {c.adjustmentsCop !== 0 && <Fila etiqueta="Ajustes" valor={precioCop(c.adjustmentsCop)} />}
          <Fila etiqueta="Entregado" valor={precioCop(c.netCop)} fuerte />
        </dl>

        <section className="mt-6 rounded-base bg-surface-sunken p-4 text-[13.5px] print:border print:border-border print:bg-white">
          {pagada ? (
            <p>
              Transferencia Bre-B del <strong>{c.paidOn ? dia(c.paidOn) : "—"}</strong>, referencia{" "}
              <strong>{c.reference}</strong>, a la llave {c.payeeKeyTypeLabel?.toLowerCase()}{" "}
              <strong>{c.payeeMaskedKey}</strong> a nombre de {c.payeeHolder}.
            </p>
          ) : (
            <p>
              Estado: <strong>{c.statusLabel}</strong>. Se paga a más tardar el {dia(c.committedPayDate)}
              {c.payeeMaskedKey ? `, a la llave ${c.payeeKeyTypeLabel?.toLowerCase()} ${c.payeeMaskedKey}` : ""}.
            </p>
          )}
        </section>

        <p className="mt-6 text-[12px] leading-relaxed text-text-muted">
          {c.mandataryName} recibe en nombre del profe lo que pagan sus estudiantes y se lo entrega cada quincena, menos la
          comisión de Orión. Cada profe declara sus propios ingresos.
        </p>
      </article>
    </main>
  );
}

function Fila({ etiqueta, valor, fuerte = false }: { etiqueta: string; valor: string; fuerte?: boolean }) {
  return (
    <div className={`flex justify-between gap-4 ${fuerte ? "border-t border-border pt-2 font-bold" : "text-text-secondary"}`}>
      <dt>{etiqueta}</dt>
      <dd className="tabular-nums">{valor}</dd>
    </div>
  );
}
