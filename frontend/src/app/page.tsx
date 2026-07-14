"use client";

import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api/fetch";

type Health = { status: string };

/**
 * Página provisional del Paso 0: solo comprueba que el proxy de Next llega al backend.
 *
 * Es un client component ("use client"): corre en el navegador, así que puede usar hooks como
 * useQuery y leer cookies. Sin esa directiva, Next lo trataría como server component y el fetch
 * saldría del servidor, sin las cookies de sesión del usuario.
 */
export default function Home() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["health"],
    queryFn: () => apiFetch<Health>("/actuator/health"),
  });

  return (
    <main className="flex-1 grid place-items-center p-6">
      <div className="w-full max-w-md rounded-card border border-line bg-card p-8 text-center">
        <h1 className="text-2xl font-semibold text-accent">Orión</h1>
        <p className="mt-1 text-sm text-ink-muted">Language Academy</p>

        <p className="mt-8 text-sm text-ink-soft">
          {isPending && "Consultando el backend…"}
          {isError && "Backend: no responde"}
          {data && `Backend: ${data.status}`}
        </p>
      </div>
    </main>
  );
}
