"use client";

import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { AvisoError } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";

export function CambiarClave({ onCerrar }: { onCerrar: () => void }) {
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [listo, setListo] = useState(false);

  const cambiar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/password", {
        method: "POST",
        body: { currentPassword: actual, newPassword: nueva },
      }),
    onSuccess: () => setListo(true),
  });

  const error = cambiar.error instanceof ApiError ? cambiar.error.message : null;
  // La regla de longitud vive en el backend; aquí solo se refleja para no dejar pulsar en vano.
  const corta = nueva.length > 0 && nueva.length < 8;

  return (
    <Modal titulo="Cambiar contraseña" onCerrar={onCerrar}>
      {listo ? (
        <>
          <p className="text-[13px] text-text-secondary">
            Listo, tu contraseña quedó actualizada. Úsala la próxima vez que entres.
          </p>
          <Boton variante="primario" onClick={onCerrar} className="mt-5 h-12 w-full">
            Entendido
          </Boton>
        </>
      ) : (
        <>
          <label className="block text-[12.5px] font-bold text-text-secondary" htmlFor="actual">
            Contraseña actual
          </label>
          <Campo
            id="actual"
            type="password"
            autoComplete="current-password"
            value={actual}
            onChange={(event) => setActual(event.target.value)}
            className="mt-1.5"
          />

          <label className="mt-3 block text-[12.5px] font-bold text-text-secondary" htmlFor="nueva">
            Contraseña nueva
          </label>
          <Campo
            id="nueva"
            type="password"
            autoComplete="new-password"
            value={nueva}
            onChange={(event) => setNueva(event.target.value)}
            className="mt-1.5"
          />
          <p className="mt-1.5 text-[11.5px] text-text-muted">Mínimo 8 caracteres.</p>

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
              variante="primario"
              disabled={!actual || nueva.length < 8 || corta || cambiar.isPending}
              onClick={() => cambiar.mutate()}
              className="h-11 flex-1"
            >
              {cambiar.isPending ? "Guardando…" : "Cambiar"}
            </Boton>
          </div>
        </>
      )}
    </Modal>
  );
}
