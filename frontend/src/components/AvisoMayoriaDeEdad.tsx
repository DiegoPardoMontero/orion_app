"use client";

import { useState } from "react";
import { AvisoError } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { ApiError } from "@/lib/api/fetch";
import { BotonPrincipal, Spinner } from "@/components/ui";
import { useConfirmarMayoriaDeEdad, useMe } from "@/lib/auth/session";

/**
 * La declaración que le falta a las cuentas anteriores a la regla de mayoría de edad.
 *
 * <p>Orión no acepta menores: el art. 7 de la Ley 1581 de 2012 prohíbe tratar sus datos salvo con
 * autorización del representante legal. Las cuentas nuevas lo declaran en el registro; estas nunca
 * lo hicieron, y marcarlas por nuestra cuenta habría sido fabricar una constancia que nadie firmó.
 *
 * <p>No se puede cerrar. Es deliberado y es la única vez que un diálogo de Orión no tiene salida:
 * un aviso que se descarta con Escape es un aviso que nadie lee, y aquí lo que se pide no es
 * atención sino una declaración. Lo que NO hace es cerrar la sesión ni tocar el saldo: quien no
 * quiera declararla puede irse, y sus clases y su dinero siguen ahí.
 */
export function AvisoMayoriaDeEdad() {
  const [marcado, setMarcado] = useState(false);
  const confirmar = useConfirmarMayoriaDeEdad();
  // También lo ve el profesor que entra por una invitación del admin (ese enlace no pide la
  // declaración): a él no se le habla de reservar ni de saldo.
  const { data: me } = useMe();
  const esProfesor = me?.role === "PROFESSOR";

  // Este diálogo no tiene salida: si el guardado falla en silencio, la persona se queda encerrada
  // mirando un botón que no hace nada. El error se enseña aunque sea del servidor.
  const error =
    confirmar.error instanceof ApiError
      ? confirmar.error.message
      : confirmar.isError
        ? "No pudimos guardar tu confirmación. Inténtalo de nuevo."
        : null;

  return (
    <Modal
      titulo="Antes de seguir"
      bloqueante
      onCerrar={() => {
        /* sin salida a propósito: se responde, no se descarta */
      }}
    >
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Orión está disponible solo para mayores de 18 años. Necesitamos que nos lo confirmes para
        que puedas seguir {esProfesor ? "dando" : "reservando"} clases.
      </p>

      <label
        htmlFor="declaro-mayoria"
        className="mt-4 flex cursor-pointer items-start gap-3 rounded-base border border-border bg-surface-sunken p-4 text-[13px] leading-relaxed text-text-secondary"
      >
        <input
          id="declaro-mayoria"
          type="checkbox"
          checked={marcado}
          onChange={(event) => setMarcado(event.target.checked)}
          className="mt-[3px] h-[18px] w-[18px] shrink-0 cursor-pointer accent-primary focus-visible:shadow-focus"
        />
        <span>
          Declaro que soy <strong>mayor de 18 años</strong>.
        </span>
      </label>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      <BotonPrincipal
        type="button"
        disabled={!marcado || confirmar.isPending}
        onClick={() => confirmar.mutate()}
        className="mt-5"
      >
        {confirmar.isPending ? (
          <>
            <Spinner />
            Guardando…
          </>
        ) : (
          "Confirmar"
        )}
      </BotonPrincipal>

      <p className="mt-3 text-center text-[12px] text-text-muted">
        {esProfesor ? "Tu perfil y tus clases siguen intactos." : "Tus clases y tu saldo siguen intactos."}
      </p>
    </Modal>
  );
}
