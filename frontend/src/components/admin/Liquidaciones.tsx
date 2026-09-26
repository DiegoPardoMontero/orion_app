"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarClock, Check, Copy, Download, Eye, RefreshCw, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { tablaAdmin as t } from "@/components/tablaAdmin";
import { Badge, Boton, BotonIcono, BotonPrincipal, Campo, Spinner, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { fechaCorta, fechaLarga, precioCop } from "@/lib/format";

/* Tipos de /api/v1/admin/payouts (AdminPayoutsController). */

type Estado = "DRAFT" | "APPROVED" | "PAID" | "ON_HOLD" | "CARRIED_OVER";

type Fila = {
  id: string;
  professorId: string;
  professorName: string | null;
  periodStart: string;
  periodEnd: string;
  status: Estado;
  statusLabel: string;
  holdReason: string | null;
  grossCop: number;
  commissionCop: number;
  adjustmentsCop: number;
  netCop: number;
  committedPayDate: string;
  paidOn: string | null;
  reference: string | null;
};

type Linea = {
  kind: "CLASS" | "LATE_CANCELLATION" | "REFUND_ADJUSTMENT" | "CARRY_OVER";
  classAt: string | null;
  studentLabel: string | null;
  grossCop: number;
  commissionRateBps: number | null;
  commissionCop: number;
  netCop: number;
  description: string;
};

type Detalle = {
  payout: Fila;
  cutoffAt: string;
  approvedAt: string | null;
  paidAt: string | null;
  payeeRegistered: boolean;
  payeeKeyTypeLabel: string | null;
  payeeMaskedKey: string | null;
  payeeHolder: string | null;
  lines: Linea[];
};

type Quincena = {
  periodStart: string | null;
  periodEnd: string | null;
  cutoffAt: string | null;
  committedPayDate: string | null;
  ranAt: string | null;
  toTransferCop: number;
  paidCop: number;
  onHold: number;
  payouts: Fila[];
};

type Cortes = {
  next: { start: string; end: string; cutoff: string; committedPayDate: string };
  cuts: { periodStart: string; periodEnd: string; cutoffAt: string; committedPayDate: string; ranAt: string; payoutsCreated: number }[];
};

type Llave = { keyType: string; keyTypeLabel: string; key: string; documentType: string; documentNumber: string; holderName: string };

const TONO: Record<Estado, "lavanda" | "coral" | "menta" | "error" | "neutral"> = {
  DRAFT: "lavanda",
  APPROVED: "coral",
  PAID: "menta",
  ON_HOLD: "error",
  CARRIED_OVER: "neutral",
};

/** Una fecha sin hora ("2026-10-20") dicha como día en Bogotá. */
const dia = (fecha: string) => fechaLarga(`${fecha}T12:00:00-05:00`);
const diaCorto = (fecha: string) => fechaCorta(`${fecha}T12:00:00-05:00`);

/**
 * Las liquidaciones quincenales bajo mandato (brief de liquidaciones, paso 4). Una vista por
 * quincena: cada profe con su estado, su neto y la fecha comprometida; el total a transferir y las
 * retenidas con su motivo. El detalle muestra las líneas, y desde ahí se aprueba y se registra el pago.
 */
export function Liquidaciones() {
  const [periodo, setPeriodo] = useState<string | null>(null);
  const [abierta, setAbierta] = useState<string | null>(null);

  const cortes = useQuery({
    queryKey: ["admin", "payouts", "cortes"],
    queryFn: () => apiFetch<Cortes>("/api/v1/admin/payouts/fortnights"),
  });
  const quincena = useQuery({
    queryKey: ["admin", "payouts", "quincena", periodo],
    queryFn: () => apiFetch<Quincena>(`/api/v1/admin/payouts${periodo ? `?periodStart=${periodo}` : ""}`),
  });

  return (
    <section className="mt-5">
      <p className="text-[13.5px] leading-relaxed text-text-secondary">
        El sistema corta a las 00:00 del 1 y del 16 y arma una liquidación por profe; tú la apruebas, transfieres por
        Bre-B y registras el pago.
      </p>

      {cortes.data && (
        <p className="mt-3 flex items-start gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[13px] text-[#5e4a8a]">
          <CalendarClock size={16} strokeWidth={1.9} className="mt-0.5 shrink-0" />
          <span>
            Próximo corte: <strong>{dia(cortes.data.next.cutoff.slice(0, 10))}</strong>, para la quincena del{" "}
            {diaCorto(cortes.data.next.start)} al {diaCorto(cortes.data.next.end)}. Se paga a más tardar el{" "}
            {dia(cortes.data.next.committedPayDate)}.
          </span>
        </p>
      )}

      {cortes.data && cortes.data.cuts.length > 0 && (
        <div className="mt-4">
          <label htmlFor="quincena" className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Quincena
          </label>
          <select
            id="quincena"
            value={periodo ?? cortes.data.cuts[0].periodStart}
            onChange={(event) => setPeriodo(event.target.value)}
            className="mt-1.5 h-11 w-full max-w-sm rounded-base border-[1.5px] border-border bg-surface-raised px-3.5 text-[14px] text-text focus:border-primary focus:shadow-focus focus:outline-none"
          >
            {cortes.data.cuts.map((c) => (
              <option key={c.periodStart} value={c.periodStart}>
                Del {diaCorto(c.periodStart)} al {diaCorto(c.periodEnd)}
              </option>
            ))}
          </select>
        </div>
      )}

      {quincena.isPending ? (
        <div className="mt-5">
          <Cargando filas={3} />
        </div>
      ) : quincena.isError ? (
        <div className="mt-5">
          <ErrorCarga mensaje="No pudimos cargar las liquidaciones." onReintentar={() => void quincena.refetch()} />
        </div>
      ) : !quincena.data.periodStart ? (
        <div className="mt-5">
          <Vacio titulo="Todavía no hay cortes" texto="El primero corre solo, a las 00:00 del 1 o del 16." />
        </div>
      ) : (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <Resumen etiqueta="Por transferir" valor={precioCop(quincena.data.toTransferCop)} />
            <Resumen etiqueta="Ya pagado" valor={precioCop(quincena.data.paidCop)} />
            <Resumen
              etiqueta="Pagar a más tardar el"
              valor={quincena.data.committedPayDate ? dia(quincena.data.committedPayDate) : "—"}
            />
          </div>
          {quincena.data.onHold > 0 && (
            <p className="mt-3 rounded-base bg-warning-bg px-4 py-3 text-[13px] text-text">
              {quincena.data.onHold === 1 ? "Hay 1 liquidación retenida" : `Hay ${quincena.data.onHold} liquidaciones retenidas`}: su
              motivo está en la fila. Se liberan solas cuando el profe lo resuelve.
            </p>
          )}

          {quincena.data.payouts.length === 0 ? (
            <div className="mt-5">
              <Vacio titulo="Nada que liquidar en esta quincena" texto="Ninguna clase había pasado su plazo de reclamo en el corte." />
            </div>
          ) : (
            <div className={`mt-5 ${t.contenedor}`}>
              <table className={t.tabla}>
                <thead className={t.cabecera}>
                  <tr className={t.filaCabecera}>
                    <th className={t.th}>Profe</th>
                    <th className={t.th}>Estado</th>
                    <th className={`${t.th} text-right`}>Neto</th>
                    <th className={`${t.th} text-right`}>Detalle</th>
                  </tr>
                </thead>
                <tbody className={t.cuerpo}>
                  {quincena.data.payouts.map((fila) => (
                    <tr key={fila.id} className={t.fila}>
                      <td className={`${t.celda} font-semibold text-text`}>{fila.professorName ?? "Profe"}</td>
                      <td className={t.celda}>
                        <Badge tono={TONO[fila.status]}>{fila.statusLabel}</Badge>
                        {fila.holdReason && <span className="mt-1 block text-[12px] text-error">{fila.holdReason}</span>}
                        {fila.status === "PAID" && fila.paidOn && (
                          <span className="mt-1 block text-[12px] text-text-muted">
                            El {diaCorto(fila.paidOn)} · {fila.reference}
                          </span>
                        )}
                      </td>
                      <td className={`${t.celda} tabular-nums lg:text-right`}>
                        <span className="lg:hidden">Neto: </span>
                        <strong>{precioCop(fila.netCop)}</strong>
                      </td>
                      <td className={t.acciones}>
                        <BotonIcono etiqueta={`Ver la liquidación de ${fila.professorName ?? "este profe"}`} onClick={() => setAbierta(fila.id)}>
                          <Eye size={17} strokeWidth={1.75} />
                        </BotonIcono>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {abierta && <DetalleDeLiquidacion id={abierta} onCerrar={() => setAbierta(null)} />}
    </section>
  );
}

function Resumen({ etiqueta, valor }: { etiqueta: string; valor: string }) {
  return (
    <Tarjeta>
      <p className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">{etiqueta}</p>
      <p className="mt-1 font-display text-[20px] font-bold tabular-nums text-text">{valor}</p>
    </Tarjeta>
  );
}

function DetalleDeLiquidacion({ id, onCerrar }: { id: string; onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const detalle = useQuery({
    queryKey: ["admin", "payouts", "detalle", id],
    queryFn: () => apiFetch<Detalle>(`/api/v1/admin/payouts/${id}`),
  });
  const refrescar = (nuevo?: Detalle) => {
    if (nuevo) queryClient.setQueryData(["admin", "payouts", "detalle", id], nuevo);
    void queryClient.invalidateQueries({ queryKey: ["admin", "payouts", "quincena"] });
  };

  const aprobar = useMutation({
    mutationFn: () => apiFetch<Detalle>(`/api/v1/admin/payouts/${id}/approve`, { method: "POST" }),
    onSuccess: refrescar,
  });
  const regenerar = useMutation({
    mutationFn: () => apiFetch<Detalle | undefined>(`/api/v1/admin/payouts/${id}/regenerate`, { method: "POST" }),
    onSuccess: (nuevo) => {
      if (!nuevo) {
        refrescar();
        onCerrar();
        return;
      }
      refrescar(nuevo);
    },
  });
  const error = [aprobar.error, regenerar.error].find((e) => e instanceof ApiError) as ApiError | undefined;

  const d = detalle.data;
  const p = d?.payout;
  return (
    <Modal titulo={p ? `Liquidación de ${p.professorName ?? "profe"}` : "Liquidación"} onCerrar={onCerrar} amplio>
      {detalle.isPending ? (
        <Cargando filas={4} />
      ) : detalle.isError || !d || !p ? (
        <ErrorCarga mensaje="No pudimos cargar la liquidación." onReintentar={() => void detalle.refetch()} />
      ) : (
        <>
          <p className="text-[13px] text-text-secondary">
            Quincena del {diaCorto(p.periodStart)} al {diaCorto(p.periodEnd)} · pagar a más tardar el{" "}
            {dia(p.committedPayDate)}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <Badge tono={TONO[p.status]}>{p.statusLabel}</Badge>
            {p.holdReason && <span className="text-[13px] font-semibold text-error">{p.holdReason}</span>}
          </div>

          <ul className="mt-4 divide-y divide-border rounded-base border border-border">
            {d.lines.map((l, i) => (
              <li key={i} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 px-4 py-3 text-[13px]">
                <div className="min-w-0">
                  <p className="font-semibold text-text">{l.description}</p>
                  {l.studentLabel && <p className="text-text-muted">{l.studentLabel}</p>}
                </div>
                <div className="text-right tabular-nums">
                  {l.commissionRateBps !== null ? (
                    <>
                      <p className="text-text-secondary">
                        {precioCop(l.grossCop)} − {l.commissionRateBps / 100} % ({precioCop(l.commissionCop)})
                      </p>
                      <p className="font-bold text-text">{precioCop(l.netCop)}</p>
                    </>
                  ) : (
                    <p className={`font-bold ${l.netCop < 0 ? "text-error" : "text-text"}`}>{precioCop(l.netCop)}</p>
                  )}
                </div>
              </li>
            ))}
          </ul>

          <dl className="mt-3 grid gap-1 text-[13.5px]">
            <Total etiqueta="Recibido en su nombre" valor={precioCop(p.grossCop)} />
            <Total etiqueta="Comisión de Orión" valor={`− ${precioCop(p.commissionCop)}`} />
            {p.adjustmentsCop !== 0 && <Total etiqueta="Ajustes" valor={precioCop(p.adjustmentsCop)} />}
            <Total etiqueta="A entregar" valor={precioCop(p.netCop)} fuerte />
          </dl>

          <div className="mt-4 rounded-base bg-surface-sunken px-4 py-3 text-[13px]">
            {d.payeeRegistered ? (
              <p>
                Llave {d.payeeKeyTypeLabel?.toLowerCase()} <strong>{d.payeeMaskedKey}</strong>, a nombre de{" "}
                <strong>{d.payeeHolder}</strong>.
              </p>
            ) : (
              <p className="text-error">El profe todavía no registró sus datos de pago.</p>
            )}
          </div>

          {error && (
            <div className="mt-4">
              <AvisoError mensaje={error.message} />
            </div>
          )}

          {p.status === "APPROVED" && <RegistrarPago id={id} onPagada={refrescar} />}

          {p.status === "PAID" && (
            <p className="mt-4 flex items-center gap-2 rounded-base bg-success-bg px-4 py-3 text-[13px] font-semibold text-success">
              <Check size={16} strokeWidth={2.2} />
              Pagada el {p.paidOn ? dia(p.paidOn) : "—"} · referencia {p.reference}
            </p>
          )}

          <div className="mt-5 flex flex-wrap gap-2">
            {p.status === "DRAFT" && (
              <Boton disabled={aprobar.isPending} onClick={() => aprobar.mutate()} className="h-11">
                {aprobar.isPending ? <Spinner /> : <ShieldCheck size={17} strokeWidth={1.75} />}
                Aprobar
              </Boton>
            )}
            {(p.status === "DRAFT" || p.status === "ON_HOLD") && (
              <Boton variante="contorno" disabled={regenerar.isPending} onClick={() => regenerar.mutate()} className="h-11">
                {regenerar.isPending ? <Spinner /> : <RefreshCw size={16} strokeWidth={1.75} />}
                Regenerar
              </Boton>
            )}
            <a
              href={`/api/v1/admin/payouts/${id}/export`}
              className="inline-flex min-h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              <Download size={16} strokeWidth={1.75} />
              CSV
            </a>
            <Link
              href={`/comprobante/${id}`}
              target="_blank"
              className="inline-flex min-h-11 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[14px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
            >
              Comprobante
            </Link>
          </div>
          {(p.status === "DRAFT" || p.status === "ON_HOLD") && (
            <p className="mt-2 text-[12px] text-text-muted">
              Regenerar la rehace con lo de hoy (por ejemplo, si entretanto se cerró un reclamo). Una aprobada o pagada ya no
              se regenera.
            </p>
          )}
        </>
      )}
    </Modal>
  );
}

function Total({ etiqueta, valor, fuerte = false }: { etiqueta: string; valor: string; fuerte?: boolean }) {
  return (
    <div className={`flex justify-between gap-4 ${fuerte ? "border-t border-border pt-2 font-bold text-text" : "text-text-secondary"}`}>
      <dt>{etiqueta}</dt>
      <dd className="tabular-nums">{valor}</dd>
    </div>
  );
}

/**
 * El pago de una liquidación aprobada: la llave completa (solo aquí, y queda auditado que se vio),
 * la fecha, la referencia Bre-B y la casilla del titular. Sin la casilla no se registra.
 */
function RegistrarPago({ id, onPagada }: { id: string; onPagada: (d: Detalle) => void }) {
  const hoy = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Bogota" }).format(new Date());
  const [fecha, setFecha] = useState(hoy);
  const [referencia, setReferencia] = useState("");
  const [verificado, setVerificado] = useState(false);
  const [copiado, setCopiado] = useState(false);

  const llave = useQuery({
    queryKey: ["admin", "payouts", "llave", id],
    queryFn: () => apiFetch<Llave>(`/api/v1/admin/payouts/${id}/payee`),
    staleTime: 0,
  });
  const pagar = useMutation({
    mutationFn: () =>
      apiFetch<Detalle>(`/api/v1/admin/payouts/${id}/pay`, {
        method: "POST",
        body: { paidOn: fecha, reference: referencia.trim(), holderVerified: verificado },
      }),
    onSuccess: onPagada,
  });
  const error = pagar.error instanceof ApiError ? pagar.error.message : null;

  return (
    <div className="mt-5 rounded-card border-[1.5px] border-primary/40 p-4">
      <p className="text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">Registrar el pago</p>
      {llave.isPending ? (
        <Cargando filas={1} />
      ) : llave.isError ? (
        <ErrorCarga mensaje="No pudimos traer la llave." onReintentar={() => void llave.refetch()} />
      ) : (
        <div className="mt-2 grid gap-1 text-[14px]">
          <p className="flex flex-wrap items-center gap-2">
            Llave {llave.data.keyTypeLabel.toLowerCase()}:{" "}
            <span className="font-mono text-[15px] font-bold text-text">{llave.data.key}</span>
            <BotonIcono
              etiqueta={copiado ? "Copiada" : "Copiar la llave"}
              onClick={() => {
                void navigator.clipboard.writeText(llave.data.key).then(() => setCopiado(true));
              }}
            >
              {copiado ? <Check size={16} strokeWidth={2} /> : <Copy size={16} strokeWidth={1.75} />}
            </BotonIcono>
          </p>
          <p>
            Titular: <strong>{llave.data.holderName}</strong> · {llave.data.documentType} {llave.data.documentNumber}
          </p>
        </div>
      )}

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div>
          <label htmlFor="fecha-pago" className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Fecha de la transferencia
          </label>
          <Campo id="fecha-pago" type="date" value={fecha} max={hoy} onChange={(e) => setFecha(e.target.value)} className="mt-1.5" />
        </div>
        <div>
          <label htmlFor="referencia" className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            Referencia Bre-B
          </label>
          <Campo
            id="referencia"
            value={referencia}
            maxLength={140}
            onChange={(e) => setReferencia(e.target.value)}
            className="mt-1.5"
          />
        </div>
      </div>
      <label htmlFor="titular-verificado" className="mt-3 flex cursor-pointer items-start gap-3 text-[13.5px] text-text">
        <input
          id="titular-verificado"
          type="checkbox"
          checked={verificado}
          onChange={(e) => setVerificado(e.target.checked)}
          className="mt-[3px] h-[18px] w-[18px] shrink-0 accent-primary"
        />
        Confirmo que el nombre que mostró mi banco coincide con el titular.
      </label>
      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}
      <BotonPrincipal
        className="mt-4"
        disabled={!verificado || !referencia.trim() || !fecha || pagar.isPending}
        onClick={() => pagar.mutate()}
      >
        {pagar.isPending ? <Spinner /> : null}
        Registrar pago
      </BotonPrincipal>
    </div>
  );
}
