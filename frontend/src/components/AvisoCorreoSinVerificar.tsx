"use client";

import { MailWarning } from "lucide-react";
import { useReenviarVerificacion } from "@/lib/auth/session";
import { ApiError } from "@/lib/api/fetch";

/**
 * La barra que recuerda confirmar el correo.
 *
 * <p>Una barra y no un diálogo, a diferencia de la de mayoría de edad. La distinción es
 * deliberada: la mayoría de edad es una declaración que hay que dar y no se puede posponer; esto
 * es una tarea que está a medias en otro sitio —el buzón— y bloquear la app mientras tanto no la
 * acerca ni un paso. Solo estorba cuando llega el momento de reservar, y ahí sí, el backend
 * responde 422.
 */
export function AvisoCorreoSinVerificar({
  correo,
  ensena = false,
}: {
  correo: string;
  /** Quien vino a enseñar todavía no puede reservar nada: decírselo le hace dudar de dónde se registró. */
  ensena?: boolean;
}) {
  const reenviar = useReenviarVerificacion();

  const mensaje = reenviar.isSuccess
    ? `Te lo reenviamos a ${correo}. Revisa también la carpeta de spam.`
    : reenviar.error instanceof ApiError
      ? reenviar.error.message
      : null;

  return (
    <div className="border-b border-accent-peach bg-accent-peach-soft px-5 py-3">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-x-3 gap-y-2 text-[13px] leading-relaxed text-[#8a5a33]">
        <MailWarning size={17} strokeWidth={1.75} className="shrink-0" />
        <p className="min-w-0 flex-1">
          {mensaje ?? (
            <>
              {ensena ? "Confirma tu correo para poder dictar tus clases." : "Confirma tu correo para poder reservar."}{" "}
              Ya te enviamos un enlace a <strong className="font-bold">{correo}</strong>.
            </>
          )}
        </p>
        {!reenviar.isSuccess && (
          <button
            type="button"
            onClick={() => reenviar.mutate()}
            disabled={reenviar.isPending}
            className="shrink-0 rounded-pill border border-[#8a5a33]/35 px-3 py-1 font-bold transition-colors hover:bg-[#8a5a33]/10 focus-visible:shadow-focus disabled:opacity-50"
          >
            {reenviar.isPending ? "Enviando…" : "Reenviar"}
          </button>
        )}
      </div>
    </div>
  );
}
