"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, FileText, Upload } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { tablaAdmin as t } from "@/components/tablaAdmin";
import { Badge, BotonIcono, Campo, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch, uploadFile } from "@/lib/api/fetch";
import { fechaCorta, precioCop } from "@/lib/format";

type FilaCertificado = {
  professorId: string;
  professorName: string;
  receivedCop: number;
  commissionCop: number;
  deliveredCop: number;
  pendingCop: number;
  uploadedAt: string | null;
};

const ANIO_ACTUAL = Number(new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota", year: "numeric" }).format(new Date()));
const ANIOS = Array.from({ length: ANIO_ACTUAL - 2025 }, (_, i) => ANIO_ACTUAL - i);

/**
 * Los reportes del mandato y el certificado anual (brief de liquidaciones, paso 6): el libro de
 * mandato por fechas, el resumen anual por profe, las comisiones por mes, y los certificados que
 * firma el contador. Todo sale de las liquidaciones, así que cuadra con ellas.
 */
export function ReportesDelMandato() {
  const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date());
  const [desde, setDesde] = useState(`${ANIO_ACTUAL}-01-01`);
  const [hasta, setHasta] = useState(hoy);
  const [anio, setAnio] = useState(ANIO_ACTUAL);

  return (
    <section className="mt-5">
      <p className="text-[13.5px] leading-relaxed text-text-secondary">
        Para tu contabilidad: lo recaudado es de cada profe hasta que se le paga, y tu único ingreso es la comisión. Los
        archivos abren bien en Excel.
      </p>

      <div className="mt-4 grid gap-3 lg:grid-cols-3">
        <Tarjeta>
          <p className="font-semibold text-text">Libro de mandato</p>
          <p className="mt-1 text-[12.5px] text-text-secondary">
            Una fila por clase con pago aprobado: la referencia de Wompi, el profe, bruto, comisión, neto y en qué
            liquidación quedó.
          </p>
          <div className="mt-3 grid grid-cols-2 gap-2">
            <Campo type="date" aria-label="Desde" value={desde} onChange={(e) => setDesde(e.target.value)} />
            <Campo type="date" aria-label="Hasta" value={hasta} onChange={(e) => setHasta(e.target.value)} />
          </div>
          <Descarga href={`/api/v1/admin/payouts/reports/ledger.csv?from=${desde}&to=${hasta}`} />
        </Tarjeta>
        <Tarjeta>
          <p className="font-semibold text-text">Resumen anual por profe</p>
          <p className="mt-1 text-[12.5px] text-text-secondary">
            Recibido en su nombre, comisión, entregado y lo pendiente al 31 de diciembre. Sirve para el certificado y la
            exógena.
          </p>
          <SelectorDeAnio anio={anio} onCambio={setAnio} />
          <Descarga href={`/api/v1/admin/payouts/reports/annual.csv?year=${anio}`} />
        </Tarjeta>
        <Tarjeta>
          <p className="font-semibold text-text">Comisiones por mes</p>
          <p className="mt-1 text-[12.5px] text-text-secondary">Tu ingreso propio: clases, recaudo y comisión de cada mes.</p>
          <SelectorDeAnio anio={anio} onCambio={setAnio} />
          <Descarga href={`/api/v1/admin/payouts/reports/commissions.csv?year=${anio}`} />
        </Tarjeta>
      </div>

      <Certificados anio={anio} onAnio={setAnio} />
    </section>
  );
}

function SelectorDeAnio({ anio, onCambio }: { anio: number; onCambio: (anio: number) => void }) {
  return (
    <select
      aria-label="Año"
      value={anio}
      onChange={(e) => onCambio(Number(e.target.value))}
      className="mt-3 h-11 w-full rounded-base border-[1.5px] border-border bg-surface-raised px-3 text-[14px] focus:border-primary focus:shadow-focus focus:outline-none"
    >
      {ANIOS.map((a) => (
        <option key={a} value={a}>
          {a}
        </option>
      ))}
    </select>
  );
}

function Descarga({ href }: { href: string }) {
  return (
    <a
      href={href}
      className="mt-3 inline-flex min-h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
    >
      <Download size={16} strokeWidth={1.75} />
      Descargar CSV
    </a>
  );
}

/**
 * El certificado de ingresos recibidos para terceros: el sistema arma el borrador, lo firma un
 * contador público, y aquí se sube el PDF firmado. El profe lo descarga en «Mis ganancias».
 */
function Certificados({ anio, onAnio }: { anio: number; onAnio: (anio: number) => void }) {
  const filas = useQuery({
    queryKey: ["admin", "payouts", "certificados", anio],
    queryFn: () => apiFetch<FilaCertificado[]>(`/api/v1/admin/payouts/certificates?year=${anio}`),
  });

  return (
    <div className="mt-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="font-display text-h3 font-bold">Certificados anuales</h2>
          <p className="mt-1 text-[13px] text-text-secondary">
            Imprime el borrador, llévalo al contador y sube el PDF firmado. El profe solo lo ve cuando ya está subido.
          </p>
        </div>
        <div className="w-32">
          <SelectorDeAnio anio={anio} onCambio={onAnio} />
        </div>
      </div>

      {filas.isPending ? (
        <div className="mt-4">
          <Cargando filas={3} />
        </div>
      ) : filas.isError ? (
        <div className="mt-4">
          <ErrorCarga mensaje="No pudimos cargar los certificados." onReintentar={() => void filas.refetch()} />
        </div>
      ) : filas.data.length === 0 ? (
        <div className="mt-4">
          <Vacio titulo={`Nadie recibió pagos en ${anio}`} texto="Cuando un profe tenga clases pagadas en el año, aparece aquí." />
        </div>
      ) : (
        <div className={`mt-4 ${t.contenedor}`}>
          <table className={t.tabla}>
            <thead className={t.cabecera}>
              <tr className={t.filaCabecera}>
                <th className={t.th}>Profe</th>
                <th className={`${t.th} text-right`}>Recibido · Entregado</th>
                <th className={t.th}>Certificado</th>
                <th className={`${t.th} text-right`}>Acciones</th>
              </tr>
            </thead>
            <tbody className={t.cuerpo}>
              {filas.data.map((f) => (
                <FilaDeCertificado key={f.professorId} fila={f} anio={anio} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function FilaDeCertificado({ fila, anio }: { fila: FilaCertificado; anio: number }) {
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const subir = useMutation({
    mutationFn: (archivo: File) => uploadFile<{ uploaded: boolean }>(`/api/v1/admin/payouts/certificates/${fila.professorId}/${anio}`, archivo),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["admin", "payouts", "certificados", anio] }),
  });
  const ver = useMutation({
    mutationFn: () => apiFetch<{ url: string }>(`/api/v1/admin/payouts/certificates/${fila.professorId}/${anio}/url`),
    onSuccess: ({ url }) => window.open(url, "_blank", "noopener"),
  });
  const error = [subir.error, ver.error].find((e) => e instanceof ApiError) as ApiError | undefined;

  return (
    <tr className={t.fila}>
      <td className={`${t.celda} font-semibold text-text`}>{fila.professorName}</td>
      <td className={`${t.celda} tabular-nums lg:text-right`}>
        {precioCop(fila.receivedCop)} · {precioCop(fila.deliveredCop)}
        {fila.pendingCop > 0 && (
          <span className="block text-[12px] text-text-muted">Pendiente al 31 dic: {precioCop(fila.pendingCop)}</span>
        )}
      </td>
      <td className={t.celda}>
        {fila.uploadedAt ? (
          <Badge tono="menta">Firmado · {fechaCorta(fila.uploadedAt)}</Badge>
        ) : (
          <Badge tono="neutral">Sin subir</Badge>
        )}
        {error && <span className="mt-1 block text-[12px] text-error">{error.message}</span>}
      </td>
      <td className={t.acciones}>
        <div className="flex items-center gap-2 lg:justify-end">
          <Link
            href={`/certificado/${fila.professorId}/${anio}`}
            target="_blank"
            aria-label={`Borrador del certificado de ${fila.professorName}`}
            title="Borrador para imprimir"
            className="grid h-11 w-11 place-items-center rounded-pill border-[1.5px] border-border text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            <FileText size={17} strokeWidth={1.75} />
          </Link>
          <BotonIcono etiqueta={fila.uploadedAt ? "Reemplazar el PDF firmado" : "Subir el PDF firmado"} disabled={subir.isPending} onClick={() => input.current?.click()}>
            <Upload size={17} strokeWidth={1.75} />
          </BotonIcono>
          {fila.uploadedAt && (
            <BotonIcono etiqueta="Ver el PDF firmado" disabled={ver.isPending} onClick={() => ver.mutate()}>
              <Download size={17} strokeWidth={1.75} />
            </BotonIcono>
          )}
          <input
            ref={input}
            type="file"
            accept="application/pdf"
            className="hidden"
            onChange={(e) => {
              const archivo = e.target.files?.[0];
              if (archivo) subir.mutate(archivo);
              e.target.value = "";
            }}
          />
        </div>
      </td>
    </tr>
  );
}
