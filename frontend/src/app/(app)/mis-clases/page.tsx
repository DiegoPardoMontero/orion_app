"use client";

import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

export default function MisClasesPage() {
  return (
    <main className="mx-auto max-w-md p-4">
      {/* useSearchParams se resuelve en el cliente: Next exige envolverlo en Suspense. */}
      <Suspense fallback={null}>
        <BannerReserva />
      </Suspense>

      <h1 className="text-xl font-semibold">Mis clases</h1>
      <p className="mt-2 text-sm text-ink-muted">Se construye en el Paso 3.</p>
    </main>
  );
}

function BannerReserva() {
  const params = useSearchParams();
  if (params.get("reservada") !== "1") return null;

  return (
    <p className="mb-3.5 rounded-card bg-success-soft px-3 py-2.5 text-sm text-success">
      ¡Clase reservada! Te enviamos la confirmación al correo.
    </p>
  );
}
