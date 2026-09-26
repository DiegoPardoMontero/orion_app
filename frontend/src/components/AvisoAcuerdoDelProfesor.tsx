"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { CuerpoLegal } from "@/components/DocumentoLegal";
import { AvisoError, Cargando, ErrorCarga } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { Boton, BotonPrincipal, Spinner } from "@/components/ui";
import { PENDIENTES_KEY, useAcuerdoDelProfesor } from "@/lib/acuerdo";
import { ApiError, apiFetch } from "@/lib/api/fetch";

/**
 * La versión nueva del acuerdo del profesor, con el mandato de recaudo (brief de liquidaciones,
 * paso 1). Los profes que aceptaron la de antes la aceptan aquí; los nuevos, al postular.
 *
 * <p>A diferencia de la mayoría de edad o el WhatsApp, se puede aplazar: sin aceptarla el profe
 * sigue dando clases, pero sus liquidaciones quedan retenidas, y eso se le dice sin rodeos.
 */
export function AvisoAcuerdoDelProfesor({ onAplazar }: { onAplazar: () => void }) {
  const queryClient = useQueryClient();
  const acuerdo = useAcuerdoDelProfesor();
  const [marcado, setMarcado] = useState(false);
  const aceptar = useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/me/agreements/TEACHER_AGREEMENT/accept", { method: "POST" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: PENDIENTES_KEY }),
  });

  const error =
    aceptar.error instanceof ApiError
      ? aceptar.error.message
      : aceptar.isError
        ? "No pudimos guardar tu aceptación. Inténtalo de nuevo."
        : null;

  return (
    <Modal titulo="Actualizamos el acuerdo del profesor" onCerrar={onAplazar} amplio>
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión.
        Esta versión lo deja por escrito. <strong className="text-text">Mientras no la aceptes, puedes seguir
        dando clases, pero no podemos pagarte tus liquidaciones.</strong>
      </p>

      <div className="mt-4 max-h-[42vh] overflow-y-auto rounded-base border border-border bg-surface-sunken px-4 pb-4">
        {acuerdo.data ? (
          <CuerpoLegal body={acuerdo.data.body} />
        ) : acuerdo.isError ? (
          <div className="pt-4">
            <ErrorCarga mensaje="No pudimos cargar el acuerdo." onReintentar={() => void acuerdo.refetch()} />
          </div>
        ) : (
          <div className="pt-4">
            <Cargando filas={3} />
          </div>
        )}
      </div>

      <label
        htmlFor="acepto-acuerdo"
        className="mt-4 flex cursor-pointer items-start gap-3 rounded-base border border-border p-4 text-[13.5px] leading-relaxed text-text"
      >
        <input
          id="acepto-acuerdo"
          type="checkbox"
          checked={marcado}
          disabled={!acuerdo.data}
          onChange={(event) => setMarcado(event.target.checked)}
          className="mt-[3px] h-[18px] w-[18px] shrink-0 cursor-pointer accent-primary focus-visible:shadow-focus"
        />
        <span>
          Leí y acepto el acuerdo del profesor{acuerdo.data ? ` (versión ${acuerdo.data.version})` : ""}, con el
          mandato de recaudo.
        </span>
      </label>

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      <BotonPrincipal type="button" disabled={!marcado || aceptar.isPending} onClick={() => aceptar.mutate()} className="mt-5">
        {aceptar.isPending ? (
          <>
            <Spinner />
            Guardando…
          </>
        ) : (
          "Aceptar"
        )}
      </BotonPrincipal>
      <Boton variante="fantasma" onClick={onAplazar} className="mt-2 w-full">
        Ahora no
      </Boton>

      <p className="mt-3 text-center text-[12px] text-text-muted">
        ¿Dudas antes de aceptar?{" "}
        <Link href="/ayuda" className="font-bold text-primary-strong hover:underline">
          Escríbenos
        </Link>
        . También lo puedes leer en{" "}
        <Link href="/acuerdo-del-profesor" target="_blank" className="font-bold text-primary-strong hover:underline">
          orionidiomas.com/acuerdo-del-profesor
        </Link>
        .
      </p>
    </Modal>
  );
}
