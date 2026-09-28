"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AvisoError } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, Campo } from "@/components/ui";
import { ApiError, apiFetch } from "@/lib/api/fetch";
import { meQueryKey, useMe } from "@/lib/auth/session";
import { esFuerte, fuerzaClave } from "@/lib/password";

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
  // Como al crear la cuenta: «Fuerte» o «Excelente» (Pardo, 27/09/2026). El servidor aplica la misma
  // regla; aquí se refleja para no dejar pulsar en vano y para decir qué le falta.
  const fuerza = fuerzaClave(nueva);
  const claveFuerte = esFuerte(nueva);

  return (
    <Modal titulo={crear ? "Crear una contraseña" : "Cambiar contraseña"} onCerrar={onCerrar}>
      {listo ? (
        <>
          <p className="text-[13px] text-text-secondary">
            {crear
              ? "Listo. Desde ahora puedes entrar como hasta ahora o con tu correo y esta contraseña."
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
              Entras con tu cuenta de Google, Microsoft, Apple o Facebook, así que no tienes una contraseña
              propia. Si quieres entrar también con tu correo ({me?.email}), crea una aquí: ese acceso sigue
              funcionando igual.
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
            aria-describedby="nueva-fuerza"
            className="mt-1.5"
          />
          <div className="mt-2.5">
            <div className="flex gap-1.5" aria-hidden="true">
              {[0, 1, 2, 3].map((i) => (
                <span
                  key={i}
                  className={`h-[5px] flex-1 rounded-pill transition-colors ${
                    i < fuerza.nivel ? "bg-success" : "bg-border"
                  }`}
                />
              ))}
            </div>
            <p
              id="nueva-fuerza"
              aria-live="polite"
              className={`mt-1.5 text-[12px] ${
                claveFuerte ? "text-success" : nueva ? "text-text-secondary" : "text-text-muted"
              }`}
            >
              {fuerza.mensaje}
              {nueva && !claveFuerte && (
                <span className="block text-text-muted">
                  Para guardarla, tiene que quedar «Fuerte» o «Excelente».
                </span>
              )}
            </p>
          </div>

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
              disabled={(!crear && !actual) || !claveFuerte || cambiar.isPending}
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
