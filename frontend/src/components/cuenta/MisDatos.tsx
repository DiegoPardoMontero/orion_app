"use client";

import { useQueryClient } from "@tanstack/react-query";
import { KeyRound, Mail, User } from "lucide-react";
import { useState } from "react";
import { useEtiquetaDeClave } from "@/components/CambiarClave";
import { useFormularioEditable } from "@/components/edicion/EdicionEnPagina";
import { AyudaWhatsapp, PhoneInput } from "@/components/PhoneInput";
import { Campo } from "@/components/ui";
import { apiFetch } from "@/lib/api/fetch";
import { meQueryKey } from "@/lib/auth/session";
import { whatsappValido } from "@/lib/phone";

export type Cuenta = {
  fullName: string;
  email: string;
  whatsappPhone: string | null;
  role: string;
  photoUrl: string | null;
};

/**
 * Nombre y WhatsApp: se editan directo y se guardan con la barra de la página. Vive aparte desde el
 * 29/09/2026 porque ya no es solo del estudiante: el profe los corrige en «Mi perfil».
 */
export function MisDatos({
  inicial,
  onCambiarClave,
  ayudaNombre,
  enTarjeta = true,
}: {
  inicial: Cuenta;
  /** Sin él no sale el botón de la contraseña (el profe la cambia desde su menú). */
  onCambiarClave?: () => void;
  ayudaNombre: string;
  /** Dentro de una sección que ya es tarjeta, sin su propio borde. */
  enTarjeta?: boolean;
}) {
  const queryClient = useQueryClient();
  const [nombre, setNombre] = useState(inicial.fullName);
  const [telefono, setTelefono] = useState(inicial.whatsappPhone ?? "");
  const etiquetaDeClave = useEtiquetaDeClave();

  const sucio = nombre.trim() !== inicial.fullName.trim() || telefono.trim() !== (inicial.whatsappPhone ?? "").trim();

  useFormularioEditable(
    sucio,
    async () => {
      if (!nombre.trim()) throw new Error("Tu nombre no puede quedar vacío.");
      // Obligatorio desde el 25/09/2026: se cambia, pero no se borra.
      if (!whatsappValido(telefono)) throw new Error("Tu WhatsApp es obligatorio: escríbelo completo.");
      const actualizada = await apiFetch<Cuenta>("/api/v1/me/account", {
        method: "PUT",
        body: { fullName: nombre.trim(), whatsappPhone: telefono },
      });
      queryClient.setQueryData(["me", "account"], actualizada);
      setNombre(actualizada.fullName);
      setTelefono(actualizada.whatsappPhone ?? "");
      // El nombre se ve en el header/avatar y, para el profe, en la portada de su perfil.
      void queryClient.invalidateQueries({ queryKey: meQueryKey });
      void queryClient.invalidateQueries({ queryKey: ["me", "profile"], exact: true });
    },
    () => {
      setNombre(inicial.fullName);
      setTelefono(inicial.whatsappPhone ?? "");
    },
  );

  return (
    <div className={enTarjeta ? "mt-3 rounded-card border border-border bg-surface-raised p-5" : ""}>
      {/* En el computador, los campos van de a dos dentro de la tarjeta: a todo el ancho, un nombre
          quedaba en un campo de 1.400 px. */}
      <div className="grid gap-4 lg:grid-cols-2 lg:gap-6">
        <div>
          <label className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="nombre">
            Nombre completo
          </label>
          <Campo
            id="nombre"
            type="text"
            maxLength={150}
            value={nombre}
            onChange={(event) => setNombre(event.target.value)}
            icono={<User size={18} strokeWidth={1.75} />}
            className="mt-1.5"
          />
          <p className="mt-1.5 text-[12px] text-text-muted">{ayudaNombre}</p>
        </div>

        <div>
          <label className="block text-[12px] font-bold uppercase tracking-[0.04em] text-text-secondary" htmlFor="telefono">
            WhatsApp
          </label>
          <PhoneInput id="telefono" value={telefono} onChange={setTelefono} className="mt-1.5" />
          <AyudaWhatsapp numero={telefono} ayuda="Solo lo usa el equipo de Orión si necesita avisarte algo de una clase." />
        </div>
      </div>

      <div className="mt-5 grid gap-3 lg:grid-cols-2 lg:gap-6">
        {/* El correo se muestra, no se edita. */}
        <div className="flex items-center gap-2.5 rounded-base bg-surface-sunken px-4 py-3">
          <Mail size={16} strokeWidth={1.75} className="shrink-0 text-text-muted" />
          <span className="truncate text-[13px] text-text-secondary">{inicial.email}</span>
        </div>

        {onCambiarClave && (
          <button
            type="button"
            onClick={onCambiarClave}
            className="flex w-full items-center gap-2.5 rounded-base border-[1.5px] border-border px-4 py-3 text-left text-[13.5px] font-semibold text-text transition-colors hover:bg-surface-sunken focus-visible:shadow-focus"
          >
            <KeyRound size={16} strokeWidth={1.75} className="text-text-secondary" />
            {etiquetaDeClave}
          </button>
        )}
      </div>
    </div>
  );
}
