"use client";

import { Pencil } from "lucide-react";
import { Boton, BotonPrincipal } from "@/components/ui";

/**
 * El gesto de editar, explícito.
 *
 * <p>Los formularios de perfil nacían abiertos: los campos parecían escribibles siempre, así que no
 * había forma de distinguir «estoy mirando mis datos» de «estoy cambiándolos». Se tocaba algo sin
 * querer y la única pista de que había pasado era un botón de guardar que llevaba ahí desde el
 * principio. Ahora hay dos modos y se ven: se lee, se pulsa Editar, se guarda o se descarta.
 *
 * <p>Cancelar devuelve los valores a como estaban — por eso {@code onCancelar} es obligatorio y no
 * un simple cambio de modo: salir de edición sin restaurar dejaría en pantalla unos cambios que no
 * se guardaron, que es la peor de las tres opciones.
 */
export function BarraDeEdicion({
  editando,
  guardando,
  onEditar,
  onCancelar,
  onGuardar,
  etiquetaEditar = "Editar",
  className = "",
}: {
  editando: boolean;
  guardando?: boolean;
  onEditar: () => void;
  onCancelar: () => void;
  onGuardar: () => void;
  etiquetaEditar?: string;
  className?: string;
}) {
  if (!editando) {
    return (
      <Boton variante="contorno" onClick={onEditar} className={className}>
        <Pencil size={16} strokeWidth={2.2} />
        {etiquetaEditar}
      </Boton>
    );
  }

  return (
    <div className={`flex flex-wrap gap-2.5 ${className}`}>
      <BotonPrincipal disabled={guardando} onClick={onGuardar}>
        {guardando ? "Guardando…" : "Guardar cambios"}
      </BotonPrincipal>
      <Boton variante="contorno" disabled={guardando} onClick={onCancelar}>
        Cancelar
      </Boton>
    </div>
  );
}
