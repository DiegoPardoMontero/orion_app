"use client";

import { useQuery } from "@tanstack/react-query";
import { Banknote, Hourglass, Landmark } from "lucide-react";
import { useState } from "react";
import { Cifra, LineaImporte } from "@/components/dinero";
import { Cargando, ErrorCarga, Vacio } from "@/components/estados";
import { Badge, Campo, Tarjeta } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import type { EarningsResponse } from "@/lib/api/types";
import { fechaCorta, horaBogota, precioCop } from "@/lib/format";

/**
 * Qué significa cada estado del dinero para quien da la clase. No se usan los nombres del enum:
 * "RELEASED" no le dice nada a nadie, "por cobrar" sí.
 */
const ESTADO_LINEA: Record<string, { texto: string; tono: "menta" | "melocoton" | "neutral" | "error" }> = {
  PENDING: { texto: "Sin pagar aún", tono: "neutral" },
  PAID: { texto: "Retenido", tono: "melocoton" },
  RELEASED: { texto: "Por cobrar", tono: "menta" },
  REFUNDED: { texto: "Devuelto al estudiante", tono: "neutral" },
  DISPUTED: { texto: "En revisión", tono: "melocoton" },
  CANCELLED: { texto: "Cancelado", tono: "error" },
};

export default function GananciasPage() {
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");

  const params = new URLSearchParams();
  if (desde) params.set("from", desde);
  if (hasta) params.set("to", hasta);
  const query = params.toString();

  const ganancias = useQuery({
    queryKey: ["me", "earnings", query],
    queryFn: () => apiFetch<EarningsResponse>(`/api/v1/me/earnings${query ? `?${query}` : ""}`),
  });

  return (
    <main className="mx-auto max-w-4xl px-6 py-6">
      <h1 className="font-display text-h1 font-bold">Mis ganancias</h1>
      <p className="mt-1 text-[13.5px] text-text-secondary">
        Orión cobra al estudiante y te transfiere lo tuyo cuando la clase ya se dictó.
      </p>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Campo
          type="date"
          value={desde}
          onChange={(event) => setDesde(event.target.value)}
          aria-label="Desde"
        />
        <Campo
          type="date"
          value={hasta}
          onChange={(event) => setHasta(event.target.value)}
          aria-label="Hasta"
        />
      </div>

      {ganancias.isPending ? (
        <div className="mt-5">
          <Cargando filas={4} />
        </div>
      ) : ganancias.isError ? (
        <div className="mt-5">
          <ErrorCarga
            mensaje="No pudimos cargar tus ganancias."
            onReintentar={() => void ganancias.refetch()}
          />
        </div>
      ) : (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-3">
            <Cifra
              tono="melocoton"
              icono={<Hourglass size={18} strokeWidth={2.2} />}
              valorCop={ganancias.data.heldCop}
              etiqueta="Retenido"
              ayuda="Clases pagadas que todavía no se han dictado."
            />
            <Cifra
              tono="menta"
              icono={<Banknote size={18} strokeWidth={2.2} />}
              valorCop={ganancias.data.payableCop}
              etiqueta="Por cobrar"
              ayuda="Ya te lo ganaste; entra en la próxima liquidación."
            />
            <Cifra
              tono="lavanda"
              icono={<Landmark size={18} strokeWidth={2.2} />}
              valorCop={ganancias.data.transferredCop}
              etiqueta="Transferido"
              ayuda="Ya salió hacia tu cuenta."
            />
          </div>

          <section className="mt-6">
            <h2 className="text-[13px] font-bold uppercase tracking-[0.04em] text-text-secondary">
              Clase por clase
            </h2>

            {ganancias.data.lines.length === 0 ? (
              <div className="mt-3">
                <Vacio
                  titulo="Sin clases en este período"
                  texto="Cuando un estudiante reserve y pague, verás aquí el detalle de lo que ganas."
                />
              </div>
            ) : (
              <ul className="mt-3 grid gap-2.5">
                {ganancias.data.lines.map((linea) => {
                  const estado = ESTADO_LINEA[linea.status] ?? {
                    texto: linea.status,
                    tono: "neutral" as const,
                  };
                  return (
                    <li key={linea.bookingId}>
                      <Tarjeta>
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="font-semibold text-text">
                              {linea.studentName ?? "Estudiante"}
                            </p>
                            {linea.classAt && (
                              <p className="text-[12.5px] text-text-secondary">
                                {fechaCorta(linea.classAt)} · {horaBogota(linea.classAt)}
                              </p>
                            )}
                          </div>
                          <Badge tono={estado.tono} punto>
                            {estado.texto}
                          </Badge>
                        </div>

                        <div className="mt-3 border-t border-border pt-2 text-[13px]">
                          <LineaImporte
                            etiqueta="Precio de la clase"
                            valor={precioCop(linea.amountCop)}
                          />
                          <LineaImporte
                            etiqueta="Comisión de Orión"
                            valor={`− ${precioCop(linea.commissionCop)}`}
                          />
                          <LineaImporte
                            tono="total"
                            etiqueta="Para ti"
                            valor={precioCop(linea.earningsCop)}
                          />
                        </div>
                      </Tarjeta>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </>
      )}
    </main>
  );
}
