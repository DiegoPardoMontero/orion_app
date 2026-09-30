"use client";

import { ShieldCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { FormularioDatosDePago } from "@/components/profesor/DatosDePago";
import { useLogout } from "@/lib/auth/session";
import { whatsappSoporte } from "@/lib/config";

/**
 * A dónde le pagamos al profe aprobado que todavía no lo ha dicho. Obligatorio (Pardo, 26/09/2026:
 * «son IMPORTANTÍSIMOS, los profes sí o sí deben llenarlos»): sin su llave Bre-B no hay cómo
 * entregarle lo de sus clases, así que no se puede cerrar, como el WhatsApp. Se cambian después en
 * «Mi perfil › Datos de pago».
 *
 * Obligatorio no es encerrado (Pardo, 29/09/2026): quien todavía no tiene su llave puede salir o
 * escribirnos. El menú con «Salir» queda debajo del velo, así que la salida va aquí.
 */
export function AvisoDatosDePago() {
  const router = useRouter();
  const logout = useLogout();
  const whatsapp = whatsappSoporte("Hola, soy profe en Orión y tengo una duda con mis datos de pago.");

  return (
    <Modal
      titulo="Falta a dónde te pagamos"
      bloqueante
      onCerrar={() => {
        /* sin salida a propósito: los datos de pago son obligatorios */
      }}
    >
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena por
        transferencia Bre-B. Sin tu llave no podemos pagarte.
      </p>
      <p className="mt-3 flex items-start gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[13px] text-[#5e4a8a]">
        <ShieldCheck size={16} strokeWidth={1.9} className="mt-0.5 shrink-0" />
        La llave debe estar a tu nombre. Solo el equipo de Orión ve estos datos completos.
      </p>
      {/* Al guardar, el formulario deja los datos en la caché y el armazón cierra este aviso solo. */}
      <FormularioDatosDePago primeraVez onListo={() => {}} />

      <div className="mt-4 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-center text-[12.5px] text-text-muted">
        {whatsapp && (
          <span>
            ¿Todavía no tienes tu llave?{" "}
            <a href={whatsapp} target="_blank" rel="noopener noreferrer" className="font-bold text-primary-strong hover:underline">
              Escríbenos por WhatsApp
            </a>
          </span>
        )}
        <button
          type="button"
          disabled={logout.isPending}
          onClick={() => logout.mutate(undefined, { onSuccess: () => router.replace("/login") })}
          className="inline-flex min-h-11 items-center font-bold text-text-secondary hover:underline"
        >
          {logout.isPending ? "Saliendo…" : "Salir"}
        </button>
      </div>
    </Modal>
  );
}
