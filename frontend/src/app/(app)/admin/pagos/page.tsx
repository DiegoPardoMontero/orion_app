"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Coins, Download, FileSpreadsheet, Wallet } from "lucide-react";
import { useState } from "react";
import { LineaImporte } from "@/components/dinero";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Boton, Campo, Segmento, Spinner, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { AdminPaymentResponse, PayoutResponse } from "@/lib/api/types";
import { etiquetaEstado } from "@/lib/estados-clase";
import { fechaCorta, precioCop } from "@/lib/format";

type Pestana = "pagos" | "liquidaciones";

const ESTADOS = [
  { valor: "", etiqueta: "Todos" },
  { valor: "PENDING", etiqueta: "Pendientes" },
  { valor: "PAID", etiqueta: "Retenidos" },
  { valor: "RELEASED", etiqueta: "Liberados" },
  { valor: "REFUNDED", etiqueta: "Devueltos" },
  { valor: "DISPUTED", etiqueta: "En revisión" },
] as const;

export default function AdminPagosPage() {
  const [pestana, setPestana] = useState<Pestana>("pagos");

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Pagos</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Orión calcula; la transferencia la haces tú y la registras aquí con su referencia.
      </p>

      <div className="mt-4">
        <Segmento<Pestana>
          valor={pestana}
          onCambio={setPestana}
          opciones={[
            { valor: "pagos", etiqueta: "Conciliación" },
            { valor: "liquidaciones", etiqueta: "Liquidaciones" },
          ]}
        />
      </div>

      {pestana === "pagos" ? <Conciliacion /> : <Liquidaciones />}
    </main>
  );
}

function Conciliacion() {
  const [estado, setEstado] = useState("");
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");

  const params = new URLSearchParams();
  if (estado) params.set("status", estado);
  if (desde) params.set("from", desde);
  if (hasta) params.set("to", hasta);
  const query = params.toString();

  const pagos = useQuery({
    queryKey: ["admin", "payments", query],
    queryFn: () => apiFetch<AdminPaymentResponse[]>(`/api/v1/admin/payments${query ? `?${query}` : ""}`),
  });

  const enRevision = (pagos.data ?? []).filter((pago) => pago.needsReview);

  return (
    <>
      <div className="mt-4 flex flex-wrap gap-2">
        {ESTADOS.map((opcion) => (
          <Boton
            key={opcion.valor}
            variante={estado === opcion.valor ? "primario" : "contorno"}
            className="h-9 px-4 text-[13px]"
            onClick={() => setEstado(opcion.valor)}
          >
            {opcion.etiqueta}
          </Boton>
        ))}
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <Campo type="date" value={desde} onChange={(e) => setDesde(e.target.value)} aria-label="Desde" />
        <Campo type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} aria-label="Hasta" />
      </div>

      {enRevision.length > 0 && (
        <p className="mt-4 flex items-start gap-2 rounded-base bg-warning-bg px-4 py-3 text-[13px] text-warning">
          <AlertTriangle size={16} strokeWidth={1.75} className="mt-0.5 shrink-0" />
          <span>
            {enRevision.length} pago{enRevision.length > 1 ? "s necesitan" : " necesita"} tu
            decisión: la pasarela cobró y la clase no existe —porque se canceló, porque el cupo
            venció mientras el banco respondía, o porque el importe no cuadró—. Esa plata no se le
            paga al profesor ni vuelve sola: abónale saldo al estudiante o devuélvesela desde el
            panel de Wompi.
          </span>
        </p>
      )}

      {pagos.isPending ? (
        <div className="mt-5">
          <Cargando filas={4} />
        </div>
      ) : pagos.isError ? (
        <div className="mt-5">
          <ErrorCarga mensaje="No pudimos cargar los pagos." onReintentar={() => void pagos.refetch()} />
        </div>
      ) : pagos.data.length === 0 ? (
        <div className="mt-5">
          <Vacio titulo="Sin pagos en este filtro" texto="Prueba con otro estado o con otras fechas." />
        </div>
      ) : (
        <ul className="mt-5 grid gap-2.5">
          {pagos.data.map((pago) => (
            <li key={pago.paymentId}>
              <Tarjeta>
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-text">
                      {pago.studentName ?? "—"} → {pago.professorName ?? "—"}
                    </p>
                    <p className="text-[12.5px] text-text-secondary">
                      {pago.classAt ? fechaCorta(pago.classAt) : "—"} ·{" "}
                      {etiquetaEstado(pago.bookingStatus ?? undefined)}
                    </p>
                    {pago.providerReference && (
                      <p className="mt-1 font-mono text-[11px] text-text-muted">
                        {pago.provider} · {pago.providerReference}
                      </p>
                    )}
                  </div>
                  <div className="flex flex-col items-end gap-1.5">
                    <Badge tono={tonoPago(pago.status)} punto>
                      {pago.status}
                    </Badge>
                    {pago.needsReview && <Badge tono="melocoton">Requiere decisión</Badge>}
                  </div>
                </div>

                <div className="mt-3 border-t border-border pt-2 text-[13px]">
                  <LineaImporte etiqueta="Precio de la clase" valor={precioCop(pago.amountCop)} />
                  {pago.creditAppliedCop > 0 && (
                    <LineaImporte
                      etiqueta="Saldo del estudiante"
                      valor={`− ${precioCop(pago.creditAppliedCop)}`}
                      tono="credito"
                    />
                  )}
                  <LineaImporte etiqueta="Cobrado por la pasarela" valor={precioCop(pago.chargedCop)} />
                  <LineaImporte
                    etiqueta={`Comisión Orión (${pago.commissionRateBps / 100} %)`}
                    valor={precioCop(pago.commissionCop)}
                  />
                  <LineaImporte
                    tono="total"
                    etiqueta="Para el profesor"
                    valor={precioCop(pago.professorEarningsCop)}
                  />
                </div>

                {pago.needsReview && <AbonarSaldo pago={pago} />}
              </Tarjeta>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

/**
 * La salida del caso que la conciliación marca: se le abona al estudiante el valor de la clase
 * como saldo. La otra salida —devolver la plata— se hace en el panel de Wompi, porque su API no
 * expone reembolsos; decirlo aquí evita que alguien busque un botón que no puede existir.
 */
/**
 * Resolver el incidente abonándole saldo al estudiante. La cifra viene sugerida por el backend y es
 * EDITABLE a propósito: cuánto capturó de verdad la pasarela solo se ve en el panel de Wompi, y la
 * sugerencia no siempre es el precio de la clase — si el pago pasó por vencido, el crédito del
 * estudiante ya volvió a su saldo y abonarle el precio entero se lo regalaría dos veces.
 *
 * Al abonar, el backend cierra el pago, así que el aviso desaparece y no se puede compensar dos veces.
 */
function AbonarSaldo({ pago }: { pago: AdminPaymentResponse }) {
  const queryClient = useQueryClient();
  const [monto, setMonto] = useState(String(pago.suggestedCreditCop));

  const abonar = useMutation({
    mutationFn: () =>
      apiFetch("/api/v1/admin/credits", {
        method: "POST",
        body: {
          studentId: pago.studentId,
          amountCop: Number(monto),
          reason: "ADMIN_ADJUSTMENT",
          bookingId: pago.bookingId,
        },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "payments"] }),
  });

  const error = abonar.error instanceof ApiError ? abonar.error.message : null;
  const valido = Number(monto) > 0;

  return (
    <div className="mt-3 border-t border-border pt-3">
      {error && (
        <div className="mb-2">
          <AvisoError mensaje={error} />
        </div>
      )}
      <p className="text-[12.5px] font-semibold text-text-secondary">
        Abonar saldo al estudiante
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <Campo
          type="number"
          min={1}
          value={monto}
          onChange={(event) => setMonto(event.target.value)}
          aria-label="Monto a abonar en pesos"
          className="w-[150px]"
        />
        <Boton
          variante="secundario"
          className="h-11 px-4 text-[13px]"
          disabled={!valido || abonar.isPending}
          onClick={() => abonar.mutate()}
        >
          {abonar.isPending ? <Spinner /> : <Wallet size={15} strokeWidth={1.75} />}
          Abonar {valido ? precioCop(Number(monto)) : ""}
        </Boton>
      </div>
      <p className="mt-2 text-[12px] text-text-muted">
        Sugerido: {precioCop(pago.suggestedCreditCop)} — lo que el estudiante puso de su bolsillo en
        este pago. Para devolverle el dinero al medio de pago en vez de abonarle saldo, hazlo desde
        el panel de Wompi: su API no expone reembolsos.
      </p>
    </div>
  );
}

function Liquidaciones() {
  const queryClient = useQueryClient();
  const [inicio, setInicio] = useState("");
  const [fin, setFin] = useState("");

  const liquidaciones = useQuery({
    queryKey: ["admin", "payouts"],
    queryFn: () => apiFetch<PayoutResponse[]>("/api/v1/admin/payouts"),
  });

  const generar = useMutation({
    mutationFn: () =>
      apiFetch<PayoutResponse[]>("/api/v1/admin/payouts/generate", {
        method: "POST",
        body: { periodStart: inicio, periodEnd: fin },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "payouts"] }),
  });

  const errorGenerar = generar.error instanceof ApiError ? generar.error.message : null;

  return (
    <>
      <Tarjeta className="mt-4">
        <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
          Generar liquidación
        </h2>
        <p className="mt-1 text-[12.5px] text-text-muted">
          Entran solo las clases que ya se dictaron y que no estén en otra liquidación.
        </p>
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <Campo type="date" value={inicio} onChange={(e) => setInicio(e.target.value)} aria-label="Desde" />
          <Campo type="date" value={fin} onChange={(e) => setFin(e.target.value)} aria-label="Hasta" />
        </div>
        {errorGenerar && (
          <div className="mt-3">
            <AvisoError mensaje={errorGenerar} />
          </div>
        )}
        <Boton
          className="mt-3"
          disabled={!inicio || !fin || generar.isPending}
          onClick={() => generar.mutate()}
        >
          {generar.isPending ? <Spinner /> : <Coins size={17} strokeWidth={1.75} />}
          Generar
        </Boton>
        {generar.isSuccess && generar.data.length === 0 && (
          <p className="mt-3 text-[13px] text-text-secondary">
            No había nada por liquidar en ese período.
          </p>
        )}
      </Tarjeta>

      {liquidaciones.isPending ? (
        <div className="mt-5">
          <Cargando filas={3} />
        </div>
      ) : liquidaciones.isError ? (
        <div className="mt-5">
          <ErrorCarga
            mensaje="No pudimos cargar las liquidaciones."
            onReintentar={() => void liquidaciones.refetch()}
          />
        </div>
      ) : liquidaciones.data.length === 0 ? (
        <div className="mt-5">
          <Vacio
            titulo="Todavía no hay liquidaciones"
            texto="Genera la primera con el período de arriba."
          />
        </div>
      ) : (
        <ul className="mt-5 grid gap-2.5">
          {liquidaciones.data.map((payout) => (
            <li key={payout.id}>
              <FilaLiquidacion payout={payout} />
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function FilaLiquidacion({ payout }: { payout: PayoutResponse }) {
  const queryClient = useQueryClient();
  const [referencia, setReferencia] = useState("");

  const marcar = useMutation({
    mutationFn: () =>
      apiFetch<PayoutResponse>(`/api/v1/admin/payouts/${payout.id}/mark-paid`, {
        method: "POST",
        body: { reference: referencia.trim() },
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "payouts"] }),
  });

  const error = marcar.error instanceof ApiError ? marcar.error.message : null;

  return (
    <Tarjeta>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-text">{payout.professorName ?? "Profesor"}</p>
          <p className="text-[12.5px] text-text-secondary">
            {fechaCorta(`${payout.periodStart}T12:00:00-05:00`)} –{" "}
            {fechaCorta(`${payout.periodEnd}T12:00:00-05:00`)}
          </p>
        </div>
        <div className="text-right">
          <p className="font-display text-h3 font-bold tabular-nums text-text">
            {precioCop(payout.amountCop)}
          </p>
          <Badge tono={payout.status === "PAID" ? "menta" : "melocoton"} punto>
            {payout.status === "PAID" ? "Transferida" : "Por transferir"}
          </Badge>
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-border pt-3">
        <a
          href={`/api/v1/admin/payouts/${payout.id}/export`}
          className="inline-flex min-h-9 items-center gap-1.5 rounded-pill border-[1.5px] border-border px-4 text-[13px] font-bold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
        >
          <Download size={15} strokeWidth={1.75} />
          CSV
        </a>

        {payout.status === "PAID" ? (
          <span className="flex items-center gap-1.5 text-[12.5px] text-text-secondary">
            <FileSpreadsheet size={14} strokeWidth={1.75} />
            Referencia: <span className="font-mono">{payout.reference}</span>
          </span>
        ) : (
          <>
            <Campo
              type="text"
              value={referencia}
              onChange={(event) => setReferencia(event.target.value)}
              maxLength={140}
              placeholder="Referencia de la transferencia"
              className="min-w-[220px] flex-1"
            />
            <Boton
              disabled={!referencia.trim() || marcar.isPending}
              onClick={() => marcar.mutate()}
            >
              {marcar.isPending ? <Spinner /> : null}
              Marcar transferida
            </Boton>
          </>
        )}
      </div>

      {error && (
        <div className="mt-3">
          <AvisoError mensaje={error} />
        </div>
      )}
    </Tarjeta>
  );
}

function tonoPago(status: string): "menta" | "melocoton" | "lavanda" | "neutral" | "error" {
  switch (status) {
    case "PAID":
      return "melocoton";
    case "RELEASED":
      return "menta";
    case "REFUNDED":
      return "lavanda";
    case "DISPUTED":
      return "melocoton";
    case "CANCELLED":
      return "error";
    default:
      return "neutral";
  }
}
