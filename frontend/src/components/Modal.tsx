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
      className="fixed inset-0 z-50 grid place-items-center p-5"
      style={{ background: "rgba(20,16,46,0.45)" }}
      role="dialog"
      aria-modal="true"
      aria-label={titulo}
      onClick={onCerrar}
    >
      <div
        className="w-full max-w-sm rounded-sheet bg-surface p-6 shadow-[0_24px_48px_rgba(20,16,46,0.35)]"
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="text-[16px] font-extrabold">{titulo}</h2>
        <div className="mt-3">{children}</div>
      </div>
    </div>
  );
}
