"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Wallet } from "lucide-react";
import { useState } from "react";
import { Liquidaciones } from "@/components/admin/Liquidaciones";
import { ReportesDelMandato } from "@/components/admin/ReportesDelMandato";
import { LineaImporte } from "@/components/dinero";
import { AvisoError, Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Boton, Campo, Segmento, Spinner, Tarjeta } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { AdminPaymentResponse } from "@/lib/api/types";
import { etiquetaEstado } from "@/lib/estados-clase";
import { estadoDePago, PARA_EL_ADMIN } from "@/lib/estadosDePago";
import { fechaCorta, precioCop } from "@/lib/format";

type Pestana = "pagos" | "liquidaciones" | "reportes";

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
        Orión recibe el dinero de cada clase por cuenta del profe y se lo entrega cada quincena. Orión calcula; la
        transferencia la haces tú y la registras aquí con su referencia.
      </p>

      <div className="mt-4">
        <Segmento<Pestana>
          valor={pestana}
          onCambio={setPestana}
          opciones={[
            { valor: "pagos", etiqueta: "Conciliación" },
            { valor: "liquidaciones", etiqueta: "Liquidaciones" },
            { valor: "reportes", etiqueta: "Reportes" },
          ]}
        />
      </div>

      {pestana === "pagos" ? <Conciliacion /> : pestana === "liquidaciones" ? <Liquidaciones /> : <ReportesDelMandato />}
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

      {/* Con rótulo a la vista y no solo en `aria-label`: dos cajas de fecha idénticas, una al
          lado de la otra, no dicen cuál es el inicio del período y cuál el final. */}
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <RangoFecha id="conc-desde" etiqueta="Desde" valor={desde} onCambio={setDesde} />
        <RangoFecha id="conc-hasta" etiqueta="Hasta" valor={hasta} onCambio={setHasta} />
      </div>

      {enRevision.length > 0 && (
        <p className="mt-4 flex items-start gap-2 rounded-base bg-warning-bg px-4 py-3 text-[13px] text-warning">
          <AlertTriangle size={16} strokeWidth={1.75} className="mt-0.5 shrink-0" />
          <span>
            {enRevision.length} pago{enRevision.length > 1 ? "s necesitan" : " necesita"} tu
            decisión: la pasarela cobró y la clase no existe (porque se canceló, porque el cupo
            venció mientras el banco respondía, o porque el importe no cuadró). Esa plata no se le
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
                    <Badge tono={estadoDePago(PARA_EL_ADMIN, pago.status).tono} punto>
                      {estadoDePago(PARA_EL_ADMIN, pago.status).texto}
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
        Sugerido: {precioCop(pago.suggestedCreditCop)}, lo que el estudiante puso de su bolsillo en
        este pago. Para devolverle el dinero al medio de pago en vez de abonarle saldo, hazlo desde
        el panel de Wompi: su API no expone reembolsos.
      </p>
    </div>
  );
}

/** Un extremo del período, con su rótulo a la vista y ligado al campo. */
function RangoFecha({
  id,
  etiqueta,
  valor,
  onCambio,
}: {
  id: string;
  etiqueta: string;
  valor: string;
  onCambio: (v: string) => void;
}) {
  return (
    <div>
      <label
        htmlFor={id}
        className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary"
      >
        {etiqueta}
      </label>
      <Campo
        id={id}
        type="date"
        value={valor}
        onChange={(e) => onCambio(e.target.value)}
        className="mt-1.5"
      />
    </div>
  );
}
