"use client";

import { useSyncExternalStore } from "react";

/**
 * Media query reactiva. Con useSyncExternalStore no hay setState dentro de un effect ni desajuste
 * de hidratación: en el servidor devuelve `false` (móvil primero) y en el cliente se sincroniza.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}
