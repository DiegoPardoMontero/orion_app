"use client";

import { useEffect } from "react";

/**
 * Escape cierra lo que está abierto encima.
 *
 * <p>Los menús desplegables de Orión se cierran con un clic fuera, y eso basta con ratón. Con
 * teclado no: la única salida era encontrar a ciegas un punto vacío de la pantalla, porque la capa
 * invisible que captura ese clic también se traga todo lo demás. El diálogo ya lo hacía; los
 * desplegables no, y son los que más se abren sin querer.
 */
export function useCerrarConEscape(abierto: boolean, cerrar: () => void) {
  useEffect(() => {
    if (!abierto) return;
    const alTeclado = (evento: KeyboardEvent) => {
      if (evento.key === "Escape") cerrar();
    };
    document.addEventListener("keydown", alTeclado);
    return () => document.removeEventListener("keydown", alTeclado);
  }, [abierto, cerrar]);
}
