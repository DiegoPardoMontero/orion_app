"use client";

import { useEffect, type ReactNode } from "react";

export function Modal({
  titulo,
  onCerrar,
  children,
}: {
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
}) {
  // Escape cierra: lo espera cualquiera que use un teclado.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onCerrar();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onCerrar]);

  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-ink/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      onClick={onCerrar}
    >
      <div
        className="w-full max-w-sm rounded-card border border-line bg-card p-5"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="text-base font-semibold">{titulo}</h2>
        <div className="mt-3">{children}</div>
      </div>
    </div>
  );
}
