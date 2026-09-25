"use client";

import { useState } from "react";
import { AvisoError } from "@/components/estados";
import { Modal } from "@/components/Modal";
import { AyudaWhatsapp, PhoneInput } from "@/components/PhoneInput";
import { BotonPrincipal, Spinner } from "@/components/ui";
import { ApiError } from "@/lib/api/fetch";
import { useGuardarWhatsapp, type Me } from "@/lib/auth/session";
import { whatsappValido } from "@/lib/phone";

/**
 * El WhatsApp que le falta a una cuenta. Es obligatorio desde el 25/09/2026 (Pardo): las cuentas
 * nuevas lo dan al registrarse, pero las que nacieron cuando era opcional —o las que creó el admin
 * sin él— no lo tienen, y se les pide al entrar, entren por donde entren.
 *
 * <p>No se puede cerrar, como la declaración de mayoría de edad: un dato obligatorio que se descarta
 * con Escape deja de serlo. No toca nada más: las clases, el saldo y el perfil siguen ahí.
 */
export function AvisoWhatsapp({ me }: { me: Me }) {
  const [numero, setNumero] = useState("");
  const guardar = useGuardarWhatsapp();
  const ensena = me.role === "PROFESSOR" || me.role === "TEACHER_APPLICANT";

  // Sin salida: si el guardado falla en silencio, la persona se queda mirando un botón que no hace
  // nada. El error se enseña aunque sea del servidor.
  const error =
    guardar.error instanceof ApiError
      ? guardar.error.message
      : guardar.isError
        ? "No pudimos guardar tu número. Inténtalo de nuevo."
        : null;

  return (
    <Modal
      titulo="Falta tu WhatsApp"
      bloqueante
      onCerrar={() => {
        /* sin salida a propósito: el número es obligatorio */
      }}
    >
      <p className="text-[14px] leading-relaxed text-text-secondary">
        {ensena
          ? "Lo usa el equipo de Orión para ubicarte si pasa algo con una clase o con tu perfil."
          : "Lo usamos para avisarte de tus clases. Con tu profesor sigues hablando dentro de Orión."}
      </p>

      <label htmlFor="whatsapp-obligatorio" className="mt-4 block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary">
        WhatsApp
      </label>
      <PhoneInput id="whatsapp-obligatorio" value={numero} onChange={setNumero} className="mt-1.5" />
      <AyudaWhatsapp numero={numero} />

      {error && (
        <div className="mt-4">
          <AvisoError mensaje={error} />
        </div>
      )}

      <BotonPrincipal
        type="button"
        disabled={!whatsappValido(numero) || guardar.isPending}
        onClick={() => guardar.mutate({ fullName: me.fullName, whatsappPhone: numero })}
        className="mt-5"
      >
        {guardar.isPending ? (
          <>
            <Spinner />
            Guardando…
          </>
        ) : (
          "Guardar"
        )}
      </BotonPrincipal>

      <p className="mt-3 text-center text-[12px] text-text-muted">No lo ve ningún profesor ni otro estudiante.</p>
    </Modal>
  );
}
