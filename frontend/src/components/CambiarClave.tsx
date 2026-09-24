"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AvisoError } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { meQueryKey, useMe } from "@/lib/auth/session";

/** El texto del botón que abre esto: «Crear una contraseña» para quien entró con Google. */
export function useEtiquetaDeClave(): string {
  const { data: me } = useMe();
  return me?.hasPassword === false ? "Crear una contraseña" : "Cambiar contraseña";
}

/**
 * Cambiar la contraseña, o crear la primera. Quien entró con Google nació con una contraseña al
 * azar que nadie conoce: pedirle «la actual» era un callejón sin salida. Ahí crea una sin la
 * actual, y desde entonces puede entrar también con su correo.
 */
export function CambiarClave({ onCerrar }: { onCerrar: () => void }) {
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const crear = me?.hasPassword === false;
  const [actual, setActual] = useState("");
  const [nueva, setNueva] = useState("");
  const [listo, setListo] = useState(false);

  const cambiar = useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/me/password", {
        method: "POST",
        body: { currentPassword: crear ? null : actual, newPassword: nueva },
      }),
    onSuccess: () => {
      setListo(true);
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
    },
  });

  const error = cambiar.error instanceof ApiError ? cambiar.error.message : null;
  // La regla de longitud vive en el backend; aquí solo se refleja para no dejar pulsar en vano.
  const corta = nueva.length > 0 && nueva.length < 8;

  return (
    <Modal titulo={crear ? "Crear una contraseña" : "Cambiar contraseña"} onCerrar={onCerrar}>
      {listo ? (
        <>
          <p className="text-[13px] text-text-secondary">
            {crear
              ? "Listo. Desde ahora puedes entrar con Google o con tu correo y esta contraseña."
              : "Listo, tu contraseña quedó actualizada. Úsala la próxima vez que entres."}
          </p>
          <Boton variante="primario" onClick={onCerrar} className="mt-5 h-12 w-full">
            Entendido
          </Boton>
        </>
      ) : (
        <>
          {crear ? (
            <p className="text-[13px] leading-relaxed text-text-secondary">
              Entras con Google, así que no tienes una contraseña propia. Si quieres entrar también con
              tu correo ({me?.email}), crea una aquí: Google sigue funcionando igual.
            </p>
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
            </>
          )}

          <label className="mt-3 block text-[12.5px] font-bold text-text-secondary" htmlFor="nueva">
            {crear ? "Tu contraseña" : "Contraseña nueva"}
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
              disabled={(!crear && !actual) || nueva.length < 8 || corta || cambiar.isPending}
              onClick={() => cambiar.mutate()}
              className="h-11 flex-1"
            >
              {cambiar.isPending ? "Guardando…" : crear ? "Crear" : "Cambiar"}
            </Boton>
          </div>
        </>
      )}
    </Modal>
  );
}
