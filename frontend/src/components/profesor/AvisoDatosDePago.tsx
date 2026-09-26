"use client";

import { ShieldCheck } from "lucide-react";
import { Modal } from "@/components/Modal";
import { FormularioDatosDePago } from "@/components/profesor/DatosDePago";

/**
 * A dónde le pagamos al profe aprobado que todavía no lo ha dicho. Obligatorio (Pardo, 26/09/2026:
 * «son IMPORTANTÍSIMOS, los profes sí o sí deben llenarlos»): sin su llave Bre-B no hay cómo
 * entregarle lo de sus clases, así que no se puede cerrar, como el WhatsApp. Se cambian después en
 * «Mi perfil › Datos de pago».
 */
export function AvisoDatosDePago() {
  return (
    <Modal
      titulo="Falta a dónde te pagamos"
      bloqueante
      onCerrar={() => {
        /* sin salida a propósito: los datos de pago son obligatorios */
      }}
    >
      <p className="text-[14px] leading-relaxed text-text-secondary">
        Orión recibe en tu nombre lo que pagan tus estudiantes y te lo entrega cada quincena, menos la comisión, por
        transferencia Bre-B. Sin tu llave no podemos pagarte.
      </p>
      <p className="mt-3 flex items-start gap-2 rounded-base bg-accent-lavender-soft px-4 py-3 text-[13px] text-[#5e4a8a]">
        <ShieldCheck size={16} strokeWidth={1.9} className="mt-0.5 shrink-0" />
        La llave debe estar a tu nombre. Solo el equipo de Orión ve estos datos completos.
      </p>
      {/* Al guardar, el formulario deja los datos en la caché y el armazón cierra este aviso solo. */}
      <FormularioDatosDePago primeraVez onListo={() => {}} />
    </Modal>
  );
}
