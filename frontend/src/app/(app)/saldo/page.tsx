"use client";

import { useQuery } from "@tanstack/react-query";
import { Wallet } from "lucide-react";
import Link from "next/link";
import { Cifra, LineaImporte } from "@/components/dinero";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { CreditBalanceResponse, MyPaymentResponse } from "@/lib/api/types";
import { fechaCorta, horaBogota, precioCop } from "@/lib/format";

/** Por qué Orión le debe plata a un estudiante, en su idioma y no en el del enum. */
const MOTIVO_CREDITO: Record<string, string> = {
  PROFESSOR_NO_SHOW: "Tu profesor no llegó a la clase",
  CANCELLED_BY_PROFESSOR: "Tu profesor canceló la clase",
  DISPUTE_RESOLVED: "Resolución de un reclamo",
  ADMIN_ADJUSTMENT: "Ajuste de Orión",
};

/** Cómo se le cuenta al estudiante el estado de su pago. Nunca aparece la comisión. */
const ESTADO_PAGO: Record<string, { texto: string; tono: "menta" | "melocoton" | "neutral" | "error" }> = {
  PENDING: { texto: "Pendiente de pago", tono: "melocoton" },
  PAID: { texto: "Pagada", tono: "menta" },
  RELEASED: { texto: "Clase dictada", tono: "menta" },
  REFUNDED: { texto: "Devuelta a tu saldo", tono: "neutral" },
  DISPUTED: { texto: "En revisión", tono: "melocoton" },
  CANCELLED: { texto: "No se completó", tono: "error" },
};

export default function SaldoPage() {
  const saldo = useQuery({
    queryKey: ["me", "credits"],
    queryFn: () => apiFetch<CreditBalanceResponse>("/api/v1/me/credits"),
  });

  const pagos = useQuery({
    queryKey: ["me", "payments"],
    queryFn: () => apiFetch<MyPaymentResponse[]>("/api/v1/me/payments"),
  });

  if (saldo.isPending || pagos.isPending) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-6">
        <Cargando filas={4} />
      </main>
    );
  }

  if (saldo.isError || pagos.isError) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-6">
        <ErrorCarga
          mensaje="No pudimos cargar tus pagos."
          onReintentar={() => {
            void saldo.refetch();
            void pagos.refetch();
          }}
        />
      </main>
    );
  }

  const creditos = saldo.data.credits;

  return (
    <main className="mx-auto max-w-3xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Pagos y saldo</h1>

      <div className="mt-4">
        <Cifra
          tono="menta"
          icono={<Wallet size={18} strokeWidth={2.2} />}
          valorCop={saldo.data.balanceCop}
          etiqueta="Saldo a favor"
          ayuda="Se descuenta solo la próxima vez que reserves una clase."
        />
      </div>

      {creditos.length > 0 && (
        <section className="mt-6">
          <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
            De dónde viene tu saldo
          </h2>
          <Tarjeta className="mt-3">
            {creditos.map((credito) => (
              <LineaImporte
                key={credito.id}
                etiqueta={
                  <span>
                    {MOTIVO_CREDITO[credito.reason] ?? credito.reason}
                    {credito.expiresAt && (
                      <span className="block text-[11.5px] text-text-muted">
                        Vence el {fechaCorta(credito.expiresAt)}
                      </span>
                    )}
                  </span>
                }
                valor={precioCop(credito.remainingCop)}
                tono="credito"
              />
            ))}
          </Tarjeta>
        </section>
      )}

      <section className="mt-6">
        <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
          Historial
        </h2>

        {pagos.data.length === 0 ? (
          <div className="mt-3">
            <Vacio
              titulo="Todavía no tienes pagos"
              texto="Cuando reserves tu primera clase la verás aquí."
            />
          </div>
        ) : (
          <ul className="mt-3 grid gap-2.5">
            {pagos.data.map((pago) => {
              const estado = ESTADO_PAGO[pago.status] ?? { texto: pago.status, tono: "neutral" as const };
              return (
                <li key={pago.paymentId}>
                  <Link
                    href={`/pago/${pago.bookingId}`}
                    className="block rounded-card bg-surface-raised p-4 shadow-sm transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-semibold text-text">{pago.professorName ?? "Clase"}</p>
                        {pago.classAt && (
                          <p className="text-[12.5px] text-text-secondary">
                            {fechaCorta(pago.classAt)} · {horaBogota(pago.classAt)}
                          </p>
                        )}
                      </div>
                      <Badge tono={estado.tono} punto>
                        {estado.texto}
                      </Badge>
                    </div>

                    <div className="mt-3 border-t border-border pt-2 text-[13px]">
                      <LineaImporte etiqueta="Valor de la clase" valor={precioCop(pago.amountCop)} />
                      {pago.creditAppliedCop > 0 && (
                        <LineaImporte
                          etiqueta="Con tu saldo"
                          valor={`− ${precioCop(pago.creditAppliedCop)}`}
                          tono="credito"
                        />
                      )}
                    </div>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </main>
  );
}
