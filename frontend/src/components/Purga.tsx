"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Trash2 } from "lucide-react";
import { useState } from "react";
import { Modal } from "@/components/Modal";
import { AvisoError, Cargando } from "@/components/estados";
import { Boton, Campo, Spinner } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import type { PurgePreview } from "@/lib/api/types";
import { precioCop } from "@/lib/format";

/** Lo que hay que escribir para confirmar. Corto, pero imposible de pulsar por accidente. */
const CONFIRMACION = "BORRAR";

/**
 * Borrado DEFINITIVO, con la vista previa por delante.
 *
 * La mitad de los borrados que alguien lamenta ocurren porque no sabía todo lo que colgaba de lo que
 * estaba borrando. Por eso aquí se pide la vista previa ANTES de mostrar el botón: cuántas filas de
 * cada cosa, cuánto dinero se lleva por delante, y qué parte de ese dinero ya se transfirió.
 */
export function BotonPurga({
  tipo,
  id,
  etiqueta = "Borrar definitivamente",
  onBorrado,
}: {
  tipo: "booking" | "user";
  id: string;
  etiqueta?: string;
  onBorrado?: () => void;
}) {
  const [abierto, setAbierto] = useState(false);

  return (
    <>
      <Boton
        variante="peligro"
        className="h-9 px-3 text-[13px]"
        onClick={() => setAbierto(true)}
      >
        <Trash2 size={15} strokeWidth={1.75} />
        {etiqueta}
      </Boton>
      {abierto && (
        <ModalPurga tipo={tipo} id={id} onCerrar={() => setAbierto(false)} onBorrado={onBorrado} />
      )}
    </>
  );
}

function ModalPurga({
  tipo,
  id,
  onCerrar,
  onBorrado,
}: {
  tipo: "booking" | "user";
  id: string;
  onCerrar: () => void;
  onBorrado?: () => void;
}) {
  const queryClient = useQueryClient();
  const [confirmacion, setConfirmacion] = useState("");
  const [motivo, setMotivo] = useState("");

  const base = tipo === "booking" ? `/api/v1/admin/bookings/${id}` : `/api/v1/admin/users/${id}`;

  const preview = useQuery({
    queryKey: ["admin", "purge-preview", tipo, id],
    queryFn: () => apiFetch<PurgePreview>(`${base}/purge-preview`),
  });

  const borrar = useMutation({
    mutationFn: () =>
      apiFetch(tipo === "booking" ? base : `${base}/purge`, {
        method: "DELETE",
        body: { confirm: confirmacion.trim(), reason: motivo.trim() || undefined },
      }),
    onSuccess: () => {
      // Se invalida todo: un borrado toca clases, pagos, usuarios y el tablero a la vez.
      void queryClient.invalidateQueries();
      onBorrado?.();
      onCerrar();
    },
  });

  const error = borrar.error instanceof ApiError ? borrar.error.message : null;
  const puedeBorrar = confirmacion.trim().toUpperCase() === CONFIRMACION;
  const datos = preview.data;

  return (
    <Modal titulo="Borrar definitivamente" onCerrar={onCerrar}>
      {preview.isPending ? (
        <Cargando filas={3} />
      ) : preview.isError || !datos ? (
        <AvisoError mensaje="No pudimos calcular qué se va a borrar. Mejor no sigas." />
      ) : (
        <>
          <p className="text-[13.5px] font-semibold text-text">{datos.label}</p>

          <div className="mt-3 rounded-base border border-border bg-surface-sunken px-4 py-3 text-[13px]">
            <p className="mb-2 text-[12px] font-bold uppercase tracking-[0.04em] text-text-muted">
              Se va a borrar
            </p>
            {datos.rows
              .filter((fila) => fila.count > 0)
              .map((fila) => (
                <div key={fila.what} className="flex items-baseline justify-between py-0.5">
                  <span className="text-text-secondary">{fila.what}</span>
                  <span className="font-semibold tabular-nums text-text">{fila.count}</span>
                </div>
              ))}
          </div>

          {(datos.money.paymentsCop > 0 || datos.money.creditsCop > 0) && (
            <div className="mt-3 rounded-base bg-warning-bg px-4 py-3 text-[13px] text-warning">
              <p className="font-bold">Dinero que desaparece</p>
              <div className="mt-1.5">
                <div className="flex items-baseline justify-between py-0.5">
                  <span>Pagos</span>
                  <span className="font-semibold tabular-nums">{precioCop(datos.money.paymentsCop)}</span>
                </div>
                {datos.money.settledCop > 0 && (
                  <div className="flex items-baseline justify-between py-0.5">
                    <span>De ese total, ya transferido a un profesor</span>
                    <span className="font-semibold tabular-nums">{precioCop(datos.money.settledCop)}</span>
                  </div>
                )}
                {datos.money.creditsCop > 0 && (
                  <div className="flex items-baseline justify-between py-0.5">
                    <span>Saldo a favor de estudiantes</span>
                    <span className="font-semibold tabular-nums">{precioCop(datos.money.creditsCop)}</span>
                  </div>
                )}
              </div>
            </div>
          )}

          {datos.warnings.length > 0 && (
            <ul className="mt-3 space-y-1.5">
              {datos.warnings.map((aviso) => (
                <li key={aviso} className="flex items-start gap-2 text-[12.5px] text-error">
                  <AlertTriangle size={14} strokeWidth={2} className="mt-0.5 shrink-0" />
                  {aviso}
                </li>
              ))}
            </ul>
          )}

          <label className="mt-4 block text-[12.5px] font-bold text-text-secondary" htmlFor="motivo-purga">
            Motivo (opcional, queda en la auditoría)
          </label>
          <Campo
            id="motivo-purga"
            type="text"
            value={motivo}
            onChange={(event) => setMotivo(event.target.value)}
            placeholder="Limpieza de datos de prueba"
            className="mt-1.5"
          />

          <label className="mt-3 block text-[12.5px] font-bold text-text-secondary" htmlFor="confirmar-purga">
            Escribe {CONFIRMACION} para confirmar
          </label>
          <Campo
            id="confirmar-purga"
            type="text"
            value={confirmacion}
            onChange={(event) => setConfirmacion(event.target.value)}
            autoComplete="off"
            className="mt-1.5"
          />

          {error && (
            <div className="mt-3">
              <AvisoError mensaje={error} />
            </div>
          )}

          <div className="mt-5 flex gap-2.5">
            <Boton variante="contorno" onClick={onCerrar} className="h-11 flex-1">
              Cancelar
            </Boton>
            <Boton
              variante="peligro"
              disabled={!puedeBorrar || borrar.isPending}
              onClick={() => borrar.mutate()}
              className="h-11 flex-1"
            >
              {borrar.isPending ? <Spinner /> : <Trash2 size={16} strokeWidth={1.75} />}
              Borrar
            </Boton>
          </div>
        </>
      )}
    </Modal>
  );
}
