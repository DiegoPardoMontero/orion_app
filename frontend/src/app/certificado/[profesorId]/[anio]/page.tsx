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
import { precioCop } from "@/lib/format";

type Borrador = {
  year: number;
  mandataryName: string;
  mandataryDocument: string | null;
  professorName: string;
  professorDocument: string | null;
  receivedCop: number;
  commissionCop: number;
  deliveredCop: number;
  withheldCop: number;
};

/**
 * El borrador del «Certificado de ingresos recibidos para terceros» (brief de liquidaciones, paso 6):
 * lo imprime el admin, lo firma un contador público y el PDF firmado se sube en Pagos → Reportes.
 */
export default function CertificadoPage() {
  const { profesorId, anio } = useParams<{ profesorId: string; anio: string }>();
  const { data: me, isError: sinSesion } = useMe();
  const borrador = useQuery({
    queryKey: ["certificado", profesorId, anio],
    queryFn: () => apiFetch<Borrador>(`/api/v1/admin/payouts/certificates/${profesorId}/${anio}/draft`),
    enabled: me?.role === "ADMIN",
  });

  if (sinSesion || (me && me.role !== "ADMIN")) {
    return <main className="mx-auto max-w-md px-6 py-16 text-center text-text-secondary">Este borrador lo ve solo el equipo de Orión.</main>;
  }
  if (!borrador.data) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-10">
        {borrador.isError ? (
          <ErrorCarga mensaje="No pudimos armar el borrador." onReintentar={() => void borrador.refetch()} />
        ) : (
          <Cargando filas={5} />
        )}
      </main>
    );
  }
  const b = borrador.data;
  return (
    <main className="mx-auto max-w-3xl bg-surface px-6 py-8 text-text print:max-w-none print:bg-white print:px-0 print:py-0">
      <div className="flex items-center justify-between gap-4 print:hidden">
        <Link href="/admin/pagos" className="text-[13px] font-bold text-primary-strong hover:underline">
          ← Volver
        </Link>
        <Boton variante="contorno" onClick={() => window.print()} className="h-11">
          <Printer size={16} strokeWidth={1.75} />
          Imprimir o guardar como PDF
        </Boton>
      </div>

      <article className="mt-6 rounded-card border border-border bg-surface-raised p-8 print:mt-0 print:border-0 print:p-0">
        <Wordmark className="text-[18px] text-primary" />
        <h1 className="mt-4 font-display text-[24px] font-bold leading-tight">Certificado de ingresos recibidos para terceros</h1>
        <p className="mt-1 text-[14px] text-text-secondary">Año gravable {b.year}</p>

        <p className="mt-6 text-[14.5px] leading-relaxed">
          <strong>{b.mandataryName}</strong>
          {b.mandataryDocument ? `, identificado con ${b.mandataryDocument},` : ""} en calidad de mandatario, certifica que
          durante el año {b.year} recibió por cuenta de <strong>{b.professorName}</strong>
          {b.professorDocument ? `, identificado con ${b.professorDocument},` : ""} (mandante) los valores que pagaron sus
          estudiantes por las clases dictadas a través de Orión, así:
        </p>

        <dl className="mt-6 grid max-w-md gap-2 text-[14.5px]">
          <Fila etiqueta="Recibido en nombre del mandante" valor={precioCop(b.receivedCop)} />
          <Fila etiqueta="Comisión cobrada por Orión" valor={precioCop(b.commissionCop)} />
          <Fila etiqueta="Entregado al mandante" valor={precioCop(b.deliveredCop)} />
          <Fila etiqueta="Retenciones practicadas" valor={precioCop(b.withheldCop)} />
        </dl>

        <p className="mt-6 text-[13px] leading-relaxed text-text-secondary">
          Lo recibido corresponde a pagos aprobados y no devueltos. Lo entregado son las transferencias hechas durante el
          año. Declarar estos ingresos es responsabilidad del mandante.
        </p>

        <section className="mt-12 grid gap-10 text-[13.5px] sm:grid-cols-2">
          <div>
            <div className="h-16 border-b border-text" />
            <p className="mt-2">Firma del contador público</p>
          </div>
          <div className="grid gap-6">
            <div>
              <div className="h-8 border-b border-text" />
              <p className="mt-2">Nombre</p>
            </div>
            <div>
              <div className="h-8 border-b border-text" />
              <p className="mt-2">Tarjeta profesional</p>
            </div>
          </div>
        </section>
      </article>
    </main>
  );
}

function Fila({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <div className="flex justify-between gap-6 border-b border-border/60 pb-2">
      <dt>{etiqueta}</dt>
      <dd className="font-semibold tabular-nums">{valor}</dd>
    </div>
  );
}
